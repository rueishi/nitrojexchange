package ig.rueishi.nitroj.exchange.common;

/**
 * Internal order lifecycle states mirrored from the execution model in the spec.
 */
public final class OrderStatus {
    public static final byte PENDING_NEW = 0;
    public static final byte NEW = 1;
    public static final byte PARTIALLY_FILLED = 2;
    public static final byte FILLED = 3;
    public static final byte PENDING_CANCEL = 4;
    public static final byte CANCELED = 5;
    public static final byte PENDING_REPLACE = 6;
    public static final byte REPLACED = 7;
    public static final byte REJECTED = 8;
    public static final byte EXPIRED = 9;

    private static final long TERMINAL_MASK =
        (1L << FILLED) | (1L << CANCELED) | (1L << REPLACED) | (1L << REJECTED) | (1L << EXPIRED);

    public static boolean isTerminal(final byte status) {
        return (TERMINAL_MASK & (1L << status)) != 0;
    }

    private OrderStatus() {
    }
}
