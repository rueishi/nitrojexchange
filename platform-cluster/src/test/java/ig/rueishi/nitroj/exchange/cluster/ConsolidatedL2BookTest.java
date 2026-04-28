package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for cross-venue L2 aggregation.
 *
 * <p>Responsibility: verifies same-price summing, per-venue contribution
 * removal, zero-size updates, best bid/ask recalculation, empty-book sentinels,
 * and integration with {@link InternalMarketView}. Role in system: protects the
 * optional consolidated market view used for fair-price, hedging, and arbitrage
 * decisions. Relationships: uses real generated MarketDataEvent codecs so the
 * InternalMarketView path matches production dispatch. Lifecycle: runs in the
 * cluster unit-test suite. Design intent: prove consolidation is layered on top
 * of per-venue L2 rather than replacing or corrupting venue books.
 */
final class ConsolidatedL2BookTest {

    @Test
    void samePriceAcrossVenues_sumsSize() {
        final ConsolidatedL2Book book = new ConsolidatedL2Book(Ids.INSTRUMENT_BTC_USD);

        book.applyVenueLevel(1, EntryType.BID, 100L, 10L);
        book.applyVenueLevel(2, EntryType.BID, 100L, 15L);

        assertThat(book.sizeAt(EntryType.BID, 100L)).isEqualTo(25L);
        assertThat(book.bestBid()).isEqualTo(100L);
    }

    @Test
    void deleteOneVenue_keepsOtherVenueContribution() {
        final ConsolidatedL2Book book = new ConsolidatedL2Book(Ids.INSTRUMENT_BTC_USD);
        book.applyVenueLevel(1, EntryType.ASK, 101L, 10L);
        book.applyVenueLevel(2, EntryType.ASK, 101L, 15L);

        book.applyVenueLevel(1, EntryType.ASK, 101L, 0L);

        assertThat(book.sizeAt(EntryType.ASK, 101L)).isEqualTo(15L);
        assertThat(book.bestAsk()).isEqualTo(101L);
    }

    @Test
    void bestBidAsk_recalculateAfterZeroSizeUpdate() {
        final ConsolidatedL2Book book = new ConsolidatedL2Book(Ids.INSTRUMENT_BTC_USD);
        book.applyVenueLevel(1, EntryType.BID, 100L, 1L);
        book.applyVenueLevel(1, EntryType.BID, 101L, 1L);
        book.applyVenueLevel(1, EntryType.ASK, 104L, 1L);
        book.applyVenueLevel(1, EntryType.ASK, 103L, 1L);

        book.applyVenueLevel(1, EntryType.BID, 101L, 0L);
        book.applyVenueLevel(1, EntryType.ASK, 103L, 0L);

        assertThat(book.bestBid()).isEqualTo(100L);
        assertThat(book.bestAsk()).isEqualTo(104L);
    }

    @Test
    void emptyBook_returnsInvalidSentinels() {
        final ConsolidatedL2Book book = new ConsolidatedL2Book(Ids.INSTRUMENT_BTC_USD);

        assertThat(book.bestBid()).isEqualTo(Ids.INVALID_PRICE);
        assertThat(book.bestAsk()).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void capacityExceeded_ignoresNewContributionAndPreservesExistingState() {
        final ConsolidatedL2Book book = new ConsolidatedL2Book(Ids.INSTRUMENT_BTC_USD, 1);

        book.applyVenueLevel(1, EntryType.BID, 100L, 10L);
        book.applyVenueLevel(2, EntryType.BID, 101L, 10L);

        assertThat(book.sizeAt(EntryType.BID, 100L)).isEqualTo(10L);
        assertThat(book.sizeAt(EntryType.BID, 101L)).isZero();
        assertThat(book.bestBid()).isEqualTo(100L);
    }

    @Test
    void invalidCapacity_throwsClearly() {
        assertThatThrownBy(() -> new ConsolidatedL2Book(Ids.INSTRUMENT_BTC_USD, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxContributions");
    }

    @Test
    void internalMarketView_updatesVenueAndConsolidatedBooks() {
        final InternalMarketView view = new InternalMarketView();

        view.apply(event(1, EntryType.BID, 100L, 10L), 1L);
        view.apply(event(2, EntryType.BID, 100L, 15L), 2L);

        assertThat(view.getBestBid(1, Ids.INSTRUMENT_BTC_USD)).isEqualTo(100L);
        assertThat(view.consolidatedBestBid(Ids.INSTRUMENT_BTC_USD)).isEqualTo(100L);
        assertThat(view.consolidatedBook(Ids.INSTRUMENT_BTC_USD).sizeAt(EntryType.BID, 100L)).isEqualTo(25L);
    }

    private static MarketDataEventDecoder event(
        final int venueId,
        final EntryType entryType,
        final long price,
        final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(entryType)
            .updateAction(size == 0L ? UpdateAction.DELETE : UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }
}
