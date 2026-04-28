package ig.rueishi.nitroj.exchange.gateway.marketdata;

import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;

/**
 * Shared base for standard FIX L3 market-data normalization.
 *
 * <p>Responsibility: centralizes common order-level FIX market-data mappings,
 * including MDEntryType side mapping, MDUpdateAction mapping, and default order
 * identity selection from MDEntryID tag 278. Role in system: concrete venue L3
 * normalizers extend this class and implement message scanning/publishing while
 * inheriting shared policy hooks. Relationships: reuses numeric parsing helpers
 * from {@link AbstractFixL2MarketDataNormalizer} and exposes
 * {@link L3MarketDataContext} to venue-specific enrichment. Lifecycle: one
 * instance is owned by a venue runtime and invoked on the Artio library thread.
 * Design intent: keep L3 support broad and FIX-standard by default while still
 * allowing venues to customize identity/delete/size semantics.
 */
public abstract class AbstractFixL3MarketDataNormalizer extends AbstractFixL2MarketDataNormalizer {

    @Override
    public Action onFixMessage(
        final long sessionId,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final long ingressNanos) {

        try {
            final FixScan scan = new FixScan();
            scan.context.ingressNanos = ingressNanos;
            scan.context.venueId = venueId(sessionId);

            final int end = offset + length;
            int cursor = offset;
            while (cursor < end) {
                final int tagStart = cursor;
                int equals = tagStart;
                while (equals < end && buffer.getByte(equals) != '=') {
                    equals++;
                }
                if (equals >= end) {
                    break;
                }

                int valueEnd = equals + 1;
                while (valueEnd < end && buffer.getByte(valueEnd) != SOH) {
                    valueEnd++;
                }

                scan.accept(buffer, parsePositiveInt(buffer, tagStart, equals), equals + 1, valueEnd,
                    peekNextTag(buffer, valueEnd + 1, end));
                cursor = valueEnd + 1;
            }
            scan.finish();
        } catch (RuntimeException ex) {
            onMalformedMessage(ex);
        }
        return Action.CONTINUE;
    }

    /**
     * Publishes one fully normalized L3 entry.
     *
     * @param context parsed and enriched context
     * @return true when the entry was published
     */
    protected abstract boolean publish(L3MarketDataContext context);

    /**
     * Venue-specific enrichment hook invoked after standard L3 fields are parsed.
     *
     * @param context mutable normalized L3 context
     */
    protected void enrich(final L3MarketDataContext context) {
        // Default FIX L3 normalization requires no venue-specific enrichment.
    }

    /**
     * Returns the order identity used for MarketByOrderEvent and L3 book state.
     *
     * @param context parsed L3 entry
     * @return MDEntryID by default
     */
    protected String orderIdentity(final L3MarketDataContext context) {
        return context.mdEntryId;
    }

    protected boolean sizeIsAbsolute() {
        return true;
    }

    protected boolean deleteRequiresPreviousState() {
        return true;
    }

    protected Side mapSide(final byte mdEntryType) {
        return switch (mdEntryType) {
            case '0' -> Side.BUY;
            case '1' -> Side.SELL;
            default -> Side.NULL_VAL;
        };
    }

    @Override
    protected UpdateAction mapUpdateAction(final byte mdUpdateAction) {
        return super.mapUpdateAction(mdUpdateAction);
    }

    @Override
    protected final boolean publish(final L2MarketDataContext context) {
        return false;
    }

    private final class FixScan {
        private final L3MarketDataContext context = new L3MarketDataContext();
        private String messageSymbol;
        private int messageInstrumentId;
        private boolean marketData;
        private boolean hasEntry;
        private UpdateAction pendingUpdateAction = UpdateAction.NEW;

        private void accept(
            final DirectBuffer buffer,
            final int tag,
            final int valueStart,
            final int valueEnd,
            final int nextTag) {
            switch (tag) {
                case 35 -> marketData = isMarketDataMessage(buffer.getByte(valueStart));
                case 34 -> context.fixSeqNum = parsePositiveInt(buffer, valueStart, valueEnd);
                case 55 -> {
                    final String decodedSymbol = buffer.getStringWithoutLengthAscii(valueStart, valueEnd - valueStart);
                    final int decodedInstrumentId = instrumentId(buffer, valueStart, valueEnd);
                    if (hasEntry) {
                        context.symbol = decodedSymbol;
                        context.instrumentId = decodedInstrumentId;
                    } else {
                        messageSymbol = decodedSymbol;
                        messageInstrumentId = decodedInstrumentId;
                    }
                }
                case 269 -> {
                    finish();
                    hasEntry = true;
                    context.clearEntry();
                    context.symbol = messageSymbol;
                    context.instrumentId = messageInstrumentId;
                    context.side = mapSide(buffer.getByte(valueStart));
                    context.updateAction = pendingUpdateAction;
                    pendingUpdateAction = UpdateAction.NEW;
                }
                case 270 -> context.priceScaled = parseScaled(buffer, valueStart, valueEnd);
                case 271 -> context.sizeScaled = parseScaled(buffer, valueStart, valueEnd);
                case 278 -> context.mdEntryId = buffer.getStringWithoutLengthAscii(valueStart, valueEnd - valueStart);
                case 280 -> context.mdEntryRefId = buffer.getStringWithoutLengthAscii(valueStart, valueEnd - valueStart);
                case 279 -> {
                    if (hasEntry && nextTag == 269) {
                        finish();
                        pendingUpdateAction = mapUpdateAction(buffer.getByte(valueStart));
                    } else if (hasEntry) {
                        context.updateAction = mapUpdateAction(buffer.getByte(valueStart));
                    } else {
                        pendingUpdateAction = mapUpdateAction(buffer.getByte(valueStart));
                    }
                }
                case 60 -> context.exchangeTimestampNanos = parseFixTimestampNanos(buffer, valueStart, valueEnd);
                default -> { }
            }
        }

        private void finish() {
            if (!marketData || !hasEntry || context.side == Side.NULL_VAL || context.symbol == null) {
                hasEntry = false;
                return;
            }
            if (context.instrumentId == 0) {
                onUnknownSymbol(context.symbol);
                hasEntry = false;
                return;
            }
            enrich(context);
            final String identity = orderIdentity(context);
            if (identity == null || identity.isBlank()) {
                onMalformedMessage(new IllegalArgumentException("L3 MDEntryID tag 278 is required"));
                hasEntry = false;
                return;
            }
            context.mdEntryId = identity;
            publish(context);
            hasEntry = false;
        }
    }
}
