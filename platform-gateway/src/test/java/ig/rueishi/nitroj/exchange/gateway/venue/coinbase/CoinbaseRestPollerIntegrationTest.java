package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import com.sun.net.httpserver.HttpServer;
import ig.rueishi.nitroj.exchange.common.RestConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local integration coverage for TASK-014 REST polling.
 *
 * <p>Responsibility: exercise {@link CoinbaseRestPoller} with the real Java
 * {@link java.net.http.HttpClient} against a local JDK HTTP server or an
 * unavailable endpoint. Role in system: this complements the unit tests by
 * verifying the asynchronous queue and real HTTP timeout behavior used by the
 * rest-poller thread. Relationships: it reuses the unit-test registry and
 * credentials while avoiding external Coinbase dependencies. Lifecycle: each
 * test owns its server/poller and stops both before returning. Design intent:
 * prove requests are serialized by the poller loop and failure sentinels arrive
 * within the configured timeout budget.</p>
 */
final class CoinbaseRestPollerIntegrationTest {
    /** Verifies concurrent enqueue callers are serialized by the single poller loop. */
    @Test
    void concurrentBalanceRequests_serialized_noRaceCondition() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicInteger requests = new AtomicInteger();
        server.createContext("/accounts", exchange -> {
            final int active = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(active, Math::max);
            requests.incrementAndGet();
            try {
                final byte[] body = "[{\"currency\":\"BTC\",\"available\":\"1\"}]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                inFlight.decrementAndGet();
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        final List<CoinbaseRestPollerTest.Published> published = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(2);
        final CoinbaseRestPoller poller = realPoller(server, 500, (venueId, instrumentId, balanceScaled, queryTimestampNanos) -> {
            published.add(new CoinbaseRestPollerTest.Published(venueId, instrumentId, balanceScaled, queryTimestampNanos));
            latch.countDown();
        });
        final Thread thread = new Thread(poller, "rest-poller-test");
        thread.start();
        try {
            poller.onBalanceQueryRequest(CoinbaseRestPollerTest.requestDecoder(11));
            poller.onBalanceQueryRequest(CoinbaseRestPollerTest.requestDecoder(11));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasValue(2);
            assertThat(maxInFlight).hasValue(1);
            assertThat(published).hasSize(2);
        } finally {
            poller.stop();
            thread.interrupt();
            thread.join(1_000L);
            server.stop(0);
        }
    }

    /** Verifies an unavailable HTTP server produces a sentinel before the timeout budget expires. */
    @Test
    void httpServer_unavailable_sentinelWithinTimeout() throws Exception {
        final int unusedPort = unusedPort();
        final List<CoinbaseRestPollerTest.Published> published = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CoinbaseRestPoller poller = realPoller("http://127.0.0.1:" + unusedPort, 200,
            (venueId, instrumentId, balanceScaled, queryTimestampNanos) -> {
                published.add(new CoinbaseRestPollerTest.Published(venueId, instrumentId, balanceScaled, queryTimestampNanos));
                latch.countDown();
            });
        final Thread thread = new Thread(poller, "rest-poller-unavailable-test");
        final long started = System.nanoTime();
        thread.start();
        try {
            poller.onBalanceQueryRequest(CoinbaseRestPollerTest.requestDecoder(11));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000L);
            assertThat(published).containsExactly(new CoinbaseRestPollerTest.Published(1, 11, -1L, 999L));
        } finally {
            poller.stop();
            thread.interrupt();
            thread.join(1_000L);
        }
    }

    private static CoinbaseRestPoller realPoller(
        final HttpServer server,
        final int timeoutMs,
        final CoinbaseRestPoller.ResponsePublisher publisher) {
        return realPoller("http://127.0.0.1:" + server.getAddress().getPort(), timeoutMs, publisher);
    }

    private static CoinbaseRestPoller realPoller(
        final String baseUrl,
        final int timeoutMs,
        final CoinbaseRestPoller.ResponsePublisher publisher) {
        final RestConfig restConfig = CoinbaseRestPollerTest.restConfig(baseUrl, timeoutMs);
        final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
        return new CoinbaseRestPoller(
            restConfig,
            CoinbaseRestPollerTest.credentials(),
            new CoinbaseRestPollerTest.TestRegistry(),
            publisher,
            request -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
            () -> 1_713_990_000L,
            () -> 999L);
    }

    private static int unusedPort() throws IOException {
        final java.net.ServerSocket socket = new java.net.ServerSocket(0);
        try (socket) {
            return socket.getLocalPort();
        }
    }
}
