package ig.rueishi.nitroj.exchange.common;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.util.Objects;

/**
 * Fixed-capacity byte store for bounded venue order IDs and execution IDs.
 *
 * <p>Storage layout: each slot owns {@code maxLength} bytes inside one
 * preallocated byte array, plus parallel length, fingerprint, and state arrays.
 * Lookup uses open addressing with linear probing. A fingerprint match is only
 * a candidate; the store always performs length and byte comparison before a
 * lookup is considered equal, so fingerprint collisions cannot alias distinct
 * venue identities. Capacity behavior is deterministic: inserting a new value
 * into a full store returns {@link #FULL} instead of allocating, throwing, or
 * resizing. Allocation boundary: normal insert, lookup, remove, copy, snapshot,
 * and load operations do not allocate after construction.</p>
 *
 * <p>Snapshot format is intentionally simple and deterministic: header
 * {@code int maxLength, int capacity, int size}, followed by occupied slots in
 * ascending slot order as {@code int slot, int length, long fingerprint, bytes}.
 * Recovery validates the destination store shape before loading and clears
 * previous state.</p>
 */
public final class BoundedTextIdentityStore {
    public static final int NOT_FOUND = -1;
    public static final int FULL = -2;

    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;
    private static final int SNAPSHOT_HEADER_LENGTH = Integer.BYTES * 3;
    private static final int SNAPSHOT_ENTRY_HEADER_LENGTH = Integer.BYTES * 2 + Long.BYTES;

    private final int capacity;
    private final int maxLength;
    private final byte[] bytes;
    private final int[] lengths;
    private final long[] fingerprints;
    private final byte[] states;
    private final FingerprintStrategy fingerprintStrategy;
    private int size;

    public BoundedTextIdentityStore(final int capacity, final int maxLength) {
        this(capacity, maxLength, BoundedTextIdentity::fingerprint);
    }

    BoundedTextIdentityStore(
        final int capacity,
        final int maxLength,
        final FingerprintStrategy fingerprintStrategy) {

        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        this.capacity = capacity;
        this.maxLength = maxLength;
        this.bytes = new byte[Math.multiplyExact(capacity, maxLength)];
        this.lengths = new int[capacity];
        this.fingerprints = new long[capacity];
        this.states = new byte[capacity];
        this.fingerprintStrategy = Objects.requireNonNull(fingerprintStrategy, "fingerprintStrategy");
    }

    public int capacity() {
        return capacity;
    }

    public int maxLength() {
        return maxLength;
    }

    public int size() {
        return size;
    }

    public boolean isFull() {
        return size == capacity;
    }

    public int put(final byte[] source, final int offset, final int length) {
        Objects.requireNonNull(source, "source");
        BoundedTextIdentity.validateRange(offset, length, source.length, maxLength);
        final long fingerprint = BoundedTextIdentity.fingerprint(source, offset, length);
        final int existing = findSlot(source, offset, length, fingerprint);
        if (existing >= 0) {
            return existing;
        }
        if (size == capacity) {
            return FULL;
        }
        final int slot = insertionSlot(fingerprint);
        store(slot, source, offset, length, fingerprint);
        size++;
        return slot;
    }

    public int put(final DirectBuffer source, final int offset, final int length) {
        Objects.requireNonNull(source, "source");
        BoundedTextIdentity.validateRange(offset, length, source.capacity(), maxLength);
        final long fingerprint = fingerprintStrategy.fingerprint(source, offset, length);
        final int existing = findSlot(source, offset, length, fingerprint);
        if (existing >= 0) {
            return existing;
        }
        if (size == capacity) {
            return FULL;
        }
        final int slot = insertionSlot(fingerprint);
        store(slot, source, offset, length, fingerprint);
        size++;
        return slot;
    }

    public boolean contains(final DirectBuffer source, final int offset, final int length) {
        return find(source, offset, length) >= 0;
    }

    public int find(final DirectBuffer source, final int offset, final int length) {
        Objects.requireNonNull(source, "source");
        BoundedTextIdentity.validateRange(offset, length, source.capacity(), maxLength);
        return findSlot(source, offset, length, fingerprintStrategy.fingerprint(source, offset, length));
    }

    public boolean remove(final DirectBuffer source, final int offset, final int length) {
        final int slot = find(source, offset, length);
        if (slot < 0) {
            return false;
        }
        states[slot] = DELETED;
        lengths[slot] = 0;
        fingerprints[slot] = 0L;
        size--;
        return true;
    }

    public void clear() {
        for (int i = 0; i < states.length; i++) {
            states[i] = EMPTY;
            lengths[i] = 0;
            fingerprints[i] = 0L;
        }
        size = 0;
    }

    public int copySlotTo(final int slot, final byte[] destination, final int destinationOffset) {
        Objects.requireNonNull(destination, "destination");
        validateOccupiedSlot(slot);
        BoundedTextIdentity.validateRange(destinationOffset, lengths[slot], destination.length, lengths[slot]);
        System.arraycopy(bytes, byteOffset(slot), destination, destinationOffset, lengths[slot]);
        return lengths[slot];
    }

    public int snapshotLength() {
        return SNAPSHOT_HEADER_LENGTH + (size * (SNAPSHOT_ENTRY_HEADER_LENGTH + maxLength));
    }

    public int writeSnapshot(final MutableDirectBuffer destination, final int offset) {
        Objects.requireNonNull(destination, "destination");
        int cursor = offset;
        destination.putInt(cursor, maxLength);
        cursor += Integer.BYTES;
        destination.putInt(cursor, capacity);
        cursor += Integer.BYTES;
        destination.putInt(cursor, size);
        cursor += Integer.BYTES;

        for (int slot = 0; slot < capacity; slot++) {
            if (states[slot] == OCCUPIED) {
                destination.putInt(cursor, slot);
                cursor += Integer.BYTES;
                destination.putInt(cursor, lengths[slot]);
                cursor += Integer.BYTES;
                destination.putLong(cursor, fingerprints[slot]);
                cursor += Long.BYTES;
                destination.putBytes(cursor, bytes, byteOffset(slot), lengths[slot]);
                cursor += maxLength;
            }
        }
        return cursor - offset;
    }

    public void loadSnapshot(final DirectBuffer source, final int offset) {
        Objects.requireNonNull(source, "source");
        int cursor = offset;
        final int snapshotMaxLength = source.getInt(cursor);
        cursor += Integer.BYTES;
        final int snapshotCapacity = source.getInt(cursor);
        cursor += Integer.BYTES;
        final int snapshotSize = source.getInt(cursor);
        cursor += Integer.BYTES;
        if (snapshotMaxLength != maxLength || snapshotCapacity != capacity || snapshotSize < 0 || snapshotSize > capacity) {
            throw new IllegalArgumentException("snapshot shape does not match identity store");
        }

        clear();
        for (int entry = 0; entry < snapshotSize; entry++) {
            final int slot = source.getInt(cursor);
            cursor += Integer.BYTES;
            final int length = source.getInt(cursor);
            cursor += Integer.BYTES;
            final long fingerprint = source.getLong(cursor);
            cursor += Long.BYTES;
            validateSnapshotSlot(slot, length);
            states[slot] = OCCUPIED;
            lengths[slot] = length;
            fingerprints[slot] = fingerprint;
            source.getBytes(cursor, bytes, byteOffset(slot), length);
            cursor += maxLength;
            size++;
        }
    }

    public void forEach(final EntryConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (int slot = 0; slot < capacity; slot++) {
            if (states[slot] == OCCUPIED) {
                consumer.onEntry(slot, fingerprints[slot], lengths[slot]);
            }
        }
    }

    private int findSlot(final DirectBuffer source, final int offset, final int length, final long fingerprint) {
        final int start = bucket(fingerprint);
        for (int probe = 0; probe < capacity; probe++) {
            final int slot = (start + probe) % capacity;
            if (states[slot] == EMPTY) {
                return NOT_FOUND;
            }
            if (states[slot] == OCCUPIED
                && fingerprints[slot] == fingerprint
                && bytesEqual(slot, source, offset, length)) {
                return slot;
            }
        }
        return NOT_FOUND;
    }

    private int findSlot(final byte[] source, final int offset, final int length, final long fingerprint) {
        final int start = bucket(fingerprint);
        for (int probe = 0; probe < capacity; probe++) {
            final int slot = (start + probe) % capacity;
            if (states[slot] == EMPTY) {
                return NOT_FOUND;
            }
            if (states[slot] == OCCUPIED
                && fingerprints[slot] == fingerprint
                && bytesEqual(slot, source, offset, length)) {
                return slot;
            }
        }
        return NOT_FOUND;
    }

    private int insertionSlot(final long fingerprint) {
        final int start = bucket(fingerprint);
        int firstDeleted = NOT_FOUND;
        for (int probe = 0; probe < capacity; probe++) {
            final int slot = (start + probe) % capacity;
            if (states[slot] == DELETED && firstDeleted == NOT_FOUND) {
                firstDeleted = slot;
            } else if (states[slot] == EMPTY) {
                return firstDeleted == NOT_FOUND ? slot : firstDeleted;
            }
        }
        return firstDeleted;
    }

    private void store(
        final int slot,
        final DirectBuffer source,
        final int offset,
        final int length,
        final long fingerprint) {

        states[slot] = OCCUPIED;
        lengths[slot] = length;
        fingerprints[slot] = fingerprint;
        source.getBytes(offset, bytes, byteOffset(slot), length);
    }

    private void store(
        final int slot,
        final byte[] source,
        final int offset,
        final int length,
        final long fingerprint) {

        states[slot] = OCCUPIED;
        lengths[slot] = length;
        fingerprints[slot] = fingerprint;
        System.arraycopy(source, offset, bytes, byteOffset(slot), length);
    }

    private boolean bytesEqual(final int slot, final DirectBuffer source, final int offset, final int length) {
        if (lengths[slot] != length) {
            return false;
        }
        final int storedOffset = byteOffset(slot);
        for (int i = 0; i < length; i++) {
            if (bytes[storedOffset + i] != source.getByte(offset + i)) {
                return false;
            }
        }
        return true;
    }

    private boolean bytesEqual(final int slot, final byte[] source, final int offset, final int length) {
        if (lengths[slot] != length) {
            return false;
        }
        final int storedOffset = byteOffset(slot);
        for (int i = 0; i < length; i++) {
            if (bytes[storedOffset + i] != source[offset + i]) {
                return false;
            }
        }
        return true;
    }

    private int bucket(final long fingerprint) {
        return Math.floorMod(fingerprint, capacity);
    }

    private int byteOffset(final int slot) {
        return slot * maxLength;
    }

    private void validateOccupiedSlot(final int slot) {
        if (slot < 0 || slot >= capacity || states[slot] != OCCUPIED) {
            throw new IllegalArgumentException("slot is not occupied: " + slot);
        }
    }

    private void validateSnapshotSlot(final int slot, final int length) {
        if (slot < 0 || slot >= capacity || states[slot] == OCCUPIED) {
            throw new IllegalArgumentException("invalid snapshot slot: " + slot);
        }
        if (length <= 0 || length > maxLength) {
            throw new IllegalArgumentException("invalid snapshot identity length: " + length);
        }
    }

    @FunctionalInterface
    interface FingerprintStrategy {
        long fingerprint(DirectBuffer source, int offset, int length);
    }

    @FunctionalInterface
    public interface EntryConsumer {
        void onEntry(int slot, long fingerprint, int length);
    }
}
