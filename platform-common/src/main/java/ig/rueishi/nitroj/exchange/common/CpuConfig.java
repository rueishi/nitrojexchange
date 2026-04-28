package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable CPU-affinity configuration shared by gateway and cluster startup.
 *
 * <p>Responsibility: stores configured CPU IDs for process loops. Role in system:
 * startup scripts and launchers can pin threads when a positive CPU ID is
 * provided; tests use {@link #noPinning()} to keep affinity disabled. Lifecycle:
 * loaded once from TOML and injected into process wiring. Design intent: use zero
 * as the explicit "no pinning" sentinel required by Section G.4 so tests can run
 * on ordinary developer machines.
 */
public record CpuConfig(
    int artioLibraryThread,
    int gatewayDisruptorThread,
    int gatewayEgressThread,
    int clusterServiceThread,
    int adminThread
) {
    /**
     * Returns a CPU config that disables every affinity pin.
     *
     * @return all-zero CPU configuration
     */
    public static CpuConfig noPinning() {
        return new CpuConfig(0, 0, 0, 0, 0);
    }
}
