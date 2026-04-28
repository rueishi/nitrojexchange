package ig.rueishi.nitroj.exchange.registry;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Object2IntHashMap;
import org.agrona.DirectBuffer;

import java.util.List;
import java.util.Objects;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Agrona-backed implementation of the NitroJEx ID registry.
 *
 * <p>Responsibility: stores startup-time mappings from venue names, instrument
 * symbols, and live Artio session IDs to platform integer IDs, plus O(1) reverse
 * arrays for ID-to-name lookups. Role in system: gateway and cluster startup
 * build this registry before constructing handlers that need ID translation.
 * Relationships: {@link VenueConfig} and {@link InstrumentConfig} provide the
 * stable name/symbol mappings; gateway Artio session handling provides
 * {@code Session.id()} values via {@link #registerSession(int, long)}. Lifecycle:
 * {@link #init(List, List)} is called once during process startup, session IDs
 * are registered by the gateway as Artio sessions become available, and lookup
 * methods are then used by hot-path handlers. Design intent: use Agrona primitive
 * maps and arrays to avoid boxed ID lookups while keeping the interface small and
 * aligned with the execution plan.
 */
public final class IdRegistryImpl implements IdRegistry {
    private static final Logger LOGGER = System.getLogger(IdRegistryImpl.class.getName());

    private final Object2IntHashMap<String> venueByName = new Object2IntHashMap<>(0);
    private final Object2IntHashMap<String> instrumentBySymbol = new Object2IntHashMap<>(0);
    private final String[] venueNames = new String[Ids.MAX_VENUES];
    private final String[] instrumentSymbols = new String[Ids.MAX_INSTRUMENTS];
    private final Long2LongHashMap venueBySessionId = new Long2LongHashMap(0);

    /**
     * Populates the startup-only venue and instrument name maps.
     *
     * <p>The method intentionally does not register FIX sessions. Artio
     * {@code Session.id()} values do not exist in {@code venues.toml}; they are
     * live gateway runtime identities and are registered later through
     * {@link #registerSession(int, long)}. Allocation is acceptable here because
     * this method is called during startup rather than on the trading hot path.
     *
     * @param venues configured venues loaded from TOML
     * @param instruments configured instruments loaded from TOML
     */
    public void init(final List<VenueConfig> venues, final List<InstrumentConfig> instruments) {
        Objects.requireNonNull(venues, "venues");
        Objects.requireNonNull(instruments, "instruments");

        for (VenueConfig venue : venues) {
            venueByName.put(venue.name(), venue.id());
            venueNames[venue.id()] = venue.name();
        }
        for (InstrumentConfig instrument : instruments) {
            instrumentBySymbol.put(instrument.symbol(), instrument.id());
            instrumentSymbols[instrument.id()] = instrument.symbol();
        }
    }

    /**
     * Registers an Artio session ID to venue mapping after the live session exists.
     *
     * <p>The mapping is idempotent for the same venue so reconnect/acquisition
     * paths can defensively repeat registration. Reusing the same session ID for a
     * different venue is a gateway wiring bug and fails fast.
     *
     * @param venueId configured venue ID
     * @param sessionId Artio {@code Session.id()} value
     * @throws IllegalStateException if the session ID is already registered for a
     *                               different venue
     */
    @Override
    public void registerSession(final int venueId, final long sessionId) {
        final long existing = venueBySessionId.get(sessionId);
        if (existing != 0 && existing != venueId) {
            throw new IllegalStateException(
                "Session " + sessionId + " already registered for venue " + existing
                    + "; cannot re-register for venue " + venueId);
        }
        venueBySessionId.put(sessionId, venueId);
    }

    /**
     * Resolves a registered Artio session ID to its venue ID.
     *
     * @param sessionId Artio {@code Session.id()} value
     * @return venue ID registered for the session
     * @throws IllegalStateException if no venue has been registered for the
     *                               session ID
     */
    @Override
    public int venueId(final long sessionId) {
        final long id = venueBySessionId.get(sessionId);
        if (id == 0) {
            throw new IllegalStateException("Unknown session: " + sessionId);
        }
        return (int)id;
    }

    /**
     * Resolves a venue-supplied symbol to a platform instrument ID.
     *
     * <p>Unknown symbols are logged and returned as {@code 0}, the configured
     * unknown-ID sentinel. Callers decide whether to drop the message or take other
     * data-quality action.
     *
     * @param symbol FIX symbol as a character sequence
     * @return instrument ID, or {@code 0} if the symbol is not configured
     */
    @Override
    public int instrumentId(final CharSequence symbol) {
        final int id = instrumentBySymbol.getOrDefault(symbol.toString(), 0);
        if (id == 0) {
            LOGGER.log(Level.WARNING, "Unknown instrument symbol: {0}", symbol);
        }
        return id;
    }

    /**
     * Resolves an ASCII FIX symbol without constructing a {@link String}.
     *
     * <p>The configured symbol universe is small and immutable, so a linear scan
     * over cached symbol strings is predictable and avoids normal-path
     * allocation. Unknown symbols allocate only for the diagnostic message, which
     * is a recoverable data-quality path rather than the expected hot path.</p>
     */
    @Override
    public int instrumentId(final DirectBuffer buffer, final int offset, final int length) {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || length < 0 || offset + length > buffer.capacity()) {
            throw new IndexOutOfBoundsException("invalid symbol byte range");
        }
        for (int instrumentId = 1; instrumentId < instrumentSymbols.length; instrumentId++) {
            final String symbol = instrumentSymbols[instrumentId];
            if (symbol != null && asciiEquals(buffer, offset, length, symbol)) {
                return instrumentId;
            }
        }
        LOGGER.log(Level.WARNING, "Unknown instrument symbol: {0}",
            buffer.getStringWithoutLengthAscii(offset, length));
        return 0;
    }

    private static boolean asciiEquals(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final String symbol) {

        if (symbol.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if ((char)buffer.getByte(offset + i) != symbol.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the cached FIX symbol for an instrument ID.
     *
     * @param instrumentId configured instrument ID
     * @return cached symbol string, or {@code null} if the ID is unmapped
     */
    @Override
    public String symbolOf(final int instrumentId) {
        return instrumentSymbols[instrumentId];
    }

    /**
     * Returns the cached venue display name for a venue ID.
     *
     * @param venueId configured venue ID
     * @return cached venue name, or {@code null} if the ID is unmapped
     */
    @Override
    public String venueNameOf(final int venueId) {
        return venueNames[venueId];
    }
}
