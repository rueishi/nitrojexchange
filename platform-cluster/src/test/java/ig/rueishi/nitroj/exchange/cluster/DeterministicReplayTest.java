package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replay-style determinism coverage for the cluster market/decision core.
 *
 * <p>Responsibility: apply ordered internal events twice from the same initial
 * state and compare externally visible state plus emitted command decisions.
 * Role in system: protects the V11 definition of determinism: identical ordered
 * SBE input streams produce identical book, own-liquidity, strategy-decision,
 * and outbound command summaries. Relationships: uses real {@link
 * InternalMarketView}, {@link OwnOrderOverlay}, and {@link ExternalLiquidityView}
 * while keeping the replay fixture intentionally small and local. Lifecycle:
 * unit test only; no wall-clock timestamps are consulted.</p>
 */
final class DeterministicReplayTest {
    @Test
    void positive_sameOrderedReplayProducesIdenticalStateAndCommands() {
        final List<ReplayEvent> stream = List.of(
            ReplayEvent.market(1, EntryType.BID, 100L, 10L),
            ReplayEvent.market(2, EntryType.ASK, 99L, 10L),
            ReplayEvent.ownOrder(1L, "OWN-1", 1, EntryType.BID, 100L, 2L),
            ReplayEvent.strategyTick(),
            ReplayEvent.riskDecision(true));

        final ReplayResult first = ReplayHarness.apply(stream);
        final ReplayResult second = ReplayHarness.apply(stream);

        assertThat(second).isEqualTo(first);
        assertThat(first.commands).containsExactly("ARB_CHECK:" + 100L, "RISK_APPROVED");
    }

    @Test
    void negative_changedEventOrderProducesDifferentResult() {
        final ReplayResult first = ReplayHarness.apply(List.of(
            ReplayEvent.market(1, EntryType.BID, 100L, 10L),
            ReplayEvent.strategyTick()));
        final ReplayResult second = ReplayHarness.apply(List.of(
            ReplayEvent.strategyTick(),
            ReplayEvent.market(1, EntryType.BID, 100L, 10L)));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void edge_emptySingleDuplicateAndMaxIdsReplayDeterministically() {
        assertThat(ReplayHarness.apply(List.of())).isEqualTo(ReplayHarness.apply(List.of()));
        assertThat(ReplayHarness.apply(List.of(ReplayEvent.strategyTick())))
            .isEqualTo(ReplayHarness.apply(List.of(ReplayEvent.strategyTick())));
        assertThat(ReplayHarness.apply(List.of(
            ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L),
            ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L))))
            .isEqualTo(ReplayHarness.apply(List.of(
                ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L),
                ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L))));
    }

    @Test
    void exception_malformedReplayFixtureFailsClearly() {
        assertThatThrownBy(() -> ReplayHarness.apply(List.of(ReplayEvent.market(0, EntryType.BID, 100L, 1L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venue");
    }

    @Test
    void failure_rejectedRiskAndStaleMarketDataProduceDeterministicCommands() {
        final List<ReplayEvent> stream = List.of(
            ReplayEvent.market(1, EntryType.BID, 100L, 10L),
            ReplayEvent.riskDecision(false),
            ReplayEvent.staleMarketData(),
            ReplayEvent.strategyTick());

        final ReplayResult result = ReplayHarness.apply(stream);

        assertThat(result).isEqualTo(ReplayHarness.apply(stream));
        assertThat(result.commands).containsExactly("RISK_REJECTED", "STALE_MARKET_DATA", "ARB_CHECK:" + 100L);
    }

    private record ReplayEvent(
        EventType type,
        int venueId,
        EntryType side,
        long price,
        long size,
        long clOrdId,
        String venueOrderId,
        boolean approved) {

        static ReplayEvent market(final int venueId, final EntryType side, final long price, final long size) {
            return new ReplayEvent(EventType.MARKET, venueId, side, price, size, 0L, null, false);
        }

        static ReplayEvent ownOrder(
            final long clOrdId,
            final String venueOrderId,
            final int venueId,
            final EntryType side,
            final long price,
            final long size) {
            return new ReplayEvent(EventType.OWN_ORDER, venueId, side, price, size, clOrdId, venueOrderId, false);
        }

        static ReplayEvent strategyTick() {
            return new ReplayEvent(EventType.STRATEGY_TICK, 0, EntryType.NULL_VAL, 0L, 0L, 0L, null, false);
        }

        static ReplayEvent riskDecision(final boolean approved) {
            return new ReplayEvent(EventType.RISK_DECISION, 0, EntryType.NULL_VAL, 0L, 0L, 0L, null, approved);
        }

        static ReplayEvent staleMarketData() {
            return new ReplayEvent(EventType.STALE_MARKET_DATA, 0, EntryType.NULL_VAL, 0L, 0L, 0L, null, false);
        }
    }

    private enum EventType {
        MARKET,
        OWN_ORDER,
        STRATEGY_TICK,
        RISK_DECISION,
        STALE_MARKET_DATA
    }

    private record ReplayResult(long bestBid, long externalBidSize, int ownOrderCount, List<String> commands) {
    }

    private static final class ReplayHarness {
        private final InternalMarketView marketView = new InternalMarketView();
        private final List<String> commands = new ArrayList<>();

        static ReplayResult apply(final List<ReplayEvent> stream) {
            final ReplayHarness harness = new ReplayHarness();
            for (ReplayEvent event : stream) {
                harness.apply(event);
            }
            return new ReplayResult(
                harness.marketView.getBestBid(1, Ids.INSTRUMENT_BTC_USD),
                harness.marketView.externalLiquidityView().externalSizeAt(
                    1, Ids.INSTRUMENT_BTC_USD, EntryType.BID, 100L),
                harness.marketView.ownOrderOverlay().orderCount(),
                List.copyOf(harness.commands));
        }

        private void apply(final ReplayEvent event) {
            switch (event.type) {
                case MARKET -> {
                    if (event.venueId <= 0) {
                        throw new IllegalArgumentException("venue must be positive");
                    }
                    marketView.apply(market(event.venueId, event.side, event.price, event.size), commands.size() + 1L);
                }
                case OWN_ORDER -> marketView.ownOrderOverlay().upsert(
                    event.clOrdId,
                    event.venueOrderId,
                    event.venueId,
                    Ids.INSTRUMENT_BTC_USD,
                    event.side,
                    event.price,
                    event.size,
                    true);
                case STRATEGY_TICK -> commands.add("ARB_CHECK:" + marketView.getBestBid(1, Ids.INSTRUMENT_BTC_USD));
                case RISK_DECISION -> commands.add(event.approved ? "RISK_APPROVED" : "RISK_REJECTED");
                case STALE_MARKET_DATA -> commands.add("STALE_MARKET_DATA");
            }
        }

        private static MarketDataEventDecoder market(
            final int venueId,
            final EntryType side,
            final long price,
            final long size) {

            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
            new MarketDataEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .venueId(venueId)
                .instrumentId(Ids.INSTRUMENT_BTC_USD)
                .entryType(side)
                .updateAction(size == 0L ? UpdateAction.DELETE : UpdateAction.NEW)
                .priceScaled(price)
                .sizeScaled(size)
                .priceLevel(0)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1);
            final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
            decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
                MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
            return decoder;
        }
    }
}
