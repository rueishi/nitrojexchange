package ig.rueishi.nitroj.exchange.gateway.venue;

import uk.co.real_logic.artio.builder.AbstractLogonEncoder;

/**
 * Venue-owned hook for outbound FIX Logon enrichment.
 *
 * <p>Responsibility: lets a venue plugin add authentication fields,
 * signatures, and proprietary logon tags after the FIX protocol plugin has
 * selected the session dictionary. Role in system: gateway session
 * customisation delegates venue-specific Logon behavior through this interface
 * instead of embedding exchange-specific rules in protocol plugins.
 * Relationships: implemented or wrapped by concrete venue plugins; the protocol
 * plugin remains responsible for BeginString and dictionary mechanics.
 * Lifecycle: one customizer is created during gateway startup from resolved
 * credentials and reused for logon and reconnect paths. Design intent: keep
 * authentication pluggable while preserving Artio ownership of body length,
 * checksum, and generated encoder types.
 */
@FunctionalInterface
public interface VenueLogonCustomizer {

    /**
     * Enriches an Artio-generated Logon encoder with venue-specific fields.
     *
     * @param logon generated Artio logon encoder for the active dictionary
     * @param sessionId Artio session id associated with the logon
     */
    void customizeLogon(AbstractLogonEncoder logon, long sessionId);
}
