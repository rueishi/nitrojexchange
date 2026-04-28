package ig.rueishi.nitroj.exchange.gateway.marketdata;

import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for shared FIX L3 normalization hooks.
 *
 * <p>Responsibility: verifies default order identity, side mapping,
 * update-action mapping, and venue enrichment behavior exposed by the L3 base
 * class. Role in system: protects the reusable TASK-107 L3 extension point
 * before concrete Coinbase FIX L3 parsing is added. Relationships: exercises a
 * minimal concrete subclass and the real {@link L3MarketDataContext}. Lifecycle:
 * runs as gateway unit coverage. Design intent: ensure venues only need to
 * override small hooks for nonstandard L3 semantics.
 */
final class AbstractFixL3MarketDataNormalizerTest {

    @Test
    void orderIdentity_defaultsToMdEntryIdTag278() {
        final TestNormalizer normalizer = new TestNormalizer();
        final L3MarketDataContext context = new L3MarketDataContext();
        context.mdEntryId = "entry-278";

        assertThat(normalizer.orderIdentity(context)).isEqualTo("entry-278");
    }

    @Test
    void mapSide_standardBidAskValues() {
        final TestNormalizer normalizer = new TestNormalizer();

        assertThat(normalizer.mapSide((byte)'0')).isEqualTo(Side.BUY);
        assertThat(normalizer.mapSide((byte)'1')).isEqualTo(Side.SELL);
        assertThat(normalizer.mapSide((byte)'2')).isEqualTo(Side.NULL_VAL);
    }

    @Test
    void mapUpdateAction_standardL3Values() {
        final TestNormalizer normalizer = new TestNormalizer();

        assertThat(normalizer.mapUpdateAction((byte)'0')).isEqualTo(UpdateAction.NEW);
        assertThat(normalizer.mapUpdateAction((byte)'1')).isEqualTo(UpdateAction.CHANGE);
        assertThat(normalizer.mapUpdateAction((byte)'2')).isEqualTo(UpdateAction.DELETE);
    }

    @Test
    void l3DefaultPolicies_areAbsoluteAndNeedPreviousDeletes() {
        final TestNormalizer normalizer = new TestNormalizer();

        assertThat(normalizer.sizeIsAbsolute()).isTrue();
        assertThat(normalizer.deleteRequiresPreviousState()).isTrue();
    }

    @Test
    void enrich_defaultNoOp_contextUnchanged() {
        final TestNormalizer normalizer = new TestNormalizer();
        final L3MarketDataContext context = new L3MarketDataContext();
        context.mdEntryId = "order-1";

        normalizer.enrich(context);

        assertThat(context.mdEntryId).isEqualTo("order-1");
    }

    @Test
    void onFixMessage_validSnapshotAddChangeDelete_publishOrderContexts() {
        final TestNormalizer normalizer = new TestNormalizer();

        normalizer.onFixMessage(77L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "279=0", "269=0", "278=o1", "270=100", "271=2"), 0, currentLength, 10L);
        normalizer.onFixMessage(77L, fix("35=X", "34=2", "55=BTC-USD", "268=2",
            "279=1", "269=0", "278=o1", "270=101", "271=3",
            "279=2", "269=1", "278=o2", "270=102", "271=0"), 0, currentLength, 11L);

        assertThat(normalizer.published).hasSize(3);
        assertThat(normalizer.published.get(0).mdEntryId).isEqualTo("o1");
        assertThat(normalizer.published.get(0).side).isEqualTo(Side.BUY);
        assertThat(normalizer.published.get(0).updateAction).isEqualTo(UpdateAction.NEW);
        assertThat(normalizer.published.get(1).updateAction).isEqualTo(UpdateAction.CHANGE);
        assertThat(normalizer.published.get(2).side).isEqualTo(Side.SELL);
        assertThat(normalizer.published.get(2).updateAction).isEqualTo(UpdateAction.DELETE);
        assertThat(normalizer.byteLookupCount).isEqualTo(2);
    }

    @Test
    void onFixMessage_missingMdEntryIdMalformedUnknownAndUnsupported_dropSafely() {
        final TestNormalizer normalizer = new TestNormalizer();

        normalizer.onFixMessage(77L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "269=0", "270=100", "271=2"), 0, currentLength, 10L);
        normalizer.onFixMessage(77L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "269=0", "278=o1", "270=bad", "271=2"), 0, currentLength, 10L);
        normalizer.onFixMessage(77L, fix("35=W", "34=1", "55=ETH-USD", "268=1",
            "269=0", "278=o1", "270=100", "271=2"), 0, currentLength, 10L);
        normalizer.onFixMessage(77L, fix("35=W", "34=1", "55=BTC-USD", "268=1",
            "269=2", "278=o1", "270=100", "271=2"), 0, currentLength, 10L);

        assertThat(normalizer.published).isEmpty();
        assertThat(normalizer.malformedCount).isEqualTo(2);
        assertThat(normalizer.unknownSymbols).containsExactly("ETH-USD");
    }

    private static int currentLength;

    private static UnsafeBuffer fix(final String... tags) {
        final String message = String.join("\001", tags) + "\001";
        currentLength = message.length();
        return new UnsafeBuffer(message.getBytes(StandardCharsets.US_ASCII));
    }

    private static final class TestNormalizer extends AbstractFixL3MarketDataNormalizer {
        private final List<L3MarketDataContext> published = new ArrayList<>();
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
        protected boolean publish(final L3MarketDataContext context) {
            final L3MarketDataContext copy = new L3MarketDataContext();
            copy.ingressNanos = context.ingressNanos;
            copy.venueId = context.venueId;
            copy.instrumentId = context.instrumentId;
            copy.fixSeqNum = context.fixSeqNum;
            copy.symbol = context.symbol;
            copy.side = context.side;
            copy.updateAction = context.updateAction;
            copy.priceScaled = context.priceScaled;
            copy.sizeScaled = context.sizeScaled;
            copy.exchangeTimestampNanos = context.exchangeTimestampNanos;
            copy.mdEntryId = context.mdEntryId;
            copy.mdEntryRefId = context.mdEntryRefId;
            published.add(copy);
            return true;
        }

        @Override
        protected void onUnknownSymbol(final String symbol) {
            unknownSymbols.add(symbol);
        }

        @Override
        protected void onMalformedMessage(final RuntimeException ex) {
            malformedCount++;
        }
    }
}
