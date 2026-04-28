package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;

/**
 * Cross-venue aggregate L2 book for one instrument.
 *
 * <p>Responsibility: tracks each venue's contribution at a price level and
 * exposes consolidated best bid/ask across all venues for one instrument. Role
 * in system: {@link InternalMarketView} updates this book after applying each
 * per-venue L2 update, giving strategies an optional market-wide reference view
 * without replacing venue-specific executable books. Relationships: consumes
 * normalized L2 price-level updates and preserves venue contribution boundaries
 * so one venue delete cannot erase another venue's liquidity at the same price.
 * Lifecycle: created lazily per instrument as market data arrives. Design
 * intent: support fair-price, hedging, and arbitrage reads while keeping
 * per-venue books authoritative for order placement. V12 stores venue
 * contributions and aggregate levels in bounded primitive tables: updates never
 * allocate after construction, deleting one venue removes only that venue's
 * contribution, and best-price queries scan the deterministic table contents
 * without depending on hash-map iteration order.
 */
public final class ConsolidatedL2Book {
    public static final int DEFAULT_MAX_CONTRIBUTIONS = 10_000;
    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;
    private static final byte BID_SIDE = 1;
    private static final byte ASK_SIDE = 2;

    private final int instrumentId;
    private final int maxContributions;
    private final int contributionMask;
    private final int aggregateMask;
    private final byte[] contributionStates;
    private final int[] contributionVenueIds;
    private final byte[] contributionSides;
    private final long[] contributionPrices;
    private final long[] contributionSizes;
    private final byte[] aggregateStates;
    private final byte[] aggregateSides;
    private final long[] aggregatePrices;
    private final long[] aggregateSizes;
    private int contributionCount;

    public ConsolidatedL2Book(final int instrumentId) {
        this(instrumentId, DEFAULT_MAX_CONTRIBUTIONS);
    }

    public ConsolidatedL2Book(final int instrumentId, final int maxContributions) {
        if (maxContributions <= 0) {
            throw new IllegalArgumentException("maxContributions must be positive");
        }
        this.instrumentId = instrumentId;
        this.maxContributions = maxContributions;
        final int contributionCapacity = tableCapacity(maxContributions);
        final int aggregateCapacity = tableCapacity(maxContributions);
        contributionMask = contributionCapacity - 1;
        aggregateMask = aggregateCapacity - 1;
        contributionStates = new byte[contributionCapacity];
        contributionVenueIds = new int[contributionCapacity];
        contributionSides = new byte[contributionCapacity];
        contributionPrices = new long[contributionCapacity];
        contributionSizes = new long[contributionCapacity];
        aggregateStates = new byte[aggregateCapacity];
        aggregateSides = new byte[aggregateCapacity];
        aggregatePrices = new long[aggregateCapacity];
        aggregateSizes = new long[aggregateCapacity];
    }

    public void applyVenueLevel(
        final int venueId,
        final EntryType side,
        final long priceScaled,
        final long sizeScaled) {

        if (side != EntryType.BID && side != EntryType.ASK) {
            return;
        }
        final byte sideValue = sideValue(side);

        final int contributionSlot = findContribution(venueId, sideValue, priceScaled);
        final long previous = contributionSlot < 0 ? 0L : contributionSizes[contributionSlot];
        if (contributionSlot < 0 && sizeScaled > 0L && contributionCount >= maxContributions) {
            return;
        }
        if (sizeScaled <= 0L) {
            if (contributionSlot >= 0) {
                removeContribution(contributionSlot);
            }
        } else {
            final int slot = contributionSlot >= 0
                ? contributionSlot
                : findContributionInsertionSlot(venueId, sideValue, priceScaled);
            if (slot < 0) {
                return;
            }
            if (contributionSlot < 0) {
                contributionStates[slot] = OCCUPIED;
                contributionVenueIds[slot] = venueId;
                contributionSides[slot] = sideValue;
                contributionPrices[slot] = priceScaled;
                contributionCount++;
            }
            contributionSizes[slot] = sizeScaled;
        }

        final long next = aggregateSize(sideValue, priceScaled) - previous + Math.max(sizeScaled, 0L);
        if (next <= 0L) {
            final int aggregateSlot = findAggregate(sideValue, priceScaled);
            if (aggregateSlot >= 0) {
                removeAggregate(aggregateSlot);
            }
        } else {
            final int aggregateSlot = findOrInsertAggregate(sideValue, priceScaled);
            if (aggregateSlot >= 0) {
                aggregateSizes[aggregateSlot] = next;
            }
        }
    }

    public int instrumentId() {
        return instrumentId;
    }

    public long bestBid() {
        long best = Ids.INVALID_PRICE;
        for (int slot = 0; slot < aggregateStates.length; slot++) {
            if (aggregateStates[slot] == OCCUPIED
                && aggregateSides[slot] == BID_SIDE
                && (best == Ids.INVALID_PRICE || aggregatePrices[slot] > best)) {
                best = aggregatePrices[slot];
            }
        }
        return best;
    }

    public long bestAsk() {
        long best = Ids.INVALID_PRICE;
        for (int slot = 0; slot < aggregateStates.length; slot++) {
            if (aggregateStates[slot] == OCCUPIED
                && aggregateSides[slot] == ASK_SIDE
                && (best == Ids.INVALID_PRICE || aggregatePrices[slot] < best)) {
                best = aggregatePrices[slot];
            }
        }
        return best;
    }

    public long sizeAt(final EntryType side, final long priceScaled) {
        if (side != EntryType.BID && side != EntryType.ASK) {
            return 0L;
        }
        return aggregateSize(sideValue(side), priceScaled);
    }

    private long aggregateSize(final byte side, final long priceScaled) {
        final int slot = findAggregate(side, priceScaled);
        return slot < 0 ? 0L : aggregateSizes[slot];
    }

    private int findContribution(final int venueId, final byte side, final long priceScaled) {
        int slot = contributionSlot(venueId, side, priceScaled);
        for (int probes = 0; probes < contributionStates.length; probes++) {
            final byte state = contributionStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED
                && contributionVenueIds[slot] == venueId
                && contributionSides[slot] == side
                && contributionPrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & contributionMask;
        }
        return -1;
    }

    private int findContributionInsertionSlot(final int venueId, final byte side, final long priceScaled) {
        int slot = contributionSlot(venueId, side, priceScaled);
        int firstDeleted = -1;
        for (int probes = 0; probes < contributionStates.length; probes++) {
            final byte state = contributionStates[slot];
            if (state == EMPTY) {
                return firstDeleted >= 0 ? firstDeleted : slot;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED
                && contributionVenueIds[slot] == venueId
                && contributionSides[slot] == side
                && contributionPrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & contributionMask;
        }
        return firstDeleted;
    }

    private void removeContribution(final int slot) {
        contributionStates[slot] = DELETED;
        contributionVenueIds[slot] = 0;
        contributionSides[slot] = 0;
        contributionPrices[slot] = 0L;
        contributionSizes[slot] = 0L;
        contributionCount--;
    }

    private int findAggregate(final byte side, final long priceScaled) {
        int slot = aggregateSlot(side, priceScaled);
        for (int probes = 0; probes < aggregateStates.length; probes++) {
            final byte state = aggregateStates[slot];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED && aggregateSides[slot] == side && aggregatePrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & aggregateMask;
        }
        return -1;
    }

    private int findOrInsertAggregate(final byte side, final long priceScaled) {
        int slot = aggregateSlot(side, priceScaled);
        int firstDeleted = -1;
        for (int probes = 0; probes < aggregateStates.length; probes++) {
            final byte state = aggregateStates[slot];
            if (state == EMPTY) {
                final int target = firstDeleted >= 0 ? firstDeleted : slot;
                aggregateStates[target] = OCCUPIED;
                aggregateSides[target] = side;
                aggregatePrices[target] = priceScaled;
                return target;
            }
            if (state == DELETED && firstDeleted < 0) {
                firstDeleted = slot;
            } else if (state == OCCUPIED && aggregateSides[slot] == side && aggregatePrices[slot] == priceScaled) {
                return slot;
            }
            slot = (slot + 1) & aggregateMask;
        }
        if (firstDeleted >= 0) {
            aggregateStates[firstDeleted] = OCCUPIED;
            aggregateSides[firstDeleted] = side;
            aggregatePrices[firstDeleted] = priceScaled;
        }
        return firstDeleted;
    }

    private void removeAggregate(final int slot) {
        aggregateStates[slot] = DELETED;
        aggregateSides[slot] = 0;
        aggregatePrices[slot] = 0L;
        aggregateSizes[slot] = 0L;
    }

    private int contributionSlot(final int venueId, final byte side, final long priceScaled) {
        return spread((((long)venueId) << 32) ^ priceScaled ^ (side * 0x9e3779b97f4a7c15L)) & contributionMask;
    }

    private int aggregateSlot(final byte side, final long priceScaled) {
        return spread(priceScaled ^ (side * 0x9e3779b97f4a7c15L)) & aggregateMask;
    }

    private static byte sideValue(final EntryType side) {
        return side == EntryType.BID ? BID_SIDE : ASK_SIDE;
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
}
