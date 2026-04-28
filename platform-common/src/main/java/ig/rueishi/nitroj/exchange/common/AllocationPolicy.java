package ig.rueishi.nitroj.exchange.common;

import java.util.Locale;
import java.util.Objects;

/**
 * Code-level allocation boundary for NitroJEx runtime paths.
 *
 * <p>Responsibility: names the paths that must be treated as steady-state
 * trading hot paths versus cold/control paths. Role in system: task cards,
 * benchmarks, and code reviews use this type as the executable counterpart to
 * the V11 spec's allocation policy. Relationships: the policy is intentionally
 * independent of gateway/cluster implementation classes so common tests can
 * detect documentation drift without loading runtime modules. Lifecycle: static
 * metadata initialized once at class load time. Design intent: keep the
 * zero-allocation claim precise: hot paths target zero allocation after warmup,
 * while startup, tooling, diagnostics, and simulator paths may allocate.</p>
 */
public final class AllocationPolicy {
    private AllocationPolicy() {
    }

    /**
     * Runtime classification used by reviewers and benchmark owners.
     */
    public enum PathClass {
        HOT_PATH,
        COLD_PATH
    }

    /**
     * Required steady-state paths that must be implemented and benchmarked with
     * zero-allocation as the target.
     */
    public enum HotPath {
        FIX_L2_PARSING,
        FIX_L3_PARSING,
        EXECUTION_REPORT_PARSING,
        SBE_ENCODE_DECODE,
        GATEWAY_DISRUPTOR_HANDOFF,
        AERON_PUBLICATION_HANDOFF,
        L2_BOOK_MUTATION,
        L3_BOOK_MUTATION,
        CONSOLIDATED_L2_MUTATION,
        OWN_ORDER_OVERLAY_UPDATE_QUERY,
        EXTERNAL_LIQUIDITY_QUERY,
        ORDER_STATE_TRANSITION,
        RISK_DECISION,
        STRATEGY_TICK,
        ORDER_COMMAND_ENCODING
    }

    /**
     * Paths allowed to allocate because they are outside normal trading-event
     * processing.
     */
    public enum ColdPath {
        CONFIG_PARSING,
        STARTUP_VALIDATION,
        PLUGIN_CONSTRUCTION,
        ADMIN_CLI,
        SIMULATOR,
        REPLAY_TOOLING,
        REST_POLLING,
        DIAGNOSTICS,
        TESTS
    }

    public static PathClass classifyHot(final HotPath path) {
        Objects.requireNonNull(path, "path");
        return PathClass.HOT_PATH;
    }

    public static PathClass classifyCold(final ColdPath path) {
        Objects.requireNonNull(path, "path");
        return PathClass.COLD_PATH;
    }

    /**
     * Looks up a documented path name for simple policy assertions and operator
     * documentation checks.
     *
     * @param name enum-style or hyphen/space separated path name
     * @return hot or cold classification
     * @throws IllegalArgumentException when the path is not documented
     */
    public static PathClass classify(final String name) {
        final String normalized = normalize(name);
        for (HotPath path : HotPath.values()) {
            if (path.name().equals(normalized)) {
                return PathClass.HOT_PATH;
            }
        }
        for (ColdPath path : ColdPath.values()) {
            if (path.name().equals(normalized)) {
                return PathClass.COLD_PATH;
            }
        }
        throw new IllegalArgumentException("Unknown allocation policy path: " + name);
    }

    private static String normalize(final String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("allocation policy path name must not be blank");
        }
        return name.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
    }
}
