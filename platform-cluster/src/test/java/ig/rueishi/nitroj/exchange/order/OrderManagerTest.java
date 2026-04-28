package ig.rueishi.nitroj.exchange.order;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.test.CapturingPublication;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * T-007 coverage for the cluster order lifecycle manager.
 *
 * <p>The tests exercise the state table, duplicate execution detection,
 * cancel-command publication, pool release behavior, generated ID monotonicity,
 * and SBE snapshot round trips. They intentionally run without a real Aeron
 * cluster so the order state machine can be validated deterministically and
 * quickly before later integration cards wire the manager into MessageRouter.</p>
 */
final class OrderManagerTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final int STRATEGY = Ids.STRATEGY_MARKET_MAKING;
    private static final long CL_ORD_ID = 1001L;
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = 10L * Ids.SCALE;

    private final AtomicLong clockMicros = new AtomicLong(500L);
    private final CapturingPublication publication = new CapturingPublication();

    @Test
    void pendingNew_execTypeNew_transitionsToNew() {
        final OrderManagerImpl manager = managerWithOrder();

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-1"))).isFalse();

        final OrderState order = manager.getOrder(CL_ORD_ID);
        assertThat(order.status).isEqualTo(OrderStatus.NEW);
        assertThat(order.venueOrderId).isEqualTo("venue-1001");
        assertThat(order.firstAckNanos).isEqualTo(1_000L);
    }

    @Test
    void pendingNew_execTypeRejected_transitionsToRejected() {
        final OrderManagerImpl manager = managerWithOrder();

        assertThat(manager.onExecution(reject(CL_ORD_ID, "rej-1"))).isFalse();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void pendingNew_execTypeFill_immediate_transitionsToFilled() {
        final OrderManagerImpl manager = managerWithOrder();

        assertThat(manager.onExecution(fill(CL_ORD_ID, QTY, QTY, 0L, true, "fill-1"))).isTrue();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void new_partialFill_transitionsToPartiallyFilled() {
        final OrderManagerImpl manager = newOrder();

        assertThat(manager.onExecution(fill(CL_ORD_ID, 4L * Ids.SCALE, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "fill-1"))).isTrue();

        final OrderState order = manager.getOrder(CL_ORD_ID);
        assertThat(order.status).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.cumFillQtyScaled).isEqualTo(4L * Ids.SCALE);
        assertThat(order.leavesQtyScaled).isEqualTo(6L * Ids.SCALE);
    }

    @Test
    void new_fullFill_transitionsToFilled() {
        final OrderManagerImpl manager = newOrder();

        assertThat(manager.onExecution(fill(CL_ORD_ID, QTY, QTY, 0L, true, "fill-1"))).isTrue();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void partiallyFilled_fullFill_transitionsToFilled() {
        final OrderManagerImpl manager = partialOrder();

        assertThat(manager.onExecution(fill(CL_ORD_ID, 6L * Ids.SCALE, QTY, 0L, true, "fill-2"))).isTrue();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void partiallyFilled_anotherPartialFill_staysPartiallyFilled() {
        final OrderManagerImpl manager = partialOrder();

        assertThat(manager.onExecution(fill(CL_ORD_ID, 3L * Ids.SCALE, 7L * Ids.SCALE, 3L * Ids.SCALE, false, "fill-2"))).isTrue();

        final OrderState order = manager.getOrder(CL_ORD_ID);
        assertThat(order.status).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.cumFillQtyScaled).isEqualTo(7L * Ids.SCALE);
    }

    @Test
    void new_cancelSent_transitionsToPendingCancel() {
        final OrderManagerImpl manager = newOrder();

        manager.markCancelSent(CL_ORD_ID);

        assertThat(manager.getStatus(CL_ORD_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
    }

    @Test
    void pendingCancel_cancelConfirm_transitionsToCanceled() {
        final OrderManagerImpl manager = newOrder();
        manager.markCancelSent(CL_ORD_ID);

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.CANCELED, 0L, 0L, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "cx-1"))).isFalse();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void pendingCancel_fillBeforeCancelAck_transitionsToFilled() {
        final OrderManagerImpl manager = newOrder();
        manager.markCancelSent(CL_ORD_ID);

        assertThat(manager.onExecution(fill(CL_ORD_ID, QTY, QTY, 0L, true, "fill-1"))).isTrue();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
    }

    @Test
    void new_expired_transitionsToExpired() {
        final OrderManagerImpl manager = newOrder();

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.EXPIRED, 0L, 0L, 0L, QTY, false, "exp-1"))).isFalse();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void pendingReplace_cancelConfirm_transitionsToPendingNew() {
        final OrderManagerImpl manager = newOrder();
        manager.markReplaceSent(CL_ORD_ID, 2002L);

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.CANCELED, 0L, 0L, 0L, QTY, false, "r-cx-1"))).isFalse();

        assertThat(manager.getStatus(CL_ORD_ID)).isEqualTo(OrderStatus.PENDING_NEW);
    }

    @Test
    void pendingReplace_replaced_transitionsToReplaced() {
        final OrderManagerImpl manager = newOrder();
        manager.markReplaceSent(CL_ORD_ID, 2002L);

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.REPLACED, 0L, 0L, 0L, QTY, false, "rpl-1"))).isFalse();

        assertThat(manager.getOrder(CL_ORD_ID)).isNull();
        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void pendingReplace_rejected_transitionsBackToNew() {
        final OrderManagerImpl manager = newOrder();
        manager.markReplaceSent(CL_ORD_ID, 2002L);

        assertThat(manager.onExecution(reject(CL_ORD_ID, "r-rej-1"))).isFalse();

        assertThat(manager.getStatus(CL_ORD_ID)).isEqualTo(OrderStatus.NEW);
        assertThat(manager.getOrder(CL_ORD_ID).rejectCode).isEqualTo(99);
    }

    @Test
    void pendingReplace_fillBeforeCancelAck_appliesFill() {
        final OrderManagerImpl manager = newOrder();
        manager.markReplaceSent(CL_ORD_ID, 2002L);

        assertThat(manager.onExecution(fill(CL_ORD_ID, 4L * Ids.SCALE, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "r-fill-1"))).isTrue();

        assertThat(manager.getStatus(CL_ORD_ID)).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    @Test
    void filled_execReport_loggedAndDiscarded() {
        final OrderManagerImpl manager = managerWithOrder();
        final OrderState order = manager.getOrder(CL_ORD_ID);
        order.status = OrderStatus.FILLED;

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "late-1"))).isFalse();

        assertThat(manager.duplicateExecutionReportCount()).isEqualTo(1);
        assertThat(order.status).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void canceled_execReport_loggedAndDiscarded() {
        final OrderManagerImpl manager = managerWithOrder();
        final OrderState order = manager.getOrder(CL_ORD_ID);
        order.status = OrderStatus.CANCELED;

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "late-1"))).isFalse();

        assertThat(manager.duplicateExecutionReportCount()).isEqualTo(1);
        assertThat(order.status).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void rejected_execReport_loggedAndDiscarded() {
        final OrderManagerImpl manager = managerWithOrder();
        final OrderState order = manager.getOrder(CL_ORD_ID);
        order.status = OrderStatus.REJECTED;

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "late-1"))).isFalse();

        assertThat(manager.duplicateExecutionReportCount()).isEqualTo(1);
        assertThat(order.status).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void unknownTransition_stateUnchanged_errorLogged() {
        final OrderManagerImpl manager = managerWithOrder();

        assertThat(manager.onExecution(exec(CL_ORD_ID, ExecType.CANCELED, 0L, 0L, 0L, QTY, false, "bad-1"))).isFalse();

        assertThat(manager.getStatus(CL_ORD_ID)).isEqualTo(OrderStatus.PENDING_NEW);
        assertThat(manager.invalidTransitionCount()).isEqualTo(1);
    }

    @Test
    void duplicateExecId_secondReportDiscarded() {
        final OrderManagerImpl manager = newOrder();

        assertThat(manager.onExecution(fill(CL_ORD_ID, 1L * Ids.SCALE, 1L * Ids.SCALE, 9L * Ids.SCALE, false, "dup-1"))).isTrue();
        assertThat(manager.onExecution(fill(CL_ORD_ID, 1L * Ids.SCALE, 2L * Ids.SCALE, 8L * Ids.SCALE, false, "dup-1"))).isFalse();

        assertThat(manager.getOrder(CL_ORD_ID).cumFillQtyScaled).isEqualTo(1L * Ids.SCALE);
    }

    @Test
    void duplicateExecId_metricsCounterIncremented() {
        final OrderManagerImpl manager = newOrder();

        manager.onExecution(fill(CL_ORD_ID, 1L * Ids.SCALE, 1L * Ids.SCALE, 9L * Ids.SCALE, false, "dup-1"));
        manager.onExecution(fill(CL_ORD_ID, 1L * Ids.SCALE, 2L * Ids.SCALE, 8L * Ids.SCALE, false, "dup-1"));

        assertThat(manager.duplicateExecutionReportCount()).isEqualTo(1);
    }

    @Test
    void fill_cumQtyUpdated() {
        final OrderManagerImpl manager = newOrder();

        manager.onExecution(fill(CL_ORD_ID, 4L * Ids.SCALE, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "fill-1"));

        assertThat(manager.getOrder(CL_ORD_ID).cumFillQtyScaled).isEqualTo(4L * Ids.SCALE);
    }

    @Test
    void fill_leavesQtyDecremented() {
        final OrderManagerImpl manager = newOrder();

        manager.onExecution(fill(CL_ORD_ID, 4L * Ids.SCALE, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "fill-1"));

        assertThat(manager.getOrder(CL_ORD_ID).leavesQtyScaled).isEqualTo(6L * Ids.SCALE);
    }

    @Test
    void fill_avgFillPriceVwapCorrect() {
        final OrderManagerImpl manager = newOrder();
        manager.onExecution(fillAt(CL_ORD_ID, 4L * Ids.SCALE, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "fill-1", 65_000L * Ids.SCALE));

        manager.onExecution(fillAt(CL_ORD_ID, 3L * Ids.SCALE, 7L * Ids.SCALE, 3L * Ids.SCALE, false, "fill-2", 66_000L * Ids.SCALE));

        assertThat(manager.getOrder(CL_ORD_ID).avgFillPriceScaled)
            .isEqualTo(ScaledMath.vwap(65_000L * Ids.SCALE, 4L * Ids.SCALE, 66_000L * Ids.SCALE, 3L * Ids.SCALE));
    }

    @Test
    void fill_onTerminalOrder_fillAppliedAnyway() {
        final OrderManagerImpl manager = managerWithOrder();
        final OrderState order = manager.getOrder(CL_ORD_ID);
        order.status = OrderStatus.FILLED;

        assertThat(manager.onExecution(fill(CL_ORD_ID, 1L * Ids.SCALE, 1L * Ids.SCALE, 0L, true, "late-fill-1"))).isTrue();

        assertThat(order.cumFillQtyScaled).isEqualTo(1L * Ids.SCALE);
        assertThat(manager.duplicateExecutionReportCount()).isEqualTo(1);
    }

    @Test
    void createOrder_poolObjectClaimed() {
        final OrderManagerImpl manager = manager();

        manager.createPendingOrder(CL_ORD_ID, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, STRATEGY);

        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE - 1);
        assertThat(manager.getStatus(CL_ORD_ID)).isEqualTo(OrderStatus.PENDING_NEW);
    }

    @Test
    void terminalState_orderReturnedToPool() {
        final OrderManagerImpl manager = managerWithOrder();

        manager.onExecution(reject(CL_ORD_ID, "rej-1"));

        assertThat(manager.poolAvailable()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void cancelAllOrders_eachLiveOrderGetsCancelCommand() {
        final OrderManagerImpl manager = managerWithOrders(3);
        manager.onExecution(exec(1001L, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-1"));
        manager.onExecution(exec(1002L, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-2"));

        manager.cancelAllOrders();

        assertThat(publication.countByTemplateId(CancelOrderCommandEncoder.TEMPLATE_ID)).isEqualTo(3);
        assertThat(List.of(cancel(0).origClOrdId(), cancel(1).origClOrdId(), cancel(2).origClOrdId()))
            .containsExactlyInAnyOrder(1001L, 1002L, 1003L);
        assertThat(manager.getStatus(1001L)).isEqualTo(OrderStatus.PENDING_CANCEL);
    }

    @Test
    void cancelAllOrders_terminalOrdersNotCanceled() {
        final OrderManagerImpl manager = managerWithOrders(2);
        manager.onExecution(reject(1001L, "rej-1"));

        manager.cancelAllOrders();

        assertThat(publication.countByTemplateId(CancelOrderCommandEncoder.TEMPLATE_ID)).isEqualTo(1);
        assertThat(cancel(0).origClOrdId()).isEqualTo(1002L);
    }

    @Test
    void snapshot_allLiveOrdersWritten() {
        final OrderManagerImpl manager = managerWithOrders(2);

        final List<byte[]> fragments = manager.snapshotFragments();

        assertThat(fragments).hasSize(3);
        assertThat(new UnsafeBuffer(fragments.get(0)).getInt(0, java.nio.ByteOrder.LITTLE_ENDIAN)).isEqualTo(2);
    }

    @Test
    void snapshot_terminalOrdersNotWritten() {
        final OrderManagerImpl manager = managerWithOrders(2);
        manager.onExecution(reject(1001L, "rej-1"));

        final List<byte[]> fragments = manager.snapshotFragments();

        assertThat(new UnsafeBuffer(fragments.get(0)).getInt(0, java.nio.ByteOrder.LITTLE_ENDIAN)).isEqualTo(1);
        assertThat(fragments).hasSize(2);
    }

    @Test
    void snapshot_loadRestoresAllFields() {
        final OrderManagerImpl manager = managerWithOrder();
        manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-1"));
        final OrderState original = manager.getOrder(CL_ORD_ID);
        original.cumFillQtyScaled = 2L * Ids.SCALE;
        original.leavesQtyScaled = 8L * Ids.SCALE;
        original.avgFillPriceScaled = PRICE;

        final OrderManagerImpl restored = manager();
        restored.loadSnapshotFragments(manager.snapshotFragments());

        final OrderState copy = restored.getOrder(CL_ORD_ID);
        assertThat(copy.clOrdId).isEqualTo(original.clOrdId);
        assertThat(copy.venueId).isEqualTo(original.venueId);
        assertThat(copy.instrumentId).isEqualTo(original.instrumentId);
        assertThat(copy.strategyId).isEqualTo(original.strategyId);
        assertThat(copy.side).isEqualTo(original.side);
        assertThat(copy.ordType).isEqualTo(original.ordType);
        assertThat(copy.timeInForce).isEqualTo(original.timeInForce);
        assertThat(copy.status).isEqualTo(original.status);
        assertThat(copy.priceScaled).isEqualTo(original.priceScaled);
        assertThat(copy.qtyScaled).isEqualTo(original.qtyScaled);
        assertThat(copy.cumFillQtyScaled).isEqualTo(original.cumFillQtyScaled);
        assertThat(copy.leavesQtyScaled).isEqualTo(original.leavesQtyScaled);
        assertThat(copy.avgFillPriceScaled).isEqualTo(original.avgFillPriceScaled);
        assertThat(copy.createdClusterTime).isEqualTo(original.createdClusterTime);
    }

    @Test
    void snapshot_loadRestoresVenueOrderId() {
        final OrderManagerImpl manager = managerWithOrder();
        manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-1"));

        final OrderManagerImpl restored = manager();
        restored.loadSnapshotFragments(manager.snapshotFragments());

        assertThat(restored.getOrder(CL_ORD_ID).venueOrderId).isEqualTo("venue-1001");
    }

    @Test
    void clOrdId_10000Orders_allUnique() {
        final OrderManagerImpl manager = manager();
        long previous = 0L;

        for (int i = 0; i < 10_000; i++) {
            final long next = manager.nextClOrdId();
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
    }

    @Test
    void duplicateExecIdWindow_prunesOldestEntry() {
        final OrderManagerImpl manager = newOrder();
        for (int i = 0; i < OrderManagerImpl.EXEC_ID_WINDOW_SIZE + 1; i++) {
            final long id = CL_ORD_ID + i;
            manager.createPendingOrder(id, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, STRATEGY);
            manager.onExecution(exec(id, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-window-" + i));
        }

        assertThat(manager.seenExecIdCount()).isEqualTo(OrderManagerImpl.EXEC_ID_WINDOW_SIZE);
    }

    private OrderManagerImpl partialOrder() {
        final OrderManagerImpl manager = newOrder();
        manager.onExecution(fill(CL_ORD_ID, 4L * Ids.SCALE, 4L * Ids.SCALE, 6L * Ids.SCALE, false, "fill-1"));
        return manager;
    }

    private OrderManagerImpl newOrder() {
        final OrderManagerImpl manager = managerWithOrder();
        manager.onExecution(exec(CL_ORD_ID, ExecType.NEW, 0L, 0L, 0L, QTY, false, "ack-1"));
        return manager;
    }

    private OrderManagerImpl managerWithOrder() {
        final OrderManagerImpl manager = manager();
        manager.createPendingOrder(CL_ORD_ID, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, STRATEGY);
        return manager;
    }

    private OrderManagerImpl managerWithOrders(final int count) {
        final OrderManagerImpl manager = manager();
        for (int i = 0; i < count; i++) {
            final long clOrdId = CL_ORD_ID + i;
            manager.createPendingOrder(clOrdId, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, STRATEGY);
        }
        return manager;
    }

    private OrderManagerImpl manager() {
        return new OrderManagerImpl(new OrderStatePool(), publication::offer, clockMicros::get);
    }

    private ExecutionEventDecoder reject(final long clOrdId, final String execId) {
        return exec(clOrdId, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, execId, PRICE, 99);
    }

    private ExecutionEventDecoder fill(
        final long clOrdId,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        return fillAt(clOrdId, fillQty, cumQty, leavesQty, isFinal, execId, PRICE);
    }

    private ExecutionEventDecoder fillAt(
        final long clOrdId,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId,
        final long fillPrice
    ) {
        return exec(clOrdId, ExecType.FILL, fillPrice, fillQty, cumQty, leavesQty, isFinal, execId);
    }

    private ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        return exec(clOrdId, execType, fillPrice, fillQty, cumQty, leavesQty, isFinal, execId, 0);
    }

    private ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId,
        final int rejectCode
    ) {
        return exec(clOrdId, execType, fillPrice, fillQty, cumQty, leavesQty, isFinal, execId, PRICE, rejectCode);
    }

    private ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId,
        final long orderPrice,
        final int rejectCode
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final ExecutionEventEncoder encoder = new ExecutionEventEncoder();
        final byte[] venueOrderId = ("venue-" + clOrdId).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        encoder
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE)
            .instrumentId(INSTRUMENT)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(fillPrice)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(cumQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(rejectCode)
            .ingressTimestampNanos(1_000L)
            .exchangeTimestampNanos(2_000L)
            .fixSeqNum(10)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execIdBytes, 0, execIdBytes.length);
        assertThat(orderPrice).isGreaterThanOrEqualTo(0L);

        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private CancelOrderCommandDecoder cancel(final int index) {
        final CancelOrderCommandDecoder decoder = new CancelOrderCommandDecoder();
        decoder.wrapAndApplyHeader(publication.message(index), 0, new MessageHeaderDecoder());
        return decoder;
    }

}
