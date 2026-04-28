package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.RestConfig;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouterImpl;
import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.OrderCommandHandler;
import ig.rueishi.nitroj.exchange.gateway.marketdata.MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.StandardOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueLogonCustomizer;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.gateway.venue.VenuePlugin;
import ig.rueishi.nitroj.exchange.gateway.venue.VenuePluginRegistry;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Venue plugin for Coinbase-specific gateway behavior.
 *
 * <p>Responsibility: owns Coinbase behavior that is independent of FIX protocol
 * version, beginning with credential-backed logon customization and configured
 * capability reporting. Role in system: gateway runtime composes this plugin
 * with a selected FIX protocol plugin. Coinbase is currently configured for
 * FIXT.1.1/FIX 5.0SP2, while the venue semantics remain independent of protocol
 * code. Relationships: wraps {@link CoinbaseLogonStrategy} and creates Coinbase
 * order-entry and market-data policies behind this same plugin. Lifecycle:
 * stateless plugin instance is
 * resolved once from {@link VenuePluginRegistry}; each logon customizer is
 * created from resolved credentials. Design intent: make Coinbase the first real
 * venue plugin while avoiding a generic or future-venue placeholder.
 */
public final class CoinbaseVenuePlugin implements VenuePlugin {
    public static final String ID = "COINBASE";
    private static final String ENV_COINBASE_API_KEY = "COINBASE_API_KEY";
    private static final String ENV_COINBASE_API_SECRET_BASE64 = "COINBASE_API_SECRET_BASE64";
    private static final String ENV_COINBASE_API_PASSPHRASE = "COINBASE_API_PASSPHRASE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public VenueCapabilities capabilities(final VenueConfig venue) {
        return Objects.requireNonNull(venue, "venue").capabilities();
    }

    @Override
    public CredentialsConfig resolveCredentials(
        final CredentialsConfig credentials,
        final Map<String, String> environment) {

        Objects.requireNonNull(environment, "environment");
        if (hasCredentialValues(credentials)) {
            return credentials;
        }
        final String vaultPath = credentials == null ? null : credentials.vaultPath();
        return new CredentialsConfig(
            vaultPath,
            requiredEnv(environment, ENV_COINBASE_API_KEY),
            requiredEnv(environment, ENV_COINBASE_API_SECRET_BASE64),
            requiredEnv(environment, ENV_COINBASE_API_PASSPHRASE));
    }

    @Override
    public VenueLogonCustomizer logonCustomizer(final CredentialsConfig credentials) {
        Objects.requireNonNull(credentials, "credentials");
        final CoinbaseLogonStrategy strategy = new CoinbaseLogonStrategy(
            credentials.apiKey(),
            credentials.secretBase64(),
            credentials.passphrase());
        return strategy::configureLogon;
    }

    @Override
    public MarketDataNormalizer marketDataNormalizer(
        final VenueConfig venue,
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor) {

        return switch (Objects.requireNonNull(venue, "venue").marketDataModel()) {
            case L2 -> new CoinbaseL2MarketDataNormalizer(idRegistry, disruptor);
            case L3 -> new CoinbaseL3MarketDataNormalizer(idRegistry, disruptor);
        };
    }

    @Override
    public VenueOrderEntryAdapter orderEntryAdapter(
        final VenueConfig venue,
        final ExecutionRouterImpl.FixSender sender,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final ExecutionRouterImpl.RejectPublisher rejectPublisher) {

        return new StandardOrderEntryAdapter(
            sender,
            idRegistry,
            account,
            backPressureCounter,
            rejectPublisher,
            Clock.systemUTC(),
            new CoinbaseOrderEntryPolicy(Objects.requireNonNull(venue, "venue").capabilities()));
    }

    @Override
    public OrderCommandHandler.BalanceQuerySink balanceQuerySink(
        final RestConfig restConfig,
        final CredentialsConfig credentials,
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor) {

        return new CoinbaseRestPoller(restConfig, credentials, idRegistry, disruptor);
    }

    private static boolean hasCredentialValues(final CredentialsConfig credentials) {
        return credentials != null
            && hasText(credentials.apiKey())
            && hasText(credentials.secretBase64())
            && hasText(credentials.passphrase());
    }

    private static String requiredEnv(final Map<String, String> environment, final String name) {
        final String value = environment.get(name);
        if (!hasText(value)) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
