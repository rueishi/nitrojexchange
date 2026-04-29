package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestDecoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestEncoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.RecoveryCompleteEventDecoder;
import ig.rueishi.nitroj.exchange.messages.RecoveryCompleteEventEncoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-013 order-command routing.
 *
 * <p>Responsibility: verify that decoded cluster order commands become the FIX
 * messages and side effects required by the TASK-013 acceptance criteria. Role
 * in system: these tests lock down the gateway egress adapter before later
 * Artio-library-loop and cluster order-manager tasks wire it into runtime.
 * Relationships: the tests use real generated SBE encoders/decoders, real
 * generated Artio outbound encoders, a deterministic {@link IdRegistry}, and a
 * fake Artio sender. Lifecycle: each test builds an isolated router or fragment
 * handler and captures emitted FIX strings in memory. Design intent: test the
 * protocol mapping and back-pressure contract without needing a live Aeron or
 * Artio engine.</p>
 */
final class OrderCommandHandlerTest {
    private static final int VENUE_ID = 1;
    private static final int INSTRUMENT_ID = 11;
    private static final byte SOH = 1;

    /** Verifies NewOrderCommand becomes FIX MsgType D with command values mapped into FIX tags. */
    @Test
    void newOrderCommand_producesCorrectFIXNewOrderSingle() {
        final Harness harness = new Harness();

        harness.router.routeNewOrder(newOrderDecoder(1001L, OrdType.LIMIT));

        assertThat(harness.sender.sent).hasSize(1);
        assertThat(harness.sender.sent.getFirst())
            .contains(tag("35=D"))
            .contains(tag("11=1001"))
            .contains(tag("55=BTC-USD"))
            .contains(tag("54=1"))
            .contains(tag("40=2"))
            .contains(tag("44=65000"));
    }

    /** Verifies every required NewOrderSingle tag from Spec section 9.5 is present. */
    @Test
    void newOrderCommand_allRequiredTagsPresent() {
        final Harness harness = new Harness();

        harness.router.routeNewOrder(newOrderDecoder(1002L, OrdType.LIMIT));

        final String fix = harness.sender.sent.getFirst();
        assertThat(fix)
            .contains(tag("35=D"))
            .contains(tag("11=1002"))
            .contains(tag("21=1"))
            .contains(tag("38=0.5"))
            .contains(tag("40=2"))
            .contains(tag("44=65000"))
            .contains(tag("54=1"))
            .contains(tag("55=BTC-USD"))
            .contains(tag("59=1"))
            .contains(tag("60=20260424-20:30:15.123"))
            .contains(tag("1=ACC1"));
    }

    /** Verifies V13 parent IDs remain internal attribution data and are not emitted to venue FIX. */
    @Test
    void newOrderCommand_parentOrderIdPreservedInternally_butNotSentToFIX() {
        final Harness harness = new Harness();
        final EncodedMessage encoded = newOrderMessageWithParent(1009L, 987_654_321_234L);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(encoded.buffer, 0, header);

        assertThat(decoder.parentOrderId()).isEqualTo(987_654_321_234L);

        harness.router.routeNewOrder(decoder);

        assertThat(harness.sender.sent).hasSize(1);
        assertThat(harness.sender.sent.getFirst())
            .contains(tag("35=D"))
            .contains(tag("11=1009"))
            .doesNotContain("987654321234")
            .doesNotContain(tag("ParentOrderId=987654321234"));
    }

    /** Verifies CancelOrderCommand becomes FIX MsgType F. */
    @Test
    void cancelCommand_producesCorrectFIXCancelRequest() {
        final Harness harness = new Harness();

        harness.router.routeCancel(cancelDecoder(2001L, 1001L, "venue-1"));

        assertThat(harness.sender.sent).hasSize(1);
        assertThat(harness.sender.sent.getFirst())
            .contains(tag("35=F"))
            .contains(tag("11=2001"))
            .contains(tag("41=1001"))
            .contains(tag("37=venue-1"));
    }

    /** Verifies every required OrderCancelRequest tag from Spec section 9.6 is present. */
    @Test
    void cancelCommand_allRequiredTagsPresent() {
        final Harness harness = new Harness();

        harness.router.routeCancel(cancelDecoder(2002L, 1002L, "venue-2"));

        final String fix = harness.sender.sent.getFirst();
        assertThat(fix)
            .contains(tag("35=F"))
            .contains(tag("11=2002"))
            .contains(tag("41=1002"))
            .contains(tag("37=venue-2"))
            .contains(tag("54=2"))
            .contains(tag("55=BTC-USD"))
            .contains(tag("38=0.5"))
            .contains(tag("60=20260424-20:30:15.123"));
    }

    /** Verifies Coinbase routing does not emit native FIX MsgType G for replace commands. */
    @Test
    void replaceCommand_coinbase_notRouted_clusterOwnsSequencing() {
        final Harness harness = new Harness();

        harness.router.routeReplace(replaceDecoder(1001L, 1003L, "venue-1"));

        assertThat(harness.sender.sent).isEmpty();
    }

    /** Verifies venues that support native replace emit FIX MsgType G with reusable ID buffers. */
    @Test
    void replaceCommand_nativeVenue_routesCancelReplaceRequest() {
        final Harness harness = new Harness(true);

        harness.router.routeReplace(replaceDecoder(1001L, 1003L, "venue-1"));

        assertThat(harness.sender.sent).hasSize(1);
        assertThat(harness.sender.sent.getFirst())
            .contains(tag("35=G"))
            .contains(tag("11=1003"))
            .contains(tag("41=1001"))
            .contains(tag("37=venue-1"))
            .contains(tag("55=BTC-USD"))
            .contains(tag("60=20260424-20:30:15.123"));
    }

    /** Verifies OrderStatusQueryCommand becomes FIX MsgType H. */
    @Test
    void orderStatusQueryCommand_sends35H() {
        final Harness harness = new Harness();

        harness.router.routeStatusQuery(statusDecoder(1001L, "venue-1"));

        assertThat(harness.sender.sent).hasSize(1);
        assertThat(harness.sender.sent.getFirst())
            .contains(tag("35=H"))
            .contains(tag("11=1001"))
            .contains(tag("37=venue-1"));
    }

    /** Verifies UTC timestamp formatting across a date boundary without String formatting allocation. */
    @Test
    void newOrderCommand_timestampFormatting_handlesUtcDayBoundary() {
        final Harness harness = new Harness(
            Clock.fixed(Instant.parse("2026-12-31T23:59:59.999Z"), ZoneOffset.UTC),
            new TestRegistry(),
            false);

        harness.router.routeNewOrder(newOrderDecoder(1007L, OrdType.LIMIT));

        assertThat(harness.sender.sent.getFirst())
            .contains(tag("60=20261231-23:59:59.999"));
    }

    /** Verifies symbol lookup failure rejects unknown instrument IDs before FIX send. */
    @Test
    void newOrderCommand_unknownInstrument_failsBeforeSend() {
        final Harness harness = new Harness(
            Clock.fixed(Instant.parse("2026-04-24T20:30:15.123Z"), ZoneOffset.UTC),
            new MissingSymbolRegistry(),
            false);

        assertThatThrownBy(() -> harness.router.routeNewOrder(newOrderDecoder(1008L, OrdType.LIMIT)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown instrumentId");
        assertThat(harness.sender.sent).isEmpty();
    }

    /** Verifies BalanceQueryRequest is dispatched to the REST poller hook rather than FIX. */
    @Test
    void balanceQueryRequest_enqueuedToRestPoller() {
        final RecordingRouter router = new RecordingRouter();
        final List<Integer> requests = new ArrayList<>();
        final OrderCommandHandler handler = new OrderCommandHandler(
            router,
            command -> requests.add(command.venueId()),
            recovery -> { },
            System.getLogger("test"));
        final EncodedMessage encoded = balanceMessage(VENUE_ID);

        handler.onFragment(encoded.buffer, 0, encoded.length, null);

        assertThat(requests).containsExactly(VENUE_ID);
        assertThat(router.newOrders).isZero();
    }

    /** Verifies RecoveryCompleteEvent is observed locally and never sent as FIX. */
    @Test
    void recoveryCompleteEvent_loggedOnly_noFIXAction() {
        final RecordingRouter router = new RecordingRouter();
        final List<Integer> recoveries = new ArrayList<>();
        final OrderCommandHandler handler = new OrderCommandHandler(
            router,
            balance -> { },
            recovery -> recoveries.add(recovery.venueId()),
            System.getLogger("test"));
        final EncodedMessage encoded = recoveryMessage(VENUE_ID);

        handler.onFragment(encoded.buffer, 0, encoded.length, null);

        assertThat(recoveries).containsExactly(VENUE_ID);
        assertThat(router.newOrders).isZero();
    }

    /** Verifies Artio back-pressure is retried up to three trySend attempts. */
    @Test
    void artioBackPressure_retriesTrySendUpToThreeTimes() {
        final Harness harness = new Harness(-1L, -1L, 24L);

        harness.router.routeNewOrder(newOrderDecoder(1004L, OrdType.LIMIT));

        assertThat(harness.sender.attempts).isEqualTo(3);
        assertThat(harness.sender.sent).hasSize(1);
        assertThat(harness.backPressureCount).hasValue(0);
        assertThat(harness.rejects).isEmpty();
    }

    /** Verifies exhausted Artio back-pressure increments metrics and publishes a reject event. */
    @Test
    void artioBackPressure_allTrySendRetriesFail_rejectEventPublished() {
        final Harness harness = new Harness(-1L, -1L, -1L);

        harness.router.routeNewOrder(newOrderDecoder(1005L, OrdType.LIMIT));

        assertThat(harness.sender.attempts).isEqualTo(3);
        assertThat(harness.sender.sent).isEmpty();
        assertThat(harness.backPressureCount).hasValue(1);
        assertThat(harness.rejects).containsExactly("1005:1:11");
    }

    /** Verifies market orders omit FIX tag 44 because price is limit-only. */
    @Test
    void newOrder_marketOrder_tag44Omitted() {
        final Harness harness = new Harness();

        harness.router.routeNewOrder(newOrderDecoder(1006L, OrdType.MARKET));

        assertThat(harness.sender.sent.getFirst())
            .contains(tag("35=D"))
            .contains(tag("40=1"))
            .doesNotContain(tag("44="));
    }

    /** Verifies a single fragment containing two fixed-length new orders routes both legs. */
    @Test
    void arbLegBatch_twoNewOrderCommandsOneFragment_bothFIXSent() {
        final Harness harness = new Harness();
        final OrderCommandHandler handler = new OrderCommandHandler(harness.router);
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        int cursor = putNewOrder(buffer, 0, 1010L, Side.BUY, OrdType.LIMIT);
        cursor = putNewOrder(buffer, cursor, 1011L, Side.SELL, OrdType.LIMIT);

        handler.onFragment(buffer, 0, cursor, null);

        assertThat(harness.sender.sent).hasSize(2);
        assertThat(harness.sender.sent.get(0)).contains(tag("11=1010")).contains(tag("54=1"));
        assertThat(harness.sender.sent.get(1)).contains(tag("11=1011")).contains(tag("54=2"));
    }

    private static NewOrderCommandDecoder newOrderDecoder(final long clOrdId, final OrdType ordType) {
        final EncodedMessage encoded = newOrderMessage(clOrdId, Side.BUY, ordType);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(encoded.buffer, 0, header);
        return decoder;
    }

    private static CancelOrderCommandDecoder cancelDecoder(
        final long cancelClOrdId,
        final long origClOrdId,
        final String venueOrderId) {
        final EncodedMessage encoded = cancelMessage(cancelClOrdId, origClOrdId, venueOrderId);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final CancelOrderCommandDecoder decoder = new CancelOrderCommandDecoder();
        decoder.wrapAndApplyHeader(encoded.buffer, 0, header);
        return decoder;
    }

    private static ReplaceOrderCommandDecoder replaceDecoder(
        final long origClOrdId,
        final long newClOrdId,
        final String venueOrderId) {
        final EncodedMessage encoded = replaceMessage(origClOrdId, newClOrdId, venueOrderId);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final ReplaceOrderCommandDecoder decoder = new ReplaceOrderCommandDecoder();
        decoder.wrapAndApplyHeader(encoded.buffer, 0, header);
        return decoder;
    }

    private static OrderStatusQueryCommandDecoder statusDecoder(final long clOrdId, final String venueOrderId) {
        final EncodedMessage encoded = statusMessage(clOrdId, venueOrderId);
        final ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder header =
            new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder();
        final OrderStatusQueryCommandDecoder decoder = new OrderStatusQueryCommandDecoder();
        decoder.wrapAndApplyHeader(encoded.buffer, 0, header);
        return decoder;
    }

    static EncodedMessage newOrderMessage(final long clOrdId, final Side side, final OrdType ordType) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final int length = putNewOrder(buffer, 0, clOrdId, side, ordType);
        return new EncodedMessage(buffer, length);
    }

    static EncodedMessage newOrderMessageWithParent(final long clOrdId, final long parentOrderId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final NewOrderCommandEncoder encoder = new NewOrderCommandEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.BUY)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.GTC)
            .priceScaled(6_500_000_000_000L)
            .qtyScaled(50_000_000L)
            .strategyId((short)7)
            .parentOrderId(parentOrderId);
        return new EncodedMessage(buffer, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
    }

    static int putNewOrder(
        final UnsafeBuffer buffer,
        final int offset,
        final long clOrdId,
        final Side side,
        final OrdType ordType) {
        final NewOrderCommandEncoder encoder = new NewOrderCommandEncoder();
        encoder.wrapAndApplyHeader(buffer, offset, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(side)
            .ordType(ordType)
            .timeInForce(TimeInForce.GTC)
            .priceScaled(6_500_000_000_000L)
            .qtyScaled(50_000_000L)
            .strategyId((short)7);
        return offset + MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    static EncodedMessage cancelMessage(final long cancelClOrdId, final long origClOrdId, final String venueOrderId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final CancelOrderCommandEncoder encoder = new CancelOrderCommandEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .cancelClOrdId(cancelClOrdId)
            .origClOrdId(origClOrdId)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.SELL)
            .originalQtyScaled(50_000_000L)
            .putVenueOrderId(ascii(venueOrderId), 0, venueOrderId.length());
        return new EncodedMessage(buffer, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
    }

    static EncodedMessage replaceMessage(final long origClOrdId, final long newClOrdId, final String venueOrderId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final ReplaceOrderCommandEncoder encoder = new ReplaceOrderCommandEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .origClOrdId(origClOrdId)
            .newClOrdId(newClOrdId)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.BUY)
            .newPriceScaled(6_600_000_000_000L)
            .newQtyScaled(50_000_000L)
            .origQtyScaled(50_000_000L)
            .putVenueOrderId(ascii(venueOrderId), 0, venueOrderId.length());
        return new EncodedMessage(buffer, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
    }

    static EncodedMessage statusMessage(final long clOrdId, final String venueOrderId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final OrderStatusQueryCommandEncoder encoder = new OrderStatusQueryCommandEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .side(Side.BUY)
            .putVenueOrderId(ascii(venueOrderId), 0, venueOrderId.length());
        return new EncodedMessage(buffer, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
    }

    static EncodedMessage balanceMessage(final int venueId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        final BalanceQueryRequestEncoder encoder = new BalanceQueryRequestEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(INSTRUMENT_ID)
            .requestId(77L);
        return new EncodedMessage(buffer, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
    }

    static EncodedMessage recoveryMessage(final int venueId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        final RecoveryCompleteEventEncoder encoder = new RecoveryCompleteEventEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .recoveryCompleteClusterTime(123L);
        return new EncodedMessage(buffer, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
    }

    static byte[] ascii(final String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    static String tag(final String value) {
        return (char)SOH + value + (char)SOH;
    }

    static final class EncodedMessage {
        final UnsafeBuffer buffer;
        final int length;

        EncodedMessage(final UnsafeBuffer buffer, final int length) {
            this.buffer = buffer;
            this.length = length;
        }
    }

    private static final class Harness {
        final CapturingSender sender;
        final AtomicInteger backPressureCount = new AtomicInteger();
        final List<String> rejects = new ArrayList<>();
        final ExecutionRouterImpl router;

        Harness(final long... results) {
            this(Clock.fixed(Instant.parse("2026-04-24T20:30:15.123Z"), ZoneOffset.UTC), new TestRegistry(), false, results);
        }

        Harness(final boolean nativeReplaceSupported) {
            this(
                Clock.fixed(Instant.parse("2026-04-24T20:30:15.123Z"), ZoneOffset.UTC),
                new TestRegistry(),
                nativeReplaceSupported);
        }

        Harness(final Clock clock, final IdRegistry idRegistry, final boolean nativeReplaceSupported) {
            this(clock, idRegistry, nativeReplaceSupported, 42L);
        }

        Harness(
            final Clock clock,
            final IdRegistry idRegistry,
            final boolean nativeReplaceSupported,
            final long... results) {
            sender = new CapturingSender(results.length == 0 ? new long[] {42L} : results);
            router = new ExecutionRouterImpl(
                sender,
                idRegistry,
                "ACC1",
                backPressureCount::incrementAndGet,
                (clOrdId, venueId, instrumentId) -> rejects.add(clOrdId + ":" + venueId + ":" + instrumentId),
                clock,
                nativeReplaceSupported);
        }
    }

    private static final class CapturingSender implements ExecutionRouterImpl.FixSender {
        private final long[] results;
        private final List<String> sent = new ArrayList<>();
        private int attempts;

        private CapturingSender(final long[] results) {
            this.results = results;
        }

        @Override
        public long trySend(final Encoder encoder) {
            final long result = results[Math.min(attempts, results.length - 1)];
            attempts++;
            if (result >= 0) {
                encoder.header()
                    .senderCompID("NITRO")
                    .targetCompID("COINBASE")
                    .msgSeqNum(attempts)
                    .sendingTime(ascii("20260424-20:30:15.123"));
                final MutableAsciiBuffer buffer = new MutableAsciiBuffer(new byte[1024]);
                final long encoded = encoder.encode(buffer, 0);
                sent.add(buffer.getAscii(Encoder.offset(encoded), Encoder.length(encoded)));
            }
            return result;
        }
    }

    private static final class TestRegistry implements IdRegistry {
        private final Map<Integer, String> symbols = new HashMap<>(Map.of(INSTRUMENT_ID, "BTC-USD"));

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
            return symbols.get(instrumentId);
        }

        @Override
        public String venueNameOf(final int venueId) {
            return "coinbase";
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
        }
    }

    private static final class MissingSymbolRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return "coinbase";
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
        }
    }

    private static final class RecordingRouter implements ExecutionRouter {
        int newOrders;

        @Override
        public void routeNewOrder(final NewOrderCommandDecoder command) {
            newOrders++;
        }

        @Override
        public void routeCancel(final CancelOrderCommandDecoder command) {
        }

        @Override
        public void routeReplace(final ReplaceOrderCommandDecoder command) {
        }

        @Override
        public void routeStatusQuery(final OrderStatusQueryCommandDecoder command) {
        }
    }
}
