package ig.rueishi.nitroj.exchange.common;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BoundedTextIdentityTest {
    @Test
    void positive_sameAsciiBytesCompareEqualAndCopy() {
        final BoundedTextIdentity first = BoundedTextIdentity.fromAscii("exec-123");
        final BoundedTextIdentity second = BoundedTextIdentity.fromBytes(ascii("xxexec-123yy"), 2, 8, 16);
        final byte[] copy = new byte[8];

        first.copyTo(copy, 0);

        assertThat(first).isEqualTo(second);
        assertThat(first.bytesEqual(second)).isTrue();
        assertThat(new String(copy, StandardCharsets.US_ASCII)).isEqualTo("exec-123");
    }

    @Test
    void negative_differentLengthOrBytesDoNotCompareEqual() {
        assertThat(BoundedTextIdentity.fromAscii("abc")).isNotEqualTo(BoundedTextIdentity.fromAscii("abcd"));
        assertThat(BoundedTextIdentity.fromAscii("exec-1")).isNotEqualTo(BoundedTextIdentity.fromAscii("exec-2"));
    }

    @Test
    void edge_maxLengthAcceptedAndOverMaxRejected() {
        assertThat(BoundedTextIdentity.fromBytes(ascii("ABCD"), 0, 4, 4).length()).isEqualTo(4);

        assertThatThrownBy(() -> BoundedTextIdentity.fromBytes(ascii("ABCDE"), 0, 5, 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds max length");
    }

    @Test
    void exception_nullBlankEmptyAndInvalidRangesFailClearly() {
        assertThatThrownBy(() -> BoundedTextIdentity.fromAscii(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BoundedTextIdentity.fromAscii(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundedTextIdentity.fromBytes(ascii("A"), 0, 0, 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> BoundedTextIdentity.fromBuffer(new UnsafeBuffer(new byte[4]), 3, 2, 4))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    private static byte[] ascii(final String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
