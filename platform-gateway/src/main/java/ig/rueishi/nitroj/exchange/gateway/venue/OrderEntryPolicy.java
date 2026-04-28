package ig.rueishi.nitroj.exchange.gateway.venue;

import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderCancelReplaceRequestEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderCancelRequestEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderStatusRequestEncoder;

/**
 * Venue-specific policy hooks for standard FIX order-entry encoding.
 *
 * <p>Responsibility: lets a venue decide optional/proprietary order-entry
 * behavior while the standard adapter owns common FIX field mapping. Role in
 * system: {@link StandardOrderEntryAdapter} calls this policy after writing
 * shared fields and before sending the generated Artio encoder. Relationships:
 * concrete venue policies express native replace support and venue-specific
 * enrichment. Lifecycle: stateless and reused by the adapter. Design intent:
 * avoid subclassing the whole order adapter when only a small number of venue
 * rules differ.
 */
public interface OrderEntryPolicy {
    boolean nativeReplaceSupported();

    /**
     * Returns the venue self-trade-prevention policy for new orders.
     *
     * <p>The default is empty because many venues either do not expose STP
     * through standard FIX or require no tag for the current account. Concrete
     * venue policies may return a venue-native value and apply it from
     * {@link #enrichNewOrder(NewOrderCommandDecoder, NewOrderSingleEncoder)} when
     * the generated dictionary exposes the proprietary field.</p>
     *
     * @return venue STP policy value, or empty string for no policy
     */
    default String selfTradePreventionPolicy() {
        return "";
    }

    default void enrichNewOrder(final NewOrderCommandDecoder command, final NewOrderSingleEncoder encoder) {
    }

    default void enrichCancel(final CancelOrderCommandDecoder command, final OrderCancelRequestEncoder encoder) {
    }

    default void enrichReplace(final ReplaceOrderCommandDecoder command, final OrderCancelReplaceRequestEncoder encoder) {
    }

    default void enrichStatusQuery(final OrderStatusQueryCommandDecoder command, final OrderStatusRequestEncoder encoder) {
    }
}
