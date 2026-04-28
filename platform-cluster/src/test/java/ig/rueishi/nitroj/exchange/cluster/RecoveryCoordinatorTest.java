package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestEncoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseEncoder;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.FillEventDecoder;
import ig.rueishi.nitroj.exchange.messages.FillEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.RecoveryCompleteEventEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.VenueStatus;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.test.CapturingPublication;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * T-011 coverage for the recovery coordinator state machine.
 *
 * <p>The tests use real SBE encoders/decoders and real OrderManager/Portfolio
 * instances wherever practical. A recording RiskEngine captures lock and kill
 * switch side effects so each recovery path can be asserted without starting
 * Aeron Cluster.</p>
 */
final class RecoveryCoordinatorTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int OTHER_VENUE = Ids.VENUE_COINBASE_SANDBOX;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final long CL_ORD_ID = 11_001L;
    private static final long QTY = 10_000_000L;
    private static final long PRICE = 65_000L * Ids.SCALE;

    private final CapturingPublication publication = new CapturingPublication();

    @Test
    void onDisconnect_venueStateAwaitingReconnect() {
        final Harness harness = harnessWithOrders(0);

        harness.recovery.onVenueStatus(VENUE, VenueStatus.DISCONNECTED);

        assertThat(harness.recovery.state(VENUE)).isEqualTo(RecoveryCoordinatorImpl.RecoveryState.AWAITING_RECONNECT);
        assertThat(harness.risk.lockedVenue).isEqualTo(VENUE);
        assertThat(harness.risk.locked).isTrue();
    }

    @Test
    void onReconnect_orderQuerySent_stateQueryingOrders() {
        final Harness harness = harnessWithOrders(3);
        harness.recovery.onVenueStatus(VENUE, VenueStatus.DISCONNECTED);

        harness.recovery.onVenueStatus(VENUE, VenueStatus.CONNECTED);

        assertThat(harness.recovery.state(VENUE)).isEqualTo(RecoveryCoordinatorImpl.RecoveryState.QUERYING_ORDERS);
        assertThat(publication.countByTemplateId(OrderStatusQueryCommandEncoder.TEMPLATE_ID)).isEqualTo(3);
    }

    @Test
    void orderQueryResponse_matches_tradingResumes() {
        final Harness harness = harnessWithOrders(1);
        startRecovery(harness);

        harness.recovery.reconcileOrder(exec(CL_ORD_ID, ExecType.NEW, Side.BUY, 0L, 0L, QTY, false));
        harness.recovery.onBalanceResponse(balance(0L));

        assertThat(harness.recovery.isInRecovery(VENUE)).isFalse();
        assertThat(publication.countByTemplateId(RecoveryCompleteEventEncoder.TEMPLATE_ID)).isEqualTo(1);
    }

    @Test
    void orderQueryResponse_missingFill_syntheticFillApplied() {
        final Harness harness = harnessWithOrders(1);
        startRecovery(harness);

        harness.recovery.reconcileOrder(exec(CL_ORD_ID, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, true));

        assertThat(harness.portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(QTY);
        assertThat(publication.countByTemplateId(FillEventEncoder.TEMPLATE_ID)).isEqualTo(1);
        assertThat(syntheticFill().isSynthetic()).isEqualTo(BooleanType.TRUE);
    }

    @Test
    void orderQueryResponse_orphanAtVenue_cancelSent() {
        final Harness harness = harnessWithOrders(0);
        startRecovery(harness);

        harness.recovery.reconcileOrder(exec(CL_ORD_ID, ExecType.NEW, Side.BUY, 0L, 0L, QTY, false));

        assertThat(publication.countByTemplateId(CancelOrderCommandEncoder.TEMPLATE_ID)).isEqualTo(1);
    }

    @Test
    void orderQueryResponse_venueCanceled_orderForceCanceled() {
        final Harness harness = harnessWithOrders(1);
        startRecovery(harness);

        harness.recovery.reconcileOrder(exec(CL_ORD_ID, ExecType.CANCELED, Side.BUY, 0L, 0L, QTY, false));

        assertThat(harness.orders.getOrder(CL_ORD_ID)).isNull();
    }

    @Test
    void balanceReconciliation_withinTolerance_tradingResumes() {
        final Harness harness = harnessWithOrders(0);
        harness.portfolio.onFill(exec(99L, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, true));
        startRecovery(harness);

        harness.recovery.reconcileBalance(VENUE, INSTRUMENT, QTY + RecoveryCoordinatorImpl.BALANCE_TOLERANCE_SCALED);

        assertThat(harness.recovery.isInRecovery(VENUE)).isFalse();
        assertThat(harness.risk.locked).isFalse();
    }

    @Test
    void balanceReconciliation_exceedsTolerance_killSwitchActivated() {
        final Harness harness = harnessWithOrders(0);
        harness.portfolio.onFill(exec(99L, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, true));
        startRecovery(harness);

        harness.recovery.reconcileBalance(VENUE, INSTRUMENT, QTY + RecoveryCoordinatorImpl.BALANCE_TOLERANCE_SCALED + 1);

        assertThat(harness.risk.killSwitchReason).isEqualTo("reconciliation_failed");
        assertThat(harness.recovery.isInRecovery(VENUE)).isTrue();
    }

    @Test
    void orderQueryTimeout_10s_killSwitchActivated() {
        final Harness harness = harnessWithOrders(1);
        startRecovery(harness);

        harness.recovery.onTimer(2000L + VENUE, 0L);

        assertThat(harness.risk.killSwitchReason).isEqualTo("recovery_timeout");
    }

    @Test
    void orderQueryTimeout_timerCorrelId_2000PlusVenueId() {
        final Harness harness = harnessWithOrders(1);

        startRecovery(harness);

        assertThat(harness.recovery.lastScheduledTimerId()).isEqualTo(2000L + VENUE);
    }

    @Test
    void balanceQueryTimeout_5s_killSwitchActivated() {
        final Harness harness = harnessWithOrders(1);
        startRecovery(harness);
        harness.recovery.reconcileOrder(exec(CL_ORD_ID, ExecType.NEW, Side.BUY, 0L, 0L, QTY, false));

        harness.recovery.onTimer(3000L + VENUE, 0L);

        assertThat(harness.risk.killSwitchReason).isEqualTo("recovery_timeout");
    }

    @Test
    void recoveryCompleteEvent_publishedOnSuccess() {
        final Harness harness = harnessWithOrders(0);
        startRecovery(harness);

        harness.recovery.onBalanceResponse(balance(0L));

        assertThat(publication.countByTemplateId(RecoveryCompleteEventEncoder.TEMPLATE_ID)).isEqualTo(1);
    }

    @Test
    void snapshot_recoveryStatePreserved() {
        final Harness harness = harnessWithOrders(0);
        harness.recovery.onVenueStatus(VENUE, VenueStatus.DISCONNECTED);
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        final int length = harness.recovery.encodeSnapshot(buffer, 0);
        final RecoveryCoordinatorImpl restored = harnessWithOrders(0).recovery;

        restored.loadSnapshot(buffer, 0);

        assertThat(length).isGreaterThan(0);
        assertThat(restored.state(VENUE)).isEqualTo(RecoveryCoordinatorImpl.RecoveryState.AWAITING_RECONNECT);
    }

    @Test
    void multipleVenues_independentRecovery() {
        final Harness harness = harnessWithOrders(0);

        harness.recovery.onVenueStatus(VENUE, VenueStatus.DISCONNECTED);

        assertThat(harness.recovery.isInRecovery(VENUE)).isTrue();
        assertThat(harness.recovery.isInRecovery(OTHER_VENUE)).isFalse();
    }

    @Test
    void disconnectDuringRecovery_resetAndRestart() {
        final Harness harness = harnessWithOrders(1);
        startRecovery(harness);

        harness.recovery.onVenueStatus(VENUE, VenueStatus.DISCONNECTED);

        assertThat(harness.recovery.state(VENUE)).isEqualTo(RecoveryCoordinatorImpl.RecoveryState.AWAITING_RECONNECT);
    }

    @Test
    void idleVenue_connectedEvent_noQuerySent() {
        final Harness harness = harnessWithOrders(1);

        harness.recovery.onVenueStatus(VENUE, VenueStatus.CONNECTED);

        assertThat(harness.recovery.state(VENUE)).isEqualTo(RecoveryCoordinatorImpl.RecoveryState.IDLE);
        assertThat(publication.countByTemplateId(OrderStatusQueryCommandEncoder.TEMPLATE_ID)).isZero();
    }

    private void startRecovery(final Harness harness) {
        harness.recovery.onVenueStatus(VENUE, VenueStatus.DISCONNECTED);
        harness.recovery.onVenueStatus(VENUE, VenueStatus.CONNECTED);
    }

    private Harness harnessWithOrders(final int count) {
        final OrderManagerImpl orders = new OrderManagerImpl();
        for (int i = 0; i < count; i++) {
            orders.createPendingOrder(CL_ORD_ID + i, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, Ids.STRATEGY_MARKET_MAKING);
        }
        final RecordingRisk risk = new RecordingRisk();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);
        final RecoveryCoordinatorImpl recovery = new RecoveryCoordinatorImpl(orders, portfolio, risk, publication::offer);
        return new Harness(orders, portfolio, risk, recovery);
    }

    private FillEventDecoder syntheticFill() {
        final FillEventDecoder decoder = new FillEventDecoder();
        decoder.wrapAndApplyHeader(publication.message(publication.size() - 2), 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static BalanceQueryResponseDecoder balance(final long balanceScaled) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new BalanceQueryResponseEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(VENUE)
            .instrumentId(INSTRUMENT)
            .balanceScaled(balanceScaled)
            .queryTimestampNanos(1L);
        final BalanceQueryResponseDecoder decoder = new BalanceQueryResponseDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    static ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final Side side,
        final long price,
        final long fillQty,
        final long leavesQty,
        final boolean isFinal
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final byte[] venueOrderId = "venue-order".getBytes(StandardCharsets.US_ASCII);
        final byte[] execId = ("exec-" + clOrdId + '-' + execType).getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE)
            .instrumentId(INSTRUMENT)
            .execType(execType)
            .side(side)
            .fillPriceScaled(price)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(fillQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execId, 0, execId.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private record Harness(
        OrderManagerImpl orders,
        PortfolioEngineImpl portfolio,
        RecordingRisk risk,
        RecoveryCoordinatorImpl recovery
    ) {
    }

    static final class RecordingRisk implements RiskEngine {
        int lockedVenue;
        boolean locked;
        String killSwitchReason;

        @Override
        public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) {
            return RiskDecision.APPROVED;
        }

        @Override
        public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) {
        }

        @Override
        public void updateDailyPnl(final long realizedPnlDeltaScaled) {
        }

        @Override
        public void setRecoveryLock(final int venueId, final boolean locked) {
            lockedVenue = venueId;
            this.locked = locked;
        }

        @Override
        public long getDailyPnlScaled() {
            return 0;
        }

        @Override
        public void activateKillSwitch(final String reason) {
            killSwitchReason = reason;
        }

        @Override
        public void deactivateKillSwitch() {
        }

        @Override
        public boolean isKillSwitchActive() {
            return killSwitchReason != null;
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
