package ig.rueishi.nitroj.exchange.cluster;

/**
 * Advisory queue-position estimate for an order visible in an L3 book.
 *
 * <p>Responsibility: carries the amount of same-price, same-side displayed
 * quantity ahead of a target order when the feed preserves enough ordering to
 * estimate it. Role in system: strategies may use this as a signal, but order
 * correctness still comes from {@code OrderManager} state and execution reports.
 * Relationships: {@link VenueL3Book} creates estimates from active L3 order
 * ordering, and callers must tolerate {@link #unknown()} when venue data is
 * insufficient. Lifecycle: immutable value object, safe to discard after each
 * market-data decision.</p>
 *
 * @param known true when the estimate is based on usable L3 ordering
 * @param sizeAheadScaled visible quantity ahead at the same venue/instrument/side/price
 * @param ownSizeScaled visible target order quantity used for the estimate
 */
public record QueuePositionEstimate(boolean known, long sizeAheadScaled, long ownSizeScaled) {
    private static final QueuePositionEstimate UNKNOWN = new QueuePositionEstimate(false, 0L, 0L);

    public QueuePositionEstimate {
        if (sizeAheadScaled < 0L || ownSizeScaled < 0L) {
            throw new IllegalArgumentException("queue-position quantities must be non-negative");
        }
        if (!known && (sizeAheadScaled != 0L || ownSizeScaled != 0L)) {
            throw new IllegalArgumentException("unknown queue-position estimate cannot carry quantities");
        }
    }

    /**
     * Creates an unknown advisory estimate.
     *
     * @return shared unknown estimate
     */
    public static QueuePositionEstimate unknown() {
        return UNKNOWN;
    }

    /**
     * Creates a known queue-position estimate.
     *
     * @param sizeAheadScaled displayed quantity ahead of the target order
     * @param ownSizeScaled target order's displayed quantity
     * @return known estimate
     */
    public static QueuePositionEstimate known(final long sizeAheadScaled, final long ownSizeScaled) {
        return new QueuePositionEstimate(true, sizeAheadScaled, ownSizeScaled);
    }
}
