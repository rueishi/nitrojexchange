package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable market-making strategy configuration loaded from cluster TOML.
 *
 * <p>Responsibility: stores all scalar parameters required by the market-making
 * strategy without further TOML lookups. Role in system: cluster startup injects
 * this record into the strategy when the strategy is enabled. Relationships:
 * numeric quantities are scaled by {@link ConfigManager#parseScaled(String)} so
 * strategy logic can operate entirely on fixed-point longs. Lifecycle: created
 * once during {@link ConfigManager#loadCluster(String)} and read repeatedly in
 * the cluster service hot path. Design intent: a flat immutable record keeps the
 * trading loop allocation-free and prevents configuration drift after startup.
 */
public record MarketMakingConfig(
    int instrumentId,
    int venueId,
    long targetSpreadBps,
    long inventorySkewFactorBps,
    long baseQuoteSizeScaled,
    long maxQuoteSizeScaled,
    long maxPositionLongScaled,
    long maxPositionShortScaled,
    long refreshThresholdBps,
    long maxQuoteAgeMicros,
    long marketDataStalenessThresholdMicros,
    long wideSpreadThresholdBps,
    long maxTolerableSpreadBps,
    long tickSizeScaled,
    long lotSizeScaled,
    long minQuoteSizeFractionBps,
    ExecutionStrategySelectionConfig executionStrategy
) {
    public MarketMakingConfig(
        final int instrumentId,
        final int venueId,
        final long targetSpreadBps,
        final long inventorySkewFactorBps,
        final long baseQuoteSizeScaled,
        final long maxQuoteSizeScaled,
        final long maxPositionLongScaled,
        final long maxPositionShortScaled,
        final long refreshThresholdBps,
        final long maxQuoteAgeMicros,
        final long marketDataStalenessThresholdMicros,
        final long wideSpreadThresholdBps,
        final long maxTolerableSpreadBps,
        final long tickSizeScaled,
        final long lotSizeScaled,
        final long minQuoteSizeFractionBps
    ) {
        this(instrumentId, venueId, targetSpreadBps, inventorySkewFactorBps, baseQuoteSizeScaled,
            maxQuoteSizeScaled, maxPositionLongScaled, maxPositionShortScaled, refreshThresholdBps,
            maxQuoteAgeMicros, marketDataStalenessThresholdMicros, wideSpreadThresholdBps,
            maxTolerableSpreadBps, tickSizeScaled, lotSizeScaled, minQuoteSizeFractionBps,
            new ExecutionStrategySelectionConfig(
                ExecutionStrategyIds.POST_ONLY_QUOTE,
                new ExecutionStrategyOverrideConfig[0]));
    }
}
