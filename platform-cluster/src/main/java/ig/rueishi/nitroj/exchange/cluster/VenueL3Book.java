package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.common.VenueOrderId;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.Objects;

/**
 * Bounded active L3 book for one venue/instrument pair.
 *
 * <p>Responsibility: stores live order-level market-data entries and derives
 * aggregate L2 price-level updates after each L3 add/change/delete. Role in
 * system: V11 cluster market-data routing applies {@code MarketByOrderEvent}
 * messages here for L3 venues, then updates the existing per-venue
 * {@link L2OrderBook} so strategies can keep consuming L2 views. Relationships:
 * {@link MarketByOrderEventDecoder} supplies normalized order-level changes;
 * {@link L2OrderBook} remains the authoritative price-level book. Lifecycle:
 * one instance is owned per {@code (venueId,instrumentId)} L3 feed and retains
 * only active orders; deleted orders are removed. V12 storage is fixed-capacity:
 * venue order IDs are copied into preallocated byte slots, hash collisions are
 * resolved with exact byte comparison, price levels are tracked in primitive
 * open-addressed tables, and insertion order is represented by a monotonic
 * sequence number. Design intent: keep add/change/delete and L3-to-L2
 * derivation allocation-free after construction while preserving the advisory
 * own-order and queue-position views over the same gross L3 state.
 */
public final class VenueL3Book {
    public static final int DEFAULT_MAX_ACTIVE_ORDERS = 100_000;
    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;
    private static final byte BUY_SIDE = 1;
    private static final byte SELL_SIDE = 2;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int venueId;
    private final int instrumentId;
    private final int maxActiveOrders;
    private final int orderMask;
    private final int levelMask;
    private final FingerprintStrategy fingerprintStrategy;
    private final byte[] orderStates;
    private final byte[] orderIdBytes;
    private final int[] orderIdLengths;
    private final long[] orderFingerprints;
    private final byte[] orderSides;
    private final long[] orderPrices;
    private final long[] orderSizes;
    private final long[] orderSequences;
    private final byte[] levelStates;
    private final byte[] levelSides;
    private final long[] levelPrices;
    private final long[] levelSizes;
    private final byte[] eventOrderIdScratch = new byte[VenueOrderId.MAX_LENGTH];
    private final UnsafeBuffer derivedBuffer = new UnsafeBuffer(new byte[256]);
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketDataEventEncoder marketDataEncoder = new MarketDataEventEncoder();
    private final MarketDataEventDecoder marketDataDecoder = new MarketDataEventDecoder();
    private int activeOrderCount;
    private long nextSequence = 1L;

    public VenueL3Book(final int venueId, final int instrumentId) {
        this(venueId, instrumentId, DEFAULT_MAX_ACTIVE_ORDERS);
    }

    public VenueL3Book(final int venueId, final int instrumentId, final int maxActiveOrders) {
        this(venueId, instrumentId, maxActiveOrders, VenueL3Book::fingerprint);
    }

    VenueL3Book(
        final int venueId,
        final int instrumentId,
        final int maxActiveOrders,
        final FingerprintStrategy fingerprintStrategy) {

        if (maxActiveOrders <= 0) {
            throw new IllegalArgumentException("maxActiveOrders must be positive");
        }
        this.venueId = venueId;
        this.instrumentId = instrumentId;
        this.maxActiveOrders = maxActiveOrders;
        this.fingerprintStrategy = Objects.requireNonNull(fingerprintStrategy, "fingerprintStrategy");
        final int orderCapacity = tableCapacity(maxActiveOrders);
        final int levelCapacity = tableCapacity(maxActiveOrders);
        orderMask = orderCapacity - 1;
        levelMask = levelCapacity - 1;
        orderStates = new byte[orderCapacity];
        orderIdBytes = new byte[orderCapacity * VenueOrderId.MAX_LENGTH];
        orderIdLengths = new int[orderCapacity];
        orderFingerprints = new long[orderCapacity];
        orderSides = new byte[orderCapacity];
        orderPrices = new long[orderCapacity];
        orderSizes = new long[orderCapacity];
        orderSequences = new long[orderCapacity];
        levelStates = new byte[levelCapacity];
        levelSides = new byte[levelCapacity];
        levelPrices = new long[levelCapacity];
        levelSizes = new long[levelCapacity];
    }

    /**
     * Applies one L3 order update and propagates the derived level to an L2 book.
     *
     * @param event normalized L3 order-level event
     * @param l2Book per-venue L2 book to update with the derived level
     * @param clusterTimeMicros cluster timestamp used by the L2 book
     * @return true when the L3 update changed state, false for missing deletes or invalid entries
     */
    public boolean apply(
        final MarketByOrderEventDecoder event,
        final L2OrderBook l2Book,
        final long clusterTimeMicros) {

        if (event.venueId() != venueId || event.instrumentId() != instrumentId) {
            throw new IllegalArgumentException("L3 event does not match book venue/instrument");
        }

        final int orderIdLength = copyVenueOrderId(event);
        if (orderIdLength <= 0 || event.side() == Side.NULL_VAL) {
            return false;
        }
        final byte side = sideValue(event.side());

        return switch (event.updateAction()) {
            case DELETE -> delete(orderIdLength, event, l2Book, clusterTimeMicros);
            case NEW, CHANGE -> upsert(orderIdLength, side, event, l2Book, clusterTimeMicros);
            default -> false;
        };
    }

    public int activeOrderCount() {
        return activeOrderCount;
    }

    public long levelSize(final Side side, final long priceScaled) {
        if (side == Side.NULL_VAL) {
            return 0L;
        }
        final int slot = findLevel(sideValue(side), priceScaled);
        return slot < 0 ? 0L : levelSizes[slot];
    }

    /**
     * Reports whether a venue order ID is currently visible in this L3 book.
     *
     * @param venueOrderId venue-assigned order identifier
     * @return true when the order is active in gross L3 state
     */
    public boolean containsOrder(final String venueOrderId) {
        final int length = copyVenueOrderId(venueOrderId);
        return findOrder(eventOrderIdScratch, length, fingerprintStrategy.fingerprint(eventOrderIdScratch, 0, length)) >= 0;
    }

    /**
     * Returns the visible gross L3 size for one venue order ID.
     *
     * @param venueOrderId venue-assigned order identifier
     * @return visible order size, or zero when absent
     */
    public long orderSize(final String venueOrderId) {
        final int length = copyVenueOrderId(venueOrderId);
        final int slot = findOrder(eventOrderIdScratch, length, fingerprintStrategy.fingerprint(eventOrderIdScratch, 0, length));
        return slot < 0 ? 0L : orderSizes[slot];
    }

    /**
     * Returns exact externally executable size for one L3 order.
     *
     * <p>The gross L3 book remains unmodified. If the order ID matches a live
     * NitroJEx-owned order in the overlay with the same venue, instrument, side,
     * and price, that own visible quantity is subtracted. Mismatches return the
     * original gross order size so stale or unrelated private state cannot
     * corrupt public market-data state.</p>
     *
     * @param venueOrderId venue-assigned order identifier
     * @param ownOrders private own-order overlay derived from order-manager state
     * @return non-negative external visible size for the order
     */
    public long externalOrderSize(final String venueOrderId, final OwnOrderOverlay ownOrders) {
        Objects.requireNonNull(ownOrders, "ownOrders");
        final int length = copyVenueOrderId(venueOrderId);
        final int slot = findOrder(eventOrderIdScratch, length, fingerprintStrategy.fingerprint(eventOrderIdScratch, 0, length));
        if (slot < 0) {
            return 0L;
        }
        final VenueOrderId normalizedOrderId = VenueOrderId.fromBytes(eventOrderIdScratch, 0, length);
        final EntryType entryType = orderSides[slot] == BUY_SIDE ? EntryType.BID : EntryType.ASK;
        final long ownSize = ownOrders.exactOwnSizeByVenueOrderId(
            normalizedOrderId, venueId, instrumentId, entryType, orderPrices[slot]);
        return Math.max(0L, orderSizes[slot] - ownSize);
    }

    /**
     * Estimates same-level queue position for a visible order.
     *
     * <p>This method is explicitly advisory. It returns {@link
     * QueuePositionEstimate#unknown()} when the caller tells us feed ordering is
     * unavailable or when the order ID is absent. When ordering is available, the
     * current insertion order of active L3 orders is used as the best estimate
     * and only same-side, same-price quantity ahead of the target is counted.</p>
     *
     * @param venueOrderId venue-assigned order identifier
     * @param feedOrderingAvailable true when the venue/feed preserves useful order sequencing
     * @return known or unknown queue-position estimate
     */
    public QueuePositionEstimate queuePosition(final String venueOrderId, final boolean feedOrderingAvailable) {
        final int length = copyVenueOrderId(venueOrderId);
        if (!feedOrderingAvailable) {
            return QueuePositionEstimate.unknown();
        }
        final int targetSlot = findOrder(
            eventOrderIdScratch,
            length,
            fingerprintStrategy.fingerprint(eventOrderIdScratch, 0, length));
        if (targetSlot < 0) {
            return QueuePositionEstimate.unknown();
        }
        long sizeAhead = 0L;
        final byte targetSide = orderSides[targetSlot];
        final long targetPrice = orderPrices[targetSlot];
        final long targetSequence = orderSequences[targetSlot];
        for (int slot = 0; slot < orderStates.length; slot++) {
            if (orderStates[slot] == OCCUPIED
                && orderSequences[slot] < targetSequence
                && orderSides[slot] == targetSide
                && orderPrices[slot] == targetPrice) {
                sizeAhead += orderSizes[slot];
            }
        }
        return QueuePositionEstimate.known(sizeAhead, orderSizes[targetSlot]);
    }

    private boolean upsert(
        final int orderIdLength,
        final byte side,
        final MarketByOrderEventDecoder event,
        final L2OrderBook l2Book,
        final long clusterTimeMicros) {

        final long fingerprint = fingerprintStrategy.fingerprint(eventOrderIdScratch, 0, orderIdLength);
        final int previousSlot = findOrder(eventOrderIdScratch, orderIdLength, fingerprint);
        if (previousSlot < 0 && event.updateAction() == UpdateAction.CHANGE) {
            return false;
        }
        if (previousSlot < 0 && activeOrderCount >= maxActiveOrders) {
            return false;
        }
        final byte previousSide;
        final long previousPrice;
        if (previousSlot >= 0) {
            previousSide = orderSides[previousSlot];
            previousPrice = orderPrices[previousSlot];
            adjustLevel(previousSide, previousPrice, -orderSizes[previousSlot]);
        } else {
            previousSide = EMPTY;
            previousPrice = 0L;
        }

        if (event.sizeScaled() == 0L) {
            if (previousSlot >= 0) {
                removeOrder(previousSlot);
                publishDerivedLevel(previousSide, previousPrice, l2Book, clusterTimeMicros, event);
                return true;
            }
            return false;
        }

        final int slot = previousSlot >= 0 ? previousSlot : findOrderInsertionSlot(eventOrderIdScratch, orderIdLength, fingerprint);
        if (slot < 0) {
            return false;
        }
        if (previousSlot < 0) {
            insertOrder(slot, orderIdLength, fingerprint);
        }
        orderSides[slot] = side;
        orderPrices[slot] = event.priceScaled();
        orderSizes[slot] = event.sizeScaled();
        if (previousSlot >= 0 && (previousSide != side || previousPrice != event.priceScaled())) {
            publishDerivedLevel(previousSide, previousPrice, l2Book, clusterTimeMicros, event);
        }
        adjustLevel(side, event.priceScaled(), event.sizeScaled());
        publishDerivedLevel(side, event.priceScaled(), l2Book, clusterTimeMicros, event);
        return true;
    }

    private boolean delete(
        final int orderIdLength,
        final MarketByOrderEventDecoder event,
        final L2OrderBook l2Book,
        final long clusterTimeMicros) {

        final long fingerprint = fingerprintStrategy.fingerprint(eventOrderIdScratch, 0, orderIdLength);
        final int slot = findOrder(eventOrderIdScratch, orderIdLength, fingerprint);
        if (slot < 0) {
            return false;
        }
        final byte side = orderSides[slot];
        final long price = orderPrices[slot];
        adjustLevel(side, price, -orderSizes[slot]);
        removeOrder(slot);
        publishDerivedLevel(side, price, l2Book, clusterTimeMicros, event);
        return true;
    }

    private void adjustLevel(final byte side, final long priceScaled, final long delta) {
        final int existingSlot = findLevel(side, priceScaled);
        final long current = existingSlot < 0 ? 0L : levelSizes[existingSlot];
        final long next = current + delta;
        if (next <= 0L) {
            if (existingSlot >= 0) {
                levelStates[existingSlot] = DELETED;
                levelSides[existingSlot] = 0;
                levelPrices[existingSlot] = 0L;
                levelSizes[existingSlot] = 0L;
            }
        } else {
            final int slot = existingSlot >= 0 ? existingSlot : findLevelInsertionSlot(side, priceScaled);
            levelStates[slot] = OCCUPIED;
            levelSides[slot] = side;
            levelPrices[slot] = priceScaled;
            levelSizes[slot] = next;
        }
    }

    private void publishDerivedLevel(
        final byte side,
        final long priceScaled,
        final L2OrderBook l2Book,
        final long clusterTimeMicros,
        final MarketByOrderEventDecoder source) {

        final long size = levelSize(side, priceScaled);
        final EntryType entryType = side == BUY_SIDE ? EntryType.BID : EntryType.ASK;
        marketDataEncoder.wrapAndApplyHeader(derivedBuffer, 0, headerEncoder)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .entryType(entryType)
            .updateAction(size == 0L ? UpdateAction.DELETE : UpdateAction.CHANGE)
            .priceScaled(priceScaled)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(source.ingressTimestampNanos())
            .exchangeTimestampNanos(source.exchangeTimestampNanos())
            .fixSeqNum(source.fixSeqNum());
        marketDataDecoder.wrap(derivedBuffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        l2Book.apply(marketDataDecoder, clusterTimeMicros);
    }

    private long levelSize(final byte side, final long priceScaled) {
        final int slot = findLevel(side, priceScaled);
        return slot < 0 ? 0L : levelSizes[slot];
    }

    private int copyVenueOrderId(final MarketByOrderEventDecoder event) {
        final int length = event.venueOrderIdLength();
        if (length <= 0 || length > VenueOrderId.MAX_LENGTH) {
            return -1;
        }
        event.getVenueOrderId(eventOrderIdScratch, 0, length);
        return length;
    }

    private int copyVenueOrderId(final String venueOrderId) {
        if (venueOrderId == null || venueOrderId.isBlank()) {
            throw new IllegalArgumentException("venueOrderId must not be null or blank");
        }
        final int length = venueOrderId.length();
        if (length > VenueOrderId.MAX_LENGTH) {
            throw new IllegalArgumentException("venueOrderId exceeds max length " + VenueOrderId.MAX_LENGTH);
        }
        for (int i = 0; i < length; i++) {
            eventOrderIdScratch[i] = (byte)venueOrderId.charAt(i);
        }
        return length;
    }

    private int findOrder(final byte[] source, final int length, final long fingerprint) {
        int slot = orderSlot(fingerprint);
        for (int probes = 0; probes < orderStates.length; probes++) {
            final byte state = orderStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED
                && orderFingerprints[slot] == fingerprint
                && orderIdLengths[slot] == length
                && orderIdEquals(slot, source, length)) {
                return slot;
            }
            slot = (slot + 1) & orderMask;
        }
        return -1;
    }

    private int findOrderInsertionSlot(final byte[] source, final int length, final long fingerprint) {
        int slot = orderSlot(fingerprint);
        int firstDeleted = -1;
        for (int probes = 0; probes < orderStates.length; probes++) {
            final byte state = orderStates[slot];
            if (state == EMPTY) {
                return firstDeleted >= 0 ? firstDeleted : slot;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED
                && orderFingerprints[slot] == fingerprint
                && orderIdLengths[slot] == length
                && orderIdEquals(slot, source, length)) {
                return slot;
            }
            slot = (slot + 1) & orderMask;
        }
        return firstDeleted;
    }

    private void insertOrder(final int slot, final int length, final long fingerprint) {
        final int offset = orderIdOffset(slot);
        System.arraycopy(eventOrderIdScratch, 0, orderIdBytes, offset, length);
        orderStates[slot] = OCCUPIED;
        orderIdLengths[slot] = length;
        orderFingerprints[slot] = fingerprint;
        orderSequences[slot] = nextSequence++;
        activeOrderCount++;
    }

    private void removeOrder(final int slot) {
        orderStates[slot] = DELETED;
        orderIdLengths[slot] = 0;
        orderFingerprints[slot] = 0L;
        orderSides[slot] = 0;
        orderPrices[slot] = 0L;
        orderSizes[slot] = 0L;
        orderSequences[slot] = 0L;
        activeOrderCount--;
    }

    private boolean orderIdEquals(final int slot, final byte[] source, final int length) {
        final int offset = orderIdOffset(slot);
        for (int i = 0; i < length; i++) {
            if (orderIdBytes[offset + i] != source[i]) {
                return false;
            }
        }
        return true;
    }

    private int findLevel(final byte side, final long priceScaled) {
        int slot = levelSlot(side, priceScaled);
        for (int probes = 0; probes < levelStates.length; probes++) {
            final byte state = levelStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED && levelSides[slot] == side && levelPrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & levelMask;
        }
        return -1;
    }

    private int findLevelInsertionSlot(final byte side, final long priceScaled) {
        int slot = levelSlot(side, priceScaled);
        int firstDeleted = -1;
        for (int probes = 0; probes < levelStates.length; probes++) {
            final byte state = levelStates[slot];
            if (state == EMPTY) {
                return firstDeleted >= 0 ? firstDeleted : slot;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED && levelSides[slot] == side && levelPrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & levelMask;
        }
        return firstDeleted;
    }

    private int orderIdOffset(final int slot) {
        return slot * VenueOrderId.MAX_LENGTH;
    }

    private int orderSlot(final long fingerprint) {
        return spread(fingerprint) & orderMask;
    }

    private int levelSlot(final byte side, final long priceScaled) {
        return spread(priceScaled ^ (side * 0x9e3779b97f4a7c15L)) & levelMask;
    }

    private static int spread(final long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int)mixed;
    }

    private static int tableCapacity(final int maxEntries) {
        int capacity = 1;
        final int target = Math.max(2, maxEntries * 2);
        while (capacity < target) {
            capacity <<= 1;
        }
        return capacity;
    }

    private static byte sideValue(final Side side) {
        return side == Side.BUY ? BUY_SIDE : SELL_SIDE;
    }

    private static Side side(final byte side) {
        return side == BUY_SIDE ? Side.BUY : Side.SELL;
    }

    private static long fingerprint(final byte[] source, final int offset, final int length) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < length; i++) {
            hash ^= source[offset + i] & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    @FunctionalInterface
    interface FingerprintStrategy {
        long fingerprint(byte[] source, int offset, int length);
    }
}
