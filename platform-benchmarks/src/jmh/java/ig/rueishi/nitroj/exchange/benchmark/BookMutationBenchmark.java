package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.cluster.ConsolidatedL2Book;
import ig.rueishi.nitroj.exchange.cluster.L2OrderBook;
import ig.rueishi.nitroj.exchange.cluster.VenueL3Book;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class BookMutationBenchmark {
    private static final int OPS = 65_536;
    private Arena arena;
    private L2OrderBook l2Book;
    private L2OrderBook l3DerivedBook;
    private VenueL3Book l3Book;
    private ConsolidatedL2Book consolidatedBook;
    private MarketDataEventDecoder bidUpdate;
    private MarketDataEventDecoder bidDelete;
    private UnsafeBuffer l3AddBuffer;
    private UnsafeBuffer l3ChangeBuffer;
    private UnsafeBuffer l3DeleteBuffer;
    private final MarketByOrderEventDecoder l3Add = new MarketByOrderEventDecoder();
    private final MarketByOrderEventDecoder l3Change = new MarketByOrderEventDecoder();
    private final MarketByOrderEventDecoder l3Delete = new MarketByOrderEventDecoder();

    @Setup
    public void setup() {
        arena = Arena.ofConfined();
        l2Book = new L2OrderBook(1, 1, arena);
        l3DerivedBook = new L2OrderBook(1, 1, arena);
        l3Book = new VenueL3Book(1, 1, 1024);
        consolidatedBook = new ConsolidatedL2Book(1);
        bidUpdate = event(EntryType.BID, UpdateAction.CHANGE, 65_000L * Ids.SCALE, 1L * Ids.SCALE);
        bidDelete = event(EntryType.BID, UpdateAction.DELETE, 65_000L * Ids.SCALE, 0L);
        l3AddBuffer = l3Event("bench-order-1", Side.BUY, UpdateAction.NEW, 65_000L * Ids.SCALE, 1L * Ids.SCALE);
        l3ChangeBuffer = l3Event("bench-order-1", Side.BUY, UpdateAction.CHANGE, 65_000L * Ids.SCALE, 2L * Ids.SCALE);
        l3DeleteBuffer = l3Event("bench-order-1", Side.BUY, UpdateAction.DELETE, 0L, 0L);
    }

    @Benchmark
    public long l2Upsert() {
        l2Book.apply(bidUpdate, 1L);
        return l2Book.getBestBid();
    }

    @Benchmark
    public long l2ZeroSizeDelete() {
        l2Book.apply(bidUpdate, 1L);
        l2Book.apply(bidDelete, 2L);
        return l2Book.getBestBid();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long consolidatedUpdate() {
        for (int i = 0; i < OPS; i++) {
            consolidatedBook.applyVenueLevel(1, EntryType.BID, 65_000L * Ids.SCALE, 1L * Ids.SCALE);
        }
        return consolidatedBook.bestBid();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long l3AddChangeDeleteDerivesL2() {
        for (int i = 0; i < OPS; i++) {
            l3Book.apply(wrap(l3Add, l3AddBuffer), l3DerivedBook, 1L);
            l3Book.apply(wrap(l3Change, l3ChangeBuffer), l3DerivedBook, 2L);
            l3Book.apply(wrap(l3Delete, l3DeleteBuffer), l3DerivedBook, 3L);
        }
        return l3Book.activeOrderCount() + l3DerivedBook.getBestBid();
    }

    private static MarketDataEventDecoder event(
        final EntryType side,
        final UpdateAction action,
        final long price,
        final long size) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(1)
            .instrumentId(1)
            .entryType(side)
            .updateAction(action)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(1)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static UnsafeBuffer l3Event(
        final String orderId,
        final Side side,
        final UpdateAction action,
        final long price,
        final long size) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] orderIdBytes = orderId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        new MarketByOrderEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(1)
            .instrumentId(1)
            .side(side)
            .updateAction(action)
            .priceScaled(price)
            .sizeScaled(size)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1)
            .putVenueOrderId(orderIdBytes, 0, orderIdBytes.length);
        return buffer;
    }

    private static MarketByOrderEventDecoder wrap(final MarketByOrderEventDecoder decoder, final UnsafeBuffer buffer) {
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketByOrderEventEncoder.BLOCK_LENGTH, MarketByOrderEventEncoder.SCHEMA_VERSION);
        return decoder;
    }
}
