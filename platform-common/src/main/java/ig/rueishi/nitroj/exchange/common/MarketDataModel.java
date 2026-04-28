package ig.rueishi.nitroj.exchange.common;

/**
 * Declares the market-data depth model supplied by a venue.
 *
 * <p>Responsibility: distinguishes price-level L2 feeds from order-level L3
 * feeds at configuration time. Role in system: gateway startup uses this value
 * to choose either the shared L2 normalizer or the shared L3 normalizer; cluster
 * code then knows whether order-level state is expected before deriving L2.
 * Relationships: stored on {@link VenueConfig} beside protocol and venue plugin
 * identifiers. Lifecycle: parsed once from the venue registry and treated as
 * immutable runtime metadata. Design intent: prevent strategies and gateways
 * from inferring feed shape from venue names or FIX versions, because L2/L3 is a
 * venue/feed capability rather than a protocol version alone.
 */
public enum MarketDataModel {
    L2,
    L3
}
