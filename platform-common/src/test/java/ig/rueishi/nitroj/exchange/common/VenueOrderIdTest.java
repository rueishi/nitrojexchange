package ig.rueishi.nitroj.exchange.common;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for bounded venue-order identity.
 */
final class VenueOrderIdTest {
    @Test
    void positive_sameAsciiIdMatchesAndCopies() {
        final VenueOrderId first = VenueOrderId.fromAscii("abc-123");
        final VenueOrderId second = VenueOrderId.fromBytes("xxabc-123yy".getBytes(StandardCharsets.US_ASCII), 2, 7);
        final byte[] copy = new byte[7];

        first.copyTo(copy, 0);

        assertThat(first).isEqualTo(second);
        assertThat(first.bytesEqual(second)).isTrue();
        assertThat(new String(copy, StandardCharsets.US_ASCII)).isEqualTo("abc-123");
    }

    @Test
    void negative_differentIdsAndPrefixesDoNotMatch() {
        assertThat(VenueOrderId.fromAscii("abc")).isNotEqualTo(VenueOrderId.fromAscii("abcd"));
        assertThat(VenueOrderId.fromAscii("venue-1")).isNotEqualTo(VenueOrderId.fromAscii("venue-2"));
    }

    @Test
    void edge_maxLengthAcceptedAndOverlongRejected() {
        final String max = "A".repeat(VenueOrderId.MAX_LENGTH);
        final String over = "A".repeat(VenueOrderId.MAX_LENGTH + 1);

        assertThat(VenueOrderId.fromAscii(max).length()).isEqualTo(VenueOrderId.MAX_LENGTH);
        assertThatThrownBy(() -> VenueOrderId.fromAscii(over))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds max length");
    }

    @Test
    void exception_nullBlankAndInvalidRangesFailClearly() {
        assertThatThrownBy(() -> VenueOrderId.fromAscii(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> VenueOrderId.fromAscii(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VenueOrderId.fromBuffer(new UnsafeBuffer(new byte[4]), 2, 9))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void failure_sameFingerprintRequirementStillComparesBytes() {
        final VenueOrderId first = VenueOrderId.fromAscii("A");
        final VenueOrderId second = VenueOrderId.fromAscii("B");

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
        assertThat(first.bytesEqual(second)).isFalse();
    }
}
