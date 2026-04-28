package ig.rueishi.nitroj.exchange.order;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the order state warm pool required before OrderManager is
 * introduced.
 *
 * <p>These tests pin the lifecycle contract that later order-management tests
 * rely on: claims consume pre-allocated objects, releases return them in LIFO
 * order, reset clears every reusable field, and heap allocation only occurs
 * after the fixed pool is exhausted.</p>
 */
final class OrderStatePoolTest {
    @Test
    void claim_returnsPreAllocatedObject() {
        final OrderStatePool pool = new OrderStatePool();

        final OrderState order = pool.claim();

        assertThat(order).isNotNull();
        assertThat(pool.available()).isEqualTo(OrderStatePool.POOL_SIZE - 1);
        assertThat(pool.overflowAllocations()).isZero();
    }

    @Test
    void claim_objectIsReset_allFieldsZero() {
        final OrderStatePool pool = new OrderStatePool();
        final OrderState order = pool.claim();
        dirty(order);
        pool.release(order);

        final OrderState reclaimed = pool.claim();

        assertReset(reclaimed);
    }

    @Test
    void release_returnsObjectToPool() {
        final OrderStatePool pool = new OrderStatePool();
        final OrderState order = pool.claim();

        pool.release(order);

        assertThat(pool.available()).isEqualTo(OrderStatePool.POOL_SIZE);
    }

    @Test
    void releaseAndClaim_sameObjectReturned() {
        final OrderStatePool pool = new OrderStatePool();
        final OrderState order = pool.claim();

        pool.release(order);

        assertThat(pool.claim()).isSameAs(order);
    }

    @Test
    void claimAll2048_noException() {
        final OrderStatePool pool = new OrderStatePool();

        for (int i = 0; i < OrderStatePool.POOL_SIZE; i++) {
            assertThat(pool.claim()).isNotNull();
        }

        assertThat(pool.available()).isZero();
        assertThat(pool.overflowAllocations()).isZero();
    }

    @Test
    void claim2049th_heapAllocates_counterIncremented() {
        final OrderStatePool pool = new OrderStatePool();
        for (int i = 0; i < OrderStatePool.POOL_SIZE; i++) {
            pool.claim();
        }

        final OrderState overflow = pool.claim();

        assertThat(overflow).isNotNull();
        assertThat(pool.available()).isZero();
        assertThat(pool.overflowAllocations()).isEqualTo(1);
    }

    @Test
    void release_afterHeapOverflow_poolReceivesObject() {
        final OrderStatePool pool = new OrderStatePool();
        for (int i = 0; i < OrderStatePool.POOL_SIZE; i++) {
            pool.claim();
        }
        final OrderState overflow = pool.claim();

        pool.release(overflow);

        assertThat(pool.available()).isEqualTo(1);
        assertThat(pool.claim()).isSameAs(overflow);
    }

    @Test
    void orderStateReset_allPrimitiveFieldsZero() {
        final OrderState order = new OrderState();
        dirty(order);

        order.reset();

        assertReset(order);
    }

    @Test
    void orderStateReset_venueOrderIdNull() {
        final OrderState order = new OrderState();
        order.venueOrderId = "venue-order-123";

        order.reset();

        assertThat(order.venueOrderId).isNull();
    }

    /**
     * Populates every field with a non-default value so reset tests can prove the
     * full reusable surface is cleared.
     *
     * @param order the state object to dirty before returning it to the pool
     */
    private static void dirty(final OrderState order) {
        order.clOrdId = 1L;
        order.venueClOrdId = 2L;
        order.venueOrderId = "venue-order-123";
        order.venueId = 3;
        order.instrumentId = 4;
        order.strategyId = 5;
        order.side = 6;
        order.ordType = 7;
        order.timeInForce = 8;
        order.priceScaled = 9L;
        order.qtyScaled = 10L;
        order.status = OrderStatus.NEW;
        order.cumFillQtyScaled = 11L;
        order.leavesQtyScaled = 12L;
        order.avgFillPriceScaled = 13L;
        order.createdClusterTime = 14L;
        order.sentNanos = 15L;
        order.firstAckNanos = 16L;
        order.lastUpdateNanos = 17L;
        order.exchangeTimestampNanos = 18L;
        order.rejectCode = 19;
        order.replacedByClOrdId = 20L;
    }

    /**
     * Verifies the exact reset contract from the task card: all primitive values
     * return to zero and the lifecycle status returns to {@code PENDING_NEW},
     * whose encoded value is also zero.
     *
     * @param order the reset state to verify
     */
    private static void assertReset(final OrderState order) {
        assertThat(order.clOrdId).isZero();
        assertThat(order.venueClOrdId).isZero();
        assertThat(order.venueOrderId).isNull();
        assertThat(order.venueId).isZero();
        assertThat(order.instrumentId).isZero();
        assertThat(order.strategyId).isZero();
        assertThat(order.side).isZero();
        assertThat(order.ordType).isZero();
        assertThat(order.timeInForce).isZero();
        assertThat(order.priceScaled).isZero();
        assertThat(order.qtyScaled).isZero();
        assertThat(order.status).isEqualTo(OrderStatus.PENDING_NEW);
        assertThat(order.cumFillQtyScaled).isZero();
        assertThat(order.leavesQtyScaled).isZero();
        assertThat(order.avgFillPriceScaled).isZero();
        assertThat(order.createdClusterTime).isZero();
        assertThat(order.sentNanos).isZero();
        assertThat(order.firstAckNanos).isZero();
        assertThat(order.lastUpdateNanos).isZero();
        assertThat(order.exchangeTimestampNanos).isZero();
        assertThat(order.rejectCode).isZero();
        assertThat(order.replacedByClOrdId).isZero();
    }
}
