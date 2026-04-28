package ig.rueishi.nitroj.exchange.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed-point arithmetic helpers for price/quantity calculations.
 */
public final class ScaledMath {
    public static long safeMulDiv(final long a, final long b, final long divisor) {
        final long hi = Math.multiplyHigh(a, b);
        final long lo = a * b;

        if (hi == 0) {
            return Long.divideUnsigned(lo, divisor);
        }

        return BigDecimal.valueOf(a)
            .multiply(BigDecimal.valueOf(b))
            .divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP)
            .longValueExact();
    }

    public static long vwap(
        final long oldPrice,
        final long oldQty,
        final long fillPrice,
        final long fillQty
    ) {
        final long newQty = oldQty + fillQty;
        if (newQty == 0) {
            return 0L;
        }

        return BigDecimal.valueOf(oldPrice)
            .multiply(BigDecimal.valueOf(oldQty))
            .add(BigDecimal.valueOf(fillPrice).multiply(BigDecimal.valueOf(fillQty)))
            .divide(BigDecimal.valueOf(newQty), RoundingMode.HALF_UP)
            .longValue();
    }

    public static long floorToTick(final long price, final long tickSize) {
        return (price / tickSize) * tickSize;
    }

    public static long ceilToTick(final long price, final long tickSize) {
        return ((price + tickSize - 1) / tickSize) * tickSize;
    }

    public static long floorToLot(final long qty, final long lotSize) {
        return (qty / lotSize) * lotSize;
    }

    private ScaledMath() {
    }
}
