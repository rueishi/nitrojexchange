package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import org.junit.jupiter.api.Test;

/**
 * ET-001 smoke coverage for market-making end-to-end command effects.
 *
 * <p>This class exercises the strategy through the same harness as the unit
 * tests but asserts business-level outcomes: two-sided quoting, requoting after
 * market movement, inventory skew after position changes, kill-switch cancels,
 * stale/wide-spread suppression, and zero-size side suppression at position
 * limits.</p>
 */
final class MarketMakingE2ETest {
    @Test
    void mmStrategy_onMarketData_submitsTwoSidedQuotes() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());

        MarketMakingStrategyTest.quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order().orders).hasSize(2);
    }

    @Test
    void mmStrategy_marketMoves_requotesWithinThreshold() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());
        MarketMakingStrategyTest.quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster().offerKinds.clear();
        harness.cluster().time = 2L;

        MarketMakingStrategyTest.quote(harness, 100_900L * Ids.SCALE, 101_100L * Ids.SCALE);

        assertThat(harness.cluster().offerKinds).containsExactly("cancel", "cancel", "new", "new");
    }

    @Test
    void mmStrategy_fillReceived_inventorySkewsNextQuote() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());
        harness.strategy().onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 5L * Ids.SCALE, 0L);

        MarketMakingStrategyTest.quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.strategy().lastBidPrice()).isLessThan(99_950L * Ids.SCALE);
    }

    @Test
    void mmStrategy_killSwitchActivated_quotesCancel() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());
        MarketMakingStrategyTest.quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster().offerKinds.clear();

        harness.strategy().onKillSwitch();

        assertThat(harness.cluster().offerKinds).containsExactly("cancel", "cancel");
    }

    @Test
    void mmStrategy_marketDataStale_quotesCancel() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());
        final ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder bid = MarketMakingStrategyTest.decoder(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.BID, 99_900L * Ids.SCALE);
        final ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder ask = MarketMakingStrategyTest.decoder(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.ASK, 100_100L * Ids.SCALE);
        harness.marketView().apply(bid, 1L);
        harness.marketView().apply(ask, 1L);
        harness.cluster().time = 20_000_001L;

        harness.strategy().onMarketData(ask);

        assertThat(harness.order().orders).isEmpty();
    }

    @Test
    void mmStrategy_wideSpread_suppressesQuotes() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());

        MarketMakingStrategyTest.quote(harness, 95_000L * Ids.SCALE, 105_000L * Ids.SCALE);

        assertThat(harness.order().orders).isEmpty();
    }

    @Test
    void mmStrategy_positionAtMaxLong_askSideOnly() {
        final MarketMakingStrategyTest.Harness harness = MarketMakingStrategyTest.harness(MarketMakingStrategyTest.config());
        harness.strategy().onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 10L * Ids.SCALE, 0L);

        MarketMakingStrategyTest.quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order().orders).extracting(order -> order.side).containsOnly(ig.rueishi.nitroj.exchange.messages.Side.SELL);
    }
}
