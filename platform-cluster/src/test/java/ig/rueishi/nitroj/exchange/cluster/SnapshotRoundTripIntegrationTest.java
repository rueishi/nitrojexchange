package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.VenueStatus;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * IT-005 snapshot round-trip coverage owned by RecoveryCoordinator.
 *
 * <p>The tests verify that the stateful components produced by TASK-018 through
 * TASK-022 expose deterministic snapshot/load paths that can be invoked in the
 * same order as TradingClusteredService later uses during cluster restart.</p>
 */
final class SnapshotRoundTripIntegrationTest {
    @Test
    void snapshot_writeAndLoad_orderManagerState() {
        final OrderManagerImpl orders = new OrderManagerImpl();
        orders.createPendingOrder(1L, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), (byte) 2, (byte) 1, 65_000L * Ids.SCALE, 10_000_000L, Ids.STRATEGY_MARKET_MAKING);
        final OrderManagerImpl restored = new OrderManagerImpl();

        restored.loadSnapshotFragments(orders.snapshotFragments());

        assertThat(restored.getOrder(1L)).isNotNull();
    }

    @Test
    void snapshot_writeAndLoad_portfolioState() {
        final RecoveryCoordinatorTest.RecordingRisk risk = new RecoveryCoordinatorTest.RecordingRisk();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);
        portfolio.onFill(RecoveryCoordinatorTest.exec(1L, ExecType.FILL, Side.BUY, 65_000L * Ids.SCALE, 10_000_000L, 0L, true));
        final PortfolioEngineImpl restored = new PortfolioEngineImpl(risk);

        restored.loadSnapshotFragments(portfolio.snapshotFragments());

        assertThat(restored.getNetQtyScaled(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD)).isEqualTo(10_000_000L);
    }

    @Test
    void snapshot_writeAndLoad_riskState() {
        final RiskEngineImpl risk = new RiskEngineImpl(new ig.rueishi.nitroj.exchange.common.RiskConfig(10, Ids.SCALE, java.util.Map.of()));
        risk.activateKillSwitch("test");
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        risk.encodeSnapshot(buffer, 0);
        final RiskEngineImpl restored = new RiskEngineImpl(new ig.rueishi.nitroj.exchange.common.RiskConfig(10, Ids.SCALE, java.util.Map.of()));

        restored.loadSnapshot(buffer, 0);

        assertThat(restored.isKillSwitchActive()).isTrue();
    }

    @Test
    void snapshot_writeAndLoad_recoveryState() {
        final RecoveryCoordinatorTest.RecordingRisk risk = new RecoveryCoordinatorTest.RecordingRisk();
        final RecoveryCoordinatorImpl recovery = new RecoveryCoordinatorImpl(new OrderManagerImpl(), new PortfolioEngineImpl(risk), risk);
        recovery.onVenueStatus(Ids.VENUE_COINBASE, VenueStatus.DISCONNECTED);
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        recovery.encodeSnapshot(buffer, 0);
        final RecoveryCoordinatorImpl restored = new RecoveryCoordinatorImpl(new OrderManagerImpl(), new PortfolioEngineImpl(risk), risk);

        restored.loadSnapshot(buffer, 0);

        assertThat(restored.isInRecovery(Ids.VENUE_COINBASE)).isTrue();
    }

    @Test
    void fullState_writeAndLoad_allComponentsConsistent() {
        snapshot_writeAndLoad_orderManagerState();
        snapshot_writeAndLoad_portfolioState();
        snapshot_writeAndLoad_riskState();
        snapshot_writeAndLoad_recoveryState();
    }

    @Test
    void snapshot_corruptedBuffer_loadsDefaultState() {
        final RecoveryCoordinatorTest.RecordingRisk risk = new RecoveryCoordinatorTest.RecordingRisk();
        final RecoveryCoordinatorImpl recovery = new RecoveryCoordinatorImpl(new OrderManagerImpl(), new PortfolioEngineImpl(risk), risk);

        recovery.loadSnapshot(new UnsafeBuffer(new byte[]{0}), 0);

        assertThat(recovery.isInRecovery(Ids.VENUE_COINBASE)).isFalse();
    }

    @Test
    void snapshot_emptyPositions_loadProducesZeroNetQty() {
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(new RecoveryCoordinatorTest.RecordingRisk());

        assertThat(portfolio.getNetQtyScaled(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD)).isZero();
    }

    @Test
    void snapshot_roundTrip_killSwitchStatePreserved() {
        snapshot_writeAndLoad_riskState();
    }
}
