package ig.rueishi.nitroj.exchange.tooling;

import ig.rueishi.nitroj.exchange.messages.AdminCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.AdminCommandType;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import io.aeron.Aeron;
import io.aeron.Publication;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Signed Aeron admin-command publisher used by the operator CLI.
 *
 * <p>The client owns nonce persistence, HMAC-SHA256 signing, SBE encoding, and
 * Aeron publication. Its HMAC byte layout intentionally mirrors
 * AdminCommandHandler: command type, venue id, instrument id, operator id, and
 * nonce are written into a 32-byte big-endian {@link ByteBuffer}; the first
 * eight HMAC bytes become the signature.</p>
 */
public final class AdminClient implements AutoCloseable {
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int BUFFER_BYTES = MessageHeaderEncoder.ENCODED_LENGTH + AdminCommandEncoder.BLOCK_LENGTH;

    private final AdminConfig config;
    private final Aeron aeron;
    private final Publication adminPublication;
    private final byte[] hmacKey;
    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[BUFFER_BYTES]);
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AdminCommandEncoder adminCommandEncoder = new AdminCommandEncoder();
    private long nonce;

    public AdminClient(final AdminConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.hmacKey = loadHmacKey(config);
        this.nonce = loadOrBootstrapNonce(config.nonceFile());
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(config.aeronDir()));
        this.adminPublication = aeron.addPublication(config.adminChannel(), config.adminStreamId());
    }

    public void activateKillSwitch(final String reason) {
        sendCommand(AdminCommandType.ACTIVATE_KILL_SWITCH, 0, 0);
    }

    public void deactivateKillSwitch() {
        sendCommand(AdminCommandType.DEACTIVATE_KILL_SWITCH, 0, 0);
    }

    public void pauseStrategy(final int strategyId) {
        sendCommand(AdminCommandType.PAUSE_STRATEGY, 0, strategyId);
    }

    public void resumeStrategy(final int strategyId) {
        sendCommand(AdminCommandType.RESUME_STRATEGY, 0, strategyId);
    }

    public void triggerSnapshot() {
        sendCommand(AdminCommandType.TRIGGER_SNAPSHOT, 0, 0);
    }

    public void resetDailyCounters() {
        sendCommand(AdminCommandType.RESET_DAILY_COUNTERS, 0, 0);
    }

    /**
     * Encodes, signs, persists nonce state, and publishes one admin command.
     *
     * @param commandType admin command enum
     * @param venueId target venue id or zero when unused
     * @param instrumentId target instrument/strategy id depending on command
     */
    void sendCommand(final AdminCommandType commandType, final int venueId, final int instrumentId) {
        final long currentNonce = nonce++;
        saveNonce(nonce);
        if (System.currentTimeMillis() * 1_000L < currentNonce) {
            throw new IllegalStateException("Nonce would decrease - clock stepped backward. Investigate NTP before retrying.");
        }
        final long signature = computeHmac(commandType, venueId, instrumentId, config.operatorId(), currentNonce);
        adminCommandEncoder.wrapAndApplyHeader(sendBuffer, 0, headerEncoder)
            .commandType(commandType)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .operatorId(config.operatorId())
            .hmacSignature(signature)
            .nonce(currentNonce);
        final long result = adminPublication.offer(sendBuffer, 0, BUFFER_BYTES);
        if (result < 0L) {
            throw new IllegalStateException("Admin command not accepted: " + result);
        }
    }

    long computeHmac(
        final AdminCommandType commandType,
        final int venueId,
        final int instrumentId,
        final int operatorId,
        final long nonce
    ) {
        return computeHmac(commandType, venueId, instrumentId, operatorId, nonce, hmacKey);
    }

    private long loadOrBootstrapNonce(final String nonceFile) {
        final Path path = Path.of(nonceFile);
        try {
            if (Files.exists(path)) {
                return Long.parseLong(Files.readString(path).strip());
            }
            return System.currentTimeMillis() * 1_000L;
        } catch (final Exception ex) {
            throw new IllegalStateException("Unable to load admin nonce file: " + nonceFile, ex);
        }
    }

    private void saveNonce(final long nonce) {
        try {
            final Path path = Path.of(config.nonceFile());
            final Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, Long.toString(nonce));
        } catch (final Exception ex) {
            throw new IllegalStateException("Unable to persist admin nonce file: " + config.nonceFile(), ex);
        }
    }

    private static byte[] loadHmacKey(final AdminConfig config) {
        if (config.hmacKeyBase64Dev() != null && !config.hmacKeyBase64Dev().isBlank()) {
            return Base64.getDecoder().decode(config.hmacKeyBase64Dev());
        }
        throw new IllegalStateException("Vault-backed admin HMAC loading is not available in this standalone client yet");
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

    @Override
    public void close() {
        adminPublication.close();
        aeron.close();
    }
}
