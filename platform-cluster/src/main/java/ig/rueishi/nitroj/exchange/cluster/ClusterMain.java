package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.ClusterNodeConfig;
import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistryImpl;
import ig.rueishi.nitroj.exchange.strategy.ArbStrategy;
import ig.rueishi.nitroj.exchange.strategy.MarketMakingStrategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngine;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.agrona.concurrent.SystemEpochClock;

/**
 * Process entry point for one NitroJEx Aeron Cluster node.
 *
 * <p>ClusterMain owns startup-only wiring: it loads typed TOML configuration,
 * builds the deterministic TradingClusteredService component graph, configures
 * the co-located MediaDriver/Archive/ConsensusModule stack, starts the service
 * container, and installs shutdown handling. Runtime trading behavior remains in
 * the service and its collaborators; this class exists to keep process boot and
 * dependency construction in one auditable place.</p>
 */
public final class ClusterMain {
    private static final String DEFAULT_VENUES_PATH = "config/venues.toml";
    private static final String DEFAULT_INSTRUMENTS_PATH = "config/instruments.toml";
    private static final byte[] DEFAULT_ADMIN_HMAC_KEY = "key".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private ClusterMain() {
    }

    /**
     * Starts one clustered service process.
     *
     * <p>Arguments are intentionally small: the first argument is the cluster
     * node TOML path; optional second and third arguments override the shared
     * venue and instrument registry files. Configuration is loaded before Aeron
     * is launched, so malformed TOML fails before cluster directories, drivers,
     * or service containers are started.</p>
     *
     * @param args {@code cluster-node.toml [venues.toml instruments.toml]}
     * @throws Exception if configuration or Aeron startup fails
     */
    public static void main(final String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: ClusterMain <cluster-node.toml> [venues.toml instruments.toml]");
        }
        final ClusterNodeConfig config = ConfigManager.loadCluster(args[0]);
        final List<VenueConfig> venues = ConfigManager.loadVenues(args.length > 1 ? args[1] : DEFAULT_VENUES_PATH);
        final List<InstrumentConfig> instruments = ConfigManager.loadInstruments(args.length > 2 ? args[2] : DEFAULT_INSTRUMENTS_PATH);
        ensureRuntimeDirectories(config);

        final TradingClusteredService service = buildClusteredService(config, venues, instruments);
        final ClusteredMediaDriver clusteredMediaDriver = ClusteredMediaDriver.launch(
            mediaDriverContext(config),
            archiveContext(config),
            consensusModuleContext(config)
        );
        final ClusteredServiceContainer serviceContainer = ClusteredServiceContainer.launch(serviceContainerContext(config, service));
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serviceContainer.close();
            clusteredMediaDriver.close();
            shutdownLatch.countDown();
        }, "nitrojex-cluster-shutdown"));
        shutdownLatch.await();
    }

    /**
     * Builds the full clustered trading service graph.
     *
     * <p>The factory constructs collaborators in dependency order. The Aeron
     * Cluster reference is deliberately absent here; TradingClusteredService
     * receives it in {@code onStart()} and propagates it to every component after
     * Aeron has completed service initialization.</p>
     *
     * @param config loaded cluster-node configuration
     * @param venues shared venue registry rows
     * @param instruments shared instrument registry rows
     * @return fully wired clustered service
     */
    static TradingClusteredService buildClusteredService(
        final ClusterNodeConfig config,
        final List<VenueConfig> venues,
        final List<InstrumentConfig> instruments
    ) {
        final IdRegistryImpl idRegistry = new IdRegistryImpl();
        idRegistry.init(venues, instruments);

        final InternalMarketView marketView = new InternalMarketView();
        final RiskEngineImpl riskEngine = new RiskEngineImpl(config.risk());
        final OrderManagerImpl orderManager = new OrderManagerImpl();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(riskEngine);
        final RecoveryCoordinatorImpl recovery = new RecoveryCoordinatorImpl(orderManager, portfolio, riskEngine);

        final StrategyContextImpl strategyContext = new StrategyContextImpl(
            marketView,
            riskEngine,
            orderManager,
            portfolio,
            recovery,
            null,
            null,
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            idRegistry,
            null
        );
        final StrategyEngine strategyEngine = new StrategyEngine(strategyContext);
        if (config.marketMakingEnabled() && config.marketMaking() != null) {
            strategyEngine.register(new MarketMakingStrategy(config.marketMaking()));
        }
        if (config.arbEnabled() && config.arb() != null) {
            strategyEngine.register(new ArbStrategy(config.arb()));
        }

        final AdminCommandHandler adminHandler = new AdminCommandHandler(
            riskEngine,
            strategyEngine,
            SystemEpochClock.INSTANCE,
            DEFAULT_ADMIN_HMAC_KEY,
            new int[]{1}
        );

        for (VenueConfig venue : venues) {
            for (InstrumentConfig instrument : instruments) {
                portfolio.initPosition(venue.id(), instrument.id());
            }
        }

        final MessageRouter router = new MessageRouter(
            strategyEngine,
            riskEngine,
            orderManager,
            portfolio,
            marketView,
            recovery,
            adminHandler
        );
        final DailyResetTimer dailyResetTimer = new DailyResetTimer(riskEngine, portfolio, SystemEpochClock.INSTANCE);
        return new TradingClusteredService(strategyEngine, riskEngine, orderManager, portfolio, recovery, dailyResetTimer, router);
    }

    private static MediaDriver.Context mediaDriverContext(final ClusterNodeConfig config) {
        return new MediaDriver.Context()
            .aeronDirectoryName(config.aeronDir())
            .threadingMode(ThreadingMode.SHARED)
            .dirDeleteOnStart(false);
    }

    private static Archive.Context archiveContext(final ClusterNodeConfig config) {
        return new Archive.Context()
            .aeronDirectoryName(config.aeronDir())
            .archiveDirectoryName(config.archiveDir())
            .controlChannel(config.archiveChannel())
            .threadingMode(ArchiveThreadingMode.SHARED)
            .deleteArchiveOnStart(false);
    }

    private static ConsensusModule.Context consensusModuleContext(final ClusterNodeConfig config) {
        return new ConsensusModule.Context()
            .aeronDirectoryName(config.aeronDir())
            .clusterMemberId(config.nodeId())
            .clusterMembers(config.clusterMembers())
            .memberEndpoints(memberEndpoints(config.clusterMembers(), config.nodeId()))
            .ingressChannel(config.ingressChannel())
            .logChannel(config.logChannel())
            .clusterDirectoryName(config.snapshotDir())
            .archiveContext(new io.aeron.archive.client.AeronArchive.Context()
                .aeronDirectoryName(config.aeronDir())
                .controlRequestChannel(config.archiveChannel()))
            .deleteDirOnStart(false)
            .electionTimeoutNs(400_000_000L);
    }

    private static ClusteredServiceContainer.Context serviceContainerContext(
        final ClusterNodeConfig config,
        final TradingClusteredService service
    ) {
        return new ClusteredServiceContainer.Context()
            .aeronDirectoryName(config.aeronDir())
            .clusterDirectoryName(config.snapshotDir())
            .clusteredService(service)
            .archiveContext(new io.aeron.archive.client.AeronArchive.Context()
                .aeronDirectoryName(config.aeronDir())
                .controlRequestChannel(config.archiveChannel()));
    }

    private static void ensureRuntimeDirectories(final ClusterNodeConfig config) throws Exception {
        Files.createDirectories(Path.of(config.archiveDir()));
        Files.createDirectories(Path.of(config.snapshotDir()));
        Files.createDirectories(Path.of(config.counterFileDir()));
    }

    private static String memberEndpoints(final String clusterMembers, final int nodeId) {
        return Arrays.stream(clusterMembers.split("\\|"))
            .map(String::trim)
            .filter(member -> member.startsWith(nodeId + ","))
            .map(member -> member.substring(member.indexOf(',') + 1))
            .findFirst()
            .orElse("");
    }
}
