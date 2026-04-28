package ig.rueishi.nitroj.exchange.gateway.fix;

import ig.rueishi.nitroj.exchange.common.FixConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.fix.fix44.FixDictionaryImpl;
import uk.co.real_logic.artio.dictionary.FixDictionary;
import uk.co.real_logic.artio.library.SessionConfiguration;

import java.util.Objects;

/**
 * Artio-backed protocol plugin for classic FIX 4.4 sessions.
 *
 * <p>Responsibility: selects the version-isolated FIX 4.4 generated dictionary
 * and applies protocol-level Artio session settings. Role in system: gateway
 * runtime composition uses this plugin for venues configured with
 * {@link FixPluginId#FIX_44}. Relationships: mirrors the FIX 4.2 plugin but
 * uses the V11 generated package created by TASK-103. Lifecycle: stateless and
 * shareable. Design intent: make FIX 4.4 a first-class selectable protocol
 * without adding any venue-specific behavior to the protocol layer.
 */
public final class ArtioFix44Plugin implements FixProtocolPlugin {
    @Override
    public FixPluginId id() {
        return FixPluginId.FIX_44;
    }

    @Override
    public String beginString() {
        return "FIX.4.4";
    }

    @Override
    public String defaultApplVerId() {
        return null;
    }

    @Override
    public Class<? extends FixDictionary> dictionaryClass() {
        return FixDictionaryImpl.class;
    }

    @Override
    public SessionConfiguration.Builder configureSession(
        final SessionConfiguration.Builder builder,
        final FixConfig fix,
        final VenueConfig venue) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(fix, "fix");
        Objects.requireNonNull(venue, "venue");
        return builder
            .address(venue.fixHost(), venue.fixPort())
            .senderCompId(fix.senderCompId())
            .targetCompId(fix.targetCompId())
            .resetSeqNum(fix.resetSeqNumOnLogon())
            .fixDictionary(dictionaryClass());
    }
}
