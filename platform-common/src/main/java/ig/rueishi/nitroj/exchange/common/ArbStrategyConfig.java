package ig.rueishi.nitroj.exchange.common;

import java.util.Arrays;

/**
 * Immutable arbitrage strategy configuration loaded from cluster TOML.
 *
 * <p>Responsibility: carries the cross-venue arbitrage limits, fees, and timing
 * parameters specified in Section 6.2.1. Role in system: cluster startup injects
 * this record into the arbitrage strategy when enabled. Relationships: per-venue
 * arrays are indexed by venue ID as required by the loader contract, leaving
 * absent venues zero-filled. Lifecycle: created once during
 * {@link ConfigManager#loadCluster(String)}. Design intent: defensive copies keep
 * the record immutable even though Java arrays are mutable by default.
 *
 * @param instrumentId instrument ID traded by the strategy
 * @param venueIds configured venue IDs, preserving TOML order
 * @param minNetProfitBps minimum net edge in basis points
 * @param takerFeeScaled taker fee by venue ID, scaled by 1e8
 * @param baseSlippageBps base slippage by venue ID
 * @param slippageSlopeBps slippage slope by venue ID
 * @param referenceSize scaled reference size for slippage
 * @param maxArbPositionScaled scaled maximum arbitrage position
 * @param maxLegSubmissionGapMicros maximum gap between leg submissions
 * @param legTimeoutClusterMicros cluster-time timeout for leg completion
 * @param cooldownAfterFailureMicros cooldown after failed attempts
 */
public record ArbStrategyConfig(
    int instrumentId,
    int[] venueIds,
    long minNetProfitBps,
    long[] takerFeeScaled,
    long[] baseSlippageBps,
    long[] slippageSlopeBps,
    long referenceSize,
    long maxArbPositionScaled,
    long maxLegSubmissionGapMicros,
    long legTimeoutClusterMicros,
    long cooldownAfterFailureMicros
) {
    public ArbStrategyConfig {
        venueIds = Arrays.copyOf(venueIds, venueIds.length);
        takerFeeScaled = Arrays.copyOf(takerFeeScaled, takerFeeScaled.length);
        baseSlippageBps = Arrays.copyOf(baseSlippageBps, baseSlippageBps.length);
        slippageSlopeBps = Arrays.copyOf(slippageSlopeBps, slippageSlopeBps.length);
    }

    @Override
    public int[] venueIds() {
        return Arrays.copyOf(venueIds, venueIds.length);
    }

    @Override
    public long[] takerFeeScaled() {
        return Arrays.copyOf(takerFeeScaled, takerFeeScaled.length);
    }

    @Override
    public long[] baseSlippageBps() {
        return Arrays.copyOf(baseSlippageBps, baseSlippageBps.length);
    }

    @Override
    public long[] slippageSlopeBps() {
        return Arrays.copyOf(slippageSlopeBps, slippageSlopeBps.length);
    }
}
