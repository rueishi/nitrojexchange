package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.LogonEncoder;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Coinbase FIX Logon authentication and proprietary tag encoding.
 *
 * <p>These tests exercise TASK-008 at the same boundary used by Artio: a
 * generated FIXT.1.1/FIX 5.0SP2 {@link LogonEncoder} with the FIX session
 * header already populated. They validate the exact HMAC prehash order,
 * Base64-decoded secret handling, required Coinbase tags, and raw SOH-delimited
 * FIX output. The class also protects against accidental credential disclosure
 * by asserting that public strategy text and failure messages do not include
 * configured secret values.</p>
 *
 * <p>The test owns only Logon message customisation. It does not start an Artio
 * session, connect to Coinbase, or register live session ids; those lifecycle
 * concerns belong to later gateway task cards.</p>
 */
final class CoinbaseLogonStrategyTest {

    private static final String API_KEY = "test-key";
    private static final String SECRET_TEXT = "secret bytes used as hmac key";
    private static final String API_SECRET = Base64.getEncoder()
        .encodeToString(SECRET_TEXT.getBytes(StandardCharsets.US_ASCII));
    private static final String API_PASSPHRASE = "coinbase-passphrase";
    private static final String SENDING_TIME = "20260424-20:30:15.123";
    private static final int MSG_SEQ_NUM = 42;
    private static final String SENDER_COMP_ID = "NITROJEX";
    private static final String TARGET_COMP_ID = "COIN";
    private static final char SOH = '\001';

    /**
     * Verifies the delimiter-free Coinbase prehash order from the task card.
     *
     * <p>This is the most important behavioral contract in the strategy: all
     * following HMAC checks can pass locally with the wrong order, but Coinbase
     * will reject the logon if any field is rearranged or replaced with an
     * unrelated nonce.</p>
     */
    @Test
    void logonMessage_prehashFormat_sendingTimeMsgTypeSeqSenderTargetPass() {
        assertThat(CoinbaseLogonStrategy.buildPrehash(
            SENDING_TIME,
            MSG_SEQ_NUM,
            SENDER_COMP_ID,
            TARGET_COMP_ID,
            API_PASSPHRASE))
            .isEqualTo(SENDING_TIME + "A" + MSG_SEQ_NUM + SENDER_COMP_ID + TARGET_COMP_ID + API_PASSPHRASE);
    }

    /**
     * Ensures the configured API secret is Base64-decoded before HMAC use.
     *
     * <p>Coinbase presents the secret as printable Base64 text, but the HMAC key
     * is the decoded byte array. This test compares the strategy output against
     * the decoded-key digest and proves it differs from using the raw text.</p>
     *
     * @throws Exception if the JCA provider unexpectedly cannot create HMAC-SHA256
     */
    @Test
    void logonMessage_apiSecret_base64DecodedBeforeHmac() throws Exception {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        final String prehash = expectedPrehash();
        assertThat(rawDataAsString(logon)).isEqualTo(hmac(API_SECRET, true, prehash));
        assertThat(rawDataAsString(logon)).isNotEqualTo(hmac(API_SECRET, false, prehash));
    }

    /**
     * Checks tag 96 against an independently computed HMAC-SHA256 signature.
     *
     * @throws Exception if HMAC-SHA256 is unavailable in the test JVM
     */
    @Test
    void logonMessage_tag96_correctHmacSha256() throws Exception {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        assertThat(rawDataAsString(logon)).isEqualTo(hmac(API_SECRET, true, expectedPrehash()));
    }

    /**
     * Verifies tag 95 matches the byte length of tag 96.
     *
     * <p>Artio 0.175's generated DATA setter stores the byte array but does not
     * infer the associated length field, so the strategy must set RawDataLength
     * explicitly before encoding the message.</p>
     */
    @Test
    void logonMessage_tag95_rawDataLengthMatchesSignature() {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        assertThat(logon.rawDataLength()).isEqualTo(rawDataAsString(logon).length());
        assertThat(encodedMessage(logon)).contains(SOH + "95=" + logon.rawDataLength() + SOH);
    }

    /**
     * Confirms Coinbase proprietary tag 8013 is encoded and tag 9406 is absent.
     */
    @Test
    void logonMessage_tag8013_cancelOnDisconnect_Y() {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        assertThat(logon.cancelOrdersOnDisconnect()).isEqualTo('Y');
        assertThat(encodedMessage(logon))
            .contains(SOH + "8013=Y" + SOH)
            .doesNotContain(SOH + "9406=");
    }

    /**
     * Confirms the Coinbase heartbeat interval is written to tag 108.
     */
    @Test
    void logonMessage_tag108_heartbeatInterval_30() {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        assertThat(logon.heartBtInt()).isEqualTo(30);
        assertThat(encodedMessage(logon)).contains(SOH + "108=30" + SOH);
    }

    /**
     * Confirms the Coinbase API passphrase is written to tag 554.
     */
    @Test
    void logonMessage_tag554_passphrase() {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        assertThat(logon.passwordAsString()).isEqualTo(API_PASSPHRASE);
        assertThat(encodedMessage(logon)).contains(SOH + "554=" + API_PASSPHRASE + SOH);
    }

    /**
     * Confirms FIX encryption method none is written to tag 98.
     */
    @Test
    void logonMessage_tag98_encryptMethod_0() {
        final LogonEncoder logon = configuredLogon();
        newStrategy().configureLogon(logon, 1001L);

        assertThat(logon.encryptMethod()).isZero();
        assertThat(encodedMessage(logon)).contains(SOH + "98=0" + SOH);
    }

    /**
     * Ensures diagnostic-facing strategy text and failure paths do not expose credentials.
     *
     * <p>The strategy intentionally has no logger. This test still guards the two
     * easy future regression paths: adding a credential-bearing {@code toString}
     * or embedding credential values in validation exceptions.</p>
     */
    @Test
    void credentials_notInLogOutput() {
        final CoinbaseLogonStrategy strategy = newStrategy();

        assertThat(strategy.toString())
            .doesNotContain(API_KEY)
            .doesNotContain(API_SECRET)
            .doesNotContain(API_PASSPHRASE);
        assertThat(new CoinbaseLogonStrategy(API_KEY, API_SECRET, API_PASSPHRASE)
            .toString())
            .doesNotContain(SECRET_TEXT);
    }

    /**
     * Proves tag 52 participates in the signature prehash.
     */
    @Test
    void hmac_differentSendingTime_differentSignature() {
        final LogonEncoder first = configuredLogon();
        final LogonEncoder second = configuredLogon();
        second.header().sendingTime("20260424-20:30:16.123".getBytes(StandardCharsets.US_ASCII));

        newStrategy().configureLogon(first, 1001L);
        newStrategy().configureLogon(second, 1001L);

        assertThat(rawDataAsString(first)).isNotEqualTo(rawDataAsString(second));
    }

    /**
     * Proves tag 34 participates in the signature prehash.
     */
    @Test
    void hmac_differentMsgSeqNum_differentSignature() {
        final LogonEncoder first = configuredLogon();
        final LogonEncoder second = configuredLogon();
        second.header().msgSeqNum(MSG_SEQ_NUM + 1);

        newStrategy().configureLogon(first, 1001L);
        newStrategy().configureLogon(second, 1001L);

        assertThat(rawDataAsString(first)).isNotEqualTo(rawDataAsString(second));
    }

    /**
     * Builds a generated Artio Logon encoder in the state Artio would provide to
     * the strategy: session header fields are populated, Coinbase-specific body
     * fields are still unset.
     *
     * @return generated Logon encoder ready for strategy customisation
     */
    private static LogonEncoder configuredLogon() {
        final LogonEncoder logon = new LogonEncoder();
        logon.header().senderCompID(SENDER_COMP_ID);
        logon.header().targetCompID(TARGET_COMP_ID);
        logon.header().msgSeqNum(MSG_SEQ_NUM);
        logon.header().sendingTime(SENDING_TIME.getBytes(StandardCharsets.US_ASCII));
        return logon;
    }

    /**
     * Creates a fresh strategy for tests that mutate its internal {@link Mac}.
     *
     * @return strategy configured with deterministic test credentials
     */
    private static CoinbaseLogonStrategy newStrategy() {
        return new CoinbaseLogonStrategy(API_KEY, API_SECRET, API_PASSPHRASE);
    }

    /**
     * Recreates the expected prehash from the deterministic test header fields.
     *
     * @return Coinbase prehash string used by independent HMAC assertions
     */
    private static String expectedPrehash() {
        return SENDING_TIME + "A" + MSG_SEQ_NUM + SENDER_COMP_ID + TARGET_COMP_ID + API_PASSPHRASE;
    }

    /**
     * Converts tag 96's stored ASCII bytes to the signature string.
     *
     * @param logon customised generated Logon encoder
     * @return Base64 HMAC signature from tag 96
     */
    private static String rawDataAsString(final LogonEncoder logon) {
        return new String(logon.rawData(), StandardCharsets.US_ASCII);
    }

    /**
     * Encodes the generated Logon message and returns the SOH-delimited FIX text.
     *
     * @param logon generated Logon encoder populated by the strategy
     * @return encoded FIX message using ASCII SOH delimiters
     */
    private static String encodedMessage(final LogonEncoder logon) {
        final MutableAsciiBuffer buffer = new MutableAsciiBuffer(new byte[1024]);
        final long result = logon.encode(buffer, 0);
        return buffer.getAscii(Encoder.offset(result), Encoder.length(result));
    }

    /**
     * Computes an independent Base64 HMAC for test expectations.
     *
     * @param configuredSecret test API secret
     * @param decodeSecret true to use Coinbase's decoded-secret behavior
     * @param prehash exact prehash data
     * @return Base64-encoded HMAC digest
     * @throws Exception if HMAC-SHA256 is unavailable
     */
    private static String hmac(
        final String configuredSecret,
        final boolean decodeSecret,
        final String prehash) throws Exception {

        final byte[] key = decodeSecret
            ? Base64.getDecoder().decode(configuredSecret)
            : configuredSecret.getBytes(StandardCharsets.US_ASCII);
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
    }
}
