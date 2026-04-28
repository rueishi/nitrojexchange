package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.messages.Side;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * V12 allocation evidence for primitive risk decision checks.
 *
 * <p>The benchmark uses a no-op risk-event sink, a deterministic primitive
 * clock, and configured limits loaded during setup. Measured invocations only
 * execute the pre-trade decision tree and return preallocated
 * {@link RiskDecision} singletons carrying primitive reject codes.</p>
 */
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class RiskDecisionBenchmark {
    private static final int OPS = 65_536;
    private static final int VENUE_ID = Ids.VENUE_COINBASE;
    private static final int INSTRUMENT_ID = Ids.INSTRUMENT_BTC_USD;
    private static final int STRATEGY_ID = Ids.STRATEGY_MARKET_MAKING;

    private RiskEngineImpl engine;
    private long clockMicros;

    @Setup
    public void setup() {
        engine = new RiskEngineImpl(config(), RiskEventSink.NO_OP, () -> { }, () -> clockMicros);
        engine.setVenueConnected(VENUE_ID, true);
        clockMicros = 1_000_000L;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public byte approvedDecision() {
        RiskDecision decision = RiskDecision.APPROVED;
        for (int i = 0; i < OPS; i++) {
            clockMicros += RiskEngineImpl.WINDOW_MICROS + 1L;
            decision = engine.preTradeCheck(
                VENUE_ID,
                INSTRUMENT_ID,
                Side.BUY.value(),
                100L * Ids.SCALE,
                1L,
                STRATEGY_ID);
        }
        return decision.rejectCode();
    }

    private static RiskConfig config() {
        return new RiskConfig(
            Ids.MAX_ORDERS_PER_WINDOW,
            1_000L * Ids.SCALE,
            Map.of(
                INSTRUMENT_ID,
                new RiskConfig.InstrumentRisk(
                    INSTRUMENT_ID,
                    10L * Ids.SCALE,
                    100L * Ids.SCALE,
                    100L * Ids.SCALE,
                    1_000_000L * Ids.SCALE,
                    80)));
    }
}
