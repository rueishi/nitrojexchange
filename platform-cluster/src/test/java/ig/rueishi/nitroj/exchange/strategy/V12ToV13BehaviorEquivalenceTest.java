package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.MultiLegContingentExecution;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.execution.PostOnlyQuoteExecution;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

/**
 * V12-to-V13 behavior-equivalence fixtures for the migrated built-in strategies.
 *
 * <p>The left side of each assertion is a frozen V12 scenario summary: the child
 * order set, risk decision count, and strategy outcome that the direct child
 * encoder path produced for the same synthetic market data. The right side is
 * the V13 default path, where the trading strategy emits a parent intent and the
 * configured execution strategy produces the child-order summary. Client order
 * IDs and byte-for-byte SBE streams are intentionally not part of the fixture
 * because V13 adds parent events and may derive IDs from parent correlation.</p>
 */
final class V12ToV13BehaviorEquivalenceTest {
    @Test
    void marketMakingDefaultPostOnlyExecution_matchesV12QuoteFixture() {
        final MmProductionHarness harness = marketMakingProductionHarness();
        harness.cluster.logPosition = 10_000L;

        MarketMakingStrategyTest.quote(
            harness.testHarness,
            99_900L * Ids.SCALE,
            100_100L * Ids.SCALE);

        assertThat(marketMakingSummary(harness.order.orders))
            .containsExactly(
                new QuoteChild(Side.BUY, 99_950L * Ids.SCALE, 10L * Ids.SCALE),
                new QuoteChild(Side.SELL, 100_050L * Ids.SCALE, 10L * Ids.SCALE));
        assertThat(harness.strategy.liveBidClOrdId()).isPositive();
        assertThat(harness.strategy.liveAskClOrdId()).isPositive();
        assertThat(harness.parentRegistry.lookup(harness.strategy.liveBidClOrdId())).isNotNull();
        assertThat(harness.parentRegistry.lookup(harness.strategy.liveAskClOrdId())).isNotNull();
        assertThat(harness.risk.isKillSwitchActive()).isFalse();
    }

    @Test
    void arbDefaultMultiLegExecution_matchesV12ArbFixture() {
        final ArbProductionHarness harness = arbProductionHarness();
        harness.cluster.logPosition = 20_000L;
        ArbStrategyTest.seedOpportunity(harness.testHarness);

        ArbStrategyTest.trigger(harness.testHarness);

        assertThat(arbSummary(harness.order.orders))
            .containsExactly(
                new ArbChild(Ids.VENUE_COINBASE, Side.BUY, TimeInForce.IOC, Ids.STRATEGY_ARB, Ids.SCALE),
                new ArbChild(Ids.VENUE_COINBASE_SANDBOX, Side.SELL, TimeInForce.IOC, Ids.STRATEGY_ARB, Ids.SCALE));
        assertThat(harness.risk.preTradeChecks).isEqualTo(2);
        assertThat(harness.risk.killSwitchReason).isNull();
        assertThat(harness.strategy.arbActive()).isTrue();
        assertThat(harness.strategy.activeArbTimeoutCorrelId()).isZero();
        assertThat(harness.timerScheduler.scheduledCorrelationId).isPositive();
    }

    @Test
    void arbDefaultMultiLegExecution_matchesV12RiskRejectFixture() {
        final ArbProductionHarness harness = arbProductionHarness();
        harness.risk.decision = ig.rueishi.nitroj.exchange.cluster.RiskDecision.REJECT_ORDER_TOO_LARGE;
        ArbStrategyTest.seedOpportunity(harness.testHarness);

        ArbStrategyTest.trigger(harness.testHarness);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.risk.preTradeChecks).isEqualTo(1);
        assertThat(harness.parentRegistry.lookup(harness.strategy.arbAttemptId()).status())
            .isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.parentRegistry.lookup(harness.strategy.arbAttemptId()).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_RISK_REJECTED);
        harness.strategy.onParentTerminal(parentTerminal(
            harness.strategy.arbAttemptId(),
            Ids.STRATEGY_ARB,
            ExecutionStrategyIds.MULTI_LEG_CONTINGENT,
            ParentTerminalReason.RISK_REJECTED));
        assertThat(harness.strategy.arbActive()).isFalse();
        assertThat(harness.strategy.cooldownUntilMicros()).isGreaterThan(harness.cluster.time);
        assertThat(harness.risk.killSwitchReason).isNull();
    }

    private static List<QuoteChild> marketMakingSummary(final List<MarketMakingStrategyTest.OrderRecord> orders) {
        return orders.stream()
            .map(order -> new QuoteChild(order.side, order.priceScaled, order.qtyScaled))
            .toList();
    }

    private static List<ArbChild> arbSummary(final List<ArbStrategyTest.OrderRecord> orders) {
        return orders.stream()
            .map(order -> new ArbChild(order.venueId, order.side, order.timeInForce, order.strategyId, order.qtyScaled))
            .toList();
    }

    private static MmProductionHarness marketMakingProductionHarness() {
        final InternalMarketView marketView = new InternalMarketView();
        final MarketMakingStrategyTest.RecordingRisk risk = new MarketMakingStrategyTest.RecordingRisk();
        final MarketMakingStrategyTest.RecordingOrder order = new MarketMakingStrategyTest.RecordingOrder();
        final MarketMakingStrategyTest.RecordingPortfolio portfolio = new MarketMakingStrategyTest.RecordingPortfolio();
        final MarketMakingStrategyTest.RecordingRecovery recovery = new MarketMakingStrategyTest.RecordingRecovery();
        final MarketMakingStrategyTest.RecordingCluster cluster = new MarketMakingStrategyTest.RecordingCluster();
        final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(16, 16);
        final ExecutionStrategyRegistry executionRegistry = new ExecutionStrategyRegistry(8, 8);
        executionRegistry.register(new PostOnlyQuoteExecution());
        executionRegistry.allowCompatibility(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.POST_ONLY_QUOTE);
        final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(
            executionRegistry,
            context(marketView, risk, order, parentRegistry, () -> cluster.time, (correlationId, deadlineClusterMicros) -> true));
        executionEngine.initRegisteredStrategies();
        final MarketMakingStrategy strategy = new MarketMakingStrategy(MarketMakingStrategyTest.config());
        strategy.init(new StrategyContextImpl(
            marketView, risk, order, portfolio, recovery, executionEngine, cluster.proxy,
            new UnsafeBuffer(new byte[512]), new MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(), null, null));
        final MarketMakingStrategyTest.Harness testHarness =
            new MarketMakingStrategyTest.Harness(strategy, marketView, risk, order, portfolio, recovery, cluster, null);
        return new MmProductionHarness(testHarness, strategy, risk, order, cluster, parentRegistry);
    }

    private static ArbProductionHarness arbProductionHarness() {
        final InternalMarketView marketView = new InternalMarketView();
        final ArbStrategyTest.RecordingRisk risk = new ArbStrategyTest.RecordingRisk();
        final ArbStrategyTest.RecordingOrder order = new ArbStrategyTest.RecordingOrder();
        final ArbStrategyTest.RecordingCluster cluster = new ArbStrategyTest.RecordingCluster();
        final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(16, 16);
        final RecordingTimerScheduler timerScheduler = new RecordingTimerScheduler();
        final ExecutionStrategyRegistry executionRegistry = new ExecutionStrategyRegistry(8, 8);
        executionRegistry.register(new MultiLegContingentExecution());
        executionRegistry.allowCompatibility(Ids.STRATEGY_ARB, ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(
            executionRegistry,
            context(marketView, risk, order, parentRegistry, () -> cluster.time, timerScheduler));
        executionEngine.initRegisteredStrategies();
        final ArbStrategy strategy = new ArbStrategy(ArbStrategyTest.config(1, 0L));
        strategy.init(new StrategyContextImpl(
            marketView, risk, order, new ArbStrategyTest.RecordingPortfolio(), new ArbStrategyTest.RecordingRecovery(),
            executionEngine, cluster.proxy,
            new UnsafeBuffer(new byte[1024]), new MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(), null, null));
        final ArbStrategyTest.Harness testHarness =
            new ArbStrategyTest.Harness(strategy, marketView, risk, order, cluster);
        return new ArbProductionHarness(testHarness, strategy, risk, order, cluster, parentRegistry, timerScheduler);
    }

    private static ExecutionStrategyContext context(
        final InternalMarketView marketView,
        final ig.rueishi.nitroj.exchange.cluster.RiskEngine risk,
        final ig.rueishi.nitroj.exchange.order.OrderManager order,
        final ParentOrderRegistry parentRegistry,
        final ExecutionStrategyContext.DeterministicClock clock,
        final ExecutionStrategyContext.TimerScheduler timerScheduler
    ) {
        return new ExecutionStrategyContext(
            marketView,
            risk,
            order,
            parentRegistry,
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(),
            clock,
            timerScheduler,
            MarketMakingStrategyTest.nullIdRegistry(),
            new CountersManager(
                new UnsafeBuffer(new byte[1024 * 1024]),
                new UnsafeBuffer(new byte[64 * 1024])));
    }

    private static ParentOrderTerminalDecoder parentTerminal(
        final long parentOrderId,
        final int strategyId,
        final int executionStrategyId,
        final ParentTerminalReason reason
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new ParentOrderTerminalEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) strategyId)
            .executionStrategyId(executionStrategyId)
            .finalCumFillQtyScaled(0L)
            .terminalReason(reason);
        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private record MmProductionHarness(
        MarketMakingStrategyTest.Harness testHarness,
        MarketMakingStrategy strategy,
        MarketMakingStrategyTest.RecordingRisk risk,
        MarketMakingStrategyTest.RecordingOrder order,
        MarketMakingStrategyTest.RecordingCluster cluster,
        ParentOrderRegistry parentRegistry
    ) { }

    private record ArbProductionHarness(
        ArbStrategyTest.Harness testHarness,
        ArbStrategy strategy,
        ArbStrategyTest.RecordingRisk risk,
        ArbStrategyTest.RecordingOrder order,
        ArbStrategyTest.RecordingCluster cluster,
        ParentOrderRegistry parentRegistry,
        RecordingTimerScheduler timerScheduler
    ) { }

    private static final class RecordingTimerScheduler implements ExecutionStrategyContext.TimerScheduler {
        long scheduledCorrelationId;
        long scheduledDeadlineClusterMicros;

        @Override
        public boolean scheduleTimer(final long correlationId, final long deadlineClusterMicros) {
            scheduledCorrelationId = correlationId;
            scheduledDeadlineClusterMicros = deadlineClusterMicros;
            return true;
        }
    }

    private record QuoteChild(Side side, long priceScaled, long qtyScaled) { }

    private record ArbChild(int venueId, Side side, TimeInForce timeInForce, int strategyId, long qtyScaled) { }
}
