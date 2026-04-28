package ig.rueishi.nitroj.exchange.tooling;

import ig.rueishi.nitroj.exchange.cluster.TradingClusteredService;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.WarmupConfig;
import ig.rueishi.nitroj.exchange.gateway.WarmupHarness;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.artio.library.FixLibrary;

/**
 * Production JVM warmup harness for gateway startup.
 *
 * <p>The harness runs synthetic SBE market-data and execution messages through
 * TradingClusteredService using a {@link WarmupClusterShim}. This warms the same
 * dispatch, book, strategy, order, portfolio, and risk paths that live traffic
 * uses, while Artio's library is kept alive with small non-blocking polls. After
 * the loop, all business state is reset before the shim is removed.</p>
 */
public final class WarmupHarnessImpl implements WarmupHarness {
    private static final Logger LOGGER = System.getLogger(WarmupHarnessImpl.class.getName());
    private static final int BUFFER_BYTES = 512;

    private final TradingClusteredService service;
    private final WarmupConfig config;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketDataEventEncoder marketDataEncoder = new MarketDataEventEncoder();
    private final ExecutionEventEncoder executionEncoder = new ExecutionEventEncoder();

    public WarmupHarnessImpl(final TradingClusteredService service, final WarmupConfig config) {
        this.service = Objects.requireNonNull(service, "service");
        this.config = Objects.requireNonNull(config, "config");
    }

    public WarmupConfig config() {
        return config;
    }

    /**
     * Runs synthetic warmup traffic before live FIX connectivity begins.
     *
     * @param fixLibrary initialized Artio library; may be {@code null} in tests
     */
    @Override
    public void runGatewayWarmup(final FixLibrary fixLibrary) {
        final WarmupClusterShim shim = new WarmupClusterShim();
        final UnsafeBuffer warmupBuffer = new UnsafeBuffer(new byte[BUFFER_BYTES]);
        service.installClusterShim(shim);
        final long start = System.nanoTime();
        try {
            for (int i = 0; i < config.iterations(); i++) {
                encodeSyntheticMarketData(warmupBuffer, i);
                service.onSessionMessage(null, shim.time(), warmupBuffer, 0, BUFFER_BYTES, null);
                if (i % 10 == 0) {
                    encodeSyntheticExecution(warmupBuffer, i);
                    service.onSessionMessage(null, shim.time(), warmupBuffer, 0, BUFFER_BYTES, null);
                }
                if (fixLibrary != null) {
                    fixLibrary.poll(1);
                }
            }
            final long elapsed = System.nanoTime() - start;
            if (config.requireC2Verified()) {
                verifyC2Compilation(elapsed, config);
            }
            LOGGER.log(Level.INFO, "Warmup complete: {0} iterations in {1}ms", config.iterations(), elapsed / 1_000_000L);
        } finally {
            service.resetWarmupState();
            service.removeClusterShim();
        }
    }

    private void encodeSyntheticMarketData(final UnsafeBuffer buffer, final int iteration) {
        marketDataEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(iteration % 2 == 0 ? EntryType.BID : EntryType.ASK)
            .updateAction(UpdateAction.NEW)
            .priceScaled((65_000L + iteration % 10) * Ids.SCALE)
            .sizeScaled(Ids.SCALE)
            .priceLevel(0)
            .ingressTimestampNanos(iteration)
            .exchangeTimestampNanos(iteration)
            .fixSeqNum(iteration);
    }

    private void encodeSyntheticExecution(final UnsafeBuffer buffer, final int iteration) {
        executionEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
            .clOrdId(1_000_000L + iteration)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(ExecType.NEW)
            .side(Side.BUY)
            .fillPriceScaled(65_000L * Ids.SCALE)
            .fillQtyScaled(0L)
            .cumQtyScaled(0L)
            .leavesQtyScaled(Ids.SCALE)
            .rejectCode(0)
            .ingressTimestampNanos(iteration)
            .exchangeTimestampNanos(iteration)
            .fixSeqNum(iteration)
            .isFinal(BooleanType.FALSE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
    }

    private void verifyC2Compilation(final long elapsedNanos, final WarmupConfig config) {
        final long avgNanos = elapsedNanos / Math.max(1, config.iterations());
        if (avgNanos > config.thresholdNanos()) {
            LOGGER.log(Level.WARNING, "Warmup avg {0}ns > threshold {1}ns. Do NOT go live until resolved.", avgNanos, config.thresholdNanos());
        } else {
            LOGGER.log(Level.INFO, "Warmup verified: avg {0}ns/iter - C2 confirmed.", avgNanos);
        }
    }
}
