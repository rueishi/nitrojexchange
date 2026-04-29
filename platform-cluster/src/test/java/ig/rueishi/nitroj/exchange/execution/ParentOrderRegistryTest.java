package ig.rueishi.nitroj.exchange.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ParentOrderRegistryTest {
    @Test
    void claimLookupAndTransition_mutateBoundedParentState() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(2, 4);

        final ParentOrderState state = registry.claim(101L, 7, 11, 1_000L, 10L);

        assertThat(state).isNotNull();
        assertThat(registry.lookup(101L)).isSameAs(state);
        assertThat(state.status()).isEqualTo(ParentOrderState.PENDING);
        assertThat(registry.activeParents()).isEqualTo(1);

        assertThat(registry.transition(101L, ParentOrderState.WORKING, ParentOrderState.REASON_NONE, 20L)).isTrue();
        assertThat(registry.updateFill(101L, 250L, 750L, 100_000L)).isTrue();

        assertThat(state.status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(state.filledQtyScaled()).isEqualTo(250L);
        assertThat(state.remainingQtyScaled()).isEqualTo(750L);
        assertThat(state.averageFillPriceScaled()).isEqualTo(100_000L);
        assertThat(state.lastTransitionClusterTime()).isEqualTo(20L);
    }

    @Test
    void duplicateClaimReturnsExistingAndIncrementsCounter() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(2, 4);
        final ParentOrderState first = registry.claim(101L, 7, 11, 1_000L, 10L);

        final ParentOrderState second = registry.claim(101L, 8, 12, 2_000L, 20L);

        assertThat(second).isSameAs(first);
        assertThat(second.strategyId()).isEqualTo(7);
        assertThat(registry.duplicateParentClaims()).isEqualTo(1L);
        assertThat(registry.activeParents()).isEqualTo(1);
    }

    @Test
    void parentCapacityFullReturnsNullAndCountsReject() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(1, 4);

        assertThat(registry.claim(101L, 7, 11, 1_000L, 10L)).isNotNull();
        assertThat(registry.claim(102L, 7, 11, 1_000L, 11L)).isNull();

        assertThat(registry.parentCapacityRejects()).isEqualTo(1L);
        assertThat(registry.activeParents()).isEqualTo(1);
    }

    @Test
    void invalidParentClaimReturnsNullAndCountsReject() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(1, 4);

        assertThat(registry.claim(0L, 7, 11, 1_000L, 10L)).isNull();
        assertThat(registry.invalidParentRequests()).isEqualTo(1L);
    }

    @Test
    void childLinkUnlinkAndActiveChildLookupAreDeterministic() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(2, 4);
        registry.claim(101L, 7, 11, 1_000L, 10L);

        assertThat(registry.linkChild(101L, 9001L)).isTrue();
        assertThat(registry.linkChild(101L, 9002L)).isTrue();

        assertThat(registry.parentOrderIdByChild(9001L)).isEqualTo(101L);
        assertThat(registry.activeChildCount(101L)).isEqualTo(2);
        final long[] childIds = new long[4];
        assertThat(registry.copyActiveChildIds(101L, childIds)).isEqualTo(2);
        assertThat(childIds[0]).isEqualTo(9001L);
        assertThat(childIds[1]).isEqualTo(9002L);

        assertThat(registry.unlinkChild(9001L)).isEqualTo(101L);
        assertThat(registry.parentOrderIdByChild(9001L)).isZero();
        assertThat(registry.activeChildCount(101L)).isEqualTo(1);
    }

    @Test
    void duplicateUnknownAndCapacityChildLinksUseCounters() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(2, 1);
        registry.claim(101L, 7, 11, 1_000L, 10L);

        assertThat(registry.linkChild(999L, 9000L)).isFalse();
        assertThat(registry.unknownParentLinks()).isEqualTo(1L);

        assertThat(registry.linkChild(101L, 9001L)).isTrue();
        assertThat(registry.linkChild(101L, 9001L)).isFalse();
        assertThat(registry.duplicateChildLinks()).isEqualTo(1L);

        assertThat(registry.linkChild(101L, 9002L)).isFalse();
        assertThat(registry.childLinkCapacityRejects()).isEqualTo(1L);

        assertThat(registry.unlinkChild(7777L)).isZero();
        assertThat(registry.unknownChildUnlinks()).isEqualTo(1L);
    }

    @Test
    void terminalReasonAndReleaseRequireNoActiveChildren() {
        final ParentOrderRegistry registry = new ParentOrderRegistry(2, 2);
        registry.claim(101L, 7, 11, 1_000L, 10L);
        registry.linkChild(101L, 9001L);

        assertThat(registry.transition(101L, ParentOrderState.FAILED, ParentOrderState.REASON_HEDGE_FAILED, 20L)).isTrue();
        assertThat(registry.lookup(101L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_HEDGE_FAILED);
        assertThat(registry.releaseTerminal(101L)).isFalse();

        assertThat(registry.unlinkChild(9001L)).isEqualTo(101L);
        assertThat(registry.releaseTerminal(101L)).isTrue();
        assertThat(registry.lookup(101L)).isNull();
        assertThat(registry.activeParents()).isZero();
    }

    @Test
    void snapshotLoadRoundTripPreservesStateLinksAndCounters() {
        final ParentOrderRegistry source = new ParentOrderRegistry(2, 3);
        source.claim(101L, 7, 11, 1_000L, 10L);
        source.transition(101L, ParentOrderState.PARTIALLY_FILLED, ParentOrderState.REASON_NONE, 20L);
        source.updateFill(101L, 100L, 900L, 50L);
        source.linkChild(101L, 9001L);
        source.linkChild(999L, 9002L);
        source.unlinkChild(777L);

        final ParentOrderRegistry.Snapshot snapshot = source.newSnapshot();
        source.snapshotInto(snapshot);

        final ParentOrderRegistry target = new ParentOrderRegistry(2, 3);
        target.loadFrom(snapshot);

        final ParentOrderState loaded = target.lookup(101L);
        assertThat(loaded).isNotNull();
        assertThat(loaded.status()).isEqualTo(ParentOrderState.PARTIALLY_FILLED);
        assertThat(loaded.filledQtyScaled()).isEqualTo(100L);
        assertThat(target.parentOrderIdByChild(9001L)).isEqualTo(101L);
        assertThat(target.unknownParentLinks()).isEqualTo(1L);
        assertThat(target.unknownChildUnlinks()).isEqualTo(1L);
    }

    @Test
    void constructorRejectsInvalidCapacitiesAndSnapshotMismatch() {
        assertThatThrownBy(() -> new ParentOrderRegistry(0, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parentCapacity");
        assertThatThrownBy(() -> new ParentOrderRegistry(1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("childLinkCapacity");

        final ParentOrderRegistry source = new ParentOrderRegistry(2, 2);
        final ParentOrderRegistry target = new ParentOrderRegistry(1, 2);
        assertThatThrownBy(() -> target.loadFrom(source.newSnapshot()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("snapshot capacity mismatch");
    }
}
