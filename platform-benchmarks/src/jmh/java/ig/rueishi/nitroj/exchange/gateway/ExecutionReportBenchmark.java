package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ExecutionReportBenchmark {
    private static final int OPS = 65_536;
    private static final long SESSION_ID = 7_001L;
    private static final char SOH = '\001';

    private GatewayDisruptor disruptor;
    private ExecutionHandler handler;
    private UnsafeBuffer fillReport;
    private UnsafeBuffer rejectReport;
    private UnsafeBuffer malformedReport;
    private int fillReportLength;
    private int rejectReportLength;
    private int malformedReportLength;
    private volatile int consumedLength;

    @Setup(Level.Trial)
    public void setup() {
        disruptor = new GatewayDisruptor(1024, 512, counters(), (slot, sequence, endOfBatch) -> {
            consumedLength = slot.length;
        });
        handler = new ExecutionHandler(new BenchmarkRegistry(), disruptor);
        fillReport = fix(
            "35=8",
            "34=21",
            "11=1000",
            "17=exec-fill-1",
            "37=venue-order-1",
            "54=1",
            "55=BTC-USD",
            "14=0.1",
            "150=F",
            "151=0",
            "31=65000",
            "32=0.1",
            "60=20260424-20:30:15.123456789");
        fillReportLength = fillReport.capacity();
        rejectReport = fix(
            "35=8",
            "34=22",
            "11=1001",
            "17=exec-reject-1",
            "37=venue-order-2",
            "54=1",
            "55=BTC-USD",
            "14=0",
            "150=8",
            "151=1",
            "103=7",
            "60=20260424-20:30:16.123456789");
        rejectReportLength = rejectReport.capacity();
        malformedReport = fix(
            "35=8",
            "34=23",
            "11=bad-id",
            "17=exec-malformed-1",
            "37=venue-order-3",
            "54=1",
            "55=BTC-USD",
            "150=0",
            "151=1",
            "60=20260424-20:30:17.123456789");
        malformedReportLength = malformedReport.capacity();
        disruptor.start();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        disruptor.close();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void fillExecutionReport(final Blackhole blackhole) {
        for (int i = 0; i < OPS; i++) {
            handler.onMessageForTest(SESSION_ID, fillReport, 0, fillReportLength);
        }
        blackhole.consume(consumedLength);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void rejectedExecutionReport(final Blackhole blackhole) {
        for (int i = 0; i < OPS; i++) {
            handler.onMessageForTest(SESSION_ID, rejectReport, 0, rejectReportLength);
        }
        blackhole.consume(consumedLength);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void malformedExecutionReportSafeDrop(final Blackhole blackhole) {
        for (int i = 0; i < OPS; i++) {
            handler.onMessageForTest(SESSION_ID, malformedReport, 0, malformedReportLength);
        }
        blackhole.consume(consumedLength);
    }

    private static UnsafeBuffer fix(final String... fields) {
        final String joined = String.join(String.valueOf(SOH), fields) + SOH;
        return new UnsafeBuffer(joined.getBytes(StandardCharsets.US_ASCII));
    }

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class BenchmarkRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            return sessionId == SESSION_ID ? 1 : 0;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return "BTC-USD".contentEquals(symbol) ? 1 : 0;
        }

        @Override
        public int instrumentId(final DirectBuffer buffer, final int offset, final int length) {
            return asciiEquals(buffer, offset, length, "BTC-USD") ? 1 : 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return instrumentId == 1 ? "BTC-USD" : null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return venueId == 1 ? "Coinbase" : null;
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
            // Benchmark fixture resolves the single session directly.
        }

        private static boolean asciiEquals(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final String expected) {

            if (length != expected.length()) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if ((char)buffer.getByte(offset + i) != expected.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
    }
}
