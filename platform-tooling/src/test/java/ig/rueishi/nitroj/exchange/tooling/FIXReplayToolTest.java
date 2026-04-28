package ig.rueishi.nitroj.exchange.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.*;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit coverage for the offline FIX/SBE replay tooling.
 *
 * <p>The tests write small deterministic replay files using the record format
 * consumed by SBEReplayFile. They verify seeking, truncation handling, template
 * dispatch, unknown-template output, empty archives, and position range filters
 * without requiring a live Aeron Archive process.</p>
 */
final class FIXReplayToolTest {
    @TempDir
    Path tempDir;

    @Test
    void sbeReplayFile_seekToPosition_nextReturnsExpectedRecord() throws Exception {
        final Path file = tempDir.resolve("archive.sbe");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            writeRecord(out, 10L, marketData());
            writeRecord(out, 20L, headerOnly(999, 0));
        }

        try (SBEReplayFile replayFile = new SBEReplayFile(file)) {
            replayFile.seekToPosition(20L);
            assertThat(replayFile.next().position()).isEqualTo(20L);
        }
    }

    @Test
    void sbeReplayFile_truncatedTrailingRecord_warnLoggedAndContinues() throws Exception {
        final Path file = tempDir.resolve("archive.sbe");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            writeRecord(out, 10L, marketData());
            out.writeLong(20L);
            out.writeInt(100);
            out.write(new byte[]{1, 2, 3});
        }

        try (SBEReplayFile replayFile = new SBEReplayFile(file)) {
            assertThat(replayFile.hasNext()).isTrue();
            assertThat(replayFile.next().position()).isEqualTo(10L);
            assertThat(replayFile.hasNext()).isFalse();
        }
    }

    @Test
    void fixReplayTool_allTemplateIds_formattedCorrectly() throws Exception {
        final Path file = tempDir.resolve("archive.sbe");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            int position = 1;
            for (TemplateSpec spec : templateSpecs()) {
                writeRecord(out, position++, headerOnly(spec.templateId, spec.blockLength));
            }
        }

        final String output = replay(file, 0, Long.MAX_VALUE);

        assertThat(output).contains("MarketDataEvent", "ExecutionEvent", "AdminCommand", "RiskStateSnapshot");
    }

    @Test
    void fixReplayTool_unknownTemplateId_unknownLineEmitted() throws Exception {
        final Path file = tempDir.resolve("archive.sbe");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            writeRecord(out, 1L, headerOnly(999, 0));
        }

        assertThat(replay(file, 0, Long.MAX_VALUE)).contains("[UNKNOWN templateId=999]");
    }

    @Test
    void fixReplayTool_emptyArchive_noOutputNoException() throws Exception {
        final Path file = tempDir.resolve("archive.sbe");
        Files.createFile(file);

        assertThat(replay(file, 0, Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void fixReplayTool_rangeStartEnd_filtersRecordsCorrectly() throws Exception {
        final Path file = tempDir.resolve("archive.sbe");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            writeRecord(out, 1L, marketData());
            writeRecord(out, 2L, marketData());
            writeRecord(out, 3L, marketData());
        }

        final String output = replay(file, 2L, 2L);

        assertThat(output).contains("[SEQ=2]");
        assertThat(output).doesNotContain("[SEQ=1]", "[SEQ=3]");
    }

    private String replay(final Path path, final long from, final long to) {
        final StringWriter out = new StringWriter();
        FIXReplayTool.replay(path, from, to, new PrintWriter(out));
        return out.toString();
    }

    private static void writeRecord(final DataOutputStream out, final long position, final byte[] payload) throws Exception {
        out.writeLong(position);
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static byte[] marketData() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(EntryType.BID)
            .updateAction(UpdateAction.NEW)
            .priceScaled(65_000L * Ids.SCALE)
            .sizeScaled(Ids.SCALE)
            .priceLevel(0)
            .ingressTimestampNanos(1)
            .exchangeTimestampNanos(1)
            .fixSeqNum(1);
        return copy(buffer, MessageHeaderEncoder.ENCODED_LENGTH + MarketDataEventEncoder.BLOCK_LENGTH);
    }

    private static byte[] headerOnly(final int templateId, final int blockLength) {
        final int length = MessageHeaderEncoder.ENCODED_LENGTH + blockLength + 64;
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[length]);
        new MessageHeaderEncoder().wrap(buffer, 0)
            .blockLength(blockLength)
            .templateId(templateId)
            .schemaId(MessageHeaderEncoder.SCHEMA_ID)
            .version(MessageHeaderEncoder.SCHEMA_VERSION);
        return copy(buffer, length);
    }

    private static byte[] copy(final UnsafeBuffer buffer, final int length) {
        final byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static TemplateSpec[] templateSpecs() {
        return new TemplateSpec[]{
            new TemplateSpec(MarketDataEventDecoder.TEMPLATE_ID, MarketDataEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(ExecutionEventDecoder.TEMPLATE_ID, ExecutionEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(VenueStatusEventDecoder.TEMPLATE_ID, VenueStatusEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(BalanceQueryResponseDecoder.TEMPLATE_ID, BalanceQueryResponseDecoder.BLOCK_LENGTH),
            new TemplateSpec(NewOrderCommandDecoder.TEMPLATE_ID, NewOrderCommandDecoder.BLOCK_LENGTH),
            new TemplateSpec(CancelOrderCommandDecoder.TEMPLATE_ID, CancelOrderCommandDecoder.BLOCK_LENGTH),
            new TemplateSpec(FillEventDecoder.TEMPLATE_ID, FillEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(PositionEventDecoder.TEMPLATE_ID, PositionEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(RiskEventDecoder.TEMPLATE_ID, RiskEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(OrderStatusQueryCommandDecoder.TEMPLATE_ID, OrderStatusQueryCommandDecoder.BLOCK_LENGTH),
            new TemplateSpec(BalanceQueryRequestDecoder.TEMPLATE_ID, BalanceQueryRequestDecoder.BLOCK_LENGTH),
            new TemplateSpec(RecoveryCompleteEventDecoder.TEMPLATE_ID, RecoveryCompleteEventDecoder.BLOCK_LENGTH),
            new TemplateSpec(AdminCommandDecoder.TEMPLATE_ID, AdminCommandDecoder.BLOCK_LENGTH),
            new TemplateSpec(ReplaceOrderCommandDecoder.TEMPLATE_ID, ReplaceOrderCommandDecoder.BLOCK_LENGTH),
            new TemplateSpec(OrderStateSnapshotDecoder.TEMPLATE_ID, OrderStateSnapshotDecoder.BLOCK_LENGTH),
            new TemplateSpec(PositionSnapshotDecoder.TEMPLATE_ID, PositionSnapshotDecoder.BLOCK_LENGTH),
            new TemplateSpec(RiskStateSnapshotDecoder.TEMPLATE_ID, RiskStateSnapshotDecoder.BLOCK_LENGTH)
        };
    }

    private record TemplateSpec(int templateId, int blockLength) {
    }
}
