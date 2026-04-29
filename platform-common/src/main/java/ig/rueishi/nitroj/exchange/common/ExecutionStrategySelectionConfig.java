package ig.rueishi.nitroj.exchange.common;

import java.util.Arrays;

/**
 * Immutable V13 execution-strategy selection loaded during startup.
 */
public record ExecutionStrategySelectionConfig(
    int executionStrategyId,
    ExecutionStrategyOverrideConfig[] overrides
) {
    public ExecutionStrategySelectionConfig {
        overrides = Arrays.copyOf(overrides, overrides.length);
    }

    @Override
    public ExecutionStrategyOverrideConfig[] overrides() {
        return Arrays.copyOf(overrides, overrides.length);
    }

    public int overrideCount() {
        return overrides.length;
    }
}
