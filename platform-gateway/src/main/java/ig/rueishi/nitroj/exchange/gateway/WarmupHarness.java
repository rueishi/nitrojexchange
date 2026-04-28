package ig.rueishi.nitroj.exchange.gateway;

import uk.co.real_logic.artio.library.FixLibrary;

/**
 * JVM warmup harness interface — run before any live FIX connectivity.
 *
 * <p>Responsibility: define the gateway startup contract for warming latency
 * sensitive hot paths before the live FIX session is initiated. Role in system:
 * {@link GatewayMain} calls this interface after Artio's {@link FixLibrary} is
 * initialized and before {@code FixLibrary.initiate(...)} starts venue logon.
 * Relationships: the production implementation is supplied by a later tooling
 * task, while tests may inject a no-op double through GatewayMain's package
 * entry point. Lifecycle: one implementation is loaded during gateway startup
 * and invoked synchronously on the startup thread. Design intent: break the
 * compile-time dependency from the gateway process to cluster/tooling warmup
 * internals while preserving the strict AC-SY-006 ordering guarantee.</p>
 */
public interface WarmupHarness {
    /**
     * Runs warmup iterations sufficient to trigger compilation of gateway hot paths.
     *
     * <p>The implementation blocks until warmup is complete. Exceptions are
     * intentionally allowed to escape so startup aborts before live FIX
     * connectivity begins.</p>
     *
     * @param fixLibrary Artio library that will later be used for live trading
     * @throws IllegalStateException when warmup cannot complete successfully
     */
    void runGatewayWarmup(FixLibrary fixLibrary);
}
