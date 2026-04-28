package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.order.OrderState;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replay-style determinism coverage for the cluster market/decision core.
 *
 * <p>Responsibility: apply ordered internal events twice from the same initial
 * state and compare externally visible state plus emitted command decisions.
 * Role in system: protects the V11 definition of determinism: identical ordered
 * SBE input streams plus the same snapshot produce identical L2/L3 books,
 * execution state, own-liquidity, risk decisions, timer effects, capacity
 * counters, and outbound command summaries. Relationships: uses real {@link
 * InternalMarketView}, {@link VenueL3Book}, {@link OwnOrderOverlay}, {@link
 * ExternalLiquidityView}, {@link OrderManagerImpl}, and {@link RiskEngineImpl}
 * while keeping the replay fixture intentionally small and local. Lifecycle:
 * unit test only; replay inputs carry deterministic timestamps and no wall-clock
 * state participates in comparisons.</p>
 */
final class DeterministicReplayTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int SECOND_VENUE = 2;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final long CL_ORD_ID = 9001L;

    @Test
    void positive_sameOrderedReplayProducesIdenticalStateAndCommands() {
        final List<ReplayEvent> stream = List.of(
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L),
            ReplayEvent.market(SECOND_VENUE, EntryType.ASK, 99L, 10L),
            ReplayEvent.l3("L3-1", Side.SELL, UpdateAction.NEW, 101L, 4L),
            ReplayEvent.createOrder(CL_ORD_ID),
            ReplayEvent.execution(CL_ORD_ID, ExecType.NEW, 0L, 0L, 5L, false, "EXEC-ACK"),
            ReplayEvent.execution(CL_ORD_ID, ExecType.PARTIAL_FILL, 100L, 2L, 3L, false, "EXEC-FILL-1"),
            ReplayEvent.execution(CL_ORD_ID, ExecType.PARTIAL_FILL, 100L, 2L, 3L, false, "EXEC-FILL-1"),
            ReplayEvent.riskDecision(false),
            ReplayEvent.capacityFull(),
            ReplayEvent.snapshotLoad(),
            ReplayEvent.timer(42L),
            ReplayEvent.strategyTick(),
            ReplayEvent.riskDecision(true));

        final ReplayResult first = ReplayHarness.apply(stream);
        final ReplayResult second = ReplayHarness.apply(stream);

        assertThat(second).isEqualTo(first);
        assertThat(first.commands).containsExactly(
            "RISK_REJECTED:3",
            "TIMER:42",
            "ARB_CHECK:" + 100L,
            "RISK_APPROVED");
        assertThat(first.duplicateExecutionReportCount).isEqualTo(1);
        assertThat(first.capacityDropCount).isEqualTo(1);
        assertThat(first.orderStatus).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    @Test
    void negative_changedEventOrderProducesDifferentResult() {
        final ReplayResult first = ReplayHarness.apply(List.of(
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L),
            ReplayEvent.strategyTick()));
        final ReplayResult second = ReplayHarness.apply(List.of(
            ReplayEvent.strategyTick(),
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L)));

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
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L),
            ReplayEvent.riskDecision(false),
            ReplayEvent.staleMarketData(),
            ReplayEvent.strategyTick());

        final ReplayResult result = ReplayHarness.apply(stream);

        assertThat(result).isEqualTo(ReplayHarness.apply(stream));
        assertThat(result.commands).containsExactly("RISK_REJECTED:3", "STALE_MARKET_DATA", "ARB_CHECK:" + 100L);
    }

    private record ReplayEvent(
        EventType type,
        int venueId,
        EntryType side,
        Side orderSide,
        UpdateAction updateAction,
        long price,
        long size,
        long fillQty,
        long leavesQty,
        long timerCorrelationId,
        long clOrdId,
        String venueOrderId,
        String execId,
        ExecType execType,
        boolean approved) {

        static ReplayEvent market(final int venueId, final EntryType side, final long price, final long size) {
            return new ReplayEvent(EventType.MARKET, venueId, side, Side.NULL_VAL, UpdateAction.NEW,
                price, size, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent l3(
            final String venueOrderId,
            final Side side,
            final UpdateAction updateAction,
            final long price,
            final long size) {
            return new ReplayEvent(EventType.MARKET_L3, VENUE, EntryType.NULL_VAL, side, updateAction,
                price, size, 0L, 0L, 0L, 0L, venueOrderId, null, null, false);
        }

        static ReplayEvent createOrder(final long clOrdId) {
            return new ReplayEvent(EventType.CREATE_ORDER, VENUE, EntryType.NULL_VAL, Side.BUY, UpdateAction.NEW,
                100L, 5L, 0L, 5L, 0L, clOrdId, "OWN-" + clOrdId, null, null, false);
        }

        static ReplayEvent execution(
            final long clOrdId,
            final ExecType execType,
            final long fillPrice,
            final long fillQty,
            final long leavesQty,
            final boolean isFinal,
            final String execId) {
            return new ReplayEvent(EventType.EXECUTION_REPORT, VENUE, EntryType.NULL_VAL, Side.BUY, UpdateAction.NEW,
                fillPrice, 0L, fillQty, leavesQty, isFinal ? 1L : 0L, clOrdId, "OWN-" + clOrdId, execId, execType, false);
        }

        static ReplayEvent strategyTick() {
            return new ReplayEvent(EventType.STRATEGY_TICK, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent riskDecision(final boolean approved) {
            return new ReplayEvent(EventType.RISK_DECISION, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, approved);
        }

        static ReplayEvent staleMarketData() {
            return new ReplayEvent(EventType.STALE_MARKET_DATA, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent timer(final long correlationId) {
            return new ReplayEvent(EventType.TIMER, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, correlationId, 0L, null, null, null, false);
        }

        static ReplayEvent snapshotLoad() {
            return new ReplayEvent(EventType.SNAPSHOT_LOAD, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent capacityFull() {
            return new ReplayEvent(EventType.CAPACITY_FULL, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }
    }

    private enum EventType {
        MARKET,
        MARKET_L3,
        CREATE_ORDER,
        EXECUTION_REPORT,
        STRATEGY_TICK,
        RISK_DECISION,
        STALE_MARKET_DATA,
        TIMER,
        SNAPSHOT_LOAD,
        CAPACITY_FULL
    }

    private record ReplayResult(
        long bestBid,
        long bestAsk,
        long consolidatedBestBid,
        long l3AskSize,
        long externalBidSize,
        int ownOrderCount,
        int l3ActiveOrderCount,
        int liveOrderCount,
        byte orderStatus,
        long invalidTransitionCount,
        long duplicateExecutionReportCount,
        byte lastRiskRejectCode,
        long timerCount,
        long capacityDropCount,
        List<String> commands) {
    }

    private static final class ReplayHarness {
        private final InternalMarketView marketView = new InternalMarketView();
        private final VenueL3Book l3Book = new VenueL3Book(VENUE, INSTRUMENT, 4);
        private final OwnOrderOverlay capacityOverlay = new OwnOrderOverlay(1);
        private OrderManagerImpl orderManager = new OrderManagerImpl();
        private final RiskEngineImpl riskEngine = new RiskEngineImpl(riskConfig(), RiskEventSink.NO_OP, () -> { }, () -> 1_000_000L);
        private final List<String> commands = new ArrayList<>();
        private long timerCount;
        private long capacityDropCount;
        private long duplicateReportCount;
        private byte lastRiskRejectCode;

        static ReplayResult apply(final List<ReplayEvent> stream) {
            final ReplayHarness harness = new ReplayHarness();
            harness.riskEngine.setVenueConnected(VENUE, true);
            for (ReplayEvent event : stream) {
                harness.apply(event);
            }
            final OrderState order = harness.orderManager.getOrder(CL_ORD_ID);
            return new ReplayResult(
                harness.marketView.getBestBid(VENUE, INSTRUMENT),
                harness.marketView.getBestAsk(VENUE, INSTRUMENT),
                harness.marketView.consolidatedBestBid(INSTRUMENT),
                harness.l3Book.levelSize(Side.SELL, 101L),
                harness.marketView.externalLiquidityView().externalSizeAt(
                    VENUE, INSTRUMENT, EntryType.BID, 100L),
                harness.marketView.ownOrderOverlay().orderCount(),
                harness.l3Book.activeOrderCount(),
                harness.orderManager.liveOrderCount(),
                order == null ? (byte)-1 : order.status(),
                harness.orderManager.invalidTransitionCount(),
                harness.duplicateReportCount,
                harness.lastRiskRejectCode,
                harness.timerCount,
                harness.capacityDropCount,
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
                case MARKET_L3 -> l3Book.apply(l3(event), marketView.book(VENUE, INSTRUMENT), commands.size() + 1L);
                case CREATE_ORDER -> orderManager.createPendingOrder(
                    event.clOrdId,
                    VENUE,
                    INSTRUMENT,
                    Side.BUY.value(),
                    OrdType.LIMIT.value(),
                    TimeInForce.GTC.value(),
                    event.price,
                    event.size,
                    Ids.STRATEGY_MARKET_MAKING);
                case EXECUTION_REPORT -> {
                    final long duplicatesBefore = orderManager.duplicateExecutionReportCount();
                    orderManager.onExecution(execution(event));
                    duplicateReportCount += orderManager.duplicateExecutionReportCount() - duplicatesBefore;
                    final OrderState order = orderManager.getOrder(event.clOrdId);
                    if (order != null) {
                        marketView.ownOrderOverlay().updateFrom(order);
                    }
                }
                case STRATEGY_TICK -> commands.add("ARB_CHECK:" + marketView.getBestBid(1, Ids.INSTRUMENT_BTC_USD));
                case RISK_DECISION -> {
                    final RiskDecision decision = riskEngine.preTradeCheck(
                        VENUE,
                        INSTRUMENT,
                        Side.BUY.value(),
                        100L,
                        event.approved ? 1L : 1_000_000_000_000L,
                        Ids.STRATEGY_MARKET_MAKING);
                    lastRiskRejectCode = decision.rejectCode();
                    commands.add(decision.approved() ? "RISK_APPROVED" : "RISK_REJECTED:" + decision.rejectCode());
                }
                case STALE_MARKET_DATA -> commands.add("STALE_MARKET_DATA");
                case TIMER -> {
                    timerCount++;
                    commands.add("TIMER:" + event.timerCorrelationId);
                }
                case SNAPSHOT_LOAD -> {
                    final OrderManagerImpl restored = new OrderManagerImpl();
                    restored.loadSnapshotFragments(orderManager.snapshotFragments());
                    orderManager = restored;
                    final OrderState order = orderManager.getOrder(CL_ORD_ID);
                    if (order != null) {
                        marketView.ownOrderOverlay().updateFrom(order);
                    }
                }
                case CAPACITY_FULL -> {
                    capacityOverlay.upsert(1L, "CAP-1", VENUE, INSTRUMENT, EntryType.BID, 1L, 1L, true);
                    final int before = capacityOverlay.orderCount();
                    capacityOverlay.upsert(2L, "CAP-2", VENUE, INSTRUMENT, EntryType.BID, 1L, 1L, true);
                    if (capacityOverlay.orderCount() == before) {
                        capacityDropCount++;
                    }
                }
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

        private static MarketByOrderEventDecoder l3(final ReplayEvent event) {
            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
            final byte[] venueOrderId = event.venueOrderId.getBytes(StandardCharsets.US_ASCII);
            new MarketByOrderEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .venueId(VENUE)
                .instrumentId(INSTRUMENT)
                .side(event.orderSide)
                .updateAction(event.updateAction)
                .priceScaled(event.price)
                .sizeScaled(event.size)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1)
                .putVenueOrderId(venueOrderId, 0, venueOrderId.length);
            final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
            decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
            return decoder;
        }

        private static ExecutionEventDecoder execution(final ReplayEvent event) {
            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
            final byte[] venueOrderId = event.venueOrderId.getBytes(StandardCharsets.US_ASCII);
            final byte[] execId = event.execId.getBytes(StandardCharsets.US_ASCII);
            new ExecutionEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .clOrdId(event.clOrdId)
                .venueId(VENUE)
                .instrumentId(INSTRUMENT)
                .execType(event.execType)
                .side(event.orderSide)
                .fillPriceScaled(event.price)
                .fillQtyScaled(event.fillQty)
                .cumQtyScaled(5L - event.leavesQty)
                .leavesQtyScaled(event.leavesQty)
                .rejectCode(0)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1)
                .isFinal(event.timerCorrelationId == 1L ? BooleanType.TRUE : BooleanType.FALSE)
                .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
                .putExecId(execId, 0, execId.length);
            final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
            decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
            return decoder;
        }

        private static RiskConfig riskConfig() {
            return new RiskConfig(
                100,
                1_000_000_000_000L,
                Map.of(INSTRUMENT, new RiskConfig.InstrumentRisk(
                    INSTRUMENT,
                    10L,
                    10_000_000_000L,
                    10_000_000_000L,
                    10_000_000_000L,
                    80)));
        }
    }
}
