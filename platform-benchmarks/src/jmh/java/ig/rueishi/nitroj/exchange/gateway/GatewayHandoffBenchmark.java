package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
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

import java.util.concurrent.TimeUnit;

/**
 * V12 allocation evidence for the gateway handoff boundary.
 *
 * <p>Fixtures are fully built in {@link #setup()}: the Disruptor ring owns its
 * {@link GatewaySlot} instances, SBE payloads are encoded once into reusable
 * slots, and the Aeron offer function is a deterministic in-memory acceptance
 * function. Measured methods only claim, publish, offer, reset, and update
 * primitive counters, matching the steady-state ownership transfer used in the
 * runtime gateway.</p>
 */
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class GatewayHandoffBenchmark {
    private static final int OPS = 1_024;
    private static final byte[] VENUE_ORDER_ID = {'v'};
    private static final byte[] EXEC_ID = {'e'};

    private GatewayDisruptor disruptor;
    private AeronPublisher publisher;
    private GatewaySlot marketDataSlot;
    private GatewaySlot fillSlot;
    private int marketDataLength;
    private int fillLength;
    private volatile int consumedLength;
    private long offeredMessages;
    private long droppedMessages;

    @Setup(Level.Trial)
    public void setup() {
        disruptor = new GatewayDisruptor(16_384, 512, counters(), (slot, sequence, endOfBatch) -> {
            consumedLength = slot.length;
        });
        disruptor.start();

        publisher = new AeronPublisher((buffer, offset, length) -> {
            offeredMessages++;
            return offeredMessages;
        }, () -> droppedMessages++);

        marketDataSlot = marketDataSlot();
        marketDataLength = marketDataSlot.length;
        fillSlot = executionSlot();
        fillLength = fillSlot.length;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        disruptor.close();
    }

    /**
     * Measures successful producer claim/publish handoff into a running
     * Disruptor with the consumer already wired and warmed.
     */
    @Benchmark
    @OperationsPerInvocation(OPS)
    public void disruptorClaimPublish(final Blackhole blackhole) {
        for (int i = 0; i < OPS; i++) {
            GatewaySlot slot;
            do {
                slot = disruptor.claimSlot();
                if (slot == null) {
                    Thread.onSpinWait();
                }
            } while (slot == null);
            slot.buffer.putInt(0, i);
            slot.length = Integer.BYTES;
            disruptor.publishSlot(slot);
        }
        blackhole.consume(consumedLength);
    }

    /**
     * Measures the non-fill Aeron publication path when Aeron accepts
     * immediately, so the lossy-drop counter remains untouched.
     */
    @Benchmark
    @OperationsPerInvocation(OPS)
    public void aeronMarketDataAccepted(final Blackhole blackhole) throws Exception {
        for (int i = 0; i < OPS; i++) {
            marketDataSlot.length = marketDataLength;
            publisher.onEvent(marketDataSlot, i, true);
        }
        blackhole.consume(offeredMessages);
    }

    /**
     * Measures the lossless fill publication path when Aeron accepts
     * immediately.
     */
    @Benchmark
    @OperationsPerInvocation(OPS)
    public void aeronFillAccepted(final Blackhole blackhole) throws Exception {
        for (int i = 0; i < OPS; i++) {
            fillSlot.length = fillLength;
            publisher.onEvent(fillSlot, i, true);
        }
        blackhole.consume(offeredMessages);
        blackhole.consume(droppedMessages);
    }

    private static GatewaySlot marketDataSlot() {
        final GatewaySlot slot = new GatewaySlot();
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        final MarketDataEventEncoder encoder = new MarketDataEventEncoder();
        encoder.wrapAndApplyHeader(slot.buffer, 0, header)
            .venueId(1)
            .instrumentId(1)
            .entryType(EntryType.BID)
            .updateAction(UpdateAction.NEW)
            .priceScaled(1)
            .sizeScaled(1)
            .priceLevel(1)
            .ingressTimestampNanos(1)
            .exchangeTimestampNanos(1)
            .fixSeqNum(1);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
        return slot;
    }

    private static GatewaySlot executionSlot() {
        final GatewaySlot slot = new GatewaySlot();
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        final ExecutionEventEncoder encoder = new ExecutionEventEncoder();
        encoder.wrapAndApplyHeader(slot.buffer, 0, header)
            .clOrdId(1)
            .venueId(1)
            .instrumentId(1)
            .execType(ExecType.FILL)
            .side(Side.BUY)
            .fillPriceScaled(1)
            .fillQtyScaled(1)
            .cumQtyScaled(1)
            .leavesQtyScaled(0)
            .rejectCode(0)
            .ingressTimestampNanos(1)
            .exchangeTimestampNanos(1)
            .fixSeqNum(1)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(VENUE_ORDER_ID, 0, VENUE_ORDER_ID.length)
            .putExecId(EXEC_ID, 0, EXEC_ID.length);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
        return slot;
    }

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }
}
