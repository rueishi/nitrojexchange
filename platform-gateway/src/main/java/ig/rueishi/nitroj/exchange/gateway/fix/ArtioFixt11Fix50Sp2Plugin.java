package ig.rueishi.nitroj.exchange.gateway.fix;

import ig.rueishi.nitroj.exchange.common.FixConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.FixDictionaryImpl;
import uk.co.real_logic.artio.dictionary.FixDictionary;
import uk.co.real_logic.artio.library.SessionConfiguration;

import java.util.Objects;

/**
 * Artio-backed protocol plugin for FIXT.1.1 / FIX 5.0SP2 sessions.
 *
 * <p>Responsibility: exposes the generated FIX 5.0SP2 compatibility dictionary
 * and protocol metadata for venues configured with FIXT transport semantics.
 * Role in system: V11 venue config can select this plugin before the gateway
 * starts real FIX connectivity. Relationships: uses the version-isolated
 * generated package from TASK-103 and reports {@code FIX.5.0SP2} as the default
 * application version for later logon enrichment. Lifecycle: stateless and
 * shareable. Design intent: make the target FIX L3 protocol selectable
 * while keeping venue authentication in the venue plugin.
 */
public final class ArtioFixt11Fix50Sp2Plugin implements FixProtocolPlugin {
    @Override
    public FixPluginId id() {
        return FixPluginId.FIXT11_FIX50SP2;
    }

    @Override
    public String beginString() {
        return "FIXT.1.1";
    }

    @Override
    public String defaultApplVerId() {
        return "FIX.5.0SP2";
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
