package ig.rueishi.nitroj.exchange.gateway;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * Preallocated event slot carried through the gateway Disruptor ring.
 *
 * <p>Responsibility: stores one encoded gateway-to-cluster message plus the
 * Disruptor sequence used to publish the claimed slot. Role in system: producer
 * threads claim a slot, encode into {@link #buffer}, set {@link #length}, and
 * publish it to the single gateway-disruptor consumer. Relationships:
 * {@link GatewayDisruptor} owns slot allocation and lifecycle, while later
 * producer cards write SBE/FIX-derived payloads into this buffer. Lifecycle:
 * slots are created once with the ring buffer, reused indefinitely, and reset
 * after every consume. Design intent: keep handoff allocation-free and avoid
 * false sharing between producer-written fields and consumer reads.
 */
@jdk.internal.vm.annotation.Contended
public final class GatewaySlot {
    public static final int DEFAULT_SLOT_SIZE_BYTES = 512;

    /** Preallocated payload buffer for one gateway event. */
    public final UnsafeBuffer buffer;

    /** Number of meaningful bytes currently encoded in {@link #buffer}. */
    public int length = 0;

    /** Disruptor sequence captured by {@link GatewayDisruptor#claimSlot()}. */
    public long sequence = -1;

    public GatewaySlot() {
        this(DEFAULT_SLOT_SIZE_BYTES);
    }

    public GatewaySlot(final int slotSizeBytes) {
        this.buffer = new UnsafeBuffer(new byte[slotSizeBytes]);
    }

    /**
     * Clears the slot after consumption so reused slots never expose stale data.
     *
     * <p>The buffer bytes are deliberately not zeroed because the authoritative
     * stale-data guard is {@link #length}; producers overwrite from offset zero on
     * each claim. This method is called by the Disruptor consumer wrapper in a
     * {@code finally} block after every event.
     */
    public void reset() {
        length = 0;
        sequence = -1;
    }
}
