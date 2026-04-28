package ig.rueishi.nitroj.exchange.cluster;

import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.util.Objects;
import org.agrona.concurrent.EpochClock;

/**
 * Schedules and handles the deterministic daily risk reset timer.
 *
 * <p>TradingClusteredService wires this component with the active Aeron Cluster
 * instance on startup. The timer uses reserved correlation id {@code 1001L} and
 * targets the next UTC midnight in epoch milliseconds. When the timer fires, the
 * component resets daily risk counters, archives portfolio PnL to cluster egress,
 * and schedules the next midnight reset.</p>
 */
public final class DailyResetTimer {
    public static final long DAILY_RESET_CORRELATION_ID = 1001L;
    private static final long MS_IN_DAY = 86_400_000L;

    private final RiskEngine riskEngine;
    private final PortfolioEngine portfolioEngine;
    private final EpochClock epochClock;
    private final TimerScheduler testScheduler;
    private final EgressSupplier testEgressSupplier;
    private Cluster cluster;

    public DailyResetTimer(final RiskEngine riskEngine, final PortfolioEngine portfolioEngine, final EpochClock epochClock) {
        this(riskEngine, portfolioEngine, epochClock, null, null);
    }

    DailyResetTimer(
        final RiskEngine riskEngine,
        final PortfolioEngine portfolioEngine,
        final EpochClock epochClock,
        final TimerScheduler testScheduler,
        final EgressSupplier testEgressSupplier
    ) {
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        this.epochClock = Objects.requireNonNull(epochClock, "epochClock");
        this.testScheduler = testScheduler;
        this.testEgressSupplier = testEgressSupplier;
    }

    /**
     * Installs the Aeron Cluster reference used for timer scheduling and egress.
     *
     * @param cluster active cluster service facade; may be replaced during test
     * or warmup wiring
     */
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Schedules the next daily reset at the following UTC midnight.
     *
     * <p>The deadline is computed from {@link EpochClock#time()} so tests can
     * verify exact UTC boundary behavior and production can use Agrona's
     * SystemEpochClock.</p>
     */
    public void scheduleNextReset() {
        final long nextMidnightMs = computeNextMidnightUtcMs(epochClock.time());
        if (testScheduler != null) {
            testScheduler.schedule(DAILY_RESET_CORRELATION_ID, nextMidnightMs);
        } else if (cluster != null) {
            cluster.scheduleTimer(DAILY_RESET_CORRELATION_ID, nextMidnightMs);
        }
    }

    /**
     * Handles cluster timer events.
     *
     * <p>Only correlation id {@code 1001L} belongs to this component. Other
     * timers are ignored so TradingClusteredService can safely fan out every
     * timer event to multiple timer owners.</p>
     */
    public void onTimer(final long correlationId, final long timestamp) {
        if (correlationId != DAILY_RESET_CORRELATION_ID) {
            return;
        }
        riskEngine.resetDailyCounters();
        portfolioEngine.archiveDailyPnl(egressPublication());
        scheduleNextReset();
    }

    long computeNextMidnightUtcMs(final long nowMs) {
        return (nowMs / MS_IN_DAY + 1L) * MS_IN_DAY;
    }

    private Publication egressPublication() {
        if (testEgressSupplier != null) {
            return testEgressSupplier.egressPublication();
        }
        return null;
    }

    @FunctionalInterface
    interface TimerScheduler {
        void schedule(long correlationId, long deadlineMs);
    }

    @FunctionalInterface
    interface EgressSupplier {
        Publication egressPublication();
    }
}
