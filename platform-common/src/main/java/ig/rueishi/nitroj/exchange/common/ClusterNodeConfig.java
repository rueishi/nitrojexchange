package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable cluster-node process configuration.
 *
 * <p>Responsibility: aggregates all TOML sections needed to start one Aeron
 * Cluster node. Role in system: {@code ClusterMain} will consume this object to
 * wire archive/snapshot directories, cluster channels, risk limits, strategies,
 * metrics, and CPU affinity. Relationships: child records keep risk and strategy
 * settings separate while this object preserves process identity and transport
 * endpoints. Lifecycle: created by {@link ConfigManager#loadCluster(String)} at
 * startup or by {@link #singleNodeForTest()} in integration tests. Design intent:
 * make cluster startup deterministic and free of ad hoc config lookups after
 * components are constructed.
 */
public record ClusterNodeConfig(
    int nodeId,
    String nodeRole,
    String aeronDir,
    String archiveDir,
    String snapshotDir,
    String clusterMembers,
    String ingressChannel,
    String logChannel,
    String archiveChannel,
    int snapshotIntervalS,
    RiskConfig risk,
    boolean marketMakingEnabled,
    MarketMakingConfig marketMaking,
    boolean arbEnabled,
    ArbStrategyConfig arb,
    CpuConfig cpu,
    String counterFileDir,
    int histogramOutputMs,
    int maxVenues,
    int maxInstruments
) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a single-node cluster configuration for integration and E2E tests.
     *
     * <p>The factory supplies loopback endpoints and process-ID-scoped Aeron paths
     * so tests can start a cluster node without reading production TOML or sharing
     * runtime directories with another JVM.
     *
     * @return minimal single-node test cluster config
     */
    public static ClusterNodeConfig singleNodeForTest() {
        final long pid = ProcessHandle.current().pid();
        return ClusterNodeConfig.builder()
            .nodeId(0)
            .nodeRole("cluster")
            .aeronDir("/dev/shm/aeron-test-" + pid)
            .archiveDir("./build/test-archive-" + pid)
            .snapshotDir("./build/test-snapshot-" + pid)
            .clusterMembers("0,localhost:29010,localhost:29020,localhost:29030,localhost:29040")
            .ingressChannel("aeron:udp?endpoint=localhost:29010")
            .logChannel("aeron:udp?control-mode=manual|alias=test-log")
            .archiveChannel("aeron:udp?endpoint=localhost:29040")
            .snapshotIntervalS(3600)
            .cpu(CpuConfig.noPinning())
            .counterFileDir("./build/test-metrics/cluster-" + pid)
            .histogramOutputMs(5000)
            .maxVenues(Ids.MAX_VENUES)
            .maxInstruments(Ids.MAX_INSTRUMENTS)
            .build();
    }

    /** Builder used by TOML loading and test factories. */
    public static final class Builder {
        private int nodeId;
        private String nodeRole;
        private String aeronDir;
        private String archiveDir;
        private String snapshotDir;
        private String clusterMembers;
        private String ingressChannel;
        private String logChannel;
        private String archiveChannel;
        private int snapshotIntervalS;
        private RiskConfig risk;
        private boolean marketMakingEnabled;
        private MarketMakingConfig marketMaking;
        private boolean arbEnabled;
        private ArbStrategyConfig arb;
        private CpuConfig cpu;
        private String counterFileDir;
        private int histogramOutputMs;
        private int maxVenues;
        private int maxInstruments;

        public Builder nodeId(final int value) { nodeId = value; return this; }
        public Builder nodeRole(final String value) { nodeRole = value; return this; }
        public Builder aeronDir(final String value) { aeronDir = value; return this; }
        public Builder archiveDir(final String value) { archiveDir = value; return this; }
        public Builder snapshotDir(final String value) { snapshotDir = value; return this; }
        public Builder clusterMembers(final String value) { clusterMembers = value; return this; }
        public Builder ingressChannel(final String value) { ingressChannel = value; return this; }
        public Builder logChannel(final String value) { logChannel = value; return this; }
        public Builder archiveChannel(final String value) { archiveChannel = value; return this; }
        public Builder snapshotIntervalS(final int value) { snapshotIntervalS = value; return this; }
        public Builder risk(final RiskConfig value) { risk = value; return this; }
        public Builder marketMakingEnabled(final boolean value) { marketMakingEnabled = value; return this; }
        public Builder marketMaking(final MarketMakingConfig value) { marketMaking = value; return this; }
        public Builder arbEnabled(final boolean value) { arbEnabled = value; return this; }
        public Builder arb(final ArbStrategyConfig value) { arb = value; return this; }
        public Builder cpu(final CpuConfig value) { cpu = value; return this; }
        public Builder counterFileDir(final String value) { counterFileDir = value; return this; }
        public Builder histogramOutputMs(final int value) { histogramOutputMs = value; return this; }
        public Builder maxVenues(final int value) { maxVenues = value; return this; }
        public Builder maxInstruments(final int value) { maxInstruments = value; return this; }

        public ClusterNodeConfig build() {
            return new ClusterNodeConfig(nodeId, nodeRole, aeronDir, archiveDir, snapshotDir,
                clusterMembers, ingressChannel, logChannel, archiveChannel, snapshotIntervalS,
                risk, marketMakingEnabled, marketMaking, arbEnabled, arb, cpu, counterFileDir,
                histogramOutputMs, maxVenues, maxInstruments);
        }
    }
}
