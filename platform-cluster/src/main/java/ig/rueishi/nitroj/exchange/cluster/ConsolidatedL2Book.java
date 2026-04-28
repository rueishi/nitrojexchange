package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;

import java.util.HashMap;
import java.util.Map;

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
 * per-venue books authoritative for order placement.
 */
public final class ConsolidatedL2Book {
    public static final int DEFAULT_MAX_CONTRIBUTIONS = 10_000;
    private final int instrumentId;
    private final int maxContributions;
    private final Map<ContributionKey, Long> contributions = new HashMap<>();
    private final Map<LevelKey, Long> aggregate = new HashMap<>();

    public ConsolidatedL2Book(final int instrumentId) {
        this(instrumentId, DEFAULT_MAX_CONTRIBUTIONS);
    }

    public ConsolidatedL2Book(final int instrumentId, final int maxContributions) {
        if (maxContributions <= 0) {
            throw new IllegalArgumentException("maxContributions must be positive");
        }
        this.instrumentId = instrumentId;
        this.maxContributions = maxContributions;
    }

    public void applyVenueLevel(
        final int venueId,
        final EntryType side,
        final long priceScaled,
        final long sizeScaled) {

        if (side != EntryType.BID && side != EntryType.ASK) {
            return;
        }

        final ContributionKey contributionKey = new ContributionKey(venueId, side, priceScaled);
        final long previous = contributions.getOrDefault(contributionKey, 0L);
        if (previous == 0L && sizeScaled > 0L && contributions.size() >= maxContributions) {
            return;
        }
        if (sizeScaled == 0L) {
            contributions.remove(contributionKey);
        } else {
            contributions.put(contributionKey, sizeScaled);
        }

        final LevelKey levelKey = new LevelKey(side, priceScaled);
        final long next = aggregate.getOrDefault(levelKey, 0L) - previous + sizeScaled;
        if (next <= 0L) {
            aggregate.remove(levelKey);
        } else {
            aggregate.put(levelKey, next);
        }
    }

    public int instrumentId() {
        return instrumentId;
    }

    public long bestBid() {
        long best = Ids.INVALID_PRICE;
        for (LevelKey key : aggregate.keySet()) {
            if (key.side == EntryType.BID && (best == Ids.INVALID_PRICE || key.priceScaled > best)) {
                best = key.priceScaled;
            }
        }
        return best;
    }

    public long bestAsk() {
        long best = Ids.INVALID_PRICE;
        for (LevelKey key : aggregate.keySet()) {
            if (key.side == EntryType.ASK && (best == Ids.INVALID_PRICE || key.priceScaled < best)) {
                best = key.priceScaled;
            }
        }
        return best;
    }

    public long sizeAt(final EntryType side, final long priceScaled) {
        return aggregate.getOrDefault(new LevelKey(side, priceScaled), 0L);
    }

    private record ContributionKey(int venueId, EntryType side, long priceScaled) {
    }

    private record LevelKey(EntryType side, long priceScaled) {
    }
}
