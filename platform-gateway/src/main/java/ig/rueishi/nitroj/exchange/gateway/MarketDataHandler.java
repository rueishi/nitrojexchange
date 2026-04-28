package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.gateway.marketdata.MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.session.Session;

import java.lang.System.Logger;
import java.util.Objects;

/**
 * Artio session callback that delegates FIX market-data normalization.
 *
 * <p>Responsibility: capture ingress time on the Artio library thread and
 * delegate raw FIX bytes to the venue/model normalizer selected during gateway
 * runtime composition. Role in system: this is the protocol-neutral Artio
 * adapter between a session callback and a concrete {@link MarketDataNormalizer}.
 * Relationships: {@link IdRegistry} and {@link GatewayDisruptor} are retained
 * for constructor compatibility and test diagnostics, while parsing and SBE
 * publication live in the selected normalizer.</p>
 *
 * <p>Lifecycle: one handler is created during gateway startup after the ID
 * registry and disruptor exist, then Artio invokes {@link #onMessage(DirectBuffer,
 * int, int, int, Session, int, long, long, long, OnMessageInfo)} for every FIX
 * message on the session. Design intent: market data is lossy under pressure, so
 * concrete normalizer owns lossy/drop behavior, while this handler preserves the
 * ingress timestamp as the first operation in the callback.</p>
 */
public final class MarketDataHandler implements SessionHandler {
    private static final Logger DEFAULT_LOGGER = System.getLogger(MarketDataHandler.class.getName());

    private final IdRegistry idRegistry;
    private final GatewayDisruptor disruptor;
    private final Logger logger;
    private final MarketDataNormalizer normalizer;

    /**
     * Creates a handler with an explicitly composed market-data normalizer.
     *
     * @param idRegistry ID lookup dependency
     * @param disruptor gateway handoff ring
     * @param logger compatibility logger retained for cold-path diagnostics
     * @param normalizer selected venue/model normalizer
     */
    public MarketDataHandler(
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor,
        final Logger logger,
        final MarketDataNormalizer normalizer) {
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.disruptor = Objects.requireNonNull(disruptor, "disruptor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    /**
     * Handles one FIX message from Artio and publishes zero or more SBE entries.
     *
     * <p>The first executable line stamps {@code System.nanoTime()} before
     * delegating to the selected normalizer.</p>
     *
     * @param buffer inbound FIX bytes
     * @param offset first byte of the FIX message
     * @param length number of bytes in the FIX message
     * @param libraryId Artio library id, unused by market-data normalization
     * @param session live Artio session used to resolve the platform venue id
     * @param sequenceIndex Artio sequence index, unused
     * @param messageType Artio message type hint, unused because tags are scanned
     * @param timestamp Artio receive timestamp, unused in favor of gateway nano time
     * @param position Aeron position, unused
     * @param messageInfo Artio message metadata, unused
     * @return {@link Action#CONTINUE} so Artio keeps polling subsequent fragments
     */
    @Override
    public Action onMessage(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final int libraryId,
        final Session session,
        final int sequenceIndex,
        final long messageType,
        final long timestamp,
        final long position,
        final OnMessageInfo messageInfo) {

        final long ingressNanos = System.nanoTime();
        return handleMarketData(ingressNanos, session.id(), buffer, offset, length);
    }

    /**
     * Test-facing entry point that exercises the same parser and publisher path
     * without requiring construction of an Artio {@link Session}.
     *
     * @param sessionId live Artio session id registered in {@link IdRegistry}
     * @param buffer FIX message bytes
     * @param offset first byte of the message
     * @param length message length
     * @return Artio continuation action
     */
    Action onMessageForTest(
        final long sessionId,
        final DirectBuffer buffer,
        final int offset,
        final int length) {

        final long ingressNanos = System.nanoTime();
        return handleMarketData(ingressNanos, sessionId, buffer, offset, length);
    }

    /**
     * Ignores Artio timeout callbacks; session lifecycle is owned by later tasks.
     *
     * @param libraryId Artio library id
     * @param session session that timed out
     */
    @Override
    public void onTimeout(final int libraryId, final Session session) {
        // No TASK-009 market-data state is maintained across Artio timeouts.
    }

    /**
     * Ignores Artio slow-consumer notifications for this task.
     *
     * @param libraryId Artio library id
     * @param session affected session
     * @param hasBecomeSlow true when the session just became slow
     */
    @Override
    public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow) {
        // Slow-consumer policy is handled by Artio/session lifecycle tasks.
    }

    /**
     * Acknowledges disconnect callbacks without publishing market-data events.
     *
     * @param libraryId Artio library id
     * @param session disconnected session
     * @param reason Artio disconnect reason
     * @return {@link Action#CONTINUE}
     */
    @Override
    public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason) {
        return Action.CONTINUE;
    }

    /**
     * Ignores Artio session-start callbacks; session registration is out of scope.
     *
     * @param session newly started session
     */
    @Override
    public void onSessionStart(final Session session) {
        // TASK-015/016 own live session registration and lifecycle wiring.
    }

    private Action handleMarketData(
        final long ingressNanos,
        final long sessionId,
        final DirectBuffer buffer,
        final int offset,
        final int length) {

        return normalizer.onFixMessage(sessionId, buffer, offset, length, ingressNanos);
    }
}
