package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for TASK-009 market-data FIX normalization.
 *
 * <p>Responsibility: verify that {@link MarketDataHandler} turns FIX MsgType
 * {@code W} and {@code X} entries into one SBE {@link MarketDataEventDecoder}
 * payload per entry. Role in system: this test anchors gateway ingress behavior
 * before later tasks add Aeron publishing and cluster book updates. Relationships:
 * it uses the real {@link GatewayDisruptor} slot lifecycle, a deterministic
 * {@link IdRegistry}, and generated SBE decoders to inspect bytes exactly as the
 * downstream cluster will receive them.</p>
 *
 * <p>Lifecycle: each test creates an isolated disruptor and handler. Tests that
 * need consumed events start the disruptor and copy SBE payloads before slots are
 * reset; the ring-full test intentionally does not start the disruptor so capacity
 * is exhausted deterministically.</p>
 */
final class MarketDataHandlerTest {
    private static final long SESSION_ID = 7001L;
    private static final int VENUE_ID = 1;
    private static final int BTC_INSTRUMENT_ID = 11;
    private static final char SOH = '\001';

    /**
     * Verifies a FIX snapshot bid entry is encoded as one SBE market-data event.
     *
     * @throws Exception if the disruptor consumer does not receive the event
     */
    @Test
    void msgTypeW_singleEntry_bidEntry_correctSBEPublished() throws Exception {
        final Harness harness = Harness.started(1);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=9", "55=BTC-USD", "268=1",
            "269=0", "270=65000.0", "271=1.5", "279=0", "1023=1", "60=20260424-20:30:15.123"), 0, currentLength);

        final MarketDataEventDecoder event = harness.onlyEvent();
        assertThat(event.venueId()).isEqualTo(VENUE_ID);
        assertThat(event.instrumentId()).isEqualTo(BTC_INSTRUMENT_ID);
        assertThat(event.entryType()).isEqualTo(EntryType.BID);
        assertThat(event.updateAction()).isEqualTo(UpdateAction.NEW);
        assertThat(event.priceScaled()).isEqualTo(6_500_000_000_000L);
        assertThat(event.sizeScaled()).isEqualTo(150_000_000L);
        assertThat(event.priceLevel()).isEqualTo(1);
        assertThat(event.fixSeqNum()).isEqualTo(9);
        harness.close();
    }

    /**
     * Verifies a FIX snapshot ask entry maps tag {@code 269=1} to SBE ASK.
     *
     * @throws Exception if the disruptor consumer does not receive the event
     */
    @Test
    void msgTypeW_singleEntry_askEntry_correctSBEPublished() throws Exception {
        final Harness harness = Harness.started(1);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=10", "55=BTC-USD", "268=1",
            "269=1", "270=65001.0", "271=0.5", "60=20260424-20:30:15.123"), 0, currentLength);

        final MarketDataEventDecoder event = harness.onlyEvent();
        assertThat(event.entryType()).isEqualTo(EntryType.ASK);
        assertThat(event.priceScaled()).isEqualTo(6_500_100_000_000L);
        assertThat(event.sizeScaled()).isEqualTo(50_000_000L);
        harness.close();
    }

    /**
     * Verifies MsgType X publishes every repeating group entry separately.
     *
     * @throws Exception if the expected events are not consumed
     */
    @Test
    void msgTypeX_multipleEntries_allPublishedSeparately() throws Exception {
        final Harness harness = Harness.started(3);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=X", "34=11", "55=BTC-USD", "268=3",
            "279=0", "269=0", "270=65000", "271=1", "1023=1",
            "279=1", "269=1", "270=65001", "271=2", "1023=1",
            "279=0", "269=2", "270=65002", "271=3", "1023=0"), 0, currentLength);

        final List<MarketDataEventDecoder> events = harness.events();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).entryType()).isEqualTo(EntryType.BID);
        assertThat(events.get(1).entryType()).isEqualTo(EntryType.ASK);
        assertThat(events.get(2).entryType()).isEqualTo(EntryType.TRADE);
        harness.close();
    }

    /**
     * Verifies ingress timestamp is captured before FIX tag decoding and ID lookup.
     *
     * @throws Exception if the event is not consumed
     */
    @Test
    void ingressTimestampNanos_setBeforeDecoding() throws Exception {
        final TrackingRegistry registry = new TrackingRegistry();
        final Harness harness = Harness.started(1, registry, new RecordingLogger());

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=12", "55=BTC-USD", "268=1",
            "269=0", "270=1", "271=1"), 0, currentLength);

        final MarketDataEventDecoder event = harness.onlyEvent();
        assertThat(event.ingressTimestampNanos()).isPositive();
        assertThat(event.ingressTimestampNanos()).isLessThanOrEqualTo(registry.firstInstrumentLookupNanos);
        harness.close();
    }

    /**
     * Verifies unknown symbols are counted and discarded without publishing.
     */
    @Test
    void unknownSymbol_notPublishedToDisruptor_counterIncremented() {
        final RecordingLogger logger = new RecordingLogger();
        final Harness harness = Harness.notStarted(4, new TrackingRegistry(), logger);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=13", "55=XRP-USD", "268=1",
            "269=0", "270=1", "271=1"), 0, currentLength);

        assertThat(harness.disruptor.remainingCapacity()).isEqualTo(4);
        assertThat(harness.normalizer.unknownSymbolDropCount()).isEqualTo(1);
        assertThat(logger.warnings).isEmpty();
        harness.close();
    }

    /**
     * Verifies market-data back-pressure drops the tick and increments the full counter.
     */
    @Test
    void disruptorFull_messageDiscarded_counterIncremented() {
        final Harness harness = Harness.notStarted(2);
        assertThat(harness.disruptor.claimSlot()).isNotNull();
        assertThat(harness.disruptor.claimSlot()).isNotNull();

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=14", "55=BTC-USD", "268=1",
            "269=0", "270=1", "271=1"), 0, currentLength);

        assertThat(harness.disruptor.disruptorFullCount()).isEqualTo(1);
        harness.close();
    }

    /**
     * Verifies execution reports sent to the market-data handler are ignored.
     */
    @Test
    void msgType8_ignored_notPublished() {
        final Harness harness = Harness.notStarted(4);

        final Action action = harness.handler.onMessageForTest(
            SESSION_ID, fix("35=8", "34=15", "55=BTC-USD"), 0, currentLength);

        assertThat(action).isEqualTo(Action.CONTINUE);
        assertThat(harness.disruptor.remainingCapacity()).isEqualTo(4);
        harness.close();
    }

    /**
     * Verifies decimal prices are converted to 1e8-scaled long values.
     *
     * @throws Exception if the event is not consumed
     */
    @Test
    void priceScaling_decimalPrice_scaledCorrectly() throws Exception {
        final Harness harness = Harness.started(1);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=16", "55=BTC-USD", "268=1",
            "269=0", "270=123.45678901", "271=0.00000001"), 0, currentLength);

        final MarketDataEventDecoder event = harness.onlyEvent();
        assertThat(event.priceScaled()).isEqualTo(12_345_678_901L);
        assertThat(event.sizeScaled()).isEqualTo(1L);
        harness.close();
    }

    /**
     * Verifies FIX tag 60 is parsed into UTC epoch nanoseconds.
     *
     * @throws Exception if the event is not consumed
     */
    @Test
    void exchangeTimestamp_parsedFromTag60_inNanos() throws Exception {
        final Harness harness = Harness.started(1);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=17", "55=BTC-USD", "268=1",
            "269=0", "270=1", "271=1", "60=20260424-20:30:15.123456789"), 0, currentLength);

        final long expected = LocalDateTime.of(2026, 4, 24, 20, 30, 15)
            .toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L + 123_456_789L;
        assertThat(harness.onlyEvent().exchangeTimestampNanos()).isEqualTo(expected);
        harness.close();
    }

    /**
     * Verifies a one-entry incremental refresh publishes once.
     *
     * @throws Exception if the event is not consumed
     */
    @Test
    void msgTypeX_singleEntry_publishedOnce() throws Exception {
        final Harness harness = Harness.started(1);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=X", "34=18", "55=BTC-USD", "268=1",
            "279=1", "269=0", "270=1", "271=2"), 0, currentLength);

        assertThat(harness.events()).hasSize(1);
        assertThat(harness.events().getFirst().updateAction()).isEqualTo(UpdateAction.CHANGE);
        harness.close();
    }

    /**
     * Verifies zero prices are preserved as zero rather than treated as missing.
     *
     * @throws Exception if the event is not consumed
     */
    @Test
    void priceScaling_zeroPrice_publishedAsZero() throws Exception {
        final Harness harness = Harness.started(1);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=19", "55=BTC-USD", "268=1",
            "269=2", "270=0.0", "271=0"), 0, currentLength);

        assertThat(harness.onlyEvent().priceScaled()).isZero();
        harness.close();
    }

    /**
     * Verifies a declared empty snapshot produces no SBE publications.
     */
    @Test
    void msgTypeW_zeroEntries_nothingPublished() {
        final Harness harness = Harness.notStarted(4);

        harness.handler.onMessageForTest(SESSION_ID, fix("35=W", "34=20", "55=BTC-USD", "268=0"), 0, currentLength);

        assertThat(harness.disruptor.remainingCapacity()).isEqualTo(4);
        harness.close();
    }

    private static UnsafeBuffer fix(final String... fields) {
        final String joined = String.join(String.valueOf(SOH), fields) + SOH;
        final byte[] bytes = joined.getBytes(StandardCharsets.US_ASCII);
        currentLength = bytes.length;
        return new UnsafeBuffer(bytes);
    }

    private static int currentLength;

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness implements AutoCloseable {
        private final GatewayDisruptor disruptor;
        private final MarketDataHandler handler;
        private final CoinbaseL2MarketDataNormalizer normalizer;
        private final CountDownLatch latch;
        private final List<UnsafeBuffer> payloads = new ArrayList<>();
        private int length;

        private Harness(
            final int ringSize,
            final int expectedEvents,
            final boolean start,
            final TrackingRegistry registry,
            final RecordingLogger logger) {

            latch = new CountDownLatch(expectedEvents);
            disruptor = new GatewayDisruptor(ringSize, 512, counters(), (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                payloads.add(new UnsafeBuffer(copy));
                latch.countDown();
            });
            normalizer = new CoinbaseL2MarketDataNormalizer(registry, disruptor, logger);
            handler = new MarketDataHandler(
                registry,
                disruptor,
                logger,
                normalizer);
            registry.registerSession(VENUE_ID, SESSION_ID);
            if (start) {
                disruptor.start();
            }
        }

        private static Harness started(final int expectedEvents) {
            return started(expectedEvents, new TrackingRegistry(), new RecordingLogger());
        }

        private static Harness started(
            final int expectedEvents,
            final TrackingRegistry registry,
            final RecordingLogger logger) {
            return new Harness(8, expectedEvents, true, registry, logger);
        }

        private static Harness notStarted(final int ringSize) {
            return notStarted(ringSize, new TrackingRegistry(), new RecordingLogger());
        }

        private static Harness notStarted(
            final int ringSize,
            final TrackingRegistry registry,
            final RecordingLogger logger) {
            return new Harness(ringSize, 0, false, registry, logger);
        }

        private MarketDataEventDecoder onlyEvent() throws Exception {
            final List<MarketDataEventDecoder> decoded = events();
            assertThat(decoded).hasSize(1);
            return decoded.getFirst();
        }

        private List<MarketDataEventDecoder> events() throws Exception {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            final List<MarketDataEventDecoder> decoded = new ArrayList<>();
            for (UnsafeBuffer payload : payloads) {
                final MessageHeaderDecoder header = new MessageHeaderDecoder();
                final MarketDataEventDecoder event = new MarketDataEventDecoder();
                event.wrapAndApplyHeader(payload, 0, header);
                decoded.add(event);
            }
            return decoded;
        }

        @Override
        public void close() {
            disruptor.close();
        }
    }

    private static final class TrackingRegistry implements IdRegistry {
        private long firstInstrumentLookupNanos = Long.MAX_VALUE;

        @Override
        public int venueId(final long sessionId) {
            assertThat(sessionId).isEqualTo(SESSION_ID);
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            firstInstrumentLookupNanos = Math.min(firstInstrumentLookupNanos, System.nanoTime());
            if ("BTC-USD".contentEquals(symbol)) {
                return BTC_INSTRUMENT_ID;
            }
            return 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return instrumentId == BTC_INSTRUMENT_ID ? "BTC-USD" : null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return venueId == VENUE_ID ? "Coinbase" : null;
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
            // The fake registry derives venueId directly from the asserted session id.
        }
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public boolean isLoggable(final Level level) {
            return true;
        }

        @Override
        public void log(final Level level, final ResourceBundle bundle, final String msg, final Throwable thrown) {
            if (level == Level.WARNING) {
                warnings.add(msg);
            }
        }

        @Override
        public void log(final Level level, final ResourceBundle bundle, final String format, final Object... params) {
            if (level == Level.WARNING) {
                String message = format;
                for (int i = 0; i < params.length; i++) {
                    message = message.replace("{" + i + "}", String.valueOf(params[i]));
                }
                warnings.add(message);
            }
        }
    }
}
