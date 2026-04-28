package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.aeron.cluster.service.Cluster;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
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

    static StrategyContext context(final Cluster cluster) {
        return new StrategyContextImpl(
            new ig.rueishi.nitroj.exchange.cluster.InternalMarketView(),
            null,
            null,
            null,
            null,
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

    private record Harness(StrategyEngine engine, RecordingStrategy first, RecordingStrategy second) {
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
}
