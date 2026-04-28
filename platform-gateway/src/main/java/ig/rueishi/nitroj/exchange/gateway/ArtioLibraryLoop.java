package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.CpuConfig;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import uk.co.real_logic.artio.library.FixLibrary;

import java.util.Objects;

/**
 * Busy-spin poll loop for Artio library duty-cycle work.
 *
 * <p>Responsibility: repeatedly call {@link FixLibrary#poll(int)} on the
 * dedicated {@code artio-library} thread. Role in system: this loop keeps Artio
 * session IO, message callbacks, and library state transitions progressing while
 * other gateway threads handle cluster egress and REST work. Relationships:
 * {@link ThreadAffinityHelper} applies optional CPU pinning from
 * {@link CpuConfig}, and Artio owns the library resource closed when the loop
 * exits. Lifecycle: gateway startup creates the loop, starts it on its own
 * thread, and later calls {@link #stop()} during shutdown. Design intent:
 * preconfigure a fixed fragment limit and busy-spin idle strategy for low
 * latency, with shutdown controlled by a volatile flag.</p>
 */
public final class ArtioLibraryLoop implements Runnable {
    static final int FRAGMENT_LIMIT = 10;

    private final FixLibrary fixLibrary;
    private final CpuConfig cpuConfig;
    private final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
    private volatile boolean running = true;

    /**
     * Creates the Artio poll loop.
     *
     * @param fixLibrary Artio library to drive and close on exit
     * @param cpuConfig CPU pinning configuration
     */
    public ArtioLibraryLoop(final FixLibrary fixLibrary, final CpuConfig cpuConfig) {
        this.fixLibrary = Objects.requireNonNull(fixLibrary, "fixLibrary");
        this.cpuConfig = Objects.requireNonNull(cpuConfig, "cpuConfig");
    }

    /**
     * Pins the current thread if configured, polls Artio until stopped, then closes the library.
     */
    @Override
    public void run() {
        ThreadAffinityHelper.pin(Thread.currentThread(), cpuConfig.artioLibraryThread());
        try {
            while (running) {
                final int workCount = fixLibrary.poll(FRAGMENT_LIMIT);
                idleStrategy.idle(workCount);
            }
        } finally {
            fixLibrary.close();
        }
    }

    /**
     * Requests cooperative loop shutdown.
     */
    public void stop() {
        running = false;
    }
}
