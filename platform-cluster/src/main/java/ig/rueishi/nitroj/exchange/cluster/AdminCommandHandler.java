package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.AdminCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.AdminCommandType;
import ig.rueishi.nitroj.exchange.strategy.StrategyEngineControl;
import io.aeron.cluster.service.Cluster;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.EpochClock;

/**
 * Validates and executes signed operator admin commands inside the cluster.
 *
 * <p>The handler protects the cluster-side admin surface with a five-step gate:
 * stale nonce rejection, replay rejection via a bounded nonce window, HMAC-SHA256
 * validation, operator whitelist validation, and finally command execution. The
 * class is single-threaded with cluster dispatch, so the nonce ring is plain
 * mutable state rather than a concurrent structure.</p>
 */
public final class AdminCommandHandler {
    static final int NONCE_WINDOW_SIZE = 10_000;
    private static final String HMAC_ALGO = "HmacSHA256";

    private final long[] nonceRing = new long[NONCE_WINDOW_SIZE];
    private final LongHashSet seenNonces = new LongHashSet(NONCE_WINDOW_SIZE * 2);
    private final RiskEngine riskEngine;
    private final StrategyEngineControl strategyEngine;
    private final EpochClock epochClock;
    private final byte[] hmacKey;
    private final byte[] rotationHmacKey;
    private final int[] allowedOperatorIds;
    private Cluster cluster;
    private int nonceHead;
    private int nonceCount;
    private int acceptedCount;
    private int rejectedCount;
    private boolean snapshotRequestedWithoutCluster;

    public AdminCommandHandler(
        final RiskEngine riskEngine,
        final StrategyEngineControl strategyEngine,
        final EpochClock epochClock,
        final byte[] hmacKey,
        final int[] allowedOperatorIds
    ) {
        this(riskEngine, strategyEngine, epochClock, hmacKey, null, allowedOperatorIds);
    }

    public AdminCommandHandler(
        final RiskEngine riskEngine,
        final StrategyEngineControl strategyEngine,
        final EpochClock epochClock,
        final byte[] hmacKey,
        final byte[] rotationHmacKey,
        final int[] allowedOperatorIds
    ) {
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine");
        this.epochClock = Objects.requireNonNull(epochClock, "epochClock");
        this.hmacKey = Objects.requireNonNull(hmacKey, "hmacKey").clone();
        this.rotationHmacKey = rotationHmacKey == null ? null : rotationHmacKey.clone();
        this.allowedOperatorIds = Objects.requireNonNull(allowedOperatorIds, "allowedOperatorIds").clone();
        Arrays.sort(this.allowedOperatorIds);
    }

    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Validates and executes one decoded AdminCommand.
     *
     * <p>The validation order intentionally matches the plan. HMAC is checked
     * before the operator whitelist so a compromised operator id without the key
     * still fails at the cryptographic gate first.</p>
     */
    public void onAdminCommand(final AdminCommandDecoder decoder) {
        final long nonce = decoder.nonce();
        final long epochSeconds = nonce >>> 32;
        final long nowSeconds = epochClock.time() / 1_000L;
        if (nowSeconds - epochSeconds > 86_400L) {
            rejectedCount++;
            return;
        }
        if (!addNonce(nonce)) {
            rejectedCount++;
            return;
        }
        if (!validateHmacDualKey(decoder, nonce)) {
            rejectedCount++;
            return;
        }
        if (Arrays.binarySearch(allowedOperatorIds, decoder.operatorId()) < 0) {
            rejectedCount++;
            return;
        }

        acceptedCount++;
        execute(decoder);
    }

    public long computeHmac(
        final AdminCommandType commandType,
        final int venueId,
        final int instrumentId,
        final int operatorId,
        final long nonce
    ) {
        return computeHmac(commandType, venueId, instrumentId, operatorId, nonce, hmacKey);
    }

    public int nonceCount() {
        return nonceCount;
    }

    public int acceptedCount() {
        return acceptedCount;
    }

    public int rejectedCount() {
        return rejectedCount;
    }

    public boolean snapshotRequestedWithoutCluster() {
        return snapshotRequestedWithoutCluster;
    }

    private void execute(final AdminCommandDecoder decoder) {
        switch (decoder.commandType()) {
            case DEACTIVATE_KILL_SWITCH -> riskEngine.deactivateKillSwitch();
            case ACTIVATE_KILL_SWITCH -> riskEngine.activateKillSwitch("operator:" + decoder.operatorId());
            case PAUSE_STRATEGY -> strategyEngine.pauseStrategy(decoder.instrumentId());
            case RESUME_STRATEGY -> strategyEngine.resumeStrategy(decoder.instrumentId());
            case RESET_DAILY_COUNTERS -> riskEngine.resetDailyCounters();
            case TRIGGER_SNAPSHOT -> triggerSnapshot();
            default -> {
            }
        }
    }

    private void triggerSnapshot() {
        snapshotRequestedWithoutCluster = true;
    }

    private boolean validateHmacDualKey(final AdminCommandDecoder decoder, final long nonce) {
        final long expectedPrimary = computeHmac(decoder.commandType(), decoder.venueId(), decoder.instrumentId(), decoder.operatorId(), nonce, hmacKey);
        if (expectedPrimary == decoder.hmacSignature()) {
            return true;
        }
        return rotationHmacKey != null
            && computeHmac(decoder.commandType(), decoder.venueId(), decoder.instrumentId(), decoder.operatorId(), nonce, rotationHmacKey)
            == decoder.hmacSignature();
    }

    private boolean addNonce(final long nonce) {
        if (seenNonces.contains(nonce)) {
            return false;
        }
        if (nonceCount == NONCE_WINDOW_SIZE) {
            seenNonces.remove(nonceRing[nonceHead]);
            nonceCount--;
        }
        nonceRing[nonceHead] = nonce;
        nonceHead = (nonceHead + 1) % NONCE_WINDOW_SIZE;
        seenNonces.add(nonce);
        nonceCount++;
        return true;
    }

    private static long computeHmac(
        final AdminCommandType commandType,
        final int venueId,
        final int instrumentId,
        final int operatorId,
        final long nonce,
        final byte[] key
    ) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            final ByteBuffer payload = ByteBuffer.allocate(32);
            payload.put(commandType.value()).putInt(venueId).putInt(instrumentId).putInt(operatorId).putLong(nonce);
            return ByteBuffer.wrap(mac.doFinal(payload.array())).getLong();
        } catch (final Exception ex) {
            throw new IllegalStateException("Unable to compute admin command HMAC", ex);
        }
    }
}
