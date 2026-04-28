package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.cluster.ExternalLiquidityView;
import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ArbStrategyConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.MarketMakingConfig;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.strategy.ArbStrategy;
import ig.rueishi.nitroj.exchange.strategy.MarketMakingStrategy;
import ig.rueishi.nitroj.exchange.strategy.Strategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContext;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import io.aeron.Aeron;
import io.aeron.DirectBufferVector;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class StrategyTickBenchmark {
    private Strategy noopStrategy;
    private MarketDataEventDecoder noopMarketData;
    private MarketMakingStrategy marketMakingStrategy;
    private MarketDataEventDecoder marketMakingTick;
    private ArbStrategy arbStrategy;
    private MarketDataEventDecoder arbTick;
    private BenchmarkCluster mmCluster;

    @Setup
    public void setup() {
        noopStrategy = new NoopStrategy();
        noopMarketData = marketData(Ids.VENUE_COINBASE, EntryType.BID, 100_000L * Ids.SCALE);

        final InternalMarketView mmView = new InternalMarketView();
        apply(mmView, Ids.VENUE_COINBASE, EntryType.BID, 99_900L * Ids.SCALE);
        marketMakingTick = apply(mmView, Ids.VENUE_COINBASE, EntryType.ASK, 100_100L * Ids.SCALE);
        mmCluster = new BenchmarkCluster();
        marketMakingStrategy = new MarketMakingStrategy(new MarketMakingConfig(
            Ids.INSTRUMENT_BTC_USD,
            Ids.VENUE_COINBASE,
            10,
            1_000,
            10L * Ids.SCALE,
            20L * Ids.SCALE,
            10L * Ids.SCALE,
            10L * Ids.SCALE,
            5,
            1,
            10_000_000,
            500,
            8_000,
            10L * Ids.SCALE,
            Ids.SCALE,
            1_000));
        marketMakingStrategy.init(context(mmView, mmCluster));

        final InternalMarketView arbView = new InternalMarketView();
        apply(arbView, Ids.VENUE_COINBASE, EntryType.BID, 99_900L * Ids.SCALE);
        arbTick = apply(arbView, Ids.VENUE_COINBASE, EntryType.ASK, 100_000L * Ids.SCALE);
        final long[] fees = new long[Ids.MAX_VENUES + 1];
        final long[] baseSlip = new long[Ids.MAX_VENUES + 1];
        final long[] slope = new long[Ids.MAX_VENUES + 1];
        baseSlip[Ids.VENUE_COINBASE] = 5;
        baseSlip[Ids.VENUE_COINBASE_SANDBOX] = 5;
        arbStrategy = new ArbStrategy(new ArbStrategyConfig(
            Ids.INSTRUMENT_BTC_USD,
            new int[] {Ids.VENUE_COINBASE, Ids.VENUE_COINBASE_SANDBOX},
            10_000,
            fees,
            baseSlip,
            slope,
            Ids.SCALE,
            10L * Ids.SCALE,
            100,
            5_000_000,
            10_000_000));
        arbStrategy.init(context(arbView, new BenchmarkCluster()));
    }

    @Benchmark
    public int strategyMarketDataDispatch() {
        noopStrategy.onMarketData(noopMarketData);
        return noopStrategy.strategyId();
    }

    @Benchmark
    public long marketMakingTick() {
        mmCluster.time += 2L;
        mmCluster.logPosition += 4L;
        marketMakingStrategy.onMarketData(marketMakingTick);
        return mmCluster.offeredBytes;
    }

    @Benchmark
    public long arbStrategyTick() {
        arbStrategy.onMarketData(arbTick);
        return arbStrategy.strategyId();
    }

    private static StrategyContext context(final InternalMarketView marketView, final BenchmarkCluster cluster) {
        return new StrategyContextImpl(
            marketView,
            new BenchmarkRisk(),
            new BenchmarkOrderManager(),
            new BenchmarkPortfolio(),
            new BenchmarkRecovery(),
            cluster,
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            null,
            null);
    }

    private static MarketDataEventDecoder apply(
        final InternalMarketView marketView,
        final int venueId,
        final EntryType entryType,
        final long price) {
        final MarketDataEventDecoder decoder = marketData(venueId, entryType, price);
        marketView.apply(decoder, 1L);
        return decoder;
    }

    private static MarketDataEventDecoder marketData(final int venueId, final EntryType entryType, final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
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
        decoder.wrap(
            buffer,
            MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH,
            MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static final class NoopStrategy implements Strategy {
        private static final int[] INSTRUMENTS = {1};
        private static final int[] VENUES = {1};
        @Override public void init(final StrategyContext ctx) { }
        @Override public void onMarketData(final MarketDataEventDecoder decoder) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
        @Override public void onKillSwitch() { }
        @Override public void onKillSwitchCleared() { }
        @Override public void onVenueRecovered(final int venueId) { }
        @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) { }
        @Override public int[] subscribedInstrumentIds() { return INSTRUMENTS; }
        @Override public int[] activeVenueIds() { return VENUES; }
        @Override public void shutdown() { }
        @Override public int strategyId() { return 999; }
    }

    private static final class BenchmarkCluster implements Cluster {
        long time = 1L;
        long logPosition = 100L;
        long offeredBytes;
        @Override public int memberId() { return 0; }
        @Override public Role role() { return Role.LEADER; }
        @Override public long logPosition() { return logPosition; }
        @Override public Aeron aeron() { return null; }
        @Override public ClusteredServiceContainer.Context context() { return null; }
        @Override public ClientSession getClientSession(final long clusterSessionId) { return null; }
        @Override public Collection<ClientSession> clientSessions() { return Collections.emptyList(); }
        @Override public void forEachClientSession(final Consumer<? super ClientSession> action) { }
        @Override public boolean closeClientSession(final long clusterSessionId) { return false; }
        @Override public long time() { return time; }
        @Override public TimeUnit timeUnit() { return TimeUnit.MICROSECONDS; }
        @Override public boolean scheduleTimer(final long correlationId, final long deadline) { return true; }
        @Override public boolean cancelTimer(final long correlationId) { return true; }
        @Override public long offer(final DirectBuffer buffer, final int offset, final int length) { offeredBytes += length; return length; }
        @Override public long offer(final DirectBufferVector[] vectors) { return 0; }
        @Override public long tryClaim(final int length, final BufferClaim bufferClaim) { return 0; }
        @Override public IdleStrategy idleStrategy() { return null; }
    }

    private static final class BenchmarkRisk implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class BenchmarkOrderManager implements OrderManager {
        long orderCount;
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { orderCount++; }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return false; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class BenchmarkPortfolio implements PortfolioEngine {
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void archiveDailyPnl(final Publication egressPublication) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class BenchmarkRecovery implements RecoveryCoordinator {
        @Override public void onVenueStatus(final VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }
}
