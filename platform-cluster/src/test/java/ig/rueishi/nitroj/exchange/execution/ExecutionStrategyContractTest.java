package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ExecutionStrategyContractTest {
    @Test
    void contextExposesDeterministicDependenciesClockAndTimer() {
        final AtomicLong scheduledCorrelation = new AtomicLong();
        final AtomicLong scheduledDeadline = new AtomicLong();
        final ExecutionStrategyContext ctx = context(
            () -> 123_456L,
            (correlationId, deadlineClusterMicros) -> {
                scheduledCorrelation.set(correlationId);
                scheduledDeadline.set(deadlineClusterMicros);
                return true;
            });

        assertThat(ctx.marketView()).isNotNull();
        assertThat(ctx.externalLiquidityView()).isSameAs(ctx.marketView().externalLiquidityView());
        assertThat(ctx.ownOrderOverlay()).isSameAs(ctx.marketView().ownOrderOverlay());
        assertThat(ctx.parentOrderRegistry()).isNotNull();
        assertThat(ctx.commandBuffer()).isNotNull();
        assertThat(ctx.clock().clusterTimeMicros()).isEqualTo(123_456L);
        assertThat(ctx.timerScheduler().scheduleTimer(44L, 55L)).isTrue();
        assertThat(scheduledCorrelation).hasValue(44L);
        assertThat(scheduledDeadline).hasValue(55L);
    }

    @Test
    void contextRejectsNullDependenciesAtStartupBoundary() {
        assertThatThrownBy(() -> new ExecutionStrategyContext(
            null,
            new RiskStub(),
            new OrderManagerStub(),
            new ParentOrderRegistry(2, 2),
            new UnsafeBuffer(new byte[128]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            () -> 1L,
            (correlationId, deadlineClusterMicros) -> true,
            new IdRegistryStub(),
            counters()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("marketView");

        assertThatThrownBy(() -> new ExecutionStrategyContext(
            new InternalMarketView(),
            new RiskStub(),
            new OrderManagerStub(),
            new ParentOrderRegistry(2, 2),
            new UnsafeBuffer(new byte[128]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            null,
            (correlationId, deadlineClusterMicros) -> true,
            new IdRegistryStub(),
            counters()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("clock");
    }

    @Test
    void executionStrategyLifecycleCanInitializeAndReceiveCallbacks() {
        final RecordingExecutionStrategy strategy = new RecordingExecutionStrategy();
        final ExecutionStrategyContext ctx = context(() -> 10L, (correlationId, deadlineClusterMicros) -> true);

        strategy.init(ctx);
        strategy.onMarketDataTick(1, 11, 10L);
        strategy.onTimer(77L);
        strategy.onCancel(9001L, ParentOrderState.REASON_CANCELED_BY_PARENT);

        assertThat(strategy.executionStrategyId()).isEqualTo(42);
        assertThat(strategy.ctx).isSameAs(ctx);
        assertThat(strategy.lastVenueId).isEqualTo(1);
        assertThat(strategy.lastInstrumentId).isEqualTo(11);
        assertThat(strategy.lastTimerCorrelationId).isEqualTo(77L);
        assertThat(strategy.lastCanceledParentId).isEqualTo(9001L);
    }

    @Test
    void parentIntentViewReusesOneFlyweightForDifferentDecoders() {
        final ParentOrderIntentDecoder first = parentIntentDecoder(9001L, 11);
        final ParentOrderIntentDecoder second = parentIntentDecoder(9002L, 12);
        final ParentOrderIntentView view = new ParentOrderIntentView();

        assertThat(view.wrap(first)).isSameAs(view);
        assertThat(view.parentOrderId()).isEqualTo(9001L);
        assertThat(view.executionStrategyId()).isEqualTo(11);
        assertThat(view.intentType()).isEqualTo(ParentIntentType.IMMEDIATE_LIMIT);
        assertThat(view.priceMode()).isEqualTo(PriceMode.LIMIT);

        assertThat(view.wrap(second)).isSameAs(view);
        assertThat(view.parentOrderId()).isEqualTo(9002L);
        assertThat(view.executionStrategyId()).isEqualTo(12);
    }

    @Test
    void childExecutionViewExposesDeterministicPrimitiveFields() {
        final ExecutionEventDecoder decoder = childExecutionDecoder();
        final ChildExecutionView view = new ChildExecutionView();

        assertThat(view.wrap(decoder, 9001L)).isSameAs(view);
        assertThat(view.parentOrderId()).isEqualTo(9001L);
        assertThat(view.childClOrdId()).isEqualTo(1001L);
        assertThat(view.venueId()).isEqualTo(1);
        assertThat(view.instrumentId()).isEqualTo(11);
        assertThat(view.execType()).isEqualTo(ExecType.PARTIAL_FILL);
        assertThat(view.side()).isEqualTo(Side.BUY);
        assertThat(view.fillPriceScaled()).isEqualTo(65_000L);
        assertThat(view.fillQtyScaled()).isEqualTo(10L);
        assertThat(view.cumQtyScaled()).isEqualTo(10L);
        assertThat(view.leavesQtyScaled()).isEqualTo(90L);
        assertThat(view.rejectCode()).isZero();
        assertThat(view.finalExecution()).isFalse();
    }

    @Test
    void flyweightViewsRejectNullDecoders() {
        assertThatThrownBy(() -> new ParentOrderIntentView().wrap(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("decoder");
        assertThatThrownBy(() -> new ChildExecutionView().wrap(null, 1L))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("decoder");
    }

    @Test
    void flyweightViewsExposeNoStringReturningHotPathAccessors() {
        assertThat(Arrays.stream(ParentOrderIntentView.class.getDeclaredMethods())
            .filter(method -> method.getReturnType() == String.class))
            .isEmpty();
        assertThat(Arrays.stream(ChildExecutionView.class.getDeclaredMethods())
            .filter(method -> method.getReturnType() == String.class))
            .isEmpty();
    }

    private static ParentOrderIntentDecoder parentIntentDecoder(final long parentOrderId, final int executionStrategyId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short)7)
            .executionStrategyId(executionStrategyId)
            .intentType(ParentIntentType.IMMEDIATE_LIMIT)
            .side(Side.BUY)
            .instrumentId(11)
            .primaryVenueId(1)
            .secondaryVenueId(0)
            .quantityScaled(100L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(65_000L)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte)1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte)0)
            .correlationId(44L)
            .legCount((byte)1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ExecutionEventDecoder childExecutionDecoder() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1001L)
            .venueId(1)
            .instrumentId(11)
            .execType(ExecType.PARTIAL_FILL)
            .side(Side.BUY)
            .fillPriceScaled(65_000L)
            .fillQtyScaled(10L)
            .cumQtyScaled(10L)
            .leavesQtyScaled(90L)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(BooleanType.FALSE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ExecutionStrategyContext context(
        final ExecutionStrategyContext.DeterministicClock clock,
        final ExecutionStrategyContext.TimerScheduler timerScheduler) {
        return new ExecutionStrategyContext(
            new InternalMarketView(),
            new RiskStub(),
            new OrderManagerStub(),
            new ParentOrderRegistry(4, 8),
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            clock,
            timerScheduler,
            new IdRegistryStub(),
            counters());
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class RecordingExecutionStrategy implements ExecutionStrategy {
        ExecutionStrategyContext ctx;
        int lastVenueId;
        int lastInstrumentId;
        long lastTimerCorrelationId;
        long lastCanceledParentId;

        @Override
        public int executionStrategyId() {
            return 42;
        }

        @Override
        public void init(final ExecutionStrategyContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onParentIntent(final ParentOrderIntentView intent) {
        }

        @Override
        public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
            lastVenueId = venueId;
            lastInstrumentId = instrumentId;
        }

        @Override
        public void onChildExecution(final ChildExecutionView execution) {
        }

        @Override
        public void onTimer(final long correlationId) {
            lastTimerCorrelationId = correlationId;
        }

        @Override
        public void onCancel(final long parentOrderId, final byte reasonCode) {
            lastCanceledParentId = parentOrderId;
        }
    }

    private static final class RiskStub implements RiskEngine {
        @Override
        public RiskDecision preTradeCheck(
            final int venueId,
            final int instrumentId,
            final byte side,
            final long priceScaled,
            final long qtyScaled,
            final int strategyId) {
            return RiskDecision.APPROVED;
        }

        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
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
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
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
