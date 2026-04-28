package ig.rueishi.nitroj.exchange.strategy;

/**
 * Minimal operator-control surface for strategies.
 *
 * <p>AdminCommandHandler owns this small interface so admin command processing
 * can pause or resume strategies before the concrete StrategyEngine is delivered
 * by a later task. StrategyEngine will implement this interface alongside its
 * runtime lifecycle contracts.</p>
 */
public interface StrategyEngineControl {
    void pauseStrategy(int strategyId);

    void resumeStrategy(int strategyId);
}
