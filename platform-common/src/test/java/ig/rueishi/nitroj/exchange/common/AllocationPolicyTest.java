package ig.rueishi.nitroj.exchange.common;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the executable V11 hot/cold allocation boundary.
 */
final class AllocationPolicyTest {
    @Test
    void positive_knownHotAndColdPathsClassifyCorrectly() {
        assertThat(AllocationPolicy.classifyHot(AllocationPolicy.HotPath.FIX_L2_PARSING))
            .isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classifyCold(AllocationPolicy.ColdPath.CONFIG_PARSING))
            .isEqualTo(AllocationPolicy.PathClass.COLD_PATH);
        assertThat(AllocationPolicy.classify("strategy tick")).isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classify("rest-polling")).isEqualTo(AllocationPolicy.PathClass.COLD_PATH);
    }

    @Test
    void negative_unknownOrBlankNamesFailClearly() {
        assertThatThrownBy(() -> AllocationPolicy.classify("unknown path"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown allocation policy path");
        assertThatThrownBy(() -> AllocationPolicy.classify(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    void edge_mixedCaseAliasesNormalizeToPolicyNames() {
        assertThat(AllocationPolicy.classify("Fix L2 Parsing")).isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classify("STARTUP-validation")).isEqualTo(AllocationPolicy.PathClass.COLD_PATH);
    }

    @Test
    void exception_nullInputFailsClearly() {
        assertThatThrownBy(() -> AllocationPolicy.classify(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name");
        assertThatThrownBy(() -> AllocationPolicy.classifyHot(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("path");
    }

    @Test
    void failure_documentationDrift_requiredCategoriesRemainRepresented() {
        assertThat(EnumSet.allOf(AllocationPolicy.HotPath.class))
            .contains(
                AllocationPolicy.HotPath.FIX_L2_PARSING,
                AllocationPolicy.HotPath.FIX_L3_PARSING,
                AllocationPolicy.HotPath.SBE_ENCODE_DECODE,
                AllocationPolicy.HotPath.L2_BOOK_MUTATION,
                AllocationPolicy.HotPath.L3_BOOK_MUTATION,
                AllocationPolicy.HotPath.ORDER_STATE_TRANSITION,
                AllocationPolicy.HotPath.RISK_DECISION,
                AllocationPolicy.HotPath.STRATEGY_TICK,
                AllocationPolicy.HotPath.ORDER_COMMAND_ENCODING);
        assertThat(EnumSet.allOf(AllocationPolicy.ColdPath.class))
            .contains(
                AllocationPolicy.ColdPath.CONFIG_PARSING,
                AllocationPolicy.ColdPath.STARTUP_VALIDATION,
                AllocationPolicy.ColdPath.ADMIN_CLI,
                AllocationPolicy.ColdPath.SIMULATOR,
                AllocationPolicy.ColdPath.REST_POLLING,
                AllocationPolicy.ColdPath.TESTS);
    }
}
