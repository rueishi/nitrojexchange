package ig.rueishi.nitroj.exchange.simulator;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies programmable fill, reject, delay, and disconnect scenarios.
 *
 * <p>Responsibility: owns the mapping from {@link CoinbaseExchangeSimulator.FillMode}
 * to concrete simulated execution behavior. Role in system: tests choose a fill
 * mode through the simulator builder, and this controller decides which execution
 * reports/counters/state transitions follow each new order. Relationships: it
 * writes observable results back through {@link CoinbaseExchangeSimulator} and
 * mutates {@link SimulatorOrderBook} state. Lifecycle: created with the simulator
 * and reused until the simulator stops. Design intent: keep scenario branching out
 * of transport/session code so tests can reason about behavior directly.
 */
public final class ScenarioController {
    private final CoinbaseExchangeSimulator simulator;
    private final ScheduledExecutorService scheduler;

    ScenarioController(final CoinbaseExchangeSimulator simulator, final ScheduledExecutorService scheduler) {
        this.simulator = simulator;
        this.scheduler = scheduler;
    }

    /**
     * Executes the configured scenario for a newly acknowledged order.
     *
     * @param order order that has just been accepted by the simulator
     */
    public void applyNewOrderScenario(final SimulatorOrderBook.SimOrder order) {
        switch (simulator.fillMode()) {
            case IMMEDIATE -> simulator.recordFill(order, order.limitPrice(), order.qty(), true);
            case PARTIAL_THEN_FULL -> {
                double halfQty = order.qty() / 2.0;
                simulator.recordFill(order, order.limitPrice(), halfQty, false);
                scheduler.schedule(() -> simulator.recordFill(order, order.limitPrice(), order.qty() - halfQty, true),
                    100, TimeUnit.MILLISECONDS);
            }
            case REJECT_ALL -> simulator.recordReject(order, 0, "Test reject");
            case NO_FILL -> {
                // Order remains pending for timeout and cancel-path tests.
            }
            case DELAYED_FILL -> scheduler.schedule(
                () -> simulator.recordFill(order, order.limitPrice(), order.qty(), true),
                simulator.config().fillDelayMs(),
                TimeUnit.MILLISECONDS);
            case DISCONNECT_ON_FILL -> scheduleDisconnect(0);
        }
    }

    /**
     * Schedules a simulator session drop.
     *
     * @param delayMs delay before marking the simulator disconnected
     */
    public void scheduleDisconnect(final long delayMs) {
        scheduler.schedule(() -> simulator.markDisconnected(false), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules a simulator session drop with optional cancel-on-logout behavior.
     *
     * @param delayMs delay before disconnect
     * @param cancelAllOnLogout whether pending orders should be cleared on disconnect
     */
    public void scheduleDisconnect(final long delayMs, final boolean cancelAllOnLogout) {
        scheduler.schedule(() -> simulator.markDisconnected(cancelAllOnLogout), delayMs, TimeUnit.MILLISECONDS);
    }
}
