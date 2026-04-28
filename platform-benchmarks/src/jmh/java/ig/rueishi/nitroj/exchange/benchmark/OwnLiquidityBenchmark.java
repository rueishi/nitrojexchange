package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.cluster.ExternalLiquidityView;
import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.OwnOrderOverlay;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
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
public class OwnLiquidityBenchmark {
    private OwnOrderOverlay overlay;
    private ExternalLiquidityView externalLiquidityView;

    @Setup
    public void setup() {
        overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "venue-order-1", 1, 1, EntryType.BID, 65_000L * Ids.SCALE, Ids.SCALE, true);
        externalLiquidityView = new ExternalLiquidityView(new InternalMarketView(), overlay);
    }

    @Benchmark
    public long ownLevelLookup() {
        return overlay.ownSizeAt(1, 1, EntryType.BID, 65_000L * Ids.SCALE);
    }

    @Benchmark
    public long exactL3Lookup() {
        return externalLiquidityView.externalSizeAtL3Order(
            1, 1, EntryType.BID, 65_000L * Ids.SCALE, 2L * Ids.SCALE, "venue-order-1");
    }
}
