package ig.rueishi.nitroj.exchange.gateway.marketdata;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for direct byte FIX field parsing.
 */
final class FixFieldParserTest {
    @Test
    void positive_parseIntegerScaledAndTimestamp() {
        assertThat(FixFieldParser.parsePositiveInt(ascii("12345"), 0, 5)).isEqualTo(12345);
        assertThat(FixFieldParser.parseScaled(ascii("65000.12345678"), 0, 14)).isEqualTo(6_500_012_345_678L);

        final long expected = LocalDateTime.of(2026, 4, 24, 20, 30, 15)
            .toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L + 123_456_789L;
        final String timestamp = "20260424-20:30:15.123456789";
        assertThat(FixFieldParser.parseFixTimestampNanos(ascii(timestamp), 0, timestamp.length()))
            .isEqualTo(expected);
    }

    @Test
    void negative_invalidDigitsFailClearly() {
        assertThatThrownBy(() -> FixFieldParser.parsePositiveInt(ascii("12x"), 0, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-digit");
        assertThatThrownBy(() -> FixFieldParser.parseScaled(ascii("1.2.3"), 0, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multiple decimal");
    }

    @Test
    void edge_zeroWholeAndPrecisionTruncation() {
        assertThat(FixFieldParser.parseScaled(ascii("0"), 0, 1)).isZero();
        assertThat(FixFieldParser.parseScaled(ascii("1.123456789"), 0, 11)).isEqualTo(112_345_678L);
        assertThat(FixFieldParser.firstByte(ascii("X"), 0, 1)).isEqualTo((byte)'X');
    }

    @Test
    void exception_invalidRangesAndNullBufferFail() {
        assertThatThrownBy(() -> FixFieldParser.parseScaled(null, 0, 1))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("buffer");
        assertThatThrownBy(() -> FixFieldParser.parseScaled(ascii("1"), 1, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid FIX field byte range");
        assertThatThrownBy(() -> FixFieldParser.parseFixTimestampNanos(ascii("2026"), 0, 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
    }

    @Test
    void failure_malformedTimestampFractionFailsWithoutPartialValue() {
        final String timestamp = "20260424-20:30:15.12x";
        assertThatThrownBy(() -> FixFieldParser.parseFixTimestampNanos(ascii(timestamp), 0, timestamp.length()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fraction");
    }

    private static UnsafeBuffer ascii(final String value) {
        return new UnsafeBuffer(value.getBytes(StandardCharsets.US_ASCII));
    }
}
