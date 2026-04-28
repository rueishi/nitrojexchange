package ig.rueishi.nitroj.exchange.gateway.fix;

import ig.rueishi.nitroj.exchange.common.FixConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import uk.co.real_logic.artio.dictionary.FixDictionary;
import uk.co.real_logic.artio.library.SessionConfiguration;

/**
 * Protocol-level extension point for FIX session configuration.
 *
 * <p>Responsibility: exposes the FIX version, generated Artio dictionary, and
 * protocol-specific session settings for one supported FIX family. Role in
 * system: gateway startup resolves a venue's {@link FixPluginId} to an
 * implementation and delegates Artio {@link SessionConfiguration} setup here
 * before initiating the session. Relationships: consumes common immutable
 * {@link FixConfig} and {@link VenueConfig}; deliberately does not reference
 * venue plugins, venue authentication, or venue business policies. Lifecycle:
 * implementations are stateless singletons registered during gateway startup.
 * Design intent: isolate wire-protocol mechanics from venue semantics so the
 * same venue plugin can evolve across FIX versions without changing the gateway
 * composition boundary.
 */
public interface FixProtocolPlugin {

    /**
     * Returns the config identifier that selects this protocol plugin.
     *
     * @return enum value loaded from {@code venues.toml}
     */
    FixPluginId id();

    /**
     * Returns the FIX session BeginString owned by this protocol family.
     *
     * @return wire-level BeginString, such as {@code FIX.4.2}
     */
    String beginString();

    /**
     * Returns the default FIX application version for FIXT sessions.
     *
     * <p>Classic FIX 4.2 and 4.4 sessions do not use DefaultApplVerID, so they
     * return {@code null}. FIXT.1.1/FIX 5.0SP2 plugins return the application
     * version configured for application messages.
     *
     * @return application version string, or {@code null} when not applicable
     */
    String defaultApplVerId();

    /**
     * Returns the generated Artio dictionary class used by session setup.
     *
     * @return generated dictionary implementation class
     */
    Class<? extends FixDictionary> dictionaryClass();

    /**
     * Applies protocol-level settings to an Artio session builder.
     *
     * <p>The method owns only protocol/session mechanics: address, CompIDs,
     * sequence reset policy, and dictionary selection. Venue authentication and
     * proprietary logon fields are configured by the venue layer in later task
     * cards.
     *
     * @param builder Artio session builder being prepared for initiation
     * @param fix per-gateway FIX identity and session settings
     * @param venue static venue endpoint and plugin metadata
     * @return the same builder for fluent startup wiring
     */
    SessionConfiguration.Builder configureSession(
        SessionConfiguration.Builder builder,
        FixConfig fix,
        VenueConfig venue);
}
