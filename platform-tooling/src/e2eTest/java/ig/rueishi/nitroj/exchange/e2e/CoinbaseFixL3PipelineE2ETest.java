package ig.rueishi.nitroj.exchange.e2e;

import ig.rueishi.nitroj.exchange.cluster.L2OrderBook;
import ig.rueishi.nitroj.exchange.cluster.VenueL3Book;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL3MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistryImpl;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E coverage for the local Coinbase FIX L3 normalization-to-book pipeline.
 */
@Tag("E2E")
final class CoinbaseFixL3PipelineE2ETest {
    private static final long SESSION_ID = 42L;

    @Test
    void coinbaseFixL3AddAndChange_buildMarketByOrderAndDerivedL2() throws Exception {
        final List<byte[]> captured = new ArrayList<>();
        try (GatewayDisruptor disruptor = disruptor(captured);
             Arena arena = Arena.ofConfined()) {
            final IdRegistryImpl registry = registry();
            registry.registerSession(Ids.VENUE_COINBASE, SESSION_ID);
            final CoinbaseL3MarketDataNormalizer normalizer = new CoinbaseL3MarketDataNormalizer(registry, disruptor);
            disruptor.start();

            normalizer.onFixMessage(SESSION_ID, fix("35=X", "34=1", "55=BTC-USD", "268=1",
                "279=0", "269=0", "278=order-1", "270=65000", "271=1"), 0, currentLength, 11L);
            normalizer.onFixMessage(SESSION_ID, fix("35=X", "34=2", "55=BTC-USD", "268=1",
                "279=1", "269=0", "278=order-1", "270=65001", "271=2"), 0, currentLength, 12L);

            waitFor(captured, 2);
            final VenueL3Book l3Book = new VenueL3Book(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD);
            final L2OrderBook l2Book = new L2OrderBook(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, arena);

            assertThat(l3Book.apply(decode(captured.get(0)), l2Book, 1L)).isTrue();
            assertThat(l3Book.apply(decode(captured.get(1)), l2Book, 2L)).isTrue();

            assertThat(l3Book.activeOrderCount()).isEqualTo(1);
            assertThat(l3Book.levelSize(ig.rueishi.nitroj.exchange.messages.Side.BUY, 6_500_100_000_000L))
                .isEqualTo(200_000_000L);
            assertThat(l2Book.getBestBid()).isEqualTo(6_500_100_000_000L);
        }
    }

    private static GatewayDisruptor disruptor(final List<byte[]> captured) {
        return new GatewayDisruptor(
            8,
            512,
            new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])),
            (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                synchronized (captured) {
                    captured.add(copy);
                    captured.notifyAll();
                }
            });
    }

    private static IdRegistryImpl registry() {
        final IdRegistryImpl registry = new IdRegistryImpl();
        registry.init(
            List.of(new VenueConfig(
                Ids.VENUE_COINBASE,
                "COINBASE",
                "fix.example.test",
                4198,
                true,
                FixPluginId.FIXT11_FIX50SP2,
                "COINBASE",
                MarketDataModel.L3,
                new VenueCapabilities(true, true, false))),
            List.of(new InstrumentConfig(Ids.INSTRUMENT_BTC_USD, "BTC-USD", "BTC", "USD")));
        return registry;
    }

    private static MarketByOrderEventDecoder decode(final byte[] bytes) {
        final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketByOrderEventEncoder.BLOCK_LENGTH, MarketByOrderEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static void waitFor(final List<byte[]> captured, final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        synchronized (captured) {
            while (captured.size() < expected && System.nanoTime() < deadline) {
                captured.wait(10L);
            }
        }
        assertThat(captured).hasSize(expected);
    }

    private static int currentLength;

    private static UnsafeBuffer fix(final String... tags) {
        final String message = String.join("\001", tags) + "\001";
        currentLength = message.length();
        return new UnsafeBuffer(message.getBytes(StandardCharsets.US_ASCII));
    }
}
