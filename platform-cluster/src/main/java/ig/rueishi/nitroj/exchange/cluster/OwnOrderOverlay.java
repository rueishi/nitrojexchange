package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.common.VenueOrderId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tracks NitroJEx-owned visible working liquidity outside the public market book.
 *
 * <p>Responsibility: maintain a small cluster-thread overlay of NitroJEx's own
 * resting quantity by venue, instrument, side, and price. Role in system:
 * arbitrage and hedging read this overlay through {@link ExternalLiquidityView}
 * to avoid treating NitroJEx's own quotes as external executable liquidity.
 * Relationships: {@link OrderState} remains the authoritative order lifecycle
 * source, while {@link L2OrderBook}, {@link VenueL3Book}, and
 * {@link ConsolidatedL2Book} remain gross venue-published market views. Lifecycle:
 * updated from order-manager state transitions and cleared on cluster reset.
 * Design intent: keep self-liquidity subtraction explicit and reversible without
 * contaminating public market-data books with private order ownership.</p>
 */
public final class OwnOrderOverlay {
    public static final int DEFAULT_MAX_OWN_ORDERS = 100_000;
    private final int maxOwnOrders;
    private final Map<Long, OwnOrder> ordersByClOrdId = new HashMap<>();
    private final Map<VenueOrderKey, Long> clOrdIdByVenueOrderId = new HashMap<>();
    private final Map<LevelKey, Long> ownSizeByLevel = new HashMap<>();

    public OwnOrderOverlay() {
        this(DEFAULT_MAX_OWN_ORDERS);
    }

    public OwnOrderOverlay(final int maxOwnOrders) {
        if (maxOwnOrders <= 0) {
            throw new IllegalArgumentException("maxOwnOrders must be positive");
        }
        this.maxOwnOrders = maxOwnOrders;
    }

    /**
     * Upserts or removes an order using current {@link OrderState} fields.
     *
     * @param order live order state from the order manager
     */
    public void updateFrom(final OrderState order) {
        Objects.requireNonNull(order, "order");
        if (!order.isWorkingVisible()) {
            remove(order.clOrdId());
            return;
        }
        upsert(
            order.clOrdId(),
            order.venueOrderId(),
            order.venueId(),
            order.instrumentId(),
            toEntryType(order.side()),
            order.priceScaled(),
            order.leavesQtyScaled(),
            true);
    }

    /**
     * Upserts a visible own order or removes it when it is not working.
     *
     * @param clOrdId internal client order ID
     * @param venueOrderId venue order ID, optional for L2 approximation
     * @param venueId venue ID
     * @param instrumentId instrument ID
     * @param side book side
     * @param priceScaled price
     * @param remainingQtyScaled visible remaining quantity
     * @param working true when the order should be considered visible/working
     */
    public void upsert(
        final long clOrdId,
        final String venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled,
        final long remainingQtyScaled,
        final boolean working) {

        if (!working || remainingQtyScaled <= 0L) {
            remove(clOrdId);
            return;
        }
        validate(clOrdId, venueId, instrumentId, side, priceScaled, remainingQtyScaled);
        final OwnOrder previous = ordersByClOrdId.get(clOrdId);
        if (previous == null && ordersByClOrdId.size() >= maxOwnOrders) {
            return;
        }
        ordersByClOrdId.remove(clOrdId);
        if (previous != null) {
            adjust(previous.levelKey(), -previous.remainingQtyScaled);
            if (hasText(previous.venueOrderId)) {
                clOrdIdByVenueOrderId.remove(previous.venueOrderKey());
            }
        }

        final OwnOrder next = new OwnOrder(clOrdId, normalizedVenueOrderId(venueOrderId), venueId, instrumentId,
            side, priceScaled, remainingQtyScaled);
        ordersByClOrdId.put(clOrdId, next);
        adjust(next.levelKey(), remainingQtyScaled);
        if (hasText(next.venueOrderId)) {
            clOrdIdByVenueOrderId.put(next.venueOrderKey(), clOrdId);
        }
    }

    /**
     * Removes a tracked order.
     *
     * @param clOrdId internal client order ID
     */
    public void remove(final long clOrdId) {
        final OwnOrder previous = ordersByClOrdId.remove(clOrdId);
        if (previous == null) {
            return;
        }
        adjust(previous.levelKey(), -previous.remainingQtyScaled);
        if (hasText(previous.venueOrderId)) {
            clOrdIdByVenueOrderId.remove(previous.venueOrderKey());
        }
    }

    public void clear() {
        ordersByClOrdId.clear();
        clOrdIdByVenueOrderId.clear();
        ownSizeByLevel.clear();
    }

    public long ownSizeAt(
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        return ownSizeByLevel.getOrDefault(new LevelKey(venueId, instrumentId, side, priceScaled), 0L);
    }

    public long exactOwnSizeByVenueOrderId(
        final String venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        if (!hasText(venueOrderId)) {
            return 0L;
        }
        return exactOwnSizeByVenueOrderId(normalizedVenueOrderId(venueOrderId), venueId, instrumentId, side, priceScaled);
    }

    public long exactOwnSizeByVenueOrderId(
        final VenueOrderId venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        if (venueOrderId == null) {
            return 0L;
        }
        final Long clOrdId = clOrdIdByVenueOrderId.get(new VenueOrderKey(venueId, instrumentId, venueOrderId));
        if (clOrdId == null) {
            return 0L;
        }
        final OwnOrder order = ordersByClOrdId.get(clOrdId);
        if (order == null
            || order.venueId != venueId
            || order.instrumentId != instrumentId
            || order.side != side
            || order.priceScaled != priceScaled) {
            return 0L;
        }
        return order.remainingQtyScaled;
    }

    /**
     * Reports whether a venue L3 order belongs to NitroJEx.
     *
     * <p>The match is deliberately strict: venue order ID, venue, instrument,
     * side, and price must all agree with current order-manager-derived state.
     * This prevents stale venue IDs or reused public order IDs from marking a
     * gross L3 order as own after the original NitroJEx order is gone.</p>
     *
     * @param venueOrderId venue-assigned order identifier from the L3 feed
     * @param venueId venue ID
     * @param instrumentId instrument ID
     * @param side book side
     * @param priceScaled price
     * @return true when this L3 order is a current NitroJEx-owned order
     */
    public boolean isOwnVenueOrder(
        final String venueOrderId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled) {

        return exactOwnSizeByVenueOrderId(venueOrderId, venueId, instrumentId, side, priceScaled) > 0L;
    }

    /**
     * Checks whether a new order would cross NitroJEx's own resting liquidity.
     *
     * @param venueId venue ID
     * @param instrumentId instrument ID
     * @param newOrderSide side of the proposed order
     * @param priceScaled proposed limit price
     * @return true when the proposed order can match an own opposite-side order
     */
    public boolean wouldSelfCross(
        final int venueId,
        final int instrumentId,
        final EntryType newOrderSide,
        final long priceScaled) {

        for (OwnOrder order : ordersByClOrdId.values()) {
            if (order.venueId != venueId || order.instrumentId != instrumentId) {
                continue;
            }
            if (newOrderSide == EntryType.BID && order.side == EntryType.ASK && order.priceScaled <= priceScaled) {
                return true;
            }
            if (newOrderSide == EntryType.ASK && order.side == EntryType.BID && order.priceScaled >= priceScaled) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for self-crossing after external-liquidity subtraction.
     *
     * <p>Own orders at a strictly better opposite price always block because a
     * marketable order would sweep them before the target level. Own orders at
     * the target price block only when no external quantity remains there; when
     * external size remains, the strategy can reduce size and rely on venue
     * self-trade prevention for the residual queue ambiguity.</p>
     */
    public boolean wouldSelfCrossBeyondExternal(
        final int venueId,
        final int instrumentId,
        final EntryType newOrderSide,
        final long priceScaled,
        final long externalSizeAtPrice) {

        for (OwnOrder order : ordersByClOrdId.values()) {
            if (order.venueId != venueId || order.instrumentId != instrumentId) {
                continue;
            }
            if (newOrderSide == EntryType.BID && order.side == EntryType.ASK) {
                if (order.priceScaled < priceScaled || (order.priceScaled == priceScaled && externalSizeAtPrice <= 0L)) {
                    return true;
                }
            }
            if (newOrderSide == EntryType.ASK && order.side == EntryType.BID) {
                if (order.priceScaled > priceScaled || (order.priceScaled == priceScaled && externalSizeAtPrice <= 0L)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int orderCount() {
        return ordersByClOrdId.size();
    }

    private void adjust(final LevelKey key, final long delta) {
        final long next = ownSizeByLevel.getOrDefault(key, 0L) + delta;
        if (next <= 0L) {
            ownSizeByLevel.remove(key);
        } else {
            ownSizeByLevel.put(key, next);
        }
    }

    private static void validate(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long priceScaled,
        final long remainingQtyScaled) {

        if (clOrdId <= 0L || venueId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException("own order identity must be positive");
        }
        if (side != EntryType.BID && side != EntryType.ASK) {
            throw new IllegalArgumentException("own order side must be BID or ASK");
        }
        if (priceScaled <= 0L || remainingQtyScaled <= 0L) {
            throw new IllegalArgumentException("own order price and remaining quantity must be positive");
        }
    }

    private static EntryType toEntryType(final byte side) {
        return Side.get(side) == Side.BUY ? EntryType.BID : EntryType.ASK;
    }

    private static VenueOrderId normalizedVenueOrderId(final String value) {
        return value == null || value.isBlank() ? null : VenueOrderId.fromAscii(value);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasText(final VenueOrderId value) {
        return value != null;
    }

    private record OwnOrder(
        long clOrdId,
        VenueOrderId venueOrderId,
        int venueId,
        int instrumentId,
        EntryType side,
        long priceScaled,
        long remainingQtyScaled) {

        private LevelKey levelKey() {
            return new LevelKey(venueId, instrumentId, side, priceScaled);
        }

        private VenueOrderKey venueOrderKey() {
            return new VenueOrderKey(venueId, instrumentId, venueOrderId);
        }
    }

    private record LevelKey(int venueId, int instrumentId, EntryType side, long priceScaled) {
    }

    private record VenueOrderKey(int venueId, int instrumentId, VenueOrderId venueOrderId) {
    }
}
