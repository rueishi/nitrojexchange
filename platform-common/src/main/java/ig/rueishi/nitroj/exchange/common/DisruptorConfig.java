package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable Disruptor buffer sizing used by the gateway process.
 *
 * <p>Responsibility: carries ring-buffer capacity and slot size from gateway
 * TOML. Role in system: the gateway disruptor wiring consumes this during
 * startup to size the command/event transfer buffer. Lifecycle: created once by
 * {@link ConfigManager#loadGateway(String)} or the gateway test factory. Design
 * intent: keep queue capacity explicit and immutable because changing it at
 * runtime would alter back-pressure behavior.
 */
public record DisruptorConfig(int ringBufferSize, int slotSizeBytes) {
    public static Builder builder() {
        return new Builder();
    }

    /** Builder used by factories that should read like the spec examples. */
    public static final class Builder {
        private int ringBufferSize;
        private int slotSizeBytes;

        public Builder ringBufferSize(final int value) { ringBufferSize = value; return this; }
        public Builder slotSizeBytes(final int value) { slotSizeBytes = value; return this; }

        public DisruptorConfig build() {
            return new DisruptorConfig(ringBufferSize, slotSizeBytes);
        }
    }
}
