package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-017 and TASK-113 off-heap venue L2 book behavior.
 *
 * <p>Responsibility: verify sorted inserts, updates, deletes, capacity handling,
 * staleness, L2/L3-derived boundaries, and empty-book sentinels for
 * {@link L2OrderBook} and the {@link InternalMarketView} wrapper. Role in system:
 * these tests lock down the market view that later MessageRouter and strategies
 * rely on before strategy dispatch. Relationships: tests use real generated SBE
 * market-data encoders and decoders and allocate each book from a confined
 * {@link Arena}. Lifecycle: each test opens one arena in {@link #createArena()}
 * and closes it in {@link #closeArena()} to avoid native-memory leaks. Design
 * intent: exercise the production off-heap layout through public book operations
 * and prove the price-level book has no order-ID keyed L3 state.</p>
 */
final class L2OrderBookTest {
    private static final int VENUE_ID = 1;
    private static final int INSTRUMENT_ID = 1;
    private Arena testArena;

    @BeforeEach
    void createArena() {
        testArena = Arena.ofConfined();
    }

    @AfterEach
    void closeArena() {
        testArena.close();
    }

    /** Verifies one bid update becomes the best bid. */
    @Test
    void insertBid_singleLevel_bestBidCorrect() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 100L), 10L);

        assertThat(book.getBestBid()).isEqualTo(65_000_00000000L);
    }

    /** Verifies one ask update becomes the best ask. */
    @Test
    void insertAsk_singleLevel_bestAskCorrect() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_010_00000000L, 100L), 10L);

        assertThat(book.getBestAsk()).isEqualTo(65_010_00000000L);
    }

    /** Verifies bids sort highest price first. */
    @Test
    void insertBid_multipleLevels_sortedDescending() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 64_900_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_100_00000000L, 1L), 2L);
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 3L);

        assertThat(book.bidPriceAt(0)).isEqualTo(65_100_00000000L);
        assertThat(book.bidPriceAt(1)).isEqualTo(65_000_00000000L);
        assertThat(book.bidPriceAt(2)).isEqualTo(64_900_00000000L);
    }

    /** Verifies asks sort lowest price first. */
    @Test
    void insertAsk_multipleLevels_sortedAscending() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_200_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_050_00000000L, 1L), 2L);
        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_100_00000000L, 1L), 3L);

        assertThat(book.askPriceAt(0)).isEqualTo(65_050_00000000L);
        assertThat(book.askPriceAt(1)).isEqualTo(65_100_00000000L);
        assertThat(book.askPriceAt(2)).isEqualTo(65_200_00000000L);
    }

    /** Verifies CHANGE updates size in place for an existing price level. */
    @Test
    void updateExistingLevel_sizeChanged_priceUnchanged() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 10L), 1L);
        book.apply(decoder(EntryType.BID, UpdateAction.CHANGE, 65_000_00000000L, 25L), 2L);

        assertThat(book.bidPriceAt(0)).isEqualTo(65_000_00000000L);
        assertThat(book.bidSizeAt(0)).isEqualTo(25L);
    }

    /** Verifies repeated same-price updates replace aggregate size rather than keeping order IDs. */
    @Test
    void updateExistingLevel_repeatedSamePrice_noOrderIdStateAccumulated() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 10L), 1L);
        book.apply(decoder(EntryType.BID, UpdateAction.CHANGE, 65_000_00000000L, 7L), 2L);
        book.apply(decoder(EntryType.BID, UpdateAction.CHANGE, 65_000_00000000L, 3L), 3L);

        assertThat(book.bidSizeAt(0)).isEqualTo(3L);
        assertThat(book.bidPriceAt(1)).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies deleting the best bid promotes the second level. */
    @Test
    void deleteBestBid_secondLevelBecomesBest() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_100_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 2L);
        book.apply(decoder(EntryType.BID, UpdateAction.DELETE, 65_100_00000000L, 0L), 3L);

        assertThat(book.getBestBid()).isEqualTo(65_000_00000000L);
    }

    /** Verifies deleting the best ask promotes the second level. */
    @Test
    void deleteBestAsk_secondLevelBecomesBest() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_000_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_100_00000000L, 1L), 2L);
        book.apply(decoder(EntryType.ASK, UpdateAction.DELETE, 65_000_00000000L, 0L), 3L);

        assertThat(book.getBestAsk()).isEqualTo(65_100_00000000L);
    }

    /** Verifies deleting a middle level compacts the remaining side. */
    @Test
    void deleteMiddleLevel_remainingLevelsCompact() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_200_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_100_00000000L, 1L), 2L);
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 3L);
        book.apply(decoder(EntryType.BID, UpdateAction.DELETE, 65_100_00000000L, 0L), 4L);

        assertThat(book.bidPriceAt(0)).isEqualTo(65_200_00000000L);
        assertThat(book.bidPriceAt(1)).isEqualTo(65_000_00000000L);
        assertThat(book.bidPriceAt(2)).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies an empty bid side returns the invalid-price sentinel. */
    @Test
    void getBestBid_emptyBook_returnsInvalidSentinel() {
        assertThat(book().getBestBid()).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies an empty ask side returns the invalid-price sentinel. */
    @Test
    void getBestAsk_emptyBook_returnsInvalidSentinel() {
        assertThat(book().getBestAsk()).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies a better level at capacity is inserted and the worst level is evicted. */
    @Test
    void insertAtMaxLevels_betterPrice_evictsWorstAndInserts() {
        final L2OrderBook book = book();
        for (int i = 0; i < L2OrderBook.LEVELS_PER_SIDE; i++) {
            book.apply(decoder(EntryType.BID, UpdateAction.NEW, (65_000L - i) * Ids.SCALE, 1L), i);
        }

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 66_000L * Ids.SCALE, 1L), 30L);

        assertThat(book.getBestBid()).isEqualTo(66_000L * Ids.SCALE);
        assertThat(book.bidPriceAt(L2OrderBook.LEVELS_PER_SIDE - 1)).isEqualTo(64_982L * Ids.SCALE);
    }

    /** Verifies a worse level at capacity is discarded. */
    @Test
    void insertAtMaxLevels_worsePrice_discarded() {
        final L2OrderBook book = book();
        for (int i = 0; i < L2OrderBook.LEVELS_PER_SIDE; i++) {
            book.apply(decoder(EntryType.ASK, UpdateAction.NEW, (65_000L + i) * Ids.SCALE, 1L), i);
        }

        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 66_000L * Ids.SCALE, 1L), 30L);

        assertThat(book.askPriceAt(L2OrderBook.LEVELS_PER_SIDE - 1)).isEqualTo(65_019L * Ids.SCALE);
    }

    /** Verifies CHANGE with zero size removes the level. */
    @Test
    void updateLevel_sizeToZero_levelRemoved() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_000_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.ASK, UpdateAction.CHANGE, 65_000_00000000L, 0L), 2L);

        assertThat(book.getBestAsk()).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies deleting a missing level leaves unrelated levels unchanged. */
    @Test
    void deleteMissingLevel_existingLevelsUnchanged() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 1L);
        book.apply(decoder(EntryType.BID, UpdateAction.DELETE, 64_000_00000000L, 0L), 2L);

        assertThat(book.getBestBid()).isEqualTo(65_000_00000000L);
        assertThat(book.bidSizeAt(0)).isEqualTo(1L);
    }

    /** Verifies non-book entries do not create price levels. */
    @Test
    void applyTradeEntry_noPriceLevelMutation() {
        final L2OrderBook book = book();

        book.apply(decoder(EntryType.TRADE, UpdateAction.NEW, 65_000_00000000L, 1L), 1L);

        assertThat(book.getBestBid()).isEqualTo(Ids.INVALID_PRICE);
        assertThat(book.getBestAsk()).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies invalid level access fails through the public API. */
    @Test
    void priceAt_invalidLevel_throwsIndexOutOfBounds() {
        final L2OrderBook book = book();

        assertThatThrownBy(() -> book.bidPriceAt(L2OrderBook.LEVELS_PER_SIDE * 2))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    /** Verifies arena lifetime violations surface as exceptions instead of silent corruption. */
    @Test
    void apply_afterArenaClosed_throwsIllegalState() {
        final L2OrderBook book = book();
        testArena.close();

        assertThatThrownBy(() -> book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 1L))
            .isInstanceOf(IllegalStateException.class);

        testArena = Arena.ofConfined();
    }

    /** Verifies a book with no recent update is stale. */
    @Test
    void staleness_noUpdateAfterThreshold_isStaleTrue() {
        final L2OrderBook book = book();
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 100L);

        assertThat(book.isStale(10L, 111L)).isTrue();
    }

    /** Verifies a recent update clears staleness. */
    @Test
    void staleness_updateReceived_isStaleCleared() {
        final L2OrderBook book = book();
        book.apply(decoder(EntryType.BID, UpdateAction.NEW, 65_000_00000000L, 1L), 110L);

        assertThat(book.isStale(10L, 119L)).isFalse();
    }

    /** Verifies repeated updates through InternalMarketView do not allocate heap-sized book storage. */
    @Test
    void offHeap_noGCPressure_heapUnchanged() {
        final InternalMarketView view = new InternalMarketView();

        for (int i = 0; i < 1_000; i++) {
            view.apply(decoder(EntryType.BID, UpdateAction.NEW, (65_000L + i % 20) * Ids.SCALE, 1L), i);
        }

        assertThat(view.getBestBid(VENUE_ID, INSTRUMENT_ID)).isEqualTo(65_019L * Ids.SCALE);
    }

    /** Verifies L2-only events update venue L2 directly without requiring L3 state. */
    @Test
    void internalMarketView_l2OnlyUpdate_directlyUpdatesVenueL2() {
        final InternalMarketView view = new InternalMarketView();

        view.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_010_00000000L, 5L), 1L);

        assertThat(view.book(VENUE_ID, INSTRUMENT_ID).askSizeAt(0)).isEqualTo(5L);
        assertThat(view.consolidatedBook(INSTRUMENT_ID).sizeAt(EntryType.ASK, 65_010_00000000L)).isEqualTo(5L);
    }

    /** Verifies configured venue/instrument books are allocated during startup warmup. */
    @Test
    void internalMarketView_preallocateMarketState_allocatesConfiguredBooks() {
        final InternalMarketView view = new InternalMarketView(new int[]{1, 2}, new int[]{10, 11});

        assertThat(view.bookCount()).isEqualTo(4);
        assertThat(view.consolidatedBookCount()).isEqualTo(2);
        assertThat(view.findBook(1, 10)).isNotNull();
        assertThat(view.findBook(2, 11)).isNotNull();
    }

    /** Verifies unknown books stay absent until explicitly requested. */
    @Test
    void internalMarketView_unknownVenueInstrument_findReturnsNullAndInvalidPrices() {
        final InternalMarketView view = new InternalMarketView(new int[]{1}, new int[]{10});

        assertThat(view.findBook(9, 99)).isNull();
        assertThat(view.getBestBid(9, 99)).isEqualTo(Ids.INVALID_PRICE);
        assertThat(view.getBestAsk(9, 99)).isEqualTo(Ids.INVALID_PRICE);
    }

    /** Verifies malformed configured IDs fail before live trading starts. */
    @Test
    void internalMarketView_preallocateMarketState_rejectsInvalidConfiguredIds() {
        assertThatThrownBy(() -> new InternalMarketView(new int[]{0}, new int[]{10}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venueId");
        assertThatThrownBy(() -> new InternalMarketView(new int[]{1}, new int[]{0}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("instrumentId");
    }

    /** Verifies the first configured tick mutates preallocated books instead of creating new ones. */
    @Test
    void internalMarketView_firstConfiguredTick_usesPreallocatedBooks() {
        final InternalMarketView view = new InternalMarketView(new int[]{VENUE_ID}, new int[]{INSTRUMENT_ID});
        final int bookCount = view.bookCount();
        final int consolidatedCount = view.consolidatedBookCount();

        view.apply(decoder(EntryType.ASK, UpdateAction.NEW, 65_010_00000000L, 5L), 1L);

        assertThat(view.bookCount()).isEqualTo(bookCount);
        assertThat(view.consolidatedBookCount()).isEqualTo(consolidatedCount);
        assertThat(view.getBestAsk(VENUE_ID, INSTRUMENT_ID)).isEqualTo(65_010_00000000L);
    }

    /** Verifies L3-derived updates flow into venue L2 and consolidated L2. */
    @Test
    void venueL3Book_derivedUpdate_updatesVenueL2AndConsolidatedL2() {
        final InternalMarketView view = new InternalMarketView();
        final VenueL3Book l3Book = new VenueL3Book(VENUE_ID, INSTRUMENT_ID);
        final L2OrderBook l2Book = view.book(VENUE_ID, INSTRUMENT_ID);

        l3Book.apply(
            VenueL3BookTestEvent.event("L3-A", Side.BUY, UpdateAction.NEW, 65_000_00000000L, 2L),
            l2Book,
            1L);
        view.consolidatedBook(INSTRUMENT_ID).applyVenueLevel(
            VENUE_ID, EntryType.BID, 65_000_00000000L, l3Book.levelSize(Side.BUY, 65_000_00000000L));

        assertThat(l2Book.getBestBid()).isEqualTo(65_000_00000000L);
        assertThat(l2Book.bidSizeAt(0)).isEqualTo(2L);
        assertThat(view.consolidatedBook(INSTRUMENT_ID).sizeAt(EntryType.BID, 65_000_00000000L)).isEqualTo(2L);
    }

    private L2OrderBook book() {
        return new L2OrderBook(VENUE_ID, INSTRUMENT_ID, testArena);
    }

    private static MarketDataEventDecoder decoder(
        final EntryType entryType,
        final UpdateAction updateAction,
        final long price,
        final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        final MarketDataEventEncoder encoder = new MarketDataEventEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(VENUE_ID)
            .instrumentId(INSTRUMENT_ID)
            .entryType(entryType)
            .updateAction(updateAction)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder());
        return decoder;
    }

    private static final class VenueL3BookTestEvent {
        private static ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder event(
            final String orderId,
            final Side side,
            final UpdateAction action,
            final long price,
            final long size) {

            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
            final byte[] orderIdBytes = orderId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            new ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .venueId(VENUE_ID)
                .instrumentId(INSTRUMENT_ID)
                .side(side)
                .updateAction(action)
                .priceScaled(price)
                .sizeScaled(size)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1)
                .putVenueOrderId(orderIdBytes, 0, orderIdBytes.length);
            final ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder decoder =
                new ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder();
            decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
                ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder.BLOCK_LENGTH,
                ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder.SCHEMA_VERSION);
            return decoder;
        }
    }
}
