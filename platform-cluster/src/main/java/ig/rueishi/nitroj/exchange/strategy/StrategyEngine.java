package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.cluster.TradingClusteredService;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import io.aeron.cluster.service.Cluster;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cluster-thread fan-out engine for registered trading strategies.
 *
 * <p>TradingClusteredService and MessageRouter call this class rather than
 * invoking strategies directly. The engine centralizes leader activation,
 * admin pause/resume controls, timer routing, and strategy initialization so
 * every concrete strategy sees the same lifecycle ordering during live running,
 * replay, and failover.</p>
 *
 * <p>Market data and fills are delivered only while the node is active leader.
 * Timer events are delivered regardless of active state, which preserves cluster
 * determinism for timeout-driven strategies when leadership changes around a
 * scheduled timer.</p>
 */
public final class StrategyEngine implements TradingClusteredService.StrategyLifecycle {
    private final List<Strategy> strategies = new ArrayList<>(4);
    private final StrategyContext baseContext;
    private StrategyContext effectiveContext;
    private Cluster cluster;
    private boolean active;

    /**
     * Creates an engine with the dependency context supplied to registered strategies.
     *
     * @param context immutable strategy dependency bundle built by ClusterMain
     */
    public StrategyEngine(final StrategyContext context) {
        this.baseContext = Objects.requireNonNull(context, "context");
        this.effectiveContext = context;
    }

    /**
     * Registers and initializes a strategy.
     *
     * <p>If the service has already received a cluster reference, the strategy is
     * initialized with that cluster-aware context. If the node is already active
     * leader, the newly registered strategy is also activated immediately.</p>
     *
     * @param strategy strategy implementation to add
     */
    public void register(final Strategy strategy) {
        final Strategy checked = Objects.requireNonNull(strategy, "strategy");
        strategies.add(checked);
        checked.init(effectiveContext);
        if (active) {
            checked.onKillSwitchCleared();
        }
    }

    /**
     * Installs the current Aeron Cluster facade and propagates it to strategies.
     *
     * <p>The startup factory creates StrategyContextImpl before Aeron provides a
     * cluster reference. Because that record is immutable, this method builds a
     * replacement context carrying the new cluster and reinitializes registered
     * strategies with the updated view.</p>
     *
     * @param cluster active cluster facade, or {@code null} during shutdown/reset
     */
    @Override
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
        this.effectiveContext = contextWithCluster(cluster);
        for (Strategy strategy : strategies) {
            strategy.init(effectiveContext);
        }
    }

    /**
     * Changes leader activation state.
     *
     * @param active {@code true} when this node is the leader and may generate
     * new strategy actions
     */
    @Override
    public void setActive(final boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (!active) {
            for (Strategy strategy : strategies) {
                strategy.onKillSwitch();
            }
        } else {
            for (Strategy strategy : strategies) {
                strategy.onKillSwitchCleared();
            }
        }
    }

    /**
     * Pauses one strategy by forwarding a kill-switch callback to matching ids.
     *
     * @param strategyId strategy id to pause
     */
    @Override
    public void pauseStrategy(final int strategyId) {
        strategies.stream()
            .filter(strategy -> strategy.strategyId() == strategyId)
            .forEach(Strategy::onKillSwitch);
    }

    /**
     * Resumes one strategy by forwarding a kill-switch-cleared callback.
     *
     * @param strategyId strategy id to resume
     */
    @Override
    public void resumeStrategy(final int strategyId) {
        strategies.stream()
            .filter(strategy -> strategy.strategyId() == strategyId)
            .forEach(Strategy::onKillSwitchCleared);
    }

    /**
     * Dispatches market data only while leader-active.
     *
     * @param decoder decoded normalized market-data event
     */
    @Override
    public void onMarketData(final MarketDataEventDecoder decoder) {
        if (!active) {
            return;
        }
        for (Strategy strategy : strategies) {
            strategy.onMarketData(decoder);
        }
    }

    /**
     * Dispatches fill callbacks only while leader-active and only for fills.
     *
     * @param decoder decoded normalized execution event
     * @param isFill true when OrderManager accepted the event as a fill
     */
    @Override
    public void onExecution(final ExecutionEventDecoder decoder, final boolean isFill) {
        if (!active) {
            return;
        }
        for (Strategy strategy : strategies) {
            if (isFill) {
                strategy.onFill(decoder);
            }
        }
    }

    /**
     * Routes timer events to all strategies regardless of activation state.
     *
     * @param correlationId cluster timer correlation id
     */
    @Override
    public void onTimer(final long correlationId) {
        for (Strategy strategy : strategies) {
            strategy.onTimer(correlationId);
        }
    }

    /**
     * Notifies every strategy that venue recovery completed.
     *
     * @param venueId recovered venue id
     */
    public void onVenueRecovered(final int venueId) {
        for (Strategy strategy : strategies) {
            strategy.onVenueRecovered(venueId);
        }
    }

    /**
     * Notifies every strategy of a position update.
     *
     * @param venueId venue id
     * @param instrumentId instrument id
     * @param netQtyScaled current net quantity
     * @param avgEntryScaled current average entry price
     */
    public void onPositionUpdate(
        final int venueId,
        final int instrumentId,
        final long netQtyScaled,
        final long avgEntryScaled
    ) {
        for (Strategy strategy : strategies) {
            strategy.onPositionUpdate(venueId, instrumentId, netQtyScaled, avgEntryScaled);
        }
    }

    /**
     * Resets strategy runtime state for warmup cleanup or service shutdown.
     */
    @Override
    public void resetAll() {
        for (Strategy strategy : strategies) {
            strategy.shutdown();
        }
        active = false;
        cluster = null;
        effectiveContext = contextWithCluster(null);
    }

    int strategyCount() {
        return strategies.size();
    }

    private StrategyContext contextWithCluster(final Cluster cluster) {
        if (baseContext instanceof StrategyContextImpl impl) {
            return new StrategyContextImpl(
                impl.marketView(),
                impl.riskEngine(),
                impl.orderManager(),
                impl.portfolioEngine(),
                impl.recoveryCoordinator(),
                cluster,
                impl.egressBuffer(),
                impl.headerEncoder(),
                impl.newOrderEncoder(),
                impl.cancelOrderEncoder(),
                impl.idRegistry(),
                impl.counters()
            );
        }
        return new ClusterOverrideContext(baseContext, cluster);
    }

    /**
     * Adapter used when tests or future wiring pass a custom StrategyContext implementation.
     */
    private record ClusterOverrideContext(StrategyContext delegate, Cluster cluster) implements StrategyContext {
        @Override public InternalMarketView marketView() { return delegate.marketView(); }
        @Override public RiskEngine riskEngine() { return delegate.riskEngine(); }
        @Override public ig.rueishi.nitroj.exchange.order.OrderManager orderManager() { return delegate.orderManager(); }
        @Override public ig.rueishi.nitroj.exchange.cluster.PortfolioEngine portfolioEngine() { return delegate.portfolioEngine(); }
        @Override public ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator recoveryCoordinator() { return delegate.recoveryCoordinator(); }
        @Override public org.agrona.concurrent.UnsafeBuffer egressBuffer() { return delegate.egressBuffer(); }
        @Override public ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder headerEncoder() { return delegate.headerEncoder(); }
        @Override public ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder newOrderEncoder() { return delegate.newOrderEncoder(); }
        @Override public ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder cancelOrderEncoder() { return delegate.cancelOrderEncoder(); }
        @Override public ig.rueishi.nitroj.exchange.registry.IdRegistry idRegistry() { return delegate.idRegistry(); }
        @Override public org.agrona.concurrent.status.CountersManager counters() { return delegate.counters(); }
    }
}
