package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.common.AllocationPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

final class BenchmarkSurfaceMapTest {
    private static final Path BENCHMARK_SOURCE_ROOT = Path.of("src", "jmh", "java");

    @Test
    void everyDeclaredV12HotPathSurfaceComesFromAllocationPolicy() {
        assertThat(Arrays.stream(AllocationPolicy.HotPath.values())
                .map(AllocationPolicy.HotPath::documentedName)
                .collect(Collectors.toSet()))
            .containsExactlyInAnyOrder(
                "FIX L2 parsing",
                "FIX L3 parsing",
                "FIX execution-report parsing",
                "SBE encode/decode",
                "Gateway disruptor handoff",
                "Aeron publication handoff",
                "VenueL2Book mutation",
                "VenueL3Book add/change/delete",
                "L3-to-L2 derivation",
                "ConsolidatedL2Book update/query",
                "OwnOrderOverlay update/query",
                "ExternalLiquidityView query",
                "OrderManager state transition",
                "RiskEngine decision",
                "StrategyEngine dispatch",
                "MarketMakingStrategy tick",
                "ArbStrategy tick",
                "ParentOrderRegistry update/query",
                "parent-to-child lookup",
                "ExecutionStrategyEngine dispatch",
                "ImmediateLimitExecution callback",
                "PostOnlyQuoteExecution callback",
                "MultiLegContingentExecution callback",
                "order command encoding");

        assertThat(AllocationPolicy.HotPath.values())
            .extracting(AllocationPolicy.HotPath::benchmarkOwner)
            .allSatisfy(owner -> assertThat(owner).isNotBlank());
    }

    @Test
    void implementedBenchmarkOwnersHaveSourceFiles() {
        final Set<String> implementedBenchmarkOwners = Set.of(
            "BookMutationBenchmark",
            "ExecutionReportBenchmark",
            "FixL2NormalizerBenchmark",
            "FixL3NormalizerBenchmark",
            "GatewayHandoffBenchmark",
            "OrderEncodingBenchmark",
            "OrderManagerBenchmark",
            "OwnLiquidityBenchmark",
            "ParentOrderRegistryBenchmark",
            "RiskDecisionBenchmark",
            "SbeCodecBenchmark",
            "ExecutionStrategyEngineBenchmark",
            "ImmediateLimitExecutionBenchmark",
            "PostOnlyQuoteExecutionBenchmark",
            "MultiLegContingentExecutionBenchmark",
            "StrategyTickBenchmark"
        );

        assertThat(implementedBenchmarkOwners)
            .allSatisfy(owner -> assertThat(benchmarkSourceExists(owner))
                .as(owner)
                .isTrue());

        assertThat(Arrays.stream(AllocationPolicy.HotPath.values())
                .filter(AllocationPolicy.HotPath::hasImplementedBenchmarkOwner)
                .map(AllocationPolicy.HotPath::benchmarkOwner)
                .collect(Collectors.toSet()))
            .isSubsetOf(implementedBenchmarkOwners);
    }

    private static boolean benchmarkSourceExists(final String owner) {
        try (var files = Files.find(
            BENCHMARK_SOURCE_ROOT,
            16,
            (path, attributes) -> attributes.isRegularFile() && path.getFileName().toString().equals(owner + ".java"))) {
            return files.findAny().isPresent();
        } catch (Exception ex) {
            throw new AssertionError("Unable to scan benchmark sources", ex);
        }
    }
}
