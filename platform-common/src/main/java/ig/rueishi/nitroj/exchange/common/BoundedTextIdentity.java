package ig.rueishi.nitroj.exchange.common;

import org.agrona.DirectBuffer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded ASCII identity used for venue order IDs and execution IDs.
 *
 * <p>Responsibility: stores a short venue-provided text field as bytes plus a
 * stable fingerprint. Role in system: gateway parsers can validate and copy
 * raw FIX/SBE bytes once, while cluster hot-path state can compare by
 * fingerprint and then exact bytes. Relationships: {@link BoundedTextIdentityStore}
 * is the allocation-free mutable container for these identities; this value
 * type is best used for tests, snapshots, and cold boundaries. Lifecycle:
 * immutable after construction. Allocation boundary: factory methods allocate
 * the backing byte array, so steady-state hot paths should prefer the store's
 * direct-buffer APIs.</p>
 */
public final class BoundedTextIdentity {
    public static final int DEFAULT_MAX_LENGTH = 64;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final byte[] bytes;
    private final int length;
    private final long fingerprint;

    private BoundedTextIdentity(final byte[] bytes, final int length, final long fingerprint) {
        this.bytes = bytes;
        this.length = length;
        this.fingerprint = fingerprint;
    }

    public static BoundedTextIdentity fromAscii(final String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("identity must not be blank");
        }
        final byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        return fromBytes(source, 0, source.length, DEFAULT_MAX_LENGTH);
    }

    public static BoundedTextIdentity fromBytes(
        final byte[] source,
        final int offset,
        final int length,
        final int maxLength) {

        Objects.requireNonNull(source, "source");
        validateRange(offset, length, source.length, maxLength);
        final byte[] copy = Arrays.copyOfRange(source, offset, offset + length);
        return new BoundedTextIdentity(copy, length, fingerprint(copy, 0, length));
    }

    public static BoundedTextIdentity fromBuffer(
        final DirectBuffer source,
        final int offset,
        final int length,
        final int maxLength) {

        Objects.requireNonNull(source, "source");
        validateRange(offset, length, source.capacity(), maxLength);
        final byte[] copy = new byte[length];
        source.getBytes(offset, copy, 0, length);
        return new BoundedTextIdentity(copy, length, fingerprint(copy, 0, length));
    }

    public int length() {
        return length;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public void copyTo(final byte[] destination, final int offset) {
        Objects.requireNonNull(destination, "destination");
        validateRange(offset, length, destination.length, length);
        System.arraycopy(bytes, 0, destination, offset, length);
    }

    public boolean bytesEqual(final BoundedTextIdentity other) {
        return other != null
            && length == other.length
            && Arrays.equals(bytes, other.bytes);
    }

    public String asAsciiString() {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || (obj instanceof BoundedTextIdentity other
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

    static long fingerprint(final byte[] source, final int offset, final int length) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < length; i++) {
            hash ^= source[offset + i] & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    static long fingerprint(final DirectBuffer source, final int offset, final int length) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < length; i++) {
            hash ^= source.getByte(offset + i) & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    static void validateRange(final int offset, final int length, final int capacity, final int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        if (offset < 0 || length < 0 || offset + length > capacity) {
            throw new IndexOutOfBoundsException("invalid identity byte range");
        }
        if (length == 0) {
            throw new IllegalArgumentException("identity must not be empty");
        }
        if (length > maxLength) {
            throw new IllegalArgumentException("identity exceeds max length " + maxLength);
        }
    }
}
