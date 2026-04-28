package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import org.agrona.collections.Long2ObjectHashMap;

import java.lang.foreign.Arena;

/**
 * Cluster-thread view of all venue/instrument L2 price-level books.
 *
 * <p>Responsibility: own the map from packed {@code (venueId,instrumentId)}
 * keys to off-heap {@link L2OrderBook} instances. Role in system: MessageRouter
 * will update this view before strategy dispatch, and strategies will read best
 * bid/ask values from it on every market-data tick. L2 feeds update these books
 * directly; L3 feeds update {@link VenueL3Book} outside this class and then apply
 * derived L2 levels here. Relationships: {@link MarketDataEventDecoder} supplies
 * normalized updates, {@link L2OrderBook} owns per-book off-heap storage, and
 * {@link Ids#INVALID_PRICE} is returned for missing books. Lifecycle: a cluster
 * node constructs one market view during service startup; books are created
 * lazily as market data arrives. Design intent: use a primitive packed key to
 * avoid tuple allocation and keep the strategy read path cheap while keeping
 * order-level L3 state separate.</p>
 */
public final class InternalMarketView {
    private static final Arena BOOK_ARENA = Arena.ofShared();

    private final Long2ObjectHashMap<L2OrderBook> books = new Long2ObjectHashMap<>();
    private final Long2ObjectHashMap<ConsolidatedL2Book> consolidatedBooks = new Long2ObjectHashMap<>();
    private final OwnOrderOverlay ownOrderOverlay = new OwnOrderOverlay();
    private final ExternalLiquidityView externalLiquidityView = new ExternalLiquidityView(this, ownOrderOverlay);

    /**
     * Applies a market-data event to its corresponding book, creating the book on first update.
     *
     * @param decoder decoded market-data event
     * @param clusterTimeMicros current cluster time in microseconds
     */
    public void apply(final MarketDataEventDecoder decoder, final long clusterTimeMicros) {
        book(decoder.venueId(), decoder.instrumentId()).apply(decoder, clusterTimeMicros);
        consolidatedBook(decoder.instrumentId()).applyVenueLevel(
            decoder.venueId(),
            decoder.entryType(),
            decoder.priceScaled(),
            decoder.sizeScaled());
    }

    /**
     * Returns the best bid for a venue/instrument pair.
     *
     * @param venueId venue ID
     * @param instrumentId instrument ID
     * @return best bid, or {@link Ids#INVALID_PRICE} when no book exists
     */
    public long getBestBid(final int venueId, final int instrumentId) {
        final L2OrderBook book = books.get(packKey(venueId, instrumentId));
        return book == null ? Ids.INVALID_PRICE : book.getBestBid();
    }

    /**
     * Returns the best ask for a venue/instrument pair.
     *
     * @param venueId venue ID
     * @param instrumentId instrument ID
     * @return best ask, or {@link Ids#INVALID_PRICE} when no book exists
     */
    public long getBestAsk(final int venueId, final int instrumentId) {
        final L2OrderBook book = books.get(packKey(venueId, instrumentId));
        return book == null ? Ids.INVALID_PRICE : book.getBestAsk();
    }

    public L2OrderBook book(final int venueId, final int instrumentId) {
        final long key = packKey(venueId, instrumentId);
        L2OrderBook book = books.get(key);
        if (book == null) {
            book = new L2OrderBook(venueId, instrumentId, BOOK_ARENA);
            books.put(key, book);
        }
        return book;
    }

    public L2OrderBook findBook(final int venueId, final int instrumentId) {
        return books.get(packKey(venueId, instrumentId));
    }

    public OwnOrderOverlay ownOrderOverlay() {
        return ownOrderOverlay;
    }

    public ExternalLiquidityView externalLiquidityView() {
        return externalLiquidityView;
    }

    public long consolidatedBestBid(final int instrumentId) {
        final ConsolidatedL2Book book = consolidatedBooks.get(instrumentId);
        return book == null ? Ids.INVALID_PRICE : book.bestBid();
    }

    public long consolidatedBestAsk(final int instrumentId) {
        final ConsolidatedL2Book book = consolidatedBooks.get(instrumentId);
        return book == null ? Ids.INVALID_PRICE : book.bestAsk();
    }

    public ConsolidatedL2Book consolidatedBook(final int instrumentId) {
        ConsolidatedL2Book book = consolidatedBooks.get(instrumentId);
        if (book == null) {
            book = new ConsolidatedL2Book(instrumentId);
            consolidatedBooks.put(instrumentId, book);
        }
        return book;
    }

    public boolean isStale(
        final int venueId,
        final int instrumentId,
        final long stalenessThresholdMicros,
        final long currentTimeMicros
    ) {
        final L2OrderBook book = books.get(packKey(venueId, instrumentId));
        return book == null || book.isStale(stalenessThresholdMicros, currentTimeMicros);
    }

    static long packKey(final int venueId, final int instrumentId) {
        return ((long)venueId << 32) | (instrumentId & 0xffff_ffffL);
    }
}
