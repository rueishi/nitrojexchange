package ig.rueishi.nitroj.exchange.execution;

import java.util.Objects;

/**
 * Bounded registry for deterministic parent-order state.
 *
 * <p>The registry preallocates parent states and child-link arrays at
 * construction time. Normal claim, lookup, transition, link, unlink, and
 * snapshot-copy operations mutate primitive arrays only. Capacity breaches are
 * reported through counters and false/null return values instead of exceptions
 * so expected lifecycle failures stay deterministic and allocation-free.</p>
 */
public final class ParentOrderRegistry {
    public static final int DEFAULT_PARENT_CAPACITY = 2048;
    public static final int DEFAULT_CHILD_LINK_CAPACITY = 4096;

    private final ParentOrderState[] states;
    private final long[] linkedParentOrderIds;
    private final long[] linkedChildClOrdIds;
    private final boolean[] childLinkActive;

    private int activeParents;
    private int activeChildLinks;
    private long parentCapacityRejects;
    private long childLinkCapacityRejects;
    private long duplicateParentClaims;
    private long duplicateChildLinks;
    private long unknownParentLinks;
    private long unknownChildUnlinks;
    private long invalidParentRequests;

    public ParentOrderRegistry() {
        this(DEFAULT_PARENT_CAPACITY, DEFAULT_CHILD_LINK_CAPACITY);
    }

    public ParentOrderRegistry(final int parentCapacity, final int childLinkCapacity) {
        if (parentCapacity <= 0) {
            throw new IllegalArgumentException("parentCapacity must be positive");
        }
        if (childLinkCapacity <= 0) {
            throw new IllegalArgumentException("childLinkCapacity must be positive");
        }
        states = new ParentOrderState[parentCapacity];
        for (int i = 0; i < states.length; i++) {
            states[i] = new ParentOrderState();
        }
        linkedParentOrderIds = new long[childLinkCapacity];
        linkedChildClOrdIds = new long[childLinkCapacity];
        childLinkActive = new boolean[childLinkCapacity];
    }

    public ParentOrderState claim(
        final long parentOrderId,
        final int strategyId,
        final int executionStrategyId,
        final long requestedQtyScaled,
        final long clusterTimeMicros) {
        if (parentOrderId <= 0L) {
            invalidParentRequests++;
            return null;
        }

        final ParentOrderState existing = lookup(parentOrderId);
        if (existing != null) {
            duplicateParentClaims++;
            return existing;
        }

        for (ParentOrderState state : states) {
            if (!state.active) {
                state.init(parentOrderId, strategyId, executionStrategyId, requestedQtyScaled, clusterTimeMicros);
                activeParents++;
                return state;
            }
        }

        parentCapacityRejects++;
        return null;
    }

    public ParentOrderState lookup(final long parentOrderId) {
        if (parentOrderId <= 0L) {
            return null;
        }
        for (ParentOrderState state : states) {
            if (state.active && state.parentOrderId == parentOrderId) {
                return state;
            }
        }
        return null;
    }

    public boolean transition(
        final long parentOrderId,
        final byte status,
        final byte reasonCode,
        final long clusterTimeMicros) {
        final ParentOrderState state = lookup(parentOrderId);
        if (state == null) {
            return false;
        }
        state.transition(status, reasonCode, clusterTimeMicros);
        return true;
    }

    public boolean updateFill(
        final long parentOrderId,
        final long filledQtyScaled,
        final long remainingQtyScaled,
        final long averageFillPriceScaled) {
        final ParentOrderState state = lookup(parentOrderId);
        if (state == null) {
            return false;
        }
        state.fill(filledQtyScaled, remainingQtyScaled, averageFillPriceScaled);
        return true;
    }

    public boolean linkChild(final long parentOrderId, final long childClOrdId) {
        if (lookup(parentOrderId) == null) {
            unknownParentLinks++;
            return false;
        }
        if (parentOrderIdByChild(childClOrdId) != 0L) {
            duplicateChildLinks++;
            return false;
        }
        for (int i = 0; i < childLinkActive.length; i++) {
            if (!childLinkActive[i]) {
                linkedParentOrderIds[i] = parentOrderId;
                linkedChildClOrdIds[i] = childClOrdId;
                childLinkActive[i] = true;
                activeChildLinks++;
                return true;
            }
        }
        childLinkCapacityRejects++;
        return false;
    }

    public long unlinkChild(final long childClOrdId) {
        for (int i = 0; i < childLinkActive.length; i++) {
            if (childLinkActive[i] && linkedChildClOrdIds[i] == childClOrdId) {
                final long parentOrderId = linkedParentOrderIds[i];
                childLinkActive[i] = false;
                linkedParentOrderIds[i] = 0L;
                linkedChildClOrdIds[i] = 0L;
                activeChildLinks--;
                return parentOrderId;
            }
        }
        unknownChildUnlinks++;
        return 0L;
    }

    public long parentOrderIdByChild(final long childClOrdId) {
        for (int i = 0; i < childLinkActive.length; i++) {
            if (childLinkActive[i] && linkedChildClOrdIds[i] == childClOrdId) {
                return linkedParentOrderIds[i];
            }
        }
        return 0L;
    }

    public int activeChildCount(final long parentOrderId) {
        int count = 0;
        for (int i = 0; i < childLinkActive.length; i++) {
            if (childLinkActive[i] && linkedParentOrderIds[i] == parentOrderId) {
                count++;
            }
        }
        return count;
    }

    public int copyActiveChildIds(final long parentOrderId, final long[] destination) {
        Objects.requireNonNull(destination, "destination");
        int count = 0;
        for (int i = 0; i < childLinkActive.length && count < destination.length; i++) {
            if (childLinkActive[i] && linkedParentOrderIds[i] == parentOrderId) {
                destination[count++] = linkedChildClOrdIds[i];
            }
        }
        return count;
    }

    public boolean releaseTerminal(final long parentOrderId) {
        final ParentOrderState state = lookup(parentOrderId);
        if (state == null || !state.terminal() || activeChildCount(parentOrderId) != 0) {
            return false;
        }
        state.reset();
        activeParents--;
        return true;
    }

    ParentOrderState[] statesForEngine() {
        return states;
    }

    public Snapshot newSnapshot() {
        return new Snapshot(states.length, childLinkActive.length);
    }

    public void snapshotInto(final Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        snapshot.resetCounters();
        for (int i = 0; i < states.length; i++) {
            final ParentOrderState state = states[i];
            snapshot.parentActive[i] = state.active;
            snapshot.parentOrderIds[i] = state.parentOrderId;
            snapshot.strategyIds[i] = state.strategyId;
            snapshot.executionStrategyIds[i] = state.executionStrategyId;
            snapshot.requestedQtyScaled[i] = state.requestedQtyScaled;
            snapshot.filledQtyScaled[i] = state.filledQtyScaled;
            snapshot.remainingQtyScaled[i] = state.remainingQtyScaled;
            snapshot.averageFillPriceScaled[i] = state.averageFillPriceScaled;
            snapshot.statuses[i] = state.status;
            snapshot.terminalReasonCodes[i] = state.terminalReasonCode;
            snapshot.lastTransitionClusterTimes[i] = state.lastTransitionClusterTime;
            snapshot.primaryTimerCorrelationIds[i] = state.primaryTimerCorrelationId;
            if (state.active) {
                snapshot.activeParents++;
            }
        }
        for (int i = 0; i < childLinkActive.length; i++) {
            snapshot.childLinkActive[i] = childLinkActive[i];
            snapshot.linkedParentOrderIds[i] = linkedParentOrderIds[i];
            snapshot.linkedChildClOrdIds[i] = linkedChildClOrdIds[i];
            if (childLinkActive[i]) {
                snapshot.activeChildLinks++;
            }
        }
        snapshot.parentCapacityRejects = parentCapacityRejects;
        snapshot.childLinkCapacityRejects = childLinkCapacityRejects;
        snapshot.duplicateParentClaims = duplicateParentClaims;
        snapshot.duplicateChildLinks = duplicateChildLinks;
        snapshot.unknownParentLinks = unknownParentLinks;
        snapshot.unknownChildUnlinks = unknownChildUnlinks;
        snapshot.invalidParentRequests = invalidParentRequests;
    }

    public void loadFrom(final Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.parentOrderIds.length != states.length || snapshot.linkedChildClOrdIds.length != childLinkActive.length) {
            throw new IllegalArgumentException("snapshot capacity mismatch");
        }
        reset();
        activeParents = snapshot.activeParents;
        activeChildLinks = snapshot.activeChildLinks;
        for (int i = 0; i < states.length; i++) {
            final ParentOrderState state = states[i];
            state.active = snapshot.parentActive[i];
            state.parentOrderId = snapshot.parentOrderIds[i];
            state.strategyId = snapshot.strategyIds[i];
            state.executionStrategyId = snapshot.executionStrategyIds[i];
            state.requestedQtyScaled = snapshot.requestedQtyScaled[i];
            state.filledQtyScaled = snapshot.filledQtyScaled[i];
            state.remainingQtyScaled = snapshot.remainingQtyScaled[i];
            state.averageFillPriceScaled = snapshot.averageFillPriceScaled[i];
            state.status = snapshot.statuses[i];
            state.terminalReasonCode = snapshot.terminalReasonCodes[i];
            state.lastTransitionClusterTime = snapshot.lastTransitionClusterTimes[i];
            state.primaryTimerCorrelationId = snapshot.primaryTimerCorrelationIds[i];
        }
        for (int i = 0; i < childLinkActive.length; i++) {
            childLinkActive[i] = snapshot.childLinkActive[i];
            linkedParentOrderIds[i] = snapshot.linkedParentOrderIds[i];
            linkedChildClOrdIds[i] = snapshot.linkedChildClOrdIds[i];
        }
        parentCapacityRejects = snapshot.parentCapacityRejects;
        childLinkCapacityRejects = snapshot.childLinkCapacityRejects;
        duplicateParentClaims = snapshot.duplicateParentClaims;
        duplicateChildLinks = snapshot.duplicateChildLinks;
        unknownParentLinks = snapshot.unknownParentLinks;
        unknownChildUnlinks = snapshot.unknownChildUnlinks;
        invalidParentRequests = snapshot.invalidParentRequests;
    }

    public void reset() {
        for (ParentOrderState state : states) {
            state.reset();
        }
        for (int i = 0; i < childLinkActive.length; i++) {
            childLinkActive[i] = false;
            linkedParentOrderIds[i] = 0L;
            linkedChildClOrdIds[i] = 0L;
        }
        activeParents = 0;
        activeChildLinks = 0;
        parentCapacityRejects = 0L;
        childLinkCapacityRejects = 0L;
        duplicateParentClaims = 0L;
        duplicateChildLinks = 0L;
        unknownParentLinks = 0L;
        unknownChildUnlinks = 0L;
        invalidParentRequests = 0L;
    }

    public int parentCapacity() {
        return states.length;
    }

    public int childLinkCapacity() {
        return childLinkActive.length;
    }

    public int activeParents() {
        return activeParents;
    }

    public int activeChildLinks() {
        return activeChildLinks;
    }

    public long parentCapacityRejects() {
        return parentCapacityRejects;
    }

    public long childLinkCapacityRejects() {
        return childLinkCapacityRejects;
    }

    public long duplicateParentClaims() {
        return duplicateParentClaims;
    }

    public long duplicateChildLinks() {
        return duplicateChildLinks;
    }

    public long unknownParentLinks() {
        return unknownParentLinks;
    }

    public long unknownChildUnlinks() {
        return unknownChildUnlinks;
    }

    public long invalidParentRequests() {
        return invalidParentRequests;
    }

    /**
     * Cold-path snapshot carrier used by deterministic replay and tests.
     */
    public static final class Snapshot {
        final boolean[] parentActive;
        final long[] parentOrderIds;
        final int[] strategyIds;
        final int[] executionStrategyIds;
        final long[] requestedQtyScaled;
        final long[] filledQtyScaled;
        final long[] remainingQtyScaled;
        final long[] averageFillPriceScaled;
        final byte[] statuses;
        final byte[] terminalReasonCodes;
        final long[] lastTransitionClusterTimes;
        final long[] primaryTimerCorrelationIds;
        final boolean[] childLinkActive;
        final long[] linkedParentOrderIds;
        final long[] linkedChildClOrdIds;
        int activeParents;
        int activeChildLinks;
        long parentCapacityRejects;
        long childLinkCapacityRejects;
        long duplicateParentClaims;
        long duplicateChildLinks;
        long unknownParentLinks;
        long unknownChildUnlinks;
        long invalidParentRequests;

        Snapshot(final int parentCapacity, final int childLinkCapacity) {
            parentActive = new boolean[parentCapacity];
            parentOrderIds = new long[parentCapacity];
            strategyIds = new int[parentCapacity];
            executionStrategyIds = new int[parentCapacity];
            requestedQtyScaled = new long[parentCapacity];
            filledQtyScaled = new long[parentCapacity];
            remainingQtyScaled = new long[parentCapacity];
            averageFillPriceScaled = new long[parentCapacity];
            statuses = new byte[parentCapacity];
            terminalReasonCodes = new byte[parentCapacity];
            lastTransitionClusterTimes = new long[parentCapacity];
            primaryTimerCorrelationIds = new long[parentCapacity];
            childLinkActive = new boolean[childLinkCapacity];
            linkedParentOrderIds = new long[childLinkCapacity];
            linkedChildClOrdIds = new long[childLinkCapacity];
        }

        private void resetCounters() {
            activeParents = 0;
            activeChildLinks = 0;
        }
    }
}
