package ig.rueishi.nitroj.exchange.gateway.marketdata;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for shared FIX L2 normalization helpers.
 *
 * <p>Responsibility: verifies the reusable mapping and parsing behavior that
 * future venue L2 normalizers inherit. Role in system: protects TASK-106's
 * extraction seam while existing {@code MarketDataHandler} tests continue to
 * cover end-to-end L2 publication. Relationships: exercises a tiny concrete
 * subclass because the production base is abstract. Lifecycle: runs in the
 * gateway unit-test suite. Design intent: prove standard FIX L2 semantics are
 * centralized before later tasks wire the base into runtime composition.
 */
final class AbstractFixL2MarketDataNormalizerTest {

    @Test
    void mapEntryType_standardFixValues() {
        final TestNormalizer normalizer = new TestNormalizer();

        assertThat(normalizer.mapEntryType((byte)'0')).isEqualTo(EntryType.BID);
        assertThat(normalizer.mapEntryType((byte)'1')).isEqualTo(EntryType.ASK);
        assertThat(normalizer.mapEntryType((byte)'2')).isEqualTo(EntryType.TRADE);
        assertThat(normalizer.mapEntryType((byte)'9')).isEqualTo(EntryType.NULL_VAL);
    }

    @Test
    void mapUpdateAction_standardFixValues() {
        final TestNormalizer normalizer = new TestNormalizer();

        assertThat(normalizer.mapUpdateAction((byte)'0')).isEqualTo(UpdateAction.NEW);
        assertThat(normalizer.mapUpdateAction((byte)'1')).isEqualTo(UpdateAction.CHANGE);
        assertThat(normalizer.mapUpdateAction((byte)'2')).isEqualTo(UpdateAction.DELETE);
        assertThat(normalizer.mapUpdateAction((byte)'9')).isEqualTo(UpdateAction.NEW);
    }

    @Test
    void parseScaled_decimalValue_scaledToEightPlaces() {
        final UnsafeBuffer buffer = ascii("65000.123456789");

        assertThat(AbstractFixL2MarketDataNormalizer.parseScaled(buffer, 0, "65000.123456789".length()))
            .isEqualTo(6_500_012_345_678L);
    }

    @Test
    void enrich_defaultNoOp_contextUnchanged() {
        final TestNormalizer normalizer = new TestNormalizer();
        final L2MarketDataContext context = new L2MarketDataContext();
        context.symbol = "BTC-USD";

        normalizer.enrich(context);

        assertThat(context.symbol).isEqualTo("BTC-USD");
    }

    @Test
    void onFixMessage_validSnapshotAndIncremental_publishContexts() {
        final TestNormalizer normalizer = new TestNormalizer();

        normalizer.onFixMessage(99L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "269=0", "270=100.00", "271=2", "1023=3"), 0, currentLength, 7L);
        normalizer.onFixMessage(99L, fix("35=X", "34=2", "55=BTC-USD", "268=1",
            "279=2", "269=1", "270=101", "271=0"), 0, currentLength, 8L);

        assertThat(normalizer.published).hasSize(2);
        assertThat(normalizer.published.get(0).entryType).isEqualTo(EntryType.BID);
        assertThat(normalizer.published.get(0).updateAction).isEqualTo(UpdateAction.NEW);
        assertThat(normalizer.published.get(0).priceScaled).isEqualTo(10_000_000_000L);
        assertThat(normalizer.published.get(0).sizeScaled).isEqualTo(200_000_000L);
        assertThat(normalizer.published.get(0).priceLevel).isEqualTo(3);
        assertThat(normalizer.published.get(1).entryType).isEqualTo(EntryType.ASK);
        assertThat(normalizer.published.get(1).updateAction).isEqualTo(UpdateAction.DELETE);
        assertThat(normalizer.byteLookupCount).isEqualTo(2);
    }

    @Test
    void onFixMessage_unknownSymbolAndMalformedNumeric_dropSafely() {
        final TestNormalizer normalizer = new TestNormalizer();

        normalizer.onFixMessage(99L, fix("35=W", "34=1", "55=ETH-USD", "268=1",
            "269=0", "270=100", "271=1"), 0, currentLength, 7L);
        normalizer.onFixMessage(99L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "269=0", "270=bad", "271=1"), 0, currentLength, 7L);

        assertThat(normalizer.published).isEmpty();
        assertThat(normalizer.unknownSymbols).containsExactly("ETH-USD");
        assertThat(normalizer.malformedCount).isEqualTo(1);
        assertThat(normalizer.unknownSymbolDropCount()).isEqualTo(1);
        assertThat(normalizer.malformedMessageDropCount()).isEqualTo(1);
    }

    @Test
    void onFixMessage_missingSymbolAndUnsupportedEntryType_drop() {
        final TestNormalizer normalizer = new TestNormalizer();

        normalizer.onFixMessage(99L, fix("35=W", "34=1", "268=1",
            "269=0", "270=100", "271=1"), 0, currentLength, 7L);
        normalizer.onFixMessage(99L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "269=9", "270=100", "271=1"), 0, currentLength, 7L);

        assertThat(normalizer.published).isEmpty();
    }

    private static UnsafeBuffer ascii(final String value) {
        return new UnsafeBuffer(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static int currentLength;

    private static UnsafeBuffer fix(final String... tags) {
        final String message = String.join("\001", tags) + "\001";
        currentLength = message.length();
        return ascii(message);
    }

    private static final class TestNormalizer extends AbstractFixL2MarketDataNormalizer {
        private final List<L2MarketDataContext> published = new ArrayList<>();
        private final List<String> unknownSymbols = new ArrayList<>();
        private int malformedCount;
        private int byteLookupCount;

        @Override
        protected int venueId(final long sessionId) {
            return 1;
        }

        @Override
        protected int instrumentId(final String symbol) {
            return "BTC-USD".equals(symbol) ? 11 : 0;
        }

        @Override
        protected int instrumentId(final DirectBuffer buffer, final int valueStart, final int valueEnd) {
            byteLookupCount++;
            final int length = valueEnd - valueStart;
            return length == 7
                && buffer.getByte(valueStart) == 'B'
                && buffer.getByte(valueStart + 1) == 'T'
                && buffer.getByte(valueStart + 2) == 'C' ? 11 : 0;
        }

        @Override
        protected boolean publish(final L2MarketDataContext context) {
            final L2MarketDataContext copy = new L2MarketDataContext();
            copy.ingressNanos = context.ingressNanos;
            copy.venueId = context.venueId;
            copy.instrumentId = context.instrumentId;
            copy.fixSeqNum = context.fixSeqNum;
            copy.symbol = context.symbol;
            copy.setSymbolRange(context.symbolBuffer, context.symbolOffset, context.symbolLength);
            copy.entryType = context.entryType;
            copy.updateAction = context.updateAction;
            copy.priceScaled = context.priceScaled;
            copy.sizeScaled = context.sizeScaled;
            copy.priceLevel = context.priceLevel;
            copy.exchangeTimestampNanos = context.exchangeTimestampNanos;
            published.add(copy);
            return true;
        }

        @Override
        protected void onUnknownSymbol(final String symbol) {
            super.onUnknownSymbol(symbol);
            unknownSymbols.add(symbol);
        }

        @Override
        protected void onUnknownSymbol(final DirectBuffer buffer, final int valueStart, final int valueEnd) {
            super.onUnknownSymbol(buffer, valueStart, valueEnd);
            unknownSymbols.add(buffer.getStringWithoutLengthAscii(valueStart, valueEnd - valueStart));
        }

        @Override
        protected void onMalformedMessage(final RuntimeException ex) {
            super.onMalformedMessage(ex);
            malformedCount++;
        }
    }
}
