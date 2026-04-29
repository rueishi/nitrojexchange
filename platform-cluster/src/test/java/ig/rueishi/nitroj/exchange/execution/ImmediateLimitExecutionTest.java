package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

final class ImmediateLimitExecutionTest {
    private static final long PARENT_ID = 9_001L;
    private static final long CHILD_ID = 10_001L;
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = Ids.SCALE;

    @Test
    void acceptedChild_createsOrderLinksParentAndEncodesCommand() {
        final Harness harness = new Harness(4, 4, RiskDecision.APPROVED);

        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, QTY, PRICE));

        final OrderState child = harness.orderManager.getOrder(CHILD_ID);
        assertThat(child.parentOrderId()).isEqualTo(PARENT_ID);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isEqualTo(PARENT_ID);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(newOrder(harness.commandBuffer).parentOrderId()).isEqualTo(PARENT_ID);
        assertThat(newOrder(harness.commandBuffer).clOrdId()).isEqualTo(CHILD_ID);
    }

    @Test
    void fullFill_transitionsParentDoneAndUnlinksChild() {
        final Harness harness = submittedHarness();

        harness.strategy.onChildExecution(child(fill(CHILD_ID, QTY, QTY, 0L, true, "fill-1"), PARENT_ID));
        harness.orderManager.onExecution(fill(CHILD_ID, QTY, QTY, 0L, true, "fill-1"));

        final ParentOrderState parent = harness.registry.lookup(PARENT_ID);
        assertThat(parent.status()).isEqualTo(ParentOrderState.DONE);
        assertThat(parent.terminalReasonCode()).isEqualTo(ParentOrderState.REASON_COMPLETED);
        assertThat(parent.filledQtyScaled()).isEqualTo(QTY);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isZero();
        assertThat(harness.orderManager.getOrder(CHILD_ID)).isNull();
    }

    @Test
    void partialFillThenTerminal_updatesParentFillAndCompletes() {
        final Harness harness = submittedHarness();

        harness.strategy.onChildExecution(child(fill(CHILD_ID, QTY / 2, QTY / 2, QTY / 2, false, "fill-1"), PARENT_ID));
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.PARTIALLY_FILLED);
        assertThat(harness.registry.lookup(PARENT_ID).remainingQtyScaled()).isEqualTo(QTY / 2);

        harness.strategy.onChildExecution(child(fill(CHILD_ID, QTY / 2, QTY, 0L, true, "fill-2"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.DONE);
        assertThat(harness.strategy.childPartiallyFilled()).isEqualTo(1L);
        assertThat(harness.strategy.childTerminal()).isEqualTo(1L);
    }

    @Test
    void childReject_transitionsParentFailed() {
        final Harness harness = submittedHarness();

        harness.strategy.onChildExecution(child(event(CHILD_ID, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, "rej-1"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_CHILD_REJECTED);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isZero();
    }

    @Test
    void parentCancelWhileChildWorking_movesParentAndChildToCancelPending() {
        final Harness harness = submittedHarness();

        harness.strategy.onCancel(PARENT_ID, ParentOrderState.REASON_CANCELED_BY_PARENT);

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.CANCEL_PENDING);
        assertThat(harness.orderManager.getStatus(CHILD_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
    }

    @Test
    void childFillWhileParentCancelPending_completesParent() {
        final Harness harness = submittedHarness();
        harness.strategy.onCancel(PARENT_ID, ParentOrderState.REASON_CANCELED_BY_PARENT);

        harness.strategy.onChildExecution(child(fill(CHILD_ID, QTY, QTY, 0L, true, "late-fill"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.DONE);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_COMPLETED);
    }

    @Test
    void riskRejectBeforeChildCommand_failsParentWithoutChild() {
        final Harness harness = new Harness(4, 4, RiskDecision.REJECT_MAX_NOTIONAL);

        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, QTY, PRICE));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_RISK_REJECTED);
        assertThat(harness.orderManager.getOrder(CHILD_ID)).isNull();
        assertThat(harness.strategy.riskRejects()).isEqualTo(1L);
    }

    @Test
    void timerNoop_onlyRecordsCallback() {
        final Harness harness = submittedHarness();

        harness.strategy.onTimer(123L);

        assertThat(harness.strategy.timerCallbacks()).isEqualTo(1L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
    }

    @Test
    void capacityFull_rejectsSecondParentBeforeChildCreation() {
        final Harness harness = new Harness(1, 2, RiskDecision.APPROVED);

        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, QTY, PRICE));
        harness.strategy.onParentIntent(intent(PARENT_ID + 1L, CHILD_ID + 1L, QTY, PRICE));

        assertThat(harness.registry.lookup(PARENT_ID + 1L)).isNull();
        assertThat(harness.orderManager.getOrder(CHILD_ID + 1L)).isNull();
        assertThat(harness.strategy.capacityRejects()).isEqualTo(1L);
    }

    @Test
    void replay_sameIntentAndExecutionProduceSameParentState() {
        final Harness first = submittedHarness();
        final Harness second = submittedHarness();

        first.strategy.onChildExecution(child(fill(CHILD_ID, QTY / 2, QTY / 2, QTY / 2, false, "fill-1"), PARENT_ID));
        second.strategy.onChildExecution(child(fill(CHILD_ID, QTY / 2, QTY / 2, QTY / 2, false, "fill-1"), PARENT_ID));

        assertThat(second.registry.lookup(PARENT_ID).status()).isEqualTo(first.registry.lookup(PARENT_ID).status());
        assertThat(second.registry.lookup(PARENT_ID).filledQtyScaled())
            .isEqualTo(first.registry.lookup(PARENT_ID).filledQtyScaled());
        assertThat(second.registry.activeChildLinks()).isEqualTo(first.registry.activeChildLinks());
    }

    @Test
    void snapshotLoad_restoresWorkingParentAndChildLink() {
        final Harness harness = submittedHarness();
        final ParentOrderRegistry.Snapshot snapshot = harness.registry.newSnapshot();
        harness.registry.snapshotInto(snapshot);

        final ParentOrderRegistry restored = new ParentOrderRegistry(4, 4);
        restored.loadFrom(snapshot);

        assertThat(restored.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(restored.parentOrderIdByChild(CHILD_ID)).isEqualTo(PARENT_ID);
    }

    private static Harness submittedHarness() {
        final Harness harness = new Harness(4, 4, RiskDecision.APPROVED);
        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, QTY, PRICE));
        return harness;
    }

    private static NewOrderCommandDecoder newOrder(final DirectBuffer buffer) {
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ParentOrderIntentView intent(
        final long parentOrderId,
        final long childClOrdId,
        final long qty,
        final long price
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(ExecutionStrategyIds.IMMEDIATE_LIMIT)
            .intentType(ParentIntentType.IMMEDIATE_LIMIT)
            .side(Side.BUY)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .primaryVenueId(Ids.VENUE_COINBASE)
            .secondaryVenueId(0)
            .quantityScaled(qty)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(price)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 0)
            .correlationId(childClOrdId)
            .legCount((byte) 1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ChildExecutionView child(final ExecutionEventDecoder decoder, final long parentOrderId) {
        return new ChildExecutionView().wrap(decoder, parentOrderId);
    }

    private static ExecutionEventDecoder fill(
        final long childClOrdId,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        return event(childClOrdId, ExecType.FILL, PRICE, fillQty, cumQty, leavesQty, isFinal, execId);
    }

    private static ExecutionEventDecoder event(
        final long childClOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = ("venue-" + childClOrdId).getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(fillPrice)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(cumQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execIdBytes, 0, execIdBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness {
        final ParentOrderRegistry registry;
        final OrderManagerImpl orderManager = new OrderManagerImpl();
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final ImmediateLimitExecution strategy = new ImmediateLimitExecution();
        final AtomicLong clock = new AtomicLong(10L);

        Harness(final int parentCapacity, final int childLinkCapacity, final RiskDecision riskDecision) {
            registry = new ParentOrderRegistry(parentCapacity, childLinkCapacity);
            strategy.init(new ExecutionStrategyContext(
                new InternalMarketView(),
                new RiskStub(riskDecision),
                orderManager,
                registry,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                clock::incrementAndGet,
                (correlationId, deadlineClusterMicros) -> true,
                new IdRegistryStub(),
                counters()));
        }
    }

    private record RiskStub(RiskDecision decision) implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return decision; }
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

    private static final class IdRegistryStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
