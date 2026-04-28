package ig.rueishi.nitroj.exchange.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScaledMathTest {
    @Test
    void safeMulDiv_hiZero_fastPath_correct() {
        assertThat(ScaledMath.safeMulDiv(6_500_000_000_000L, 1_000_000L, Ids.SCALE))
            .isEqualTo(65_000_000_000L);
    }

    @Test
    void safeMulDiv_hiNonZero_1BtcAt65K_exact() {
        assertThat(ScaledMath.safeMulDiv(100_000_000L, 6_500_000_000_000L, Ids.SCALE))
            .isEqualTo(6_500_000_000_000L);
    }

    @Test
    void safeMulDiv_bothMaxLong_bigDecimalPath_noSilentWrap() {
        assertThatThrownBy(() -> ScaledMath.safeMulDiv(Long.MAX_VALUE, Long.MAX_VALUE, 1L))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void vwap_twoFills_correctWeightedAverage() {
        assertThat(ScaledMath.vwap(6_500_000_000_000L, 10_000_000L, 6_510_000_000_000L, 10_000_000L))
            .isEqualTo(6_505_000_000_000L);
    }

    @Test
    void vwap_newQtyZero_returnsZero() {
        assertThat(ScaledMath.vwap(6_500_000_000_000L, 5_000_000L, 6_510_000_000_000L, -5_000_000L))
            .isZero();
    }

    @Test
    void floorToTick_roundsDown() {
        assertThat(ScaledMath.floorToTick(6_500_123_456_789L, 100_000_000L))
            .isEqualTo(6_500_100_000_000L);
    }

    @Test
    void ceilToTick_roundsUp() {
        assertThat(ScaledMath.ceilToTick(6_500_123_456_789L, 100_000_000L))
            .isEqualTo(6_500_200_000_000L);
    }

    @Test
    void floorToLot_roundsDown() {
        assertThat(ScaledMath.floorToLot(12_345_678L, 1_000_000L))
            .isEqualTo(12_000_000L);
    }
}
