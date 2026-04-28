package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouterImpl;
import ig.rueishi.nitroj.exchange.gateway.venue.StandardOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
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
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class OrderEncodingBenchmark {
    private static final int OPS = 4_096;
    private static final int VENUE_ID = 1;
    private static final int INSTRUMENT_ID = 1;
    private static final byte[] VENUE_ORDER_ID = {'v', 'e', 'n', 'u', 'e', '-', '1'};

    private UnsafeBuffer buffer;
    private MessageHeaderEncoder headerEncoder;
    private NewOrderCommandEncoder encoder;
    private StandardOrderEntryAdapter adapter;
    private NewOrderCommandDecoder newOrderDecoder;
    private CancelOrderCommandDecoder cancelDecoder;
    private ReplaceOrderCommandDecoder replaceDecoder;
    private OrderStatusQueryCommandDecoder statusDecoder;
    private BenchmarkSender sender;

    @Setup
    public void setup() {
        buffer = new UnsafeBuffer(new byte[256]);
        headerEncoder = new MessageHeaderEncoder();
        encoder = new NewOrderCommandEncoder();
        sender = new BenchmarkSender();
        adapter = new StandardOrderEntryAdapter(
            sender,
            new BenchmarkRegistry(),
            "ACC1",
            () -> { },
            (clOrdId, venueId, instrumentId) -> { },
            Clock.fixed(Instant.parse("2026-04-24T20:30:15.123Z"), ZoneOffset.UTC),
            () -> true);
        newOrderDecoder = newOrderDecoder();
        cancelDecoder = cancelDecoder();
        replaceDecoder = replaceDecoder();
        statusDecoder = statusDecoder();
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

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long fixNewOrderSingle() {
        for (int i = 0; i < OPS; i++) {
            newOrderDecoder.sbeRewind();
            adapter.sendNewOrder(newOrderDecoder);
        }
        return sender.encodedBytes;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long fixCancelReplaceStatusCycle() {
        for (int i = 0; i < OPS; i++) {
            cancelDecoder.sbeRewind();
            adapter.sendCancel(cancelDecoder);
            replaceDecoder.sbeRewind();
            adapter.sendReplace(replaceDecoder);
            statusDecoder.sbeRewind();
            adapter.sendStatusQuery(statusDecoder);
        }
        return sender.encodedBytes;
    }

    private static NewOrderCommandDecoder newOrderDecoder() {
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[256]);
        final NewOrderCommandEncoder commandEncoder = new NewOrderCommandEncoder();
        commandEncoder.wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderEncoder())
            .clOrdId(1001L)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .strategyId((short)Ids.STRATEGY_ARB)
            .side(Side.BUY)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.GTC)
            .priceScaled(65_000L * Ids.SCALE)
            .qtyScaled(Ids.SCALE);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(commandBuffer, 0, header);
        return decoder;
    }

    private static CancelOrderCommandDecoder cancelDecoder() {
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[256]);
        final CancelOrderCommandEncoder commandEncoder = new CancelOrderCommandEncoder();
        commandEncoder.wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderEncoder())
            .cancelClOrdId(2001L)
            .origClOrdId(1001L)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.BUY)
            .originalQtyScaled(Ids.SCALE)
            .putVenueOrderId(VENUE_ORDER_ID, 0, VENUE_ORDER_ID.length);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final CancelOrderCommandDecoder decoder = new CancelOrderCommandDecoder();
        decoder.wrapAndApplyHeader(commandBuffer, 0, header);
        return decoder;
    }

    private static ReplaceOrderCommandDecoder replaceDecoder() {
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[256]);
        final ReplaceOrderCommandEncoder commandEncoder = new ReplaceOrderCommandEncoder();
        commandEncoder.wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderEncoder())
            .origClOrdId(1001L)
            .newClOrdId(3001L)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.BUY)
            .newPriceScaled(65_100L * Ids.SCALE)
            .newQtyScaled(Ids.SCALE)
            .origQtyScaled(Ids.SCALE)
            .putVenueOrderId(VENUE_ORDER_ID, 0, VENUE_ORDER_ID.length);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final ReplaceOrderCommandDecoder decoder = new ReplaceOrderCommandDecoder();
        decoder.wrapAndApplyHeader(commandBuffer, 0, header);
        return decoder;
    }

    private static OrderStatusQueryCommandDecoder statusDecoder() {
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[256]);
        final OrderStatusQueryCommandEncoder commandEncoder = new OrderStatusQueryCommandEncoder();
        commandEncoder.wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderEncoder())
            .clOrdId(1001L)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.BUY)
            .putVenueOrderId(VENUE_ORDER_ID, 0, VENUE_ORDER_ID.length);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final OrderStatusQueryCommandDecoder decoder = new OrderStatusQueryCommandDecoder();
        decoder.wrapAndApplyHeader(commandBuffer, 0, header);
        return decoder;
    }

    private static final class BenchmarkSender implements ExecutionRouterImpl.FixSender {
        private static final byte[] SENDER_COMP_ID = {'N', 'I', 'T', 'R', 'O'};
        private static final byte[] TARGET_COMP_ID = {'C', 'O', 'I', 'N', 'B', 'A', 'S', 'E'};
        private static final byte[] SENDING_TIME = {
            '2', '0', '2', '6', '0', '4', '2', '4', '-',
            '2', '0', ':', '3', '0', ':', '1', '5', '.', '1', '2', '3'
        };

        private final MutableAsciiBuffer output = new MutableAsciiBuffer(new byte[1024]);
        private int sequence = 1;
        private long encodedBytes;

        @Override
        public long trySend(final Encoder encoder) {
            encoder.header()
                .senderCompID(SENDER_COMP_ID, 0, SENDER_COMP_ID.length)
                .targetCompID(TARGET_COMP_ID, 0, TARGET_COMP_ID.length)
                .msgSeqNum(sequence++)
                .sendingTime(SENDING_TIME, 0, SENDING_TIME.length);
            final long encoded = encoder.encode(output, 0);
            encodedBytes += Encoder.length(encoded);
            return encodedBytes;
        }
    }

    private static final class BenchmarkRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return "BTC-USD".contentEquals(symbol) ? INSTRUMENT_ID : 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return instrumentId == INSTRUMENT_ID ? "BTC-USD" : null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return "coinbase";
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
        }
    }
}
