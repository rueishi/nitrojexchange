package ig.rueishi.nitroj.exchange.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ArbStrategyConfig;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.MarketMakingConfig;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.MultiLegContingentExecution;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.execution.PostOnlyQuoteExecution;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouter;
import ig.rueishi.nitroj.exchange.gateway.OrderCommandHandler;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import ig.rueishi.nitroj.exchange.simulator.CoinbaseExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.SimulatorConfig;
import ig.rueishi.nitroj.exchange.strategy.ArbStrategy;
import ig.rueishi.nitroj.exchange.strategy.MarketMakingStrategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import ig.rueishi.nitroj.exchange.test.TradingSystemTestHarness;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V13 parent-intent live-wire E2E coverage over the local Coinbase simulator.
 *
 * <p>Topology: trading strategy emits a parent intent, the execution strategy
 * creates child {@code OrderState} and SBE order commands, the gateway
 * {@link OrderCommandHandler} path decodes the SBE child command, and a local
 * test router writes the corresponding FIX NewOrderSingle to the Coinbase
 * simulator socket. The simulator produces execution reports, the test converts
 * that simulator evidence back into normalized SBE execution events, drives
 * parent state, and invokes the same parent-terminal callback trading
 * strategies use in cluster. No live Coinbase network, REST, credentials, or
 * external venue endpoint is used; QA/UAT remains blocked until these local
 * gates plus the release gates pass.</p>
 */
@Tag("E2E")
final class V13ParentIntentLiveWireE2ETest {
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = Ids.SCALE / 10L;

    @Test
    void marketMakingQuoteIntent_postOnlyExecution_reachesSimulatorFillAndParentCallback() throws Exception {
        try (TradingSystemTestHarness harness = startHarness(CoinbaseExchangeSimulator.FillMode.IMMEDIATE);
             FixClient client = FixClient.connect(harness.simulator().config().port())) {
            client.logon();
            applySimulatorL2Snapshot(harness, client);
            harness.marketView().apply(marketData(Ids.VENUE_COINBASE, EntryType.BID, 65_000L * Ids.SCALE), 1L);
            harness.marketView().apply(marketData(Ids.VENUE_COINBASE, EntryType.ASK, 65_001L * Ids.SCALE), 1L);
            final ParentFixture fixture = parentFixture(harness.marketView(), harness.idRegistry(), false);
            final MarketMakingStrategy strategy = new MarketMakingStrategy(mmConfig());
            strategy.init(strategyContext(fixture, harness.marketView(), harness.idRegistry()));

            strategy.onMarketData(marketData(Ids.VENUE_COINBASE, EntryType.ASK, (65_001L * Ids.SCALE)));
            final NewOrderCommandDecoder child = newOrder(fixture.commandBuffer);
            final long parentOrderId = child.parentOrderId();

            routeNewOrderToSimulator(fixture.commandBuffer, client, harness.idRegistry());
            client.readMessageContaining("150=F");
            driveSimulatorReports(harness.simulator(), fixture, child.clOrdId(), child.side());
            strategy.onParentTerminal(parentTerminal(parentOrderId, Ids.STRATEGY_MARKET_MAKING,
                ExecutionStrategyIds.POST_ONLY_QUOTE, ParentTerminalReason.COMPLETED));

            assertThat(harness.simulator().receivedOrders()).hasSize(1);
            assertThat(harness.simulator().getFillCount()).isEqualTo(1);
            assertThat(fixture.registry.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.DONE);
            assertThat(liveAskParentId(strategy)).isZero();
            assertThat(fixture.engine.parentIntentDispatches()).isGreaterThanOrEqualTo(1);
            assertThat(fixture.engine.childExecutionDispatches()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void arbMultiLegIntent_contingentExecution_reachesSimulatorFillAndCooldownCallback() throws Exception {
        try (TradingSystemTestHarness harness = startHarness(CoinbaseExchangeSimulator.FillMode.IMMEDIATE);
             FixClient client = FixClient.connect(harness.simulator().config().port())) {
            client.logon();
            final ParentFixture fixture = parentFixture(harness.marketView(), harness.idRegistry(), true);
            final ArbStrategy strategy = new ArbStrategy(arbConfig());
            strategy.init(strategyContext(fixture, harness.marketView(), harness.idRegistry()));
            applyArbOpportunity(harness.marketView());

            strategy.onMarketData(marketData(Ids.VENUE_COINBASE, EntryType.ASK, 65_000L * Ids.SCALE));
            final NewOrderCommandDecoder child = newOrder(fixture.commandBuffer);
            final long parentOrderId = child.parentOrderId();

            routeNewOrderToSimulator(fixture.commandBuffer, client, harness.idRegistry());
            client.readMessageContaining("150=F");
            driveSimulatorReports(harness.simulator(), fixture, child.clOrdId(), child.side());
            fixture.multiLeg.onTimer(fixture.multiLeg.timerCorrelationId());
            strategy.onParentTerminal(parentTerminal(parentOrderId, Ids.STRATEGY_ARB,
                ExecutionStrategyIds.MULTI_LEG_CONTINGENT, ParentTerminalReason.HEDGE_FAILED));

            assertThat(harness.simulator().receivedOrders()).hasSize(1);
            assertThat(harness.simulator().getFillCount()).isEqualTo(1);
            assertThat(fixture.risk.killSwitchReason).isEqualTo("hedge_risk_reject");
            assertThat(fixture.registry.lookup(parentOrderId).terminalReasonCode())
                .isEqualTo(ParentOrderState.REASON_HEDGE_FAILED);
            assertThat(cooldownUntil(strategy)).isGreaterThan(1_000L);
            assertThat(fixture.engine.parentIntentDispatches()).isEqualTo(1);
        }
    }

    private static ParentFixture parentFixture(
        final InternalMarketView marketView,
        final IdRegistry idRegistry,
        final boolean rejectHedgeRisk) {

        final ParentOrderRegistry parents = new ParentOrderRegistry(16, 32);
        final OrderManagerImpl orders = new OrderManagerImpl();
        final RiskStub risk = new RiskStub(rejectHedgeRisk);
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final PostOnlyQuoteExecution postOnly = new PostOnlyQuoteExecution();
        final MultiLegContingentExecution multiLeg = new MultiLegContingentExecution();
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry();
        registry.register(postOnly);
        registry.register(multiLeg);
        registry.allowCompatibility(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.POST_ONLY_QUOTE);
        registry.allowCompatibility(Ids.STRATEGY_ARB, ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        final ExecutionStrategyContext context = new ExecutionStrategyContext(
            marketView,
            risk,
            orders,
            parents,
            commandBuffer,
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            () -> 1_000L,
            (correlationId, deadlineClusterMicros) -> true,
            idRegistry,
            counters());
        final ExecutionStrategyEngine engine = new ExecutionStrategyEngine(registry, context);
        engine.initRegisteredStrategies();
        return new ParentFixture(parents, orders, risk, commandBuffer, engine, multiLeg);
    }

    private static StrategyContextImpl strategyContext(
        final ParentFixture fixture,
        final InternalMarketView marketView,
        final IdRegistry idRegistry) {

        return new StrategyContextImpl(
            marketView,
            fixture.risk,
            fixture.orders,
            new PortfolioStub(),
            new RecoveryStub(),
            fixture.engine,
            cluster(),
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            idRegistry,
            counters());
    }

    private static void routeNewOrderToSimulator(
        final UnsafeBuffer commandBuffer,
        final FixClient client,
        final IdRegistry idRegistry) {

        final SocketExecutionRouter router = new SocketExecutionRouter(client, idRegistry);
        new OrderCommandHandler(router).onFragment(commandBuffer, 0,
            MessageHeaderEncoder.ENCODED_LENGTH + NewOrderCommandEncoder.BLOCK_LENGTH, null);
        assertThat(router.sentMessages).isEqualTo(1);
    }

    private static void driveSimulatorReports(
        final CoinbaseExchangeSimulator simulator,
        final ParentFixture fixture,
        final long childClOrdId,
        final Side side) {

        final List<CoinbaseExchangeSimulator.ExecutionReport> reports = simulator.executionReports().stream()
            .filter(report -> Long.toString(childClOrdId).equals(report.clOrdId()))
            .toList();
        assertThat(reports).extracting(CoinbaseExchangeSimulator.ExecutionReport::execType).contains("0", "F");
        for (CoinbaseExchangeSimulator.ExecutionReport report : reports) {
            final OrderState child = fixture.orders.getOrder(childClOrdId);
            final long parentOrderId = child.parentOrderId();
            final ExecutionEventDecoder execution = execution(report, childClOrdId, side);
            fixture.orders.onExecution(execution);
            fixture.engine.onChildExecution(execution, parentOrderId);
        }
    }

    private static ExecutionEventDecoder execution(
        final CoinbaseExchangeSimulator.ExecutionReport report,
        final long childClOrdId,
        final Side side) {

        final ExecType execType = switch (report.execType()) {
            case "0" -> ExecType.NEW;
            case "F" -> ExecType.FILL;
            case "8" -> ExecType.REJECTED;
            default -> ExecType.ORDER_STATUS;
        };
        final long fillQty = scale(report.lastQty());
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = ("SIM-" + childClOrdId).getBytes(StandardCharsets.US_ASCII);
        final byte[] execId = ("sim-" + childClOrdId + '-' + report.execType()).getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(execType)
            .side(side)
            .fillPriceScaled(scale(report.lastPx()))
            .fillQtyScaled(fillQty)
            .cumQtyScaled(fillQty)
            .leavesQtyScaled(execType == ExecType.FILL ? 0L : QTY)
            .rejectCode(Math.max(report.rejectReason(), 0))
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(execType == ExecType.FILL ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execId, 0, execId.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static void applySimulatorL2Snapshot(final TradingSystemTestHarness harness, final FixClient client)
        throws IOException {

        client.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
        assertThat(harness.applyCoinbaseL2FixMessage(client.readMessageContaining("269=0"))).isTrue();
        assertThat(harness.applyCoinbaseL2FixMessage(client.readMessageContaining("269=1"))).isTrue();
    }

    private static void applyArbOpportunity(final InternalMarketView marketView) {
        marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.ASK, 65_000L * Ids.SCALE), 1L);
        marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.BID, 64_990L * Ids.SCALE), 1L);
        marketView.apply(marketData(Ids.VENUE_COINBASE_SANDBOX, EntryType.BID, 65_200L * Ids.SCALE), 1L);
        marketView.apply(marketData(Ids.VENUE_COINBASE_SANDBOX, EntryType.ASK, 65_210L * Ids.SCALE), 1L);
    }

    private static MarketDataEventDecoder marketData(final int venueId, final EntryType entryType, final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(entryType)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(10L * Ids.SCALE)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static ParentOrderTerminalDecoder parentTerminal(
        final long parentOrderId,
        final int strategyId,
        final int executionStrategyId,
        final ParentTerminalReason reason) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new ParentOrderTerminalEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) strategyId)
            .executionStrategyId(executionStrategyId)
            .finalCumFillQtyScaled(QTY)
            .terminalReason(reason);
        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static NewOrderCommandDecoder newOrder(final UnsafeBuffer buffer) {
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
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

    private static MarketMakingConfig mmConfig() {
        return new MarketMakingConfig(
            Ids.INSTRUMENT_BTC_USD,
            Ids.VENUE_COINBASE,
            10,
            0,
            QTY,
            QTY,
            10L * Ids.SCALE,
            10L * Ids.SCALE,
            1,
            10_000_000L,
            10_000_000L,
            1_000,
            2_000,
            10L * Ids.SCALE,
            QTY,
            1_000);
    }

    private static ArbStrategyConfig arbConfig() {
        final long[] zero = new long[Ids.MAX_VENUES + 1];
        return new ArbStrategyConfig(
            Ids.INSTRUMENT_BTC_USD,
            new int[] {Ids.VENUE_COINBASE, Ids.VENUE_COINBASE_SANDBOX},
            1,
            zero,
            zero,
            zero,
            QTY,
            QTY,
            100L,
            1_000L,
            25_000L);
    }

    private static Cluster cluster() {
        return (Cluster) Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[] {Cluster.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "time" -> 1_000L;
                case "logPosition" -> 70_000L;
                case "timeUnit" -> TimeUnit.MICROSECONDS;
                case "scheduleTimer", "cancelTimer" -> true;
                case "offer" -> ((Number) args[2]).longValue();
                case "toString" -> "V13ParentIntentLiveWireCluster";
                default -> null;
            });
    }

    private static long liveAskParentId(final MarketMakingStrategy strategy) throws ReflectiveOperationException {
        final Field field = MarketMakingStrategy.class.getDeclaredField("liveAskClOrdId");
        field.setAccessible(true);
        return field.getLong(strategy);
    }

    private static long cooldownUntil(final ArbStrategy strategy) throws ReflectiveOperationException {
        final Field field = ArbStrategy.class.getDeclaredField("cooldownUntilMicros");
        field.setAccessible(true);
        return field.getLong(strategy);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static long scale(final double value) {
        return Math.round(value * Ids.SCALE);
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private record ParentFixture(
        ParentOrderRegistry registry,
        OrderManagerImpl orders,
        RiskStub risk,
        UnsafeBuffer commandBuffer,
        ExecutionStrategyEngine engine,
        MultiLegContingentExecution multiLeg) {
    }

    private static final class SocketExecutionRouter implements ExecutionRouter {
        private final FixClient client;
        private final IdRegistry idRegistry;
        private int sentMessages;

        SocketExecutionRouter(final FixClient client, final IdRegistry idRegistry) {
            this.client = client;
            this.idRegistry = idRegistry;
        }

        @Override
        public void routeNewOrder(final NewOrderCommandDecoder command) {
            sentMessages++;
            try {
                client.send("D", Map.of(
                    "49", "NITRO",
                    "56", "COINBASE",
                    "11", Long.toString(command.clOrdId()),
                    "55", idRegistry.symbolOf(command.instrumentId()),
                    "54", command.side() == Side.BUY ? "1" : "2",
                    "44", decimal(command.priceScaled()),
                    "38", decimal(command.qtyScaled()),
                    "40", "2",
                    "59", command.timeInForce() == TimeInForce.IOC ? "3" : "1"));
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override public void routeCancel(final CancelOrderCommandDecoder command) { }
        @Override public void routeReplace(final ReplaceOrderCommandDecoder command) { }
        @Override public void routeStatusQuery(final OrderStatusQueryCommandDecoder command) { }

        private static String decimal(final long scaled) {
            return Double.toString((double) scaled / Ids.SCALE);
        }
    }

    private static final class RiskStub implements RiskEngine {
        private final boolean rejectHedgeRisk;
        private String killSwitchReason;

        RiskStub(final boolean rejectHedgeRisk) {
            this.rejectHedgeRisk = rejectHedgeRisk;
        }

        @Override
        public RiskDecision preTradeCheck(
            final int venueId,
            final int instrumentId,
            final byte side,
            final long priceScaled,
            final long qtyScaled,
            final int strategyId) {

            return rejectHedgeRisk && strategyId == Ids.STRATEGY_ARB_HEDGE
                ? RiskDecision.REJECT_MAX_NOTIONAL
                : RiskDecision.APPROVED;
        }

        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { killSwitchReason = reason; }
        @Override public void deactivateKillSwitch() { killSwitchReason = null; }
        @Override public boolean isKillSwitchActive() { return killSwitchReason != null; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class PortfolioStub implements PortfolioEngine {
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0L; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0L; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0L; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0L; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0L; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void archiveDailyPnl(final Publication egressPublication) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RecoveryStub implements RecoveryCoordinator {
        @Override public void onVenueStatus(final VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }

    private static final class FixClient implements AutoCloseable {
        private static final char SOH = '\001';
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private String pending = "";

        private FixClient(final Socket socket) throws IOException {
            this.socket = socket;
            input = socket.getInputStream();
            output = socket.getOutputStream();
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
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
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
