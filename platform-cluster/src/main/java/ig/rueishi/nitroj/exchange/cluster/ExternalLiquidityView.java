package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;

import java.util.Objects;

/**
 * Strategy-facing executable-liquidity view.
 *
 * <p>Responsibility: expose venue L2 prices and sizes after subtracting
 * NitroJEx-owned visible working quantity from the gross public market book.
 * Role in system: arbitrage and hedging use this view so they do not create
 * false opportunities from their own resting orders. Relationships:
 * {@link InternalMarketView} remains the owner of gross venue/consolidated books;
 * {@link OwnOrderOverlay} supplies private self-liquidity. Lifecycle: constructed
 * with the market view and reused on the single cluster thread. Design intent:
 * provide conservative L2 subtraction by default and exact L3 venue-order-ID
 * subtraction when a venue exposes reliable order identities.</p>
 */
public final class ExternalLiquidityView {
    private final InternalMarketView marketView;
    private final OwnOrderOverlay ownOrders;

    public ExternalLiquidityView(final InternalMarketView marketView, final OwnOrderOverlay ownOrders) {
        this.marketView = Objects.requireNonNull(marketView, "marketView");
        this.ownOrders = Objects.requireNonNull(ownOrders, "ownOrders");
    }

    public long externalSizeAt(
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        final long gross = grossSizeAt(venueId, instrumentId, side, priceScaled);
        return Math.max(0L, gross - ownOrders.ownSizeAt(venueId, instrumentId, side, priceScaled));
    }

    public long externalSizeAtL3Order(
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled,
        final long grossSizeScaled,
        final String venueOrderId) {

        final long exactOwn = ownOrders.exactOwnSizeByVenueOrderId(
            venueOrderId, venueId, instrumentId, side, priceScaled);
        return Math.max(0L, grossSizeScaled - exactOwn);
    }

    public long externalBestBid(final int venueId, final int instrumentId) {
        final L2OrderBook book = marketView.findBook(venueId, instrumentId);
        if (book == null) {
            return Ids.INVALID_PRICE;
        }
        for (int level = 0; level < L2OrderBook.LEVELS_PER_SIDE; level++) {
            final long price = book.bidPriceAt(level);
            if (price == Ids.INVALID_PRICE) {
                return Ids.INVALID_PRICE;
            }
            if (externalSizeAt(venueId, instrumentId, EntryType.BID, price) > 0L) {
                return price;
            }
        }
        return Ids.INVALID_PRICE;
    }

    public long externalBestAsk(final int venueId, final int instrumentId) {
        final L2OrderBook book = marketView.findBook(venueId, instrumentId);
        if (book == null) {
            return Ids.INVALID_PRICE;
        }
        for (int level = 0; level < L2OrderBook.LEVELS_PER_SIDE; level++) {
            final long price = book.askPriceAt(level);
            if (price == Ids.INVALID_PRICE) {
                return Ids.INVALID_PRICE;
            }
            if (externalSizeAt(venueId, instrumentId, EntryType.ASK, price) > 0L) {
                return price;
            }
        }
        return Ids.INVALID_PRICE;
    }

    private long grossSizeAt(
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        final L2OrderBook book = marketView.findBook(venueId, instrumentId);
        if (book == null || (side != EntryType.BID && side != EntryType.ASK)) {
            return 0L;
        }
        for (int level = 0; level < L2OrderBook.LEVELS_PER_SIDE; level++) {
            final long price = side == EntryType.BID ? book.bidPriceAt(level) : book.askPriceAt(level);
            if (price == Ids.INVALID_PRICE) {
                return 0L;
            }
            if (price == priceScaled) {
                return side == EntryType.BID ? book.bidSizeAt(level) : book.askSizeAt(level);
            }
        }
        return 0L;
    }
}
