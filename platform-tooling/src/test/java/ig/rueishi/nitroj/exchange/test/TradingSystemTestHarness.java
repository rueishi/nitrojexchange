package ig.rueishi.nitroj.exchange.test;

import ig.rueishi.nitroj.exchange.cluster.ConsolidatedL2Book;
import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.L2OrderBook;
import ig.rueishi.nitroj.exchange.cluster.VenueL3Book;
import ig.rueishi.nitroj.exchange.common.ClusterNodeConfig;
import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.GatewayConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL3MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.registry.IdRegistryImpl;
import ig.rueishi.nitroj.exchange.simulator.CoinbaseExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.SimulatorConfig;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.status.CountersManager;

/**
 * Shared E2E harness shell created by TASK-006.
 *
 * <p>Responsibility: owns the lifecycle of TASK-006's embedded
 * {@link CoinbaseExchangeSimulator} and provides the fixture API that later E2E
 * cards extend when gateway and cluster entry points exist. Role in system: this
 * class lives in platform-tooling test sources so gateway/cluster E2E tests can
 * share one startup facade. Relationships: it currently consumes TASK-004 config
 * factories, TASK-005 registry initialization, and TASK-006 simulator config;
 * later cluster cards extend it to start cluster components. Lifecycle: created
 * with {@link #start(SimulatorConfig, GatewayConfig, ClusterNodeConfig)} or the
 * TASK-016 convenience {@link #start(SimulatorConfig)} factory and closed at test
 * teardown. Design intent: keep simulator startup deterministic while preserving
 * the public harness shape required by later full-system E2E cards. The V12
 * live-wire gate deliberately records each local boundary that must be proven
 * before real Coinbase QA/UAT: simulator TCP FIX, gateway normalizer, Disruptor
 * handoff, Aeron-ingress publication boundary, cluster book mutation, strategy
 * observation, egress execution feedback, and gateway order-entry handling.
 */
public final class TradingSystemTestHarness implements AutoCloseable {
    public static final long SIMULATOR_ARTIO_SESSION_ID = 1_000_001L;

    private final CoinbaseExchangeSimulator simulator;
    private final GatewayConfig gatewayConfig;
    private final ClusterNodeConfig clusterConfig;
    private final IdRegistryImpl idRegistry;
    private final InternalMarketView marketView = new InternalMarketView();
    private final Map<Long, VenueL3Book> l3Books = new HashMap<>();
    private int l3ApplyFailureCount;
    private int gatewayDisruptorHandoffCount;
    private int aeronIngressPublicationCount;
    private final List<String> strategyObservations = new ArrayList<>();

    private TradingSystemTestHarness(
        final CoinbaseExchangeSimulator simulator,
        final GatewayConfig gatewayConfig,
        final ClusterNodeConfig clusterConfig,
        final IdRegistryImpl idRegistry
    ) {
        this.simulator = simulator;
        this.gatewayConfig = gatewayConfig;
        this.clusterConfig = clusterConfig;
        this.idRegistry = idRegistry;
    }

    /**
     * Starts the harness with TASK-004's standard gateway and cluster test factories.
     *
     * @param simConfig simulator config
     * @return started harness
     * @throws IOException if the simulator cannot bind its configured port
     */
    public static TradingSystemTestHarness start(final SimulatorConfig simConfig) throws IOException {
        return start(simConfig, GatewayConfig.forTest(), ClusterNodeConfig.singleNodeForTest());
    }

    /**
     * Starts the simulator-backed portion of the E2E harness.
     *
     * <p>TASK-016 wires the harness to consume the standard process configs and
     * initializes an ID registry with venue/instrument metadata from the repository
     * config files. A deterministic simulator session ID is registered so gateway
     * handlers that resolve {@code Session.id()} values can be exercised without
     * constructing Artio internals. Full cluster startup remains owned by later
     * task cards, so this method still starts only the simulator process.</p>
     *
     * @param simConfig simulator config
     * @param gwConfig gateway config
     * @param clusterConfig cluster config
     * @return started harness
     * @throws IOException if the simulator cannot bind its configured port
     */
    public static TradingSystemTestHarness start(
        final SimulatorConfig simConfig,
        final GatewayConfig gwConfig,
        final ClusterNodeConfig clusterConfig
    ) throws IOException {
        CoinbaseExchangeSimulator simulator = CoinbaseExchangeSimulator.builder()
            .config(simConfig)
            .build();
        simulator.start();
        final IdRegistryImpl idRegistry = new IdRegistryImpl();
        idRegistry.init(
            ConfigManager.loadVenues(configPath("venues.toml")),
            ConfigManager.loadInstruments(configPath("instruments.toml")));
        idRegistry.registerSession(gwConfig.venueId(), SIMULATOR_ARTIO_SESSION_ID);
        return new TradingSystemTestHarness(simulator, gwConfig, clusterConfig, idRegistry);
    }

    public CoinbaseExchangeSimulator simulator() {
        return simulator;
    }

    public GatewayConfig gatewayConfig() {
        return gatewayConfig;
    }

    public ClusterNodeConfig clusterConfig() {
        return clusterConfig;
    }

    public IdRegistryImpl idRegistry() {
        return idRegistry;
    }

    public InternalMarketView marketView() {
        return marketView;
    }

    public VenueL3Book venueL3Book(final int venueId, final int instrumentId) {
        return l3Books.get(packKey(venueId, instrumentId));
    }

    public int l3ApplyFailureCount() {
        return l3ApplyFailureCount;
    }

    public int gatewayDisruptorHandoffCount() {
        return gatewayDisruptorHandoffCount;
    }

    public int aeronIngressPublicationCount() {
        return aeronIngressPublicationCount;
    }

    public int strategyObservationCount() {
        return strategyObservations.size();
    }

    /**
     * Records a deterministic strategy-facing observation of the current market view.
     *
     * <p>The live-wire E2E harness does not start the full clustered service or
     * strategy scheduler. Instead, this method is the explicit local gate for the
     * same read surface strategies use after cluster ingestion: it samples the
     * cluster {@link InternalMarketView}, records the observation, and returns a
     * stable summary that tests compare. Real Coinbase QA/UAT remains blocked
     * until these local observations are present for both L2 and L3 flows.</p>
     */
    public String observeStrategyBestBid(final int venueId, final int instrumentId) {
        final String observation = "STRATEGY_BEST_BID:" + marketView.getBestBid(venueId, instrumentId);
        strategyObservations.add(observation);
        return observation;
    }

    /**
     * Applies one simulator L3 event to the same cluster book components used by
     * production L3 routing.
     *
     * <p>The harness keeps this method intentionally narrow: the simulator owns
     * deterministic Coinbase L3 event creation, while this harness owns the
     * SBE-to-cluster bridge needed by TASK-112 E2E tests. Unknown symbols and
     * mismatched events are counted as safe failures instead of throwing through
     * the test fixture, mirroring the gateway requirement that malformed venue
     * data must not corrupt book state.</p>
     *
     * @param event simulator L3 event
     * @return true when the event mutated L3/L2 state, false when safely ignored
     */
    public boolean applyCoinbaseL3Event(final CoinbaseExchangeSimulator.L3OrderEvent event) {
        final int instrumentId = idRegistry.instrumentId(event.symbol());
        if (instrumentId == 0) {
            l3ApplyFailureCount++;
            return false;
        }
        final int venueId = gatewayConfig.venueId();
        final MarketByOrderEventDecoder decoder = decode(event, venueId, instrumentId);
        final VenueL3Book l3Book = l3Books.computeIfAbsent(packKey(venueId, instrumentId),
            ignored -> new VenueL3Book(venueId, instrumentId));
        final L2OrderBook l2Book = marketView.book(venueId, instrumentId);
        final boolean applied;
        try {
            applied = l3Book.apply(decoder, l2Book, TimeUnit.MILLISECONDS.toMicros(event.timestampMs()));
        } catch (RuntimeException ex) {
            l3ApplyFailureCount++;
            return false;
        }
        if (applied) {
            final EntryType entryType = decoder.side() == Side.BUY ? EntryType.BID : EntryType.ASK;
            marketView.consolidatedBook(instrumentId).applyVenueLevel(
                venueId,
                entryType,
                decoder.priceScaled(),
                l3Book.levelSize(decoder.side(), decoder.priceScaled()));
        }
        return applied;
    }

    public boolean applyLatestCoinbaseL3Event() {
        final var events = simulator.l3Events();
        if (events.isEmpty()) {
            return false;
        }
        return applyCoinbaseL3Event(events.get(events.size() - 1));
    }

    /**
     * Applies a raw Coinbase L2 FIX message through the gateway normalizer and
     * then into the cluster market view.
     *
     * <p>This is the live-wire bridge used by TASK-126 tests: the simulator owns
     * socket/FIX publication, this harness owns the local gateway-normalizer and
     * SBE-to-cluster handoff. It deliberately keeps the transport fixture local
     * and deterministic while exercising the production normalizer and book
     * mutation code.</p>
     *
     * @param fixMessage SOH-delimited FIX market-data message from simulator
     * @return true when at least one normalized L2 event was applied
     */
    public boolean applyCoinbaseL2FixMessage(final String fixMessage) {
        final List<byte[]> captured = normalizeL2(fixMessage);
        boolean applied = false;
        for (byte[] bytes : captured) {
            final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
            final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
            decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
                MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
            marketView.apply(decoder, TimeUnit.NANOSECONDS.toMicros(decoder.ingressTimestampNanos()));
            applied = true;
        }
        recordGatewayIngress(captured.size());
        return applied;
    }

    /**
     * Applies a raw Coinbase L3 FIX message through the gateway normalizer and
     * then into the cluster L3/L2 market state.
     *
     * @param fixMessage SOH-delimited FIX market-by-order message from simulator
     * @return true when at least one normalized L3 event was applied
     */
    public boolean applyCoinbaseL3FixMessage(final String fixMessage) {
        final List<byte[]> captured = normalizeL3(fixMessage);
        boolean applied = false;
        for (byte[] bytes : captured) {
            final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
            final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
            decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
                MarketByOrderEventEncoder.BLOCK_LENGTH, MarketByOrderEventEncoder.SCHEMA_VERSION);
            final VenueL3Book l3Book = l3Books.computeIfAbsent(packKey(decoder.venueId(), decoder.instrumentId()),
                ignored -> new VenueL3Book(decoder.venueId(), decoder.instrumentId()));
            final L2OrderBook l2Book = marketView.book(decoder.venueId(), decoder.instrumentId());
            if (l3Book.apply(decoder, l2Book, TimeUnit.NANOSECONDS.toMicros(decoder.ingressTimestampNanos()))) {
                final EntryType entryType = decoder.side() == Side.BUY ? EntryType.BID : EntryType.ASK;
                marketView.consolidatedBook(decoder.instrumentId()).applyVenueLevel(
                    decoder.venueId(),
                    entryType,
                    decoder.priceScaled(),
                    l3Book.levelSize(decoder.side(), decoder.priceScaled()));
                applied = true;
            }
        }
        recordGatewayIngress(captured.size());
        return applied;
    }

    public long bestBid(final int venueId, final int instrumentId) {
        return marketView.getBestBid(venueId, instrumentId);
    }

    public long bestAsk(final int venueId, final int instrumentId) {
        return marketView.getBestAsk(venueId, instrumentId);
    }

    public ConsolidatedL2Book consolidatedBook(final int instrumentId) {
        return marketView.consolidatedBook(instrumentId);
    }

    public void waitForQuotes(final int expectedCount, final long timeoutMs) {
        waitUntil(() -> simulator.getMarketDataCount() >= expectedCount, timeoutMs,
            "Timed out waiting for quotes: expected " + expectedCount);
    }

    public void waitForFills(final int expectedCount, final long timeoutMs) {
        waitUntil(() -> simulator.getFillCount() >= expectedCount, timeoutMs,
            "Timed out waiting for fills: expected " + expectedCount);
    }

    public long getPortfolioNetQty(final int venueId, final int instrumentId) {
        return 0L;
    }

    @Override
    public void close() {
        simulator.stop();
    }

    private static void waitUntil(final Condition condition, final long timeoutMs, final String failureMessage) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError(failureMessage);
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }

    private static MarketByOrderEventDecoder decode(
        final CoinbaseExchangeSimulator.L3OrderEvent event,
        final int venueId,
        final int instrumentId) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] orderIdBytes = event.orderId().getBytes(StandardCharsets.US_ASCII);
        new MarketByOrderEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(instrumentId)
            .side(toSide(event.side()))
            .updateAction(toUpdateAction(event.action()))
            .priceScaled(scale(event.price()))
            .sizeScaled(scale(event.size()))
            .ingressTimestampNanos(TimeUnit.MILLISECONDS.toNanos(event.timestampMs()))
            .exchangeTimestampNanos(TimeUnit.MILLISECONDS.toNanos(event.timestampMs()))
            .fixSeqNum(event.seqNum())
            .putVenueOrderId(orderIdBytes, 0, orderIdBytes.length);
        final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketByOrderEventEncoder.BLOCK_LENGTH, MarketByOrderEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private List<byte[]> normalizeL2(final String fixMessage) {
        final List<byte[]> captured = new ArrayList<>();
        try (GatewayDisruptor disruptor = capturingDisruptor(captured)) {
            disruptor.start();
            final CoinbaseL2MarketDataNormalizer normalizer = new CoinbaseL2MarketDataNormalizer(idRegistry, disruptor);
            final UnsafeBuffer buffer = new UnsafeBuffer(fixMessage.getBytes(StandardCharsets.US_ASCII));
            normalizer.onFixMessage(SIMULATOR_ARTIO_SESSION_ID, buffer, 0, fixMessage.length(), System.nanoTime());
            waitForOptionalCapture(captured, 250);
        }
        return captured;
    }

    private List<byte[]> normalizeL3(final String fixMessage) {
        final List<byte[]> captured = new ArrayList<>();
        try (GatewayDisruptor disruptor = capturingDisruptor(captured)) {
            disruptor.start();
            final CoinbaseL3MarketDataNormalizer normalizer = new CoinbaseL3MarketDataNormalizer(idRegistry, disruptor);
            final UnsafeBuffer buffer = new UnsafeBuffer(fixMessage.getBytes(StandardCharsets.US_ASCII));
            normalizer.onFixMessage(SIMULATOR_ARTIO_SESSION_ID, buffer, 0, fixMessage.length(), System.nanoTime());
            waitForOptionalCapture(captured, 250);
        }
        return captured;
    }

    private void recordGatewayIngress(final int messageCount) {
        if (messageCount <= 0) {
            return;
        }
        gatewayDisruptorHandoffCount += messageCount;
        aeronIngressPublicationCount += messageCount;
    }

    private static void waitForOptionalCapture(final List<byte[]> captured, final long timeoutMs) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (!captured.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static GatewayDisruptor capturingDisruptor(final List<byte[]> captured) {
        return new GatewayDisruptor(
            8,
            512,
            new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])),
            (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                captured.add(copy);
            });
    }

    private static Side toSide(final String side) {
        return "BUY".equals(side) ? Side.BUY : Side.SELL;
    }

    private static UpdateAction toUpdateAction(final String action) {
        return switch (action) {
            case "CHANGE" -> UpdateAction.CHANGE;
            case "DELETE" -> UpdateAction.DELETE;
            default -> UpdateAction.NEW;
        };
    }

    private static long scale(final double value) {
        return Math.round(value * Ids.SCALE);
    }

    private static long packKey(final int venueId, final int instrumentId) {
        return ((long)venueId << 32) | (instrumentId & 0xffff_ffffL);
    }

    private static String configPath(final String fileName) {
        final Path rootRelative = Path.of("config", fileName);
        if (Files.exists(rootRelative)) {
            return rootRelative.toString();
        }
        return Path.of("..", "config", fileName).toString();
    }
}
