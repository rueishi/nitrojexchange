package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concrete Coinbase FIX L3 normalizer coverage.
 *
 * <p>Responsibility: verifies Coinbase's selected L3 normalizer converts FIX
 * market-by-order entries into SBE payloads through the real gateway disruptor.
 * Role in system: complements simulator E2E tests by covering the actual FIX
 * normalizer path used before cluster ingestion. Relationships: reuses the
 * shared FIX L3 parser and generated {@link MarketByOrderEventDecoder} schema.
 * Lifecycle: gateway unit coverage. Design intent: prevent Coinbase L3 from
 * silently regressing to simulator-only validation.</p>
 */
final class CoinbaseL3MarketDataNormalizerTest {
    private static final long SESSION_ID = 7001L;
    private static final int VENUE_ID = 1;
    private static final int BTC_INSTRUMENT_ID = 11;

    @Test
    void fixSnapshotPublishesMarketByOrderEvent() throws Exception {
        try (Harness harness = Harness.started(1)) {
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=W", "34=21", "55=BTC-USD", "268=1",
                "279=0", "269=0", "278=coinbase-order-1", "270=65000.25", "271=0.75"), 0, currentLength, 99L);

            final MarketByOrderEventDecoder event = harness.onlyEvent();
            assertThat(event.venueId()).isEqualTo(VENUE_ID);
            assertThat(event.instrumentId()).isEqualTo(BTC_INSTRUMENT_ID);
            assertThat(event.side()).isEqualTo(Side.BUY);
            assertThat(event.updateAction()).isEqualTo(UpdateAction.NEW);
            assertThat(event.priceScaled()).isEqualTo(6_500_025_000_000L);
            assertThat(event.sizeScaled()).isEqualTo(75_000_000L);
            assertThat(event.fixSeqNum()).isEqualTo(21);
            assertThat(event.ingressTimestampNanos()).isEqualTo(99L);
            assertThat(venueOrderId(event)).isEqualTo("coinbase-order-1");
        }
    }

    @Test
    void unknownSymbolDropsWithoutPublishingAndIncrementsCounter() {
        final RecordingLogger logger = new RecordingLogger();
        try (Harness harness = Harness.notStarted(4, logger)) {
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=W", "34=22", "55=DOGE-USD", "268=1",
                "279=0", "269=1", "278=ignored", "270=1", "271=1"), 0, currentLength, 100L);

            assertThat(harness.disruptor.remainingCapacity()).isEqualTo(4);
            assertThat(harness.normalizer.unknownSymbolDropCount()).isEqualTo(1);
            assertThat(logger.warnings).isEmpty();
        }
    }

    private static String venueOrderId(final MarketByOrderEventDecoder event) {
        final byte[] bytes = new byte[event.venueOrderIdLength()];
        event.getVenueOrderId(bytes, 0, bytes.length);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static UnsafeBuffer fix(final String... fields) {
        final String joined = String.join("\001", fields) + "\001";
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
        private final CoinbaseL3MarketDataNormalizer normalizer;
        private final CountDownLatch latch;
        private final List<UnsafeBuffer> payloads = new ArrayList<>();

        private Harness(final int ringSize, final int expectedEvents, final boolean start, final RecordingLogger logger) {
            latch = new CountDownLatch(expectedEvents);
            disruptor = new GatewayDisruptor(ringSize, 512, counters(), (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                payloads.add(new UnsafeBuffer(copy));
                latch.countDown();
            });
            normalizer = new CoinbaseL3MarketDataNormalizer(new TestRegistry(), disruptor, logger);
            if (start) {
                disruptor.start();
            }
        }

        private static Harness started(final int expectedEvents) {
            return new Harness(8, expectedEvents, true, new RecordingLogger());
        }

        private static Harness notStarted(final int ringSize, final RecordingLogger logger) {
            return new Harness(ringSize, 0, false, logger);
        }

        private MarketByOrderEventDecoder onlyEvent() throws Exception {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(payloads).hasSize(1);
            final MessageHeaderDecoder header = new MessageHeaderDecoder();
            final MarketByOrderEventDecoder event = new MarketByOrderEventDecoder();
            event.wrapAndApplyHeader(payloads.getFirst(), 0, header);
            return event;
        }

        @Override
        public void close() {
            disruptor.close();
        }
    }

    private static final class TestRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            assertThat(sessionId).isEqualTo(SESSION_ID);
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return "BTC-USD".contentEquals(symbol) ? BTC_INSTRUMENT_ID : 0;
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
            // The test normalizer asks for venue id directly from the session.
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
