package ig.rueishi.nitroj.exchange.gateway.marketdata;

import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;

/**
 * Mutable context for one normalized L3 order-level market-data entry.
 *
 * <p>Responsibility: carries standard FIX L3 fields such as side, action,
 * price, size, and MDEntryID through parsing and venue enrichment. Role in
 * system: {@link AbstractFixL3MarketDataNormalizer} uses this context before
 * publishing {@code MarketByOrderEvent} and before L3 book aggregation derives
 * L2 updates. Relationships: venue plugins may override enrichment or identity
 * hooks to account for nonstandard order identifiers. Lifecycle: intended for
 * reuse by a single normalizer on the Artio library thread. Design intent:
 * keep order-level state explicit and separate from L2 price-level contexts.
 */
public final class L3MarketDataContext {
    public long ingressNanos;
    public int venueId;
    public int instrumentId;
    public int fixSeqNum;
    public String symbol;
    public Side side = Side.NULL_VAL;
    public UpdateAction updateAction = UpdateAction.NEW;
    public long priceScaled;
    public long sizeScaled;
    public long exchangeTimestampNanos;
    public String mdEntryId;
    public String mdEntryRefId;

    /**
     * Clears entry-specific state before parsing the next L3 entry.
     */
    public void clearEntry() {
        instrumentId = 0;
        symbol = null;
        side = Side.NULL_VAL;
        updateAction = UpdateAction.NEW;
        priceScaled = 0L;
        sizeScaled = 0L;
        exchangeTimestampNanos = 0L;
        mdEntryId = null;
        mdEntryRefId = null;
    }
}
