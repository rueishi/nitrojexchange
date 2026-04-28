package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.ExternalLiquidityView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;

/**
 * Dependency contract supplied to every strategy at initialization.
 *
 * <p>Strategies are plugins inside the clustered service, but they must still
 * use the same deterministic state owners as the rest of the cluster: market
 * view, risk, orders, portfolio, recovery, SBE encoders, id registry, and
 * counters. This interface keeps that dependency surface explicit and stable so
 * concrete strategies do not reach into ClusterMain or the service directly.</p>
 *
 * <p>The cluster reference may be {@code null} while the service factory is
 * assembling components. StrategyEngine updates strategy lifecycle state after
 * TradingClusteredService receives the real Aeron Cluster facade.</p>
 */
public interface StrategyContext {
    InternalMarketView marketView();

    /**
     * Returns the strategy-facing executable-liquidity view.
     *
     * <p>The default keeps existing strategy contexts source-compatible while
     * ensuring new arbitrage logic can consume gross market data minus
     * NitroJEx-owned visible liquidity.</p>
     *
     * @return external liquidity view derived from {@link #marketView()}
     */
    default ExternalLiquidityView externalLiquidityView() {
        return marketView().externalLiquidityView();
    }

    RiskEngine riskEngine();

    OrderManager orderManager();

    PortfolioEngine portfolioEngine();

    RecoveryCoordinator recoveryCoordinator();

    Cluster cluster();

    UnsafeBuffer egressBuffer();

    MessageHeaderEncoder headerEncoder();

    NewOrderCommandEncoder newOrderEncoder();

    CancelOrderCommandEncoder cancelOrderEncoder();

    IdRegistry idRegistry();

    CountersManager counters();
}
