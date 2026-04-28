package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.FixConfig;
import ig.rueishi.nitroj.exchange.common.GatewayConfig;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.fix.FixProtocolPlugin;
import ig.rueishi.nitroj.exchange.gateway.fix.FixProtocolPluginRegistry;
import ig.rueishi.nitroj.exchange.gateway.marketdata.MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.VenuePlugin;
import ig.rueishi.nitroj.exchange.gateway.venue.VenuePluginRegistry;
import ig.rueishi.nitroj.exchange.registry.IdRegistryImpl;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.engine.EngineConfiguration;
import uk.co.real_logic.artio.engine.FixEngine;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionAcquireHandler;
import uk.co.real_logic.artio.library.SessionAcquiredInfo;
import uk.co.real_logic.artio.library.SessionConfiguration;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.session.Session;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gateway process entry point and component wiring root.
 *
 * <p>Responsibility: load gateway and registry configuration, create Aeron,
 * Aeron Cluster, Artio, gateway handlers, poll loops, and graceful shutdown
 * wiring in the order required by TASK-016. Role in system: this class is the
 * runtime composition boundary for the gateway process; individual protocol
 * adapters remain in their task-owned classes. Relationships:
 * {@link ConfigManager} supplies process and registry config, {@link IdRegistryImpl}
 * provides ID lookups, {@link GatewayDisruptor} carries gateway events to Aeron,
 * Artio handlers normalize FIX ingress, and {@link WarmupHarness} enforces the
 * warm-before-logon startup contract. Lifecycle: {@link #main(String[])} is
 * invoked by the application plugin, runs until an OS shutdown signal arrives,
 * then tears components down in reverse order. Design intent: keep startup
 * explicit and boring, with registry data loaded outside {@link GatewayConfig}
 * so process config does not become a metadata registry.</p>
 */
public final class GatewayMain {
    private static final Logger LOGGER = System.getLogger(GatewayMain.class.getName());
    private static final int COUNTERS_METADATA_BYTES = 1024 * 1024;
    private static final int COUNTERS_VALUES_BYTES = 64 * 1024;

    private GatewayMain() {
    }

    /**
     * Loads config paths and starts the gateway process.
     *
     * <p>Arguments are {@code gateway.toml}, {@code venues.toml}, and
     * {@code instruments.toml}. The warmup harness is loaded via
     * {@link ServiceLoader}; TASK-033 supplies the production implementation.
     * Missing warmup is fatal because the gateway must not initiate FIX before
     * warmup has completed.</p>
     *
     * @param args gateway, venue-registry, and instrument-registry paths
     * @throws Exception when startup, warmup, or runtime blocking fails
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "Usage: GatewayMain <gateway.toml> <venues.toml> <instruments.toml>");
        }

        final List<VenueConfig> venues = ConfigManager.loadVenues(args[1]);
        final GatewayConfig loadedConfig = ConfigManager.loadGateway(args[0]);
        final GatewayConfig config = withResolvedCredentials(
            loadedConfig,
            selectedVenue(loadedConfig, venues),
            System.getenv());
        final List<InstrumentConfig> instruments = ConfigManager.loadInstruments(args[2]);
        run(config, venues, instruments, loadWarmupHarness(), new ShutdownSignalBarrier());
    }

    /**
     * Starts the gateway with already-loaded config and an injected warmup harness.
     *
     * <p>This method exists so tests and harnesses can supply deterministic config
     * and a test double without installing a production warmup provider. The
     * sequence intentionally runs warmup before {@link FixLibrary#initiate} and
     * installs a shutdown hook that closes resources in reverse construction order.</p>
     *
     * @param config gateway process config
     * @param venues immutable venue registry records
     * @param instruments immutable instrument registry records
     * @param warmupHarness startup warmup implementation
     * @param barrier shutdown barrier to await
     * @throws Exception when startup or runtime blocking fails
     */
    static void run(
        final GatewayConfig config,
        final List<VenueConfig> venues,
        final List<InstrumentConfig> instruments,
        final WarmupHarness warmupHarness,
        final ShutdownSignalBarrier barrier) throws Exception {

        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(warmupHarness, "warmupHarness");
        final IdRegistryImpl idRegistry = buildIdRegistry(venues, instruments);
        final DeferredExecutionRouter deferredRouter = new DeferredExecutionRouter();

        final Aeron aeron = Aeron.connect(new Aeron.Context()
            .aeronDirectoryName(config.aeronDir())
            .idleStrategy(new SleepingMillisIdleStrategy(1)));
        final GatewayRuntime runtime = new GatewayRuntime(aeron);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "gateway-shutdown"));

        try {
            final AeronCluster clusterClient = AeronCluster.connect(new AeronCluster.Context()
                .aeron(aeron)
                .ownsAeronClient(false)
                .ingressChannel("aeron:udp")
                .ingressEndpoints(buildIngressEndpoints(config.clusterMembers()))
                .egressChannel(config.clusterEgressChannel()));
            runtime.clusterClient = clusterClient;

            final CountersManager countersManager = countersManager();
            final GatewayDisruptor disruptor = new GatewayDisruptor(
                config.disruptor(),
                countersManager,
                new AeronPublisher(clusterClient.ingressPublication(), () -> { }));
            runtime.disruptor = disruptor;

            final VenueConfig venue = selectedVenue(config, venues);
            final VenuePlugin venuePlugin = new VenuePluginRegistry().get(venue.venuePlugin());
            final OrderCommandHandler.BalanceQuerySink restPoller = venuePlugin.balanceQuerySink(
                config.rest(), config.credentials(), idRegistry, disruptor);
            final VenueStatusHandler venueStatusHandler = new VenueStatusHandler(idRegistry, disruptor);
            final OrderCommandHandler commandHandler = new OrderCommandHandler(
                deferredRouter,
                restPoller,
                recovery -> { },
                System.getLogger(OrderCommandHandler.class.getName()));
            configureEgressListener(clusterClient, commandHandler);

            final MarketDataHandler marketDataHandler = new MarketDataHandler(
                idRegistry,
                disruptor,
                System.getLogger(MarketDataHandler.class.getName()),
                buildMarketDataNormalizer(venue, idRegistry, disruptor));
            final ExecutionHandler executionHandler = new ExecutionHandler(idRegistry, disruptor);

            final FixEngine fixEngine = FixEngine.launch(engineConfiguration(config, venuePlugin));
            runtime.fixEngine = fixEngine;

            final FixLibrary fixLibrary = FixLibrary.connect(libraryConfiguration(
                config,
                idRegistry,
                venueStatusHandler,
                marketDataHandler,
                executionHandler,
                deferredRouter,
                disruptor,
                venues));
            runtime.fixLibrary = fixLibrary;

            warmupHarness.runGatewayWarmup(fixLibrary);
            fixLibrary.initiate(buildSessionConfig(config, venues));

            final Thread artioThread = Thread.ofPlatform()
                .name("artio-library")
                .start(new ArtioLibraryLoop(fixLibrary, config.cpu()));
            runtime.artioThread = artioThread;

            final Thread egressThread = Thread.ofPlatform()
                .name("gateway-egress")
                .start(new EgressPollLoop(clusterClient.egressSubscription(), commandHandler, config.cpu()));
            runtime.egressThread = egressThread;

            disruptor.start();
            LOGGER.log(Level.INFO, "Gateway started for venue: {0}", config.venueId());
            barrier.await();
        } finally {
            runtime.close();
        }
    }

    /**
     * Builds the startup registry without registering any Artio sessions.
     *
     * @param venues loaded venue records
     * @param instruments loaded instrument records
     * @return initialized registry ready for later live-session registration
     */
    static IdRegistryImpl buildIdRegistry(
        final List<VenueConfig> venues,
        final List<InstrumentConfig> instruments) {
        final IdRegistryImpl idRegistry = new IdRegistryImpl();
        idRegistry.init(venues, instruments);
        return idRegistry;
    }

    /**
     * Registers a live Artio session after Artio exposes its long session ID.
     *
     * @param idRegistry initialized registry
     * @param config gateway config carrying venue ID
     * @param session live Artio session
     */
    static void registerArtioSession(
        final IdRegistryImpl idRegistry,
        final GatewayConfig config,
        final Session session) {
        idRegistry.registerSession(config.venueId(), session.id());
    }

    static GatewayConfig withResolvedCredentials(
        final GatewayConfig config,
        final VenueConfig venue,
        final Map<String, String> environment) {

        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(environment, "environment");

        final VenuePlugin venuePlugin = new VenuePluginRegistry().get(venue.venuePlugin());
        final CredentialsConfig credentials = venuePlugin.resolveCredentials(config.credentials(), environment);
        if (Objects.equals(credentials, config.credentials())) {
            return config;
        }

        return GatewayConfig.builder()
            .venueId(config.venueId())
            .nodeRole(config.nodeRole())
            .aeronDir(config.aeronDir())
            .logDir(config.logDir())
            .clusterIngressChannel(config.clusterIngressChannel())
            .clusterEgressChannel(config.clusterEgressChannel())
            .clusterMembers(config.clusterMembers())
            .fix(config.fix())
            .credentials(credentials)
            .rest(config.rest())
            .cpu(config.cpu())
            .disruptor(config.disruptor())
            .counterFileDir(config.counterFileDir())
            .histogramOutputMs(config.histogramOutputMs())
            .warmup(config.warmup())
            .build();
    }

    static SessionConfiguration buildSessionConfig(final GatewayConfig config, final List<VenueConfig> venues) {
        final VenueConfig venue = selectedVenue(config, venues);
        final FixConfig fix = config.fix();
        final FixProtocolPlugin fixPlugin = new FixProtocolPluginRegistry().get(venue.fixPlugin());
        return fixPlugin.configureSession(SessionConfiguration.builder(), fix, venue).build();
    }

    static VenueRuntime buildVenueRuntime(
        final GatewayConfig config,
        final List<VenueConfig> venues,
        final IdRegistryImpl idRegistry,
        final GatewayDisruptor disruptor,
        final ExecutionRouterImpl.FixSender sender,
        final ExecutionRouterImpl.RejectPublisher rejectPublisher) {

        final VenueConfig venue = selectedVenue(config, venues);
        final FixProtocolPlugin fixPlugin = new FixProtocolPluginRegistry().get(venue.fixPlugin());
        final VenuePlugin venuePlugin = new VenuePluginRegistry().get(venue.venuePlugin());
        final MarketDataNormalizer marketDataNormalizer = venuePlugin.marketDataNormalizer(venue, idRegistry, disruptor);
        final var orderEntryAdapter = venuePlugin.orderEntryAdapter(
            venue,
            sender,
            idRegistry,
            config.fix().senderCompId(),
            disruptor::incrementAeronBackPressureCounter,
            rejectPublisher);
        return new VenueRuntime(venue, fixPlugin, venuePlugin, marketDataNormalizer, orderEntryAdapter);
    }

    static MarketDataNormalizer buildMarketDataNormalizer(
        final VenueConfig venue,
        final IdRegistryImpl idRegistry,
        final GatewayDisruptor disruptor) {

        final VenuePlugin venuePlugin = new VenuePluginRegistry().get(venue.venuePlugin());
        return venuePlugin.marketDataNormalizer(venue, idRegistry, disruptor);
    }

    private static VenueConfig selectedVenue(final GatewayConfig config, final List<VenueConfig> venues) {
        return venues.stream()
            .filter(candidate -> candidate.id() == config.venueId())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No venue config for venueId=" + config.venueId()));
    }

    static String buildIngressEndpoints(final List<String> clusterMembers) {
        final StringBuilder endpoints = new StringBuilder();
        for (int i = 0; i < clusterMembers.size(); i++) {
            if (i > 0) {
                endpoints.append(',');
            }
            endpoints.append(i).append('=').append(endpoint(clusterMembers.get(i)));
        }
        return endpoints.toString();
    }

    private static String endpoint(final String member) {
        final int marker = member.indexOf("endpoint=");
        if (marker < 0) {
            return member;
        }
        final int start = marker + "endpoint=".length();
        final int end = member.indexOf('|', start);
        return end < 0 ? member.substring(start) : member.substring(start, end);
    }

    private static WarmupHarness loadWarmupHarness() {
        return ServiceLoader.load(WarmupHarness.class).findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No WarmupHarness provider found; TASK-033 must supply the production implementation"));
    }

    private static EngineConfiguration engineConfiguration(final GatewayConfig config, final VenuePlugin venuePlugin) {
        final var logonCustomizer = venuePlugin.logonCustomizer(config.credentials());
        return new EngineConfiguration()
            .libraryAeronChannel("aeron:ipc")
            .logFileDir(config.fix().artioLogDir())
            .replayIndexFileRecordCapacity(config.fix().artioReplayCapacity())
            .sessionCustomisationStrategy(new uk.co.real_logic.artio.session.SessionCustomisationStrategy() {
                @Override
                public void configureLogon(
                    final uk.co.real_logic.artio.builder.AbstractLogonEncoder logon,
                    final long sessionId) {
                    logonCustomizer.customizeLogon(logon, sessionId);
                }

                @Override
                public void configureLogout(
                    final uk.co.real_logic.artio.builder.AbstractLogoutEncoder logout,
                    final long sessionId) {
                    // Venue plugins currently customize logon only.
                }
            });
    }

    private static LibraryConfiguration libraryConfiguration(
        final GatewayConfig config,
        final IdRegistryImpl idRegistry,
        final VenueStatusHandler venueStatusHandler,
        final MarketDataHandler marketDataHandler,
        final ExecutionHandler executionHandler,
        final DeferredExecutionRouter deferredRouter,
        final GatewayDisruptor disruptor,
        final List<VenueConfig> venues) {
        final SessionAcquireHandler acquireHandler = (session, info) -> {
            registerArtioSession(idRegistry, config, session);
            final VenueRuntime venueRuntime = buildVenueRuntime(
                config,
                venues,
                idRegistry,
                disruptor,
                session::trySend,
                (clOrdId, venueId, instrumentId) ->
                    LOGGER.log(Level.WARNING, "FIX send rejected after back-pressure: clOrdId={0}", clOrdId));
            deferredRouter.delegate(new ExecutionRouterImpl(venueRuntime.orderEntryAdapter()));
            return new CompositeSessionHandler(
                venueStatusHandler.onSessionAcquired(session, info),
                marketDataHandler,
                executionHandler);
        };
        return new LibraryConfiguration()
            .libraryAeronChannels(List.of("aeron:ipc"))
            .sessionAcquireHandler(acquireHandler);
    }

    private static CountersManager countersManager() {
        return new CountersManager(
            new UnsafeBuffer(new byte[COUNTERS_METADATA_BYTES]),
            new UnsafeBuffer(new byte[COUNTERS_VALUES_BYTES]));
    }

    private static void configureEgressListener(final AeronCluster clusterClient, final OrderCommandHandler handler) {
        clusterClient.context().egressListener(new EgressAdapter(handler));
    }

    private static final class DeferredExecutionRouter implements ExecutionRouter {
        private final AtomicReference<ExecutionRouter> delegate = new AtomicReference<>();

        private void delegate(final ExecutionRouter router) {
            delegate.set(router);
        }

        @Override
        public void routeNewOrder(final ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder command) {
            current().routeNewOrder(command);
        }

        @Override
        public void routeCancel(final ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder command) {
            current().routeCancel(command);
        }

        @Override
        public void routeReplace(final ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder command) {
            current().routeReplace(command);
        }

        @Override
        public void routeStatusQuery(final ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder command) {
            current().routeStatusQuery(command);
        }

        private ExecutionRouter current() {
            final ExecutionRouter router = delegate.get();
            if (router == null) {
                throw new IllegalStateException("FIX session is not acquired yet");
            }
            return router;
        }
    }

    private static final class CompositeSessionHandler implements SessionHandler {
        private final SessionHandler venueStatusHandler;
        private final MarketDataHandler marketDataHandler;
        private final ExecutionHandler executionHandler;

        private CompositeSessionHandler(
            final SessionHandler venueStatusHandler,
            final MarketDataHandler marketDataHandler,
            final ExecutionHandler executionHandler) {
            this.venueStatusHandler = venueStatusHandler;
            this.marketDataHandler = marketDataHandler;
            this.executionHandler = executionHandler;
        }

        @Override
        public Action onMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final int libraryId,
            final Session session,
            final int sequenceIndex,
            final long messageType,
            final long timestamp,
            final long position,
            final OnMessageInfo messageInfo) {
            marketDataHandler.onMessage(buffer, offset, length, libraryId, session, sequenceIndex,
                messageType, timestamp, position, messageInfo);
            executionHandler.onMessage(buffer, offset, length, libraryId, session, sequenceIndex,
                messageType, timestamp, position, messageInfo);
            return Action.CONTINUE;
        }

        @Override
        public void onTimeout(final int libraryId, final Session session) {
            venueStatusHandler.onTimeout(libraryId, session);
        }

        @Override
        public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow) {
            venueStatusHandler.onSlowStatus(libraryId, session, hasBecomeSlow);
        }

        @Override
        public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason) {
            return venueStatusHandler.onDisconnect(libraryId, session, reason);
        }

        @Override
        public void onSessionStart(final Session session) {
            venueStatusHandler.onSessionStart(session);
        }
    }

    private static final class EgressAdapter implements EgressListener {
        private final OrderCommandHandler handler;

        private EgressAdapter(final OrderCommandHandler handler) {
            this.handler = handler;
        }

        @Override
        public void onMessage(
            final long clusterSessionId,
            final long timestamp,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header) {
            handler.onFragment(buffer, offset, length, header);
        }
    }

    private static final class GatewayRuntime implements AutoCloseable {
        private final Aeron aeron;
        private AeronCluster clusterClient;
        private GatewayDisruptor disruptor;
        private FixEngine fixEngine;
        private FixLibrary fixLibrary;
        private Thread artioThread;
        private Thread egressThread;
        private boolean closed;

        private GatewayRuntime(final Aeron aeron) {
            this.aeron = aeron;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (artioThread != null) {
                artioThread.interrupt();
            }
            if (egressThread != null) {
                egressThread.interrupt();
            }
            closeQuietly(disruptor);
            closeQuietly(fixLibrary);
            closeQuietly(fixEngine);
            closeQuietly(clusterClient);
            closeQuietly(aeron);
        }

        private static void closeQuietly(final AutoCloseable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (final Exception ex) {
                    LOGGER.log(Level.WARNING, "Gateway shutdown close failed: {0}", ex.getMessage());
                }
            }
        }
    }
}
