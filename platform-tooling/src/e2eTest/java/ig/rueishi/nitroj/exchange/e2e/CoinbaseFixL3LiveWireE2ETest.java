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
 * Coinbase L3 live-wire E2E coverage using the local FIX simulator endpoint.
 *
 * <p>Responsibility: validates the L3/order-level side of the V11 pre-QA/UAT
 * gate. The test subscribes to the simulator over a socket FIX session, feeds
 * the resulting Coinbase-style order-level FIX bytes through the gateway L3
 * normalizer and cluster L3/L2 harness, sends an order back over the same FIX
 * wire, and verifies simulator execution feedback. This complements the older
 * harness-level L3 tests by proving the simulator FIX endpoint itself is part of
 * the automated path.</p>
 */
@Tag("E2E")
final class CoinbaseFixL3LiveWireE2ETest {
    private static final char SOH = '\001';

    @Test
    void l3LiveWire_marketDataOrderExecutionAndFailureCases_areCovered() throws Exception {
        try (TradingSystemTestHarness harness = startHarness(CoinbaseExchangeSimulator.FillMode.IMMEDIATE);
             FixClient client = FixClient.connect(harness.simulator().config().port())) {

            client.logon();
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L3", "55", "BTC-USD"));
            final String bid = client.readMessageContaining("278=WIRE-BID-1");
            final String ask = client.readMessageContaining("278=WIRE-ASK-1");
            assertThat(harness.applyCoinbaseL3FixMessage(bid)).isTrue();
            assertThat(harness.applyCoinbaseL3FixMessage(ask)).isTrue();

            final int venueId = GatewayConfig.forTest().venueId();
            assertThat(harness.venueL3Book(venueId, Ids.INSTRUMENT_BTC_USD).activeOrderCount()).isEqualTo(2);
            assertThat(harness.bestBid(venueId, Ids.INSTRUMENT_BTC_USD)).isEqualTo(scale(65_000.00));
            assertThat(harness.bestAsk(venueId, Ids.INSTRUMENT_BTC_USD)).isEqualTo(scale(65_001.00));
            assertThat(harness.consolidatedBook(Ids.INSTRUMENT_BTC_USD).sizeAt(
                EntryType.ASK, scale(65_001.00))).isEqualTo(scale(1.0));

            final var changed = harness.simulator().emitL3ChangeOrder("WIRE-BID-1", "BUY", "BTC-USD", 65_000.00, 0.25);
            assertThat(harness.applyCoinbaseL3FixMessage(harness.simulator().l3FixMessages().get(
                harness.simulator().l3FixMessages().size() - 1))).isTrue();
            assertThat(changed.size()).isEqualTo(0.25);
            assertThat(harness.marketView().book(venueId, Ids.INSTRUMENT_BTC_USD).bidSizeAt(0))
                .isEqualTo(scale(0.25));

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "L3-CL-1", "55", "BTC-USD",
                "54", "2", "44", "65001.0", "38", "0.1"));
            assertThat(client.readMessageContaining("150=0")).contains("35=8");
            assertThat(client.readMessageContaining("150=F")).contains("35=8");
            assertThat(harness.simulator().receivedOrders()).hasSize(1);

            client.send("F", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "11", "L3-CXL-MISS", "41", "UNKNOWN"));
            assertThat(client.readMessageContaining("150=9")).contains("35=8");
            client.send("Z", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET"));
            assertThat(client.readMessageContaining("unsupported MsgType Z")).contains("35=j");
            assertThat(harness.applyCoinbaseL3FixMessage(harness.simulator().emitMalformedL3Message("bad-l3")))
                .isFalse();
        }
    }

    @Test
    void l3LiveWire_edgeExceptionAndDisconnectCases_areDeterministic() throws Exception {
        try (TradingSystemTestHarness harness = startHarness(CoinbaseExchangeSimulator.FillMode.PARTIAL_THEN_FULL);
             FixClient client = FixClient.connect(harness.simulator().config().port())) {

            client.logon();
            harness.simulator().emitL3SequenceGap(5);
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L3", "55", "BTC-USD"));
            assertThat(client.readMessageContaining("34=6")).contains("35=X");

            final var reused = harness.simulator().emitL3AddOrder("REUSE", "BUY", "BTC-USD", 65_000.00, 0.4);
            assertThat(harness.applyCoinbaseL3FixMessage(harness.simulator().l3FixMessages().get(
                harness.simulator().l3FixMessages().size() - 1))).isTrue();
            harness.simulator().emitL3DeleteOrder("REUSE", "BUY", "BTC-USD", 65_000.00);
            assertThat(harness.applyCoinbaseL3FixMessage(harness.simulator().l3FixMessages().get(
                harness.simulator().l3FixMessages().size() - 1))).isTrue();
            harness.simulator().emitL3AddOrder("REUSE", "BUY", "BTC-USD", 65_000.00, 0.2);
            assertThat(harness.applyCoinbaseL3FixMessage(harness.simulator().l3FixMessages().get(
                harness.simulator().l3FixMessages().size() - 1))).isTrue();
            assertThat(reused.orderId()).isEqualTo("REUSE");

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "L3-PART", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.2"));
            assertThat(client.readMessageContaining("150=F")).contains("35=8");
            assertThat(harness.simulator().getFillCount()).isGreaterThanOrEqualTo(1);

            client.raw("8=FIXT.1.1" + SOH + "9=0" + SOH + "49=TEST_SENDER" + SOH + "10=000" + SOH);
            assertThat(client.readMessageContaining("missing MsgType")).contains("35=j");
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
            raw(builder.toString());
        }

        void raw(final String message) throws IOException {
            output.write(message.getBytes(StandardCharsets.US_ASCII));
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
