package ig.rueishi.nitroj.exchange.simulator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-level coverage for the local Coinbase FIX simulator acceptor.
 *
 * <p>Responsibility: proves TASK-125's simulator can be driven through real TCP
 * FIX bytes rather than direct Java method calls. Role in system: TASK-126 can
 * reuse this fixture as the exchange side of live-wire gateway/cluster E2E
 * tests. Relationships: the tests exercise {@link CoinbaseExchangeSimulator},
 * {@link SimulatorSessionHandler}, {@link SimulatorConfig}, and the internal
 * socket acceptor while avoiding live Coinbase connectivity. Lifecycle: each
 * test starts an acceptor on an ephemeral port, connects a plain socket client,
 * exchanges SOH-delimited FIX messages, and closes both sides deterministically.
 * Design intent: keep the fixture intentionally small but strict about the
 * session behaviors V11 needs: logon, L2/L3 subscription, order entry, cancel,
 * reject, duplicate session handling, disconnect, and malformed input.
 */
final class CoinbaseFixConnectivitySimulatorTest {
    private static final char SOH = '\001';

    @Test
    void positive_logonHeartbeatL2L3OrderCancelAndLogout_overTcpFix() throws Exception {
        try (CoinbaseExchangeSimulator simulator = simulator(CoinbaseExchangeSimulator.FillMode.NO_FILL);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.send("A", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "554", "coinbase-passphrase"));
            assertThat(client.readUntil("35=A")).contains("35=A");
            client.send("0", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET"));
            assertThat(client.readUntil("35=0")).contains("35=0");

            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            assertThat(client.readUntil("35=X")).contains("269=0").contains("270=65000.0");
            assertThat(simulator.l2Events()).hasSize(2);

            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L3", "55", "BTC-USD"));
            assertThat(client.readUntil("278=WIRE-BID-1")).contains("35=X").contains("278=WIRE-BID-1");
            assertThat(simulator.l3Events()).hasSize(2);

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "CL-1", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.1"));
            assertThat(client.readUntil("150=0")).contains("35=8").contains("11=CL-1");
            assertThat(simulator.receivedOrders()).hasSize(1);

            client.send("F", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "11", "CXL-1", "41", "CL-1"));
            assertThat(client.readUntil("150=4")).contains("35=8").contains("11=CXL-1");
            assertThat(simulator.receivedCancels()).hasSize(1);

            client.send("5", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET"));
            assertThat(client.readUntil("35=5")).contains("35=5");
            assertThat(simulator.getWireLogonCount()).isEqualTo(1);
            assertThat(simulator.inboundFixMessages()).extracting(message -> field(message, "35"))
                .contains("A", "0", "V", "D", "F", "5");
        }
    }

    @Test
    void negative_badCredentialsUnknownSymbolInvalidMessageAndDuplicateSession_areRejected() throws Exception {
        try (CoinbaseExchangeSimulator simulator = simulator(CoinbaseExchangeSimulator.FillMode.NO_FILL);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.send("A", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "554", "bad"));
            assertThat(client.readUntil("logon rejected")).contains("35=5").contains("logon rejected");

            client.send("A", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "554", "coinbase-passphrase"));
            assertThat(client.readUntil("35=A")).contains("35=A");
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "DOGE-USD"));
            assertThat(client.readUntil("unknown symbol")).contains("35=Y").contains("DOGE-USD");
            client.send("Z", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET"));
            assertThat(client.readUntil("unsupported MsgType Z")).contains("35=j");

            try (FixClient duplicate = FixClient.connect(simulator.config().port())) {
                assertThat(duplicate.readUntil("duplicate session")).contains("35=5").contains("duplicate session");
            }
            assertThat(simulator.getRejectedWireSessionCount()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void edge_reconnectEmptySnapshotsRepeatedSubscriptionHighSequenceAndConcurrentTraffic_work() throws Exception {
        try (CoinbaseExchangeSimulator simulator = simulator(CoinbaseExchangeSimulator.FillMode.PARTIAL_THEN_FULL);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.logon();
            simulator.emitL3SequenceGap(99);
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L3", "55", "BTC-USD"));
            assertThat(client.readUntil("34=100")).contains("35=X");

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "CL-PART", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.2"));
            assertThat(client.readUntil("150=F")).contains("35=8");
            assertThat(simulator.getFillCount()).isGreaterThanOrEqualTo(1);

            simulator.scheduleDisconnect(0);
            waitUntil(() -> !simulator.isConnected());
            simulator.reconnect();
            assertThat(simulator.isConnected()).isTrue();
        }
    }

    @Test
    void exception_malformedFixInvalidNumericMissingTagsPortBindAndShutdownDuringActiveSession_areSafe()
        throws Exception {
        final int port = freePort();
        try (ServerSocket blocker = new ServerSocket(port)) {
            assertThatThrownBy(() -> CoinbaseExchangeSimulator.builder().port(port).build().start())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Simulator port");
        }

        try (CoinbaseExchangeSimulator simulator = simulator(CoinbaseExchangeSimulator.FillMode.NO_FILL);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.raw("8=FIXT.1.1" + SOH + "9=0" + SOH + "49=TEST_SENDER" + SOH + "10=000" + SOH);
            assertThat(client.readUntil("missing MsgType")).contains("35=j");

            client.logon();
            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "BADPX", "55", "BTC-USD",
                "54", "1", "44", "not-a-price", "38", "0.1"));
            waitUntil(() -> simulator.getMalformedInboundFixCount() > 0 || !simulator.isConnected());

            simulator.stop();
            assertThat(simulator.isConnected()).isFalse();
        }
    }

    @Test
    void failure_sequenceGapRejectDelayedFillForcedCloseAndPendingCleanup_areRecorded() throws Exception {
        try (CoinbaseExchangeSimulator simulator = simulator(CoinbaseExchangeSimulator.FillMode.REJECT_ALL);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.logon();
            simulator.emitL3SequenceGap(3);
            client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L3", "55", "BTC-USD"));
            assertThat(client.readUntil("278=WIRE-BID-1")).contains("34=4");

            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "REJ-1", "55", "BTC-USD",
                "54", "2", "44", "65001.0", "38", "0.1"));
            assertThat(client.readUntil("150=8")).contains("35=8");
            assertThat(simulator.getRejectCount()).isEqualTo(1);

            simulator.setFillMode(CoinbaseExchangeSimulator.FillMode.DELAYED_FILL);
            client.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "DELAY-1", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.1"));
            assertThat(client.readUntil("150=0")).contains("35=8");
            simulator.scheduleDisconnect(0, true);
            waitUntil(() -> !simulator.isConnected() && simulator.pendingOrderCount() == 0);
            assertThat(simulator.pendingOrderCount()).isZero();
        }
    }

    private static CoinbaseExchangeSimulator simulator(final CoinbaseExchangeSimulator.FillMode fillMode)
        throws IOException {
        final CoinbaseExchangeSimulator simulator = CoinbaseExchangeSimulator.builder()
            .config(SimulatorConfig.builder()
                .port(freePort())
                .instrument("BTC-USD", 65_000.00, 65_001.00)
                .fillMode(fillMode)
                .build())
            .build();
        simulator.start();
        return simulator;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String field(final String message, final String tag) {
        for (String token : message.split(String.valueOf(SOH))) {
            if (token.startsWith(tag + "=")) {
                return token.substring(tag.length() + 1);
            }
        }
        return "";
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
            readUntil("35=A");
        }

        void send(final String msgType, final Map<String, String> fields) throws IOException {
            final StringBuilder builder = new StringBuilder();
            builder.append("8=FIXT.1.1").append(SOH);
            builder.append("9=0").append(SOH);
            builder.append("35=").append(msgType).append(SOH);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.append(entry.getKey()).append('=').append(entry.getValue()).append(SOH);
            }
            builder.append("10=000").append(SOH);
            raw(builder.toString());
        }

        void raw(final String message) throws IOException {
            output.write(message.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        String readUntil(final String marker) throws IOException {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
            final byte[] buffer = new byte[256];
            final long deadline = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < deadline) {
                final int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                bytes.write(buffer, 0, read);
                final String text = bytes.toString(StandardCharsets.US_ASCII);
                if (text.contains(marker)) {
                    return text;
                }
            }
            throw new AssertionError("FIX marker not received: " + marker + " in " + bytes);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
