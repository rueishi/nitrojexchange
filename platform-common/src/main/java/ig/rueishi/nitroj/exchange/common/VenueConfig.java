package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable venue identity and transport endpoint loaded from {@code venues.toml}.
 *
 * <p>Responsibility: carries the stable venue ID registry fields owned by
 * TASK-004. Role in system: {@code IdRegistryImpl} consumes these values in
 * TASK-005 to map names to IDs, while gateway startup uses the FIX host and port
 * to connect to the venue. Relationships: FIX session identity is deliberately
 * absent because sender/target CompIDs live in {@link FixConfig} and are tied to
 * the gateway API key. Lifecycle: records are created once during configuration
 * loading and then shared read-only. Design intent: keep permanent venue identity
 * separate from per-process credentials and session identity.
 *
 * @param id immutable integer venue ID
 * @param name registry name, for example {@code COINBASE_SANDBOX}
 * @param fixHost venue FIX host
 * @param fixPort venue FIX TCP port
 * @param sandbox whether this venue entry targets a sandbox environment
 * @param fixPlugin FIX protocol plugin identifier used for Artio dictionary and session setup
 * @param venuePlugin venue-specific plugin identifier used for authentication and semantic policies
 * @param marketDataModel configured market-data depth model supplied by this venue
 * @param capabilities static venue feature flags used by gateway runtime wiring
 */
public record VenueConfig(
    int id,
    String name,
    String fixHost,
    int fixPort,
    boolean sandbox,
    FixPluginId fixPlugin,
    String venuePlugin,
    MarketDataModel marketDataModel,
    VenueCapabilities capabilities
) {
}
