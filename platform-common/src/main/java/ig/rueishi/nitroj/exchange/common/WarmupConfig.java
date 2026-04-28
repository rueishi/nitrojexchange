package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable warmup policy used before latency-sensitive process components go live.
 *
 * <p>Responsibility: defines how many warmup iterations to run and whether C2
 * verification is required. Role in system: gateway and cluster startup inject
 * this into warmup tooling rather than hardcoding production or development
 * behavior. Relationships: {@link GatewayConfig#forTest()} deliberately chooses
 * {@link #development()} so tests do not wait for production warmup. Lifecycle:
 * created at configuration load or via one of the static factories. Design
 * intent: the three-field class is the AMB-002 fixed shape from the spec, with a
 * production profile that verifies low-latency readiness and a development
 * profile optimized for repeatable tests.
 */
public final class WarmupConfig {
    private final int iterations;
    private final boolean requireC2Verified;
    private final long thresholdNanos;

    /**
     * Creates a warmup policy.
     *
     * @param iterations number of warmup iterations to run
     * @param requireC2Verified whether the startup path must enforce C2 readiness
     * @param thresholdNanos average-iteration threshold used by C2 verification
     */
    public WarmupConfig(final int iterations, final boolean requireC2Verified, final long thresholdNanos) {
        this.iterations = iterations;
        this.requireC2Verified = requireC2Verified;
        this.thresholdNanos = thresholdNanos;
    }

    public int iterations() { return iterations; }
    public boolean requireC2Verified() { return requireC2Verified; }
    public long thresholdNanos() { return thresholdNanos; }

    /**
     * Returns the production warmup profile required before live startup.
     *
     * @return 100,000-iteration C2-verifying warmup config
     */
    public static WarmupConfig production() {
        return new WarmupConfig(100_000, true, 500);
    }

    /**
     * Returns the development/test warmup profile.
     *
     * @return 10,000-iteration warmup config with no C2 gate
     */
    public static WarmupConfig development() {
        return new WarmupConfig(10_000, false, 0);
    }
}
