package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V12 financial-correctness preflight coverage.
 *
 * <p>Responsibility: anchors automatable production-readiness controls in
 * cluster behavior. Role in system: manual runbooks still own venue onboarding,
 * monitoring, failover, deployment, and rollback evidence, while this test proves
 * the local deterministic controls for kill switch, rejected orders, stale data,
 * and self-trade prevention cannot be removed silently.</p>
 */
final class ProductionFinancialPreflightTest {
    @Test
    void killSwitchAndRejectedOrders_blockPortfolioMutation() {
        final RiskEngineImpl risk = riskEngine();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);
        risk.setVenueConnected(Ids.VENUE_COINBASE, true);
        risk.activateKillSwitch("preflight");

        final RiskDecision decision = risk.preTradeCheck(
            Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD,
            Side.BUY.value(),
            65_000L,
            1L,
            Ids.STRATEGY_MARKET_MAKING);

        assertThat(decision).isSameAs(RiskDecision.REJECT_KILL_SWITCH);
        assertThat(portfolio.getNetQtyScaled(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD)).isZero();
    }

    @Test
    void staleDataAndSelfTradePrevention_areVisibleBeforeStrategyExecution() {
        final InternalMarketView view = new InternalMarketView();
        view.apply(market(EntryType.BID, 100L, 10L), 100L);
        view.ownOrderOverlay().upsert(
            1L,
            "OWN-1",
            Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD,
            EntryType.BID,
            100L,
            4L,
            true);

        assertThat(view.isStale(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 10L, 111L)).isTrue();
        assertThat(view.externalLiquidityView().externalSizeAt(
            Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD,
            EntryType.BID,
            100L)).isEqualTo(6L);
        assertThat(view.ownOrderOverlay().wouldSelfCross(
            Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK,
            99L)).isTrue();
    }

    private static RiskEngineImpl riskEngine() {
        return new RiskEngineImpl(new RiskConfig(
            100,
            Ids.SCALE,
            Map.of(Ids.INSTRUMENT_BTC_USD, new RiskConfig.InstrumentRisk(
                Ids.INSTRUMENT_BTC_USD,
                10L,
                100L,
                100L,
                1_000_000L,
                80))));
    }

    private static MarketDataEventDecoder market(final EntryType side, final long price, final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(side)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }
}
