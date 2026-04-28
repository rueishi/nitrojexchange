package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.marketdata.AbstractFixL3MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.marketdata.L3MarketDataContext;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.DirectBuffer;

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
 * output policy separate from both shared FIX semantics and cluster books while
 * inherited counters report expected safe drops without hot-path logging.
 */
public final class CoinbaseL3MarketDataNormalizer extends AbstractFixL3MarketDataNormalizer {
    private final IdRegistry idRegistry;
    private final GatewayDisruptor disruptor;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketByOrderEventEncoder marketByOrderEncoder = new MarketByOrderEventEncoder();

    public CoinbaseL3MarketDataNormalizer(final IdRegistry idRegistry, final GatewayDisruptor disruptor) {
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.disruptor = Objects.requireNonNull(disruptor, "disruptor");
    }

    public CoinbaseL3MarketDataNormalizer(
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor,
        final System.Logger logger) {
        this(idRegistry, disruptor);
        Objects.requireNonNull(logger, "logger");
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
            .putVenueOrderId(context.mdEntryIdBuffer, context.mdEntryIdOffset, context.mdEntryIdLength);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + marketByOrderEncoder.encodedLength();
        disruptor.publishSlot(slot);
        return true;
    }

}
