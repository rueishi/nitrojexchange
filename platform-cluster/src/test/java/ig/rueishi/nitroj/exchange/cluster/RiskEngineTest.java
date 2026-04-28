package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.RiskEventDecoder;
import ig.rueishi.nitroj.exchange.messages.RiskEventEncoder;
import ig.rueishi.nitroj.exchange.messages.RiskEventType;
import ig.rueishi.nitroj.exchange.messages.RiskStateSnapshotEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.test.CapturingPublication;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RiskEngineTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final int STRATEGY = Ids.STRATEGY_MARKET_MAKING;
    private static final long DAILY_LOSS = scaled(1_000);
    private final AtomicLong clockMicros = new AtomicLong(10_000_000L);
    private final CapturingPublication publication = new CapturingPublication();

    @Test
    void approved_whenWithinAllLimitsAndVenueConnected() {
        final RiskEngineImpl engine = connectedEngine(config(100));

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        assertThat(publication.size()).isZero();
    }

    @Test
    void recoveryLock_checkedBeforeKillSwitch() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.activateKillSwitch("admin");
        engine.setRecoveryLock(VENUE, true);
        publication.clear();

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.REJECT_RECOVERY);
        assertThat(lastRiskEvent().riskEventType()).isEqualTo(RiskEventType.HARD_LIMIT_REJECT);
    }

    @Test
    void killSwitch_rejectsOrders() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.activateKillSwitch("admin");

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.REJECT_KILL_SWITCH);
    }

    @Test
    void maxOrderSize_rejectsOrdersAboveLimit() {
        final RiskEngineImpl engine = connectedEngine(config(100));

        assertThat(check(engine, scaled(100), scaled(10) + 1)).isSameAs(RiskDecision.REJECT_ORDER_TOO_LARGE);
    }

    @Test
    void maxLongPosition_rejectsProjectedLongBreach() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.updatePositionSnapshot(VENUE, INSTRUMENT, scaled(99));

        assertThat(check(engine, scaled(100), scaled(2))).isSameAs(RiskDecision.REJECT_MAX_LONG);
    }

    @Test
    void maxShortPosition_rejectsProjectedShortBreach() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.updatePositionSnapshot(VENUE, INSTRUMENT, -scaled(99));

        final RiskDecision decision = engine.preTradeCheck(
            VENUE,
            INSTRUMENT,
            Side.SELL.value(),
            scaled(100),
            scaled(2),
            STRATEGY
        );

        assertThat(decision).isSameAs(RiskDecision.REJECT_MAX_SHORT);
    }

    @Test
    void maxNotional_rejectsOrdersAboveLimit() {
        final RiskEngineImpl engine = connectedEngine(config(100));

        assertThat(check(engine, scaled(200_000), scaled(6))).isSameAs(RiskDecision.REJECT_MAX_NOTIONAL);
    }

    @Test
    void orderRateLimit_rejectsAtWindowLimit() {
        final RiskEngineImpl engine = connectedEngine(config(2));

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.REJECT_RATE_LIMIT);
    }

    @Test
    void orderRateLimit_prunesOrdersOlderThanOneSecond() {
        final RiskEngineImpl engine = connectedEngine(config(2));

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        clockMicros.addAndGet(RiskEngineImpl.WINDOW_MICROS + 1);

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
    }

    @Test
    void dailyLoss_exceeded_activatesKillSwitchAndRejects() {
        final AtomicBoolean cancelAllOrders = new AtomicBoolean();
        final RiskEngineImpl engine = connectedEngine(config(100), () -> cancelAllOrders.set(true));
        engine.updateDailyPnl(-DAILY_LOSS - 1);

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.REJECT_DAILY_LOSS);
        assertThat(engine.isKillSwitchActive()).isTrue();
        assertThat(cancelAllOrders).isTrue();
        assertThat(publication.countByTemplateId(RiskEventEncoder.TEMPLATE_ID)).isEqualTo(1);
    }

    @Test
    void venueDisconnected_rejectsAfterOtherChecks() {
        final RiskEngineImpl engine = engine(config(100));

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.REJECT_VENUE_NOT_CONNECTED);
    }

    @Test
    void hardReject_publishesRiskEvent() {
        final RiskEngineImpl engine = connectedEngine(config(100));

        assertThat(check(engine, scaled(100), scaled(11))).isSameAs(RiskDecision.REJECT_ORDER_TOO_LARGE);

        final RiskEventDecoder event = lastRiskEvent();
        assertThat(event.riskEventType()).isEqualTo(RiskEventType.HARD_LIMIT_REJECT);
        assertThat(event.venueId()).isEqualTo(VENUE);
        assertThat(event.instrumentId()).isEqualTo(INSTRUMENT);
        assertThat(event.limitValueScaled()).isEqualTo(scaled(10));
        assertThat(event.currentValueScaled()).isEqualTo(scaled(11));
    }

    @Test
    void softPositionLimit_publishesAlertButApproves() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.updatePositionSnapshot(VENUE, INSTRUMENT, scaled(79));

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        assertThat(lastRiskEvent().riskEventType()).isEqualTo(RiskEventType.SOFT_LIMIT_BREACH);
    }

    @Test
    void softDailyLoss_publishesAlertButApproves() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.updateDailyPnl(-scaled(800));

        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
        assertThat(lastRiskEvent().riskEventType()).isEqualTo(RiskEventType.SOFT_LIMIT_BREACH);
    }

    @Test
    void killSwitchActivationPublishesEventAndCancelsOrders() {
        final AtomicBoolean cancelAllOrders = new AtomicBoolean();
        final RiskEngineImpl engine = connectedEngine(config(100), () -> cancelAllOrders.set(true));

        engine.activateKillSwitch("operator");

        assertThat(engine.isKillSwitchActive()).isTrue();
        assertThat(cancelAllOrders).isTrue();
        assertThat(lastRiskEvent().riskEventType()).isEqualTo(RiskEventType.KILL_SWITCH_ACTIVATED);
    }

    @Test
    void killSwitchDeactivatePublishesEventAndAllowsTrading() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.activateKillSwitch("operator");
        publication.clear();

        engine.deactivateKillSwitch();

        assertThat(engine.isKillSwitchActive()).isFalse();
        assertThat(lastRiskEvent().riskEventType()).isEqualTo(RiskEventType.KILL_SWITCH_DEACTIVATED);
        assertThat(check(engine, scaled(100), scaled(1))).isSameAs(RiskDecision.APPROVED);
    }

    @Test
    void recoveryLockPublishesSetAndClearEvents() {
        final RiskEngineImpl engine = connectedEngine(config(100));

        engine.setRecoveryLock(VENUE, true);
        engine.setRecoveryLock(VENUE, false);

        assertThat(publication.countByTemplateId(RiskEventEncoder.TEMPLATE_ID)).isEqualTo(2);
        assertThat(riskEvent(0).riskEventType()).isEqualTo(RiskEventType.RECOVERY_LOCK_SET);
        assertThat(riskEvent(1).riskEventType()).isEqualTo(RiskEventType.RECOVERY_LOCK_CLEARED);
    }

    @Test
    void resetDailyCountersClearsPnlAndVolume() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.updateDailyPnl(-scaled(123));
        engine.updateDailyUnrealizedPnl(-scaled(45));

        engine.resetDailyCounters();

        assertThat(engine.getDailyPnlScaled()).isZero();
        assertThat(engine.getDailyUnrealizedPnlScaled()).isZero();
        assertThat(engine.getDailyVolumeScaled()).isZero();
    }

    @Test
    void snapshot_writeAndLoad_preservesKillSwitchAndDailyMetrics() {
        final RiskEngineImpl source = connectedEngine(config(100));
        source.activateKillSwitch("operator");
        source.updateDailyPnl(-scaled(77));

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        final int length = source.encodeSnapshot(buffer, 0);
        final RiskEngineImpl restored = connectedEngine(config(100));
        restored.loadSnapshot(buffer, 0);

        assertThat(length).isEqualTo(MessageHeaderDecoder.ENCODED_LENGTH + RiskStateSnapshotEncoder.BLOCK_LENGTH);
        assertThat(restored.isKillSwitchActive()).isTrue();
        assertThat(restored.getDailyPnlScaled()).isEqualTo(-scaled(77));
    }

    @Test
    void resetAllClearsRuntimeStateButKeepsConfiguredLimits() {
        final RiskEngineImpl engine = connectedEngine(config(100));
        engine.updatePositionSnapshot(VENUE, INSTRUMENT, scaled(50));
        engine.activateKillSwitch("operator");
        engine.resetAll();
        engine.setVenueConnected(VENUE, true);

        assertThat(engine.isKillSwitchActive()).isFalse();
        assertThat(engine.getPositionSnapshot(INSTRUMENT)).isZero();
        assertThat(check(engine, scaled(100), scaled(11))).isSameAs(RiskDecision.REJECT_ORDER_TOO_LARGE);
    }

    @Test
    void preTradeCheck_averageLatencyUnderFiveMicros() {
        final RiskEngineImpl engine = connectedEngine(config(Ids.MAX_ORDERS_PER_WINDOW));
        final int iterations = 1_000;

        final long start = System.nanoTime();
        RiskDecision decision = RiskDecision.APPROVED;
        for (int i = 0; i < iterations; i++) {
            clockMicros.incrementAndGet();
            decision = check(engine, scaled(100), 1);
        }
        final long elapsedNanos = System.nanoTime() - start;

        assertThat(decision).isSameAs(RiskDecision.APPROVED);
        assertThat(elapsedNanos / iterations).isLessThan(5_000L);
    }

    private RiskDecision check(final RiskEngineImpl engine, final long priceScaled, final long qtyScaled) {
        return engine.preTradeCheck(VENUE, INSTRUMENT, Side.BUY.value(), priceScaled, qtyScaled, STRATEGY);
    }

    private RiskEngineImpl connectedEngine(final RiskConfig config) {
        return connectedEngine(config, () -> { });
    }

    private RiskEngineImpl connectedEngine(final RiskConfig config, final Runnable cancelAllOrders) {
        final RiskEngineImpl engine = engine(config, cancelAllOrders);
        engine.setVenueConnected(VENUE, true);
        return engine;
    }

    private RiskEngineImpl engine(final RiskConfig config) {
        return engine(config, () -> { });
    }

    private RiskEngineImpl engine(final RiskConfig config, final Runnable cancelAllOrders) {
        return new RiskEngineImpl(config, publication::offer, cancelAllOrders, clockMicros::get);
    }

    private static RiskConfig config(final int maxOrdersPerSecond) {
        return new RiskConfig(
            maxOrdersPerSecond,
            DAILY_LOSS,
            Map.of(
                INSTRUMENT,
                new RiskConfig.InstrumentRisk(
                    INSTRUMENT,
                    scaled(10),
                    scaled(100),
                    scaled(100),
                    scaled(1_000_000),
                    80
                )
            )
        );
    }

    private RiskEventDecoder lastRiskEvent() {
        return riskEvent(publication.size() - 1);
    }

    private RiskEventDecoder riskEvent(final int index) {
        final RiskEventDecoder decoder = new RiskEventDecoder();
        decoder.wrapAndApplyHeader(publication.message(index), 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static long scaled(final long value) {
        return value * Ids.SCALE;
    }
}
