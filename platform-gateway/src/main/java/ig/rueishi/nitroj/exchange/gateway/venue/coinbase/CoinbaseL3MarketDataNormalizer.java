package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.marketdata.AbstractFixL3MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.marketdata.L3MarketDataContext;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.DirectBuffer;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Coinbase L3 market-data normalizer backed by the shared standard FIX parser.
 *
 * <p>Responsibility: publishes order-level FIX entries as
 * {@code MarketByOrderEvent} messages. Role in system: selected for Coinbase
 * venues configured with {@code marketDataModel = L3}, allowing the cluster to
 * maintain active order state and derive L2. Relationships: inherits standard
 * FIX L3 parsing from {@link AbstractFixL3MarketDataNormalizer}, resolves IDs
 * through {@link IdRegistry}, and publishes to {@link GatewayDisruptor}.
 * Lifecycle: one instance per venue runtime. Design intent: keep Coinbase L3
 * output policy separate from both shared FIX semantics and cluster books.
 */
public final class CoinbaseL3MarketDataNormalizer extends AbstractFixL3MarketDataNormalizer {
    private static final Logger DEFAULT_LOGGER =
        System.getLogger(CoinbaseL3MarketDataNormalizer.class.getName());

    private final IdRegistry idRegistry;
    private final GatewayDisruptor disruptor;
    private final Logger logger;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketByOrderEventEncoder marketByOrderEncoder = new MarketByOrderEventEncoder();
    private final byte[] venueOrderIdScratch = new byte[64];

    public CoinbaseL3MarketDataNormalizer(final IdRegistry idRegistry, final GatewayDisruptor disruptor) {
        this(idRegistry, disruptor, DEFAULT_LOGGER);
    }

    public CoinbaseL3MarketDataNormalizer(
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor,
        final Logger logger) {
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.disruptor = Objects.requireNonNull(disruptor, "disruptor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    protected int venueId(final long sessionId) {
        return idRegistry.venueId(sessionId);
    }

    @Override
    protected int instrumentId(final String symbol) {
        return idRegistry.instrumentId(symbol);
    }

    @Override
    protected int instrumentId(final DirectBuffer buffer, final int valueStart, final int valueEnd) {
        return idRegistry.instrumentId(buffer, valueStart, valueEnd - valueStart);
    }

    @Override
    protected boolean publish(final L3MarketDataContext context) {
        final GatewaySlot slot = disruptor.claimSlot();
        if (slot == null) {
            return false;
        }

        final int orderIdLength = copyAscii(context.mdEntryId, venueOrderIdScratch);
        marketByOrderEncoder.wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
            .venueId(context.venueId)
            .instrumentId(context.instrumentId)
            .side(context.side)
            .updateAction(context.updateAction)
            .priceScaled(context.priceScaled)
            .sizeScaled(context.sizeScaled)
            .ingressTimestampNanos(context.ingressNanos)
            .exchangeTimestampNanos(context.exchangeTimestampNanos)
            .fixSeqNum(context.fixSeqNum)
            .putVenueOrderId(venueOrderIdScratch, 0, orderIdLength);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + marketByOrderEncoder.encodedLength();
        disruptor.publishSlot(slot);
        return true;
    }

    @Override
    protected void onUnknownSymbol(final String symbol) {
        logger.log(Level.WARNING, "Unknown L3 market-data symbol {0}; dropping FIX tick", symbol);
    }

    @Override
    protected void onMalformedMessage(final RuntimeException ex) {
        logger.log(Level.WARNING, "Malformed Coinbase L3 market-data message; dropping FIX tick", ex);
    }

    private static int copyAscii(final String value, final byte[] destination) {
        if (value.length() > destination.length) {
            throw new IllegalArgumentException("Coinbase L3 order id exceeds " + destination.length + " bytes");
        }
        for (int i = 0; i < value.length(); i++) {
            destination[i] = (byte)value.charAt(i);
        }
        return value.length();
    }
}
