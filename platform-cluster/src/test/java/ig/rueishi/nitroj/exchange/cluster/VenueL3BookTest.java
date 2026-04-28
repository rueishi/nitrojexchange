package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for deriving L2 levels from active L3 order state.
 *
 * <p>Responsibility: verifies add, change, delete, duplicate update, missing
 * delete, zero-size update, and book-mismatch behavior for {@link VenueL3Book}.
 * Role in system: protects the cluster-side bridge between normalized
 * MarketByOrderEvent messages and existing per-venue {@link L2OrderBook}
 * consumers. Relationships: uses real generated SBE codecs and a real off-heap
 * L2 book. Lifecycle: each test owns a confined arena that is closed after the
 * test. Design intent: keep L3 correctness focused on active order state and
 * derived aggregate levels without adding queue-position complexity.
 */
final class VenueL3BookTest {
    private static final int VENUE_ID = Ids.VENUE_COINBASE;
    private static final int INSTRUMENT_ID = Ids.INSTRUMENT_BTC_USD;
    private final Arena arena = Arena.ofConfined();
    private final VenueL3Book l3Book = new VenueL3Book(VENUE_ID, INSTRUMENT_ID);
    private final L2OrderBook l2Book = new L2OrderBook(VENUE_ID, INSTRUMENT_ID, arena);

    @AfterEach
    void closeArena() {
        arena.close();
    }

    @Test
    void add_twoOrdersSameBidPrice_aggregatesDerivedL2() {
        assertThat(l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L)).isTrue();
        assertThat(l3Book.apply(event("B", Side.BUY, UpdateAction.NEW, 100L, 25L), l2Book, 2L)).isTrue();

        assertThat(l3Book.activeOrderCount()).isEqualTo(2);
        assertThat(l3Book.levelSize(Side.BUY, 100L)).isEqualTo(35L);
        assertThat(l2Book.getBestBid()).isEqualTo(100L);
        assertThat(l2Book.bidSizeAt(0)).isEqualTo(35L);
    }

    @Test
    void change_existingOrder_replacesPreviousContribution() {
        l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L);

        assertThat(l3Book.apply(event("A", Side.BUY, UpdateAction.CHANGE, 100L, 4L), l2Book, 2L)).isTrue();

        assertThat(l3Book.levelSize(Side.BUY, 100L)).isEqualTo(4L);
        assertThat(l2Book.bidSizeAt(0)).isEqualTo(4L);
    }

    @Test
    void change_existingOrder_preservesQueuePosition() {
        l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L);
        l3Book.apply(event("B", Side.BUY, UpdateAction.NEW, 100L, 5L), l2Book, 2L);

        assertThat(l3Book.apply(event("A", Side.BUY, UpdateAction.CHANGE, 100L, 4L), l2Book, 3L)).isTrue();

        final QueuePositionEstimate estimate = l3Book.queuePosition("B", true);
        assertThat(estimate.known()).isTrue();
        assertThat(estimate.sizeAheadScaled()).isEqualTo(4L);
        assertThat(estimate.ownSizeScaled()).isEqualTo(5L);
    }

    @Test
    void duplicateAdd_existingOrder_replacesPreviousContribution() {
        l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L);

        assertThat(l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 6L), l2Book, 2L)).isTrue();

        assertThat(l3Book.activeOrderCount()).isEqualTo(1);
        assertThat(l3Book.levelSize(Side.BUY, 100L)).isEqualTo(6L);
        assertThat(l2Book.bidSizeAt(0)).isEqualTo(6L);
    }

    @Test
    void change_missingOrder_returnsFalseAndKeepsBook() {
        assertThat(l3Book.apply(event("missing", Side.BUY, UpdateAction.CHANGE, 100L, 4L), l2Book, 1L)).isFalse();

        assertThat(l3Book.activeOrderCount()).isZero();
        assertThat(l2Book.getBestBid()).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void capacityExceeded_newOrderRejectedWithoutMutatingExistingState() {
        final VenueL3Book capped = new VenueL3Book(VENUE_ID, INSTRUMENT_ID, 1);

        assertThat(capped.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L)).isTrue();
        assertThat(capped.apply(event("B", Side.BUY, UpdateAction.NEW, 101L, 10L), l2Book, 2L)).isFalse();

        assertThat(capped.activeOrderCount()).isEqualTo(1);
        assertThat(capped.containsOrder("A")).isTrue();
        assertThat(capped.containsOrder("B")).isFalse();
    }

    @Test
    void invalidCapacity_throwsClearly() {
        assertThatThrownBy(() -> new VenueL3Book(VENUE_ID, INSTRUMENT_ID, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxActiveOrders");
    }

    @Test
    void priceMove_existingOrder_removesOldLevelAndAddsNewLevel() {
        l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L);

        assertThat(l3Book.apply(event("A", Side.BUY, UpdateAction.CHANGE, 101L, 10L), l2Book, 2L)).isTrue();

        assertThat(l3Book.levelSize(Side.BUY, 100L)).isZero();
        assertThat(l3Book.levelSize(Side.BUY, 101L)).isEqualTo(10L);
        assertThat(l2Book.getBestBid()).isEqualTo(101L);
        assertThat(l2Book.bidSizeAt(0)).isEqualTo(10L);
    }

    @Test
    void delete_existingOrder_removesOrderAndDerivedLevel() {
        l3Book.apply(event("A", Side.SELL, UpdateAction.NEW, 110L, 10L), l2Book, 1L);

        assertThat(l3Book.apply(event("A", Side.SELL, UpdateAction.DELETE, 0L, 0L), l2Book, 2L)).isTrue();

        assertThat(l3Book.activeOrderCount()).isZero();
        assertThat(l3Book.levelSize(Side.SELL, 110L)).isZero();
        assertThat(l2Book.getBestAsk()).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void delete_missingOrder_returnsFalseAndKeepsBook() {
        assertThat(l3Book.apply(event("missing", Side.BUY, UpdateAction.DELETE, 0L, 0L), l2Book, 1L)).isFalse();

        assertThat(l3Book.activeOrderCount()).isZero();
        assertThat(l2Book.getBestBid()).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void zeroSizeChange_existingOrder_removesOrder() {
        l3Book.apply(event("A", Side.BUY, UpdateAction.NEW, 100L, 10L), l2Book, 1L);

        assertThat(l3Book.apply(event("A", Side.BUY, UpdateAction.CHANGE, 100L, 0L), l2Book, 2L)).isTrue();

        assertThat(l3Book.activeOrderCount()).isZero();
        assertThat(l2Book.getBestBid()).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void mismatchedVenue_throwsIllegalArgumentException() {
        final MarketByOrderEventDecoder event = event("A", Side.BUY, UpdateAction.NEW, 100L, 1L, 99);

        assertThatThrownBy(() -> l3Book.apply(event, l2Book, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venue/instrument");
    }

    private static MarketByOrderEventDecoder event(
        final String orderId,
        final Side side,
        final UpdateAction action,
        final long price,
        final long size) {
        return event(orderId, side, action, price, size, VENUE_ID);
    }

    private static MarketByOrderEventDecoder event(
        final String orderId,
        final Side side,
        final UpdateAction action,
        final long price,
        final long size,
        final int venueId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] orderIdBytes = orderId.getBytes(StandardCharsets.US_ASCII);
        new MarketByOrderEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(INSTRUMENT_ID)
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
}
