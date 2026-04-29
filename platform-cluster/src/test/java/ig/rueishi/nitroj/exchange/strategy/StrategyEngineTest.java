package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategy;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.cluster.service.Cluster;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

/**
 * T-020 coverage for StrategyEngine lifecycle fan-out.
 *
 * <p>The tests use lightweight recording strategies so the engine can be
 * verified without concrete market-making or arbitrage algorithms. This keeps
 * TASK-029 focused on plugin registration, leader activation, admin controls,
 * timer determinism, and cluster-reference propagation.</p>
 */
final class StrategyEngineTest {
    @Test
    void register_strategyAddedToList() {
        final StrategyEngine engine = new StrategyEngine(context(null));

        engine.register(new RecordingStrategy(1));

        assertThat(engine.strategyCount()).isEqualTo(1);
    }

    @Test
    void setActive_false_callsOnKillSwitchOnAllStrategies() {
        final Harness harness = harness();
        harness.engine.setActive(true);

        harness.engine.setActive(false);

        assertThat(harness.first.killSwitchCount).isEqualTo(1);
        assertThat(harness.second.killSwitchCount).isEqualTo(1);
    }

    @Test
    void setActive_true_strategiesReceiveKillSwitchCleared() {
        final Harness harness = harness();

        harness.engine.setActive(true);

        assertThat(harness.first.killSwitchClearedCount).isEqualTo(1);
        assertThat(harness.second.killSwitchClearedCount).isEqualTo(1);
    }

    @Test
    void pauseStrategy_specificStrategy_otherStrategiesUnaffected() {
        final Harness harness = harness();

        harness.engine.pauseStrategy(1);

        assertThat(harness.first.killSwitchCount).isEqualTo(1);
        assertThat(harness.second.killSwitchCount).isZero();
    }

    @Test
    void resumeStrategy_specificStrategy_resumes() {
        final Harness harness = harness();

        harness.engine.resumeStrategy(2);

        assertThat(harness.first.killSwitchClearedCount).isZero();
        assertThat(harness.second.killSwitchClearedCount).isEqualTo(1);
    }

    @Test
    void onMarketData_inactive_noDispatch() {
        final Harness harness = harness();

        harness.engine.onMarketData(null);

        assertThat(harness.first.marketDataCount).isZero();
        assertThat(harness.second.marketDataCount).isZero();
    }

    @Test
    void onExecution_inactiveButFill_onFillNotCalled() {
        final Harness harness = harness();

        harness.engine.onExecution(null, true);

        assertThat(harness.first.fillCount).isZero();
        assertThat(harness.second.fillCount).isZero();
    }

    @Test
    void pauseStrategy_unknownId_noEffect() {
        final Harness harness = harness();

        harness.engine.pauseStrategy(99);

        assertThat(harness.first.killSwitchCount).isZero();
        assertThat(harness.second.killSwitchCount).isZero();
    }

    @Test
    void setActive_sameValue_idempotent() {
        final Harness harness = harness();

        harness.engine.setActive(true);
        harness.engine.setActive(true);

        assertThat(harness.first.killSwitchClearedCount).isEqualTo(1);
        assertThat(harness.second.killSwitchClearedCount).isEqualTo(1);
    }

    @Test
    void register_afterSetActive_newStrategyActivatedCorrectly() {
        final StrategyEngine engine = new StrategyEngine(context(null));
        engine.setActive(true);
        final RecordingStrategy strategy = new RecordingStrategy(1);

        engine.register(strategy);

        assertThat(strategy.killSwitchClearedCount).isEqualTo(1);
    }

    @Test
    void onTimer_inactive_stillRouted_clusterDeterminism() {
        final Harness harness = harness();

        harness.engine.onTimer(4_001L);

        assertThat(harness.first.timerIds).containsExactly(4_001L);
        assertThat(harness.second.timerIds).containsExactly(4_001L);
    }

    @Test
    void onExecution_inactive_routesParentExecutionBeforeTradingGate() {
        final ExecutionHarness harness = executionHarness();
        harness.parentRegistry.claim(9_001L, 7, 2, 100L, 1L);

        harness.engine.onExecution(execution(55L), true, 9_001L);

        assertThat(harness.executionStrategy.events).containsExactly("child:55:9001");
        assertThat(harness.tradingStrategy.fillCount).isZero();
    }

    @Test
    void onMarketData_active_routesToExecutionBeforeTradingStrategies() {
        final ExecutionHarness harness = executionHarness();
        harness.engine.setActive(true);

        harness.engine.onMarketData(marketData(), 123L);

        assertThat(harness.executionStrategy.events).containsExactly("market:1:11:123");
        assertThat(harness.tradingStrategy.marketDataCount).isEqualTo(1);
    }

    @Test
    void onTimer_routesOnlyRegisteredExecutionTimerOwner() {
        final ExecutionHarness harness = executionHarness();
        assertThat(harness.executionEngine.registerTimerOwner(77L, 2)).isTrue();

        harness.engine.onTimer(77L);
        harness.engine.onTimer(88L);

        assertThat(harness.executionStrategy.events).containsExactly("timer:77");
        assertThat(harness.executionEngine.unknownTimerRejects()).isZero();
    }

    @Test
    void setCluster_reinitializesStrategiesWithClusterAwareContext() {
        final Harness harness = harness();
        final Cluster cluster = cluster();

        harness.engine.setCluster(cluster);

        assertThat(harness.first.contexts).hasSize(2);
        assertThat(harness.first.contexts.get(1).cluster()).isSameAs(cluster);
        assertThat(harness.second.contexts.get(1).cluster()).isSameAs(cluster);
    }

    private static Harness harness() {
        final StrategyEngine engine = new StrategyEngine(context(null));
        final RecordingStrategy first = new RecordingStrategy(1);
        final RecordingStrategy second = new RecordingStrategy(2);
        engine.register(first);
        engine.register(second);
        return new Harness(engine, first, second);
    }

    private static ExecutionHarness executionHarness() {
        final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(8, 8);
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(4, 8);
        final RecordingExecutionStrategy executionStrategy = new RecordingExecutionStrategy(2);
        registry.register(executionStrategy);
        registry.allowCompatibility(7, 2);
        final ExecutionStrategyContext executionContext = new ExecutionStrategyContext(
            new ig.rueishi.nitroj.exchange.cluster.InternalMarketView(),
            new RiskStub(),
            new OrderManagerStub(),
            parentRegistry,
            new UnsafeBuffer(new byte[512]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            () -> 1L,
            (correlationId, deadlineClusterMicros) -> true,
            new IdRegistryStub(),
            counters());
        final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(registry, executionContext);
        executionEngine.initRegisteredStrategies();
        final StrategyEngine engine = new StrategyEngine(context(null, executionEngine));
        final RecordingStrategy tradingStrategy = new RecordingStrategy(7);
        engine.register(tradingStrategy);
        return new ExecutionHarness(engine, executionEngine, parentRegistry, executionStrategy, tradingStrategy);
    }

    static StrategyContext context(final Cluster cluster) {
        return context(cluster, null);
    }

    static StrategyContext context(final Cluster cluster, final ExecutionStrategyEngine executionEngine) {
        return new StrategyContextImpl(
            new ig.rueishi.nitroj.exchange.cluster.InternalMarketView(),
            null,
            null,
            null,
            null,
            executionEngine,
            cluster,
            new org.agrona.concurrent.UnsafeBuffer(new byte[512]),
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(),
            null,
            null
        );
    }

    static Cluster cluster() {
        return (Cluster) Proxy.newProxyInstance(
            Cluster.class.getClassLoader(),
            new Class<?>[]{Cluster.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "time", "logPosition" -> 1L;
                case "scheduleTimer", "cancelTimer" -> true;
                case "toString" -> "StrategyEngineTestCluster";
                default -> null;
            }
        );
    }

    private static MarketDataEventDecoder marketData() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(1)
            .instrumentId(11)
            .entryType(EntryType.BID)
            .updateAction(UpdateAction.NEW)
            .priceScaled(65_000L)
            .sizeScaled(1_000L)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ExecutionEventDecoder execution(final long childClOrdId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(1)
            .instrumentId(11)
            .execType(ExecType.FILL)
            .side(Side.BUY)
            .fillPriceScaled(65_000L)
            .fillQtyScaled(100L)
            .cumQtyScaled(100L)
            .leavesQtyScaled(0L)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private record Harness(StrategyEngine engine, RecordingStrategy first, RecordingStrategy second) {
    }

    private record ExecutionHarness(
        StrategyEngine engine,
        ExecutionStrategyEngine executionEngine,
        ParentOrderRegistry parentRegistry,
        RecordingExecutionStrategy executionStrategy,
        RecordingStrategy tradingStrategy) {
    }

    static final class RecordingStrategy implements Strategy {
        private final int id;
        private final List<Long> timerIds = new ArrayList<>();
        private final List<StrategyContext> contexts = new ArrayList<>();
        private int killSwitchCount;
        private int killSwitchClearedCount;
        private int marketDataCount;
        private int fillCount;

        RecordingStrategy(final int id) {
            this.id = id;
        }

        @Override public void init(final StrategyContext ctx) { contexts.add(ctx); }
        @Override public void onMarketData(final ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder decoder) { marketDataCount++; }
        @Override public void onFill(final ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder decoder) { fillCount++; }
        @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
        @Override public void onKillSwitch() { killSwitchCount++; }
        @Override public void onKillSwitchCleared() { killSwitchClearedCount++; }
        @Override public void onVenueRecovered(final int venueId) { }
        @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) { }
        @Override public int[] subscribedInstrumentIds() { return new int[0]; }
        @Override public int[] activeVenueIds() { return new int[0]; }
        @Override public void shutdown() { }
        @Override public int strategyId() { return id; }
        @Override public void onTimer(final long correlationId) { timerIds.add(correlationId); }
    }

    private static final class RecordingExecutionStrategy implements ExecutionStrategy {
        private final int id;
        private final List<String> events = new ArrayList<>();

        private RecordingExecutionStrategy(final int id) {
            this.id = id;
        }

        @Override public int executionStrategyId() { return id; }
        @Override public void init(final ExecutionStrategyContext ctx) { }
        @Override public void onParentIntent(final ParentOrderIntentView intent) { }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { events.add("market:" + venueId + ":" + instrumentId + ":" + clusterTimeMicros); }
        @Override public void onChildExecution(final ChildExecutionView execution) { events.add("child:" + execution.childClOrdId() + ":" + execution.parentOrderId()); }
        @Override public void onTimer(final long correlationId) { events.add("timer:" + correlationId); }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { }
    }

    private static final class RiskStub implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final io.aeron.ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final io.aeron.Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class OrderManagerStub implements OrderManager {
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return true; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final io.aeron.ExclusivePublication pub) { }
        @Override public void loadSnapshot(final io.aeron.Image image) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class IdRegistryStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return 1; }
        @Override public int instrumentId(final CharSequence symbol) { return 11; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
