package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import io.aeron.Publication;
import org.agrona.concurrent.EpochClock;
import org.junit.jupiter.api.Test;

/**
 * T-013 coverage for midnight daily reset scheduling.
 *
 * <p>The tests inject a deterministic EpochClock and a capture scheduler so UTC
 * boundary behavior can be verified without a real Aeron Cluster. Risk and
 * portfolio test doubles record the side effects produced by the reserved daily
 * reset correlation id.</p>
 */
final class DailyResetTimerTest {
    @Test
    void onTimer_correctCorrelId_resetsCountersAndReschedules() {
        final Harness harness = harness(12 * 60 * 60 * 1_000L);

        harness.timer.onTimer(DailyResetTimer.DAILY_RESET_CORRELATION_ID, 0L);

        assertThat(harness.risk.resetCount).isEqualTo(1);
        assertThat(harness.portfolio.archiveCount).isEqualTo(1);
        assertThat(harness.scheduler.correlationId).isEqualTo(DailyResetTimer.DAILY_RESET_CORRELATION_ID);
    }

    @Test
    void onTimer_wrongCorrelId_noAction() {
        final Harness harness = harness(0L);

        harness.timer.onTimer(9999L, 0L);

        assertThat(harness.risk.resetCount).isZero();
        assertThat(harness.portfolio.archiveCount).isZero();
        assertThat(harness.scheduler.scheduleCount).isZero();
    }

    @Test
    void scheduleNextReset_schedulesAtNextMidnightUtc() {
        final Harness harness = harness(12 * 60 * 60 * 1_000L);

        harness.timer.scheduleNextReset();

        assertThat(harness.scheduler.correlationId).isEqualTo(DailyResetTimer.DAILY_RESET_CORRELATION_ID);
        assertThat(harness.scheduler.deadlineMs).isEqualTo(86_400_000L);
    }

    @Test
    void computeNextMidnight_midday_returnsNextMidnight() {
        final Harness harness = harness(0L);

        assertThat(harness.timer.computeNextMidnightUtcMs(12 * 60 * 60 * 1_000L)).isEqualTo(86_400_000L);
    }

    @Test
    void computeNextMidnight_justBeforeMidnight_returnsNextMidnight() {
        final Harness harness = harness(0L);

        assertThat(harness.timer.computeNextMidnightUtcMs(86_400_000L - 1L)).isEqualTo(86_400_000L);
    }

    @Test
    void computeNextMidnight_atMidnight_returnsFollowingMidnight() {
        final Harness harness = harness(0L);

        assertThat(harness.timer.computeNextMidnightUtcMs(86_400_000L)).isEqualTo(172_800_000L);
    }

    @Test
    void dailyReset_doesNotClearKillSwitch() {
        final Harness harness = harness(0L);
        harness.risk.killSwitchActive = true;

        harness.timer.onTimer(DailyResetTimer.DAILY_RESET_CORRELATION_ID, 0L);

        assertThat(harness.risk.killSwitchActive).isTrue();
    }

    private static Harness harness(final long nowMs) {
        final RecordingRisk risk = new RecordingRisk();
        final RecordingPortfolio portfolio = new RecordingPortfolio();
        final RecordingScheduler scheduler = new RecordingScheduler();
        final EpochClock clock = () -> nowMs;
        return new Harness(
            new DailyResetTimer(risk, portfolio, clock, scheduler::schedule, () -> null),
            risk,
            portfolio,
            scheduler
        );
    }

    private record Harness(
        DailyResetTimer timer,
        RecordingRisk risk,
        RecordingPortfolio portfolio,
        RecordingScheduler scheduler
    ) {
    }

    private static final class RecordingScheduler {
        long correlationId;
        long deadlineMs;
        int scheduleCount;

        void schedule(final long correlationId, final long deadlineMs) {
            this.correlationId = correlationId;
            this.deadlineMs = deadlineMs;
            scheduleCount++;
        }
    }

    private static final class RecordingPortfolio implements PortfolioEngine {
        int archiveCount;

        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0; }
        @Override public void writeSnapshot(final io.aeron.ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final io.aeron.Image snapshotImage) { }

        @Override
        public void archiveDailyPnl(final Publication egressPublication) {
            archiveCount++;
        }

        @Override public void setCluster(final io.aeron.cluster.service.Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RecordingRisk implements RiskEngine {
        int resetCount;
        boolean killSwitchActive;

        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }
        @Override public void activateKillSwitch(final String reason) { killSwitchActive = true; }
        @Override public void deactivateKillSwitch() { killSwitchActive = false; }
        @Override public boolean isKillSwitchActive() { return killSwitchActive; }
        @Override public void writeSnapshot(final io.aeron.ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final io.aeron.Image snapshotImage) { }

        @Override
        public void resetDailyCounters() {
            resetCount++;
        }

        @Override public void setCluster(final io.aeron.cluster.service.Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }
}
