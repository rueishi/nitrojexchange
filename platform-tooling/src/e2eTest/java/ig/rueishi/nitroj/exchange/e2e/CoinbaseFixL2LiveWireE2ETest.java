package ig.rueishi.nitroj.exchange.e2e;

import ig.rueishi.nitroj.exchange.common.GatewayConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.simulator.CoinbaseExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.SimulatorConfig;
import ig.rueishi.nitroj.exchange.test.TradingSystemTestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coinbase L2 live-wire E2E coverage using the local FIX simulator endpoint.
 *
 * <p>Responsibility: validates the L2 side of the V12 pre-QA/UAT gate without
 * live Coinbase connectivity. The test connects to the simulator over TCP FIX,
 * requests Coinbase-style L2 market data, applies the received FIX bytes through
 * the gateway L2 normalizer and cluster market view harness, sends a FIX order
 * back to the simulator, and verifies execution feedback. The fixture is local
 * and deterministic, but it is intentionally wire-level: market data and order
 * entry cross a socket as SOH-delimited FIX messages rather than direct Java
 * method calls.</p>
 */
@Tag("E2E")
final class CoinbaseFixL2LiveWireE2ETest {
    private static final char SOH = '\001';

    @Test
    void l2LiveWire_marketDataOrderExecutionAndFailureCases_areCovered() throws Exception {
        try (TradingSystemTestHarness harness = startHarness(CoinbaseExchangeSimulator.FillMode.IMMEDIATE);
             FixClient client = FixClient.connect(harness.simulator().config().port())) {

            client.logon();
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            final String bid = client.readMessageContaining("269=0");
            final String ask = client.readMessageContaining("269=1");
            assertThat(harness.applyCoinbaseL2FixMessage(bid)).isTrue();
            assertThat(harness.applyCoinbaseL2FixMessage(ask)).isTrue();

            final int venueId = GatewayConfig.forTest().venueId();
            assertThat(harness.bestBid(venueId, Ids.INSTRUMENT_BTC_USD)).isEqualTo(scale(65_000.00));
            assertThat(harness.bestAsk(venueId, Ids.INSTRUMENT_BTC_USD)).isEqualTo(scale(65_001.00));
            assertThat(harness.consolidatedBook(Ids.INSTRUMENT_BTC_USD).sizeAt(
                EntryType.BID, scale(65_000.00))).isEqualTo(scale(1.0));
            assertThat(harness.gatewayDisruptorHandoffCount()).isEqualTo(2);
            assertThat(harness.aeronIngressPublicationCount()).isEqualTo(2);
            assertThat(harness.observeStrategyBestBid(venueId, Ids.INSTRUMENT_BTC_USD))
                .isEqualTo("STRATEGY_BEST_BID:" + scale(65_000.00));
            assertThat(harness.strategyObservationCount()).isEqualTo(1);

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "L2-CL-1", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.1"));
            assertThat(client.readMessageContaining("150=0")).contains("35=8");
            assertThat(client.readMessageContaining("150=F")).contains("35=8");
            assertThat(harness.simulator().receivedOrders()).hasSize(1);
            assertThat(harness.simulator().getFillCount()).isEqualTo(1);

            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "DOGE-USD"));
            assertThat(client.readMessageContaining("unknown symbol")).contains("35=Y");
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "FULL", "55", "BTC-USD"));
            assertThat(client.readMessageContaining("Unsupported simulator market-data model")).contains("35=j");
            assertThat(harness.applyCoinbaseL2FixMessage(harness.simulator().emitMalformedL2Message("bad-l2")))
                .isFalse();
        }
    }

    @Test
    void l2LiveWire_edgeAndDisconnectCases_areDeterministic() throws Exception {
        try (TradingSystemTestHarness harness = startHarness(CoinbaseExchangeSimulator.FillMode.PARTIAL_THEN_FULL);
             FixClient client = FixClient.connect(harness.simulator().config().port())) {

            client.logon();
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            assertThat(harness.applyCoinbaseL2FixMessage(client.readMessageContaining("269=0"))).isTrue();

            final var zero = harness.simulator().emitL2Update("BUY", "BTC-USD", 65_000.00, 0.0, "CHANGE");
            assertThat(harness.applyCoinbaseL2FixMessage(harness.simulator().l2FixMessages().get(
                harness.simulator().l2FixMessages().size() - 1))).isTrue();
            assertThat(zero.size()).isZero();

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "L2-PART", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.2"));
            assertThat(client.readMessageContaining("150=F")).contains("35=8");
            assertThat(harness.simulator().getFillCount()).isGreaterThanOrEqualTo(1);

            harness.simulator().scheduleDisconnect(0);
            waitUntil(() -> !harness.simulator().isConnected());
            harness.simulator().reconnect();
            assertThat(harness.simulator().isConnected()).isTrue();
        }
    }

    private static TradingSystemTestHarness startHarness(final CoinbaseExchangeSimulator.FillMode fillMode)
        throws IOException {
        return TradingSystemTestHarness.start(SimulatorConfig.builder()
            .port(freePort())
            .marketDataIntervalMs(1_000)
            .instrument("BTC-USD", 65_000.00, 65_001.00)
            .fillMode(fillMode)
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
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("condition was not met before timeout");
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }

    private static final class FixClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private String pending = "";

        private FixClient(final Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        static FixClient connect(final int port) throws IOException {
            final Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2_000);
            return new FixClient(socket);
        }

        void logon() throws IOException {
            send("A", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "554", "coinbase-passphrase"));
            readMessageContaining("35=A");
        }

        void send(final String msgType, final Map<String, String> fields) throws IOException {
            final StringBuilder builder = new StringBuilder("8=FIXT.1.1").append(SOH).append("9=0").append(SOH)
                .append("35=").append(msgType).append(SOH);
            fields.forEach((tag, value) -> builder.append(tag).append('=').append(value).append(SOH));
            builder.append("10=000").append(SOH);
            output.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        String readMessageContaining(final String marker) throws IOException {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[256];
            final long deadline = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < deadline) {
                final String message = pollCompleteMessage();
                if (message != null) {
                    if (message.contains(marker)) {
                        return message;
                    }
                    continue;
                }
                final int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                bytes.write(buffer, 0, read);
                pending += bytes.toString(StandardCharsets.US_ASCII);
                bytes.reset();
            }
            throw new AssertionError("FIX marker not received: " + marker);
        }

        private String pollCompleteMessage() {
            final int checksum = pending.indexOf(SOH + "10=");
            if (checksum < 0) {
                return null;
            }
            final int end = pending.indexOf(SOH, checksum + 1);
            if (end < 0) {
                return null;
            }
            final String message = pending.substring(0, end + 1);
            pending = pending.substring(end + 1);
            return message;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
