package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.RiskEventEncoder;
import ig.rueishi.nitroj.exchange.messages.RiskEventType;
import ig.rueishi.nitroj.exchange.messages.RiskStateSnapshotDecoder;
import ig.rueishi.nitroj.exchange.messages.RiskStateSnapshotEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RiskEngineImpl implements RiskEngine {
    static final long WINDOW_MICROS = 1_000_000L;

    private final boolean[] recoveryLocks = new boolean[Ids.MAX_VENUES + 1];
    private final boolean[] venueConnected = new boolean[Ids.MAX_VENUES + 1];
    private final long[] netPositionSnapshot = new long[Ids.MAX_INSTRUMENTS + 1];
    private final long[] maxLongScaled = new long[Ids.MAX_INSTRUMENTS + 1];
    private final long[] maxShortScaled = new long[Ids.MAX_INSTRUMENTS + 1];
    private final long[] maxOrderScaled = new long[Ids.MAX_INSTRUMENTS + 1];
    private final long[] maxNotionalScaled = new long[Ids.MAX_INSTRUMENTS + 1];
    private final int[] softLimitPct = new int[Ids.MAX_INSTRUMENTS + 1];
    private final long[] orderTimestampsMicros = new long[Ids.MAX_ORDERS_PER_WINDOW];
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final RiskEventEncoder riskEventEncoder = new RiskEventEncoder();
    private final RiskStateSnapshotEncoder snapshotEncoder = new RiskStateSnapshotEncoder();
    private final RiskStateSnapshotDecoder snapshotDecoder = new RiskStateSnapshotDecoder();
    private final UnsafeBuffer eventBuffer =
        new UnsafeBuffer(new byte[MessageHeaderEncoder.ENCODED_LENGTH + RiskEventEncoder.BLOCK_LENGTH]);
    private final UnsafeBuffer snapshotBuffer =
        new UnsafeBuffer(new byte[MessageHeaderEncoder.ENCODED_LENGTH + RiskStateSnapshotEncoder.BLOCK_LENGTH]);
    private final RiskEventSink riskEventSink;
    private final Runnable cancelAllOrders;
    private final LongSupplier fallbackClockMicros;
    private final int maxOrdersPerSecond;
    private final long maxDailyLossScaled;

    private Cluster cluster;
    private boolean killSwitchActive;
    private long dailyRealizedPnlScaled;
    private long dailyUnrealizedPnlScaled;
    private long dailyVolumeScaled;
    private int head;
    private int count;

    public RiskEngineImpl(final RiskConfig config) {
        this(config, RiskEventSink.NO_OP, () -> { }, () -> System.nanoTime() / 1_000L);
    }

    RiskEngineImpl(
        final RiskConfig config,
        final RiskEventSink riskEventSink,
        final Runnable cancelAllOrders,
        final LongSupplier fallbackClockMicros
    ) {
        Objects.requireNonNull(config, "config");
        this.riskEventSink = Objects.requireNonNull(riskEventSink, "riskEventSink");
        this.cancelAllOrders = Objects.requireNonNull(cancelAllOrders, "cancelAllOrders");
        this.fallbackClockMicros = Objects.requireNonNull(fallbackClockMicros, "fallbackClockMicros");
        this.maxOrdersPerSecond = Math.max(0, config.maxOrdersPerSecond());
        this.maxDailyLossScaled = Math.max(0L, config.maxDailyLossUsd());
        Arrays.fill(softLimitPct, 80);
        config.instruments().values().forEach(this::loadInstrumentRisk);
    }

    @Override
    public RiskDecision preTradeCheck(
        final int venueId,
        final int instrumentId,
        final byte side,
        final long priceScaled,
        final long qtyScaled,
        final int strategyId
    ) {
        // Decision order is part of the reject-code contract: callers can rely
        // on the returned singleton and its primitive code being deterministic
        // for the first breached boundary on the hot path.
        if (recoveryLocks[venueId]) {
            return reject(RiskDecision.REJECT_RECOVERY, venueId, instrumentId, 0L, 0L);
        }
        if (killSwitchActive) {
            return reject(RiskDecision.REJECT_KILL_SWITCH, venueId, instrumentId, 0L, 0L);
        }
        if (qtyScaled > maxOrderScaled[instrumentId]) {
            return reject(RiskDecision.REJECT_ORDER_TOO_LARGE, venueId, instrumentId, maxOrderScaled[instrumentId], qtyScaled);
        }

        final long projectedPosition = netPositionSnapshot[instrumentId]
            + (side == Side.BUY.value() ? qtyScaled : -qtyScaled);
        if (projectedPosition > maxLongScaled[instrumentId]) {
            return reject(RiskDecision.REJECT_MAX_LONG, venueId, instrumentId, maxLongScaled[instrumentId], projectedPosition);
        }
        if (projectedPosition < -maxShortScaled[instrumentId]) {
            return reject(RiskDecision.REJECT_MAX_SHORT, venueId, instrumentId, maxShortScaled[instrumentId], -projectedPosition);
        }

        final long notional = ScaledMath.safeMulDiv(qtyScaled, priceScaled, Ids.SCALE);
        if (notional > maxNotionalScaled[instrumentId]) {
            return reject(RiskDecision.REJECT_MAX_NOTIONAL, venueId, instrumentId, maxNotionalScaled[instrumentId], notional);
        }

        final long nowMicros = nowMicros();
        pruneOrderWindow(nowMicros);
        if (count >= maxOrdersPerSecond) {
            return reject(RiskDecision.REJECT_RATE_LIMIT, venueId, instrumentId, maxOrdersPerSecond, count);
        }

        final long totalDailyPnlScaled = dailyRealizedPnlScaled + dailyUnrealizedPnlScaled;
        if (totalDailyPnlScaled < -maxDailyLossScaled) {
            activateKillSwitch("daily_loss_limit");
            return RiskDecision.REJECT_DAILY_LOSS;
        }

        if (!venueConnected[venueId]) {
            return reject(RiskDecision.REJECT_VENUE_NOT_CONNECTED, venueId, instrumentId, 0L, 0L);
        }

        publishSoftLimitBreaches(venueId, instrumentId, projectedPosition, notional, totalDailyPnlScaled);
        recordOrderInWindow(nowMicros);
        return RiskDecision.APPROVED;
    }

    @Override
    public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) {
        netPositionSnapshot[instrumentId] = netQtyScaled;
    }

    public long getPositionSnapshot(final int instrumentId) {
        return netPositionSnapshot[instrumentId];
    }

    @Override
    public void updateDailyPnl(final long realizedPnlDeltaScaled) {
        dailyRealizedPnlScaled += realizedPnlDeltaScaled;
    }

    public void updateDailyUnrealizedPnl(final long unrealizedPnlScaled) {
        dailyUnrealizedPnlScaled = unrealizedPnlScaled;
    }

    @Override
    public void setRecoveryLock(final int venueId, final boolean locked) {
        recoveryLocks[venueId] = locked;
        publishRiskEvent(
            locked ? RiskEventType.RECOVERY_LOCK_SET : RiskEventType.RECOVERY_LOCK_CLEARED,
            venueId,
            0,
            0L,
            0L
        );
    }

    public void setVenueConnected(final int venueId, final boolean connected) {
        venueConnected[venueId] = connected;
    }

    @Override
    public long getDailyPnlScaled() {
        return dailyRealizedPnlScaled;
    }

    public long getDailyUnrealizedPnlScaled() {
        return dailyUnrealizedPnlScaled;
    }

    public long getDailyVolumeScaled() {
        return dailyVolumeScaled;
    }

    @Override
    public void activateKillSwitch(final String reason) {
        if (!killSwitchActive) {
            killSwitchActive = true;
            publishRiskEvent(RiskEventType.KILL_SWITCH_ACTIVATED, 0, 0, 0L, 0L);
            cancelAllOrders.run();
        }
    }

    @Override
    public void deactivateKillSwitch() {
        if (killSwitchActive) {
            killSwitchActive = false;
            publishRiskEvent(RiskEventType.KILL_SWITCH_DEACTIVATED, 0, 0, 0L, 0L);
        }
    }

    @Override
    public boolean isKillSwitchActive() {
        return killSwitchActive;
    }

    @Override
    public void writeSnapshot(final ExclusivePublication snapshotPublication) {
        if (snapshotPublication == null) {
            return;
        }
        final int length = encodeSnapshot(snapshotBuffer, 0);
        while (snapshotPublication.offer(snapshotBuffer, 0, length) < 0) {
            Thread.onSpinWait();
        }
    }

    @Override
    public void loadSnapshot(final Image snapshotImage) {
        if (snapshotImage == null) {
            return;
        }
        final boolean[] loaded = new boolean[1];
        while (!loaded[0]) {
            final int fragments = snapshotImage.poll((buffer, offset, length, header) -> {
                loadSnapshot(buffer, offset);
                loaded[0] = true;
            }, 1);
            if (fragments == 0) {
                Thread.onSpinWait();
            }
        }
    }

    @Override
    public void resetDailyCounters() {
        dailyRealizedPnlScaled = 0L;
        dailyUnrealizedPnlScaled = 0L;
        dailyVolumeScaled = 0L;
    }

    @Override
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void onFill(final ExecutionEventDecoder decoder) {
        dailyVolumeScaled += decoder.fillQtyScaled();
    }

    @Override
    public void resetAll() {
        Arrays.fill(recoveryLocks, false);
        Arrays.fill(venueConnected, false);
        Arrays.fill(netPositionSnapshot, 0L);
        Arrays.fill(orderTimestampsMicros, 0L);
        killSwitchActive = false;
        head = 0;
        count = 0;
        resetDailyCounters();
    }

    int encodeSnapshot(final MutableDirectBuffer buffer, final int offset) {
        snapshotEncoder
            .wrapAndApplyHeader(buffer, offset, headerEncoder)
            .killSwitchActive(killSwitchActive ? BooleanType.TRUE : BooleanType.FALSE)
            .dailyRealizedPnlScaled(dailyRealizedPnlScaled)
            .dailyVolumeScaled(dailyVolumeScaled)
            .snapshotClusterTime(nowMicros());
        return MessageHeaderEncoder.ENCODED_LENGTH + snapshotEncoder.encodedLength();
    }

    void loadSnapshot(final DirectBuffer buffer, final int offset) {
        headerDecoder.wrap(buffer, offset);
        snapshotDecoder.wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            headerDecoder.blockLength(),
            headerDecoder.version()
        );
        killSwitchActive = snapshotDecoder.killSwitchActive() == BooleanType.TRUE;
        dailyRealizedPnlScaled = snapshotDecoder.dailyRealizedPnlScaled();
        dailyVolumeScaled = snapshotDecoder.dailyVolumeScaled();
    }

    private void loadInstrumentRisk(final RiskConfig.InstrumentRisk risk) {
        final int instrumentId = risk.instrumentId();
        maxOrderScaled[instrumentId] = risk.maxOrderSizeScaled();
        maxLongScaled[instrumentId] = risk.maxLongPositionScaled();
        maxShortScaled[instrumentId] = risk.maxShortPositionScaled();
        maxNotionalScaled[instrumentId] = risk.maxNotionalUsdScaled();
        softLimitPct[instrumentId] = risk.softLimitPct();
    }

    private RiskDecision reject(
        final RiskDecision decision,
        final int venueId,
        final int instrumentId,
        final long limitValueScaled,
        final long currentValueScaled
    ) {
        publishRiskEvent(RiskEventType.HARD_LIMIT_REJECT, venueId, instrumentId, limitValueScaled, currentValueScaled);
        return decision;
    }

    private void publishSoftLimitBreaches(
        final int venueId,
        final int instrumentId,
        final long projectedPosition,
        final long notional,
        final long totalDailyPnlScaled
    ) {
        final int thresholdPct = softLimitPct[instrumentId];
        final long positionLimit = projectedPosition >= 0L ? maxLongScaled[instrumentId] : maxShortScaled[instrumentId];
        if (atSoftLimit(Math.abs(projectedPosition), positionLimit, thresholdPct)) {
            publishRiskEvent(RiskEventType.SOFT_LIMIT_BREACH, venueId, instrumentId, positionLimit, Math.abs(projectedPosition));
        }
        if (atSoftLimit(notional, maxNotionalScaled[instrumentId], thresholdPct)) {
            publishRiskEvent(RiskEventType.SOFT_LIMIT_BREACH, venueId, instrumentId, maxNotionalScaled[instrumentId], notional);
        }
        if (totalDailyPnlScaled < 0L && atSoftLimit(-totalDailyPnlScaled, maxDailyLossScaled, thresholdPct)) {
            publishRiskEvent(RiskEventType.SOFT_LIMIT_BREACH, venueId, instrumentId, maxDailyLossScaled, -totalDailyPnlScaled);
        }
    }

    private static boolean atSoftLimit(final long currentValue, final long limitValue, final int thresholdPct) {
        return limitValue > 0L && currentValue >= ScaledMath.safeMulDiv(limitValue, thresholdPct, 100L);
    }

    private void publishRiskEvent(
        final RiskEventType type,
        final int venueId,
        final int instrumentId,
        final long limitValueScaled,
        final long currentValueScaled
    ) {
        riskEventEncoder
            .wrapAndApplyHeader(eventBuffer, 0, headerEncoder)
            .riskEventType(type)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .affectedClOrdId(0L)
            .limitValueScaled(limitValueScaled)
            .currentValueScaled(currentValueScaled)
            .eventClusterTime(nowMicros());
        riskEventSink.offer(eventBuffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + riskEventEncoder.encodedLength());
    }

    private void pruneOrderWindow(final long nowMicros) {
        while (count > 0) {
            final int tail = (head - count + Ids.MAX_ORDERS_PER_WINDOW) % Ids.MAX_ORDERS_PER_WINDOW;
            if (nowMicros - orderTimestampsMicros[tail] > WINDOW_MICROS) {
                count--;
            } else {
                break;
            }
        }
    }

    private void recordOrderInWindow(final long nowMicros) {
        orderTimestampsMicros[head] = nowMicros;
        head = (head + 1) % Ids.MAX_ORDERS_PER_WINDOW;
        count++;
    }

    private long nowMicros() {
        return cluster == null ? fallbackClockMicros.getAsLong() : cluster.time();
    }
}
