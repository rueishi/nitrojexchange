package ig.rueishi.nitroj.exchange.gateway.fix;

import ig.rueishi.nitroj.exchange.common.FixConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import org.junit.jupiter.api.Test;
import ig.rueishi.nitroj.exchange.fix.fix42.FixDictionaryImpl;
import uk.co.real_logic.artio.dictionary.FixDictionary;
import uk.co.real_logic.artio.library.SessionConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for the V11 FIX protocol plugin registry and FIX 4.2 plugin.
 *
 * <p>Responsibility: verifies plugin lookup, unsupported-plugin failure, and
 * protocol-only Artio session configuration. Role in system: this test protects
 * the first V11 seam between venue config and Artio session initiation before
 * later tasks add FIX 4.4/FIXT dictionaries. Relationships: uses real common
 * config records and Artio's {@link SessionConfiguration} builder, but does not
 * touch venue plugins or Coinbase logon behavior. Lifecycle: runs in the gateway
 * unit-test suite as the compatibility contract for TASK-102. Design intent:
 * prove the new abstraction can select existing FIX 4.2 behavior without
 * smuggling venue-specific logic into the protocol layer.
 */
final class FixProtocolPluginRegistryTest {

    /**
     * Verifies the default registry exposes the first migrated FIX 4.2 plugin.
     */
    @Test
    void get_fix42_returnsArtioFix42Plugin() {
        final FixProtocolPlugin plugin = new FixProtocolPluginRegistry().get(FixPluginId.FIX_42);

        assertThat(plugin).isInstanceOf(ArtioFix42Plugin.class);
        assertThat(plugin.id()).isEqualTo(FixPluginId.FIX_42);
        assertThat(plugin.beginString()).isEqualTo("FIX.4.2");
        assertThat(plugin.defaultApplVerId()).isNull();
        assertThat(plugin.dictionaryClass()).isEqualTo(FixDictionaryImpl.class);
    }

    /**
     * Verifies unsupported plugin IDs fail at registry lookup with a clear
     * startup-facing message.
     */
    @Test
    void get_unsupportedPlugin_throwsIllegalArgumentException() {
        final FixProtocolPluginRegistry registry = new FixProtocolPluginRegistry(
            Map.of(FixPluginId.FIX_42, new ArtioFix42Plugin()));

        assertThatThrownBy(() -> registry.get(FixPluginId.FIX_44))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FIX_44");
    }

    /**
     * Verifies null plugin IDs fail before any map lookup can hide the caller
     * error as an unsupported-plugin case.
     */
    @Test
    void get_nullPluginId_throwsNullPointerException() {
        assertThatThrownBy(() -> new FixProtocolPluginRegistry().get(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");
    }

    /**
     * Verifies custom registration rejects mismatched keys and plugin IDs.
     */
    @Test
    void constructor_mismatchedPluginId_throwsIllegalArgumentException() {
        final FixProtocolPlugin mismatched = new StubPlugin(FixPluginId.FIX_44);

        assertThatThrownBy(() -> new FixProtocolPluginRegistry(Map.of(FixPluginId.FIX_42, mismatched)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FIX_42")
            .hasMessageContaining("FIX_44");
    }

    /**
     * Verifies the FIX 4.2 plugin configures only protocol/session fields and
     * selects the generated FIX 4.2 dictionary class.
     */
    @Test
    void configureSession_fix42_appliesProtocolFields() {
        final FixConfig fix = FixConfig.builder()
            .senderCompId("SENDER")
            .targetCompId("TARGET")
            .resetSeqNumOnLogon(true)
            .build();
        final VenueConfig venue = new VenueConfig(1, "TEST", "localhost", 4198, false,
            FixPluginId.FIX_42, "COINBASE", MarketDataModel.L3,
            new VenueCapabilities(true, true, false));

        final SessionConfiguration configuration = new ArtioFix42Plugin()
            .configureSession(SessionConfiguration.builder(), fix, venue)
            .build();

        assertThat(configuration.hosts()).containsExactly("localhost");
        assertThat(configuration.ports().getInt(0)).isEqualTo(4198);
        assertThat(configuration.senderCompId()).isEqualTo("SENDER");
        assertThat(configuration.targetCompId()).isEqualTo("TARGET");
        assertThat(configuration.resetSeqNum()).isTrue();
        assertThat(configuration.fixDictionary()).isEqualTo(FixDictionaryImpl.class);
    }

    private record StubPlugin(FixPluginId id) implements FixProtocolPlugin {
        @Override
        public String beginString() {
            return "TEST";
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
            return builder;
        }
    }
}
