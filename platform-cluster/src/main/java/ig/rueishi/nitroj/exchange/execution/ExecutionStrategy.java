package ig.rueishi.nitroj.exchange.execution;

/**
 * Cluster-thread execution strategy plugin contract.
 *
 * <p>Trading strategies submit parent intents. Execution strategies own how
 * those intents become child orders, cancels, timers, fills, and terminal
 * parent outcomes. Every callback runs on the deterministic cluster service
 * thread and must avoid blocking work, wall-clock reads, formatted logging for
 * expected outcomes, and hot-path allocation.</p>
 */
public interface ExecutionStrategy {
    int executionStrategyId();

    void init(ExecutionStrategyContext ctx);

    void onParentIntent(ParentOrderIntentView intent);

    void onMarketDataTick(int venueId, int instrumentId, long clusterTimeMicros);

    void onChildExecution(ChildExecutionView execution);

    void onTimer(long correlationId);

    void onCancel(long parentOrderId, byte reasonCode);
}
