package ig.rueishi.nitroj.exchange.simulator;

import ig.rueishi.nitroj.exchange.common.GatewayConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.test.TradingSystemTestHarness;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the Coinbase FIX L3 simulator path.
 *
 * <p>Responsibility: proves TASK-112's simulator-to-cluster path without live
 * Coinbase connectivity. Role in system: the test starts the shared
 * {@link TradingSystemTestHarness}, emits Coinbase-style L3 events from
 * {@link CoinbaseExchangeSimulator}, applies them through the harness SBE bridge,
 * and asserts `VenueL3Book`, derived venue L2, and consolidated L2 outcomes.
 * Relationships: TASK-111 owns simulator event generation; this class owns the
 * E2E fixture coverage requested before real Coinbase QA/UAT. Lifecycle: each
 * test starts a simulator on an ephemeral port and closes it at teardown.
 * Design intent: keep failure cases deterministic and local so malformed L3,
 * sequence gaps, and reconnect behavior are tested before any real venue access.
 */
final class CoinbaseFixL3E2ETest {
    @Test
    void l3Snapshot_buildsVenueL3DerivedL2AndConsolidatedL2() throws Exception {
        try (TradingSystemTestHarness harness = startHarness()) {
            CoinbaseExchangeSimulator simulator = harness.simulator();

            simulator.emitL3Snapshot();
            simulator.l3Events().forEach(harness::applyCoinbaseL3Event);

            assertThat(harness.venueL3Book(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD).activeOrderCount())
                .isEqualTo(2);
            assertThat(harness.bestBid(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD))
                .isEqualTo(scale(65_000.00));
            assertThat(harness.bestAsk(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD))
                .isEqualTo(scale(65_001.00));
            assertThat(harness.consolidatedBook(Ids.INSTRUMENT_BTC_USD).bestBid()).isEqualTo(scale(65_000.00));
            assertThat(harness.consolidatedBook(Ids.INSTRUMENT_BTC_USD).bestAsk()).isEqualTo(scale(65_001.00));
        }
    }

    @Test
    void l3AddChangeDelete_derivesCorrectVenueL2Levels() throws Exception {
        try (TradingSystemTestHarness harness = startHarness()) {
            CoinbaseExchangeSimulator simulator = harness.simulator();

            harness.applyCoinbaseL3Event(simulator.emitL3AddOrder("A", "BUY", "BTC-USD", 65_000.00, 0.5));
            harness.applyCoinbaseL3Event(simulator.emitL3AddOrder("B", "BUY", "BTC-USD", 65_000.00, 0.25));
            harness.applyCoinbaseL3Event(simulator.emitL3ChangeOrder("A", "BUY", "BTC-USD", 65_000.00, 0.10));

            assertThat(harness.marketView().book(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD).bidSizeAt(0))
                .isEqualTo(scale(0.35));
            assertThat(harness.consolidatedBook(Ids.INSTRUMENT_BTC_USD).sizeAt(EntryType.BID, scale(65_000.00)))
                .isEqualTo(scale(0.35));

            harness.applyCoinbaseL3Event(simulator.emitL3DeleteOrder("B", "BUY", "BTC-USD", 65_000.00));

            assertThat(harness.marketView().book(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD).bidSizeAt(0))
                .isEqualTo(scale(0.10));
        }
    }

    @Test
    void l3UnknownAndUnexpectedInputs_failSafelyWithoutCorruptingBookState() throws Exception {
        try (TradingSystemTestHarness harness = startHarness()) {
            CoinbaseExchangeSimulator simulator = harness.simulator();
            harness.applyCoinbaseL3Event(simulator.emitL3AddOrder("A", "SELL", "BTC-USD", 65_001.00, 0.5));

            assertThat(simulator.emitMalformedL3Message("bad-size")).contains("bad-size");
            assertThat(harness.applyCoinbaseL3Event(
                new CoinbaseExchangeSimulator.L3OrderEvent(2, "X", "BUY", "DOGE-USD", 1.0, 1.0, "ADD", 1L)))
                .isFalse();
            assertThat(harness.applyCoinbaseL3Event(simulator.emitL3DeleteOrder("MISSING", "SELL", "BTC-USD", 65_001.00)))
                .isFalse();

            assertThat(harness.l3ApplyFailureCount()).isEqualTo(1);
            assertThat(harness.bestAsk(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD))
                .isEqualTo(scale(65_001.00));
            assertThat(harness.consolidatedBook(Ids.INSTRUMENT_BTC_USD).sizeAt(EntryType.ASK, scale(65_001.00)))
                .isEqualTo(scale(0.5));
        }
    }

    @Test
    void l3EdgeCases_emptyZeroDuplicateAndRepeatedUpdatesRemainConsistent() throws Exception {
        try (TradingSystemTestHarness harness = startHarness()) {
            CoinbaseExchangeSimulator simulator = harness.simulator();

            assertThat(harness.applyLatestCoinbaseL3Event()).isFalse();
            harness.applyCoinbaseL3Event(simulator.emitL3ChangeOrder("A", "BUY", "BTC-USD", 65_000.00, 0.0));
            harness.applyCoinbaseL3Event(simulator.emitL3AddOrder("A", "BUY", "BTC-USD", 65_000.00, 0.5));
            harness.applyCoinbaseL3Event(simulator.emitL3AddOrder("A", "BUY", "BTC-USD", 65_000.00, 0.25));
            harness.applyCoinbaseL3Event(simulator.emitL3ChangeOrder("A", "BUY", "BTC-USD", 65_000.00, 0.25));

            assertThat(harness.marketView().book(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD).bidSizeAt(0))
                .isEqualTo(scale(0.25));
            assertThat(harness.venueL3Book(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD).activeOrderCount())
                .isEqualTo(1);
        }
    }

    @Test
    void l3Failure_sequenceGapDisconnectReconnectDoesNotCorruptBook() throws Exception {
        try (TradingSystemTestHarness harness = startHarness()) {
            CoinbaseExchangeSimulator simulator = harness.simulator();

            simulator.emitL3SequenceGap(3);
            harness.applyCoinbaseL3Event(simulator.emitL3AddOrder("A", "SELL", "BTC-USD", 65_001.00, 0.5));
            simulator.scheduleDisconnect(0);
            waitUntil(() -> !simulator.isConnected());
            simulator.reconnect();
            harness.applyCoinbaseL3Event(simulator.emitL3ChangeOrder("A", "SELL", "BTC-USD", 65_001.00, 0.25));

            assertThat(simulator.getL3SequenceGapCount()).isEqualTo(1);
            assertThat(simulator.isConnected()).isTrue();
            assertThat(harness.bestAsk(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD))
                .isEqualTo(scale(65_001.00));
            assertThat(harness.marketView().book(GatewayConfig.forTest().venueId(), Ids.INSTRUMENT_BTC_USD).askSizeAt(0))
                .isEqualTo(scale(0.25));
        }
    }

    private static TradingSystemTestHarness startHarness() throws IOException {
        return TradingSystemTestHarness.start(SimulatorConfig.builder()
            .port(freePort())
            .marketDataIntervalMs(1_000)
            .instrument("BTC-USD", 65_000.00, 65_001.00)
            .fillMode(CoinbaseExchangeSimulator.FillMode.NO_FILL)
            .build());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static long scale(final double value) {
        return Math.round(value * Ids.SCALE);
    }

    private static void waitUntil(final Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Condition was not met before timeout");
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }
}
