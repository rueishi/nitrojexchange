package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * IT-003 risk gate integration checks for recovery and fill edge cases.
 *
 * <p>This integration layer is intentionally narrow: it verifies the risk engine
 * decisions that MessageRouter consumers depend on, while the fan-out behavior
 * itself is covered by MessageRouterTest.</p>
 */
final class RiskGateIntegrationTest {
    @Test
    void recoveryLock_onlyAffectedVenue_ordersForOtherVenueAllowed() {
        final RiskEngineImpl risk = riskEngine();
        risk.setVenueConnected(Ids.VENUE_COINBASE, true);
        risk.setVenueConnected(Ids.VENUE_COINBASE_SANDBOX, true);
        risk.setRecoveryLock(Ids.VENUE_COINBASE, true);

        assertThat(risk.preTradeCheck(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), 65_000L * Ids.SCALE, 1L, 1)).isSameAs(RiskDecision.REJECT_RECOVERY);
        assertThat(risk.preTradeCheck(Ids.VENUE_COINBASE_SANDBOX, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), 65_000L * Ids.SCALE, 1L, 1)).isSameAs(RiskDecision.APPROVED);
    }

    @Test
    void riskReject_doesNotUpdatePortfolio() {
        final RiskEngineImpl risk = riskEngine();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);

        final RiskDecision decision = risk.preTradeCheck(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), 65_000L * Ids.SCALE, 10_000_000_000L, 1);

        assertThat(decision.approved()).isFalse();
        assertThat(portfolio.getNetQtyScaled(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD)).isZero();
    }

    @Test
    void fillWithKillSwitchActive_orderStillApplied() {
        final RiskEngineImpl risk = riskEngine();
        risk.activateKillSwitch("test");
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);
        final OrderManagerImpl orders = new OrderManagerImpl();
        orders.createPendingOrder(1L, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), 65_000L * Ids.SCALE, 10_000_000L, 1);
        orders.onExecution(RecoveryCoordinatorTest.exec(1L, ExecType.NEW, Side.BUY, 0L, 0L, 10_000_000L, false));

        if (orders.onExecution(RecoveryCoordinatorTest.exec(1L, ExecType.FILL, Side.BUY, 65_000L * Ids.SCALE, 10_000_000L, 0L, true))) {
            portfolio.onFill(RecoveryCoordinatorTest.exec(1L, ExecType.FILL, Side.BUY, 65_000L * Ids.SCALE, 10_000_000L, 0L, true));
        }

        assertThat(portfolio.getNetQtyScaled(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD)).isEqualTo(10_000_000L);
    }

    private static RiskEngineImpl riskEngine() {
        return new RiskEngineImpl(
            new RiskConfig(
                100,
                1_000L * Ids.SCALE,
                Map.of(
                    Ids.INSTRUMENT_BTC_USD,
                    new RiskConfig.InstrumentRisk(Ids.INSTRUMENT_BTC_USD, 10L * Ids.SCALE, 100L * Ids.SCALE, 100L * Ids.SCALE, 1_000_000L * Ids.SCALE, 80)
                )
            )
        );
    }
}
