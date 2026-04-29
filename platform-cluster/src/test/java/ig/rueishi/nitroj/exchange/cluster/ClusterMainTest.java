package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.ClusterNodeConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.strategy.StrategyContext;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngine;
import io.aeron.cluster.service.Cluster;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ClusterMainTest {
    @Test
    void buildClusteredService_wiresExecutionEngineAndPreservesItAfterClusterInstall() {
        final TradingClusteredService service = ClusterMain.buildClusteredService(
            config(),
            List.of(venue(Ids.VENUE_COINBASE)),
            List.of(new InstrumentConfig(Ids.INSTRUMENT_BTC_USD, "BTC-USD", "BTC", "USD")));
        final StrategyEngine strategyEngine = field(service, "strategyEngine", StrategyEngine.class);

        assertThat(context(strategyEngine, "baseContext").executionEngine()).isNotNull();

        service.installClusterShim(cluster());

        assertThat(context(strategyEngine, "effectiveContext").executionEngine())
            .isSameAs(context(strategyEngine, "baseContext").executionEngine());
    }

    private static ClusterNodeConfig config() {
        return ClusterNodeConfig.builder()
            .nodeId(0)
            .nodeRole("cluster")
            .aeronDir("/dev/shm/aeron-test-" + ProcessHandle.current().pid())
            .archiveDir("./build/test-archive-" + ProcessHandle.current().pid())
            .snapshotDir("./build/test-snapshot-" + ProcessHandle.current().pid())
            .clusterMembers("0,localhost:29010,localhost:29020,localhost:29030,localhost:29040")
            .ingressChannel("aeron:udp?endpoint=localhost:29010")
            .logChannel("aeron:udp?control-mode=manual|alias=test-log")
            .archiveChannel("aeron:udp?endpoint=localhost:29040")
            .snapshotIntervalS(3600)
            .risk(new RiskConfig(
                100,
                1_000_000_000L,
                Map.of(Ids.INSTRUMENT_BTC_USD, new RiskConfig.InstrumentRisk(
                    Ids.INSTRUMENT_BTC_USD,
                    100L * Ids.SCALE,
                    100L * Ids.SCALE,
                    100L * Ids.SCALE,
                    10_000_000L * Ids.SCALE,
                    80))))
            .marketMakingEnabled(false)
            .arbEnabled(false)
            .cpu(ig.rueishi.nitroj.exchange.common.CpuConfig.noPinning())
            .counterFileDir("./build/test-metrics/cluster-" + ProcessHandle.current().pid())
            .histogramOutputMs(5000)
            .maxVenues(Ids.MAX_VENUES)
            .maxInstruments(Ids.MAX_INSTRUMENTS)
            .build();
    }

    private static VenueConfig venue(final int venueId) {
        return new VenueConfig(
            venueId,
            "coinbase",
            "localhost",
            4198,
            true,
            FixPluginId.FIX_44,
            "coinbase",
            MarketDataModel.L2,
            new VenueCapabilities(true, true, true));
    }

    private static Cluster cluster() {
        return (Cluster) Proxy.newProxyInstance(
            Cluster.class.getClassLoader(),
            new Class<?>[]{Cluster.class},
            (ignoredProxy, method, args) -> switch (method.getName()) {
                case "time" -> 123L;
                case "scheduleTimer" -> true;
                case "cancelTimer" -> true;
                case "timeUnit" -> TimeUnit.MICROSECONDS;
                case "role" -> Cluster.Role.LEADER;
                case "memberId" -> 0;
                case "logPosition" -> 0L;
                case "offer", "tryClaim" -> 0L;
                case "closeClientSession" -> true;
                case "toString" -> "ClusterMainTestCluster";
                default -> defaultValue(method.getReturnType());
            });
    }

    private static StrategyContext context(final StrategyEngine strategyEngine, final String fieldName) {
        return field(strategyEngine, fieldName, StrategyContext.class);
    }

    private static <T> T field(final Object target, final String fieldName, final Class<T> type) {
        try {
            final Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
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
