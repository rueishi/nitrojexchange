package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;

import java.util.Objects;

/**
 * Post-only quote execution for market-making parent intents.
 *
 * <p>V13 quote intents are worked as one live child quote per parent side. The
 * strategy owns child submission, refresh cancel/replace, parent cancel races,
 * and one-tick-deeper retry after a post-only-style child reject. Retry is
 * deliberately bounded to one attempt so replay produces the same terminal
 * state and command sequence under the same ordered events.</p>
 *
 * <p>The implementation stores only primitive active-parent fields and uses the
 * context's reusable command buffer/encoders. Timer callbacks are deterministic
 * no-ops except for the observable counter; market-data callbacks trigger a
 * cancel/replace when a child is working.</p>
 */
public final class PostOnlyQuoteExecution implements ExecutionStrategy {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final long ONE_TICK_SCALED = 1L;
    private static final int MAX_POST_ONLY_RETRIES = 1;

    private ExecutionStrategyContext ctx;
    private long activeParentOrderId;
    private long activeChildClOrdId;
    private int activeStrategyId;
    private int activeVenueId;
    private int activeInstrumentId;
    private byte activeSide;
    private long activePriceScaled;
    private long activeQtyScaled;
    private int retryCount;
    private boolean refreshPending;
    private boolean parentCancelPending;

    private long parentIntents;
    private long refreshTriggers;
    private long cancelReplaceRequests;
    private long retrySubmissions;
    private long retryExhaustions;
    private long fills;
    private long parentCancels;
    private long riskRejects;
    private long capacityRejects;
    private long timerCallbacks;

    @Override
    public int executionStrategyId() {
        return ExecutionStrategyIds.POST_ONLY_QUOTE;
    }

    @Override
    public void init(final ExecutionStrategyContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void onParentIntent(final ParentOrderIntentView intent) {
        requireInitialized();
        parentIntents++;
        if (intent.parentOrderId() <= 0L || intent.quantityScaled() <= 0L || intent.intentType() != ParentIntentType.QUOTE) {
            return;
        }

        if (activeParentOrderId == intent.parentOrderId() && activeChildClOrdId != 0L) {
            requestCancelReplace();
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

        activeParentOrderId = intent.parentOrderId();
        activeStrategyId = intent.strategyId();
        activeVenueId = intent.primaryVenueId();
        activeInstrumentId = intent.instrumentId();
        activeSide = intent.side().value();
        activePriceScaled = intent.limitPriceScaled();
        activeQtyScaled = intent.quantityScaled();
        retryCount = 0;
        refreshPending = false;
        parentCancelPending = false;
        submitChild(childClOrdId(intent), activePriceScaled, ParentOrderState.REASON_NONE);
    }

    @Override
    public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
        requireInitialized();
        if (activeChildClOrdId == 0L || venueId != activeVenueId || instrumentId != activeInstrumentId) {
            return;
        }
        refreshTriggers++;
        requestCancelReplace();
    }

    @Override
    public void onChildExecution(final ChildExecutionView execution) {
        requireInitialized();
        if (execution.parentOrderId() != activeParentOrderId || activeParentOrderId == 0L) {
            return;
        }

        switch (execution.execType()) {
            case NEW -> ctx.parentOrderRegistry().transition(activeParentOrderId, ParentOrderState.WORKING,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
            case FILL, PARTIAL_FILL -> onFill(execution);
            case REJECTED -> onPostOnlyReject(execution.childClOrdId());
            case CANCELED -> onChildCanceled(execution.childClOrdId());
            case EXPIRED -> terminal(ParentOrderState.EXPIRED, ParentOrderState.REASON_EXPIRED, execution.childClOrdId());
            default -> {
                if (execution.finalExecution()) {
                    terminal(ParentOrderState.FAILED, ParentOrderState.REASON_EXECUTION_ABORTED, execution.childClOrdId());
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
        if (parentOrderId != activeParentOrderId || activeChildClOrdId == 0L) {
            return;
        }
        parentCancels++;
        parentCancelPending = true;
        encodeCancel(activeChildClOrdId);
        ctx.orderManager().markCancelSent(activeChildClOrdId);
        ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.CANCEL_PENDING,
            reasonCode, ctx.clock().clusterTimeMicros());
    }

    public long refreshTriggers() {
        return refreshTriggers;
    }

    public long cancelReplaceRequests() {
        return cancelReplaceRequests;
    }

    public long retrySubmissions() {
        return retrySubmissions;
    }

    public long retryExhaustions() {
        return retryExhaustions;
    }

    public long fills() {
        return fills;
    }

    public long parentCancels() {
        return parentCancels;
    }

    public long riskRejects() {
        return riskRejects;
    }

    public long capacityRejects() {
        return capacityRejects;
    }

    public long timerCallbacks() {
        return timerCallbacks;
    }

    private void requestCancelReplace() {
        cancelReplaceRequests++;
        refreshPending = true;
        encodeCancel(activeChildClOrdId);
        ctx.orderManager().markCancelSent(activeChildClOrdId);
        ctx.parentOrderRegistry().transition(activeParentOrderId, ParentOrderState.CANCEL_PENDING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    private void onFill(final ChildExecutionView execution) {
        fills++;
        ctx.parentOrderRegistry().updateFill(
            activeParentOrderId,
            execution.cumQtyScaled(),
            execution.leavesQtyScaled(),
            execution.fillPriceScaled());
        if (execution.finalExecution() || execution.leavesQtyScaled() == 0L) {
            terminal(ParentOrderState.DONE, ParentOrderState.REASON_COMPLETED, execution.childClOrdId());
        } else {
            ctx.parentOrderRegistry().transition(activeParentOrderId, ParentOrderState.PARTIALLY_FILLED,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
        }
    }

    private void onPostOnlyReject(final long rejectedChildClOrdId) {
        ctx.parentOrderRegistry().unlinkChild(rejectedChildClOrdId);
        if (retryCount >= MAX_POST_ONLY_RETRIES) {
            retryExhaustions++;
            terminalWithoutUnlink(ParentOrderState.FAILED, ParentOrderState.REASON_CHILD_REJECTED);
            return;
        }
        retryCount++;
        retrySubmissions++;
        activePriceScaled = activeSide == Side.BUY.value()
            ? Math.max(0L, activePriceScaled - ONE_TICK_SCALED)
            : activePriceScaled + ONE_TICK_SCALED;
        submitChild(rejectedChildClOrdId + 1L, activePriceScaled, ParentOrderState.REASON_NONE);
    }

    private void onChildCanceled(final long canceledChildClOrdId) {
        ctx.parentOrderRegistry().unlinkChild(canceledChildClOrdId);
        if (parentCancelPending) {
            terminalWithoutUnlink(ParentOrderState.CANCELED, ParentOrderState.REASON_CANCELED_BY_PARENT);
            return;
        }
        if (refreshPending) {
            refreshPending = false;
            submitChild(canceledChildClOrdId + 1L, activePriceScaled, ParentOrderState.REASON_NONE);
        }
    }

    private void submitChild(final long childClOrdId, final long priceScaled, final byte reasonCode) {
        final RiskDecision risk = ctx.riskEngine().preTradeCheck(
            activeVenueId,
            activeInstrumentId,
            activeSide,
            priceScaled,
            activeQtyScaled,
            activeStrategyId);
        if (!risk.approved()) {
            riskRejects++;
            ctx.parentOrderRegistry().transition(activeParentOrderId, ParentOrderState.FAILED,
                ParentOrderState.REASON_RISK_REJECTED, ctx.clock().clusterTimeMicros());
            activeChildClOrdId = 0L;
            return;
        }
        if (!ctx.parentOrderRegistry().linkChild(activeParentOrderId, childClOrdId)) {
            capacityRejects++;
            ctx.parentOrderRegistry().transition(activeParentOrderId, ParentOrderState.FAILED,
                ParentOrderState.REASON_CAPACITY_REJECTED, ctx.clock().clusterTimeMicros());
            activeChildClOrdId = 0L;
            return;
        }
        activeChildClOrdId = childClOrdId;
        ctx.orderManager().createPendingOrder(
            childClOrdId,
            activeVenueId,
            activeInstrumentId,
            activeSide,
            OrdType.LIMIT.value(),
            TimeInForce.GTC.value(),
            priceScaled,
            activeQtyScaled,
            activeStrategyId,
            activeParentOrderId);
        encodeNew(childClOrdId, priceScaled);
        ctx.parentOrderRegistry().transition(activeParentOrderId, ParentOrderState.WORKING,
            reasonCode, ctx.clock().clusterTimeMicros());
    }

    private void terminal(final byte status, final byte reasonCode, final long childClOrdId) {
        ctx.parentOrderRegistry().unlinkChild(childClOrdId);
        terminalWithoutUnlink(status, reasonCode);
    }

    private void terminalWithoutUnlink(final byte status, final byte reasonCode) {
        ctx.parentOrderRegistry().transition(activeParentOrderId, status, reasonCode, ctx.clock().clusterTimeMicros());
        activeChildClOrdId = 0L;
        refreshPending = false;
        parentCancelPending = false;
    }

    private void encodeNew(final long childClOrdId, final long priceScaled) {
        final NewOrderCommandEncoder encoder = ctx.newOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .clOrdId(childClOrdId)
            .venueId(activeVenueId)
            .instrumentId(activeInstrumentId)
            .side(Side.get(activeSide))
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.GTC)
            .priceScaled(priceScaled)
            .qtyScaled(activeQtyScaled)
            .strategyId((short) activeStrategyId)
            .parentOrderId(activeParentOrderId);
    }

    private void encodeCancel(final long childClOrdId) {
        final CancelOrderCommandEncoder encoder = ctx.cancelOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .cancelClOrdId(childClOrdId + 1L)
            .origClOrdId(childClOrdId)
            .venueId(activeVenueId)
            .instrumentId(activeInstrumentId)
            .side(Side.get(activeSide))
            .originalQtyScaled(activeQtyScaled)
            .putVenueOrderId(EMPTY_BYTES, 0, 0);
    }

    private static long childClOrdId(final ParentOrderIntentView intent) {
        return intent.correlationId() > 0L ? intent.correlationId() : intent.parentOrderId();
    }

    private void requireInitialized() {
        if (ctx == null) {
            throw new IllegalStateException("PostOnlyQuoteExecution is not initialized");
        }
    }
}
