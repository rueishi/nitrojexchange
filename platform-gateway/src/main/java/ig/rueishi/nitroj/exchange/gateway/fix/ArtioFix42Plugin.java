package ig.rueishi.nitroj.exchange.gateway.fix;

import ig.rueishi.nitroj.exchange.common.FixConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.fix.fix42.FixDictionaryImpl;
import uk.co.real_logic.artio.dictionary.FixDictionary;
import uk.co.real_logic.artio.library.SessionConfiguration;

import java.util.Objects;

/**
 * Artio-backed protocol plugin for classic FIX 4.2 sessions.
 *
 * <p>Responsibility: supplies the current generated FIX 4.2 dictionary and
 * applies baseline Artio session settings for venues configured with
 * {@link FixPluginId#FIX_42}. Role in system: this is the compatibility bridge
 * that lets V11 keep FIX 4.2 selectable through a version-isolated generated
 * package. Relationships: consumes {@link FixConfig} for gateway identity and
 * {@link VenueConfig} for network address; it intentionally contains no venue
 * authentication, proprietary tags, or order-entry policy. Lifecycle: stateless
 * and safe to share. Design intent: keep classic FIX 4.2 available while the
 * Coinbase path uses the FIXT.1.1/FIX 5.0SP2 plugin.
 */
public final class ArtioFix42Plugin implements FixProtocolPlugin {
    private static final String BEGIN_STRING = "FIX.4.2";

    @Override
    public FixPluginId id() {
        return FixPluginId.FIX_42;
    }

    @Override
    public String beginString() {
        return BEGIN_STRING;
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
