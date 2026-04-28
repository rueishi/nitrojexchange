package ig.rueishi.nitroj.exchange.cluster;

/**
 * Mutable portfolio state for one venue/instrument pair.
 *
 * <p>The portfolio engine owns instances of this class inside a primitive-keyed
 * map. Each instance is created during startup via {@link PortfolioEngine#initPosition(int, int)}
 * or during snapshot load, then mutated on the single cluster-service thread as
 * fills arrive. Keeping the fields flat and primitive lets the engine update
 * position, average entry, and PnL without allocating per fill.</p>
 */
public final class Position {
    final int venueId;
    final int instrumentId;
    long netQtyScaled;
    long avgEntryPriceScaled;
    long realizedPnlScaled;
    long unrealizedPnlScaled;

    public Position(final int venueId, final int instrumentId) {
        this.venueId = venueId;
        this.instrumentId = instrumentId;
    }
}
