package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.common.VenueOrderId;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimum active L3 book for one venue/instrument pair.
 *
 * <p>Responsibility: stores live order-level market-data entries and derives
 * aggregate L2 price-level updates after each L3 add/change/delete. Role in
 * system: V11 cluster market-data routing applies {@code MarketByOrderEvent}
 * messages here for L3 venues, then updates the existing per-venue
 * {@link L2OrderBook} so strategies can keep consuming L2 views. Relationships:
 * {@link MarketByOrderEventDecoder} supplies normalized order-level changes;
 * {@link L2OrderBook} remains the authoritative price-level book. Lifecycle:
 * one instance is owned per {@code (venueId,instrumentId)} L3 feed and retains
 * only active orders; deleted orders are removed. Design intent: implement the
 * minimal L3 state needed for correct L2 derivation. Optional own-order
 * reconciliation and queue-position methods are advisory views over the same
 * gross L3 state; they never mark or remove orders from the public book.
 */
public final class VenueL3Book {
    public static final int DEFAULT_MAX_ACTIVE_ORDERS = 100_000;
    private final int venueId;
    private final int instrumentId;
    private final int maxActiveOrders;
    private final Map<VenueOrderId, OrderEntry> orders = new LinkedHashMap<>();
    private final Map<LevelKey, Long> levels = new HashMap<>();
    private final UnsafeBuffer derivedBuffer = new UnsafeBuffer(new byte[256]);
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketDataEventEncoder marketDataEncoder = new MarketDataEventEncoder();
    private final MarketDataEventDecoder marketDataDecoder = new MarketDataEventDecoder();

    public VenueL3Book(final int venueId, final int instrumentId) {
        this(venueId, instrumentId, DEFAULT_MAX_ACTIVE_ORDERS);
    }

    public VenueL3Book(final int venueId, final int instrumentId, final int maxActiveOrders) {
        if (maxActiveOrders <= 0) {
            throw new IllegalArgumentException("maxActiveOrders must be positive");
        }
        this.venueId = venueId;
        this.instrumentId = instrumentId;
        this.maxActiveOrders = maxActiveOrders;
    }

    /**
     * Applies one L3 order update and propagates the derived level to an L2 book.
     *
     * @param event normalized L3 order-level event
     * @param l2Book per-venue L2 book to update with the derived level
     * @param clusterTimeMicros cluster timestamp used by the L2 book
     * @return true when the L3 update changed state, false for missing deletes or invalid entries
     */
    public boolean apply(
        final MarketByOrderEventDecoder event,
        final L2OrderBook l2Book,
        final long clusterTimeMicros) {

        if (event.venueId() != venueId || event.instrumentId() != instrumentId) {
            throw new IllegalArgumentException("L3 event does not match book venue/instrument");
        }

        final VenueOrderId orderId = venueOrderId(event);
        if (orderId == null || event.side() == Side.NULL_VAL) {
            return false;
        }

        return switch (event.updateAction()) {
            case DELETE -> delete(orderId, event, l2Book, clusterTimeMicros);
            case NEW, CHANGE -> upsert(orderId, event, l2Book, clusterTimeMicros);
            default -> false;
        };
    }

    public int activeOrderCount() {
        return orders.size();
    }

    public long levelSize(final Side side, final long priceScaled) {
        return levels.getOrDefault(new LevelKey(side, priceScaled), 0L);
    }

    /**
     * Reports whether a venue order ID is currently visible in this L3 book.
     *
     * @param venueOrderId venue-assigned order identifier
     * @return true when the order is active in gross L3 state
     */
    public boolean containsOrder(final String venueOrderId) {
        return orders.containsKey(requireVenueOrderId(venueOrderId));
    }

    /**
     * Returns the visible gross L3 size for one venue order ID.
     *
     * @param venueOrderId venue-assigned order identifier
     * @return visible order size, or zero when absent
     */
    public long orderSize(final String venueOrderId) {
        final OrderEntry order = orders.get(requireVenueOrderId(venueOrderId));
        return order == null ? 0L : order.sizeScaled;
    }

    /**
     * Returns exact externally executable size for one L3 order.
     *
     * <p>The gross L3 book remains unmodified. If the order ID matches a live
     * NitroJEx-owned order in the overlay with the same venue, instrument, side,
     * and price, that own visible quantity is subtracted. Mismatches return the
     * original gross order size so stale or unrelated private state cannot
     * corrupt public market-data state.</p>
     *
     * @param venueOrderId venue-assigned order identifier
     * @param ownOrders private own-order overlay derived from order-manager state
     * @return non-negative external visible size for the order
     */
    public long externalOrderSize(final String venueOrderId, final OwnOrderOverlay ownOrders) {
        Objects.requireNonNull(ownOrders, "ownOrders");
        final VenueOrderId normalizedOrderId = requireVenueOrderId(venueOrderId);
        final OrderEntry order = orders.get(normalizedOrderId);
        if (order == null) {
            return 0L;
        }
        final EntryType entryType = order.side == Side.BUY ? EntryType.BID : EntryType.ASK;
        final long ownSize = ownOrders.exactOwnSizeByVenueOrderId(
            normalizedOrderId, venueId, instrumentId, entryType, order.priceScaled);
        return Math.max(0L, order.sizeScaled - ownSize);
    }

    /**
     * Estimates same-level queue position for a visible order.
     *
     * <p>This method is explicitly advisory. It returns {@link
     * QueuePositionEstimate#unknown()} when the caller tells us feed ordering is
     * unavailable or when the order ID is absent. When ordering is available, the
     * current insertion order of active L3 orders is used as the best estimate
     * and only same-side, same-price quantity ahead of the target is counted.</p>
     *
     * @param venueOrderId venue-assigned order identifier
     * @param feedOrderingAvailable true when the venue/feed preserves useful order sequencing
     * @return known or unknown queue-position estimate
     */
    public QueuePositionEstimate queuePosition(final String venueOrderId, final boolean feedOrderingAvailable) {
        final VenueOrderId normalizedOrderId = requireVenueOrderId(venueOrderId);
        if (!feedOrderingAvailable) {
            return QueuePositionEstimate.unknown();
        }
        final OrderEntry target = orders.get(normalizedOrderId);
        if (target == null) {
            return QueuePositionEstimate.unknown();
        }
        long sizeAhead = 0L;
        for (Map.Entry<VenueOrderId, OrderEntry> entry : orders.entrySet()) {
            if (entry.getKey().equals(normalizedOrderId)) {
                return QueuePositionEstimate.known(sizeAhead, target.sizeScaled);
            }
            final OrderEntry candidate = entry.getValue();
            if (candidate.side == target.side && candidate.priceScaled == target.priceScaled) {
                sizeAhead += candidate.sizeScaled;
            }
        }
        return QueuePositionEstimate.unknown();
    }

    private boolean upsert(
        final VenueOrderId orderId,
        final MarketByOrderEventDecoder event,
        final L2OrderBook l2Book,
        final long clusterTimeMicros) {

        final OrderEntry previous = orders.get(orderId);
        if (previous == null && event.updateAction() == UpdateAction.CHANGE) {
            return false;
        }
        if (previous == null && orders.size() >= maxActiveOrders) {
            return false;
        }
        if (previous != null) {
            adjustLevel(previous.side, previous.priceScaled, -previous.sizeScaled);
        }

        if (event.sizeScaled() == 0L) {
            orders.remove(orderId);
            publishDerivedLevel(previous == null ? event.side() : previous.side,
                previous == null ? event.priceScaled() : previous.priceScaled,
                l2Book,
                clusterTimeMicros,
                event);
            return previous != null;
        }

        final OrderEntry next = new OrderEntry(event.side(), event.priceScaled(), event.sizeScaled());
        orders.put(orderId, next);
        if (previous != null && (previous.side != next.side || previous.priceScaled != next.priceScaled)) {
            publishDerivedLevel(previous.side, previous.priceScaled, l2Book, clusterTimeMicros, event);
        }
        adjustLevel(next.side, next.priceScaled, next.sizeScaled);
        publishDerivedLevel(next.side, next.priceScaled, l2Book, clusterTimeMicros, event);
        return true;
    }

    private boolean delete(
        final VenueOrderId orderId,
        final MarketByOrderEventDecoder event,
        final L2OrderBook l2Book,
        final long clusterTimeMicros) {

        final OrderEntry previous = orders.remove(orderId);
        if (previous == null) {
            return false;
        }
        adjustLevel(previous.side, previous.priceScaled, -previous.sizeScaled);
        publishDerivedLevel(previous.side, previous.priceScaled, l2Book, clusterTimeMicros, event);
        return true;
    }

    private void adjustLevel(final Side side, final long priceScaled, final long delta) {
        final LevelKey key = new LevelKey(side, priceScaled);
        final long next = levels.getOrDefault(key, 0L) + delta;
        if (next <= 0L) {
            levels.remove(key);
        } else {
            levels.put(key, next);
        }
    }

    private void publishDerivedLevel(
        final Side side,
        final long priceScaled,
        final L2OrderBook l2Book,
        final long clusterTimeMicros,
        final MarketByOrderEventDecoder source) {

        final long size = levelSize(side, priceScaled);
        final EntryType entryType = side == Side.BUY ? EntryType.BID : EntryType.ASK;
        marketDataEncoder.wrapAndApplyHeader(derivedBuffer, 0, headerEncoder)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .entryType(entryType)
            .updateAction(size == 0L ? UpdateAction.DELETE : UpdateAction.CHANGE)
            .priceScaled(priceScaled)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(source.ingressTimestampNanos())
            .exchangeTimestampNanos(source.exchangeTimestampNanos())
            .fixSeqNum(source.fixSeqNum());
        marketDataDecoder.wrap(derivedBuffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        l2Book.apply(marketDataDecoder, clusterTimeMicros);
    }

    private static VenueOrderId venueOrderId(final MarketByOrderEventDecoder event) {
        final int length = event.venueOrderIdLength();
        if (length == 0) {
            return null;
        }
        final byte[] bytes = new byte[length];
        event.getVenueOrderId(bytes, 0, length);
        return VenueOrderId.fromBytes(bytes, 0, length);
    }

    private static VenueOrderId requireVenueOrderId(final String venueOrderId) {
        if (venueOrderId == null || venueOrderId.isBlank()) {
            throw new IllegalArgumentException("venueOrderId must not be null or blank");
        }
        return VenueOrderId.fromAscii(venueOrderId);
    }

    private record OrderEntry(Side side, long priceScaled, long sizeScaled) {
    }

    private record LevelKey(Side side, long priceScaled) {
    }
}
