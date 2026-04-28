package ig.rueishi.nitroj.exchange.gateway.venue;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.RestConfig;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouterImpl;
import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.OrderCommandHandler;
import ig.rueishi.nitroj.exchange.gateway.marketdata.MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * Venue-specific gateway extension point.
 *
 * <p>Responsibility: exposes behavior that is tied to a venue rather than a FIX
 * protocol version, starting with capability reporting and logon customization.
 * Role in system: gateway startup resolves the configured venue plugin ID and
 * asks it for venue semantics while FIX protocol plugins provide dictionary and
 * session mechanics. Relationships: {@link VenuePluginRegistry} owns lookup,
 * concrete implementations live under their venue packages, and V11 task cards
 * extend this boundary with order-entry and market-data adapters. Lifecycle:
 * implementations are stateless and constructed once per gateway runtime.
 * Design intent: create a real venue-extension boundary without inventing
 * placeholder future venues or mixing protocol mechanics into venue code.
 */
public interface VenuePlugin {

    /**
     * Returns the venue plugin ID used in {@code venues.toml}.
     *
     * @return stable venue plugin identifier
     */
    String id();

    /**
     * Returns the static capabilities declared by the venue config.
     *
     * @param venue immutable venue registry entry
     * @return configured capability flags
     */
    VenueCapabilities capabilities(VenueConfig venue);

    /**
     * Creates the venue-owned logon customizer from resolved credentials.
     *
     * @param credentials resolved venue credentials
     * @return customizer used by Artio session customisation
     */
    VenueLogonCustomizer logonCustomizer(CredentialsConfig credentials);

    /**
     * Resolves credentials from configured values or venue-owned fallback sources.
     *
     * @param credentials configured credentials
     * @param environment process environment snapshot
     * @return resolved credentials ready for logon and REST calls
     */
    default CredentialsConfig resolveCredentials(
        final CredentialsConfig credentials,
        final Map<String, String> environment) {

        Objects.requireNonNull(environment, "environment");
        if (hasCredentialValues(credentials)) {
            return credentials;
        }
        throw new IllegalStateException("Missing credentials for venue plugin: " + id());
    }

    /**
     * Creates the venue/model-specific market-data normalizer.
     *
     * @param venue venue config
     * @param idRegistry registry used for session and symbol lookup
     * @param disruptor gateway event ring
     * @return market-data normalizer
     */
    default MarketDataNormalizer marketDataNormalizer(
        final VenueConfig venue,
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor) {
        throw new IllegalArgumentException("Missing market-data normalizer for venue plugin: " + id());
    }

    /**
     * Creates the venue-specific order-entry adapter.
     *
     * @param venue venue config
     * @param sender Artio send facade
     * @param idRegistry registry used for symbol lookup
     * @param account FIX account tag value
     * @param backPressureCounter exhausted-send metric callback
     * @param rejectPublisher rejection publisher
     * @return order-entry adapter
     */
    default VenueOrderEntryAdapter orderEntryAdapter(
        final VenueConfig venue,
        final ExecutionRouterImpl.FixSender sender,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final ExecutionRouterImpl.RejectPublisher rejectPublisher) {
        throw new IllegalArgumentException("Missing order-entry adapter for venue plugin: " + id());
    }

    /**
     * Creates the venue-specific balance query sink.
     *
     * @param restConfig REST configuration
     * @param credentials resolved credentials
     * @param idRegistry registry used for currency/instrument lookup
     * @param disruptor gateway event ring
     * @return balance query sink
     */
    default OrderCommandHandler.BalanceQuerySink balanceQuerySink(
        final RestConfig restConfig,
        final CredentialsConfig credentials,
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor) {
        throw new IllegalArgumentException("Missing balance query sink for venue plugin: " + id());
    }

    private static boolean hasCredentialValues(final CredentialsConfig credentials) {
        return credentials != null
            && hasText(credentials.apiKey())
            && hasText(credentials.secretBase64())
            && hasText(credentials.passphrase());
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
