package ig.rueishi.nitroj.exchange.common;

import java.util.List;

/**
 * Immutable gateway process configuration.
 *
 * <p>Responsibility: aggregates all TOML sections needed to start one gateway
 * process. Role in system: {@code GatewayMain} consumes this object to wire Artio,
 * Aeron ingress/egress, REST polling, CPU affinity, Disruptor sizing, metrics,
 * and warmup. Relationships: child records such as {@link FixConfig},
 * {@link RestConfig}, and {@link CredentialsConfig} keep protocol-specific
 * concerns isolated while the gateway config supplies process-level identity.
 * Lifecycle: created by {@link ConfigManager#loadGateway(String)} for production
 * or {@link #forTest()} for harnesses. Design intent: the object is immutable so
 * all gateway components observe a single startup contract.
 */
public record GatewayConfig(
    int venueId,
    String nodeRole,
    String aeronDir,
    String logDir,
    String clusterIngressChannel,
    String clusterEgressChannel,
    List<String> clusterMembers,
    FixConfig fix,
    CredentialsConfig credentials,
    RestConfig rest,
    CpuConfig cpu,
    DisruptorConfig disruptor,
    String counterFileDir,
    int histogramOutputMs,
    WarmupConfig warmup
) {
    public GatewayConfig {
        clusterMembers = List.copyOf(clusterMembers);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a loopback gateway configuration for tests and E2E harnesses.
     *
     * <p>The Aeron and Artio paths include the current process ID so parallel test
     * JVMs do not share media-driver or session-store directories. The factory is
     * called by test harnesses instead of TOML loading and deliberately uses
     * {@link CpuConfig#noPinning()} plus {@link WarmupConfig#development()} to run
     * on ordinary development machines.
     *
     * @return deterministic gateway config for local tests
     */
    public static GatewayConfig forTest() {
        final long pid = ProcessHandle.current().pid();
        return GatewayConfig.builder()
            .venueId(Ids.VENUE_COINBASE_SANDBOX)
            .nodeRole("gateway")
            .aeronDir("/dev/shm/aeron-test-" + pid)
            .logDir("./build/test-gateway-" + pid)
            .clusterIngressChannel("aeron:udp?endpoint=localhost:29010")
            .clusterEgressChannel("aeron:udp?endpoint=localhost:29020")
            .clusterMembers(List.of("aeron:udp?endpoint=localhost:29010"))
            .fix(FixConfig.builder()
                .senderCompId("TEST_SENDER")
                .targetCompId("TEST_TARGET")
                .heartbeatIntervalS(5)
                .reconnectIntervalMs(1000)
                .resetSeqNumOnLogon(false)
                .artioLogDir("./build/test-artio-" + pid)
                .artioReplayCapacity(256)
                .build())
            .credentials(CredentialsConfig.hardcoded("test-key", "dGVzdA==", "test-pass"))
            .rest(RestConfig.builder()
                .baseUrl("http://localhost:18080")
                .pollIntervalMs(500)
                .timeoutMs(2000)
                .build())
            .cpu(CpuConfig.noPinning())
            .disruptor(DisruptorConfig.builder().ringBufferSize(256).slotSizeBytes(512).build())
            .counterFileDir("./build/test-metrics/gateway-" + pid)
            .histogramOutputMs(5000)
            .warmup(WarmupConfig.development())
            .build();
    }

    /** Builder used by the TOML loader and spec-shaped test factories. */
    public static final class Builder {
        private int venueId;
        private String nodeRole;
        private String aeronDir;
        private String logDir;
        private String clusterIngressChannel;
        private String clusterEgressChannel;
        private List<String> clusterMembers = List.of();
        private FixConfig fix;
        private CredentialsConfig credentials;
        private RestConfig rest;
        private CpuConfig cpu;
        private DisruptorConfig disruptor;
        private String counterFileDir;
        private int histogramOutputMs;
        private WarmupConfig warmup;

        public Builder venueId(final int value) { venueId = value; return this; }
        public Builder nodeRole(final String value) { nodeRole = value; return this; }
        public Builder aeronDir(final String value) { aeronDir = value; return this; }
        public Builder logDir(final String value) { logDir = value; return this; }
        public Builder clusterIngressChannel(final String value) { clusterIngressChannel = value; return this; }
        public Builder clusterEgressChannel(final String value) { clusterEgressChannel = value; return this; }
        public Builder clusterMembers(final List<String> value) { clusterMembers = value; return this; }
        public Builder fix(final FixConfig value) { fix = value; return this; }
        public Builder credentials(final CredentialsConfig value) { credentials = value; return this; }
        public Builder rest(final RestConfig value) { rest = value; return this; }
        public Builder cpu(final CpuConfig value) { cpu = value; return this; }
        public Builder disruptor(final DisruptorConfig value) { disruptor = value; return this; }
        public Builder counterFileDir(final String value) { counterFileDir = value; return this; }
        public Builder histogramOutputMs(final int value) { histogramOutputMs = value; return this; }
        public Builder warmup(final WarmupConfig value) { warmup = value; return this; }

        public GatewayConfig build() {
            return new GatewayConfig(venueId, nodeRole, aeronDir, logDir, clusterIngressChannel,
                clusterEgressChannel, clusterMembers, fix, credentials, rest, cpu, disruptor,
                counterFileDir, histogramOutputMs, warmup);
        }
    }
}
