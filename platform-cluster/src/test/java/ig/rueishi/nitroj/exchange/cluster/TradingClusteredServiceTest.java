package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-021 coverage for the clustered trading service lifecycle.
 *
 * <p>The tests use recording component doubles and a dynamic {@link Cluster}
 * proxy rather than starting Aeron infrastructure. This keeps the suite focused
 * on TASK-026 responsibilities: component wiring, deterministic snapshot order,
 * timer fan-out, role activation, SBE header dispatch, and warmup cleanup. The
 * same constructor shape is later used by ClusterMain when real implementations
 * are assembled.</p>
 */
final class TradingClusteredServiceTest {
    private static final long RECOVERY_ORDER_QUERY_TIMEOUT_ID = 2_001L;
    private static final long RECOVERY_BALANCE_QUERY_TIMEOUT_ID = 3_001L;
    private static final long ARB_TIMEOUT_ID = 4_001L;

    @Test
    void onStart_nullSnapshot_allStateZeroed() {
        final Harness harness = harness();

        harness.service.onStart(harness.cluster.proxy, null);

        assertThat(harness.calls).contains("strategy-cluster", "risk-cluster", "order-cluster", "portfolio-cluster", "recovery-cluster");
        assertThat(harness.cluster.scheduleCount).isEqualTo(1);
        assertThat(harness.calls).doesNotContain("order-load", "portfolio-load", "risk-load", "recovery-load");
    }

    @Test
    void onStart_withSnapshot_stateRestored() {
        final Harness harness = harness();
        final Image snapshotImage = emptyImage();

        harness.service.onStart(harness.cluster.proxy, snapshotImage);

        assertThat(harness.calls)
            .containsSubsequence("order-load", "portfolio-load", "risk-load", "recovery-load");
    }

    @Test
    void onRoleChange_toFollower_strategiesDeactivated() {
        final Harness harness = harness();

        harness.service.onRoleChange(Cluster.Role.FOLLOWER);

        assertThat(harness.strategy.active).isFalse();
    }

    @Test
    void onRoleChange_toLeader_strategiesActivated() {
        final Harness harness = harness();

        harness.service.onRoleChange(Cluster.Role.LEADER);

        assertThat(harness.strategy.active).isTrue();
    }

    @Test
    void onTimerEvent_dailyResetId_timerForwarded() {
        final Harness harness = harness();

        harness.service.onTimerEvent(DailyResetTimer.DAILY_RESET_CORRELATION_ID, 9L);

        assertThat(harness.risk.dailyResetCount).isEqualTo(1);
        assertThat(harness.portfolio.archiveCount).isEqualTo(1);
    }

    @Test
    void onTimerEvent_arbTimeoutId_timerForwarded() {
        final Harness harness = harness();

        harness.service.onTimerEvent(ARB_TIMEOUT_ID, 9L);

        assertThat(harness.strategy.timerIds).containsExactly(ARB_TIMEOUT_ID);
    }

    @Test
    void onTimerEvent_recoveryOrderQueryTimeoutId_timerForwarded() {
        final Harness harness = harness();

        harness.service.onTimerEvent(RECOVERY_ORDER_QUERY_TIMEOUT_ID, 9L);

        assertThat(harness.recovery.timerIds).containsExactly(RECOVERY_ORDER_QUERY_TIMEOUT_ID);
    }

    @Test
    void onTimerEvent_recoveryBalanceQueryTimeoutId_timerForwarded() {
        final Harness harness = harness();

        harness.service.onTimerEvent(RECOVERY_BALANCE_QUERY_TIMEOUT_ID, 9L);

        assertThat(harness.recovery.timerIds).containsExactly(RECOVERY_BALANCE_QUERY_TIMEOUT_ID);
    }

    @Test
    void installClusterShim_propagatedToAllComponents() {
        final Harness harness = harness();

        harness.service.installClusterShim(harness.cluster.proxy);

        assertThat(harness.strategy.cluster).isSameAs(harness.cluster.proxy);
        assertThat(harness.risk.cluster).isSameAs(harness.cluster.proxy);
        assertThat(harness.order.cluster).isSameAs(harness.cluster.proxy);
        assertThat(harness.portfolio.cluster).isSameAs(harness.cluster.proxy);
        assertThat(harness.recovery.cluster).isSameAs(harness.cluster.proxy);
        assertThat(clusterField(harness.timer)).isSameAs(harness.cluster.proxy);
        assertThat(clusterField(harness.router)).isSameAs(harness.cluster.proxy);
    }

    @Test
    void removeClusterShim_clusterNullInAllComponents() {
        final Harness harness = harness();

        harness.service.installClusterShim(harness.cluster.proxy);
        harness.service.removeClusterShim();

        assertThat(harness.strategy.cluster).isNull();
        assertThat(harness.risk.cluster).isNull();
        assertThat(harness.order.cluster).isNull();
        assertThat(harness.portfolio.cluster).isNull();
        assertThat(harness.recovery.cluster).isNull();
        assertThat(clusterField(harness.timer)).isNull();
        assertThat(clusterField(harness.router)).isNull();
    }

    @Test
    void resetWarmupState_allComponentsReset() {
        final Harness harness = harness();

        harness.service.resetWarmupState();

        assertThat(harness.calls)
            .containsSubsequence("order-reset", "portfolio-reset", "risk-reset", "recovery-reset", "strategy-reset");
    }

    @Test
    void onTakeSnapshot_writesAllComponentsInOrder() {
        final Harness harness = harness();

        harness.service.onTakeSnapshot(null);

        assertThat(harness.calls)
            .containsSubsequence("order-snapshot", "portfolio-snapshot", "risk-snapshot", "recovery-snapshot");
    }

    @Test
    void installClusterShim_realClusterAlreadySet_throws() {
        final Harness harness = harness();
        harness.service.onStart(harness.cluster.proxy, null);

        assertThatThrownBy(() -> harness.service.installClusterShim(harness.cluster.proxy))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot install shim while real cluster is set");
    }

    @Test
    void onRoleChange_toLeaderTwice_idempotent() {
        final Harness harness = harness();

        harness.service.onRoleChange(Cluster.Role.LEADER);
        harness.service.onRoleChange(Cluster.Role.LEADER);

        assertThat(harness.strategy.active).isTrue();
        assertThat(harness.strategy.activationChanges).isEqualTo(1);
    }

    @Test
    void onTimerEvent_unknownCorrelId_ignoredSilently() {
        final Harness harness = harness();

        assertThatCode(() -> harness.service.onTimerEvent(999_999L, 1L)).doesNotThrowAnyException();

        assertThat(harness.risk.dailyResetCount).isZero();
        assertThat(harness.portfolio.archiveCount).isZero();
        assertThat(harness.recovery.timerIds).isEmpty();
        assertThat(harness.strategy.timerIds).isEmpty();
    }

    @Test
    void onSessionMessage_marketData_routedThroughHeaderDecoder() {
        final Harness harness = harness();
        final UnsafeBuffer buffer = marketData();

        harness.service.onSessionMessage(null, 1L, buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + MarketDataEventEncoder.BLOCK_LENGTH, null);

        assertThat(harness.strategy.marketDataCount).isEqualTo(1);
        assertThat(harness.router.unknownTemplateCount()).isZero();
    }

    @Test
    @Tag("SlowTest")
    void warmup_c2Verified_avgIterationBelowThreshold() {
        final Harness harness = harness();
        final UnsafeBuffer buffer = marketData();
        final int iterations = 10_000;

        final long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            harness.service.onSessionMessage(null, i, buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + MarketDataEventEncoder.BLOCK_LENGTH, null);
        }
        final long avgNanos = (System.nanoTime() - start) / iterations;

        assertThat(avgNanos).isLessThan(100_000L);
    }

    private static Harness harness() {
        final List<String> calls = new ArrayList<>();
        final RecordingStrategy strategy = new RecordingStrategy(calls);
        final RecordingRisk risk = new RecordingRisk(calls);
        final RecordingOrderManager order = new RecordingOrderManager(calls);
        final RecordingPortfolio portfolio = new RecordingPortfolio(calls);
        final RecordingRecovery recovery = new RecordingRecovery(calls);
        final DailyResetTimer timer = new DailyResetTimer(risk, portfolio, () -> 0L);
        final MessageRouter router = new MessageRouter(
            strategy,
            risk,
            order,
            portfolio,
            new InternalMarketView(),
            recovery,
            new AdminCommandHandler(risk, strategy, () -> 1_700_000_000_000L, "key".getBytes(java.nio.charset.StandardCharsets.US_ASCII), new int[]{1})
        );
        final TradingClusteredService service = new TradingClusteredService(strategy, risk, order, portfolio, recovery, timer, router);
        return new Harness(service, strategy, risk, order, portfolio, recovery, timer, router, new RecordingCluster(), calls);
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

    private static Object clusterField(final Object target) {
        try {
            final Field field = target.getClass().getDeclaredField("cluster");
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Image emptyImage() {
        try {
            final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return (Image) unsafe.allocateInstance(Image.class);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private record Harness(
        TradingClusteredService service,
        RecordingStrategy strategy,
        RecordingRisk risk,
        RecordingOrderManager order,
        RecordingPortfolio portfolio,
        RecordingRecovery recovery,
        DailyResetTimer timer,
        MessageRouter router,
        RecordingCluster cluster,
        List<String> calls
    ) {
    }

    /**
     * Dynamic Aeron Cluster proxy used by lifecycle tests.
     *
     * <p>Only the methods touched by TASK-026 collaborators have behavior:
     * {@code scheduleTimer()} records daily-reset scheduling, {@code time()}
     * returns a deterministic logical time for market-data routing, and simple
     * scalar methods return neutral values. Any unsupported reference-returning
     * method returns {@code null} because the service does not use it.</p>
     */
    private static final class RecordingCluster {
        final Cluster proxy;
        int scheduleCount;

        RecordingCluster() {
            proxy = (Cluster) Proxy.newProxyInstance(
                Cluster.class.getClassLoader(),
                new Class<?>[]{Cluster.class},
                (ignoredProxy, method, args) -> switch (method.getName()) {
                    case "scheduleTimer" -> {
                        scheduleCount++;
                        yield true;
                    }
                    case "cancelTimer" -> true;
                    case "time" -> 123L;
                    case "timeUnit" -> TimeUnit.MILLISECONDS;
                    case "role" -> Cluster.Role.LEADER;
                    case "memberId" -> 0;
                    case "logPosition" -> 0L;
                    case "offer", "tryClaim" -> 0L;
                    case "closeClientSession" -> true;
                    case "toString" -> "RecordingCluster";
                    default -> defaultValue(method.getReturnType());
                }
            );
        }

        private static Object defaultValue(final Class<?> type) {
            if (type == Boolean.TYPE) {
                return false;
            }
            if (type == Integer.TYPE) {
                return 0;
            }
            if (type == Long.TYPE) {
                return 0L;
            }
            return null;
        }
    }

    private static final class RecordingStrategy implements TradingClusteredService.StrategyLifecycle {
        private final List<String> calls;
        private final List<Long> timerIds = new ArrayList<>();
        private Cluster cluster;
        private boolean active;
        private int activationChanges;
        private int marketDataCount;

        RecordingStrategy(final List<String> calls) {
            this.calls = calls;
        }

        @Override public void setCluster(final Cluster cluster) { this.cluster = cluster; calls.add("strategy-cluster"); }
        @Override public void pauseStrategy(final int strategyId) { }
        @Override public void resumeStrategy(final int strategyId) { }
        @Override public void onMarketData(final MarketDataEventDecoder decoder) { marketDataCount++; }
        @Override public void onExecution(final ExecutionEventDecoder decoder, final boolean isFill) { }

        @Override
        public void onTimer(final long correlationId) {
            if (correlationId == ARB_TIMEOUT_ID) {
                timerIds.add(correlationId);
            }
        }

        @Override
        public void setActive(final boolean active) {
            if (this.active != active) {
                activationChanges++;
            }
            this.active = active;
        }

        @Override public void resetAll() { calls.add("strategy-reset"); }
    }

    private static final class RecordingOrderManager implements OrderManager {
        private final List<String> calls;
        private Cluster cluster;

        RecordingOrderManager(final List<String> calls) {
            this.calls = calls;
        }

        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return false; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final ExclusivePublication pub) { calls.add("order-snapshot"); }
        @Override public void loadSnapshot(final Image image) { calls.add("order-load"); }
        @Override public void setCluster(final Cluster cluster) { this.cluster = cluster; calls.add("order-cluster"); }
        @Override public void resetAll() { calls.add("order-reset"); }
    }

    private static final class RecordingPortfolio implements PortfolioEngine {
        private final List<String> calls;
        private Cluster cluster;
        private int archiveCount;

        RecordingPortfolio(final List<String> calls) {
            this.calls = calls;
        }

        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { calls.add("portfolio-snapshot"); }
        @Override public void loadSnapshot(final Image snapshotImage) { calls.add("portfolio-load"); }
        @Override public void archiveDailyPnl(final Publication egressPublication) { archiveCount++; }
        @Override public void setCluster(final Cluster cluster) { this.cluster = cluster; calls.add("portfolio-cluster"); }
        @Override public void resetAll() { calls.add("portfolio-reset"); }
    }

    private static final class RecordingRisk implements RiskEngine {
        private final List<String> calls;
        private Cluster cluster;
        private int dailyResetCount;

        RecordingRisk(final List<String> calls) {
            this.calls = calls;
        }

        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { calls.add("risk-snapshot"); }
        @Override public void loadSnapshot(final Image snapshotImage) { calls.add("risk-load"); }
        @Override public void resetDailyCounters() { dailyResetCount++; }
        @Override public void setCluster(final Cluster cluster) { this.cluster = cluster; calls.add("risk-cluster"); }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { calls.add("risk-reset"); }
    }

    private static final class RecordingRecovery implements RecoveryCoordinator {
        private final List<String> calls;
        private final List<Long> timerIds = new ArrayList<>();
        private Cluster cluster;

        RecordingRecovery(final List<String> calls) {
            this.calls = calls;
        }

        @Override public void onVenueStatus(final ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder decoder) { }

        @Override
        public void onTimer(final long correlationId, final long timestamp) {
            if (correlationId == RECOVERY_ORDER_QUERY_TIMEOUT_ID || correlationId == RECOVERY_BALANCE_QUERY_TIMEOUT_ID) {
                timerIds.add(correlationId);
            }
        }

        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { calls.add("recovery-snapshot"); }
        @Override public void loadSnapshot(final Image snapshotImage) { calls.add("recovery-load"); }
        @Override public void resetAll() { calls.add("recovery-reset"); }
        @Override public void setCluster(final Cluster cluster) { this.cluster = cluster; calls.add("recovery-cluster"); }
    }
}
