package ig.rueishi.nitroj.exchange.tooling;

import ig.rueishi.nitroj.exchange.messages.*;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.agrona.DirectBuffer;

/**
 * Offline audit-log renderer for NitroJEx SBE archive records.
 *
 * <p>The tool reads records through {@link SBEReplayFile}, decodes the standard
 * SBE message header, dispatches all generated NitroJEx template ids, and writes
 * a human-readable log. Unknown or malformed records are reported in-line and
 * replay continues so incident review is resilient to partial trailing writes.</p>
 */
public final class FIXReplayTool {
    private FIXReplayTool() {
    }

    public static void main(final String[] args) throws Exception {
        final Path archiveDir = Path.of(requiredArg(args, "--archive-dir"));
        final long fromPosition = Long.parseLong(arg(args, "--from-position", "0"));
        final long toPosition = Long.parseLong(arg(args, "--to-position", Long.toString(Long.MAX_VALUE)));
        final Path output = Path.of(requiredArg(args, "--output"));
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            replay(archiveDir, fromPosition, toPosition, writer);
        }
    }

    public static void replay(final Path archivePath, final long fromPosition, final long toPosition, final PrintWriter writer) {
        try (SBEReplayFile replayFile = new SBEReplayFile(archivePath)) {
            replayFile.seekToPosition(fromPosition);
            while (replayFile.hasNext()) {
                final SBEReplayFile.Record record = replayFile.next();
                if (record.position() > toPosition) {
                    break;
                }
                writer.println(format(record));
            }
        }
    }

    static String format(final SBEReplayFile.Record record) {
        final MessageHeaderDecoder header = new MessageHeaderDecoder();
        try {
            if (record.length() < MessageHeaderDecoder.ENCODED_LENGTH) {
                return "[WARN position=" + record.position() + "] truncated header";
            }
            header.wrap(record.buffer(), 0);
            final int templateId = header.templateId();
            final StringBuilder body = new StringBuilder();
            try {
                appendDecoded(body, record.buffer(), header);
            } catch (Exception ex) {
                body.append("[WARN decode=").append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append("]");
            }
            return "[SEQ=" + record.position() + "] [T=" + Instant.EPOCH + "] [TEMPLATE=" + templateName(templateId) + "(" + templateId + ")] " + body;
        } catch (Exception ex) {
            return "[WARN position=" + record.position() + "] " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private static void appendDecoded(final StringBuilder out, final DirectBuffer buffer, final MessageHeaderDecoder header) {
        final int offset = MessageHeaderDecoder.ENCODED_LENGTH;
        final int blockLength = header.blockLength();
        final int version = header.version();
        switch (header.templateId()) {
            case MarketDataEventDecoder.TEMPLATE_ID -> new MarketDataEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case ExecutionEventDecoder.TEMPLATE_ID -> new ExecutionEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case VenueStatusEventDecoder.TEMPLATE_ID -> new VenueStatusEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case BalanceQueryResponseDecoder.TEMPLATE_ID -> new BalanceQueryResponseDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case NewOrderCommandDecoder.TEMPLATE_ID -> new NewOrderCommandDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case CancelOrderCommandDecoder.TEMPLATE_ID -> new CancelOrderCommandDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case FillEventDecoder.TEMPLATE_ID -> new FillEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case PositionEventDecoder.TEMPLATE_ID -> new PositionEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case RiskEventDecoder.TEMPLATE_ID -> new RiskEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case OrderStatusQueryCommandDecoder.TEMPLATE_ID -> new OrderStatusQueryCommandDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case BalanceQueryRequestDecoder.TEMPLATE_ID -> new BalanceQueryRequestDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case RecoveryCompleteEventDecoder.TEMPLATE_ID -> new RecoveryCompleteEventDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case AdminCommandDecoder.TEMPLATE_ID -> new AdminCommandDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case ReplaceOrderCommandDecoder.TEMPLATE_ID -> new ReplaceOrderCommandDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case OrderStateSnapshotDecoder.TEMPLATE_ID -> new OrderStateSnapshotDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case PositionSnapshotDecoder.TEMPLATE_ID -> new PositionSnapshotDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            case RiskStateSnapshotDecoder.TEMPLATE_ID -> new RiskStateSnapshotDecoder().wrap(buffer, offset, blockLength, version).appendTo(out);
            default -> out.append("[UNKNOWN templateId=").append(header.templateId()).append("]");
        }
    }

    private static String templateName(final int templateId) {
        return switch (templateId) {
            case MarketDataEventDecoder.TEMPLATE_ID -> "MarketDataEvent";
            case ExecutionEventDecoder.TEMPLATE_ID -> "ExecutionEvent";
            case VenueStatusEventDecoder.TEMPLATE_ID -> "VenueStatusEvent";
            case BalanceQueryResponseDecoder.TEMPLATE_ID -> "BalanceQueryResponse";
            case NewOrderCommandDecoder.TEMPLATE_ID -> "NewOrderCommand";
            case CancelOrderCommandDecoder.TEMPLATE_ID -> "CancelOrderCommand";
            case FillEventDecoder.TEMPLATE_ID -> "FillEvent";
            case PositionEventDecoder.TEMPLATE_ID -> "PositionEvent";
            case RiskEventDecoder.TEMPLATE_ID -> "RiskEvent";
            case OrderStatusQueryCommandDecoder.TEMPLATE_ID -> "OrderStatusQueryCommand";
            case BalanceQueryRequestDecoder.TEMPLATE_ID -> "BalanceQueryRequest";
            case RecoveryCompleteEventDecoder.TEMPLATE_ID -> "RecoveryCompleteEvent";
            case AdminCommandDecoder.TEMPLATE_ID -> "AdminCommand";
            case ReplaceOrderCommandDecoder.TEMPLATE_ID -> "ReplaceOrderCommand";
            case OrderStateSnapshotDecoder.TEMPLATE_ID -> "OrderStateSnapshot";
            case PositionSnapshotDecoder.TEMPLATE_ID -> "PositionSnapshot";
            case RiskStateSnapshotDecoder.TEMPLATE_ID -> "RiskStateSnapshot";
            default -> "UNKNOWN";
        };
    }

    private static String requiredArg(final String[] args, final String name) {
        final String value = arg(args, name, null);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private static String arg(final String[] args, final String name, final String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
