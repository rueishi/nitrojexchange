package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.AdminCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.AdminCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.AdminCommandType;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseEncoder;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.messages.VenueStatus;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventEncoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngineControl;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * T-018 coverage for MessageRouter dispatch and fan-out ordering.
 *
 * <p>Each collaborator records into a shared list, giving direct assertions over
 * the required order of operations without mocking frameworks or cluster
 * transport.</p>
 */
final class MessageRouterTest {
    private final List<String> calls = new ArrayList<>();

    @Test
    void onMarketData_bookUpdatedBeforeStrategyDispatched() {
        final Harness harness = harness(true);
        final UnsafeBuffer buffer = marketData();

        harness.router.dispatch(buffer, 0, MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION, MessageHeaderEncoder.ENCODED_LENGTH, MarketDataEventEncoder.TEMPLATE_ID);

        assertThat(calls).containsExactly("strategy-market:1");
        assertThat(harness.marketView.getBestBid(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD)).isEqualTo(65_000L * Ids.SCALE);
    }

    @Test
    void onExecution_fill_orderManagerBeforePortfolio() {
        final Harness harness = harness(true);

        dispatchExecution(harness);

        assertThat(calls.indexOf("order")).isLessThan(calls.indexOf("portfolio"));
    }

    @Test
    void onExecution_fill_portfolioBeforeRisk() {
        final Harness harness = harness(true);

        dispatchExecution(harness);

        assertThat(calls.indexOf("portfolio")).isLessThan(calls.indexOf("risk-fill"));
    }

    @Test
    void onExecution_fill_riskBeforeStrategy() {
        final Harness harness = harness(true);

        dispatchExecution(harness);

        assertThat(calls).containsExactly("order", "portfolio", "risk-fill", "strategy-exec:true");
    }

    @Test
    void onExecution_nonFill_portfolioAndRiskNotCalled() {
        final Harness harness = harness(false);

        dispatchExecution(harness);

        assertThat(calls).containsExactly("order", "strategy-exec:false");
    }

    @Test
    void adminCommand_routedToAdminCommandHandler() {
        final Harness harness = harness(false);
        final UnsafeBuffer buffer = adminCommand(harness.adminHandler);

        harness.router.dispatch(buffer, 0, AdminCommandEncoder.BLOCK_LENGTH, AdminCommandEncoder.SCHEMA_VERSION, MessageHeaderEncoder.ENCODED_LENGTH, AdminCommandEncoder.TEMPLATE_ID);

        assertThat(harness.risk.resetCount).isEqualTo(1);
    }

    @Test
    void venueStatus_routedToRecoveryCoordinator() {
        final Harness harness = harness(false);
        final UnsafeBuffer buffer = venueStatus();

        harness.router.dispatch(buffer, 0, VenueStatusEventEncoder.BLOCK_LENGTH, VenueStatusEventEncoder.SCHEMA_VERSION, MessageHeaderEncoder.ENCODED_LENGTH, VenueStatusEventEncoder.TEMPLATE_ID);

        assertThat(calls).containsExactly("venue");
    }

    @Test
    void balanceResponse_routedToRecoveryCoordinator() {
        final Harness harness = harness(false);
        final UnsafeBuffer buffer = balance();

        harness.router.dispatch(buffer, 0, BalanceQueryResponseEncoder.BLOCK_LENGTH, BalanceQueryResponseEncoder.SCHEMA_VERSION, MessageHeaderEncoder.ENCODED_LENGTH, BalanceQueryResponseEncoder.TEMPLATE_ID);

        assertThat(calls).containsExactly("balance");
    }

    @Test
    void unknownTemplateId_warnLogged_noException() {
        final Harness harness = harness(false);

        harness.router.dispatch(new UnsafeBuffer(new byte[16]), 0, 0, 0, MessageHeaderEncoder.ENCODED_LENGTH, 999);

        assertThat(harness.router.unknownTemplateCount()).isEqualTo(1);
    }

    private void dispatchExecution(final Harness harness) {
        final UnsafeBuffer buffer = execution();
        harness.router.dispatch(buffer, 0, ExecutionEventEncoder.BLOCK_LENGTH, ExecutionEventEncoder.SCHEMA_VERSION, MessageHeaderEncoder.ENCODED_LENGTH, ExecutionEventEncoder.TEMPLATE_ID);
    }

    private Harness harness(final boolean fill) {
        final RecordingRisk risk = new RecordingRisk(calls);
        final RecordingStrategy strategy = new RecordingStrategy(calls);
        final AdminCommandHandler adminHandler = new AdminCommandHandler(risk, strategy, () -> 1_700_000_000_000L, "key".getBytes(java.nio.charset.StandardCharsets.US_ASCII), new int[]{1});
        final MessageRouter router = new MessageRouter(strategy, risk, new RecordingOrderManager(calls, fill), new RecordingPortfolio(calls), new InternalMarketView(), new RecordingRecovery(calls), adminHandler, () -> 1L);
        return new Harness(router, risk, adminHandler, (InternalMarketView) routerMarketView(router));
    }

    private static Object routerMarketView(final MessageRouter router) {
        try {
            final java.lang.reflect.Field field = MessageRouter.class.getDeclaredField("marketView");
            field.setAccessible(true);
            return field.get(router);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static UnsafeBuffer marketData() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(EntryType.BID)
            .updateAction(UpdateAction.NEW)
            .priceScaled(65_000L * Ids.SCALE)
            .sizeScaled(1_000_000L)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        return buffer;
    }

    private static UnsafeBuffer execution() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] empty = new byte[0];
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1L)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(ExecType.FILL)
            .side(Side.BUY)
            .fillPriceScaled(65_000L * Ids.SCALE)
            .fillQtyScaled(1_000_000L)
            .cumQtyScaled(1_000_000L)
            .leavesQtyScaled(0L)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(empty, 0, 0)
            .putExecId(empty, 0, 0);
        return buffer;
    }

    private static UnsafeBuffer venueStatus() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        new VenueStatusEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .status(VenueStatus.DISCONNECTED)
            .ingressTimestampNanos(1L);
        return buffer;
    }

    private static UnsafeBuffer balance() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        new BalanceQueryResponseEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .balanceScaled(0L)
            .queryTimestampNanos(1L);
        return buffer;
    }

    private UnsafeBuffer adminCommand(final AdminCommandHandler handler) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        final long nonce = (1_700_000_000L << 32) | 1L;
        final long hmac = handler.computeHmac(AdminCommandType.RESET_DAILY_COUNTERS, 0, 0, 1, nonce);
        new AdminCommandEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .commandType(AdminCommandType.RESET_DAILY_COUNTERS)
            .venueId(0)
            .instrumentId(0)
            .operatorId(1)
            .hmacSignature(hmac)
            .nonce(nonce);
        return buffer;
    }

    private record Harness(MessageRouter router, RecordingRisk risk, AdminCommandHandler adminHandler, InternalMarketView marketView) {
    }

    private record RecordingStrategy(List<String> calls) implements MessageRouter.StrategyDispatch, StrategyEngineControl {
        @Override public void onMarketData(final MarketDataEventDecoder decoder) { calls.add("strategy-market"); }
        @Override public void onMarketData(final MarketDataEventDecoder decoder, final long clusterTimeMicros) { calls.add("strategy-market:" + clusterTimeMicros); }
        @Override public void onExecution(final ExecutionEventDecoder decoder, final boolean isFill) { calls.add("strategy-exec:" + isFill); }
        @Override public void pauseStrategy(final int strategyId) { }
        @Override public void resumeStrategy(final int strategyId) { }
    }

    private record RecordingOrderManager(List<String> calls, boolean fill) implements OrderManager {
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { calls.add("order"); return fill; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private record RecordingPortfolio(List<String> calls) implements PortfolioEngine {
        @Override public void onFill(final ExecutionEventDecoder decoder) { calls.add("portfolio"); }
        @Override public void initPosition(final int venueId, final int instrumentId) { }
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
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RecordingRisk implements RiskEngine {
        final List<String> calls;
        int resetCount;
        RecordingRisk(final List<String> calls) { this.calls = calls; }
        @Override public void onFill(final ExecutionEventDecoder decoder) { calls.add("risk-fill"); }
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
        @Override public void resetDailyCounters() { resetCount++; }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private record RecordingRecovery(List<String> calls) implements RecoveryCoordinator {
        @Override public void onVenueStatus(final VenueStatusEventDecoder decoder) { calls.add("venue"); }
        @Override public void onBalanceResponse(final BalanceQueryResponseDecoder decoder) { calls.add("balance"); }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }
}
