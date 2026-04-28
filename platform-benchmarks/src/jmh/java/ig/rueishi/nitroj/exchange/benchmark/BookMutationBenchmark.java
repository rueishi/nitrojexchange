package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.cluster.ConsolidatedL2Book;
import ig.rueishi.nitroj.exchange.cluster.L2OrderBook;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
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

import java.lang.foreign.Arena;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class BookMutationBenchmark {
    private Arena arena;
    private L2OrderBook l2Book;
    private ConsolidatedL2Book consolidatedBook;
    private MarketDataEventDecoder bidUpdate;
    private MarketDataEventDecoder bidDelete;

    @Setup
    public void setup() {
        arena = Arena.ofConfined();
        l2Book = new L2OrderBook(1, 1, arena);
        consolidatedBook = new ConsolidatedL2Book(1);
        bidUpdate = event(EntryType.BID, UpdateAction.CHANGE, 65_000L * Ids.SCALE, 1L * Ids.SCALE);
        bidDelete = event(EntryType.BID, UpdateAction.DELETE, 65_000L * Ids.SCALE, 0L);
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
    public long consolidatedUpdate() {
        consolidatedBook.applyVenueLevel(1, EntryType.BID, 65_000L * Ids.SCALE, 1L * Ids.SCALE);
        return consolidatedBook.bestBid();
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
}
