package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link ExternalLiquidityView}.
 *
 * <p>Responsibility: verifies that strategy-facing liquidity subtracts
 * NitroJEx-owned visible quantity while preserving gross public books. Role in
 * system: arbitrage reads this view to avoid self-liquidity false positives.
 * Relationships: uses real {@link InternalMarketView} and {@link OwnOrderOverlay}
 * instances to prove integration with venue L2 books. Lifecycle: each test owns
 * a fresh market view. Design intent: cover positive, negative, edge, exception,
 * and failure-like ordering cases through the public view API.</p>
 */
final class ExternalLiquidityViewTest {
    @Test
    void externalSize_noOwnOrders_returnsGrossSize() {
        final InternalMarketView view = marketWithBid(100L, 10L);

        assertThat(view.externalLiquidityView().externalSizeAt(1, 1, EntryType.BID, 100L)).isEqualTo(10L);
    }

    @Test
    void externalSize_l2Approximation_subtractsOwnLevel() {
        final InternalMarketView view = marketWithBid(100L, 10L);
        view.ownOrderOverlay().upsert(1L, null, 1, 1, EntryType.BID, 100L, 3L, true);

        assertThat(view.externalLiquidityView().externalSizeAt(1, 1, EntryType.BID, 100L)).isEqualTo(7L);
        assertThat(view.book(1, 1).bidSizeAt(0)).isEqualTo(10L);
    }

    @Test
    void externalSize_ownSizeExceedsGross_returnsZero() {
        final InternalMarketView view = marketWithBid(100L, 5L);
        view.ownOrderOverlay().upsert(1L, null, 1, 1, EntryType.BID, 100L, 8L, true);

        assertThat(view.externalLiquidityView().externalSizeAt(1, 1, EntryType.BID, 100L)).isZero();
    }

    @Test
    void externalSize_l3ExactMatch_subtractsVenueOrderId() {
        final InternalMarketView view = marketWithBid(100L, 10L);
        view.ownOrderOverlay().upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 4L, true);

        assertThat(view.externalLiquidityView().externalSizeAtL3Order(1, 1, EntryType.BID, 100L, 10L, "VO-1"))
            .isEqualTo(6L);
    }

    @Test
    void externalSize_l3MissingId_keepsGrossOrderSize() {
        final InternalMarketView view = marketWithBid(100L, 10L);
        view.ownOrderOverlay().upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 4L, true);

        assertThat(view.externalLiquidityView().externalSizeAtL3Order(1, 1, EntryType.BID, 100L, 10L, "UNKNOWN"))
            .isEqualTo(10L);
        assertThat(view.externalLiquidityView().externalSizeAt(1, 1, EntryType.BID, 100L)).isEqualTo(6L);
    }

    @Test
    void externalBestBid_skipsFullyOwnBestLevel() {
        final InternalMarketView view = new InternalMarketView();
        view.apply(decoder(EntryType.BID, 101L, 5L), 1L);
        view.apply(decoder(EntryType.BID, 100L, 7L), 1L);
        view.ownOrderOverlay().upsert(1L, "VO-1", 1, 1, EntryType.BID, 101L, 5L, true);

        assertThat(view.externalLiquidityView().externalBestBid(1, 1)).isEqualTo(100L);
    }

    @Test
    void externalBestAsk_unknownBook_returnsInvalidPrice() {
        final InternalMarketView view = new InternalMarketView();

        assertThat(view.externalLiquidityView().externalBestAsk(1, 1)).isEqualTo(Ids.INVALID_PRICE);
    }

    @Test
    void externalSize_rejectedOrTerminalCleanup_removesOwnLiquidity() {
        final InternalMarketView view = marketWithBid(100L, 10L);
        view.ownOrderOverlay().upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 4L, true);
        view.ownOrderOverlay().upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 4L, false);

        assertThat(view.externalLiquidityView().externalSizeAt(1, 1, EntryType.BID, 100L)).isEqualTo(10L);
    }

    private static InternalMarketView marketWithBid(final long price, final long size) {
        final InternalMarketView view = new InternalMarketView();
        view.apply(decoder(EntryType.BID, price, size), 1L);
        return view;
    }

    private static MarketDataEventDecoder decoder(final EntryType side, final long price, final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(1)
            .instrumentId(1)
            .entryType(side)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }
}
