package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;

/**
 * Gateway boundary for routing decoded cluster order commands into execution venues.
 *
 * <p>Responsibility: define the narrow command-routing contract used by
 * {@link OrderCommandHandler} after it has decoded SBE messages from cluster
 * egress. Role in system: this interface separates Aeron/SBE fragment parsing
 * from FIX-specific message construction and Artio back-pressure handling.
 * Relationships: generated command decoders come from the platform-common SBE
 * schema, while implementations such as {@link ExecutionRouterImpl} translate
 * those commands into venue FIX encoders. Lifecycle: one implementation is
 * created during gateway wiring and then called repeatedly on the gateway egress
 * thread. Design intent: keep the parsing loop allocation-free and protocol
 * agnostic while allowing venue-specific execution routing to evolve behind a
 * small surface.</p>
 */
public interface ExecutionRouter {
    /**
     * Routes a decoded new-order command to the configured execution venue.
     *
     * @param command decoder positioned on a complete {@code NewOrderCommand}
     */
    void routeNewOrder(NewOrderCommandDecoder command);

    /**
     * Routes a decoded cancel command to the configured execution venue.
     *
     * @param command decoder positioned on a complete {@code CancelOrderCommand}
     */
    void routeCancel(CancelOrderCommandDecoder command);

    /**
     * Routes a decoded replace command to the configured execution venue.
     *
     * @param command decoder positioned on a complete {@code ReplaceOrderCommand}
     */
    void routeReplace(ReplaceOrderCommandDecoder command);

    /**
     * Routes a decoded order-status query to the configured execution venue.
     *
     * @param command decoder positioned on a complete {@code OrderStatusQueryCommand}
     */
    void routeStatusQuery(OrderStatusQueryCommandDecoder command);
}
