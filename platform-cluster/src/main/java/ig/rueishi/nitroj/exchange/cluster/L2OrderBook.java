package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * Off-heap twenty-level venue L2 bid/ask book for one venue and instrument.
 *
 * <p>Responsibility: store and update a compact price-level L2 book using a
 * Panama {@link MemorySegment}. This class is intentionally not an L3/order-level
 * book: it has no order-ID keyed state, and it represents only aggregate size at
 * each visible price level for a single venue/instrument. Role in system:
 * {@link InternalMarketView} owns one instance per {@code (venueId,instrumentId)}
 * pair and strategies later read the best bid/ask on the cluster thread before
 * making decisions. L2 venue feeds update this book directly; L3 venue feeds
 * update {@link VenueL3Book} first, which then derives price-level updates for
 * this book. Relationships: generated {@link MarketDataEventDecoder} events
 * supply normalized venue book changes, {@link Ids#INVALID_PRICE} marks empty
 * levels, and the caller supplies cluster time for staleness tracking. Lifecycle:
 * production books are allocated from a shared arena; tests pass a confined arena
 * and close it after each test. Design intent: keep book storage invisible to the
 * GC and use fixed offsets so reads on the strategy path are primitive,
 * predictable, and allocation-free.</p>
 */
public final class L2OrderBook {
    public static final int LEVELS_PER_SIDE = 20;
    static final int LEVEL_BYTES = Long.BYTES * 2;
    static final int SIDE_BYTES = LEVELS_PER_SIDE * LEVEL_BYTES;
    static final int BOOK_BYTES = SIDE_BYTES * 2;

    private static final ValueLayout.OfLong LONG_LAYOUT =
        ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.nativeOrder());

    private final int venueId;
    private final int instrumentId;
    private final MemorySegment segment;
    private long lastUpdateClusterTime;

    /**
     * Allocates a book from the provided arena.
     *
     * @param venueId platform venue ID
     * @param instrumentId platform instrument ID
     * @param arena arena that owns the native memory lifecycle
     */
    public L2OrderBook(final int venueId, final int instrumentId, final Arena arena) {
        this.venueId = venueId;
        this.instrumentId = instrumentId;
        this.segment = arena.allocate(BOOK_BYTES, Long.BYTES);
        initializeSide(0);
        initializeSide(SIDE_BYTES);
    }

    /**
     * Applies one venue L2 market-data update to the bid or ask side.
     *
     * <p>Bids are kept in descending price order and asks in ascending price
     * order. NEW and CHANGE both upsert by price; CHANGE with size zero removes
     * the level. DELETE removes by price. Trade entries are ignored because this
     * class owns price-level book state only and deliberately has no trade or
     * order-ID storage.</p>
     *
     * @param decoder decoded normalized market-data event
     * @param clusterTimeMicros cluster time associated with the update
     */
    public void apply(final MarketDataEventDecoder decoder, final long clusterTimeMicros) {
        lastUpdateClusterTime = clusterTimeMicros;
        final EntryType entryType = decoder.entryType();
        if (entryType != EntryType.BID && entryType != EntryType.ASK) {
            return;
        }

        final int sideOffset = entryType == EntryType.BID ? 0 : SIDE_BYTES;
        final long price = decoder.priceScaled();
        final long size = decoder.sizeScaled();
        if (decoder.updateAction() == UpdateAction.DELETE || size == 0L) {
            delete(sideOffset, price);
        } else {
            upsert(sideOffset, entryType == EntryType.BID, price, size);
        }
    }

    /**
     * Reports whether the book has exceeded a caller-supplied staleness threshold.
     *
     * @param stalenessThresholdMicros allowed idle interval in microseconds
     * @param currentTimeMicros current cluster time in microseconds
     * @return true when the last update is older than the threshold
     */
    public boolean isStale(final long stalenessThresholdMicros, final long currentTimeMicros) {
        return currentTimeMicros - lastUpdateClusterTime > stalenessThresholdMicros;
    }

    public int venueId() {
        return venueId;
    }

    public int instrumentId() {
        return instrumentId;
    }

    public long getBestBid() {
        return priceAt(0, 0);
    }

    public long getBestAsk() {
        return priceAt(SIDE_BYTES, 0);
    }

    public long bidPriceAt(final int level) {
        return priceAt(0, level);
    }

    public long askPriceAt(final int level) {
        return priceAt(SIDE_BYTES, level);
    }

    public long bidSizeAt(final int level) {
        return sizeAt(0, level);
    }

    public long askSizeAt(final int level) {
        return sizeAt(SIDE_BYTES, level);
    }

    public long lastUpdateClusterTime() {
        return lastUpdateClusterTime;
    }

    private void initializeSide(final int sideOffset) {
        for (int level = 0; level < LEVELS_PER_SIDE; level++) {
            setLevel(sideOffset, level, Ids.INVALID_PRICE, 0L);
        }
    }

    private void upsert(final int sideOffset, final boolean bid, final long price, final long size) {
        final int existing = find(sideOffset, price);
        if (existing >= 0) {
            setLevel(sideOffset, existing, price, size);
            return;
        }

        final int insertAt = insertionPoint(sideOffset, bid, price);
        if (insertAt >= LEVELS_PER_SIDE) {
            return;
        }
        for (int level = LEVELS_PER_SIDE - 1; level > insertAt; level--) {
            setLevel(sideOffset, level, priceAt(sideOffset, level - 1), sizeAt(sideOffset, level - 1));
        }
        setLevel(sideOffset, insertAt, price, size);
    }

    private void delete(final int sideOffset, final long price) {
        final int index = find(sideOffset, price);
        if (index < 0) {
            return;
        }
        for (int level = index; level < LEVELS_PER_SIDE - 1; level++) {
            setLevel(sideOffset, level, priceAt(sideOffset, level + 1), sizeAt(sideOffset, level + 1));
        }
        setLevel(sideOffset, LEVELS_PER_SIDE - 1, Ids.INVALID_PRICE, 0L);
    }

    private int find(final int sideOffset, final long price) {
        for (int level = 0; level < LEVELS_PER_SIDE; level++) {
            final long current = priceAt(sideOffset, level);
            if (current == Ids.INVALID_PRICE) {
                return -1;
            }
            if (current == price) {
                return level;
            }
        }
        return -1;
    }

    private int insertionPoint(final int sideOffset, final boolean bid, final long price) {
        for (int level = 0; level < LEVELS_PER_SIDE; level++) {
            final long current = priceAt(sideOffset, level);
            if (current == Ids.INVALID_PRICE) {
                return level;
            }
            if ((bid && price > current) || (!bid && price < current)) {
                return level;
            }
        }
        return LEVELS_PER_SIDE;
    }

    private long priceAt(final int sideOffset, final int level) {
        return segment.get(LONG_LAYOUT, sideOffset + (long)level * LEVEL_BYTES);
    }

    private long sizeAt(final int sideOffset, final int level) {
        return segment.get(LONG_LAYOUT, sideOffset + (long)level * LEVEL_BYTES + Long.BYTES);
    }

    private void setLevel(final int sideOffset, final int level, final long price, final long size) {
        final long offset = sideOffset + (long)level * LEVEL_BYTES;
        segment.set(LONG_LAYOUT, offset, price);
        segment.set(LONG_LAYOUT, offset + Long.BYTES, size);
    }
}
