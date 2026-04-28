package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.gateway.marketdata.AbstractFixL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.marketdata.L2MarketDataContext;
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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class FixL2NormalizerBenchmark {
    private BenchmarkL2Normalizer normalizer;
    private UnsafeBuffer validMessage;
    private UnsafeBuffer unsupportedMessage;
    private int validLength;
    private int unsupportedLength;

    @Setup
    public void setup() {
        normalizer = new BenchmarkL2Normalizer();
        validMessage = fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "279=0", "269=0", "270=65000.00", "271=1.25", "1023=1");
        validLength = validMessage.capacity();
        unsupportedMessage = fix("35=8", "34=2", "55=BTC-USD");
        unsupportedLength = unsupportedMessage.capacity();
    }

    @Benchmark
    public int validL2SnapshotParse() {
        normalizer.onFixMessage(1L, validMessage, 0, validLength, 1L);
        return normalizer.publishCount;
    }

    @Benchmark
    public int ignoredUnsupportedMessage() {
        normalizer.onFixMessage(1L, unsupportedMessage, 0, unsupportedLength, 1L);
        return normalizer.publishCount;
    }

    private static UnsafeBuffer fix(final String... fields) {
        return new UnsafeBuffer((String.join("\001", fields) + "\001").getBytes(StandardCharsets.US_ASCII));
    }

    private static final class BenchmarkL2Normalizer extends AbstractFixL2MarketDataNormalizer {
        private int publishCount;

        @Override
        protected int venueId(final long sessionId) {
            return 1;
        }

        @Override
        protected int instrumentId(final String symbol) {
            return "BTC-USD".equals(symbol) ? 1 : 0;
        }

        @Override
        protected boolean publish(final L2MarketDataContext context) {
            publishCount++;
            return true;
        }
    }
}
