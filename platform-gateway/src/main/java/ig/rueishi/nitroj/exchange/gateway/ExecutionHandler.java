package ig.rueishi.nitroj.exchange.gateway;

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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

        final ExecutionScan scan = new ExecutionScan();
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
            scan.accept(buffer, parsePositiveInt(buffer, tagStart, equals), equals + 1, valueEnd);
            cursor = valueEnd + 1;
        }

        if (!scan.executionReport) {
            return Action.CONTINUE;
        }
        publish(scan, buffer);
        return Action.CONTINUE;
    }

    private void publish(final ExecutionScan scan, final DirectBuffer sourceBuffer) {
        scan.instrumentId = idRegistry.instrumentId(scan.symbol);
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
        int value = 0;
        for (int i = start; i < end; i++) {
            value = (value * 10) + (buffer.getByte(i) - '0');
        }
        return value;
    }

    private static long parsePositiveLong(final DirectBuffer buffer, final int start, final int end) {
        long value = 0;
        for (int i = start; i < end; i++) {
            value = (value * 10) + (buffer.getByte(i) - '0');
        }
        return value;
    }

    private static long parseScaled(final DirectBuffer buffer, final int start, final int end) {
        long whole = 0;
        long fraction = 0;
        int fractionDigits = 0;
        boolean afterDecimal = false;
        for (int i = start; i < end; i++) {
            final byte b = buffer.getByte(i);
            if (b == '.') {
                afterDecimal = true;
            } else if (afterDecimal && fractionDigits < 8) {
                fraction = (fraction * 10) + (b - '0');
                fractionDigits++;
            } else if (!afterDecimal) {
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
        if (start >= end) {
            return 0L;
        }
        final int year = parsePositiveInt(buffer, start, start + 4);
        final int month = parsePositiveInt(buffer, start + 4, start + 6);
        final int day = parsePositiveInt(buffer, start + 6, start + 8);
        final int hour = parsePositiveInt(buffer, start + 9, start + 11);
        final int minute = parsePositiveInt(buffer, start + 12, start + 14);
        final int second = parsePositiveInt(buffer, start + 15, start + 17);
        int nanos = 0;
        int digits = 0;
        for (int i = start + 18; i < end && digits < 9; i++) {
            nanos = (nanos * 10) + (buffer.getByte(i) - '0');
            digits++;
        }
        while (digits < 9) {
            nanos *= 10;
            digits++;
        }
        return (LocalDateTime.of(year, month, day, hour, minute, second)
            .toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L) + nanos;
    }

    private static final class ExecutionScan {
        private boolean executionReport;
        private long ingressNanos;
        private int venueId;
        private int instrumentId;
        private int fixSeqNum;
        private String symbol;
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

        private void accept(final DirectBuffer buffer, final int tag, final int valueStart, final int valueEnd) {
            switch (tag) {
                case 35 -> executionReport = buffer.getByte(valueStart) == '8';
                case 34 -> fixSeqNum = parsePositiveInt(buffer, valueStart, valueEnd);
                case 11 -> clOrdId = parsePositiveLong(buffer, valueStart, valueEnd);
                case 17 -> {
                    execIdStart = valueStart;
                    execIdLength = valueEnd - valueStart;
                }
                case 31 -> fillPriceScaled = parseScaled(buffer, valueStart, valueEnd);
                case 32 -> fillQtyScaled = parseScaled(buffer, valueStart, valueEnd);
                case 37 -> {
                    venueOrderIdStart = valueStart;
                    venueOrderIdLength = valueEnd - valueStart;
                }
                case 54 -> side = side(buffer, valueStart);
                case 55 -> symbol = buffer.getStringWithoutLengthAscii(valueStart, valueEnd - valueStart);
                case 60 -> exchangeTimestampNanos = parseFixTimestampNanos(buffer, valueStart, valueEnd);
                case 103 -> {
                    if (execType == ExecType.REJECTED) {
                        rejectCode = parsePositiveInt(buffer, valueStart, valueEnd);
                    }
                }
                case 14 -> cumQtyScaled = parseScaled(buffer, valueStart, valueEnd);
                case 150 -> {
                    execType = mapExecType((char)buffer.getByte(valueStart));
                    if (execType != ExecType.REJECTED) {
                        rejectCode = 0;
                    }
                }
                case 151 -> {
                    leavesQtyScaled = parseScaled(buffer, valueStart, valueEnd);
                    isFinal = terminal(execType, leavesQtyScaled);
                }
                default -> { }
            }
            if (tag == 150 || tag == 151) {
                isFinal = terminal(execType, leavesQtyScaled);
            }
        }
    }
}
