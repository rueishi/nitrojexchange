package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.AdminCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import io.aeron.cluster.service.Cluster;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;

/**
 * Single ingress dispatch point for cluster business messages.
 *
 * <p>TradingClusteredService calls this class from {@code onSessionMessage()}
 * after reading the SBE header. The router owns one decoder per inbound message
 * type and dispatches with an integer {@code templateId} switch. It also enforces
 * the fill fan-out order that keeps order state, portfolio, risk, and strategy
 * views consistent within the same cluster log event.</p>
 */
public final class MessageRouter {
    private final MarketDataEventDecoder mdDecoder = new MarketDataEventDecoder();
    private final ExecutionEventDecoder execDecoder = new ExecutionEventDecoder();
    private final VenueStatusEventDecoder venueDecoder = new VenueStatusEventDecoder();
    private final BalanceQueryResponseDecoder balanceDecoder = new BalanceQueryResponseDecoder();
    private final AdminCommandDecoder adminDecoder = new AdminCommandDecoder();
    private final StrategyDispatch strategyEngine;
    private final RiskEngine riskEngine;
    private final OrderManager orderManager;
    private final PortfolioEngine portfolioEngine;
    private final InternalMarketView marketView;
    private final RecoveryCoordinator recoveryCoordinator;
    private final AdminCommandHandler adminCommandHandler;
    private final LongSupplier clusterTimeMicros;
    private Cluster cluster;
    private int unknownTemplateCount;

    public MessageRouter(
        final StrategyDispatch strategyEngine,
        final RiskEngine riskEngine,
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final InternalMarketView marketView,
        final RecoveryCoordinator recoveryCoordinator,
        final AdminCommandHandler adminCommandHandler
    ) {
        this(strategyEngine, riskEngine, orderManager, portfolioEngine, marketView, recoveryCoordinator, adminCommandHandler, () -> 0L);
    }

    MessageRouter(
        final StrategyDispatch strategyEngine,
        final RiskEngine riskEngine,
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final InternalMarketView marketView,
        final RecoveryCoordinator recoveryCoordinator,
        final AdminCommandHandler adminCommandHandler,
        final LongSupplier clusterTimeMicros
    ) {
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine");
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
        this.orderManager = Objects.requireNonNull(orderManager, "orderManager");
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        this.marketView = Objects.requireNonNull(marketView, "marketView");
        this.recoveryCoordinator = Objects.requireNonNull(recoveryCoordinator, "recoveryCoordinator");
        this.adminCommandHandler = Objects.requireNonNull(adminCommandHandler, "adminCommandHandler");
        this.clusterTimeMicros = Objects.requireNonNull(clusterTimeMicros, "clusterTimeMicros");
    }

    public void dispatch(
        final DirectBuffer buffer,
        final int offset,
        final int blockLen,
        final int version,
        final int hdrLen,
        final int templateId
    ) {
        switch (templateId) {
            case MarketDataEventDecoder.TEMPLATE_ID -> {
                mdDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onMarketData(mdDecoder);
            }
            case ExecutionEventDecoder.TEMPLATE_ID -> {
                execDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onExecution(execDecoder);
            }
            case VenueStatusEventDecoder.TEMPLATE_ID -> {
                venueDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                recoveryCoordinator.onVenueStatus(venueDecoder);
            }
            case BalanceQueryResponseDecoder.TEMPLATE_ID -> {
                balanceDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                recoveryCoordinator.onBalanceResponse(balanceDecoder);
            }
            case AdminCommandDecoder.TEMPLATE_ID -> {
                adminDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                adminCommandHandler.onAdminCommand(adminDecoder);
            }
            default -> unknownTemplateCount++;
        }
    }

    public int unknownTemplateCount() {
        return unknownTemplateCount;
    }

    /**
     * Installs the active Aeron Cluster reference for deterministic timestamp reads.
     *
     * <p>TradingClusteredService calls this during {@code onStart()} and warmup
     * shim installation so market-data book updates use the same logical clock
     * as the rest of the cluster service. Tests may leave the value {@code null}
     * and rely on the constructor-supplied clock.</p>
     *
     * @param cluster active cluster facade, a warmup shim, or {@code null} when
     * clearing service wiring
     */
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    private void onMarketData(final MarketDataEventDecoder decoder) {
        final long timestampMicros = cluster == null ? clusterTimeMicros.getAsLong() : cluster.time();
        marketView.apply(decoder, timestampMicros);
        strategyEngine.onMarketData(decoder);
    }

    private void onExecution(final ExecutionEventDecoder decoder) {
        final boolean isFill = orderManager.onExecution(decoder);
        if (isFill) {
            portfolioEngine.onFill(decoder);
            riskEngine.onFill(decoder);
        }
        strategyEngine.onExecution(decoder, isFill);
    }

    /**
     * Strategy callbacks needed by MessageRouter.
     *
     * <p>The concrete StrategyEngine delivered later can implement this interface
     * along with admin control. Keeping the router dependency this small prevents
     * strategy internals from leaking into the dispatch layer.</p>
     */
    public interface StrategyDispatch {
        void onMarketData(MarketDataEventDecoder decoder);

        void onExecution(ExecutionEventDecoder decoder, boolean isFill);
    }
}
