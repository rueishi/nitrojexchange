package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.HeaderEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.LogonEncoder;
import uk.co.real_logic.artio.builder.AbstractLogonEncoder;
import uk.co.real_logic.artio.builder.AbstractLogoutEncoder;
import uk.co.real_logic.artio.session.SessionCustomisationStrategy;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;

/**
 * Adds Coinbase-specific authentication fields to Artio FIX Logon messages.
 *
 * <p>The gateway uses Artio's {@link SessionCustomisationStrategy} hook when a
 * FIX session is created or reconnected. Artio builds the base session header,
 * then invokes this strategy so NitroJEx can add venue-required fields without
 * hand-editing raw FIX buffers. This class is deliberately narrow: it owns only
 * the Coinbase Logon signature and proprietary cancel-on-disconnect tag, while
 * session lifecycle registration and message handling stay in gateway runtime
 * code.</p>
 *
 * <p>The implementation depends on the version-isolated generated Artio
 * {@link LogonEncoder} for the configured FIXT.1.1/FIX 5.0SP2 plugin. The
 * compatibility dictionary declares proprietary tag
 * {@code 8013=CancelOrdersOnDisconnect}, giving this strategy a strongly typed
 * setter and keeping checksum/body-length handling inside Artio. Coinbase
 * credentials are accepted at construction time, the API secret is Base64-decoded
 * once, and a reusable {@link Mac} instance is kept for the rare logon/reconnect
 * path.</p>
 */
public final class CoinbaseLogonStrategy implements SessionCustomisationStrategy {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String LOGON_MSG_TYPE = "A";
    private static final int ENCRYPT_METHOD_NONE = 0;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final char CANCEL_ORDERS_ON_DISCONNECT = 'Y';

    private final String apiPassphrase;
    private final byte[] apiSecretDecoded;
    private final Mac mac;

    /**
     * Creates the strategy from Coinbase API credentials loaded by gateway
     * configuration.
     *
     * <p>The API key is validated for wiring completeness even though Coinbase's
     * FIX Logon HMAC formula for this task uses the passphrase, not the key. The
     * secret must be Base64 encoded; decoding it in the constructor prevents the
     * common integration mistake of using the printable Base64 text as the HMAC
     * key. No credential values are logged or included in exception messages.</p>
     *
     * @param apiKey Coinbase API key, validated for non-null gateway wiring
     * @param apiSecret Base64-encoded Coinbase API secret
     * @param apiPassphrase Coinbase API passphrase written to tag 554
     * @throws NullPointerException if any credential input is null
     * @throws IllegalArgumentException if {@code apiSecret} is not valid Base64
     * @throws IllegalStateException if Java 21 does not provide HMAC-SHA256
     */
    public CoinbaseLogonStrategy(final String apiKey, final String apiSecret, final String apiPassphrase) {
        Objects.requireNonNull(apiKey, "apiKey");
        this.apiPassphrase = Objects.requireNonNull(apiPassphrase, "apiPassphrase");
        this.apiSecretDecoded = Base64.getDecoder().decode(Objects.requireNonNull(apiSecret, "apiSecret"));
        try {
            this.mac = Mac.getInstance(HMAC_ALGORITHM);
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }

    /**
     * Writes Coinbase authentication fields to Artio's generated Logon encoder.
     *
     * <p>Artio calls this method after it has populated the session header. The
     * signature prehash is built from those header values exactly as Coinbase
     * specifies: sending time, message type {@code A}, sequence number, sender
     * CompID, target CompID, and API passphrase, with no delimiters. The HMAC is
     * Base64 encoded into tag 96, tag 95 is set to the signature byte length, and
     * tag 8013 is set through the dictionary-generated setter so Artio still owns
     * message framing and checksum calculation.</p>
     *
     * @param abstractLogon Artio Logon encoder supplied for a new or reconnecting
     *                      FIX session
     * @param sessionId Artio session id; not used by Coinbase's signature formula
     * @throws IllegalArgumentException if the gateway is not wired to the generated
     *                                  Coinbase Logon/Header encoder classes
     * @throws IllegalStateException if HMAC signing fails unexpectedly
     */
    @Override
    public synchronized void configureLogon(final AbstractLogonEncoder abstractLogon, final long sessionId) {
        if (!(abstractLogon instanceof LogonEncoder logon)) {
            throw new IllegalArgumentException(
                "Coinbase logon requires the generated FIXT.1.1/FIX 5.0SP2 Coinbase LogonEncoder");
        }
        final HeaderEncoder header = logon.header();

        final String prehash = buildPrehash(
            header.sendingTimeAsString(),
            header.msgSeqNum(),
            header.senderCompIDAsString(),
            header.targetCompIDAsString(),
            apiPassphrase);
        final String signature = computeSignature(mac, apiSecretDecoded, prehash);
        final byte[] signatureBytes = signature.getBytes(StandardCharsets.US_ASCII);

        logon.encryptMethod(ENCRYPT_METHOD_NONE);
        logon.heartBtInt(HEARTBEAT_INTERVAL_SECONDS);
        logon.password(apiPassphrase);
        logon.rawDataLength(signatureBytes.length);
        logon.rawData(signatureBytes);
        logon.cancelOrdersOnDisconnect(CANCEL_ORDERS_ON_DISCONNECT);
    }

    /**
     * Leaves Coinbase Logout messages unmodified.
     *
     * <p>Artio 0.175 requires customisation strategies to implement both logon
     * and logout hooks. Coinbase authentication for this task is Logon-only, so
     * the logout hook intentionally has no side effects.</p>
     *
     * @param logout Artio Logout encoder supplied by the session library
     * @param sessionId Artio session id for the logout being encoded
     */
    @Override
    public void configureLogout(final AbstractLogoutEncoder logout, final long sessionId) {
        // Coinbase requires no proprietary logout fields.
    }

    /**
     * Builds Coinbase's exact FIX Logon signature prehash string.
     *
     * @param sendingTime FIX tag 52 as already written by Artio
     * @param msgSeqNum FIX tag 34 sequence number
     * @param senderCompId FIX tag 49 sender CompID
     * @param targetCompId FIX tag 56 target CompID
     * @param password Coinbase passphrase from tag 554
     * @return delimiter-free prehash string required by Coinbase
     */
    static String buildPrehash(
        final String sendingTime,
        final int msgSeqNum,
        final String senderCompId,
        final String targetCompId,
        final String password) {

        return sendingTime + LOGON_MSG_TYPE + msgSeqNum + senderCompId + targetCompId + password;
    }

    /**
     * Computes the Base64 HMAC-SHA256 signature written to FIX tag 96.
     *
     * @param mac reusable HMAC instance owned by the strategy
     * @param decodedSecret Base64-decoded Coinbase API secret bytes
     * @param prehash exact prehash string from {@link #buildPrehash(String, int, String, String, String)}
     * @return Base64-encoded HMAC digest
     * @throws IllegalStateException if the JCA provider rejects HMAC initialization
     */
    static String computeSignature(final Mac mac, final byte[] decodedSecret, final String prehash) {
        try {
            mac.init(new SecretKeySpec(decodedSecret, HMAC_ALGORITHM));
            final byte[] hmacBytes = mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("Coinbase logon signature failed", ex);
        }
    }
}
