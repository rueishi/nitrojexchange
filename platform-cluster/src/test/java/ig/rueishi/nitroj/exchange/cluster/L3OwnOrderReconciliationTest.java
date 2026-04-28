package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-style coverage for L3 own-order reconciliation hooks.
 *
 * <p>Responsibility: proves venue-order-ID matching, exact own-size exclusion,
 * queue-position estimation, and failure handling around {@link VenueL3Book},
 * {@link OwnOrderOverlay}, and {@link OrderManagerImpl}. Role in system:
 * protects the V11 arbitrage self-awareness path while keeping gross L3/L2
 * market books immutable from private ownership decisions. Relationships: the
 * tests use real generated SBE codecs, a real off-heap {@link L2OrderBook}, and
 * real order-manager execution-report transitions. Lifecycle: each test owns a
 * confined arena and closes it after execution. Design intent: queue-position
 * data is advisory only; order truth remains with execution reports.</p>
 */
final class L3OwnOrderReconciliationTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int OTHER_VENUE = Ids.VENUE_COINBASE_SANDBOX;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final int OTHER_INSTRUMENT = Ids.INSTRUMENT_ETH_USD;
    private static final long PRICE = 100_000L * Ids.SCALE;
    private static final long QTY = 10L * Ids.SCALE;
    private final Arena arena = Arena.ofConfined();
    private final VenueL3Book l3Book = new VenueL3Book(VENUE, INSTRUMENT);
    private final L2OrderBook l2Book = new L2OrderBook(VENUE, INSTRUMENT, arena);

    @AfterEach
    void closeArena() {
        arena.close();
    }

    @Test
    void ownOrderAppearsInL3_exactOwnSizeExcludedAndGrossBooksRemainUnchanged() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "own-1", VENUE, INSTRUMENT, EntryType.ASK, PRICE, 4L * Ids.SCALE, true);

        assertThat(l3Book.apply(l3("own-1", Side.SELL, UpdateAction.NEW, PRICE, 4L * Ids.SCALE), l2Book, 1L)).isTrue();

        assertThat(overlay.isOwnVenueOrder("own-1", VENUE, INSTRUMENT, EntryType.ASK, PRICE)).isTrue();
        assertThat(l3Book.orderSize("own-1")).isEqualTo(4L * Ids.SCALE);
        assertThat(l3Book.externalOrderSize("own-1", overlay)).isZero();
        assertThat(l3Book.levelSize(Side.SELL, PRICE)).isEqualTo(4L * Ids.SCALE);
        assertThat(l2Book.askSizeAt(0)).isEqualTo(4L * Ids.SCALE);
    }

    @Test
    void mixedOwnAndExternalAtOneLevel_excludesOnlyExactOwnOrder() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "own-1", VENUE, INSTRUMENT, EntryType.ASK, PRICE, 3L * Ids.SCALE, true);

        l3Book.apply(l3("external-a", Side.SELL, UpdateAction.NEW, PRICE, 5L * Ids.SCALE), l2Book, 1L);
        l3Book.apply(l3("own-1", Side.SELL, UpdateAction.NEW, PRICE, 3L * Ids.SCALE), l2Book, 2L);
        l3Book.apply(l3("external-b", Side.SELL, UpdateAction.NEW, PRICE, 2L * Ids.SCALE), l2Book, 3L);

        assertThat(l3Book.externalOrderSize("external-a", overlay)).isEqualTo(5L * Ids.SCALE);
        assertThat(l3Book.externalOrderSize("own-1", overlay)).isZero();
        assertThat(l3Book.levelSize(Side.SELL, PRICE)).isEqualTo(QTY);
        assertThat(l2Book.askSizeAt(0)).isEqualTo(QTY);
    }

    @Test
    void ownOrderSizeChangePriceMoveDeleteAndPartialFillFollowOrderManagerState() {
        final OrderManagerImpl manager = new OrderManagerImpl();
        manager.createPendingOrder(11L, VENUE, INSTRUMENT, Side.SELL.value(), OrdType.LIMIT.value(),
            TimeInForce.GTC.value(), PRICE, QTY, Ids.STRATEGY_ARB);
        manager.onExecution(exec(11L, ExecType.NEW, Side.SELL, 0L, 0L, QTY, false, "ack-1", "venue-11"));
        l3Book.apply(l3("venue-11", Side.SELL, UpdateAction.NEW, PRICE, QTY), l2Book, 1L);

        assertThat(l3Book.externalOrderSize("venue-11", manager.ownOrderOverlaySnapshot())).isZero();

        manager.onExecution(exec(11L, ExecType.PARTIAL_FILL, Side.SELL, 2L * Ids.SCALE, 2L * Ids.SCALE,
            8L * Ids.SCALE, false, "fill-1", "venue-11"));
        l3Book.apply(l3("venue-11", Side.SELL, UpdateAction.CHANGE, PRICE, 8L * Ids.SCALE), l2Book, 2L);
        assertThat(l3Book.externalOrderSize("venue-11", manager.ownOrderOverlaySnapshot())).isZero();

        final OwnOrderOverlay movedOverlay = new OwnOrderOverlay();
        movedOverlay.upsert(11L, "venue-11", VENUE, INSTRUMENT, EntryType.ASK, PRICE + Ids.SCALE, 8L * Ids.SCALE, true);
        l3Book.apply(l3("venue-11", Side.SELL, UpdateAction.CHANGE, PRICE + Ids.SCALE, 8L * Ids.SCALE), l2Book, 3L);
        assertThat(l3Book.externalOrderSize("venue-11", movedOverlay)).isZero();
        assertThat(l3Book.levelSize(Side.SELL, PRICE)).isZero();

        l3Book.apply(l3("venue-11", Side.SELL, UpdateAction.DELETE, 0L, 0L), l2Book, 4L);
        assertThat(l3Book.containsOrder("venue-11")).isFalse();
        assertThat(l3Book.externalOrderSize("venue-11", movedOverlay)).isZero();
    }

    @Test
    void queuePosition_knownWhenOrderingAvailable_unknownWhenUnavailableOrMissing() {
        l3Book.apply(l3("ahead-1", Side.BUY, UpdateAction.NEW, PRICE, 2L * Ids.SCALE), l2Book, 1L);
        l3Book.apply(l3("other-price", Side.BUY, UpdateAction.NEW, PRICE - Ids.SCALE, QTY), l2Book, 2L);
        l3Book.apply(l3("own-1", Side.BUY, UpdateAction.NEW, PRICE, 3L * Ids.SCALE), l2Book, 3L);

        final QueuePositionEstimate estimate = l3Book.queuePosition("own-1", true);

        assertThat(estimate.known()).isTrue();
        assertThat(estimate.sizeAheadScaled()).isEqualTo(2L * Ids.SCALE);
        assertThat(estimate.ownSizeScaled()).isEqualTo(3L * Ids.SCALE);
        assertThat(l3Book.queuePosition("own-1", false)).isEqualTo(QueuePositionEstimate.unknown());
        assertThat(l3Book.queuePosition("missing", true)).isEqualTo(QueuePositionEstimate.unknown());
    }

    @Test
    void negativeMismatchesAndUnknownIdsDoNotMarkOrderAsOwn() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "own-1", VENUE, INSTRUMENT, EntryType.ASK, PRICE, QTY, true);
        l3Book.apply(l3("own-1", Side.SELL, UpdateAction.NEW, PRICE, QTY), l2Book, 1L);

        assertThat(overlay.isOwnVenueOrder("own-1", OTHER_VENUE, INSTRUMENT, EntryType.ASK, PRICE)).isFalse();
        assertThat(overlay.isOwnVenueOrder("own-1", VENUE, OTHER_INSTRUMENT, EntryType.ASK, PRICE)).isFalse();
        assertThat(overlay.isOwnVenueOrder("own-1", VENUE, INSTRUMENT, EntryType.BID, PRICE)).isFalse();
        assertThat(overlay.isOwnVenueOrder("own-1", VENUE, INSTRUMENT, EntryType.ASK, PRICE + Ids.SCALE)).isFalse();
        assertThat(overlay.isOwnVenueOrder("unknown", VENUE, INSTRUMENT, EntryType.ASK, PRICE)).isFalse();
        overlay.remove(1L);
        assertThat(l3Book.externalOrderSize("own-1", overlay)).isEqualTo(QTY);
    }

    @Test
    void edgeCases_duplicateAddDeleteBeforeExecutionReportAndIdReuseRemainAdvisory() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        assertThat(l3Book.apply(l3("future-own", Side.BUY, UpdateAction.DELETE, 0L, 0L), l2Book, 1L)).isFalse();

        l3Book.apply(l3("reuse", Side.BUY, UpdateAction.NEW, PRICE, 4L * Ids.SCALE), l2Book, 2L);
        l3Book.apply(l3("reuse", Side.BUY, UpdateAction.NEW, PRICE, 6L * Ids.SCALE), l2Book, 3L);
        assertThat(l3Book.orderSize("reuse")).isEqualTo(6L * Ids.SCALE);

        overlay.upsert(1L, "reuse", VENUE, INSTRUMENT, EntryType.BID, PRICE, 6L * Ids.SCALE, true);
        assertThat(l3Book.externalOrderSize("reuse", overlay)).isZero();
        overlay.remove(1L);
        assertThat(l3Book.externalOrderSize("reuse", overlay)).isEqualTo(6L * Ids.SCALE);

        overlay.upsert(2L, "reuse", VENUE, INSTRUMENT, EntryType.BID, PRICE, 5L * Ids.SCALE, true);
        l3Book.apply(l3("reuse", Side.BUY, UpdateAction.CHANGE, PRICE, 0L), l2Book, 4L);
        assertThat(l3Book.containsOrder("reuse")).isFalse();
        assertThat(l3Book.levelSize(Side.BUY, PRICE)).isZero();
        assertThat(l2Book.getBestBid()).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void exceptions_invalidInputsAndIncompleteOrderIdentityAreRejected() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();

        assertThatThrownBy(() -> l3Book.containsOrder(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venueOrderId");
        assertThatThrownBy(() -> l3Book.externalOrderSize(" ", overlay))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venueOrderId");
        assertThatThrownBy(() -> QueuePositionEstimate.known(-1L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
        assertThatThrownBy(() -> overlay.upsert(0L, "bad", VENUE, INSTRUMENT, EntryType.ASK, PRICE, QTY, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("identity");
    }

    @Test
    void failureCases_outOfOrderMissingDeleteGapAndReconnectDoNotCorruptGrossBooks() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "own-1", VENUE, INSTRUMENT, EntryType.ASK, PRICE, 4L * Ids.SCALE, true);

        assertThat(l3Book.apply(l3("missing", Side.SELL, UpdateAction.CHANGE, PRICE, Ids.SCALE), l2Book, 1L)).isFalse();
        assertThat(l3Book.apply(l3("missing", Side.SELL, UpdateAction.DELETE, 0L, 0L), l2Book, 2L)).isFalse();
        assertThat(l3Book.activeOrderCount()).isZero();
        assertThat(l2Book.getBestAsk()).isEqualTo(Ids.INVALID_PRICE);

        l3Book.apply(l3("own-1", Side.SELL, UpdateAction.NEW, PRICE, 4L * Ids.SCALE), l2Book, 3L);
        l3Book.apply(l3("external", Side.SELL, UpdateAction.NEW, PRICE, 6L * Ids.SCALE), l2Book, 10L);
        assertThat(l3Book.levelSize(Side.SELL, PRICE)).isEqualTo(QTY);
        assertThat(l3Book.externalOrderSize("own-1", overlay)).isZero();
        assertThat(l3Book.externalOrderSize("external", overlay)).isEqualTo(6L * Ids.SCALE);

        overlay.clear();
        assertThat(l3Book.externalOrderSize("own-1", overlay)).isEqualTo(4L * Ids.SCALE);
        assertThat(l3Book.levelSize(Side.SELL, PRICE)).isEqualTo(QTY);
        assertThat(l2Book.askSizeAt(0)).isEqualTo(QTY);
    }

    private static MarketByOrderEventDecoder l3(
        final String orderId,
        final Side side,
        final UpdateAction action,
        final long price,
        final long size) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] orderIdBytes = orderId.getBytes(StandardCharsets.US_ASCII);
        new MarketByOrderEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(VENUE)
            .instrumentId(INSTRUMENT)
            .side(side)
            .updateAction(action)
            .priceScaled(price)
            .sizeScaled(size)
            .ingressTimestampNanos(11L)
            .exchangeTimestampNanos(22L)
            .fixSeqNum(7)
            .putVenueOrderId(orderIdBytes, 0, orderIdBytes.length);
        final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketByOrderEventEncoder.BLOCK_LENGTH, MarketByOrderEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final Side side,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId,
        final String venueOrderId) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final byte[] venueOrderIdBytes = venueOrderId.getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE)
            .instrumentId(INSTRUMENT)
            .execType(execType)
            .side(side)
            .fillPriceScaled(PRICE)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(cumQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1_000L)
            .exchangeTimestampNanos(2_000L)
            .fixSeqNum(10)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderIdBytes, 0, venueOrderIdBytes.length)
            .putExecId(execIdBytes, 0, execIdBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }
}
