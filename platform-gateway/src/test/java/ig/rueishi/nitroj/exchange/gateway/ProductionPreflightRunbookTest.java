package ig.rueishi.nitroj.exchange.gateway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V12 production-readiness runbook guard.
 *
 * <p>Responsibility: keeps the documented QA/UAT blocker aligned with the
 * repository evidence files. Role in system: production connectivity cannot be
 * claimed from code tests alone; this test ensures the runbook, config matrix,
 * and executable preflight script all name the security, operations, financial
 * correctness, deployment, and rollback evidence required before real Coinbase
 * QA/UAT.</p>
 */
final class ProductionPreflightRunbookTest {
    @Test
    void v12PreflightEvidenceMatrix_coversSecurityOperationsFinancialAndRollbackGates() throws Exception {
        final Path root = repoRoot();
        final String readme = Files.readString(root.resolve("README.md"));
        final String matrix = Files.readString(root.resolve("config/v12-production-preflight.toml"));
        final Path scriptPath = root.resolve("scripts/v12-preflight-check.sh");
        final String script = Files.readString(scriptPath);
        final String combined = (readme + "\n" + matrix + "\n" + script).toLowerCase();

        assertThat(Files.isExecutable(scriptPath)).isTrue();
        assertThat(combined).contains(
            "secrets",
            "credential rotation",
            "kill switch",
            "rejected",
            "disconnect/reconnect",
            "stale",
            "self-trade",
            "reconciliation",
            "monitoring",
            "failover",
            "deployment",
            "rollback");
    }

    @Test
    void v12PreflightScript_runsRequiredAutomatedQaUatBlockers() throws Exception {
        final Path root = repoRoot();
        final String script = Files.readString(root.resolve("scripts/v12-preflight-check.sh"));
        final String matrix = Files.readString(root.resolve("config/v12-production-preflight.toml"));

        assertThat(script).contains("gradlew\" clean");
        assertThat(script).contains("gradlew\" check");
        assertThat(script).contains("gradlew\" e2eTest");
        assertThat(script).contains(":platform-benchmarks:jmh");
        assertThat(script).contains(":platform-benchmarks:jmhLatencyReport");
        assertThat(matrix).contains("release_artifacts");
    }

    private static Path repoRoot() {
        final Path local = Path.of("README.md");
        if (Files.exists(local)) {
            return Path.of(".");
        }
        return Path.of("..");
    }
}
