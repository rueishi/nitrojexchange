package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.ExternalLiquidityView;
import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.OwnOrderOverlay;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;

import java.util.Objects;

/**
 * Deterministic dependency surface for execution strategies.
 *
 * <p>The context exposes the state owners and reusable SBE command resources an
 * execution strategy may use on the cluster thread. Construction is a startup
 * concern and rejects missing dependencies early. Accessors are allocation-free
 * reference/primitive reads; strategies must use {@link #clock()} and
 * {@link #timerScheduler()} for time and timers so replay stays deterministic.</p>
 */
public final class ExecutionStrategyContext {
    private final InternalMarketView marketView;
    private final ExternalLiquidityView externalLiquidityView;
    private final OwnOrderOverlay ownOrderOverlay;
    private final RiskEngine riskEngine;
    private final OrderManager orderManager;
    private final ParentOrderRegistry parentOrderRegistry;
    private final UnsafeBuffer commandBuffer;
    private final MessageHeaderEncoder headerEncoder;
    private final NewOrderCommandEncoder newOrderEncoder;
    private final CancelOrderCommandEncoder cancelOrderEncoder;
    private final DeterministicClock clock;
    private final TimerScheduler timerScheduler;
    private final IdRegistry idRegistry;
    private final CountersManager counters;

    public ExecutionStrategyContext(
        final InternalMarketView marketView,
        final RiskEngine riskEngine,
        final OrderManager orderManager,
        final ParentOrderRegistry parentOrderRegistry,
        final UnsafeBuffer commandBuffer,
        final MessageHeaderEncoder headerEncoder,
        final NewOrderCommandEncoder newOrderEncoder,
        final CancelOrderCommandEncoder cancelOrderEncoder,
        final DeterministicClock clock,
        final TimerScheduler timerScheduler,
        final IdRegistry idRegistry,
        final CountersManager counters) {
        this.marketView = Objects.requireNonNull(marketView, "marketView");
        this.externalLiquidityView = this.marketView.externalLiquidityView();
        this.ownOrderOverlay = this.marketView.ownOrderOverlay();
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
        this.orderManager = Objects.requireNonNull(orderManager, "orderManager");
        this.parentOrderRegistry = Objects.requireNonNull(parentOrderRegistry, "parentOrderRegistry");
        this.commandBuffer = Objects.requireNonNull(commandBuffer, "commandBuffer");
        this.headerEncoder = Objects.requireNonNull(headerEncoder, "headerEncoder");
        this.newOrderEncoder = Objects.requireNonNull(newOrderEncoder, "newOrderEncoder");
        this.cancelOrderEncoder = Objects.requireNonNull(cancelOrderEncoder, "cancelOrderEncoder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timerScheduler = Objects.requireNonNull(timerScheduler, "timerScheduler");
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.counters = Objects.requireNonNull(counters, "counters");
    }

    public InternalMarketView marketView() {
        return marketView;
    }

    public ExternalLiquidityView externalLiquidityView() {
        return externalLiquidityView;
    }

    public OwnOrderOverlay ownOrderOverlay() {
        return ownOrderOverlay;
    }

    public RiskEngine riskEngine() {
        return riskEngine;
    }

    public OrderManager orderManager() {
        return orderManager;
    }

    public ParentOrderRegistry parentOrderRegistry() {
        return parentOrderRegistry;
    }

    public UnsafeBuffer commandBuffer() {
        return commandBuffer;
    }

    public MessageHeaderEncoder headerEncoder() {
        return headerEncoder;
    }

    public NewOrderCommandEncoder newOrderEncoder() {
        return newOrderEncoder;
    }

    public CancelOrderCommandEncoder cancelOrderEncoder() {
        return cancelOrderEncoder;
    }

    public DeterministicClock clock() {
        return clock;
    }

    public TimerScheduler timerScheduler() {
        return timerScheduler;
    }

    public IdRegistry idRegistry() {
        return idRegistry;
    }

    public CountersManager counters() {
        return counters;
    }

    @FunctionalInterface
    public interface DeterministicClock {
        long clusterTimeMicros();
    }

    public interface TimerScheduler {
        boolean scheduleTimer(long correlationId, long deadlineClusterMicros);

        default boolean scheduleTimer(
            final long correlationId,
            final long deadlineClusterMicros,
            final int executionStrategyId) {
            return scheduleTimer(correlationId, deadlineClusterMicros);
        }
    }
}
