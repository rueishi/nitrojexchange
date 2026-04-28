package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for TASK-010 ExecutionReport normalization.
 *
 * <p>Responsibility: verify that {@link ExecutionHandler} maps FIX MsgType
 * {@code 8} into generated SBE {@link ExecutionEventDecoder} payloads with the
 * exact ExecType, finality, reject-code, ID, timestamp, and variable-length
 * fields required by the task card. Role in system: these tests lock down the
 * gateway execution ingress path before later cluster tasks apply order-state
 * transitions and portfolio updates.</p>
 *
 * <p>Relationships: tests use the real {@link GatewayDisruptor} and generated
 * SBE decoder classes, while a minimal {@link IdRegistry} provides deterministic
 * venue/session/symbol mappings. Lifecycle: each test owns an isolated disruptor
 * instance and copies consumed slot bytes before the production reset wrapper
 * clears the reusable slot.</p>
 */
final class ExecutionHandlerTest {
    private static final long SESSION_ID = 7001L;
    private static final int VENUE_ID = 1;
    private static final int INSTRUMENT_ID = 11;
    private static final char SOH = '\001';
    private static int currentLength;

    /** Verifies ExecType=0 maps to SBE NEW with core fields populated. */
    @Test
    void execTypeNew_correctSBEFields() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=0", "151=1", "31=0", "32=0"));

        assertThat(event.execType()).isEqualTo(ExecType.NEW);
        assertThat(event.clOrdId()).isEqualTo(1000L);
        assertThat(event.venueId()).isEqualTo(VENUE_ID);
        assertThat(event.instrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(event.side()).isEqualTo(Side.BUY);
        assertThat(event.isFinal()).isEqualTo(BooleanType.FALSE);
    }

    /** Verifies full fills are final when LeavesQty is zero. */
    @Test
    void execTypeFill_fullFill_isFinalTrue() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=F", "151=0", "31=65000", "32=0.1"));

        assertThat(event.execType()).isEqualTo(ExecType.FILL);
        assertThat(event.isFinal()).isEqualTo(BooleanType.TRUE);
    }

    /** Verifies fills with remaining quantity are not final. */
    @Test
    void execTypeFill_partialFill_isFinalFalse() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=F", "151=0.5", "31=65000", "32=0.1"));

        assertThat(event.execType()).isEqualTo(ExecType.FILL);
        assertThat(event.isFinal()).isEqualTo(BooleanType.FALSE);
    }

    /** Verifies rejected executions populate tag 103. */
    @Test
    void execTypeRejected_rejectCodePopulated() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=8", "103=7", "151=1"));

        assertThat(event.execType()).isEqualTo(ExecType.REJECTED);
        assertThat(event.rejectCode()).isEqualTo(7);
        assertThat(event.isFinal()).isEqualTo(BooleanType.TRUE);
    }

    /** Verifies rejected executions without tag 103 default to zero. */
    @Test
    void execTypeRejected_noRejectCode_rejectCodeZero() throws Exception {
        assertThat(publish(base("150=8", "151=1")).rejectCode()).isZero();
    }

    /** Verifies canceled executions are terminal. */
    @Test
    void execTypeCanceled_isFinalTrue() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=4", "151=1"));

        assertThat(event.execType()).isEqualTo(ExecType.CANCELED);
        assertThat(event.isFinal()).isEqualTo(BooleanType.TRUE);
    }

    /** Verifies replaced executions are terminal. */
    @Test
    void execTypeReplaced_isFinalTrue() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=5", "151=1"));

        assertThat(event.execType()).isEqualTo(ExecType.REPLACED);
        assertThat(event.isFinal()).isEqualTo(BooleanType.TRUE);
    }

    /** Verifies Coinbase ExecType=I maps to ORDER_STATUS rather than a fill or terminal transition. */
    @Test
    void execTypeOrderStatus_execTypeOrderStatusInSBE() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=I", "151=1"));

        assertThat(event.execType()).isEqualTo(ExecType.ORDER_STATUS);
        assertThat(event.isFinal()).isEqualTo(BooleanType.FALSE);
    }

    /** Verifies ClOrdID tag 11 is parsed as a long. */
    @Test
    void clOrdId_parsedAsLong_fromTag11() throws Exception {
        assertThat(publish(baseWithClOrdId("1234567890", "150=0", "151=1")).clOrdId()).isEqualTo(1_234_567_890L);
    }

    /** Verifies ingress time is stamped before symbol lookup. */
    @Test
    void ingressTimestampNanos_setBeforeDecoding() throws Exception {
        final TrackingRegistry registry = new TrackingRegistry();
        final Harness harness = Harness.started(1, registry);

        harness.handler.onMessageForTest(SESSION_ID, fix(base("150=0", "151=1")), 0, currentLength);

        final ExecutionEventDecoder event = harness.onlyEvent();
        assertThat(event.ingressTimestampNanos()).isPositive();
        assertThat(event.ingressTimestampNanos()).isLessThanOrEqualTo(registry.firstInstrumentLookupNanos);
        harness.close();
    }

    /** Verifies venueOrderId varString is copied from tag 37. */
    @Test
    void venueOrderId_varLengthField_populatedFromTag37() throws Exception {
        assertThat(venueOrderId(publish(base("37=venue-abc", "150=0", "151=1")))).isEqualTo("venue-abc");
    }

    /** Verifies execId varString is copied from tag 17. */
    @Test
    void execId_varLengthField_populatedFromTag17() throws Exception {
        assertThat(execId(publish(base("17=exec-xyz", "150=0", "151=1")))).isEqualTo("exec-xyz");
    }

    /** Verifies missing tag 103 remains zero. */
    @Test
    void missingTag103_rejectCodeZero() throws Exception {
        assertThat(publish(base("150=F", "151=0", "31=1", "32=1")).rejectCode()).isZero();
    }

    /** Verifies zero LeavesQty on fill is final. */
    @Test
    void execTypeFill_zeroLeavesQty_isFinalTrue() throws Exception {
        assertThat(publish(base("150=F", "151=0.00000000")).isFinal()).isEqualTo(BooleanType.TRUE);
    }

    /** Verifies large fill quantities scale without overflow for expected crypto sizes. */
    @Test
    void execTypeFill_largeFillQty_noOverflow() throws Exception {
        final ExecutionEventDecoder event = publish(base("150=F", "151=0", "31=65000", "32=123456789.12345678"));

        assertThat(event.fillQtyScaled()).isEqualTo(12_345_678_912_345_678L);
    }

    /** Verifies Long.MAX_VALUE ClOrdID parses correctly. */
    @Test
    void clOrdId_maxLongValue_parsedCorrectly() throws Exception {
        assertThat(publish(baseWithClOrdId(Long.toString(Long.MAX_VALUE), "150=0", "151=1")).clOrdId())
            .isEqualTo(Long.MAX_VALUE);
    }

    private static ExecutionEventDecoder publish(final String[] fields) throws Exception {
        final Harness harness = Harness.started(1, new TrackingRegistry());
        harness.handler.onMessageForTest(SESSION_ID, fix(fields), 0, currentLength);
        final ExecutionEventDecoder event = harness.onlyEvent();
        harness.close();
        return event;
    }

    private static String[] base(final String... overrides) {
        return baseWithClOrdId("1000", overrides);
    }

    private static String[] baseWithClOrdId(final String clOrdId, final String... overrides) {
        final List<String> fields = new ArrayList<>(List.of(
            "35=8",
            "34=21",
            "11=" + clOrdId,
            "17=exec-1",
            "37=venue-1",
            "54=1",
            "55=BTC-USD",
            "14=0.1",
            "60=20260424-20:30:15.123456789"));
        fields.addAll(List.of(overrides));
        return fields.toArray(String[]::new);
    }

    private static UnsafeBuffer fix(final String... fields) {
        final String joined = String.join(String.valueOf(SOH), fields) + SOH;
        final byte[] bytes = joined.getBytes(StandardCharsets.US_ASCII);
        currentLength = bytes.length;
        return new UnsafeBuffer(bytes);
    }

    private static String venueOrderId(final ExecutionEventDecoder event) {
        final byte[] bytes = new byte[event.venueOrderIdLength()];
        event.getVenueOrderId(bytes, 0, bytes.length);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static String execId(final ExecutionEventDecoder event) {
        event.skipVenueOrderId();
        final byte[] bytes = new byte[event.execIdLength()];
        event.getExecId(bytes, 0, bytes.length);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness implements AutoCloseable {
        private final GatewayDisruptor disruptor;
        private final ExecutionHandler handler;
        private final CountDownLatch latch;
        private final List<UnsafeBuffer> payloads = new ArrayList<>();

        private Harness(final int expectedEvents, final TrackingRegistry registry) {
            latch = new CountDownLatch(expectedEvents);
            disruptor = new GatewayDisruptor(8, 512, counters(), (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                payloads.add(new UnsafeBuffer(copy));
                latch.countDown();
            });
            handler = new ExecutionHandler(registry, disruptor);
            disruptor.start();
        }

        private static Harness started(final int expectedEvents, final TrackingRegistry registry) {
            return new Harness(expectedEvents, registry);
        }

        private ExecutionEventDecoder onlyEvent() throws Exception {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(payloads).hasSize(1);
            final MessageHeaderDecoder header = new MessageHeaderDecoder();
            final ExecutionEventDecoder event = new ExecutionEventDecoder();
            event.wrapAndApplyHeader(payloads.getFirst(), 0, header);
            assertThat(event.exchangeTimestampNanos()).isEqualTo(
                LocalDateTime.of(2026, 4, 24, 20, 30, 15)
                    .toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L + 123_456_789L);
            assertThat(event.fixSeqNum()).isEqualTo(21);
            return event;
        }

        @Override
        public void close() {
            disruptor.close();
        }
    }

    private static final class TrackingRegistry implements IdRegistry {
        private long firstInstrumentLookupNanos = Long.MAX_VALUE;

        @Override
        public int venueId(final long sessionId) {
            assertThat(sessionId).isEqualTo(SESSION_ID);
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            firstInstrumentLookupNanos = Math.min(firstInstrumentLookupNanos, System.nanoTime());
            return "BTC-USD".contentEquals(symbol) ? INSTRUMENT_ID : 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return instrumentId == INSTRUMENT_ID ? "BTC-USD" : null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return venueId == VENUE_ID ? "Coinbase" : null;
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
            // The fake registry resolves venue directly from the session id.
        }
    }
}
