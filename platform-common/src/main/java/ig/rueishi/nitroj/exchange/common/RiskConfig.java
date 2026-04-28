package ig.rueishi.nitroj.exchange.common;

import java.util.Map;

/**
 * Immutable cluster risk-limit configuration.
 *
 * <p>Responsibility: stores global and per-instrument limits parsed from the
 * cluster TOML. Role in system: the future risk engine consumes this record at
 * startup and never reads TOML directly. Relationships: per-instrument entries
 * are keyed by immutable instrument ID from {@link InstrumentConfig}. Lifecycle:
 * created by {@link ConfigManager#loadCluster(String)} before business logic is
 * wired. Design intent: represent all financial limits as scaled longs so risk
 * checks avoid floating point and stay deterministic in the clustered service.
 *
 * @param maxOrdersPerSecond global order-rate limit
 * @param maxDailyLossUsd scaled global loss limit
 * @param instruments per-instrument limits keyed by instrument ID
 */
public record RiskConfig(
    int maxOrdersPerSecond,
    long maxDailyLossUsd,
    Map<Integer, InstrumentRisk> instruments
) {
    /**
     * Creates the risk config with an immutable instrument map.
     */
    public RiskConfig {
        instruments = Map.copyOf(instruments);
    }

    /**
     * Per-instrument risk limits parsed from {@code [risk.instrument.<id>]}.
     *
     * @param instrumentId immutable instrument ID
     * @param maxOrderSizeScaled scaled maximum single-order size
     * @param maxLongPositionScaled scaled maximum long position
     * @param maxShortPositionScaled scaled maximum short position
     * @param maxNotionalUsdScaled scaled maximum notional exposure
     * @param softLimitPct soft-alert percentage of hard limits
     */
    public record InstrumentRisk(
        int instrumentId,
        long maxOrderSizeScaled,
        long maxLongPositionScaled,
        long maxShortPositionScaled,
        long maxNotionalUsdScaled,
        int softLimitPct
    ) {
    }
}
