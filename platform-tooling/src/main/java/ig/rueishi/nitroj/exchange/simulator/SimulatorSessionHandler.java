package ig.rueishi.nitroj.exchange.simulator;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulator-side handler for inbound FIX-like test messages.
 *
 * <p>Responsibility: translates test/client actions into simulator order-book and
 * scenario calls. Role in system: this is the narrow boundary that a future Artio
 * acceptor callback can delegate to; TASK-006 tests call through the simulator's
 * public methods, which in turn use this handler. Relationships: it records
 * orders and cancels in {@link CoinbaseExchangeSimulator}, updates
 * {@link SimulatorOrderBook}, and exposes back-pressure counters for diagnostics.
 * Lifecycle: created once with the simulator and reset with simulator state.
 * Design intent: isolate protocol-routing decisions from assertion helpers and
 * scenario branching.
 */
public final class SimulatorSessionHandler {
    private final CoinbaseExchangeSimulator simulator;
    private final AtomicInteger backPressureCount = new AtomicInteger();

    SimulatorSessionHandler(final CoinbaseExchangeSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * Handles a test logon event.
     */
    public void onLogon() {
        simulator.recordLogon();
    }

    /**
     * Handles a simulated NewOrderSingle.
     *
     * @param clOrdId client order ID
     * @param side order side text
     * @param symbol FIX symbol
     * @param limitPrice limit price
     * @param qty order quantity
     */
    public void onNewOrderSingle(
        final String clOrdId,
        final String side,
        final String symbol,
        final double limitPrice,
        final double qty
    ) {
        simulator.acceptOrder(clOrdId, side, symbol, limitPrice, qty);
    }

    /**
     * Handles a simulated OrderCancelRequest.
     *
     * @param clOrdId cancel client order ID
     * @param origClOrdId original order client ID
     */
    public void onOrderCancelRequest(final String clOrdId, final String origClOrdId) {
        simulator.acceptCancel(clOrdId, origClOrdId);
    }

    /**
     * Handles a simulated market-data subscription request.
     *
     * @param marketDataModel requested model, currently {@code L2} or {@code L3}
     */
    public void onMarketDataSubscribe(final String marketDataModel) {
        simulator.acceptMarketDataSubscribe(marketDataModel);
    }

    /**
     * Counts a simulated back-pressure failure.
     */
    public void recordBackPressure() {
        backPressureCount.incrementAndGet();
    }

    public int backPressureCount() {
        return backPressureCount.get();
    }
}
