package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatus;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongHashSet;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionAcquireHandler;
import uk.co.real_logic.artio.library.SessionAcquiredInfo;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.session.Session;

import java.util.Objects;

/**
 * Publishes venue connectivity state changes observed through Artio sessions.
 *
 * <p>Responsibility: convert Artio session acquisition and disconnection events
 * into {@link ig.rueishi.nitroj.exchange.messages.VenueStatusEvent} SBE messages. Role
 * in system: this is the gateway-side trigger that lets cluster recovery logic
 * know when a venue is connected or disconnected. Relationships:
 * {@link IdRegistry} maps Artio session ids to platform venue ids, and
 * {@link GatewayDisruptor} carries encoded events to the Aeron publisher.</p>
 *
 * <p>Lifecycle: Artio invokes {@link #onSessionAcquired(Session, SessionAcquiredInfo)}
 * when a FIX session is acquired by the library. The returned inner
 * {@link VenueSessionHandler} handles disconnect callbacks for that venue.
 * Design intent: status events are recoverable notifications, so ring-full
 * publication attempts are dropped instead of blocking the Artio library thread.</p>
 */
public final class VenueStatusHandler implements SessionAcquireHandler {
    private final IdRegistry idRegistry;
    private final GatewayDisruptor disruptor;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final VenueStatusEventEncoder statusEncoder = new VenueStatusEventEncoder();
    private final LongHashSet connectedSessions = new LongHashSet();

    /**
     * Creates a venue status bridge.
     *
     * @param idRegistry session-to-venue registry populated during gateway wiring
     * @param disruptor gateway handoff ring for encoded status events
     */
    public VenueStatusHandler(final IdRegistry idRegistry, final GatewayDisruptor disruptor) {
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.disruptor = Objects.requireNonNull(disruptor, "disruptor");
    }

    /**
     * Publishes CONNECTED for a newly acquired Artio session.
     *
     * <p>Artio 0.175 exposes the live identity through {@link Session#id()} rather
     * than {@link SessionAcquiredInfo}, so the session is the authoritative lookup
     * source. Duplicate acquisition callbacks for the same live session return a
     * handler but publish only one CONNECTED event.</p>
     *
     * @param session acquired Artio session
     * @param sessionInfo Artio acquisition metadata, not needed for ID lookup
     * @return per-session handler that publishes DISCONNECTED on disconnect
     */
    @Override
    public SessionHandler onSessionAcquired(final Session session, final SessionAcquiredInfo sessionInfo) {
        return onSessionAcquired(session.id());
    }

    /**
     * Test-facing acquisition path that avoids constructing Artio session internals.
     *
     * @param sessionId live Artio session id
     * @return per-session handler for disconnect callbacks
     */
    SessionHandler onSessionAcquired(final long sessionId) {
        final int venueId = idRegistry.venueId(sessionId);
        if (venueId == 0) {
            throw new ConfigValidationException("FIX session", "Unknown FIX session: " + sessionId);
        }
        if (connectedSessions.add(sessionId)) {
            publishVenueStatus(venueId, VenueStatus.CONNECTED);
        }
        return new VenueSessionHandler(venueId);
    }

    private void publishVenueStatus(final int venueId, final VenueStatus status) {
        final GatewaySlot slot = disruptor.claimSlot();
        if (slot == null) {
            return;
        }
        statusEncoder.wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
            .venueId(venueId)
            .status(status)
            .ingressTimestampNanos(System.nanoTime());
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + statusEncoder.encodedLength();
        disruptor.publishSlot(slot);
    }

    /**
     * Per-session no-op message handler that publishes one DISCONNECTED event.
     *
     * <p>The handler deliberately ignores ordinary FIX messages because market
     * data and execution messages are owned by dedicated handlers. Disconnect is
     * idempotent so repeated Artio callbacks cannot flood recovery with duplicate
     * status transitions.</p>
     */
    private final class VenueSessionHandler implements SessionHandler {
        private final int venueId;
        private boolean disconnected;

        private VenueSessionHandler(final int venueId) {
            this.venueId = venueId;
        }

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
            return Action.CONTINUE;
        }

        @Override
        public void onTimeout(final int libraryId, final Session session) {
            // Venue status changes are driven by acquisition/disconnect only.
        }

        @Override
        public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow) {
            // Slow status is not a venue connectivity transition for TASK-011.
        }

        @Override
        public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason) {
            if (!disconnected) {
                disconnected = true;
                publishVenueStatus(venueId, VenueStatus.DISCONNECTED);
            }
            return Action.CONTINUE;
        }

        @Override
        public void onSessionStart(final Session session) {
            // Already represented by onSessionAcquired.
        }
    }
}
