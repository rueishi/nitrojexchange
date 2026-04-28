package ig.rueishi.nitroj.exchange.gateway.venue;

import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;

/**
 * Venue-owned adapter for outbound order-entry commands.
 *
 * <p>Responsibility: translates normalized NitroJEx order commands into the
 * outbound protocol messages required by one venue. Role in system:
 * {@code ExecutionRouterImpl} delegates to this interface so gateway core no
 * longer imports generated FIX encoder classes directly. Relationships: standard
 * implementations can share FIX encoding code while venue policies provide
 * required-field and replace-behavior differences. Lifecycle: one adapter is
 * created for an acquired Artio session and reused on the gateway egress thread.
 * Design intent: keep generated FIX classes and venue-specific order-entry
 * semantics behind a narrow, testable boundary.
 */
public interface VenueOrderEntryAdapter {
    void sendNewOrder(NewOrderCommandDecoder command);

    void sendCancel(CancelOrderCommandDecoder command);

    void sendReplace(ReplaceOrderCommandDecoder command);

    void sendStatusQuery(OrderStatusQueryCommandDecoder command);
}
