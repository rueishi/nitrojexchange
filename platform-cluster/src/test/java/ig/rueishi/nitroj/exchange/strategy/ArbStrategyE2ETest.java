package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import org.junit.jupiter.api.Test;

/**
 * ET-004 smoke coverage for arbitrage lifecycle outcomes.
 *
 * <p>These tests exercise the same production ArbStrategy used by unit tests
 * but assert full workflow outcomes: opportunity execution, imbalanced hedge,
 * hedge-failure kill switch, and leg-timeout cancellation.</p>
 */
final class ArbStrategyE2ETest {
    @Test
    void arbOpportunity_detected_bothLegsSubmitted() {
        final ArbStrategyTest.Harness harness = ArbStrategyTest.executedHarness();

        assertThat(harness.order().orders).hasSize(2);
        assertThat(harness.strategy().arbActive()).isTrue();
    }

    @Test
    void arbLeg1FillLeg2Cancel_hedgeSubmitted() {
        final ArbStrategyTest.Harness harness = ArbStrategyTest.executedHarness();
        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));

        harness.strategy().onTimer(harness.strategy().activeArbTimeoutCorrelId());

        assertThat(harness.order().orders).extracting(order -> order.strategyId).contains(Ids.STRATEGY_ARB_HEDGE);
    }

    @Test
    void arbHedgeFails_killSwitchActivated() {
        final ArbStrategyTest.Harness harness = ArbStrategyTest.executedHarness();
        harness.risk().decision = ig.rueishi.nitroj.exchange.cluster.RiskDecision.REJECT_KILL_SWITCH;

        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));
        harness.strategy().onTimer(harness.strategy().activeArbTimeoutCorrelId());

        assertThat(harness.risk().killSwitchReason).isEqualTo("hedge_failure");
    }

    @Test
    void arbLegTimeout_pendingLegCanceled_imbalanceHedged() {
        final ArbStrategyTest.Harness harness = ArbStrategyTest.executedHarness();
        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));

        harness.strategy().onTimer(harness.strategy().activeArbTimeoutCorrelId());

        assertThat(harness.strategy().arbActive()).isFalse();
        assertThat(harness.order().orders).extracting(order -> order.strategyId).contains(Ids.STRATEGY_ARB_HEDGE);
    }
}
