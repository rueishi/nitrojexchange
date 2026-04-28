package ig.rueishi.nitroj.exchange.simulator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable configuration for the embedded Coinbase FIX simulator.
 *
 * <p>Responsibility: captures the simulator port, FIX identity, market-data
 * cadence, fill timing, and configured instruments used by TASK-006 tests. Role
 * in system: E2E fixtures create a {@link CoinbaseExchangeSimulator} from this
 * record instead of scattering simulator defaults through tests. Relationships:
 * it mirrors TASK-004 {@code GatewayConfig.forTest()} by using the simulator-side
 * sender/target CompIDs that match the gateway test identity. Lifecycle: built
 * by tests or harness startup, then treated as read-only while the simulator is
 * running. Design intent: keep simulator setup deterministic and easy to override
 * per test, especially port selection for parallel test isolation.
 *
 * @param port TCP port reserved by the simulator
 * @param logDir simulator FIX log directory
 * @param marketDataIntervalMs interval for synthetic market-data ticks
 * @param fillDelayMs delay used by delayed-fill scenarios
 * @param senderCompId simulator sender CompID
 * @param targetCompId simulator target CompID
 * @param requiredPassword optional logon password/passphrase required by the
 *                         wire FIX fixture; blank disables credential checking
 * @param instruments configured instruments keyed by symbol
 * @param fillMode default fill behavior
 */
public record SimulatorConfig(
    int port,
    String logDir,
    long marketDataIntervalMs,
    long fillDelayMs,
    String senderCompId,
    String targetCompId,
    String requiredPassword,
    Map<String, Instrument> instruments,
    CoinbaseExchangeSimulator.FillMode fillMode
) {
    public SimulatorConfig {
        instruments = Map.copyOf(instruments);
    }

    /**
     * Creates default local simulator config matching {@code GatewayConfig.forTest()}.
     *
     * @return default simulator config on port 19898
     */
    public static SimulatorConfig defaults() {
        return SimulatorConfig.builder()
            .port(19898)
            .logDir("./build/test-simulator-fix")
            .marketDataIntervalMs(100)
            .fillDelayMs(50)
            .senderCompId("TEST_TARGET")
            .targetCompId("TEST_SENDER")
            .requiredPassword("coinbase-passphrase")
            .instrument("BTC-USD", 65_000.00, 65_001.00)
            .fillMode(CoinbaseExchangeSimulator.FillMode.IMMEDIATE)
            .build();
    }

    /**
     * Creates defaults with a caller-selected port for parallel test isolation.
     *
     * @param port simulator TCP port
     * @return config using the supplied port
     */
    public static SimulatorConfig forPort(final int port) {
        return defaults().toBuilder().port(port).build();
    }

    public Builder toBuilder() {
        return new Builder()
            .port(port)
            .logDir(logDir)
            .marketDataIntervalMs(marketDataIntervalMs)
            .fillDelayMs(fillDelayMs)
            .senderCompId(senderCompId)
            .targetCompId(targetCompId)
            .requiredPassword(requiredPassword)
            .fillMode(fillMode)
            .instruments(instruments);
    }

    public SimulatorConfig withPort(final int port) {
        return toBuilder().port(port).build();
    }

    public SimulatorConfig withFillDelay(final long fillDelayMs) {
        return toBuilder().fillDelayMs(fillDelayMs).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Configured synthetic market for one symbol.
     *
     * @param symbol FIX symbol
     * @param bid starting bid
     * @param ask starting ask
     */
    public record Instrument(String symbol, double bid, double ask) {
    }

    /** Builder for immutable simulator config instances. */
    public static final class Builder {
        private int port = 19898;
        private String logDir = "./build/test-simulator-fix";
        private long marketDataIntervalMs = 100;
        private long fillDelayMs = 50;
        private String senderCompId = "TEST_TARGET";
        private String targetCompId = "TEST_SENDER";
        private String requiredPassword = "coinbase-passphrase";
        private final Map<String, Instrument> instruments = new LinkedHashMap<>();
        private CoinbaseExchangeSimulator.FillMode fillMode = CoinbaseExchangeSimulator.FillMode.IMMEDIATE;

        public Builder port(final int value) { port = value; return this; }
        public Builder logDir(final String value) { logDir = value; return this; }
        public Builder marketDataIntervalMs(final long value) { marketDataIntervalMs = value; return this; }
        public Builder fillDelayMs(final long value) { fillDelayMs = value; return this; }
        public Builder senderCompId(final String value) { senderCompId = value; return this; }
        public Builder targetCompId(final String value) { targetCompId = value; return this; }
        public Builder requiredPassword(final String value) { requiredPassword = value; return this; }
        public Builder fillMode(final CoinbaseExchangeSimulator.FillMode value) { fillMode = value; return this; }

        public Builder instrument(final String symbol, final double bid, final double ask) {
            instruments.put(symbol, new Instrument(symbol, bid, ask));
            return this;
        }

        public Builder instruments(final Map<String, Instrument> values) {
            instruments.clear();
            instruments.putAll(values);
            return this;
        }

        public SimulatorConfig build() {
            if (instruments.isEmpty()) {
                instrument("BTC-USD", 65_000.00, 65_001.00);
            }
            return new SimulatorConfig(port, logDir, marketDataIntervalMs, fillDelayMs, senderCompId,
                targetCompId, requiredPassword == null ? "" : requiredPassword, instruments, fillMode);
        }
    }
}
