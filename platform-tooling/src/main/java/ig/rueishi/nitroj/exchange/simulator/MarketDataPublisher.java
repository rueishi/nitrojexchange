package ig.rueishi.nitroj.exchange.simulator;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically publishes synthetic bid/ask ticks into simulator-visible state.
 *
 * <p>Responsibility: models the simulator's market-data stream with deterministic
 * counters and latest-tick fields for tests. Role in system: later gateway E2E
 * tests can treat these ticks as the source of FIX MsgType=W/X behavior, while
 * TASK-006 unit tests assert that configured bid/ask values are emitted.
 * Relationships: reads current market values from {@link CoinbaseExchangeSimulator}
 * and records ticks back on the simulator. Lifecycle: started by
 * {@link CoinbaseExchangeSimulator#start()} and stopped by
 * {@link CoinbaseExchangeSimulator#stop()}. Design intent: make timing explicit
 * and bounded without introducing cluster or Aeron dependencies.
 */
public final class MarketDataPublisher {
    private final CoinbaseExchangeSimulator simulator;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    MarketDataPublisher(final CoinbaseExchangeSimulator simulator, final ScheduledExecutorService scheduler) {
        this.simulator = simulator;
        this.scheduler = scheduler;
    }

    /**
     * Starts fixed-rate publication of synthetic market data.
     *
     * @param intervalMs tick interval in milliseconds
     */
    public void startPublishing(final long intervalMs) {
        task = scheduler.scheduleAtFixedRate(this::publishTick, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops scheduled publication.
     */
    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
    }

    private void publishTick() {
        if (simulator.isConnected()) {
            simulator.recordMarketDataTick();
        }
    }
}
