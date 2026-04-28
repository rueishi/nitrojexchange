package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class SbeCodecBenchmark {
    private UnsafeBuffer buffer;
    private MessageHeaderEncoder headerEncoder;
    private MessageHeaderDecoder headerDecoder;
    private MarketDataEventEncoder marketDataEncoder;
    private MarketDataEventDecoder marketDataDecoder;

    @Setup
    public void setup() {
        buffer = new UnsafeBuffer(new byte[128]);
        headerEncoder = new MessageHeaderEncoder();
        headerDecoder = new MessageHeaderDecoder();
        marketDataEncoder = new MarketDataEventEncoder();
        marketDataDecoder = new MarketDataEventDecoder();
        encodeMarketData();
    }

    @Benchmark
    public int encodeMarketDataEvent() {
        encodeMarketData();
        return marketDataEncoder.encodedLength();
    }

    @Benchmark
    public long decodeMarketDataEvent() {
        marketDataDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return marketDataDecoder.priceScaled()
            + marketDataDecoder.sizeScaled()
            + marketDataDecoder.fixSeqNum();
    }

    private void encodeMarketData() {
        marketDataEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(EntryType.BID)
            .updateAction(UpdateAction.CHANGE)
            .priceScaled(65_000L * Ids.SCALE)
            .sizeScaled(2L * Ids.SCALE)
            .priceLevel(1)
            .ingressTimestampNanos(1_000_000L)
            .exchangeTimestampNanos(999_000L)
            .fixSeqNum(42);
    }
}
