package ig.rueishi.nitroj.exchange.gateway.marketdata;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;

/**
 * Mutable context for one normalized L2 market-data entry.
 *
 * <p>Responsibility: carries parsed standard FIX L2 fields through shared
 * normalization and venue-specific enrichment. Role in system: an
 * {@link AbstractFixL2MarketDataNormalizer} creates or reuses this context for
 * each bid/ask/trade entry before publishing a MarketDataEvent. Relationships:
 * venue normalizers may override {@code enrich(...)} to adjust or add semantics
 * without reimplementing the parser. Lifecycle: hot-path object intended for
 * reuse by a single normalizer on the Artio library thread. Design intent:
 * provide an explicit, testable data carrier instead of passing a long list of
 * primitive parser locals through enrichment hooks.
 */
public final class L2MarketDataContext {
    public long ingressNanos;
    public int venueId;
    public int instrumentId;
    public int fixSeqNum;
    public String symbol;
    public EntryType entryType = EntryType.NULL_VAL;
    public UpdateAction updateAction = UpdateAction.NEW;
    public long priceScaled;
    public long sizeScaled;
    public int priceLevel;
    public long exchangeTimestampNanos;

    /**
     * Resets all entry fields before parsing a new L2 entry.
     */
    public void clearEntry() {
        instrumentId = 0;
        symbol = null;
        entryType = EntryType.NULL_VAL;
        updateAction = UpdateAction.NEW;
        priceScaled = 0L;
        sizeScaled = 0L;
        priceLevel = 0;
        exchangeTimestampNanos = 0L;
    }
}
