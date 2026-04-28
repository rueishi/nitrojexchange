package ig.rueishi.nitroj.exchange.order;

import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.common.BoundedTextIdentity;

import java.nio.charset.StandardCharsets;

/**
 * Mutable in-memory representation of one live order in the cluster service.
 *
 * <p>The object is intentionally a simple field carrier. TASK-020's order manager
 * mutates these fields on the single Aeron Cluster service thread while processing
 * execution reports, snapshotting, and recovery actions. Keeping the state flat
 * avoids per-order allocations and makes pool reuse cheap and explicit.</p>
 *
 * <p>Venue order identity is stored as bounded ASCII bytes inside the state
 * object. Hot order transitions copy decoder bytes into this reusable storage
 * instead of constructing {@link String} or byte-array objects. The
 * {@link #venueOrderId()} accessor is a cold/test convenience that materializes
 * a string only for assertions, diagnostics, and compatibility boundaries.</p>
 */
public final class OrderState {
    long clOrdId;
    long venueClOrdId;
    final byte[] venueOrderIdBytes = new byte[BoundedTextIdentity.DEFAULT_MAX_LENGTH];
    int venueOrderIdLength;
    int venueId;
    int instrumentId;
    int strategyId;
    byte side;
    byte ordType;
    byte timeInForce;
    long priceScaled;
    long qtyScaled;
    byte status;
    long cumFillQtyScaled;
    long leavesQtyScaled;
    long avgFillPriceScaled;
    long createdClusterTime;
    long sentNanos;
    long firstAckNanos;
    long lastUpdateNanos;
    long exchangeTimestampNanos;
    int rejectCode;
    long replacedByClOrdId;

    /**
     * Restores this state object to its initial reusable form.
     *
     * <p>The pool calls this before returning an object from {@code claim()} and
     * before placing an object back into the stack during {@code release()}. That
     * double reset is deliberate: callers always receive a clean object, and the
     * pool never holds stale user-visible data while idle.</p>
     */
    public void reset() {
        clOrdId = 0L;
        venueClOrdId = 0L;
        venueOrderIdLength = 0;
        venueId = 0;
        instrumentId = 0;
        strategyId = 0;
        side = 0;
        ordType = 0;
        timeInForce = 0;
        priceScaled = 0L;
        qtyScaled = 0L;
        status = OrderStatus.PENDING_NEW;
        cumFillQtyScaled = 0L;
        leavesQtyScaled = 0L;
        avgFillPriceScaled = 0L;
        createdClusterTime = 0L;
        sentNanos = 0L;
        firstAckNanos = 0L;
        lastUpdateNanos = 0L;
        exchangeTimestampNanos = 0L;
        rejectCode = 0;
        replacedByClOrdId = 0L;
    }

    public long clOrdId() {
        return clOrdId;
    }

    public String venueOrderId() {
        return venueOrderIdLength == 0 ? null : new String(venueOrderIdBytes, 0, venueOrderIdLength, StandardCharsets.US_ASCII);
    }

    public int venueId() {
        return venueId;
    }

    public int instrumentId() {
        return instrumentId;
    }

    public int strategyId() {
        return strategyId;
    }

    public byte side() {
        return side;
    }

    public long priceScaled() {
        return priceScaled;
    }

    public long qtyScaled() {
        return qtyScaled;
    }

    public byte status() {
        return status;
    }

    public long cumFillQtyScaled() {
        return cumFillQtyScaled;
    }

    public long leavesQtyScaled() {
        return leavesQtyScaled;
    }

    /**
     * Reports whether this state can contribute visible own liquidity.
     *
     * <p>The market books remain gross venue views. This helper exists for
     * overlay code that needs to project authoritative order-manager state into a
     * separate self-liquidity view without duplicating lifecycle constants.</p>
     *
     * @return true when the order is live and has remaining quantity
     */
    public boolean isWorkingVisible() {
        return !OrderStatus.isTerminal(status) && leavesQtyScaled > 0L;
    }
}
