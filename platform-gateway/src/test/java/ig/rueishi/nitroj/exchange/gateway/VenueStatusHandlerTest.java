package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatus;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-011 venue status publication.
 *
 * <p>Responsibility: verify that {@link VenueStatusHandler} publishes CONNECTED
 * and DISCONNECTED SBE events around Artio session lifecycle callbacks. Role in
 * system: these tests protect the gateway signal that later recovery components
 * consume to begin venue resynchronization. Relationships: tests use the real
 * {@link GatewayDisruptor}, generated venue-status SBE decoders, and a tiny
 * {@link IdRegistry} fake for deterministic session id resolution.</p>
 *
 * <p>Lifecycle: every test creates a fresh handler and disruptor. The helper
 * copies encoded slots before the disruptor reset wrapper clears reusable
 * buffers, then decodes the payload exactly as downstream components will.</p>
 */
final class VenueStatusHandlerTest {
    private static final long SESSION_ID = 9001L;
    private static final int VENUE_ID = 3;

    /** Verifies session acquisition publishes CONNECTED. */
    @Test
    void onSessionAcquired_publishesConnectedEvent() throws Exception {
        final Harness harness = Harness.started(1, new Registry(VENUE_ID));

        harness.handler.onSessionAcquired(SESSION_ID);

        assertThat(harness.onlyEvent().status()).isEqualTo(VenueStatus.CONNECTED);
        harness.close();
    }

    /** Verifies disconnect publishes DISCONNECTED. */
    @Test
    void onDisconnect_publishesDisconnectedEvent() throws Exception {
        final Harness harness = Harness.started(2, new Registry(VENUE_ID));
        final SessionHandler sessionHandler = harness.handler.onSessionAcquired(SESSION_ID);

        sessionHandler.onDisconnect(1, null, DisconnectReason.REMOTE_DISCONNECT);

        assertThat(harness.events().get(1).status()).isEqualTo(VenueStatus.DISCONNECTED);
        harness.close();
    }

    /** Verifies venue id comes from the registry lookup for the acquired session. */
    @Test
    void onSessionAcquired_venueIdResolvedFromSessionId() throws Exception {
        final Harness harness = Harness.started(1, new Registry(7));

        harness.handler.onSessionAcquired(SESSION_ID);

        assertThat(harness.onlyEvent().venueId()).isEqualTo(7);
        harness.close();
    }

    /** Verifies unknown session ids fail fast with the required config exception type. */
    @Test
    void onSessionAcquired_unknownSessionId_throws() {
        final Harness harness = Harness.notStarted(new Registry(0));

        assertThatThrownBy(() -> harness.handler.onSessionAcquired(SESSION_ID))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("Unknown FIX session: " + SESSION_ID);
        harness.close();
    }

    /** Verifies DISCONNECTED events retain the venue id resolved on acquisition. */
    @Test
    void onDisconnect_correctVenueId_inEvent() throws Exception {
        final Harness harness = Harness.started(2, new Registry(12));
        final SessionHandler sessionHandler = harness.handler.onSessionAcquired(SESSION_ID);

        sessionHandler.onDisconnect(1, null, DisconnectReason.LOGOUT);

        assertThat(harness.events().get(1).venueId()).isEqualTo(12);
        harness.close();
    }

    /** Verifies duplicate acquisition callbacks for the same session publish one CONNECTED event. */
    @Test
    void multipleLogons_sameSession_onlyOneConnectedEvent() throws Exception {
        final Harness harness = Harness.started(1, new Registry(VENUE_ID));

        harness.handler.onSessionAcquired(SESSION_ID);
        harness.handler.onSessionAcquired(SESSION_ID);

        assertThat(harness.events()).hasSize(1);
        harness.close();
    }

    /** Verifies repeated disconnect callbacks are idempotent. */
    @Test
    void onDisconnect_afterDisconnect_idempotent() throws Exception {
        final Harness harness = Harness.started(2, new Registry(VENUE_ID));
        final SessionHandler sessionHandler = harness.handler.onSessionAcquired(SESSION_ID);

        assertThat(sessionHandler.onDisconnect(1, null, DisconnectReason.LOGOUT)).isEqualTo(Action.CONTINUE);
        sessionHandler.onDisconnect(1, null, DisconnectReason.LOGOUT);

        assertThat(harness.events()).hasSize(2);
        assertThat(harness.events().get(1).status()).isEqualTo(VenueStatus.DISCONNECTED);
        harness.close();
    }

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness implements AutoCloseable {
        private final GatewayDisruptor disruptor;
        private final VenueStatusHandler handler;
        private final CountDownLatch latch;
        private final List<UnsafeBuffer> payloads = new ArrayList<>();

        private Harness(final int expectedEvents, final boolean start, final Registry registry) {
            latch = new CountDownLatch(expectedEvents);
            disruptor = new GatewayDisruptor(8, 512, counters(), (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                payloads.add(new UnsafeBuffer(copy));
                latch.countDown();
            });
            handler = new VenueStatusHandler(registry, disruptor);
            if (start) {
                disruptor.start();
            }
        }

        private static Harness started(final int expectedEvents, final Registry registry) {
            return new Harness(expectedEvents, true, registry);
        }

        private static Harness notStarted(final Registry registry) {
            return new Harness(0, false, registry);
        }

        private VenueStatusEventDecoder onlyEvent() throws Exception {
            final List<VenueStatusEventDecoder> decoded = events();
            assertThat(decoded).hasSize(1);
            return decoded.getFirst();
        }

        private List<VenueStatusEventDecoder> events() throws Exception {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            final List<VenueStatusEventDecoder> decoded = new ArrayList<>();
            for (UnsafeBuffer payload : payloads) {
                final MessageHeaderDecoder header = new MessageHeaderDecoder();
                final VenueStatusEventDecoder event = new VenueStatusEventDecoder();
                event.wrapAndApplyHeader(payload, 0, header);
                assertThat(event.ingressTimestampNanos()).isPositive();
                decoded.add(event);
            }
            return decoded;
        }

        @Override
        public void close() {
            disruptor.close();
        }
    }

    private static final class Registry implements IdRegistry {
        private final int venueId;

        private Registry(final int venueId) {
            this.venueId = venueId;
        }

        @Override
        public int venueId(final long sessionId) {
            assertThat(sessionId).isEqualTo(SESSION_ID);
            return venueId;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return null;
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
            // Tests resolve session id directly.
        }
    }
}
