package ig.rueishi.nitroj.exchange.gateway.fix;

import ig.rueishi.nitroj.exchange.common.FixPluginId;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for gateway FIX protocol plugins.
 *
 * <p>Responsibility: maps validated venue-config protocol identifiers to
 * stateless protocol plugin implementations. Role in system: gateway startup
 * resolves the configured {@link FixPluginId} through this registry before
 * building Artio session configuration. Relationships: currently registers the
 * first migrated V11 protocol plugin, {@link ArtioFix42Plugin}; later task cards
 * add FIX 4.4 and FIXT.1.1/FIX 5.0SP2 implementations without changing callers.
 * Lifecycle: constructed once during gateway wiring or tests, then reused
 * read-only. Design intent: fail fast with an operator-readable unsupported
 * plugin error instead of scattering switch statements through gateway startup.
 */
public final class FixProtocolPluginRegistry {
    private final Map<FixPluginId, FixProtocolPlugin> plugins;

    /**
     * Creates the default V11 registry with currently implemented protocol plugins.
     */
    public FixProtocolPluginRegistry() {
        this(Map.of(
            FixPluginId.FIX_42, new ArtioFix42Plugin(),
            FixPluginId.FIX_44, new ArtioFix44Plugin(),
            FixPluginId.FIXT11_FIX50SP2, new ArtioFixt11Fix50Sp2Plugin()));
    }

    /**
     * Creates a registry from explicit plugins for tests and future dependency injection.
     *
     * @param registeredPlugins plugins keyed by their declared config ID
     * @throws NullPointerException when the map or any plugin is null
     * @throws IllegalArgumentException when a plugin is registered under a key
     *                                  that does not match {@link FixProtocolPlugin#id()}
     */
    public FixProtocolPluginRegistry(final Map<FixPluginId, FixProtocolPlugin> registeredPlugins) {
        Objects.requireNonNull(registeredPlugins, "registeredPlugins");
        final EnumMap<FixPluginId, FixProtocolPlugin> copy = new EnumMap<>(FixPluginId.class);
        for (Map.Entry<FixPluginId, FixProtocolPlugin> entry : registeredPlugins.entrySet()) {
            final FixPluginId id = Objects.requireNonNull(entry.getKey(), "plugin id");
            final FixProtocolPlugin plugin = Objects.requireNonNull(entry.getValue(), "plugin");
            if (plugin.id() != id) {
                throw new IllegalArgumentException("FIX plugin registered under " + id
                    + " but declares " + plugin.id());
            }
            copy.put(id, plugin);
        }
        this.plugins = Map.copyOf(copy);
    }

    /**
     * Resolves a protocol plugin by config ID.
     *
     * @param id configured FIX protocol plugin ID
     * @return matching plugin
     * @throws IllegalArgumentException when no plugin is registered for {@code id}
     * @throws NullPointerException when {@code id} is null
     */
    public FixProtocolPlugin get(final FixPluginId id) {
        final FixPluginId requiredId = Objects.requireNonNull(id, "id");
        final FixProtocolPlugin plugin = plugins.get(requiredId);
        if (plugin == null) {
            throw new IllegalArgumentException("Unsupported FIX protocol plugin: " + requiredId);
        }
        return plugin;
    }
}
