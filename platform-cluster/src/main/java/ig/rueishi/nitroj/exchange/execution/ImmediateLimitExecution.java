package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;

import java.util.Objects;

/**
 * One-child IOC/limit execution strategy for V13 parent intents.
 *
 * <p>The strategy is intentionally small: a valid parent intent becomes one
 * child order after pre-trade risk approval. Parent lifecycle state is stored in
 * {@link ParentOrderRegistry}; child lifecycle remains owned by the
 * {@code OrderManager}. Parent ID {@code 0}, non-positive quantity, unsupported
 * intent type, and parent/child capacity failures terminate before child state
 * is created.</p>
 *
 * <p>All callbacks run on the cluster thread. The implementation mutates only
 * primitive fields, reuses the SBE encoders from {@link ExecutionStrategyContext},
 * and treats timers as deterministic no-ops because immediate execution has no
 * strategy-owned timer lifecycle.</p>
 */
public final class ImmediateLimitExecution implements ExecutionStrategy {
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final long[] oneChildScratch = new long[1];

    private ExecutionStrategyContext ctx;
    private long parentIntents;
    private long childAccepted;
    private long childPartiallyFilled;
    private long childTerminal;
    private long riskRejects;
    private long capacityRejects;
    private long cancelRequests;
    private long timerCallbacks;
    private long malformedRejects;

    @Override
    public int executionStrategyId() {
        return ExecutionStrategyIds.IMMEDIATE_LIMIT;
    }

    @Override
    public void init(final ExecutionStrategyContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void onParentIntent(final ParentOrderIntentView intent) {
        requireInitialized();
        parentIntents++;

        if (intent.parentOrderId() <= 0L || intent.quantityScaled() <= 0L
            || intent.intentType() != ParentIntentType.IMMEDIATE_LIMIT) {
            malformedRejects++;
            return;
        }

        final ParentOrderState parent = ctx.parentOrderRegistry().claim(
            intent.parentOrderId(),
            intent.strategyId(),
            executionStrategyId(),
            intent.quantityScaled(),
            ctx.clock().clusterTimeMicros());
        if (parent == null) {
            capacityRejects++;
            return;
        }

        final long priceScaled = executionPrice(intent);
        final RiskDecision risk = ctx.riskEngine().preTradeCheck(
            intent.primaryVenueId(),
            intent.instrumentId(),
            intent.side().value(),
            priceScaled,
            intent.quantityScaled(),
            intent.strategyId());
        if (!risk.approved()) {
            riskRejects++;
            ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.FAILED,
                ParentOrderState.REASON_RISK_REJECTED, ctx.clock().clusterTimeMicros());
            return;
        }

        final long childClOrdId = childClOrdId(intent);
        if (!ctx.parentOrderRegistry().linkChild(intent.parentOrderId(), childClOrdId)) {
            capacityRejects++;
            ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.FAILED,
                ParentOrderState.REASON_CAPACITY_REJECTED, ctx.clock().clusterTimeMicros());
            return;
        }

        ctx.orderManager().createPendingOrder(
            childClOrdId,
            intent.primaryVenueId(),
            intent.instrumentId(),
            intent.side().value(),
            OrdType.LIMIT.value(),
            timeInForce(intent).value(),
            priceScaled,
            intent.quantityScaled(),
            intent.strategyId(),
            intent.parentOrderId());
        encodeNewOrder(intent, childClOrdId, priceScaled);
        ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.WORKING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    @Override
    public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
        // ImmediateLimitExecution has no market-data-driven refresh behavior.
    }

    @Override
    public void onChildExecution(final ChildExecutionView execution) {
        requireInitialized();
        final long parentOrderId = execution.parentOrderId();
        if (ctx.parentOrderRegistry().lookup(parentOrderId) == null) {
            return;
        }

        final ExecType execType = execution.execType();
        switch (execType) {
            case NEW -> {
                childAccepted++;
                ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.WORKING,
                    ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
            }
            case FILL, PARTIAL_FILL -> onFill(execution);
            case REJECTED -> failTerminal(execution, ParentOrderState.REASON_CHILD_REJECTED);
            case CANCELED -> cancelTerminal(execution);
            case EXPIRED -> expireTerminal(execution);
            default -> {
                if (execution.finalExecution()) {
                    failTerminal(execution, ParentOrderState.REASON_EXECUTION_ABORTED);
                }
            }
        }
    }

    @Override
    public void onTimer(final long correlationId) {
        timerCallbacks++;
    }

    @Override
    public void onCancel(final long parentOrderId, final byte reasonCode) {
        requireInitialized();
        cancelRequests++;
        final ParentOrderState parent = ctx.parentOrderRegistry().lookup(parentOrderId);
        if (parent == null || parent.terminal()) {
            return;
        }
        final int childCount = ctx.parentOrderRegistry().copyActiveChildIds(parentOrderId, oneChildScratch);
        if (childCount == 0) {
            ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.CANCELED,
                ParentOrderState.REASON_CANCELED_BY_PARENT, ctx.clock().clusterTimeMicros());
            return;
        }
        final long childClOrdId = oneChildScratch[0];
        final var child = ctx.orderManager().getOrder(childClOrdId);
        if (child != null) {
            encodeCancel(childClOrdId, child.venueId(), child.instrumentId(), child.side(), child.qtyScaled());
            ctx.orderManager().markCancelSent(childClOrdId);
        }
        ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.CANCEL_PENDING,
            reasonCode, ctx.clock().clusterTimeMicros());
    }

    public long parentIntents() {
        return parentIntents;
    }

    public long childAccepted() {
        return childAccepted;
    }

    public long childPartiallyFilled() {
        return childPartiallyFilled;
    }

    public long childTerminal() {
        return childTerminal;
    }

    public long riskRejects() {
        return riskRejects;
    }

    public long capacityRejects() {
        return capacityRejects;
    }

    public long cancelRequests() {
        return cancelRequests;
    }

    public long timerCallbacks() {
        return timerCallbacks;
    }

    public long malformedRejects() {
        return malformedRejects;
    }

    private void onFill(final ChildExecutionView execution) {
        ctx.parentOrderRegistry().updateFill(
            execution.parentOrderId(),
            execution.cumQtyScaled(),
            execution.leavesQtyScaled(),
            execution.fillPriceScaled());
        if (execution.finalExecution() || execution.leavesQtyScaled() == 0L) {
            childTerminal++;
            ctx.parentOrderRegistry().transition(execution.parentOrderId(), ParentOrderState.DONE,
                ParentOrderState.REASON_COMPLETED, ctx.clock().clusterTimeMicros());
            ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
        } else {
            childPartiallyFilled++;
            ctx.parentOrderRegistry().transition(execution.parentOrderId(), ParentOrderState.PARTIALLY_FILLED,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
        }
    }

    private void failTerminal(final ChildExecutionView execution, final byte reasonCode) {
        childTerminal++;
        ctx.parentOrderRegistry().transition(execution.parentOrderId(), ParentOrderState.FAILED,
            reasonCode, ctx.clock().clusterTimeMicros());
        ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
    }

    private void cancelTerminal(final ChildExecutionView execution) {
        childTerminal++;
        ctx.parentOrderRegistry().transition(execution.parentOrderId(), ParentOrderState.CANCELED,
            ParentOrderState.REASON_CANCELED_BY_PARENT, ctx.clock().clusterTimeMicros());
        ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
    }

    private void expireTerminal(final ChildExecutionView execution) {
        childTerminal++;
        ctx.parentOrderRegistry().transition(execution.parentOrderId(), ParentOrderState.EXPIRED,
            ParentOrderState.REASON_EXPIRED, ctx.clock().clusterTimeMicros());
        ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
    }

    private void encodeNewOrder(
        final ParentOrderIntentView intent,
        final long childClOrdId,
        final long priceScaled
    ) {
        final NewOrderCommandEncoder encoder = ctx.newOrderEncoder();
        encoder
            .wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .clOrdId(childClOrdId)
            .venueId(intent.primaryVenueId())
            .instrumentId(intent.instrumentId())
            .side(intent.side())
            .ordType(OrdType.LIMIT)
            .timeInForce(timeInForce(intent))
            .priceScaled(priceScaled)
            .qtyScaled(intent.quantityScaled())
            .strategyId((short) intent.strategyId())
            .parentOrderId(intent.parentOrderId());
    }

    private void encodeCancel(
        final long childClOrdId,
        final int venueId,
        final int instrumentId,
        final byte side,
        final long originalQtyScaled
    ) {
        final CancelOrderCommandEncoder encoder = ctx.cancelOrderEncoder();
        encoder
            .wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .cancelClOrdId(childClOrdId + 1L)
            .origClOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .side(Side.get(side))
            .originalQtyScaled(originalQtyScaled)
            .putVenueOrderId(EMPTY_BYTES, 0, 0);
    }

    private static long executionPrice(final ParentOrderIntentView intent) {
        return intent.priceMode() == PriceMode.REFERENCE ? intent.referencePriceScaled() : intent.limitPriceScaled();
    }

    private static long childClOrdId(final ParentOrderIntentView intent) {
        return intent.correlationId() > 0L ? intent.correlationId() : intent.parentOrderId();
    }

    private static TimeInForce timeInForce(final ParentOrderIntentView intent) {
        final TimeInForce preference = intent.timeInForcePreference();
        return preference == TimeInForce.NULL_VAL ? TimeInForce.IOC : preference;
    }

    private void requireInitialized() {
        if (ctx == null) {
            throw new IllegalStateException("ImmediateLimitExecution is not initialized");
        }
    }
}
