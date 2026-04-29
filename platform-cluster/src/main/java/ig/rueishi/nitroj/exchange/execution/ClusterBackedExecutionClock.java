package ig.rueishi.nitroj.exchange.execution;

import io.aeron.cluster.service.Cluster;

/**
 * Mutable cluster-clock and timer adapter for execution strategies.
 *
 * <p>Cluster startup builds execution strategy dependencies before Aeron
 * supplies the real {@link Cluster} facade. This adapter lets the context be
 * constructed once, then receive the live cluster reference during
 * {@code TradingClusteredService.onStart()} and warmup shim installation.</p>
 */
public final class ClusterBackedExecutionClock
    implements ExecutionStrategyContext.DeterministicClock, ExecutionStrategyContext.TimerScheduler {
    private Cluster cluster;
    private ExecutionStrategyEngine executionStrategyEngine;

    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    public void setExecutionStrategyEngine(final ExecutionStrategyEngine executionStrategyEngine) {
        this.executionStrategyEngine = executionStrategyEngine;
    }

    @Override
    public long clusterTimeMicros() {
        return cluster == null ? 0L : cluster.time();
    }

    @Override
    public boolean scheduleTimer(final long correlationId, final long deadlineClusterMicros) {
        return cluster != null && cluster.scheduleTimer(correlationId, deadlineClusterMicros);
    }

    @Override
    public boolean scheduleTimer(
        final long correlationId,
        final long deadlineClusterMicros,
        final int executionStrategyId) {
        if (executionStrategyEngine == null) {
            return scheduleTimer(correlationId, deadlineClusterMicros);
        }
        if (!executionStrategyEngine.registerTimerOwner(correlationId, executionStrategyId)) {
            return false;
        }
        if (!scheduleTimer(correlationId, deadlineClusterMicros)) {
            executionStrategyEngine.unregisterTimerOwner(correlationId);
            return false;
        }
        return true;
    }
}
