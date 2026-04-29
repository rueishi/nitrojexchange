package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderUpdateDecoder;

/**
 * Plugin contract for deterministic cluster-side trading strategies.
 *
 * <p>StrategyEngine owns registration and lifecycle fan-out, while concrete
 * strategies implement this interface to react to market data, fills, risk
 * state, venue recovery, and timers. The interface is intentionally plain rather
 * than sealed so market making and arbitrage implementations can be delivered
 * independently and future strategies can be added without editing this type.</p>
 *
 * <p>All callbacks run on the Aeron clustered service thread. Implementations
 * must therefore avoid blocking work and should keep mutable state local to the
 * strategy instance so replay and failover remain deterministic.</p>
 */
public interface Strategy {
    void init(StrategyContext ctx);

    void onMarketData(MarketDataEventDecoder decoder);

    void onFill(ExecutionEventDecoder decoder);

    void onOrderRejected(long clOrdId, byte rejectCode, int venueId);

    void onKillSwitch();

    void onKillSwitchCleared();

    void onVenueRecovered(int venueId);

    void onPositionUpdate(int venueId, int instrumentId, long netQtyScaled, long avgEntryScaled);

    int[] subscribedInstrumentIds();

    int[] activeVenueIds();

    void shutdown();

    default int strategyId() {
        return 0;
    }

    default void onTimer(final long correlationId) {
    }

    default void onParentUpdate(final ParentOrderUpdateDecoder decoder) {
    }

    default void onParentTerminal(final ParentOrderTerminalDecoder decoder) {
    }
}
