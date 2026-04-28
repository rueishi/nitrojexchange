package ig.rueishi.nitroj.exchange.gateway.marketdata;

import org.agrona.DirectBuffer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Allocation-conscious FIX field parser for gateway market-data normalizers.
 *
 * <p>Responsibility: parse numeric and timestamp FIX tag values directly from
 * byte ranges. Role in system: shared L2/L3 normalizers call this utility so
 * price, size, sequence, enum-adjacent, and timestamp parsing does not require
 * temporary strings. Relationships: uses the platform-wide 1e8 scale expected
 * by SBE market-data messages. Lifecycle: stateless static utility. Design
 * intent: keep malformed field handling explicit and reusable before deeper
 * generated-decoder integration.</p>
 */
public final class FixFieldParser {
    public static final long SCALE = 100_000_000L;

    private FixFieldParser() {
    }

    public static int parsePositiveInt(final DirectBuffer buffer, final int start, final int end) {
        requireRange(buffer, start, end);
        int value = 0;
        for (int i = start; i < end; i++) {
            final byte b = buffer.getByte(i);
            if (b < '0' || b > '9') {
                throw new IllegalArgumentException("integer field contains non-digit");
            }
            value = (value * 10) + (b - '0');
        }
        return value;
    }

    public static long parseScaled(final DirectBuffer buffer, final int start, final int end) {
        requireRange(buffer, start, end);
        long whole = 0;
        long fraction = 0;
        int fractionDigits = 0;
        boolean afterDecimal = false;
        for (int i = start; i < end; i++) {
            final byte b = buffer.getByte(i);
            if (b == '.') {
                if (afterDecimal) {
                    throw new IllegalArgumentException("scaled decimal contains multiple decimal points");
                }
                afterDecimal = true;
                continue;
            }
            if (b < '0' || b > '9') {
                throw new IllegalArgumentException("scaled decimal contains non-digit");
            }
            if (afterDecimal) {
                if (fractionDigits < 8) {
                    fraction = (fraction * 10) + (b - '0');
                    fractionDigits++;
                }
            } else {
                whole = (whole * 10) + (b - '0');
            }
        }
        while (fractionDigits < 8) {
            fraction *= 10;
            fractionDigits++;
        }
        return (whole * SCALE) + fraction;
    }

    public static long parseFixTimestampNanos(final DirectBuffer buffer, final int start, final int end) {
        if (start >= end) {
            return 0L;
        }
        if (end - start < 17) {
            throw new IllegalArgumentException("FIX timestamp is too short");
        }
        final int year = parsePositiveInt(buffer, start, start + 4);
        final int month = parsePositiveInt(buffer, start + 4, start + 6);
        final int day = parsePositiveInt(buffer, start + 6, start + 8);
        final int hour = parsePositiveInt(buffer, start + 9, start + 11);
        final int minute = parsePositiveInt(buffer, start + 12, start + 14);
        final int second = parsePositiveInt(buffer, start + 15, start + 17);
        int nanos = 0;
        int digits = 0;
        for (int i = start + 18; i < end && digits < 9; i++) {
            final byte b = buffer.getByte(i);
            if (b < '0' || b > '9') {
                throw new IllegalArgumentException("FIX timestamp fraction contains non-digit");
            }
            nanos = (nanos * 10) + (b - '0');
            digits++;
        }
        while (digits < 9) {
            nanos *= 10;
            digits++;
        }
        return (LocalDateTime.of(year, month, day, hour, minute, second)
            .toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L) + nanos;
    }

    public static byte firstByte(final DirectBuffer buffer, final int start, final int end) {
        requireRange(buffer, start, end);
        return buffer.getByte(start);
    }

    private static void requireRange(final DirectBuffer buffer, final int start, final int end) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (start < 0 || end <= start || end > buffer.capacity()) {
            throw new IllegalArgumentException("invalid FIX field byte range");
        }
    }
}
