package ig.rueishi.nitroj.exchange.gateway;

import net.openhft.affinity.AffinityLock;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Non-fatal CPU affinity helper for gateway runtime threads.
 *
 * <p>Responsibility: centralize the optional CPU-pinning behavior used by the
 * gateway poll loops. Role in system: {@link ArtioLibraryLoop} and
 * {@link EgressPollLoop} call this during thread startup so production configs
 * can isolate latency-sensitive loops while tests and development configs can
 * disable pinning with CPU {@code 0}. Relationships: CPU IDs come from
 * {@link ig.rueishi.nitroj.exchange.common.CpuConfig}, OpenHFT Affinity performs the
 * operating-system binding, and failures are logged rather than surfaced to
 * gateway startup. Lifecycle: called once per loop thread before entering the
 * busy-spin polling loop. Design intent: affinity improves production
 * determinism, but it must never make local/dev startup brittle on machines
 * without pinning permissions.</p>
 */
public final class ThreadAffinityHelper {
    private static final Logger LOGGER = System.getLogger(ThreadAffinityHelper.class.getName());

    private ThreadAffinityHelper() {
    }

    /**
     * Pins a thread to a positive CPU ID when configured.
     *
     * <p>CPU IDs less than or equal to zero are the explicit no-pinning mode used
     * by tests. OpenHFT or OS-level errors are logged and swallowed because the
     * gateway can still run correctly, just with less deterministic scheduling.</p>
     *
     * @param thread thread being pinned, used for diagnostic logging
     * @param cpu configured CPU ID, or {@code 0} to disable pinning
     */
    public static void pin(final Thread thread, final int cpu) {
        if (cpu <= 0) {
            return;
        }
        try {
            AffinityLock.acquireLock(cpu);
        } catch (final RuntimeException | Error ex) {
            LOGGER.log(
                Level.WARNING,
                "CPU pinning failed for {0} to CPU {1}: {2}",
                thread.getName(),
                cpu,
                ex.getMessage());
        }
    }
}
