package ig.rueishi.nitroj.exchange.gateway.marketdata;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;

/**
 * Shared base for standard FIX L2 market-data normalization.
 *
 * <p>Responsibility: provides common FIX tag mapping helpers and venue
 * enrichment hooks for L2 price-level feeds. Role in system: concrete venue L2
 * normalizers extend this class so they can reuse standard FIX semantics for
 * {@code 35=W/X}, {@code 269}, {@code 270}, {@code 271}, {@code 279},
 * {@code 1023}, and timestamp parsing. Relationships: publishes through
 * subclass-specific sinks and exposes {@link L2MarketDataContext} to
 * {@link #enrich(L2MarketDataContext)}. Lifecycle: one instance is owned by a
 * venue runtime and used on the Artio library thread. Design intent: centralize
 * common L2 protocol decisions while leaving current {@code MarketDataHandler}
 * behavior stable during the extraction slice.
 */
public abstract class AbstractFixL2MarketDataNormalizer implements MarketDataNormalizer {
    protected static final byte SOH = 1;
    protected static final long SCALE = 100_000_000L;
    private final FixScan scan = new FixScan();
    private long unknownSymbolDropCount;
    private long malformedMessageDropCount;

    @Override
    public Action onFixMessage(
        final long sessionId,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final long ingressNanos) {

        try {
            scan.reset(ingressNanos, venueId(sessionId));

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
     * Resolves a live Artio session id to the platform venue id.
     *
     * @param sessionId Artio session id
     * @return platform venue id
     */
    protected abstract int venueId(long sessionId);

    /**
     * Resolves a FIX symbol to the platform instrument id.
     *
     * @param symbol FIX symbol from tag 55
     * @return platform instrument id, or zero when unknown
     */
    protected abstract int instrumentId(String symbol);

    /**
     * Resolves a FIX symbol directly from bytes when a venue normalizer can do
     * so. The default preserves existing subclasses; Coinbase and other
     * production normalizers should override this path with the registry's
     * byte-based lookup to avoid normal-path symbol string allocation.
     */
    protected int instrumentId(final DirectBuffer buffer, final int valueStart, final int valueEnd) {
        return instrumentId(buffer.getStringWithoutLengthAscii(valueStart, valueEnd - valueStart));
    }

    /**
     * Publishes one fully normalized L2 entry.
     *
     * @param context parsed and enriched context
     * @return true when the entry was published
     */
    protected abstract boolean publish(L2MarketDataContext context);

    /**
     * Callback for unknown symbols. Implementations increment a counter and drop.
     *
     * @param symbol FIX symbol
     */
    protected void onUnknownSymbol(final String symbol) {
        unknownSymbolDropCount++;
    }

    protected void onUnknownSymbol(final DirectBuffer buffer, final int valueStart, final int valueEnd) {
        unknownSymbolDropCount++;
    }

    /**
     * Callback for malformed FIX messages. Implementations increment a counter
     * and drop without logging on the hot path.
     *
     * @param ex parse failure
     */
    protected void onMalformedMessage(final RuntimeException ex) {
        malformedMessageDropCount++;
    }

    protected void onMalformedMessage() {
        malformedMessageDropCount++;
    }

    public long unknownSymbolDropCount() {
        return unknownSymbolDropCount;
    }

    public long malformedMessageDropCount() {
        return malformedMessageDropCount;
    }

    /**
     * Venue-specific enrichment hook invoked after standard FIX fields are parsed.
     *
     * @param context mutable normalized entry context
     */
    protected void enrich(final L2MarketDataContext context) {
        // Default FIX L2 normalization requires no venue-specific enrichment.
    }

    protected boolean isMarketDataMessage(final byte msgType) {
        return msgType == 'W' || msgType == 'X';
    }

    protected EntryType mapEntryType(final byte mdEntryType) {
        return switch (mdEntryType) {
            case '0' -> EntryType.BID;
            case '1' -> EntryType.ASK;
            case '2' -> EntryType.TRADE;
            default -> EntryType.NULL_VAL;
        };
    }

    protected UpdateAction mapUpdateAction(final byte mdUpdateAction) {
        return switch (mdUpdateAction) {
            case '1' -> UpdateAction.CHANGE;
            case '2' -> UpdateAction.DELETE;
            default -> UpdateAction.NEW;
        };
    }

    protected static int parsePositiveInt(final DirectBuffer buffer, final int start, final int end) {
        return FixFieldParser.parsePositiveInt(buffer, start, end);
    }

    protected static long parseScaled(final DirectBuffer buffer, final int start, final int end) {
        return FixFieldParser.parseScaled(buffer, start, end);
    }

    protected static long parseFixTimestampNanos(final DirectBuffer buffer, final int start, final int end) {
        return FixFieldParser.parseFixTimestampNanos(buffer, start, end);
    }

    protected static int peekNextTag(final DirectBuffer buffer, final int cursor, final int end) {
        if (cursor >= end) {
            return -1;
        }
        int equals = cursor;
        while (equals < end && buffer.getByte(equals) != '=') {
            equals++;
        }
        if (equals >= end) {
            return -1;
        }
        return parsePositiveInt(buffer, cursor, equals);
    }

    private final class FixScan {
        private final L2MarketDataContext context = new L2MarketDataContext();
        private DirectBuffer messageSymbolBuffer;
        private int messageSymbolStart;
        private int messageSymbolEnd;
        private int messageInstrumentId;
        private boolean marketData;
        private boolean hasEntry;
        private UpdateAction pendingUpdateAction = UpdateAction.NEW;

        private void reset(final long ingressNanos, final int venueId) {
            context.clearEntry();
            context.ingressNanos = ingressNanos;
            context.venueId = venueId;
            context.fixSeqNum = 0;
            messageSymbolBuffer = null;
            messageSymbolStart = 0;
            messageSymbolEnd = 0;
            messageInstrumentId = 0;
            marketData = false;
            hasEntry = false;
            pendingUpdateAction = UpdateAction.NEW;
        }

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
                    final int decodedInstrumentId = instrumentId(buffer, valueStart, valueEnd);
                    if (hasEntry) {
                        context.setSymbolRange(buffer, valueStart, valueEnd - valueStart);
                        context.instrumentId = decodedInstrumentId;
                    } else {
                        messageSymbolBuffer = buffer;
                        messageSymbolStart = valueStart;
                        messageSymbolEnd = valueEnd;
                        messageInstrumentId = decodedInstrumentId;
                    }
                }
                case 269 -> {
                    finish();
                    hasEntry = true;
                    context.clearEntry();
                    if (messageSymbolBuffer != null) {
                        context.setSymbolRange(messageSymbolBuffer, messageSymbolStart, messageSymbolEnd - messageSymbolStart);
                    }
                    context.instrumentId = messageInstrumentId;
                    context.entryType = mapEntryType(buffer.getByte(valueStart));
                    context.updateAction = pendingUpdateAction;
                    pendingUpdateAction = UpdateAction.NEW;
                }
                case 270 -> context.priceScaled = parseScaled(buffer, valueStart, valueEnd);
                case 271 -> context.sizeScaled = parseScaled(buffer, valueStart, valueEnd);
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
                case 1023 -> context.priceLevel = parsePositiveInt(buffer, valueStart, valueEnd);
                case 60 -> context.exchangeTimestampNanos = parseFixTimestampNanos(buffer, valueStart, valueEnd);
                default -> { }
            }
        }

        private void finish() {
            if (!marketData || !hasEntry || context.entryType == EntryType.NULL_VAL || !context.hasSymbolRange()) {
                hasEntry = false;
                return;
            }
            if (context.instrumentId == 0) {
                onUnknownSymbol(context.symbolBuffer, context.symbolOffset, context.symbolOffset + context.symbolLength);
                hasEntry = false;
                return;
            }
            enrich(context);
            publish(context);
            hasEntry = false;
        }
    }
}
