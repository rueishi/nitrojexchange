package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;

final class BenchmarkOrderManager implements OrderManager {
    private static final long[] EMPTY_IDS = new long[0];

    private long createCalls;
    private long cancelMarks;

    @Override
    public void createPendingOrder(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final byte side,
        final byte ordType,
        final byte timeInForce,
        final long priceScaled,
        final long qtyScaled,
        final int strategyId
    ) {
        createCalls++;
    }

    @Override
    public void createPendingOrder(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final byte side,
        final byte ordType,
        final byte timeInForce,
        final long priceScaled,
        final long qtyScaled,
        final int strategyId,
        final long parentOrderId
    ) {
        createCalls++;
    }

    @Override
    public boolean onExecution(final ExecutionEventDecoder decoder) {
        return false;
    }

    @Override
    public void cancelAllOrders() {
    }

    @Override
    public long[] getLiveOrderIds(final int venueId) {
        return EMPTY_IDS;
    }

    @Override
    public OrderState getOrder(final long clOrdId) {
        return null;
    }

    @Override
    public void forceTransitionToCanceled(final long clOrdId) {
    }

    @Override
    public void markCancelSent(final long clOrdId) {
        cancelMarks++;
    }

    @Override
    public void writeSnapshot(final ExclusivePublication pub) {
    }

    @Override
    public void loadSnapshot(final Image image) {
    }

    @Override
    public void setCluster(final Cluster cluster) {
    }

    @Override
    public void resetAll() {
        createCalls = 0L;
        cancelMarks = 0L;
    }

    long calls() {
        return createCalls + cancelMarks;
    }
}
