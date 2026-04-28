package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable capability flags advertised by a configured venue.
 *
 * <p>Responsibility: records which gateway flows are enabled for a venue and
 * whether native FIX cancel/replace is supported. Role in system: venue plugins
 * and runtime wiring consume these flags before constructing order-entry and
 * market-data components. Relationships: embedded in {@link VenueConfig} so the
 * venue registry is the single source of truth for static venue capabilities.
 * Lifecycle: created during TOML loading and reused read-only for the lifetime
 * of the process. Design intent: keep capability checks declarative and
 * testable, avoiding hidden venue-specific constants in gateway code.
 *
 * @param orderEntryEnabled whether the venue supports order-entry flow
 * @param marketDataEnabled whether the venue supports market-data flow
 * @param nativeReplaceSupported whether MsgType G style replace is supported
 */
public record VenueCapabilities(
    boolean orderEntryEnabled,
    boolean marketDataEnabled,
    boolean nativeReplaceSupported
) {
}
