package ig.rueishi.nitroj.exchange.common;

/**
 * Identifies the FIX protocol plugin selected by a venue registry entry.
 *
 * <p>Responsibility: names the supported FIX session/application protocol
 * families without coupling common configuration to gateway implementation
 * classes. Role in system: {@link ConfigManager} parses this value from
 * {@code venues.toml}, and V11 gateway plugin registries use it to select the
 * Artio dictionary/session behavior for a venue. Relationships: paired with
 * {@link VenueConfig#fixPlugin()} and intentionally separate from
 * {@link VenueConfig#venuePlugin()} so protocol mechanics do not leak venue
 * business behavior. Lifecycle: values are created once during config loading
 * and then read during gateway startup. Design intent: keep version selection
 * explicit, finite, and validation-friendly before any FIX session is opened.
 */
public enum FixPluginId {
    FIX_42,
    FIX_44,
    FIXT11_FIX50SP2
}
