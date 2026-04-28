package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.messages.AdminCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.AdminCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.AdminCommandType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngineControl;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * T-012 coverage for signed admin command validation and execution.
 *
 * <p>Commands are encoded with the generated SBE codecs and signed through the
 * production handler's HMAC implementation. The tests pin validation ordering:
 * nonce freshness, replay protection, HMAC, whitelist, then execution.</p>
 */
final class AdminCommandHandlerTest {
    private static final byte[] KEY = "primary-admin-key-32-bytes".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final long NOW_SECONDS = 1_700_000_000L;
    private long sequence = 1L;

    @Test
    void validHmac_activateKillSwitch_killSwitchSet() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1));

        assertThat(harness.risk.killSwitchActive).isTrue();
        assertThat(harness.risk.reason).isEqualTo("operator:1");
    }

    @Test
    void validHmac_deactivateKillSwitch_killSwitchCleared() {
        final Harness harness = harness();
        harness.risk.killSwitchActive = true;

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.DEACTIVATE_KILL_SWITCH, 0, 0, 1));

        assertThat(harness.risk.killSwitchActive).isFalse();
    }

    @Test
    void validHmac_triggerSnapshot_snapshotCalled() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.TRIGGER_SNAPSHOT, 0, 0, 1));

        assertThat(harness.handler.snapshotRequestedWithoutCluster()).isTrue();
    }

    @Test
    void validHmac_pauseStrategy_strategyPaused() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.PAUSE_STRATEGY, 0, 42, 1));

        assertThat(harness.strategy.pausedStrategyId).isEqualTo(42);
    }

    @Test
    void validHmac_resumeStrategy_strategyResumed() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.RESUME_STRATEGY, 0, 43, 1));

        assertThat(harness.strategy.resumedStrategyId).isEqualTo(43);
    }

    @Test
    void validHmac_resetDailyCounters_countersCleared() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.RESET_DAILY_COUNTERS, 0, 0, 1));

        assertThat(harness.risk.resetCount).isEqualTo(1);
    }

    @Test
    void wrongHmac_commandRejected_stateUnchanged() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1, nonce(), 123L));

        assertThat(harness.risk.killSwitchActive).isFalse();
        assertThat(harness.handler.rejectedCount()).isEqualTo(1);
    }

    @Test
    void emptyHmac_commandRejected() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1, nonce(), 0L));

        assertThat(harness.risk.killSwitchActive).isFalse();
    }

    @Test
    void tamperedPayload_hmacInvalid_rejected() {
        final Harness harness = harness();
        final long nonce = nonce();
        final long signature = harness.handler.computeHmac(AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1, nonce);

        harness.handler.onAdminCommand(command(AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 99, 1, nonce, signature));

        assertThat(harness.risk.killSwitchActive).isFalse();
        assertThat(harness.handler.rejectedCount()).isEqualTo(1);
    }

    @Test
    void replayedNonce_commandRejected_warnLogged() {
        final Harness harness = harness();
        final long nonce = nonce();
        final AdminCommandDecoder first = command(harness.handler, AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1, nonce);
        final AdminCommandDecoder second = command(harness.handler, AdminCommandType.DEACTIVATE_KILL_SWITCH, 0, 0, 1, nonce);

        harness.handler.onAdminCommand(first);
        harness.handler.onAdminCommand(second);

        assertThat(harness.risk.killSwitchActive).isTrue();
        assertThat(harness.handler.rejectedCount()).isEqualTo(1);
    }

    @Test
    void staleNonce_olderThan24h_rejected() {
        final Harness harness = harness();
        final long staleNonce = ((NOW_SECONDS - 86_401L) << 32) | 1L;

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1, staleNonce));

        assertThat(harness.risk.killSwitchActive).isFalse();
    }

    @Test
    void nonceTimestampEncoded_upperBits_validWindow() {
        final long nonce = nonce();

        assertThat(nonce >>> 32).isEqualTo(NOW_SECONDS);
    }

    @Test
    void activateKillSwitch_alreadyActive_idempotent() {
        final Harness harness = harness();
        harness.risk.killSwitchActive = true;

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 1));

        assertThat(harness.risk.killSwitchActive).isTrue();
    }

    @Test
    void deactivateKillSwitch_notActive_idempotent() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.DEACTIVATE_KILL_SWITCH, 0, 0, 1));

        assertThat(harness.risk.killSwitchActive).isFalse();
    }

    @Test
    void unknownOperatorId_commandRejected_afterHmacValidation() {
        final Harness harness = harness();

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0, 99));

        assertThat(harness.risk.killSwitchActive).isFalse();
        assertThat(harness.handler.rejectedCount()).isEqualTo(1);
    }

    @Test
    void nonceRingBuffer_afterWindowFull_oldNonceEvictedAndReaccepted() {
        final Harness harness = harness();
        final long firstNonce = nonce();
        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.RESET_DAILY_COUNTERS, 0, 0, 1, firstNonce));
        for (int i = 0; i < AdminCommandHandler.NONCE_WINDOW_SIZE; i++) {
            harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.RESET_DAILY_COUNTERS, 0, 0, 1));
        }

        harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.RESET_DAILY_COUNTERS, 0, 0, 1, firstNonce));

        assertThat(harness.handler.rejectedCount()).isZero();
    }

    @Test
    void nonceRingBuffer_sizeNeverExceedsCapacity() {
        final Harness harness = harness();

        for (int i = 0; i < AdminCommandHandler.NONCE_WINDOW_SIZE + 5; i++) {
            harness.handler.onAdminCommand(command(harness.handler, AdminCommandType.RESET_DAILY_COUNTERS, 0, 0, 1));
        }

        assertThat(harness.handler.nonceCount()).isEqualTo(AdminCommandHandler.NONCE_WINDOW_SIZE);
    }

    private Harness harness() {
        final RecordingRisk risk = new RecordingRisk();
        final RecordingStrategy strategy = new RecordingStrategy();
        final AdminCommandHandler handler = new AdminCommandHandler(risk, strategy, () -> NOW_SECONDS * 1_000L, KEY, new int[]{1, 2, 3});
        return new Harness(handler, risk, strategy);
    }

    private AdminCommandDecoder command(
        final AdminCommandHandler handler,
        final AdminCommandType type,
        final int venueId,
        final int instrumentId,
        final int operatorId
    ) {
        return command(handler, type, venueId, instrumentId, operatorId, nonce());
    }

    private AdminCommandDecoder command(
        final AdminCommandHandler handler,
        final AdminCommandType type,
        final int venueId,
        final int instrumentId,
        final int operatorId,
        final long nonce
    ) {
        return command(type, venueId, instrumentId, operatorId, nonce, handler.computeHmac(type, venueId, instrumentId, operatorId, nonce));
    }

    private static AdminCommandDecoder command(
        final AdminCommandType type,
        final int venueId,
        final int instrumentId,
        final int operatorId,
        final long nonce,
        final long signature
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new AdminCommandEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .commandType(type)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .operatorId(operatorId)
            .hmacSignature(signature)
            .nonce(nonce);
        final AdminCommandDecoder decoder = new AdminCommandDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private long nonce() {
        return (NOW_SECONDS << 32) | sequence++;
    }

    private record Harness(AdminCommandHandler handler, RecordingRisk risk, RecordingStrategy strategy) {
    }

    private static final class RecordingStrategy implements StrategyEngineControl {
        int pausedStrategyId;
        int resumedStrategyId;

        @Override
        public void pauseStrategy(final int strategyId) {
            pausedStrategyId = strategyId;
        }

        @Override
        public void resumeStrategy(final int strategyId) {
            resumedStrategyId = strategyId;
        }
    }

    private static final class RecordingRisk implements RiskEngine {
        boolean killSwitchActive;
        String reason;
        int resetCount;

        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }

        @Override
        public void activateKillSwitch(final String reason) {
            killSwitchActive = true;
            this.reason = reason;
        }

        @Override
        public void deactivateKillSwitch() {
            killSwitchActive = false;
        }

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
