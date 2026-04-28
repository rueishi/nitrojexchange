package ig.rueishi.nitroj.exchange.common;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BoundedTextIdentityStoreTest {
    @Test
    void positive_insertLookupAndCopyUseStoredBytes() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(4, 8);
        final UnsafeBuffer alpha = buffer("alpha");
        final int slot = store.put(alpha, 0, 5);
        final byte[] copy = new byte[8];

        assertThat(slot).isGreaterThanOrEqualTo(0);
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.contains(alpha, 0, 5)).isTrue();
        assertThat(store.copySlotTo(slot, copy, 0)).isEqualTo(5);
        assertThat(new String(copy, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("alpha");
    }

    @Test
    void positive_duplicateInsertReturnsExistingSlotWithoutGrowing() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(4, 8);
        final UnsafeBuffer value = buffer("dup");

        final int first = store.put(value, 0, 3);
        final int second = store.put(value, 0, 3);

        assertThat(second).isEqualTo(first);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void negative_removeMissingReturnsFalseAndExistingRemoveClearsLookup() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(4, 8);
        final UnsafeBuffer alpha = buffer("alpha");
        final UnsafeBuffer beta = buffer("beta");

        store.put(alpha, 0, 5);

        assertThat(store.remove(beta, 0, 4)).isFalse();
        assertThat(store.remove(alpha, 0, 5)).isTrue();
        assertThat(store.contains(alpha, 0, 5)).isFalse();
        assertThat(store.size()).isZero();
    }

    @Test
    void edge_maxLengthAcceptedAndTooLongRejected() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(2, 4);

        assertThat(store.put(buffer("ABCD"), 0, 4)).isGreaterThanOrEqualTo(0);
        assertThatThrownBy(() -> store.put(buffer("ABCDE"), 0, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds max length");
    }

    @Test
    void capacity_fullStoreReturnsDeterministicStatusCode() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(2, 8);

        assertThat(store.put(buffer("one"), 0, 3)).isGreaterThanOrEqualTo(0);
        assertThat(store.put(buffer("two"), 0, 3)).isGreaterThanOrEqualTo(0);
        assertThat(store.put(buffer("three"), 0, 5)).isEqualTo(BoundedTextIdentityStore.FULL);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void collision_sameFingerprintStillRequiresByteEquality() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(4, 8, (source, offset, length) -> 7L);
        final UnsafeBuffer alpha = buffer("alpha");
        final UnsafeBuffer beta = buffer("beta");

        final int alphaSlot = store.put(alpha, 0, 5);
        final int betaSlot = store.put(beta, 0, 4);

        assertThat(betaSlot).isNotEqualTo(alphaSlot);
        assertThat(store.contains(alpha, 0, 5)).isTrue();
        assertThat(store.contains(beta, 0, 4)).isTrue();
        assertThat(store.contains(buffer("gamma"), 0, 5)).isFalse();
    }

    @Test
    void snapshot_roundTripRestoresShapeEntriesAndLookup() {
        final BoundedTextIdentityStore source = new BoundedTextIdentityStore(4, 8);
        final UnsafeBuffer alpha = buffer("alpha");
        final UnsafeBuffer beta = buffer("beta");
        source.put(alpha, 0, 5);
        source.put(beta, 0, 4);
        final UnsafeBuffer snapshot = new UnsafeBuffer(new byte[source.snapshotLength()]);

        final int written = source.writeSnapshot(snapshot, 0);
        final BoundedTextIdentityStore restored = new BoundedTextIdentityStore(4, 8);
        restored.loadSnapshot(snapshot, 0);

        assertThat(written).isEqualTo(source.snapshotLength());
        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.contains(alpha, 0, 5)).isTrue();
        assertThat(restored.contains(beta, 0, 4)).isTrue();
    }

    @Test
    void snapshot_invalidShapeFailsBeforeMutatingExistingState() {
        final BoundedTextIdentityStore source = new BoundedTextIdentityStore(2, 8);
        source.put(buffer("one"), 0, 3);
        final UnsafeBuffer snapshot = new UnsafeBuffer(new byte[source.snapshotLength()]);
        source.writeSnapshot(snapshot, 0);
        final BoundedTextIdentityStore target = new BoundedTextIdentityStore(2, 4);
        target.put(buffer("keep"), 0, 4);

        assertThatThrownBy(() -> target.loadSnapshot(snapshot, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("snapshot shape");
        assertThat(target.size()).isEqualTo(1);
    }

    @Test
    void deterministicIteration_usesAscendingSlotOrder() {
        final BoundedTextIdentityStore store = new BoundedTextIdentityStore(4, 8, (source, offset, length) -> source.getByte(offset));
        store.put(buffer(new byte[] {2, 'b'}), 0, 2);
        store.put(buffer(new byte[] {0, 'a'}), 0, 2);
        store.put(buffer(new byte[] {1, 'c'}), 0, 2);
        final List<Integer> slots = new ArrayList<>();

        store.forEach((slot, fingerprint, length) -> slots.add(slot));

        assertThat(slots).containsExactly(0, 1, 2);
    }

    @Test
    void exception_invalidConstructionAndLookupInputsFailClearly() {
        assertThatThrownBy(() -> new BoundedTextIdentityStore(0, 8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("capacity");
        assertThatThrownBy(() -> new BoundedTextIdentityStore(1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxLength");
        assertThatThrownBy(() -> new BoundedTextIdentityStore(1, 8).find(null, 0, 1))
            .isInstanceOf(NullPointerException.class);
    }

    private static UnsafeBuffer buffer(final String value) {
        return new UnsafeBuffer(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static UnsafeBuffer buffer(final byte[] value) {
        return new UnsafeBuffer(value);
    }
}
