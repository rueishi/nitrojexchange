package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable REST polling configuration for gateway-side venue reconciliation.
 *
 * <p>Responsibility: carries the REST base URL and polling/timeout settings from
 * gateway TOML. Role in system: REST polling components use this record for
 * timeout-safe calls without reading raw TOML. Lifecycle: created at process
 * startup and passed to gateway services. Design intent: separate the REST
 * endpoint from FIX session configuration because the two protocols have
 * different failure modes and lifecycle owners.
 */
public record RestConfig(String baseUrl, int pollIntervalMs, int timeoutMs) {
    public static Builder builder() {
        return new Builder();
    }

    /** Builder used by TOML loading and the gateway test factory. */
    public static final class Builder {
        private String baseUrl;
        private int pollIntervalMs;
        private int timeoutMs;

        public Builder baseUrl(final String value) { baseUrl = value; return this; }
        public Builder pollIntervalMs(final int value) { pollIntervalMs = value; return this; }
        public Builder timeoutMs(final int value) { timeoutMs = value; return this; }

        public RestConfig build() {
            return new RestConfig(baseUrl, pollIntervalMs, timeoutMs);
        }
    }
}
