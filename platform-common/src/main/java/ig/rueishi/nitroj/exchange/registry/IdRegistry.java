package ig.rueishi.nitroj.exchange.registry;

import org.agrona.DirectBuffer;

/**
 * Startup-loaded lookup service that maps external venue, instrument, and Artio
 * session identifiers into the integer IDs used inside NitroJEx.
 *
 * <p>Responsibility: this interface defines the narrow ID lookup contract shared
 * by gateway, cluster, and later business components. Role in system: startup
 * code populates an implementation from TOML-backed config records, and hot-path
 * FIX/SBE handlers then use primitive IDs instead of repeatedly parsing strings.
 * Relationships: venue and instrument metadata comes from TASK-004
 * {@code VenueConfig} and {@code InstrumentConfig}; live FIX session IDs are
 * registered by gateway-side Artio session handling after Artio exposes
 * {@code Session.id()}. Lifecycle: implementations are initialized once during
 * process construction, then read from many times without changing name/symbol
 * mappings. Design intent: keep the runtime surface primitive and allocation
 * conscious while making unknown-session failures explicit programming errors.
 */
public interface IdRegistry {
    /**
     * Resolves a live Artio {@code Session.id()} value to the platform venue ID.
     *
     * <p>Gateway handlers call this after session creation/acquisition/logon has
     * registered the Artio session ID via {@link #registerSession(int, long)}. An
     * unknown session indicates gateway wiring failed, so the method throws rather
     * than returning the unknown-ID sentinel used for recoverable data errors.
     *
     * @param sessionId Artio {@code Session.id()} value
     * @return configured venue ID for that live session
     * @throws IllegalStateException if the session ID has not been registered
     */
    int venueId(long sessionId);

    /**
     * Resolves a FIX symbol to the platform instrument ID.
     *
     * <p>Market-data and execution handlers call this for venue-supplied symbols.
     * Unknown symbols are treated as recoverable data errors because venues may
     * publish instruments outside the configured trading universe.
     *
     * @param symbol FIX tag 55 symbol as a {@link CharSequence}
     * @return instrument ID, or {@code 0} when the symbol is unknown
     */
    int instrumentId(CharSequence symbol);

    /**
     * Resolves a FIX symbol directly from an ASCII byte range.
     *
     * <p>Gateway market-data normalizers use this overload to avoid allocating a
     * {@link String} for known symbols on the normal hot path. Implementations
     * should compare the supplied bytes against startup-cached symbols. The
     * default method preserves compatibility for tests and simple registries.</p>
     *
     * @param buffer source buffer containing the FIX symbol bytes
     * @param offset first symbol byte
     * @param length number of symbol bytes
     * @return instrument ID, or {@code 0} when the symbol is unknown
     */
    default int instrumentId(final DirectBuffer buffer, final int offset, final int length) {
        return instrumentId(buffer.getStringWithoutLengthAscii(offset, length));
    }

    /**
     * Returns the cached FIX symbol for a configured instrument ID.
     *
     * @param instrumentId configured platform instrument ID
     * @return FIX symbol string, or {@code null} for an unmapped ID
     */
    String symbolOf(int instrumentId);

    /**
     * Returns the cached display name for a configured venue ID.
     *
     * @param venueId configured platform venue ID
     * @return venue display name, or {@code null} for an unmapped ID
     */
    String venueNameOf(int venueId);

    /**
     * Registers a live Artio session ID for a venue.
     *
     * <p>Gateway-side Artio session creation/acquisition/logon handling calls this
     * after {@code init(...)} and when {@code Session.id()} is available. Cluster
     * code never calls it because the cluster process has no FIX sessions.
     *
     * @param venueId platform venue ID represented by the session
     * @param sessionId live Artio {@code Session.id()} value
     * @throws IllegalStateException if the same session ID is already registered
     *                               for a different venue
     */
    void registerSession(int venueId, long sessionId);
}
