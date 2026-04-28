package ig.rueishi.nitroj.exchange.registry;

import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-005 {@link IdRegistryImpl}.
 *
 * <p>Responsibility: verifies the registry's startup initialization, primitive
 * Artio session mapping, forward symbol lookup, and reverse array lookup
 * contracts. Role in system: later gateway and cluster tasks depend on these
 * mappings to translate external FIX data into internal integer IDs. Relationships:
 * the tests use TASK-004 config record shapes directly and deliberately avoid
 * gateway/cluster runtime classes so the common module remains independently
 * testable. Lifecycle: executed by the platform-common unit test task after the
 * registry implementation is compiled. Design intent: lock in the V9.7/V10 rule
 * that {@code init()} populates name/symbol mappings only, while live Artio
 * {@code Session.id()} values are registered separately.
 */
final class IdRegistryImplTest {
    private static final long COINBASE_SESSION_ID = 101L;
    private static final long UNKNOWN_SESSION_ID = 202L;

    private IdRegistryImpl registry;

    /**
     * Creates a registry with the two configured venues and instruments used by
     * the execution plan's TOML examples.
     */
    @BeforeEach
    void setUp() {
        registry = new IdRegistryImpl();
        registry.init(venues(), instruments());
    }

    /**
     * Verifies a registered Coinbase Artio session resolves to venue ID 1.
     */
    @Test
    void venueId_sessionIdForCoinbase_returns1_afterRegisterSession() {
        registry.registerSession(Ids.VENUE_COINBASE, COINBASE_SESSION_ID);

        assertThat(registry.venueId(COINBASE_SESSION_ID)).isEqualTo(Ids.VENUE_COINBASE);
    }

    /**
     * Verifies unknown sessions fail fast because they indicate gateway wiring
     * missed session registration.
     */
    @Test
    void venueId_unknownSession_throwsIllegalStateException() {
        assertThatThrownBy(() -> registry.venueId(UNKNOWN_SESSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown session: " + UNKNOWN_SESSION_ID);
    }

    /**
     * Verifies the same Artio session ID cannot be mapped to two venues.
     */
    @Test
    void registerSession_sameSessionId_differentVenueId_throwsIllegalStateException() {
        registry.registerSession(Ids.VENUE_COINBASE, COINBASE_SESSION_ID);

        assertThatThrownBy(() -> registry.registerSession(Ids.VENUE_COINBASE_SANDBOX, COINBASE_SESSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already registered for venue " + Ids.VENUE_COINBASE);
    }

    /**
     * Verifies repeated registration of the same session/venue pair is safe.
     */
    @Test
    void registerSession_sameSessionId_sameVenueId_idempotent() {
        registry.registerSession(Ids.VENUE_COINBASE, COINBASE_SESSION_ID);
        registry.registerSession(Ids.VENUE_COINBASE, COINBASE_SESSION_ID);

        assertThat(registry.venueId(COINBASE_SESSION_ID)).isEqualTo(Ids.VENUE_COINBASE);
    }

    /**
     * Verifies init() does not infer session mappings from venue config.
     */
    @Test
    void init_doesNotPopulateVenueBySession() {
        assertThatThrownBy(() -> registry.venueId(COINBASE_SESSION_ID))
            .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Verifies configured BTC-USD resolves to the immutable instrument ID.
     */
    @Test
    void instrumentId_btcUsd_returns1() {
        assertThat(registry.instrumentId("BTC-USD")).isEqualTo(Ids.INSTRUMENT_BTC_USD);
    }

    /**
     * Verifies unknown symbols use the recoverable data-error sentinel.
     */
    @Test
    void instrumentId_unknownSymbol_returns0() {
        assertThat(registry.instrumentId("UNKNOWN-PAIR")).isZero();
    }

    /**
     * Verifies the API accepts a non-String CharSequence without requiring callers
     * to allocate a String before calling the registry.
     */
    @Test
    void instrumentId_asciiBufferSlice_noAllocation() {
        CharSequence symbolSlice = new FixedSymbolSlice("BTC-USD");

        assertThat(registry.instrumentId(symbolSlice)).isEqualTo(Ids.INSTRUMENT_BTC_USD);
    }

    @Test
    void instrumentId_directBufferKnownSymbol_returnsConfiguredId() {
        final UnsafeBuffer buffer = ascii("xxBTC-USDyy");

        assertThat(registry.instrumentId(buffer, 2, 7)).isEqualTo(Ids.INSTRUMENT_BTC_USD);
    }

    @Test
    void instrumentId_directBufferUnknownAndPrefixSymbols_returnExpectedSentinel() {
        assertThat(registry.instrumentId(ascii("BT"), 0, 2)).isZero();
        assertThat(registry.instrumentId(ascii("BTC-USDX"), 0, 8)).isZero();
        assertThat(registry.instrumentId(ascii("ETH-USD"), 0, 7)).isEqualTo(Ids.INSTRUMENT_ETH_USD);
    }

    @Test
    void instrumentId_directBufferInvalidRange_failsClearly() {
        final UnsafeBuffer buffer = ascii("BTC-USD");

        assertThatThrownBy(() -> registry.instrumentId(buffer, -1, 7))
            .isInstanceOf(IndexOutOfBoundsException.class)
            .hasMessageContaining("invalid symbol byte range");
        assertThatThrownBy(() -> registry.instrumentId(buffer, 0, 99))
            .isInstanceOf(IndexOutOfBoundsException.class)
            .hasMessageContaining("invalid symbol byte range");
        assertThatThrownBy(() -> registry.instrumentId(null, 0, 1))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("buffer");
    }

    /**
     * Verifies reverse instrument lookup uses the cached symbol array.
     */
    @Test
    void symbolOf_id1_returnsBtcUsd() {
        assertThat(registry.symbolOf(Ids.INSTRUMENT_BTC_USD)).isEqualTo("BTC-USD");
    }

    /**
     * Verifies reverse venue lookup uses the cached venue-name array.
     */
    @Test
    void venueNameOf_id1_returnsCoinbase() {
        assertThat(registry.venueNameOf(Ids.VENUE_COINBASE)).isEqualTo("COINBASE");
    }

    /**
     * Verifies init() populated name/symbol maps while leaving session mappings to
     * explicit gateway registration.
     */
    @Test
    void init_populatesNameMapsOnly() {
        assertThat(registry.instrumentId("ETH-USD")).isEqualTo(Ids.INSTRUMENT_ETH_USD);
        assertThat(registry.venueNameOf(Ids.VENUE_COINBASE_SANDBOX)).isEqualTo("COINBASE_SANDBOX");
        assertThatThrownBy(() -> registry.venueId(COINBASE_SESSION_ID))
            .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Verifies forward and reverse instrument mappings agree after init().
     */
    @Test
    void init_reverseArrayMatchesForwardMap() {
        int instrumentId = registry.instrumentId("BTC-USD");

        assertThat(registry.symbolOf(instrumentId)).isEqualTo("BTC-USD");
        assertThat(registry.venueNameOf(Ids.VENUE_COINBASE)).isEqualTo("COINBASE");
    }

    private static List<VenueConfig> venues() {
        return List.of(
            new VenueConfig(Ids.VENUE_COINBASE, "COINBASE", "fix.exchange.coinbase.com", 4198, false,
                FixPluginId.FIXT11_FIX50SP2, "COINBASE", MarketDataModel.L3,
                new VenueCapabilities(true, true, false)),
            new VenueConfig(Ids.VENUE_COINBASE_SANDBOX, "COINBASE_SANDBOX",
                "fix.sandbox.exchange.coinbase.com", 4198, true,
                FixPluginId.FIXT11_FIX50SP2, "COINBASE", MarketDataModel.L3,
                new VenueCapabilities(true, true, false))
        );
    }

    private static List<InstrumentConfig> instruments() {
        return List.of(
            new InstrumentConfig(Ids.INSTRUMENT_BTC_USD, "BTC-USD", "BTC", "USD"),
            new InstrumentConfig(Ids.INSTRUMENT_ETH_USD, "ETH-USD", "ETH", "USD")
        );
    }

    private static UnsafeBuffer ascii(final String value) {
        return new UnsafeBuffer(value.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Tiny CharSequence test double that behaves like a buffer slice from a FIX
     * decoder: callers can pass it without converting to String first.
     */
    private record FixedSymbolSlice(String value) implements CharSequence {
        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(final int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return value.subSequence(start, end);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
