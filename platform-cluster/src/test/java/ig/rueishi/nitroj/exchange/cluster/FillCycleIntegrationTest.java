package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * IT-004 integration coverage for the fill chain owned by TASK-021.
 *
 * <p>The test wires the real OrderManager and PortfolioEngine together with a
 * recording RiskEngine. It verifies the practical contract used later by
 * MessageRouter: OrderManager decides whether an execution is a fill; if so,
 * PortfolioEngine updates inventory and RiskEngine receives the post-fill
 * position snapshot.</p>
 */
final class FillCycleIntegrationTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final long CL_ORD_ID = 7_001L;
    private static final long QTY = 10_000_000L;
    private static final long PRICE = 65_000L * Ids.SCALE;

    @Test
    void fill_updatesPortfolio_riskSnapshotUpdated() {
        final PortfolioEngineTest.RecordingRiskEngine risk = new PortfolioEngineTest.RecordingRiskEngine();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);
        final OrderManagerImpl orders = orderManager();
        orders.createPendingOrder(CL_ORD_ID, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, Ids.STRATEGY_MARKET_MAKING);
        orders.onExecution(exec(CL_ORD_ID, ExecType.NEW, Side.BUY, 0L, 0L, QTY, false, "ack-1"));

        final ExecutionEventDecoder fill = exec(CL_ORD_ID, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, true, "fill-1");
        if (orders.onExecution(fill)) {
            portfolio.onFill(fill);
            risk.onFill(fill);
        }

        assertThat(portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(QTY);
        assertThat(risk.notificationCount).isEqualTo(1);
        assertThat(risk.lastNetQtyScaled).isEqualTo(QTY);
    }

    @Test
    void multipleFills_cumulativePosition_correct() {
        final PortfolioEngineTest.RecordingRiskEngine risk = new PortfolioEngineTest.RecordingRiskEngine();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);

        portfolio.onFill(exec(CL_ORD_ID, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, false, "fill-1"));
        portfolio.onFill(exec(CL_ORD_ID, ExecType.FILL, Side.SELL, PRICE + 500L * Ids.SCALE, QTY / 2, QTY / 2, false, "fill-2"));

        assertThat(portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(QTY / 2);
        assertThat(portfolio.realizedPnl(VENUE, INSTRUMENT)).isEqualTo(2_500_000_000L);
    }

    @Test
    void fillOnTerminalOrder_portfolioUnchanged() {
        final PortfolioEngineTest.RecordingRiskEngine risk = new PortfolioEngineTest.RecordingRiskEngine();
        final PortfolioEngineImpl portfolio = new PortfolioEngineImpl(risk);
        final OrderManagerImpl orders = orderManager();
        orders.createPendingOrder(CL_ORD_ID, VENUE, INSTRUMENT, Side.BUY.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, QTY, Ids.STRATEGY_MARKET_MAKING);
        orders.onExecution(exec(CL_ORD_ID, ExecType.NEW, Side.BUY, 0L, 0L, QTY, false, "ack-1"));
        final ExecutionEventDecoder fill = exec(CL_ORD_ID, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, true, "fill-1");
        if (orders.onExecution(fill)) {
            portfolio.onFill(fill);
        }

        final ExecutionEventDecoder lateFill = exec(CL_ORD_ID, ExecType.FILL, Side.BUY, PRICE, QTY, 0L, true, "fill-2");
        if (orders.onExecution(lateFill)) {
            portfolio.onFill(lateFill);
        }

        assertThat(portfolio.getNetQtyScaled(VENUE, INSTRUMENT)).isEqualTo(QTY);
    }

    private static OrderManagerImpl orderManager() {
        return new OrderManagerImpl();
    }

    private static ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final Side side,
        final long price,
        final long fillQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final ExecutionEventEncoder encoder = new ExecutionEventEncoder();
        final byte[] venueOrderId = "venue-order".getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        encoder
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
            .putExecId(execIdBytes, 0, execIdBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }
}
