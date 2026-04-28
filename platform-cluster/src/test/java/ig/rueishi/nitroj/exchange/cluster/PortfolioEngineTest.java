package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * T-008 unit coverage for portfolio position and PnL math.
 *
 * <p>The vectors mirror the implementation plan's explicit BTC examples and
 * exercise both long and short inventory paths. A small in-memory RiskEngine
 * double records post-fill position snapshots so tests can verify the required
 * PortfolioEngine to RiskEngine handoff without starting a cluster.</p>
 */
final class PortfolioEngineTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int SECOND_VENUE = Ids.VENUE_COINBASE_SANDBOX;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final int SECOND_INSTRUMENT = Ids.INSTRUMENT_ETH_USD;
    private static final long BTC_005 = 5_000_000L;
    private static final long BTC_01 = 10_000_000L;
    private static final long BTC_02 = 20_000_000L;
    private static final long PX_64_500 = price(64_500);
    private static final long PX_65_000 = price(65_000);
    private static final long PX_65_100 = price(65_100);
    private static final long PX_65_200 = price(65_200);
    private static final long PX_65_500 = price(65_500);

    @Test
    void buy_longPosition_avgPriceCorrect() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(BTC_01);
        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(PX_65_000);
    }

    @Test
    void buy_twoFills_vwapAvgPrice() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_100));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(BTC_02);
        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(price(65_050));
    }

    @Test
    void sell_longClose_realizedPnlCorrect() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.SELL, BTC_01, PX_65_500));

        assertThat(harness.portfolio.realizedPnl(VENUE, INSTRUMENT)).isEqualTo(5_000_000_000L);
        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isZero();
    }

    @Test
    void sell_shortOpen_avgPriceTracked() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_000));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(-BTC_005);
        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(PX_65_000);
    }

    @Test
    void buy_shortCover_realizedPnlCorrect() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_000));
        harness.portfolio.onFill(fill(Side.BUY, BTC_005, PX_64_500));

        assertThat(harness.portfolio.realizedPnl(VENUE, INSTRUMENT)).isEqualTo(2_500_000_000L);
        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isZero();
    }

    @Test
    void buy_closesShortAndGoesLong_residualQtyHandled() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_000));
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_64_500));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(BTC_005);
        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(PX_64_500);
        assertThat(harness.portfolio.realizedPnl(VENUE, INSTRUMENT)).isEqualTo(2_500_000_000L);
    }

    @Test
    void sell_closesLongAndGoesShort_residualQtyHandled() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.SELL, BTC_02, PX_65_200));

        assertThat(harness.portfolio.realizedPnl(VENUE, INSTRUMENT)).isEqualTo(2_000_000_000L);
        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(-BTC_01);
        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(PX_65_200);
    }

    @Test
    void buy_exactlyClosesShort_netQtyZero() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_000));
        harness.portfolio.onFill(fill(Side.BUY, BTC_005, PX_64_500));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isZero();
    }

    @Test
    void sell_exactlyClosesLong_netQtyZero() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.SELL, BTC_01, PX_65_500));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isZero();
    }

    @Test
    void netQtyZero_afterFullClose_avgPriceZero() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.SELL, BTC_01, PX_65_500));

        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isZero();
    }

    @Test
    void unrealizedPnl_longPosition_markAboveEntry_positive() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));

        assertThat(harness.portfolio.unrealizedPnl(VENUE, INSTRUMENT, PX_65_500)).isEqualTo(5_000_000_000L);
    }

    @Test
    void unrealizedPnl_longPosition_markBelowEntry_negative() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));

        assertThat(harness.portfolio.unrealizedPnl(VENUE, INSTRUMENT, PX_64_500)).isEqualTo(-5_000_000_000L);
    }

    @Test
    void unrealizedPnl_shortPosition_markBelowEntry_positive() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_000));

        assertThat(harness.portfolio.unrealizedPnl(VENUE, INSTRUMENT, PX_64_500)).isEqualTo(2_500_000_000L);
    }

    @Test
    void unrealizedPnl_shortPosition_markAboveEntry_negative() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_000));

        assertThat(harness.portfolio.unrealizedPnl(VENUE, INSTRUMENT, PX_65_500)).isEqualTo(-2_500_000_000L);
    }

    @Test
    void unrealizedPnl_zeroPosition_alwaysZero() {
        final Harness harness = harness();

        assertThat(harness.portfolio.unrealizedPnl(VENUE, INSTRUMENT, PX_65_500)).isZero();
    }

    @Test
    void multipleFills_sameSide_vwapAccumulates() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.BUY, BTC_005, PX_65_500));

        assertThat(harness.portfolio.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(priceFraction(65_166, 66_666_667));
    }

    @Test
    void riskEngine_notifiedAfterEachFill() {
        final Harness harness = harness();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_500));

        assertThat(harness.risk.notificationCount).isEqualTo(2);
        assertThat(harness.risk.lastVenueId).isEqualTo(VENUE);
        assertThat(harness.risk.lastInstrumentId).isEqualTo(INSTRUMENT);
        assertThat(harness.risk.lastNetQtyScaled).isEqualTo(BTC_005);
    }

    @Test
    void overflowProtection_largePriceAndQty_noSilentWrap() {
        final Harness harness = harness();
        final long oneBtc = Ids.SCALE;

        harness.portfolio.onFill(fill(Side.BUY, oneBtc, PX_65_000));

        assertThat(harness.portfolio.unrealizedPnl(VENUE, INSTRUMENT, price(66_000))).isEqualTo(100_000_000_000L);
    }

    @Test
    void snapshot_positionPreserved() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));

        final PortfolioEngineImpl restored = new PortfolioEngineImpl(new RecordingRiskEngine());
        restored.loadSnapshotFragments(harness.portfolio.snapshotFragments());

        assertThat(restored.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(BTC_01);
        assertThat(restored.getAvgEntryPriceScaled(VENUE, INSTRUMENT)).isEqualTo(PX_65_000);
    }

    @Test
    void snapshot_realizedPnlPreserved() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(Side.SELL, BTC_005, PX_65_500));

        final PortfolioEngineImpl restored = new PortfolioEngineImpl(new RecordingRiskEngine());
        restored.loadSnapshotFragments(harness.portfolio.snapshotFragments());

        assertThat(restored.realizedPnl(VENUE, INSTRUMENT)).isEqualTo(2_500_000_000L);
    }

    @Test
    void snapshot_multiplePositions_allRestored() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(VENUE, INSTRUMENT, Side.BUY, BTC_01, PX_65_000));
        harness.portfolio.onFill(fill(SECOND_VENUE, SECOND_INSTRUMENT, Side.SELL, BTC_005, PX_65_500));

        final PortfolioEngineImpl restored = new PortfolioEngineImpl(new RecordingRiskEngine());
        restored.loadSnapshotFragments(harness.portfolio.snapshotFragments());

        assertThat(restored.positionCount()).isEqualTo(2);
        assertThat(restored.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(BTC_01);
        assertThat(restored.getNetQtyScaled(SECOND_VENUE, SECOND_INSTRUMENT)).isEqualTo(-BTC_005);
    }

    @Test
    void initPosition_preAllocatesForAllPairs_noFirstFillAllocation() {
        final Harness harness = harness();
        harness.portfolio.initPosition(VENUE, INSTRUMENT);
        final int before = harness.portfolio.positionCount();

        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));

        assertThat(harness.portfolio.positionCount()).isEqualTo(before);
    }

    @Test
    void refreshUnrealizedPnl_updatesTotalUnrealizedPnl() {
        final Harness harness = harness();
        harness.portfolio.onFill(fill(Side.BUY, BTC_01, PX_65_000));

        harness.portfolio.refreshUnrealizedPnl(VENUE, INSTRUMENT, PX_65_500);

        assertThat(harness.portfolio.getTotalUnrealizedPnlScaled()).isEqualTo(5_000_000_000L);
    }

    private static Harness harness() {
        final RecordingRiskEngine risk = new RecordingRiskEngine();
        return new Harness(new PortfolioEngineImpl(risk), risk);
    }

    private static ExecutionEventDecoder fill(final Side side, final long qty, final long price) {
        return fill(VENUE, INSTRUMENT, side, qty, price);
    }

    private static ExecutionEventDecoder fill(
        final int venueId,
        final int instrumentId,
        final Side side,
        final long qty,
        final long price
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final ExecutionEventEncoder encoder = new ExecutionEventEncoder();
        final byte[] venueOrderId = "venue-order".getBytes(StandardCharsets.US_ASCII);
        final byte[] execId = "exec-id".getBytes(StandardCharsets.US_ASCII);
        encoder
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1L)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .execType(ExecType.FILL)
            .side(side)
            .fillPriceScaled(price)
            .fillQtyScaled(qty)
            .cumQtyScaled(qty)
            .leavesQtyScaled(0L)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execId, 0, execId.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static long price(final long dollars) {
        return dollars * Ids.SCALE;
    }

    private static long priceFraction(final long dollars, final long fractionalScaled) {
        return dollars * Ids.SCALE + fractionalScaled;
    }

    private record Harness(PortfolioEngineImpl portfolio, RecordingRiskEngine risk) {
    }

    static final class RecordingRiskEngine implements RiskEngine {
        int notificationCount;
        int lastVenueId;
        int lastInstrumentId;
        long lastNetQtyScaled;

        @Override
        public RiskDecision preTradeCheck(
            final int venueId,
            final int instrumentId,
            final byte side,
            final long priceScaled,
            final long qtyScaled,
            final int strategyId
        ) {
            return RiskDecision.APPROVED;
        }

        @Override
        public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) {
            notificationCount++;
            lastVenueId = venueId;
            lastInstrumentId = instrumentId;
            lastNetQtyScaled = netQtyScaled;
        }

        @Override
        public void updateDailyPnl(final long realizedPnlDeltaScaled) {
        }

        @Override
        public void setRecoveryLock(final int venueId, final boolean locked) {
        }

        @Override
        public long getDailyPnlScaled() {
            return 0;
        }

        @Override
        public void activateKillSwitch(final String reason) {
        }

        @Override
        public void deactivateKillSwitch() {
        }

        @Override
        public boolean isKillSwitchActive() {
            return false;
        }

        @Override
        public void writeSnapshot(final io.aeron.ExclusivePublication snapshotPublication) {
        }

        @Override
        public void loadSnapshot(final io.aeron.Image snapshotImage) {
        }

        @Override
        public void resetDailyCounters() {
        }

        @Override
        public void setCluster(final io.aeron.cluster.service.Cluster cluster) {
        }

        @Override
        public void onFill(final ExecutionEventDecoder decoder) {
        }

        @Override
        public void resetAll() {
        }
    }
}
