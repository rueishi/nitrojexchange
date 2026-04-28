package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.common.BoundedTextIdentity;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.session.Session;

import java.util.Objects;

/**
 * Artio session callback that normalizes FIX ExecutionReport messages.
 *
 * <p>Responsibility: parse standard FIX MsgType {@code 8} reports, map venue
 * execution semantics into NitroJEx's SBE {@link ig.rueishi.nitroj.exchange.messages.ExecutionEvent}
 * schema, and publish the result through the gateway disruptor. Role in system:
 * this handler is the execution ingress adapter between Artio and the cluster.
 * Relationships: {@link IdRegistry} resolves venue and instrument IDs,
 * {@link GatewayDisruptor} owns preallocated publication slots, and downstream
 * order/portfolio/risk components consume the encoded SBE event in later tasks.</p>
 *
 * <p>Lifecycle: the gateway creates one handler after startup wiring is complete;
 * Artio calls it on the library thread for each inbound FIX message. Design
 * intent: fills are lossless and spin until a slot is available, while non-fill
 * execution events use a single non-blocking claim. This implements the task
 * card's distinction between position-critical fill data and recoverable status
 * noise.</p>
 */
public final class ExecutionHandler implements SessionHandler {
    private static final byte SOH = 1;
    private static final long SCALE = 100_000_000L;

    private final IdRegistry idRegistry;
    private final GatewayDisruptor disruptor;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final ExecutionEventEncoder executionEncoder = new ExecutionEventEncoder();
    private final ExecutionScan scan = new ExecutionScan();

    /**
     * Creates an ExecutionReport handler.
     *
     * @param idRegistry registry for live Artio session and symbol lookups
     * @param disruptor gateway handoff ring for encoded SBE events
     */
    public ExecutionHandler(final IdRegistry idRegistry, final GatewayDisruptor disruptor) {
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.disruptor = Objects.requireNonNull(disruptor, "disruptor");
    }

    /**
     * Handles an Artio FIX message.
     *
     * <p>The first executable line stamps ingress time before scanning any FIX
     * tag. Only MsgType {@code 8} is published; market data and other messages are
     * ignored so handlers can be composed defensively during session wiring.</p>
     *
     * @return {@link Action#CONTINUE} for all handled and ignored messages
     */
    @Override
    public Action onMessage(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final int libraryId,
        final Session session,
        final int sequenceIndex,
        final long messageType,
        final long timestamp,
        final long position,
        final OnMessageInfo messageInfo) {

        final long ingressNanos = System.nanoTime();
        return handleExecution(ingressNanos, session.id(), buffer, offset, length);
    }

    /**
     * Test-facing entry point that avoids constructing an Artio session.
     *
     * @param sessionId registered Artio session id
     * @param buffer FIX message bytes
     * @param offset message offset
     * @param length message length
     * @return Artio continuation action
     */
    Action onMessageForTest(final long sessionId, final DirectBuffer buffer, final int offset, final int length) {
        final long ingressNanos = System.nanoTime();
        return handleExecution(ingressNanos, sessionId, buffer, offset, length);
    }

    @Override
    public void onTimeout(final int libraryId, final Session session) {
        // Session lifecycle handling is outside TASK-010.
    }

    @Override
    public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow) {
        // Slow-consumer policy is owned by Artio/session lifecycle tasks.
    }

    @Override
    public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason) {
        return Action.CONTINUE;
    }

    @Override
    public void onSessionStart(final Session session) {
        // Session registration is owned by later gateway wiring tasks.
    }

    private Action handleExecution(
        final long ingressNanos,
        final long sessionId,
        final DirectBuffer buffer,
        final int offset,
        final int length) {

        scan.reset();
        scan.ingressNanos = ingressNanos;
        scan.venueId = idRegistry.venueId(sessionId);

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
            final int tag = parsePositiveInt(buffer, tagStart, equals);
            if (tag < 0) {
                scan.malformed = true;
                break;
            }
            scan.accept(buffer, tag, equals + 1, valueEnd);
            cursor = valueEnd + 1;
        }

        if (!scan.executionReport || scan.malformed) {
            return Action.CONTINUE;
        }
        publish(scan, buffer);
        return Action.CONTINUE;
    }

    private void publish(final ExecutionScan scan, final DirectBuffer sourceBuffer) {
        if (!scan.validForPublish()) {
            return;
        }
        scan.instrumentId = idRegistry.instrumentId(scan.symbolBuffer, scan.symbolStart, scan.symbolLength);
        if (scan.instrumentId == 0) {
            return;
        }

        GatewaySlot slot = disruptor.claimSlot();
        while (slot == null && scan.execType == ExecType.FILL) {
            Thread.onSpinWait();
            slot = disruptor.claimSlot();
        }
        if (slot == null) {
            return;
        }

        executionEncoder.wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
            .clOrdId(scan.clOrdId)
            .venueId(scan.venueId)
            .instrumentId(scan.instrumentId)
            .execType(scan.execType)
            .side(scan.side)
            .fillPriceScaled(scan.fillPriceScaled)
            .fillQtyScaled(scan.fillQtyScaled)
            .cumQtyScaled(scan.cumQtyScaled)
            .leavesQtyScaled(scan.leavesQtyScaled)
            .rejectCode(scan.rejectCode)
            .ingressTimestampNanos(scan.ingressNanos)
            .exchangeTimestampNanos(scan.exchangeTimestampNanos)
            .fixSeqNum(scan.fixSeqNum)
            .isFinal(scan.isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(sourceBuffer, scan.venueOrderIdStart, scan.venueOrderIdLength)
            .putExecId(sourceBuffer, scan.execIdStart, scan.execIdLength);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + executionEncoder.encodedLength();
        disruptor.publishSlot(slot);
    }

    /**
     * Maps standard FIX ExecType characters into NitroJEx SBE enum values.
     *
     * @param fixExecType tag 150 as a single FIX character
     * @return internal SBE enum value
     */
    static ExecType mapExecType(final char fixExecType) {
        return switch (fixExecType) {
            case '0' -> ExecType.NEW;
            case '1' -> ExecType.PARTIAL_FILL;
            case '4' -> ExecType.CANCELED;
            case '5' -> ExecType.REPLACED;
            case '8' -> ExecType.REJECTED;
            case 'C' -> ExecType.EXPIRED;
            case 'F' -> ExecType.FILL;
            case 'I' -> ExecType.ORDER_STATUS;
            default -> ExecType.NULL_VAL;
        };
    }

    private static boolean terminal(final ExecType execType, final long leavesQtyScaled) {
        return execType == ExecType.CANCELED
            || execType == ExecType.REPLACED
            || execType == ExecType.REJECTED
            || execType == ExecType.EXPIRED
            || (execType == ExecType.FILL && leavesQtyScaled == 0L);
    }

    private static Side side(final DirectBuffer buffer, final int start) {
        return buffer.getByte(start) == '1' ? Side.BUY : Side.SELL;
    }

    private static int parsePositiveInt(final DirectBuffer buffer, final int start, final int end) {
        if (start >= end) {
            return -1;
        }
        int value = 0;
        for (int i = start; i < end; i++) {
            final byte b = buffer.getByte(i);
            if (b < '0' || b > '9') {
                return -1;
            }
            value = (value * 10) + (b - '0');
        }
        return value;
    }

    private static long parsePositiveLong(final DirectBuffer buffer, final int start, final int end) {
        if (start >= end) {
            return -1L;
        }
        long value = 0;
        for (int i = start; i < end; i++) {
            final byte b = buffer.getByte(i);
            if (b < '0' || b > '9') {
                return -1L;
            }
            value = (value * 10) + (b - '0');
        }
        return value;
    }

    private static long parseScaled(final DirectBuffer buffer, final int start, final int end) {
        if (start >= end) {
            return -1L;
        }
        long whole = 0;
        long fraction = 0;
        int fractionDigits = 0;
        boolean afterDecimal = false;
        for (int i = start; i < end; i++) {
            final byte b = buffer.getByte(i);
            if (b == '.') {
                if (afterDecimal) {
                    return -1L;
                }
                afterDecimal = true;
            } else if (afterDecimal && fractionDigits < 8) {
                if (b < '0' || b > '9') {
                    return -1L;
                }
                fraction = (fraction * 10) + (b - '0');
                fractionDigits++;
            } else if (!afterDecimal) {
                if (b < '0' || b > '9') {
                    return -1L;
                }
                whole = (whole * 10) + (b - '0');
            }
        }
        while (fractionDigits < 8) {
            fraction *= 10;
            fractionDigits++;
        }
        return (whole * SCALE) + fraction;
    }

    private static long parseFixTimestampNanos(final DirectBuffer buffer, final int start, final int end) {
        if (end - start < 17
            || buffer.getByte(start + 8) != '-'
            || buffer.getByte(start + 11) != ':'
            || buffer.getByte(start + 14) != ':') {
            return -1L;
        }
        final int year = parsePositiveInt(buffer, start, start + 4);
        final int month = parsePositiveInt(buffer, start + 4, start + 6);
        final int day = parsePositiveInt(buffer, start + 6, start + 8);
        final int hour = parsePositiveInt(buffer, start + 9, start + 11);
        final int minute = parsePositiveInt(buffer, start + 12, start + 14);
        final int second = parsePositiveInt(buffer, start + 15, start + 17);
        if (year < 0 || month < 1 || month > 12 || day < 1 || day > 31
            || hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 60) {
            return -1L;
        }
        int nanos = 0;
        int digits = 0;
        for (int i = start + 18; i < end && digits < 9; i++) {
            final byte b = buffer.getByte(i);
            if (b < '0' || b > '9') {
                return -1L;
            }
            nanos = (nanos * 10) + (b - '0');
            digits++;
        }
        while (digits < 9) {
            nanos *= 10;
            digits++;
        }
        final long epochDay = epochDay(year, month, day);
        return ((((epochDay * 24L) + hour) * 60L + minute) * 60L + second) * 1_000_000_000L + nanos;
    }

    private static long epochDay(final int year, final int month, final int day) {
        long y = year;
        long m = month;
        long total = 365L * y;
        if (y >= 0) {
            total += (y + 3L) / 4L - (y + 99L) / 100L + (y + 399L) / 400L;
        } else {
            total -= y / -4L - y / -100L + y / -400L;
        }
        total += ((367L * m - 362L) / 12L) + day - 1L;
        if (m > 2L) {
            total -= isLeapYear(year) ? 1L : 2L;
        }
        return total - 719_528L;
    }

    private static boolean isLeapYear(final int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    private static final class ExecutionScan {
        private boolean executionReport;
        private boolean malformed;
        private long ingressNanos;
        private int venueId;
        private int instrumentId;
        private int fixSeqNum;
        private DirectBuffer symbolBuffer;
        private int symbolStart;
        private int symbolLength;
        private long clOrdId;
        private ExecType execType = ExecType.NULL_VAL;
        private Side side = Side.NULL_VAL;
        private long fillPriceScaled;
        private long fillQtyScaled;
        private long cumQtyScaled;
        private long leavesQtyScaled;
        private int rejectCode;
        private long exchangeTimestampNanos;
        private int venueOrderIdStart;
        private int venueOrderIdLength;
        private int execIdStart;
        private int execIdLength;
        private boolean isFinal;

        private void reset() {
            executionReport = false;
            malformed = false;
            ingressNanos = 0L;
            venueId = 0;
            instrumentId = 0;
            fixSeqNum = 0;
            symbolBuffer = null;
            symbolStart = 0;
            symbolLength = 0;
            clOrdId = 0L;
            execType = ExecType.NULL_VAL;
            side = Side.NULL_VAL;
            fillPriceScaled = 0L;
            fillQtyScaled = 0L;
            cumQtyScaled = 0L;
            leavesQtyScaled = 0L;
            rejectCode = 0;
            exchangeTimestampNanos = 0L;
            venueOrderIdStart = 0;
            venueOrderIdLength = 0;
            execIdStart = 0;
            execIdLength = 0;
            isFinal = false;
        }

        private void accept(final DirectBuffer buffer, final int tag, final int valueStart, final int valueEnd) {
            if (malformed || valueStart >= valueEnd) {
                malformed = true;
                return;
            }
            switch (tag) {
                case 35 -> executionReport = buffer.getByte(valueStart) == '8';
                case 34 -> {
                    fixSeqNum = parsePositiveInt(buffer, valueStart, valueEnd);
                    malformed = fixSeqNum < 0;
                }
                case 11 -> {
                    clOrdId = parsePositiveLong(buffer, valueStart, valueEnd);
                    malformed = clOrdId < 0;
                }
                case 17 -> {
                    execIdStart = valueStart;
                    execIdLength = valueEnd - valueStart;
                    malformed = !boundedIdentityLength(execIdLength);
                }
                case 31 -> {
                    fillPriceScaled = parseScaled(buffer, valueStart, valueEnd);
                    malformed = fillPriceScaled < 0;
                }
                case 32 -> {
                    fillQtyScaled = parseScaled(buffer, valueStart, valueEnd);
                    malformed = fillQtyScaled < 0;
                }
                case 37 -> {
                    venueOrderIdStart = valueStart;
                    venueOrderIdLength = valueEnd - valueStart;
                    malformed = !boundedIdentityLength(venueOrderIdLength);
                }
                case 54 -> side = side(buffer, valueStart);
                case 55 -> {
                    symbolBuffer = buffer;
                    symbolStart = valueStart;
                    symbolLength = valueEnd - valueStart;
                    malformed = symbolLength <= 0;
                }
                case 60 -> {
                    exchangeTimestampNanos = parseFixTimestampNanos(buffer, valueStart, valueEnd);
                    malformed = exchangeTimestampNanos < 0;
                }
                case 103 -> {
                    if (execType == ExecType.REJECTED) {
                        rejectCode = parsePositiveInt(buffer, valueStart, valueEnd);
                        malformed = rejectCode < 0;
                    }
                }
                case 14 -> {
                    cumQtyScaled = parseScaled(buffer, valueStart, valueEnd);
                    malformed = cumQtyScaled < 0;
                }
                case 150 -> {
                    execType = mapExecType((char)buffer.getByte(valueStart));
                    malformed = execType == ExecType.NULL_VAL;
                    if (execType != ExecType.REJECTED) {
                        rejectCode = 0;
                    }
                }
                case 151 -> {
                    leavesQtyScaled = parseScaled(buffer, valueStart, valueEnd);
                    malformed = leavesQtyScaled < 0;
                    isFinal = terminal(execType, leavesQtyScaled);
                }
                default -> { }
            }
            if (tag == 150 || tag == 151) {
                isFinal = terminal(execType, leavesQtyScaled);
            }
        }

        private boolean validForPublish() {
            return !malformed
                && symbolBuffer != null
                && symbolLength > 0
                && execType != ExecType.NULL_VAL
                && venueOrderIdLength > 0
                && execIdLength > 0;
        }

        private static boolean boundedIdentityLength(final int length) {
            return length > 0 && length <= BoundedTextIdentity.DEFAULT_MAX_LENGTH;
        }
    }
}
