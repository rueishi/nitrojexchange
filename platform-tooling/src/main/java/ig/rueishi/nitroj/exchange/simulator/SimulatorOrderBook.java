package ig.rueishi.nitroj.exchange.simulator;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory order book for simulator-owned orders.
 *
 * <p>Responsibility: tracks pending simulated orders by client order ID and
 * applies fills, rejects, and cancels. Role in system: the simulator session
 * handler records inbound FIX order flow here before scenario logic emits
 * responses. Relationships: {@link ScenarioController} mutates orders through
 * this book, while {@link CoinbaseExchangeSimulator} exposes assertion views.
 * Lifecycle: created with the simulator, cleared by {@link #reset()}, and used
 * only for deterministic tests. Design intent: model just enough venue state for
 * TASK-006 without introducing cluster or Aeron dependencies.
 */
public final class SimulatorOrderBook {
    private final ConcurrentHashMap<String, SimOrder> pendingOrders = new ConcurrentHashMap<>();

    public void add(final SimOrder order) {
        pendingOrders.put(order.clOrdId(), order);
    }

    public SimOrder get(final String clOrdId) {
        return pendingOrders.get(clOrdId);
    }

    public SimOrder remove(final String clOrdId) {
        return pendingOrders.remove(clOrdId);
    }

    public int pendingOrderCount() {
        return pendingOrders.size();
    }

    public Collection<SimOrder> pendingOrders() {
        return pendingOrders.values();
    }

    public void reset() {
        pendingOrders.clear();
    }

    /**
     * Mutable simulator order state.
     *
     * @param clOrdId client order ID
     * @param venueOrderId simulator-assigned venue order ID
     * @param symbol FIX symbol
     * @param side FIX side value represented as BUY/SELL for test readability
     * @param limitPrice order limit price
     * @param qty original order quantity
     */
    public record SimOrder(
        String clOrdId,
        String venueOrderId,
        String symbol,
        String side,
        double limitPrice,
        double qty
    ) {
    }
}
