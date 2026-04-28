package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.strategy.Strategy;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class StrategyTickBenchmark {
    private Strategy noopStrategy;
    private MarketDataEventDecoder marketData;

    @Setup
    public void setup() {
        noopStrategy = new NoopStrategy();
        marketData = new MarketDataEventDecoder();
    }

    @Benchmark
    public int strategyMarketDataDispatch() {
        noopStrategy.onMarketData(marketData);
        return noopStrategy.strategyId();
    }

    private static final class NoopStrategy implements Strategy {
        @Override public void init(final ig.rueishi.nitroj.exchange.strategy.StrategyContext ctx) { }
        @Override public void onMarketData(final MarketDataEventDecoder decoder) { }
        @Override public void onFill(final ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder decoder) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
        @Override public void onKillSwitch() { }
        @Override public void onKillSwitchCleared() { }
        @Override public void onVenueRecovered(final int venueId) { }
        @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) { }
        @Override public int[] subscribedInstrumentIds() { return new int[] { 1 }; }
        @Override public int[] activeVenueIds() { return new int[] { 1 }; }
        @Override public void shutdown() { }
        @Override public int strategyId() { return 999; }
    }
}
