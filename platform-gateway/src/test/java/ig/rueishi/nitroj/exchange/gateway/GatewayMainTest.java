package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.GatewayConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.venue.StandardOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL3MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseVenuePlugin;
import ig.rueishi.nitroj.exchange.registry.IdRegistryImpl;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.library.SessionConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for gateway startup wiring that runs before external processes
 * such as Artio, Aeron, or Coinbase FIX are contacted.
 */
final class GatewayMainTest {

    @Test
    void withResolvedCredentials_missingConfigValues_usesEnvironmentFallback() {
        final GatewayConfig config = missingCredentialConfig();

        final GatewayConfig resolved = GatewayMain.withResolvedCredentials(config, venue(), Map.of(
            "COINBASE_API_KEY", "env-key",
            "COINBASE_API_SECRET_BASE64", "ZW52LXNlY3JldA==",
            "COINBASE_API_PASSPHRASE", "env-pass"));

        assertThat(resolved.credentials().vaultPath()).isEqualTo("secret/trading/coinbase/venue-1");
        assertThat(resolved.credentials().apiKey()).isEqualTo("env-key");
        assertThat(resolved.credentials().secretBase64()).isEqualTo("ZW52LXNlY3JldA==");
        assertThat(resolved.credentials().passphrase()).isEqualTo("env-pass");
        assertThat(resolved.fix()).isEqualTo(config.fix());
        assertThat(resolved.rest()).isEqualTo(config.rest());
    }

    @Test
    void withResolvedCredentials_existingCredentialValues_ignoresEnvironmentFallback() {
        final GatewayConfig config = GatewayConfig.forTest();

        final GatewayConfig resolved = GatewayMain.withResolvedCredentials(config, venue(), Map.of(
            "COINBASE_API_KEY", "env-key",
            "COINBASE_API_SECRET_BASE64", "ZW52LXNlY3JldA==",
            "COINBASE_API_PASSPHRASE", "env-pass"));

        assertThat(resolved).isSameAs(config);
        assertThat(resolved.credentials().apiKey()).isEqualTo("test-key");
        assertThat(resolved.credentials().secretBase64()).isEqualTo("dGVzdA==");
        assertThat(resolved.credentials().passphrase()).isEqualTo("test-pass");
    }

    @Test
    void withResolvedCredentials_missingEnvironmentValue_failsBeforeStartup() {
        final GatewayConfig config = missingCredentialConfig();

        assertThatThrownBy(() -> GatewayMain.withResolvedCredentials(config, venue(), Map.of(
            "COINBASE_API_KEY", "env-key",
            "COINBASE_API_PASSPHRASE", "env-pass")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("COINBASE_API_SECRET_BASE64");
    }

    @Test
    void buildSessionConfig_usesVenueSelectedFixProtocolDictionary() {
        final GatewayConfig config = GatewayConfig.forTest();
        final SessionConfiguration session = GatewayMain.buildSessionConfig(
            config,
            ConfigManager.loadVenues("../config/venues.toml"));

        assertThat(session.fixDictionary())
            .isEqualTo(ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.FixDictionaryImpl.class);
        assertThat(session.senderCompId()).isEqualTo(config.fix().senderCompId());
        assertThat(session.targetCompId()).isEqualTo(config.fix().targetCompId());
    }

    @Test
    void buildVenueRuntime_l2Venue_composesL2NormalizerAndAdapter() {
        try (GatewayDisruptor disruptor = testDisruptor()) {
            final GatewayConfig config = GatewayConfig.forTest();
            final VenueRuntime runtime = GatewayMain.buildVenueRuntime(
                config,
                List.of(venue(MarketDataModel.L2, CoinbaseVenuePlugin.ID)),
                registry(),
                disruptor,
                encoder -> 1L,
                (clOrdId, venueId, instrumentId) -> { });

            assertThat(runtime.marketDataNormalizer()).isInstanceOf(CoinbaseL2MarketDataNormalizer.class);
            assertThat(runtime.orderEntryAdapter()).isInstanceOf(StandardOrderEntryAdapter.class);
            assertThat(runtime.venuePlugin()).isInstanceOf(CoinbaseVenuePlugin.class);
        }
    }

    @Test
    void buildVenueRuntime_l3Venue_composesCoinbaseFixL3Runtime() {
        try (GatewayDisruptor disruptor = testDisruptor()) {
            final VenueRuntime runtime = GatewayMain.buildVenueRuntime(
                GatewayConfig.forTest(),
                List.of(venue(MarketDataModel.L3, CoinbaseVenuePlugin.ID)),
                registry(),
                disruptor,
                encoder -> 1L,
                (clOrdId, venueId, instrumentId) -> { });

            assertThat(runtime.fixPlugin().id()).isEqualTo(FixPluginId.FIXT11_FIX50SP2);
            assertThat(runtime.marketDataNormalizer()).isInstanceOf(CoinbaseL3MarketDataNormalizer.class);
            assertThat(runtime.venue().marketDataModel()).isEqualTo(MarketDataModel.L3);
        }
    }

    @Test
    void buildVenueRuntime_unsupportedVenuePlugin_failsClearly() {
        try (GatewayDisruptor disruptor = testDisruptor()) {
            assertThatThrownBy(() -> GatewayMain.buildVenueRuntime(
                GatewayConfig.forTest(),
                List.of(venue(MarketDataModel.L3, "UNKNOWN")),
                registry(),
                disruptor,
                encoder -> 1L,
                (clOrdId, venueId, instrumentId) -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported venue plugin");
        }
    }

    @Test
    void buildMarketDataNormalizer_unsupportedVenuePlugin_failsClearly() {
        try (GatewayDisruptor disruptor = testDisruptor()) {
            assertThatThrownBy(() -> GatewayMain.buildMarketDataNormalizer(
                venue(MarketDataModel.L3, "UNKNOWN"),
                registry(),
                disruptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported venue plugin");
        }
    }

    private static GatewayConfig missingCredentialConfig() {
        final GatewayConfig config = GatewayConfig.forTest();
        return GatewayConfig.builder()
            .venueId(config.venueId())
            .nodeRole(config.nodeRole())
            .aeronDir(config.aeronDir())
            .logDir(config.logDir())
            .clusterIngressChannel(config.clusterIngressChannel())
            .clusterEgressChannel(config.clusterEgressChannel())
            .clusterMembers(config.clusterMembers())
            .fix(config.fix())
            .credentials(new CredentialsConfig("secret/trading/coinbase/venue-1", null, null, null))
            .rest(config.rest())
            .cpu(config.cpu())
            .disruptor(config.disruptor())
            .counterFileDir(config.counterFileDir())
            .histogramOutputMs(config.histogramOutputMs())
            .warmup(config.warmup())
            .build();
    }

    private static VenueConfig venue(final MarketDataModel model, final String venuePlugin) {
        return new VenueConfig(
            Ids.VENUE_COINBASE_SANDBOX,
            "COINBASE_SANDBOX",
            "fix.example.test",
            4198,
            true,
            FixPluginId.FIXT11_FIX50SP2,
            venuePlugin,
            model,
            new VenueCapabilities(true, true, false));
    }

    private static VenueConfig venue() {
        return venue(MarketDataModel.L3, CoinbaseVenuePlugin.ID);
    }

    private static IdRegistryImpl registry() {
        final IdRegistryImpl registry = new IdRegistryImpl();
        registry.init(
            List.of(venue(MarketDataModel.L3, CoinbaseVenuePlugin.ID)),
            List.of(new InstrumentConfig(Ids.INSTRUMENT_BTC_USD, "BTC-USD", "BTC", "USD")));
        return registry;
    }

    private static GatewayDisruptor testDisruptor() {
        return new GatewayDisruptor(
            8,
            512,
            new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])),
            (slot, sequence, endOfBatch) -> { });
    }
}
