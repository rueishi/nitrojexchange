package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.CpuConfig;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.util.Objects;

/**
 * Busy-spin poll loop for Aeron cluster egress commands.
 *
 * <p>Responsibility: poll the cluster egress {@link Subscription} and dispatch
 * fragments to {@link OrderCommandHandler}. Role in system: this loop is the
 * gateway-egress-thread consumer for cluster-originated commands such as order
 * routing, cancel routing, status queries, and balance requests. Relationships:
 * the preallocated {@link FragmentHandler} delegates to the task-013 command
 * handler, {@link ThreadAffinityHelper} applies optional CPU pinning, and Aeron
 * owns the subscription. Lifecycle: gateway startup creates the loop, starts it
 * on its own thread, and calls {@link #stop()} during shutdown. Design intent:
 * avoid per-poll lambda allocation by preallocating the fragment handler and use
 * a busy-spin idle strategy for the low-latency command path.</p>
 */
public final class EgressPollLoop implements Runnable {
    static final int FRAGMENT_LIMIT = 10;

    private final Subscription egressSubscription;
    private final OrderCommandHandler commandHandler;
    private final CpuConfig cpuConfig;
    private final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
    private final FragmentHandler handler;
    private volatile boolean running = true;

    /**
     * Creates the cluster-egress poll loop.
     *
     * @param egressSubscription Aeron subscription carrying cluster egress fragments
     * @param commandHandler fragment decoder and router
     * @param cpuConfig CPU pinning configuration
     */
    public EgressPollLoop(
        final Subscription egressSubscription,
        final OrderCommandHandler commandHandler,
        final CpuConfig cpuConfig) {
        this.egressSubscription = Objects.requireNonNull(egressSubscription, "egressSubscription");
        this.commandHandler = Objects.requireNonNull(commandHandler, "commandHandler");
        this.cpuConfig = Objects.requireNonNull(cpuConfig, "cpuConfig");
        this.handler = this.commandHandler::onFragment;
    }

    /**
     * Pins the current thread if configured and polls egress until stopped.
     */
    @Override
    public void run() {
        ThreadAffinityHelper.pin(Thread.currentThread(), cpuConfig.gatewayEgressThread());
        while (running) {
            final int workCount = egressSubscription.poll(handler, FRAGMENT_LIMIT);
            idleStrategy.idle(workCount);
        }
    }

    /**
     * Requests cooperative loop shutdown.
     */
    public void stop() {
        running = false;
    }
}
