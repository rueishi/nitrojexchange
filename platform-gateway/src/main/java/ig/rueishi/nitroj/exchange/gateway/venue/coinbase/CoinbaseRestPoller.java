package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.RestConfig;
import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.OrderCommandHandler;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestDecoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * REST balance poller for Coinbase account reconciliation.
 *
 * <p>Responsibility: accept {@link BalanceQueryRequestDecoder} commands from
 * gateway egress, serialize them onto the REST poller thread, call Coinbase
 * {@code GET /accounts}, parse configured account balances, and publish
 * {@link ig.rueishi.nitroj.exchange.messages.BalanceQueryResponse} SBE messages through
 * the gateway disruptor. Role in system: this class is the REST-side companion
 * to FIX execution routing, used during recovery and reconciliation when the
 * cluster needs authoritative venue balances. Relationships: {@link RestConfig}
 * supplies endpoint and timeout policy, {@link CredentialsConfig} supplies
 * Coinbase authentication material, {@link IdRegistry} filters venue currencies
 * to configured instruments, and {@link GatewayDisruptor} carries encoded
 * responses to cluster ingress. JSON parsing is deliberately confined to this
 * Coinbase REST-owned class; downstream gateway/cluster hot paths see only
 * primitive SBE balance responses. Lifecycle: gateway wiring creates one poller,
 * {@link OrderCommandHandler} enqueues requests via
 * {@link #onBalanceQueryRequest(BalanceQueryRequestDecoder)}, and a dedicated
 * rest-poller thread runs {@link #run()} until stopped. Design intent: copy SBE
 * request fields at enqueue time, keep HTTP and JSON work off the egress thread,
 * and publish a {@code -1} balance sentinel on transport failure so cluster
 * recovery can fail closed.</p>
 */
public final class CoinbaseRestPoller implements Runnable, OrderCommandHandler.BalanceQuerySink {
    static final String ACCOUNTS_PATH = "/accounts";
    static final long ERROR_BALANCE_SENTINEL = -1L;

    private final RestConfig restConfig;
    private final CredentialsConfig credentials;
    private final IdRegistry idRegistry;
    private final ResponsePublisher responsePublisher;
    private final HttpTransport httpTransport;
    private final LongSupplier epochSeconds;
    private final LongSupplier nanoClock;
    private final BlockingQueue<BalanceRequest> requests = new LinkedBlockingQueue<>();
    private final Mac mac;
    private volatile boolean running = true;

    /**
     * Creates a production REST poller backed by Java 21 {@link HttpClient}.
     *
     * @param restConfig REST endpoint and timeout settings
     * @param credentials Coinbase API key, secret, and passphrase
     * @param idRegistry registry used to map currency symbols to instruments
     * @param disruptor gateway handoff ring for encoded SBE responses
     */
    public CoinbaseRestPoller(
        final RestConfig restConfig,
        final CredentialsConfig credentials,
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor) {
        this(
            restConfig,
            credentials,
            idRegistry,
            new DisruptorResponsePublisher(disruptor),
            new JavaHttpTransport(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(restConfig.timeoutMs()))
                .build()),
            () -> System.currentTimeMillis() / 1_000L,
            System::nanoTime);
    }

    CoinbaseRestPoller(
        final RestConfig restConfig,
        final CredentialsConfig credentials,
        final IdRegistry idRegistry,
        final ResponsePublisher responsePublisher,
        final HttpTransport httpTransport,
        final LongSupplier epochSeconds,
        final LongSupplier nanoClock) {
        this.restConfig = Objects.requireNonNull(restConfig, "restConfig");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.responsePublisher = Objects.requireNonNull(responsePublisher, "responsePublisher");
        this.httpTransport = Objects.requireNonNull(httpTransport, "httpTransport");
        this.epochSeconds = Objects.requireNonNull(epochSeconds, "epochSeconds");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.mac = newMac(credentials.secretBase64());
    }

    /**
     * Copies a balance request from the reusable SBE decoder into the poller queue.
     *
     * <p>{@link OrderCommandHandler} calls this on the gateway egress thread.
     * The decoder instance is reused by that handler, so this method stores only
     * primitive values before returning. The queue is unbounded because recovery
     * requests are sparse and losing one would hide balance-reconciliation
     * failures from the cluster.</p>
     *
     * @param decoder decoded balance query request
     */
    @Override
    public void onBalanceQueryRequest(final BalanceQueryRequestDecoder decoder) {
        requests.add(new BalanceRequest(decoder.venueId(), decoder.instrumentId(), decoder.requestId()));
    }

    /**
     * Runs the serialized REST polling loop.
     *
     * <p>The loop waits for queued balance requests, performs each HTTP call to
     * completion, and sleeps according to {@link RestConfig#pollIntervalMs()} only
     * when idle. Interruptions request shutdown while preserving the interrupt
     * flag for gateway lifecycle code.</p>
     */
    @Override
    public void run() {
        while (running) {
            try {
                final BalanceRequest request = requests.poll(restConfig.pollIntervalMs(), TimeUnit.MILLISECONDS);
                if (request != null) {
                    process(request);
                }
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    /**
     * Requests shutdown of the REST poller loop.
     */
    public void stop() {
        running = false;
    }

    int queuedRequestCount() {
        return requests.size();
    }

    void drainOnceForTest() {
        final BalanceRequest request = requests.poll();
        if (request != null) {
            process(request);
        }
    }

    HttpRequest buildAuthenticatedRequest() {
        return buildAuthenticatedRequest(Long.toString(epochSeconds.getAsLong()));
    }

    HttpRequest buildAuthenticatedRequest(final String timestamp) {
        final String prehash = timestamp + "GET" + ACCOUNTS_PATH;
        final String signature;
        synchronized (mac) {
            mac.reset();
            signature = Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
        }

        return HttpRequest.newBuilder(URI.create(restConfig.baseUrl() + ACCOUNTS_PATH))
            .timeout(Duration.ofMillis(restConfig.timeoutMs()))
            .GET()
            .header("CB-ACCESS-KEY", credentials.apiKey())
            .header("CB-ACCESS-SIGN", signature)
            .header("CB-ACCESS-TIMESTAMP", timestamp)
            .header("CB-ACCESS-PASSPHRASE", credentials.passphrase())
            .build();
    }

    private void process(final BalanceRequest request) {
        try {
            final HttpResponse<String> response = httpTransport.send(buildAuthenticatedRequest());
            if (response.statusCode() != 200) {
                publishFailure(request);
                return;
            }
            parseAndPublish(request, response.body());
        } catch (final IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            publishFailure(request);
        }
    }

    private void parseAndPublish(final BalanceRequest request, final String responseBody) {
        final JSONArray accounts = new JSONArray(responseBody);
        for (int i = 0; i < accounts.length(); i++) {
            final JSONObject account = accounts.getJSONObject(i);
            final String currency = account.getString("currency");
            final int instrumentId = idRegistry.instrumentId(currency + "-USD");
            if (instrumentId == 0) {
                continue;
            }

            // Coinbase exposes both "balance" and "available"; reconciliation
            // must use available funds so locked/open-order funds are excluded.
            final ParsedBalance balance = new ParsedBalance(
                request.venueId,
                instrumentId,
                ConfigManager.parseScaled(account.getString("available")));
            responsePublisher.publish(balance.venueId, balance.instrumentId, balance.balanceScaled, nanoClock.getAsLong());
        }
    }

    private void publishFailure(final BalanceRequest request) {
        responsePublisher.publish(
            request.venueId,
            request.instrumentId,
            ERROR_BALANCE_SENTINEL,
            nanoClock.getAsLong());
    }

    private static Mac newMac(final String secretBase64) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256"));
            return mac;
        } catch (final NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalArgumentException("Unable to initialize Coinbase REST HMAC", ex);
        }
    }

    private record BalanceRequest(int venueId, int instrumentId, long requestId) {
    }

    /**
     * Cold-path DTO crossing the JSON boundary.
     *
     * <p>No {@code org.json} type is exposed outside {@link CoinbaseRestPoller};
     * the poller converts account fields to primitive platform IDs and scaled
     * balances before publishing into the gateway handoff path.</p>
     */
    private record ParsedBalance(int venueId, int instrumentId, long balanceScaled) {
    }

    /**
     * Sends one authenticated HTTP request and returns the string response.
     */
    @FunctionalInterface
    interface HttpTransport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    /**
     * Publishes decoded balance responses to the gateway handoff path.
     */
    @FunctionalInterface
    interface ResponsePublisher {
        void publish(int venueId, int instrumentId, long balanceScaled, long queryTimestampNanos);
    }

    private static final class JavaHttpTransport implements HttpTransport {
        private final HttpClient client;

        private JavaHttpTransport(final HttpClient client) {
            this.client = client;
        }

        @Override
        public HttpResponse<String> send(final HttpRequest request) throws IOException, InterruptedException {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private static final class DisruptorResponsePublisher implements ResponsePublisher {
        private final GatewayDisruptor disruptor;
        private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        private final BalanceQueryResponseEncoder responseEncoder = new BalanceQueryResponseEncoder();

        private DisruptorResponsePublisher(final GatewayDisruptor disruptor) {
            this.disruptor = Objects.requireNonNull(disruptor, "disruptor");
        }

        @Override
        public void publish(
            final int venueId,
            final int instrumentId,
            final long balanceScaled,
            final long queryTimestampNanos) {
            GatewaySlot slot = disruptor.claimSlot();
            while (slot == null) {
                Thread.onSpinWait();
                slot = disruptor.claimSlot();
            }

            responseEncoder.wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
                .venueId(venueId)
                .instrumentId(instrumentId)
                .balanceScaled(balanceScaled)
                .queryTimestampNanos(queryTimestampNanos);
            slot.length = MessageHeaderEncoder.ENCODED_LENGTH + responseEncoder.encodedLength();
            disruptor.publishSlot(slot);
        }
    }
}
