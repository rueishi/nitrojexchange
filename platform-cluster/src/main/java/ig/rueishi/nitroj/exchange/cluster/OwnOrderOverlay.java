package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.VenueOrderId;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.order.OrderState;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tracks NitroJEx-owned visible working liquidity outside the public market book.
 *
 * <p>Responsibility: maintain a bounded cluster-thread overlay of NitroJEx's own
 * resting quantity by venue, instrument, side, price, and optional venue order
 * ID. Role in system: arbitrage and hedging read this overlay through {@link
 * ExternalLiquidityView} to avoid treating NitroJEx's own quotes as external
 * executable liquidity. Relationships: {@link OrderState} remains the
 * authoritative order lifecycle source, while {@link L2OrderBook}, {@link
 * VenueL3Book}, and {@link ConsolidatedL2Book} remain gross venue-published
 * market views.</p>
 *
 * <p>V12 storage model: orders, exact venue-order-ID lookup, and aggregate
 * level quantities live in fixed-size primitive open-addressed tables. Venue
 * order IDs are copied into preallocated byte slots and compared by fingerprint
 * plus exact bytes, so hash collisions cannot mark the wrong order as own.
 * Exact L3 subtraction uses the venue-order-ID table. L2 approximation uses
 * the level table. Self-cross checks scan active own-order slots directly.
 * Capacity-full behavior is deterministic: a new order is ignored when the
 * configured order capacity is full, while updates/removes for existing orders
 * still proceed and stale venue-ID mappings are cleaned up.</p>
 */
public final class OwnOrderOverlay {
    public static final int DEFAULT_MAX_OWN_ORDERS = 100_000;
    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;
    private static final byte BID_SIDE = 1;
    private static final byte ASK_SIDE = 2;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int maxOwnOrders;
    private final int orderMask;
    private final int venueOrderMask;
    private final int levelMask;
    private final FingerprintStrategy fingerprintStrategy;
    private final byte[] orderStates;
    private final long[] orderClOrdIds;
    private final int[] orderVenueIds;
    private final int[] orderInstrumentIds;
    private final byte[] orderSides;
    private final long[] orderPrices;
    private final long[] orderRemainingQty;
    private final boolean[] orderHasVenueOrderId;
    private final byte[] orderVenueOrderIdBytes;
    private final int[] orderVenueOrderIdLengths;
    private final long[] orderVenueOrderIdFingerprints;
    private final byte[] venueOrderStates;
    private final int[] venueOrderVenueIds;
    private final int[] venueOrderInstrumentIds;
    private final byte[] venueOrderIdBytes;
    private final int[] venueOrderIdLengths;
    private final long[] venueOrderIdFingerprints;
    private final long[] venueOrderClOrdIds;
    private final byte[] levelStates;
    private final int[] levelVenueIds;
    private final int[] levelInstrumentIds;
    private final byte[] levelSides;
    private final long[] levelPrices;
    private final long[] levelSizes;
    private final byte[] idScratch = new byte[VenueOrderId.MAX_LENGTH];
    private int orderCount;

    public OwnOrderOverlay() {
        this(DEFAULT_MAX_OWN_ORDERS);
    }

    public OwnOrderOverlay(final int maxOwnOrders) {
        this(maxOwnOrders, OwnOrderOverlay::fingerprint);
    }

    OwnOrderOverlay(final int maxOwnOrders, final FingerprintStrategy fingerprintStrategy) {
        if (maxOwnOrders <= 0) {
            throw new IllegalArgumentException("maxOwnOrders must be positive");
        }
        this.maxOwnOrders = maxOwnOrders;
        this.fingerprintStrategy = Objects.requireNonNull(fingerprintStrategy, "fingerprintStrategy");
        final int capacity = tableCapacity(maxOwnOrders);
        orderMask = capacity - 1;
        venueOrderMask = capacity - 1;
        levelMask = capacity - 1;
        orderStates = new byte[capacity];
        orderClOrdIds = new long[capacity];
        orderVenueIds = new int[capacity];
        orderInstrumentIds = new int[capacity];
        orderSides = new byte[capacity];
        orderPrices = new long[capacity];
        orderRemainingQty = new long[capacity];
        orderHasVenueOrderId = new boolean[capacity];
        orderVenueOrderIdBytes = new byte[capacity * VenueOrderId.MAX_LENGTH];
        orderVenueOrderIdLengths = new int[capacity];
        orderVenueOrderIdFingerprints = new long[capacity];
        venueOrderStates = new byte[capacity];
        venueOrderVenueIds = new int[capacity];
        venueOrderInstrumentIds = new int[capacity];
        venueOrderIdBytes = new byte[capacity * VenueOrderId.MAX_LENGTH];
        venueOrderIdLengths = new int[capacity];
        venueOrderIdFingerprints = new long[capacity];
        venueOrderClOrdIds = new long[capacity];
        levelStates = new byte[capacity];
        levelVenueIds = new int[capacity];
        levelInstrumentIds = new int[capacity];
        levelSides = new byte[capacity];
        levelPrices = new long[capacity];
        levelSizes = new long[capacity];
    }

    /**
     * Upserts or removes an order using current {@link OrderState} fields.
     *
     * @param order live order state from the order manager
     */
    public void updateFrom(final OrderState order) {
        Objects.requireNonNull(order, "order");
        if (!order.isWorkingVisible()) {
            remove(order.clOrdId());
            return;
        }
        upsert(
            order.clOrdId(),
            order.venueOrderId(),
            order.venueId(),
            order.instrumentId(),
            toEntryType(order.side()),
            order.priceScaled(),
            order.leavesQtyScaled(),
            true);
    }

    /**
     * Upserts a visible own order or removes it when it is not working.
     */
    public void upsert(
        final long clOrdId,
        final String venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled,
        final long remainingQtyScaled,
        final boolean working) {

        if (!working || remainingQtyScaled <= 0L) {
            remove(clOrdId);
            return;
        }
        validate(clOrdId, venueId, instrumentId, side, priceScaled, remainingQtyScaled);
        final int previousSlot = findOrder(clOrdId);
        if (previousSlot < 0 && orderCount >= maxOwnOrders) {
            return;
        }
        final int venueOrderIdLength = copyVenueOrderId(venueOrderId);
        final long venueOrderIdFingerprint = venueOrderIdLength > 0
            ? fingerprintStrategy.fingerprint(idScratch, 0, venueOrderIdLength)
            : 0L;
        final byte sideValue = sideValue(side);

        final int slot = previousSlot >= 0 ? previousSlot : findOrderInsertionSlot(clOrdId);
        if (slot < 0) {
            return;
        }
        if (previousSlot >= 0) {
            subtractExisting(slot);
        } else {
            orderStates[slot] = OCCUPIED;
            orderClOrdIds[slot] = clOrdId;
            orderCount++;
        }

        orderVenueIds[slot] = venueId;
        orderInstrumentIds[slot] = instrumentId;
        orderSides[slot] = sideValue;
        orderPrices[slot] = priceScaled;
        orderRemainingQty[slot] = remainingQtyScaled;
        storeOrderVenueOrderId(slot, venueOrderIdLength, venueOrderIdFingerprint);
        adjustLevel(venueId, instrumentId, sideValue, priceScaled, remainingQtyScaled);
        if (venueOrderIdLength > 0) {
            putVenueOrderMapping(venueId, instrumentId, venueOrderIdLength, venueOrderIdFingerprint, clOrdId);
        }
    }

    public void remove(final long clOrdId) {
        final int slot = findOrder(clOrdId);
        if (slot < 0) {
            return;
        }
        subtractExisting(slot);
        orderStates[slot] = DELETED;
        orderClOrdIds[slot] = 0L;
        orderVenueIds[slot] = 0;
        orderInstrumentIds[slot] = 0;
        orderSides[slot] = 0;
        orderPrices[slot] = 0L;
        orderRemainingQty[slot] = 0L;
        orderHasVenueOrderId[slot] = false;
        orderVenueOrderIdLengths[slot] = 0;
        orderVenueOrderIdFingerprints[slot] = 0L;
        orderCount--;
    }

    public void clear() {
        Arrays.fill(orderStates, EMPTY);
        Arrays.fill(venueOrderStates, EMPTY);
        Arrays.fill(levelStates, EMPTY);
        orderCount = 0;
    }

    public long ownSizeAt(
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        if (side != EntryType.BID && side != EntryType.ASK) {
            return 0L;
        }
        final int slot = findLevel(venueId, instrumentId, sideValue(side), priceScaled);
        return slot < 0 ? 0L : levelSizes[slot];
    }

    public long exactOwnSizeByVenueOrderId(
        final String venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        final int length = copyVenueOrderId(venueOrderId);
        if (length <= 0) {
            return 0L;
        }
        return exactOwnSizeByVenueOrderId(idScratch, length,
            fingerprintStrategy.fingerprint(idScratch, 0, length), venueId, instrumentId, side, priceScaled);
    }

    public long exactOwnSizeByVenueOrderId(
        final VenueOrderId venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        if (venueOrderId == null) {
            return 0L;
        }
        venueOrderId.copyTo(idScratch, 0);
        final int length = venueOrderId.length();
        return exactOwnSizeByVenueOrderId(idScratch, length,
            fingerprintStrategy.fingerprint(idScratch, 0, length), venueId, instrumentId, side, priceScaled);
    }

    public boolean isOwnVenueOrder(
        final String venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        return exactOwnSizeByVenueOrderId(venueOrderId, venueId, instrumentId, side, priceScaled) > 0L;
    }

    public boolean wouldSelfCross(
        final int venueId,
        final int instrumentId,
        final EntryType newOrderSide,
        final long priceScaled) {

        for (int slot = 0; slot < orderStates.length; slot++) {
            if (orderStates[slot] != OCCUPIED
                || orderVenueIds[slot] != venueId
                || orderInstrumentIds[slot] != instrumentId) {
                continue;
            }
            if (newOrderSide == EntryType.BID && orderSides[slot] == ASK_SIDE && orderPrices[slot] <= priceScaled) {
                return true;
            }
            if (newOrderSide == EntryType.ASK && orderSides[slot] == BID_SIDE && orderPrices[slot] >= priceScaled) {
                return true;
            }
        }
        return false;
    }

    public boolean wouldSelfCrossBeyondExternal(
        final int venueId,
        final int instrumentId,
        final EntryType newOrderSide,
        final long priceScaled,
        final long externalSizeAtPrice) {

        for (int slot = 0; slot < orderStates.length; slot++) {
            if (orderStates[slot] != OCCUPIED
                || orderVenueIds[slot] != venueId
                || orderInstrumentIds[slot] != instrumentId) {
                continue;
            }
            if (newOrderSide == EntryType.BID && orderSides[slot] == ASK_SIDE) {
                if (orderPrices[slot] < priceScaled || (orderPrices[slot] == priceScaled && externalSizeAtPrice <= 0L)) {
                    return true;
                }
            }
            if (newOrderSide == EntryType.ASK && orderSides[slot] == BID_SIDE) {
                if (orderPrices[slot] > priceScaled || (orderPrices[slot] == priceScaled && externalSizeAtPrice <= 0L)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int orderCount() {
        return orderCount;
    }

    private long exactOwnSizeByVenueOrderId(
        final byte[] source,
        final int length,
        final long fingerprint,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        if (side != EntryType.BID && side != EntryType.ASK) {
            return 0L;
        }
        final int venueSlot = findVenueOrder(venueId, instrumentId, source, length, fingerprint);
        if (venueSlot < 0) {
            return 0L;
        }
        final int orderSlot = findOrder(venueOrderClOrdIds[venueSlot]);
        if (orderSlot < 0
            || orderVenueIds[orderSlot] != venueId
            || orderInstrumentIds[orderSlot] != instrumentId
            || orderSides[orderSlot] != sideValue(side)
            || orderPrices[orderSlot] != priceScaled) {
            return 0L;
        }
        return orderRemainingQty[orderSlot];
    }

    private void subtractExisting(final int slot) {
        adjustLevel(orderVenueIds[slot], orderInstrumentIds[slot], orderSides[slot], orderPrices[slot],
            -orderRemainingQty[slot]);
        if (orderHasVenueOrderId[slot]) {
            final int offset = orderVenueOrderIdOffset(slot);
            removeVenueOrderMapping(
                orderVenueIds[slot],
                orderInstrumentIds[slot],
                orderVenueOrderIdBytes,
                offset,
                orderVenueOrderIdLengths[slot],
                orderVenueOrderIdFingerprints[slot],
                orderClOrdIds[slot]);
        }
    }

    private void storeOrderVenueOrderId(final int slot, final int length, final long fingerprint) {
        orderHasVenueOrderId[slot] = length > 0;
        orderVenueOrderIdLengths[slot] = length;
        orderVenueOrderIdFingerprints[slot] = fingerprint;
        if (length > 0) {
            System.arraycopy(idScratch, 0, orderVenueOrderIdBytes, orderVenueOrderIdOffset(slot), length);
        }
    }

    private void adjustLevel(
        final int venueId,
        final int instrumentId,
        final byte side,
        final long priceScaled,
        final long delta) {

        final int existing = findLevel(venueId, instrumentId, side, priceScaled);
        final long next = (existing < 0 ? 0L : levelSizes[existing]) + delta;
        if (next <= 0L) {
            if (existing >= 0) {
                levelStates[existing] = DELETED;
                levelSizes[existing] = 0L;
            }
            return;
        }
        final int slot = existing >= 0 ? existing : findLevelInsertionSlot(venueId, instrumentId, side, priceScaled);
        if (slot >= 0) {
            levelStates[slot] = OCCUPIED;
            levelVenueIds[slot] = venueId;
            levelInstrumentIds[slot] = instrumentId;
            levelSides[slot] = side;
            levelPrices[slot] = priceScaled;
            levelSizes[slot] = next;
        }
    }

    private int findOrder(final long clOrdId) {
        int slot = spread(clOrdId) & orderMask;
        for (int probes = 0; probes < orderStates.length; probes++) {
            final byte state = orderStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED && orderClOrdIds[slot] == clOrdId) {
                return slot;
            }
            slot = (slot + 1) & orderMask;
        }
        return -1;
    }

    private int findOrderInsertionSlot(final long clOrdId) {
        int slot = spread(clOrdId) & orderMask;
        int firstDeleted = -1;
        for (int probes = 0; probes < orderStates.length; probes++) {
            final byte state = orderStates[slot];
            if (state == EMPTY) {
                return firstDeleted >= 0 ? firstDeleted : slot;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED && orderClOrdIds[slot] == clOrdId) {
                return slot;
            }
            slot = (slot + 1) & orderMask;
        }
        return firstDeleted;
    }

    private void putVenueOrderMapping(
        final int venueId,
        final int instrumentId,
        final int length,
        final long fingerprint,
        final long clOrdId) {

        final int slot = findVenueOrderInsertionSlot(venueId, instrumentId, idScratch, 0, length, fingerprint);
        if (slot < 0) {
            return;
        }
        venueOrderStates[slot] = OCCUPIED;
        venueOrderVenueIds[slot] = venueId;
        venueOrderInstrumentIds[slot] = instrumentId;
        venueOrderIdLengths[slot] = length;
        venueOrderIdFingerprints[slot] = fingerprint;
        venueOrderClOrdIds[slot] = clOrdId;
        System.arraycopy(idScratch, 0, venueOrderIdBytes, venueOrderIdOffset(slot), length);
    }

    private void removeVenueOrderMapping(
        final int venueId,
        final int instrumentId,
        final byte[] source,
        final int sourceOffset,
        final int length,
        final long fingerprint,
        final long clOrdId) {

        final int slot = findVenueOrder(venueId, instrumentId, source, sourceOffset, length, fingerprint);
        if (slot >= 0 && venueOrderClOrdIds[slot] == clOrdId) {
            venueOrderStates[slot] = DELETED;
            venueOrderClOrdIds[slot] = 0L;
            venueOrderIdLengths[slot] = 0;
            venueOrderIdFingerprints[slot] = 0L;
        }
    }

    private int findVenueOrder(
        final int venueId,
        final int instrumentId,
        final byte[] source,
        final int length,
        final long fingerprint) {

        return findVenueOrder(venueId, instrumentId, source, 0, length, fingerprint);
    }

    private int findVenueOrder(
        final int venueId,
        final int instrumentId,
        final byte[] source,
        final int sourceOffset,
        final int length,
        final long fingerprint) {

        int slot = venueOrderSlot(venueId, instrumentId, fingerprint);
        for (int probes = 0; probes < venueOrderStates.length; probes++) {
            final byte state = venueOrderStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED
                && venueOrderVenueIds[slot] == venueId
                && venueOrderInstrumentIds[slot] == instrumentId
                && venueOrderIdFingerprints[slot] == fingerprint
                && venueOrderIdLengths[slot] == length
                && venueOrderIdEquals(slot, source, sourceOffset, length)) {
                return slot;
            }
            slot = (slot + 1) & venueOrderMask;
        }
        return -1;
    }

    private int findVenueOrderInsertionSlot(
        final int venueId,
        final int instrumentId,
        final byte[] source,
        final int sourceOffset,
        final int length,
        final long fingerprint) {

        int slot = venueOrderSlot(venueId, instrumentId, fingerprint);
        int firstDeleted = -1;
        for (int probes = 0; probes < venueOrderStates.length; probes++) {
            final byte state = venueOrderStates[slot];
            if (state == EMPTY) {
                return firstDeleted >= 0 ? firstDeleted : slot;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED
                && venueOrderVenueIds[slot] == venueId
                && venueOrderInstrumentIds[slot] == instrumentId
                && venueOrderIdFingerprints[slot] == fingerprint
                && venueOrderIdLengths[slot] == length
                && venueOrderIdEquals(slot, source, sourceOffset, length)) {
                return slot;
            }
            slot = (slot + 1) & venueOrderMask;
        }
        return firstDeleted;
    }

    private boolean venueOrderIdEquals(final int slot, final byte[] source, final int sourceOffset, final int length) {
        final int offset = venueOrderIdOffset(slot);
        for (int i = 0; i < length; i++) {
            if (venueOrderIdBytes[offset + i] != source[sourceOffset + i]) {
                return false;
            }
        }
        return true;
    }

    private int findLevel(final int venueId, final int instrumentId, final byte side, final long priceScaled) {
        int slot = levelSlot(venueId, instrumentId, side, priceScaled);
        for (int probes = 0; probes < levelStates.length; probes++) {
            final byte state = levelStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED
                && levelVenueIds[slot] == venueId
                && levelInstrumentIds[slot] == instrumentId
                && levelSides[slot] == side
                && levelPrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & levelMask;
        }
        return -1;
    }

    private int findLevelInsertionSlot(final int venueId, final int instrumentId, final byte side, final long priceScaled) {
        int slot = levelSlot(venueId, instrumentId, side, priceScaled);
        int firstDeleted = -1;
        for (int probes = 0; probes < levelStates.length; probes++) {
            final byte state = levelStates[slot];
            if (state == EMPTY) {
                return firstDeleted >= 0 ? firstDeleted : slot;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED
                && levelVenueIds[slot] == venueId
                && levelInstrumentIds[slot] == instrumentId
                && levelSides[slot] == side
                && levelPrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & levelMask;
        }
        return firstDeleted;
    }

    private int copyVenueOrderId(final String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        final int length = value.length();
        if (length > VenueOrderId.MAX_LENGTH) {
            throw new IllegalArgumentException("venue order id exceeds max length " + VenueOrderId.MAX_LENGTH);
        }
        for (int i = 0; i < length; i++) {
            idScratch[i] = (byte)value.charAt(i);
        }
        return length;
    }

    private int orderVenueOrderIdOffset(final int slot) {
        return slot * VenueOrderId.MAX_LENGTH;
    }

    private int venueOrderIdOffset(final int slot) {
        return slot * VenueOrderId.MAX_LENGTH;
    }

    private int venueOrderSlot(final int venueId, final int instrumentId, final long fingerprint) {
        return spread((((long)venueId) << 32) ^ (((long)instrumentId) << 16) ^ fingerprint) & venueOrderMask;
    }

    private int levelSlot(final int venueId, final int instrumentId, final byte side, final long priceScaled) {
        return spread((((long)venueId) << 32) ^ (((long)instrumentId) << 16) ^ priceScaled
            ^ (side * 0x9e3779b97f4a7c15L)) & levelMask;
    }

    private static void validate(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled,
        final long remainingQtyScaled) {

        if (clOrdId <= 0L || venueId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException("own order identity must be positive");
        }
        if (side != EntryType.BID && side != EntryType.ASK) {
            throw new IllegalArgumentException("own order side must be BID or ASK");
        }
        if (priceScaled <= 0L || remainingQtyScaled <= 0L) {
            throw new IllegalArgumentException("own order price and remaining quantity must be positive");
        }
    }

    private static EntryType toEntryType(final byte side) {
        return Side.get(side) == Side.BUY ? EntryType.BID : EntryType.ASK;
    }

    private static byte sideValue(final EntryType side) {
        return side == EntryType.BID ? BID_SIDE : ASK_SIDE;
    }

    private static int tableCapacity(final int maxEntries) {
        int capacity = 1;
        final int target = Math.max(2, maxEntries * 2);
        while (capacity < target) {
            capacity <<= 1;
        }
        return capacity;
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
