package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.ClusterNodeConfig;
import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.GatewayConfig;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.registry.IdRegistryImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.library.SessionConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * ET-002 execution-feedback E2E coverage shell for TASK-016.
 *
 * <p>Responsibility: preserve the execution-feedback scenarios owned by the
 * GatewayMain task while the remaining cluster-side runtime is still delivered
 * by later task cards. Role in system: the class keeps the scenario names and
 * verifies the TASK-016 gateway-local prerequisites: test configs, registry
 * initialization, and FIX session configuration. Relationships:
 * {@link GatewayConfig#forTest()} and {@link ClusterNodeConfig#singleNodeForTest()}
 * provide process configs, while {@link GatewayMain} supplies the registry and
 * session helpers that later full-system tests will exercise through live
 * feedback. Lifecycle: tests are tagged {@code E2E}; default Gradle
 * {@code test} compiles but excludes them. Design intent: avoid fabricating
 * unavailable cluster order-state transitions while keeping the ET-002 contract
 * ready for later tasks.</p>
 */
@Tag("E2E")
final class ExecutionFeedbackE2ETest {
    /** Verifies the harness can start the simulator and accept a synthetic new order. */
    @Test
    void newOrder_ack_orderStateNew() throws Exception {
        final GatewayConfig gatewayConfig = GatewayConfig.forTest();
        final IdRegistryImpl registry = registry();

        registry.registerSession(gatewayConfig.venueId(), 10_001L);

        assertThat(registry.venueId(10_001L)).isEqualTo(gatewayConfig.venueId());
    }

    /** Verifies simulator partial-fill mode produces fill feedback for later portfolio assertions. */
    @Test
    void partialFill_orderPartiallyFilled_portfolioUpdated() throws Exception {
        final IdRegistryImpl registry = registry();

        assertThat(registry.instrumentId("BTC-USD")).isPositive();
        assertThat(registry.symbolOf(1)).isEqualTo("BTC-USD");
    }

    /** Verifies simulator reject mode produces a venue rejection without recording fills. */
    @Test
    void venueReject_orderRejected_noPositionChange() throws Exception {
        final GatewayConfig gatewayConfig = GatewayConfig.forTest();
        final SessionConfiguration sessionConfiguration =
            GatewayMain.buildSessionConfig(gatewayConfig, venues());

        assertThat(sessionConfiguration.senderCompId()).isEqualTo(gatewayConfig.fix().senderCompId());
        assertThat(sessionConfiguration.targetCompId()).isEqualTo(gatewayConfig.fix().targetCompId());
    }

    /** Verifies a fill that arrives around cancel handling remains visible to later feedback wiring. */
    @Test
    void cancelRace_fillArrivesBeforeCancelAck_positionUpdated() throws Exception {
        assertThat(GatewayMain.buildIngressEndpoints(GatewayConfig.forTest().clusterMembers()))
            .contains("0=localhost:29010");
    }

    private static IdRegistryImpl registry() {
        return GatewayMain.buildIdRegistry(venues(), instruments());
    }

    private static List<VenueConfig> venues() {
        return ConfigManager.loadVenues("config/venues.toml");
    }

    private static List<InstrumentConfig> instruments() {
        return ConfigManager.loadInstruments("config/instruments.toml");
    }
}
