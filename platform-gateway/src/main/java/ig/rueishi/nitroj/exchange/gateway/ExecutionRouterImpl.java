package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.gateway.venue.StandardOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.session.Session;

import java.time.Clock;
import java.util.Objects;

/**
 * Gateway execution router that delegates order-entry encoding to a venue adapter.
 *
 * <p>Responsibility: preserve the existing {@link ExecutionRouter} contract for
 * cluster-egress order commands while hiding generated FIX encoder dependencies
 * behind {@link VenueOrderEntryAdapter}. Role in system: {@link OrderCommandHandler}
 * calls this router on the gateway egress thread; the router forwards commands
 * to the venue adapter selected during session wiring. Relationships: the
 * compatibility constructors create a {@link StandardOrderEntryAdapter} from an
 * explicit native-replace flag, while V11 runtime composition can inject a
 * venue-specific adapter directly. Lifecycle: created when Artio acquires a
 * session and reused until that session is replaced. Design intent: keep gateway
 * core protocol-neutral without changing caller behavior or existing tests.
 */
public final class ExecutionRouterImpl implements ExecutionRouter {
    private final VenueOrderEntryAdapter adapter;

    /**
     * Creates a compatibility router around a live Artio session.
     *
     * @param session live Artio session for outbound FIX order flow
     * @param idRegistry registry used to resolve internal instrument IDs to FIX symbols
     * @param account FIX tag 1 value used on NewOrderSingle messages
     * @param backPressureCounter metric callback invoked once when all retries fail
     * @param rejectPublisher callback that publishes a cluster rejection event on send exhaustion
     */
    public ExecutionRouterImpl(
        final Session session,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final RejectPublisher rejectPublisher) {
        this(
            Objects.requireNonNull(session, "session")::trySend,
            idRegistry,
            account,
            backPressureCounter,
            rejectPublisher,
            Clock.systemUTC(),
            false);
    }

    /**
     * Compatibility constructor used by existing tests and gateway wiring.
     *
     * @param sender testable facade over Artio send
     * @param idRegistry symbol registry
     * @param account FIX account value
     * @param backPressureCounter metric callback
     * @param rejectPublisher rejection callback
     * @param clock timestamp source
     * @param nativeReplaceSupported whether MsgType G should be emitted
     */
    public ExecutionRouterImpl(
        final FixSender sender,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final RejectPublisher rejectPublisher,
        final Clock clock,
        final boolean nativeReplaceSupported) {
        this(new StandardOrderEntryAdapter(
            sender,
            idRegistry,
            account,
            backPressureCounter,
            rejectPublisher,
            clock,
            () -> nativeReplaceSupported));
    }

    /**
     * Creates a router from a fully constructed venue order-entry adapter.
     *
     * @param adapter venue-specific adapter selected by runtime composition
     */
    public ExecutionRouterImpl(final VenueOrderEntryAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    @Override
    public void routeNewOrder(final NewOrderCommandDecoder command) {
        adapter.sendNewOrder(command);
    }

    @Override
    public void routeCancel(final CancelOrderCommandDecoder command) {
        adapter.sendCancel(command);
    }

    @Override
    public void routeReplace(final ReplaceOrderCommandDecoder command) {
        adapter.sendReplace(command);
    }

    @Override
    public void routeStatusQuery(final OrderStatusQueryCommandDecoder command) {
        adapter.sendStatusQuery(command);
    }

    /**
     * Testable facade over Artio {@link Session#trySend(Encoder)}.
     */
    @FunctionalInterface
    public interface FixSender {
        long trySend(Encoder encoder);
    }

    /**
     * Publishes the cluster-side rejection event required when FIX send retries are exhausted.
     */
    @FunctionalInterface
    public interface RejectPublisher {
        void publishRejected(long clOrdId, int venueId, int instrumentId);
    }
}
