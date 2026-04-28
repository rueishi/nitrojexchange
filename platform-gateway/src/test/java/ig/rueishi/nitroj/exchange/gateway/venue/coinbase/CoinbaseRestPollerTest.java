package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.RestConfig;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestDecoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for TASK-014 REST balance polling.
 *
 * <p>Responsibility: verify Coinbase authentication, account JSON parsing,
 * unknown-currency filtering, and failure sentinel publication for
 * {@link CoinbaseRestPoller}. Role in system: these tests lock down the gateway REST
 * reconciliation path before the poller is wired into process startup.
 * Relationships: the poller is exercised with generated SBE balance-query
 * decoders, an injectable HTTP transport, a deterministic registry, and an
 * in-memory response publisher. Lifecycle: each test enqueues one request and
 * drains exactly one poller item to mimic serialized rest-poller-thread work.
 * Design intent: keep protocol behavior deterministic without relying on
 * external Coinbase services.</p>
 */
final class CoinbaseRestPollerTest {
    private static final int VENUE_ID = 1;
    private static final int BTC_ID = 11;
    private static final int ETH_ID = 12;
    private static final String SECRET = Base64.getEncoder().encodeToString("super-secret".getBytes(StandardCharsets.UTF_8));

    /** Verifies an HTTP 200 account response publishes one balance response. */
    @Test
    void balanceRequest_httpSuccess_responsePublished() {
        final Harness harness = new Harness(200, """
            [{"currency":"BTC","available":"1.25","balance":"99"}]
            """);

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published).containsExactly(new Published(VENUE_ID, BTC_ID, 125_000_000L, 999L));
    }

    /** Verifies multiple configured currencies in one Coinbase response publish independently. */
    @Test
    void balanceRequest_parsesAllCurrencies() {
        final Harness harness = new Harness(200, """
            [{"currency":"BTC","available":"1.25"},{"currency":"ETH","available":"2.5"}]
            """);

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published)
            .containsExactly(
                new Published(VENUE_ID, BTC_ID, 125_000_000L, 999L),
                new Published(VENUE_ID, ETH_ID, 250_000_000L, 999L));
    }

    /** Verifies transport timeouts publish the -1 failure sentinel. */
    @Test
    void balanceRequest_httpTimeout_errorSentinelPublished() {
        final Harness harness = new Harness(request -> {
            throw new IOException("timeout");
        });

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published).containsExactly(new Published(VENUE_ID, BTC_ID, -1L, 999L));
    }

    /** Verifies non-200 HTTP status publishes the -1 failure sentinel. */
    @Test
    void balanceRequest_http401_errorSentinelPublished() {
        final Harness harness = new Harness(401, "unauthorized");

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published).containsExactly(new Published(VENUE_ID, BTC_ID, -1L, 999L));
    }

    /** Verifies CB-ACCESS-SIGN matches Coinbase's timestamp + method + path HMAC. */
    @Test
    void authHeader_cbAccessSign_computedCorrectly() throws Exception {
        final Harness harness = new Harness(200, "[]");

        final HttpRequest request = harness.poller.buildAuthenticatedRequest("1713990000");

        assertThat(request.headers().firstValue("CB-ACCESS-KEY")).contains("key");
        assertThat(request.headers().firstValue("CB-ACCESS-TIMESTAMP")).contains("1713990000");
        assertThat(request.headers().firstValue("CB-ACCESS-PASSPHRASE")).contains("pass");
        assertThat(request.headers().firstValue("CB-ACCESS-SIGN")).contains(expectedSignature("1713990000GET/accounts"));
    }

    /** Verifies unconfigured currencies are silently ignored. */
    @Test
    void unknownCurrency_skipped_noResponsePublished() {
        final Harness harness = new Harness(200, """
            [{"currency":"LINK","available":"100"}]
            """);

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published).isEmpty();
    }

    @Test
    void malformedJson_publishesFailureSentinelWithoutEscapingJsonTypes() {
        final Harness harness = new Harness(200, "{bad-json");

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published).containsExactly(new Published(VENUE_ID, BTC_ID, -1L, 999L));
    }

    @Test
    void jsonUsage_confinedToCoinbaseRestPollerProductionCode() throws Exception {
        final Path sourceRoot = Path.of("src/main/java");
        final List<Path> jsonUsers;
        try (var stream = Files.walk(sourceRoot)) {
            jsonUsers = stream
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("org.json")
                            || Files.readString(path).contains("JSONObject")
                            || Files.readString(path).contains("JSONArray");
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    }
                })
                .toList();
        }

        assertThat(jsonUsers)
            .containsExactly(Path.of(
                "src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseRestPoller.java"));
    }

    /** Verifies reconciliation uses available funds rather than total balance. */
    @Test
    void available_usedNotBalance_forReconciliation() {
        final Harness harness = new Harness(200, """
            [{"currency":"BTC","available":"1.25","balance":"99"}]
            """);

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published.getFirst().balanceScaled).isEqualTo(125_000_000L);
    }

    /** Verifies two configured currencies produce two response publications. */
    @Test
    void balanceRequest_twoConfiguredCurrencies_twoResponsesPublished() {
        final Harness harness = new Harness(200, """
            [{"currency":"BTC","available":"0.1"},{"currency":"ETH","available":"0.2"}]
            """);

        harness.poller.onBalanceQueryRequest(requestDecoder(BTC_ID));
        harness.poller.drainOnceForTest();

        assertThat(harness.published).hasSize(2);
        assertThat(harness.published).extracting(Published::instrumentId).containsExactly(BTC_ID, ETH_ID);
    }

    static BalanceQueryRequestDecoder requestDecoder(final int instrumentId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        final BalanceQueryRequestEncoder encoder = new BalanceQueryRequestEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(VENUE_ID)
            .instrumentId(instrumentId)
            .requestId(77L);
        final BalanceQueryRequestDecoder decoder = new BalanceQueryRequestDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder());
        return decoder;
    }

    static CredentialsConfig credentials() {
        return CredentialsConfig.hardcoded("key", SECRET, "pass");
    }

    static RestConfig restConfig(final String baseUrl, final int timeoutMs) {
        return RestConfig.builder().baseUrl(baseUrl).pollIntervalMs(5).timeoutMs(timeoutMs).build();
    }

    static String expectedSignature(final String prehash) throws Exception {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
    }

    record Published(int venueId, int instrumentId, long balanceScaled, long queryTimestampNanos) {
    }

    static final class Harness {
        final List<Published> published = new ArrayList<>();
        final CoinbaseRestPoller poller;

        Harness(final int statusCode, final String body) {
            this(request -> new StringResponse(request, statusCode, body));
        }

        Harness(final CoinbaseRestPoller.HttpTransport transport) {
            poller = new CoinbaseRestPoller(
                restConfig("http://localhost:18080", 50),
                credentials(),
                new TestRegistry(),
                (venueId, instrumentId, balanceScaled, queryTimestampNanos) ->
                    published.add(new Published(venueId, instrumentId, balanceScaled, queryTimestampNanos)),
                transport,
                () -> 1_713_990_000L,
                () -> 999L);
        }
    }

    static final class TestRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            if ("BTC-USD".contentEquals(symbol)) {
                return BTC_ID;
            }
            if ("ETH-USD".contentEquals(symbol)) {
                return ETH_ID;
            }
            return 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return switch (instrumentId) {
                case BTC_ID -> "BTC-USD";
                case ETH_ID -> "ETH-USD";
                default -> null;
            };
        }

        @Override
        public String venueNameOf(final int venueId) {
            return "coinbase";
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
        }
    }

    static final class StringResponse implements HttpResponse<String> {
        private final HttpRequest request;
        private final int statusCode;
        private final String body;

        StringResponse(final HttpRequest request, final int statusCode, final String body) {
            this.request = request;
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
