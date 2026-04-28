package ig.rueishi.nitroj.exchange.common;

import org.agrona.DirectBuffer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded venue order identifier for L3/own-order reconciliation.
 *
 * <p>Responsibility: stores venue-provided textual order IDs as bounded ASCII
 * bytes plus a stable fingerprint. Role in system: L3 books and own-order
 * overlays use this value so exact matching is venue/instrument scoped and hash
 * collisions are resolved by byte comparison. Relationships: FIX normalizers
 * still receive textual fields from venues, but hot-path state should retain
 * this bounded representation instead of raw {@link String} map keys. Lifecycle:
 * instances are immutable and safe as map keys. Design intent: make the next
 * allocation-hardening step mechanical by centralizing length validation,
 * hashing, and collision-correct equality.</p>
 */
public final class VenueOrderId {
    public static final int MAX_LENGTH = 64;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final byte[] bytes;
    private final int length;
    private final long fingerprint;

    private VenueOrderId(final byte[] bytes, final int length, final long fingerprint) {
        this.bytes = bytes;
        this.length = length;
        this.fingerprint = fingerprint;
    }

    public static VenueOrderId fromAscii(final String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("venue order id must not be blank");
        }
        final byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        return fromBytes(source, 0, source.length);
    }

    public static VenueOrderId fromBuffer(final DirectBuffer buffer, final int offset, final int length) {
        Objects.requireNonNull(buffer, "buffer");
        validateRange(offset, length, buffer.capacity());
        final byte[] copy = new byte[length];
        buffer.getBytes(offset, copy, 0, length);
        return fromBytes(copy, 0, length);
    }

    public static VenueOrderId fromBytes(final byte[] source, final int offset, final int length) {
        Objects.requireNonNull(source, "source");
        validateRange(offset, length, source.length);
        if (length == 0) {
            throw new IllegalArgumentException("venue order id must not be empty");
        }
        final byte[] copy = Arrays.copyOfRange(source, offset, offset + length);
        return new VenueOrderId(copy, length, fingerprint(copy, 0, length));
    }

    public int length() {
        return length;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public boolean bytesEqual(final VenueOrderId other) {
        return other != null
            && length == other.length
            && Arrays.equals(bytes, other.bytes);
    }

    public void copyTo(final byte[] destination, final int offset) {
        Objects.requireNonNull(destination, "destination");
        validateRange(offset, length, destination.length);
        System.arraycopy(bytes, 0, destination, offset, length);
    }

    public String asAsciiString() {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || (obj instanceof VenueOrderId other
            && fingerprint == other.fingerprint
            && bytesEqual(other));
    }

    @Override
    public int hashCode() {
        return Long.hashCode(fingerprint);
    }

    @Override
    public String toString() {
        return asAsciiString();
    }

    private static long fingerprint(final byte[] source, final int offset, final int length) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < length; i++) {
            hash ^= source[offset + i] & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static void validateRange(final int offset, final int length, final int capacity) {
        if (offset < 0 || length < 0 || offset + length > capacity) {
            throw new IndexOutOfBoundsException("invalid venue order id byte range");
        }
        if (length > MAX_LENGTH) {
            throw new IllegalArgumentException("venue order id exceeds max length " + MAX_LENGTH);
        }
    }
}
