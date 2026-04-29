package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class MultiLegContingentExecutionBenchmark {
    private static final long PARENT_ID = 39_001L;
    private static final long BASE_ID = 40_000L;
    private static final long LEG1_ID = BASE_ID + 1L;
    private static final long LEG2_ID = BASE_ID + 2L;
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = Ids.SCALE;

    private MultiLegContingentExecution strategy;
    private ParentOrderRegistry registry;
    private BenchmarkOrderManager orderManager;
    private ParentOrderIntentView intent;
    private ChildExecutionView leg1Fill;
    private ChildExecutionView leg2Fill;

    @Setup(Level.Trial)
    public void setupTrial() {
        registry = new ParentOrderRegistry(8, 8);
        orderManager = new BenchmarkOrderManager();
        strategy = new MultiLegContingentExecution();
        strategy.init(new ExecutionStrategyContext(
            new InternalMarketView(),
            new RiskStub(),
            orderManager,
            registry,
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            () -> 1L,
            (correlationId, deadlineClusterMicros) -> true,
            new IdRegistryStub(),
            counters()));
        intent = new ParentOrderIntentView().wrap(intentDecoder());
        leg1Fill = new ChildExecutionView().wrap(fill(LEG1_ID), PARENT_ID);
        leg2Fill = new ChildExecutionView().wrap(fill(LEG2_ID), PARENT_ID);
    }

    @Benchmark
    public long legFillCallbacks() {
        registry.reset();
        orderManager.resetAll();
        strategy.onParentIntent(intent);
        strategy.onChildExecution(leg1Fill);
        strategy.onChildExecution(leg2Fill);
        return strategy.bothLegsFilled();
    }

    @Benchmark
    public long timerHedgeCallback() {
        registry.reset();
        orderManager.resetAll();
        strategy.onParentIntent(intent);
        strategy.onChildExecution(leg1Fill);
        strategy.onTimer(strategy.timerCorrelationId());
        return strategy.imbalanceHedges();
    }

    private static ParentOrderIntentDecoder intentDecoder() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(PARENT_ID)
            .strategyId((short) Ids.STRATEGY_ARB)
            .executionStrategyId(ExecutionStrategyIds.MULTI_LEG_CONTINGENT)
            .intentType(ParentIntentType.MULTI_LEG)
            .side(Side.BUY)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .primaryVenueId(Ids.VENUE_COINBASE)
            .secondaryVenueId(Ids.VENUE_COINBASE_SANDBOX)
            .quantityScaled(QTY)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(PRICE)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 0)
            .correlationId(BASE_ID)
            .legCount((byte) 2)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(PRICE + 1L)
            .parentTimeoutMicros(1_000L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ExecutionEventDecoder fill(final long childClOrdId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(ExecType.FILL)
            .side(Side.BUY)
            .fillPriceScaled(PRICE)
            .fillQtyScaled(QTY)
            .cumQtyScaled(QTY)
            .leavesQtyScaled(0L)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class RiskStub implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class IdRegistryStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
