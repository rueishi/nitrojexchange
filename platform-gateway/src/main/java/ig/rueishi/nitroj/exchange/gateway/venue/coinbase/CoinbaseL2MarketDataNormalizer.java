package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.marketdata.AbstractFixL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.marketdata.L2MarketDataContext;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.DirectBuffer;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Coinbase L2 market-data normalizer backed by the shared standard FIX parser.
 *
 * <p>Responsibility: resolves Coinbase session and symbol IDs, logs Coinbase
 * data-quality drops, and publishes normalized L2 entries to the gateway ring.
 * Role in system: selected when a Coinbase venue is configured with
 * {@code marketDataModel = L2}. Relationships: inherits FIX tag parsing from
 * {@link AbstractFixL2MarketDataNormalizer}, uses {@link IdRegistry} for IDs,
 * and emits {@code MarketDataEvent} payloads into {@link GatewayDisruptor}.
 * Lifecycle: one instance is owned by a venue runtime or compatibility handler.
 * Design intent: keep Coinbase-specific publication and warning policy out of
 * the shared parser.
 */
public final class CoinbaseL2MarketDataNormalizer extends AbstractFixL2MarketDataNormalizer {
    private static final Logger DEFAULT_LOGGER =
        System.getLogger(CoinbaseL2MarketDataNormalizer.class.getName());

    private final IdRegistry idRegistry;
    private final GatewayDisruptor disruptor;
    private final Logger logger;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketDataEventEncoder marketDataEncoder = new MarketDataEventEncoder();

    public CoinbaseL2MarketDataNormalizer(final IdRegistry idRegistry, final GatewayDisruptor disruptor) {
        this(idRegistry, disruptor, DEFAULT_LOGGER);
    }

    public CoinbaseL2MarketDataNormalizer(
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
    protected boolean publish(final L2MarketDataContext context) {
        final GatewaySlot slot = disruptor.claimSlot();
        if (slot == null) {
            return false;
        }

        marketDataEncoder.wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
            .venueId(context.venueId)
            .instrumentId(context.instrumentId)
            .entryType(context.entryType)
            .updateAction(context.updateAction)
            .priceScaled(context.priceScaled)
            .sizeScaled(context.sizeScaled)
            .priceLevel(context.priceLevel)
            .ingressTimestampNanos(context.ingressNanos)
            .exchangeTimestampNanos(context.exchangeTimestampNanos)
            .fixSeqNum(context.fixSeqNum);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + marketDataEncoder.encodedLength();
        disruptor.publishSlot(slot);
        return true;
    }

    @Override
    protected void onUnknownSymbol(final String symbol) {
        logger.log(Level.WARNING, "Unknown market-data symbol {0}; dropping FIX tick", symbol);
    }

    @Override
    protected void onMalformedMessage(final RuntimeException ex) {
        logger.log(Level.WARNING, "Malformed Coinbase L2 market-data message; dropping FIX tick", ex);
    }
}
