package ig.rueishi.nitroj.exchange.order;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
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
public class OrderManagerBenchmark {
    private static final int OPS = 1024;
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = Ids.SCALE;

    private OrderManagerImpl manager;
    private ExecutionEventDecoder[] acknowledgements;
    private ExecutionEventDecoder[] fills;

    @Setup
    public void setup() {
        manager = new OrderManagerImpl();
        acknowledgements = new ExecutionEventDecoder[OPS];
        fills = new ExecutionEventDecoder[OPS];
        for (int i = 0; i < OPS; i++) {
            final long clOrdId = i + 1L;
            acknowledgements[i] = event(clOrdId, ExecType.NEW, 0L, 0L, QTY, false, "", "venue-" + clOrdId);
            fills[i] = event(clOrdId, ExecType.FILL, PRICE, QTY, 0L, true, "", "venue-" + clOrdId);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int createAckFillReleaseCycle() {
        for (int i = 0; i < OPS; i++) {
            final long clOrdId = i + 1L;
            manager.createPendingOrder(
                clOrdId,
                Ids.VENUE_COINBASE,
                Ids.INSTRUMENT_BTC_USD,
                Side.BUY.value(),
                OrdType.LIMIT.value(),
                TimeInForce.GTC.value(),
                PRICE,
                QTY,
                Ids.STRATEGY_MARKET_MAKING);
            manager.onExecution(acknowledgements[i]);
            manager.onExecution(fills[i]);
        }
        return manager.poolAvailable();
    }

    private static ExecutionEventDecoder event(
        final long clOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId,
        final String venueOrderId) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderIdBytes = venueOrderId.getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(fillPrice)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(fillQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderIdBytes, 0, venueOrderIdBytes.length)
            .putExecId(execIdBytes, 0, execIdBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            ExecutionEventEncoder.BLOCK_LENGTH, ExecutionEventEncoder.SCHEMA_VERSION);
        return decoder;
    }
}
