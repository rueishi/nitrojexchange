package ig.rueishi.nitroj.exchange.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.test.CapturingPublication;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("E2E")
final class RiskE2ETest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;

    @Test
    void dailyLossLimit_exceeded_killSwitchActivated() {
        final AtomicLong clockMicros = new AtomicLong(1_000L);
        final RiskEngineImpl engine = engine(100, clockMicros);
        engine.updateDailyPnl(-scaled(1_001));

        final RiskDecision decision = engine.preTradeCheck(
            VENUE,
            INSTRUMENT,
            Side.BUY.value(),
            scaled(100),
            scaled(1),
            Ids.STRATEGY_MARKET_MAKING
        );

        assertThat(decision).isSameAs(RiskDecision.REJECT_DAILY_LOSS);
        assertThat(engine.isKillSwitchActive()).isTrue();
    }

    @Test
    void orderRateLimit_exceeded_ordersRejectedByRisk() {
        final AtomicLong clockMicros = new AtomicLong(1_000L);
        final RiskEngineImpl engine = engine(1, clockMicros);

        assertThat(order(engine)).isSameAs(RiskDecision.APPROVED);
        assertThat(order(engine)).isSameAs(RiskDecision.REJECT_RATE_LIMIT);
    }

    @Test
    void killSwitch_deactivatedByAdmin_tradingResumes() {
        final AtomicLong clockMicros = new AtomicLong(1_000L);
        final RiskEngineImpl engine = engine(100, clockMicros);
        engine.activateKillSwitch("operator");

        assertThat(order(engine)).isSameAs(RiskDecision.REJECT_KILL_SWITCH);

        engine.deactivateKillSwitch();

        assertThat(order(engine)).isSameAs(RiskDecision.APPROVED);
    }

    private static RiskDecision order(final RiskEngineImpl engine) {
        return engine.preTradeCheck(
            VENUE,
            INSTRUMENT,
            Side.BUY.value(),
            scaled(100),
            scaled(1),
            Ids.STRATEGY_MARKET_MAKING
        );
    }

    private static RiskEngineImpl engine(final int maxOrdersPerSecond, final AtomicLong clockMicros) {
        final RiskEngineImpl engine = new RiskEngineImpl(config(maxOrdersPerSecond), new CapturingPublication()::offer, () -> { }, clockMicros::get);
        engine.setVenueConnected(VENUE, true);
        return engine;
    }

    private static RiskConfig config(final int maxOrdersPerSecond) {
        return new RiskConfig(
            maxOrdersPerSecond,
            scaled(1_000),
            Map.of(
                INSTRUMENT,
                new RiskConfig.InstrumentRisk(
                    INSTRUMENT,
                    scaled(10),
                    scaled(100),
                    scaled(100),
                    scaled(1_000_000),
                    80
                )
            )
        );
    }

    private static long scaled(final long value) {
        return value * Ids.SCALE;
    }
}
