package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.gateway.marketdata.AbstractFixL3MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.marketdata.L3MarketDataContext;
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
public class FixL3NormalizerBenchmark {
    private BenchmarkL3Normalizer normalizer;
    private UnsafeBuffer validMessage;
    private UnsafeBuffer malformedMessage;
    private int validLength;
    private int malformedLength;

    @Setup
    public void setup() {
        normalizer = new BenchmarkL3Normalizer();
        validMessage = fix("35=X", "34=3", "55=BTC-USD", "268=1",
            "279=0", "269=0", "278=order-1", "270=65000.00", "271=1.25");
        validLength = validMessage.capacity();
        malformedMessage = fix("35=X", "34=4", "55=BTC-USD", "268=1",
            "279=0", "269=0", "270=65000.00", "271=1.25");
        malformedLength = malformedMessage.capacity();
    }

    @Benchmark
    public int validL3IncrementalParse() {
        normalizer.onFixMessage(1L, validMessage, 0, validLength, 1L);
        return normalizer.publishCount;
    }

    @Benchmark
    public int malformedL3MessageSafeDrop() {
        normalizer.onFixMessage(1L, malformedMessage, 0, malformedLength, 1L);
        return normalizer.malformedCount;
    }

    private static UnsafeBuffer fix(final String... fields) {
        return new UnsafeBuffer((String.join("\001", fields) + "\001").getBytes(StandardCharsets.US_ASCII));
    }

    private static final class BenchmarkL3Normalizer extends AbstractFixL3MarketDataNormalizer {
        private int publishCount;
        private int malformedCount;

        @Override
        protected int venueId(final long sessionId) {
            return 1;
        }

        @Override
        protected int instrumentId(final String symbol) {
            return "BTC-USD".equals(symbol) ? 1 : 0;
        }

        @Override
        protected boolean publish(final L3MarketDataContext context) {
            publishCount++;
            return true;
        }

        @Override
        protected void onMalformedMessage(final RuntimeException ex) {
            malformedCount++;
        }
    }
}
