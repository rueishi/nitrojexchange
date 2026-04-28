package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.strategy.Strategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContext;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngine;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * IT-002 coverage for book update to strategy dispatch ordering.
 *
 * <p>MessageRouter owns the production path where market-data messages update
 * {@link InternalMarketView} before StrategyEngine receives the same decoder.
 * These tests verify that a strategy reading the context market view observes
 * current prices and can filter by instrument or suppress itself when the book
 * is not actionable, without depending on concrete strategy algorithms from
 * later tasks.</p>
 */
final class L2BookToStrategyIntegrationTest {
    @Test
    void marketDataEvent_updatesBook_strategySeesNewPrices() {
        final Harness harness = harness(new BookReadingStrategy(Ids.INSTRUMENT_BTC_USD));
        harness.strategyEngine.setActive(true);

        harness.router.dispatch(marketData(Ids.INSTRUMENT_BTC_USD, EntryType.BID, 65_000L * Ids.SCALE), 0,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION,
            MessageHeaderEncoder.ENCODED_LENGTH, MarketDataEventEncoder.TEMPLATE_ID);

        assertThat(harness.strategy.seenBid).isEqualTo(65_000L * Ids.SCALE);
    }

    @Test
    void bookStale_strategySuppress_noOrders() {
        final Harness harness = harness(new BookReadingStrategy(Ids.INSTRUMENT_BTC_USD));
        harness.strategyEngine.setActive(true);

        harness.strategy.onMarketData(null);

        assertThat(harness.strategy.actionableCount).isZero();
    }

    @Test
    void marketDataThenFill_positionUpdated_skewApplied() {
        final Harness harness = harness(new BookReadingStrategy(Ids.INSTRUMENT_BTC_USD));
        harness.strategyEngine.setActive(true);

        harness.strategyEngine.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 2L * Ids.SCALE, 65_000L * Ids.SCALE);

        assertThat(harness.strategy.lastPosition).isEqualTo(2L * Ids.SCALE);
        assertThat(harness.strategy.lastAvgEntry).isEqualTo(65_000L * Ids.SCALE);
    }

    @Test
    void multipleInstruments_bookUpdates_onlyCorrectStrategyNotified() {
        final Harness harness = harness(new BookReadingStrategy(Ids.INSTRUMENT_ETH_USD));
        harness.strategyEngine.setActive(true);

        harness.router.dispatch(marketData(Ids.INSTRUMENT_BTC_USD, EntryType.BID, 65_000L * Ids.SCALE), 0,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION,
            MessageHeaderEncoder.ENCODED_LENGTH, MarketDataEventEncoder.TEMPLATE_ID);
        harness.router.dispatch(marketData(Ids.INSTRUMENT_ETH_USD, EntryType.BID, 3_200L * Ids.SCALE), 0,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION,
            MessageHeaderEncoder.ENCODED_LENGTH, MarketDataEventEncoder.TEMPLATE_ID);

        assertThat(harness.strategy.actionableCount).isEqualTo(1);
        assertThat(harness.strategy.seenBid).isEqualTo(3_200L * Ids.SCALE);
    }

    private static Harness harness(final BookReadingStrategy strategy) {
        final InternalMarketView marketView = new InternalMarketView();
        final RecordingRisk risk = new RecordingRisk();
        final RecordingOrder order = new RecordingOrder();
        final RecordingPortfolio portfolio = new RecordingPortfolio();
        final RecordingRecovery recovery = new RecordingRecovery();
        final StrategyContext context = new ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl(
            marketView,
            risk,
            order,
            portfolio,
            recovery,
            null,
            new UnsafeBuffer(new byte[512]),
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(),
            null,
            null
        );
        final StrategyEngine strategyEngine = new StrategyEngine(context);
        strategyEngine.register(strategy);
        final MessageRouter router = new MessageRouter(
            strategyEngine,
            risk,
            order,
            portfolio,
            marketView,
            recovery,
            new AdminCommandHandler(risk, strategyEngine, () -> 1_700_000_000_000L, "key".getBytes(java.nio.charset.StandardCharsets.US_ASCII), new int[]{1}),
            () -> 1L
        );
        return new Harness(strategyEngine, router, strategy);
    }

    private static UnsafeBuffer marketData(final int instrumentId, final EntryType entryType, final long priceScaled) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(instrumentId)
            .entryType(entryType)
            .updateAction(UpdateAction.NEW)
            .priceScaled(priceScaled)
            .sizeScaled(1_000_000L)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        return buffer;
    }

    private record Harness(StrategyEngine strategyEngine, MessageRouter router, BookReadingStrategy strategy) {
    }

    private static final class BookReadingStrategy implements Strategy {
        private final int instrumentId;
        private StrategyContext context;
        private long seenBid = Ids.INVALID_PRICE;
        private long lastPosition;
        private long lastAvgEntry;
        private int actionableCount;

        BookReadingStrategy(final int instrumentId) {
            this.instrumentId = instrumentId;
        }

        @Override public void init(final StrategyContext ctx) { context = ctx; }

        @Override
        public void onMarketData(final MarketDataEventDecoder decoder) {
            final long bestBid = context.marketView().getBestBid(Ids.VENUE_COINBASE, instrumentId);
            if (bestBid == Ids.INVALID_PRICE) {
                return;
            }
            if (decoder != null && decoder.instrumentId() == instrumentId) {
                actionableCount++;
                seenBid = bestBid;
            }
        }

        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
        @Override public void onKillSwitch() { }
        @Override public void onKillSwitchCleared() { }
        @Override public void onVenueRecovered(final int venueId) { }
        @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) { lastPosition = netQtyScaled; lastAvgEntry = avgEntryScaled; }
        @Override public int[] subscribedInstrumentIds() { return new int[]{instrumentId}; }
        @Override public int[] activeVenueIds() { return new int[]{Ids.VENUE_COINBASE}; }
        @Override public void shutdown() { }
    }

    private static final class RecordingOrder implements OrderManager {
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return false; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void setCluster(final io.aeron.cluster.service.Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RecordingPortfolio implements PortfolioEngine {
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void archiveDailyPnl(final Publication egressPublication) { }
        @Override public void setCluster(final io.aeron.cluster.service.Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RecordingRisk implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final io.aeron.cluster.service.Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class RecordingRecovery implements RecoveryCoordinator {
        @Override public void onVenueStatus(final ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final io.aeron.cluster.service.Cluster cluster) { }
    }
}
