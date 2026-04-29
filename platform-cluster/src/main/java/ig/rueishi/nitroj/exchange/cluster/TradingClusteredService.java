package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.execution.ClusterBackedExecutionClock;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngineControl;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * Central Aeron Cluster service for the trading platform.
 *
 * <p>This class is the single entry point for ordered cluster log events. Aeron
 * invokes it during service startup, message delivery, timer delivery, snapshot
 * creation, and leadership changes. The service intentionally contains no
 * trading decisions; it wires lifecycle state into the domain components and
 * delegates every decoded ingress message to {@link MessageRouter}.</p>
 *
 * <p>The deterministic ordering here is part of the recovery design. Snapshot
 * loading completes before any ingress message can be processed, timer events
 * are fanned out to their owners in a fixed order, and snapshots are written in
 * the same component order used by the recovery tests. Startup construction is
 * also the allocation boundary for configured market-view books: ClusterMain
 * preallocates those books before this service is launched so the first live
 * market-data message should only mutate existing state.</p>
 */
public final class TradingClusteredService implements ClusteredService {
    private final StrategyLifecycle strategyEngine;
    private final RiskEngine riskEngine;
    private final OrderManager orderManager;
    private final PortfolioEngine portfolioEngine;
    private final RecoveryCoordinator recoveryCoordinator;
    private final DailyResetTimer dailyResetTimer;
    private final MessageRouter router;
    private final ClusterBackedExecutionClock executionClock;
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private Cluster cluster;

    /**
     * Creates the service around already constructed business components.
     *
     * <p>ClusterMain owns concrete construction in a later task. Keeping this
     * constructor dependency-injected lets unit tests verify lifecycle ordering
     * without starting an Aeron media driver or clustered container.</p>
     *
     * @param strategyEngine strategy runtime facade used for dispatch, admin
     * control, timers, role activation, and warmup reset
     * @param riskEngine cluster-local pre-trade and post-fill risk state
     * @param orderManager order lifecycle owner
     * @param portfolioEngine position and PnL owner
     * @param recoveryCoordinator venue recovery owner
     * @param dailyResetTimer daily risk reset timer owner
     * @param router decoded-message dispatch table
     */
    public TradingClusteredService(
        final StrategyLifecycle strategyEngine,
        final RiskEngine riskEngine,
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RecoveryCoordinator recoveryCoordinator,
        final DailyResetTimer dailyResetTimer,
        final MessageRouter router
    ) {
        this(strategyEngine, riskEngine, orderManager, portfolioEngine, recoveryCoordinator, dailyResetTimer, router, null);
    }

    public TradingClusteredService(
        final StrategyLifecycle strategyEngine,
        final RiskEngine riskEngine,
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RecoveryCoordinator recoveryCoordinator,
        final DailyResetTimer dailyResetTimer,
        final MessageRouter router,
        final ClusterBackedExecutionClock executionClock
    ) {
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine");
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
        this.orderManager = Objects.requireNonNull(orderManager, "orderManager");
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        this.recoveryCoordinator = Objects.requireNonNull(recoveryCoordinator, "recoveryCoordinator");
        this.dailyResetTimer = Objects.requireNonNull(dailyResetTimer, "dailyResetTimer");
        this.router = Objects.requireNonNull(router, "router");
        this.executionClock = executionClock;
    }

    /**
     * Wires the real cluster facade, restores state, and schedules the first
     * daily reset.
     *
     * <p>Aeron calls this before opening ingress delivery to the service. That
     * ordering is why recovery replay is complete before gateway reconnect work
     * can send new messages into the cluster.</p>
     *
     * @param cluster active Aeron Cluster service facade
     * @param snapshotImage optional snapshot image supplied by Aeron during
     * restart; {@code null} on a fresh service start
     */
    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage) {
        setCluster(cluster);
        if (snapshotImage != null) {
            loadSnapshot(snapshotImage);
        }
        dailyResetTimer.scheduleNextReset();
    }

    /**
     * Accepts client session open notifications.
     *
     * <p>The current service has no per-session state, so the callback is
     * intentionally a no-op. Aeron still requires the method so future session
     * accounting can be added without changing the service type.</p>
     *
     * @param session opened client session
     * @param timestamp cluster timestamp for the event
     */
    @Override
    public void onSessionOpen(final ClientSession session, final long timestamp) {
        // No per-session state is required for TASK-026.
    }

    /**
     * Accepts client session close notifications.
     *
     * <p>FIX session lifecycle is owned by the gateway, not by the cluster
     * service. Closing an Aeron ingress session therefore does not directly
     * mutate trading state here.</p>
     *
     * @param session closed client session
     * @param timestamp cluster timestamp for the event
     * @param closeReason Aeron reason for the close
     */
    @Override
    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason) {
        // No per-session state is required for TASK-026.
    }

    /**
     * Decodes the SBE message header and delegates business dispatch.
     *
     * <p>Aeron has already sequenced the buffer through the replicated log when
     * this method runs. The service reads only the SBE header at {@code offset}
     * and passes the original buffer plus header metadata to {@link MessageRouter}
     * so each message type can be decoded by its owner.</p>
     *
     * @param session ingress session that supplied the message
     * @param timestamp cluster timestamp for the log event
     * @param buffer encoded SBE message including the standard header
     * @param offset start of the SBE header
     * @param length encoded message length supplied by Aeron
     * @param header Aeron log buffer header
     */
    @Override
    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header
    ) {
        headerDecoder.wrap(buffer, offset);
        router.dispatch(
            buffer,
            offset,
            headerDecoder.blockLength(),
            headerDecoder.version(),
            headerDecoder.encodedLength(),
            headerDecoder.templateId()
        );
    }

    /**
     * Fans out deterministic cluster timer events.
     *
     * <p>Timer correlation id ranges are owned by individual components. Each
     * component ignores ids outside its range, so the service can fan out every
     * timer in a stable sequence without maintaining another routing table.</p>
     *
     * @param correlationId timer id supplied when the timer was scheduled
     * @param timestamp cluster timestamp for the timer event
     */
    @Override
    public void onTimerEvent(final long correlationId, final long timestamp) {
        dailyResetTimer.onTimer(correlationId, timestamp);
        recoveryCoordinator.onTimer(correlationId, timestamp);
        strategyEngine.onTimer(correlationId);
    }

    /**
     * Writes component snapshots in deterministic recovery order.
     *
     * <p>The write order mirrors the snapshot round-trip tests and the restore
     * order in {@link #loadSnapshot(Image)}. Strategy state is intentionally not
     * snapshotted; open strategy work is reconciled from orders and recovery
     * state after restart.</p>
     *
     * @param snapshotPublication Aeron exclusive publication for snapshot data
     */
    @Override
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {
        orderManager.writeSnapshot(snapshotPublication);
        portfolioEngine.writeSnapshot(snapshotPublication);
        riskEngine.writeSnapshot(snapshotPublication);
        recoveryCoordinator.writeSnapshot(snapshotPublication);
    }

    /**
     * Activates or deactivates strategy order generation based on cluster role.
     *
     * <p>Followers continue maintaining replicated state, but only the leader is
     * allowed to emit new trading decisions.</p>
     *
     * @param newRole current Aeron cluster role
     */
    @Override
    public void onRoleChange(final Cluster.Role newRole) {
        strategyEngine.setActive(newRole == Cluster.Role.LEADER);
    }

    /**
     * Clears the live cluster reference when the container terminates.
     *
     * <p>Aeron calls this as part of shutdown. Clearing the reference prevents
     * accidental use of a stale cluster facade by tests or later warmup code.</p>
     *
     * @param cluster terminating cluster facade
     */
    @Override
    public void onTerminate(final Cluster cluster) {
        removeClusterReference();
    }

    /**
     * Installs a temporary cluster facade for warmup runs.
     *
     * <p>The warmup harness uses this before the real cluster starts so hot-path
     * code can execute against a local shim. Installing a shim after {@link
     * #onStart(Cluster, Image)} would risk replacing the real cluster facade, so
     * that case fails fast.</p>
     *
     * @param shimCluster temporary cluster facade used by warmup code
     * @throws IllegalStateException if the real service cluster is already set
     */
    public void installClusterShim(final Cluster shimCluster) {
        if (cluster != null) {
            throw new IllegalStateException("Cannot install shim while real cluster is set");
        }
        setCluster(shimCluster);
    }

    /**
     * Removes any warmup cluster shim and clears propagated component wiring.
     *
     * <p>This method is called after warmup reset so the subsequent production
     * start receives only the real Aeron Cluster facade from {@link #onStart}.</p>
     */
    public void removeClusterShim() {
        removeClusterReference();
    }

    /**
     * Clears all mutable warmup state owned by cluster components.
     *
     * <p>Warmup intentionally exercises the same hot paths used by production.
     * Before live traffic starts, all stateful components are reset so synthetic
     * orders, positions, risk counters, recovery locks, and strategy state do not
     * leak into the production session.</p>
     */
    public void resetWarmupState() {
        orderManager.resetAll();
        portfolioEngine.resetAll();
        riskEngine.resetAll();
        recoveryCoordinator.resetAll();
        strategyEngine.resetAll();
    }

    private void loadSnapshot(final Image snapshotImage) {
        orderManager.loadSnapshot(snapshotImage);
        portfolioEngine.loadSnapshot(snapshotImage);
        riskEngine.loadSnapshot(snapshotImage);
        recoveryCoordinator.loadSnapshot(snapshotImage);
    }

    private void setCluster(final Cluster cluster) {
        this.cluster = cluster;
        strategyEngine.setCluster(cluster);
        riskEngine.setCluster(cluster);
        orderManager.setCluster(cluster);
        portfolioEngine.setCluster(cluster);
        recoveryCoordinator.setCluster(cluster);
        dailyResetTimer.setCluster(cluster);
        router.setCluster(cluster);
        if (executionClock != null) {
            executionClock.setCluster(cluster);
        }
    }

    private void removeClusterReference() {
        setCluster(null);
    }

    /**
     * Narrow strategy runtime contract needed by the clustered service.
     *
     * <p>The concrete StrategyEngine is implemented in a later task. This local
     * facade captures only the behavior TASK-026 must call: message callbacks
     * used by {@link MessageRouter}, admin pause/resume controls, cluster
     * lifecycle wiring, timer delivery, leader activation, and warmup reset.</p>
     */
    public interface StrategyLifecycle extends MessageRouter.StrategyDispatch, StrategyEngineControl {
        void setCluster(Cluster cluster);

        void onTimer(long correlationId);

        void setActive(boolean active);

        void resetAll();

        @Override
        void onMarketData(MarketDataEventDecoder decoder);

        @Override
        void onExecution(ExecutionEventDecoder decoder, boolean isFill);
    }
}
