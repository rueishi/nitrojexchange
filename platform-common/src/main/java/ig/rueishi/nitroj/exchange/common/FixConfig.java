package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable gateway FIX session configuration.
 *
 * <p>Responsibility: carries sender/target CompIDs and Artio session storage
 * settings from gateway TOML. Role in system: gateway startup uses this record
 * when creating Artio sessions and later registers the session identity with the
 * ID registry. Relationships: venue network endpoints live in {@link VenueConfig}
 * while per-gateway FIX identity lives here because it is credential-bound.
 * Lifecycle: created once by {@link ConfigManager#loadGateway(String)} or
 * {@link GatewayConfig#forTest()}. Design intent: isolate session identity from
 * permanent venue metadata.
 */
public record FixConfig(
    String senderCompId,
    String targetCompId,
    int heartbeatIntervalS,
    int reconnectIntervalMs,
    boolean resetSeqNumOnLogon,
    String artioLogDir,
    int artioReplayCapacity
) {
    public static Builder builder() {
        return new Builder();
    }

    /** Builder used by test factories and TOML mapping code. */
    public static final class Builder {
        private String senderCompId;
        private String targetCompId;
        private int heartbeatIntervalS;
        private int reconnectIntervalMs;
        private boolean resetSeqNumOnLogon;
        private String artioLogDir;
        private int artioReplayCapacity;

        public Builder senderCompId(final String value) { senderCompId = value; return this; }
        public Builder targetCompId(final String value) { targetCompId = value; return this; }
        public Builder heartbeatIntervalS(final int value) { heartbeatIntervalS = value; return this; }
        public Builder reconnectIntervalMs(final int value) { reconnectIntervalMs = value; return this; }
        public Builder resetSeqNumOnLogon(final boolean value) { resetSeqNumOnLogon = value; return this; }
        public Builder artioLogDir(final String value) { artioLogDir = value; return this; }
        public Builder artioReplayCapacity(final int value) { artioReplayCapacity = value; return this; }

        public FixConfig build() {
            return new FixConfig(senderCompId, targetCompId, heartbeatIntervalS, reconnectIntervalMs,
                resetSeqNumOnLogon, artioLogDir, artioReplayCapacity);
        }
    }
}
