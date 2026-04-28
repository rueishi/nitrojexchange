package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class OrderEncodingBenchmark {
    private UnsafeBuffer buffer;
    private MessageHeaderEncoder headerEncoder;
    private NewOrderCommandEncoder encoder;

    @Setup
    public void setup() {
        buffer = new UnsafeBuffer(new byte[256]);
        headerEncoder = new MessageHeaderEncoder();
        encoder = new NewOrderCommandEncoder();
    }

    @Benchmark
    public int encodeIocOrderCommand() {
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
            .clOrdId(1L)
            .venueId(1)
            .instrumentId(1)
            .strategyId((short)Ids.STRATEGY_ARB)
            .side(Side.BUY)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(65_000L * Ids.SCALE)
            .qtyScaled(Ids.SCALE);
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }
}
