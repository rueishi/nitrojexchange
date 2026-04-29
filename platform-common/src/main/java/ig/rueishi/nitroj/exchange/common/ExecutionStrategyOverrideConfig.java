package ig.rueishi.nitroj.exchange.common;

/**
 * Startup-only execution-strategy override for one instrument, venue, or pair.
 */
public record ExecutionStrategyOverrideConfig(
    int instrumentId,
    int venueId,
    int executionStrategyId
) {
    public static final int ANY_INSTRUMENT = 0;
    public static final int ANY_VENUE = 0;
}
