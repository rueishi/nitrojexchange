package ig.rueishi.nitroj.exchange.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static guardrails for common value helpers used by declared V12 hot paths.
 *
 * <p>This test intentionally starts with common value/math helpers that are
 * already on the platform-common test classpath. Gateway and cluster packages
 * add their own benchmark evidence in later task-owned cards; this guardrail
 * prevents common hot-path helpers from quietly depending on allocation-heavy
 * or nondeterministic APIs while TASK-202 establishes the evidence harness.</p>
 */
final class HotPathArchitectureTest {
    private static final Set<String> COMMON_HOT_PATH_HELPERS = Set.of(
        "ig.rueishi.nitroj.exchange.common.AllocationPolicy",
        "ig.rueishi.nitroj.exchange.common.Ids",
        "ig.rueishi.nitroj.exchange.common.VenueOrderId"
    );
    private static final Set<String> KNOWN_V12_REMEDIATION_HELPERS = Set.of(
        "ig.rueishi.nitroj.exchange.common.ScaledMath"
    );

    @Test
    void commonHotPathHelpersAvoidAllocationHeavyCollectionsAndFormatting() {
        final ArchRule rule = noClasses()
            .that(describe("are declared common hot-path helpers",
                javaClass -> COMMON_HOT_PATH_HELPERS.contains(javaClass.getName())))
            .should().dependOnClassesThat(describe("are allocation-heavy or formatting APIs",
                HotPathArchitectureTest::isAllocationHeavyOrFormattingApi));

        rule.check(importedCommonClasses());
    }

    @Test
    void commonHotPathHelpersAvoidWallClockAndRandomSources() {
        final ArchRule rule = noClasses()
            .that(describe("are declared common hot-path helpers",
                javaClass -> COMMON_HOT_PATH_HELPERS.contains(javaClass.getName())))
            .should().dependOnClassesThat(describe("are nondeterministic time or random APIs",
                HotPathArchitectureTest::isNondeterministicApi));

        rule.check(importedCommonClasses());
    }

    @Test
    void knownCommonHelperRemediationExclusionsStayExplicit() {
        assertThat(KNOWN_V12_REMEDIATION_HELPERS)
            .containsExactly("ig.rueishi.nitroj.exchange.common.ScaledMath");
    }

    private static com.tngtech.archunit.core.domain.JavaClasses importedCommonClasses() {
        return new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ig.rueishi.nitroj.exchange.common");
    }

    private static boolean isAllocationHeavyOrFormattingApi(final JavaClass javaClass) {
        final String name = javaClass.getName();
        return name.startsWith("java.math.")
            || name.equals("java.lang.StringBuilder")
            || name.equals("java.lang.StringBuffer")
            || name.equals("java.text.DecimalFormat")
            || name.equals("java.util.Formatter")
            || name.equals("java.util.ArrayList")
            || name.equals("java.util.HashMap")
            || name.equals("java.util.HashSet")
            || name.equals("java.util.LinkedHashMap")
            || name.equals("java.util.LinkedHashSet");
    }

    private static boolean isNondeterministicApi(final JavaClass javaClass) {
        final String name = javaClass.getName();
        return name.equals("java.time.Clock")
            || name.equals("java.time.Instant")
            || name.equals("java.time.LocalDateTime")
            || name.equals("java.time.OffsetDateTime")
            || name.equals("java.time.ZonedDateTime")
            || name.equals("java.util.Date")
            || name.equals("java.util.Random")
            || name.equals("java.security.SecureRandom")
            || name.startsWith("java.util.concurrent.ThreadLocalRandom");
    }
}
