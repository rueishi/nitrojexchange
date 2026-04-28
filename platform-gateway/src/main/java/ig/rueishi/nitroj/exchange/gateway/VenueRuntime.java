package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.fix.FixProtocolPlugin;
import ig.rueishi.nitroj.exchange.gateway.marketdata.MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.VenuePlugin;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueOrderEntryAdapter;

/**
 * Immutable composition record for one venue gateway runtime.
 *
 * <p>Responsibility: groups the selected venue config, FIX protocol plugin,
 * venue plugin, market-data normalizer, and order-entry adapter for a gateway
 * venue. Role in system: V11 GatewayMain constructs this record after config
 * and plugin resolution so later wiring can pass one explicit runtime object
 * instead of separate loosely related components. Relationships: references
 * protocol and venue plugin boundaries introduced by TASK-102/TASK-104 and the
 * order-entry/market-data abstractions introduced by TASK-105/TASK-106.
 * Lifecycle: created during gateway startup and treated as read-only while the
 * venue session is active. Design intent: make plugin composition visible and
 * testable before future multi-venue gateway support stores these records in a
 * map by venue ID.
 */
public record VenueRuntime(
    VenueConfig venue,
    FixProtocolPlugin fixPlugin,
    VenuePlugin venuePlugin,
    MarketDataNormalizer marketDataNormalizer,
    VenueOrderEntryAdapter orderEntryAdapter
) {
}
