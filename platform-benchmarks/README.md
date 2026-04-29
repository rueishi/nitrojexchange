# NitroJEx V12/V13 Hot-Path Benchmark Evidence

This module owns the V12 and V13 JMH evidence harness. Production code cannot claim
low latency, zero allocation, or zero GC for a declared hot path unless the
current benchmark artifacts prove that path under the documented parameters.

## Commands

Allocation evidence:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
```

Latency percentile evidence:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
```

Release evidence check:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:verifyJmhReports
```

The `jmh` task runs with `-prof gc`, writes normal human-readable JMH output to
the console, and writes machine-readable JSON to:

```text
platform-benchmarks/build/reports/jmh/jmh-allocation-results.json
```

The `jmhLatencyReport` task runs JMH sample-time mode in nanoseconds and writes
latency percentile/histogram JSON to:

```text
platform-benchmarks/build/reports/jmh/jmh-latency-results.json
```

## Benchmark Parameters

Default V12 evidence parameters:

```text
benchmark mode: SampleTime unless a benchmark class explicitly overrides it
warmup: 3 iterations, 1 second each
measurement: 5 iterations, 1 second each
forks: 1
time unit: nanoseconds
GC profiler: -prof gc on allocation evidence runs
JVM: Java 21 with --enable-preview, -XX:-RestrictContended, java.base/jdk.internal.vm.annotation export,
     java.base/jdk.internal.misc export/open, and --enable-native-access=ALL-UNNAMED
venues: Coinbase venue id 1 plus bounded multi-venue structures up to Ids.MAX_VENUES
symbols: BTC-USD instrument id 1, ETH-USD instrument id 2 where multi-symbol coverage is required
order count: bounded by the benchmark fixture or the production capacity under test
event mix: steady-state add/change/delete, query, parse, encode, and dispatch operations
capacities: production constants or explicitly constructed bounded fixtures in benchmark setup
```

Benchmarks must preallocate fixtures in `@Setup`. Any allocation reported by
`-prof gc` in a measured steady-state hot path blocks a zero-allocation claim
until it has an owner, reason, path classification, and remediation task.

## V12/V13 Surface Map

Implemented benchmark owners:

```text
FIX L2 parsing -> FixL2NormalizerBenchmark
FIX L3 parsing -> FixL3NormalizerBenchmark
FIX execution-report parsing -> ExecutionReportBenchmark
SBE encode/decode -> SbeCodecBenchmark
Gateway disruptor handoff -> GatewayHandoffBenchmark
Aeron publication handoff -> GatewayHandoffBenchmark
VenueL2Book mutation -> BookMutationBenchmark
VenueL3Book add/change/delete -> BookMutationBenchmark
L3-to-L2 derivation -> BookMutationBenchmark
ConsolidatedL2Book update/query -> BookMutationBenchmark
OwnOrderOverlay update/query -> OwnLiquidityBenchmark
ExternalLiquidityView query -> OwnLiquidityBenchmark
OrderManager state transition -> OrderManagerBenchmark
RiskEngine decision -> RiskDecisionBenchmark
StrategyEngine dispatch -> StrategyTickBenchmark
MarketMakingStrategy tick -> StrategyTickBenchmark
ArbStrategy tick -> StrategyTickBenchmark
ParentOrderRegistry update/query -> ParentOrderRegistryBenchmark
parent-to-child lookup -> ParentOrderRegistryBenchmark
ExecutionStrategyEngine dispatch -> ExecutionStrategyEngineBenchmark
ImmediateLimitExecution callback -> ImmediateLimitExecutionBenchmark
PostOnlyQuoteExecution callback -> PostOnlyQuoteExecutionBenchmark
MultiLegContingentExecution callback -> MultiLegContingentExecutionBenchmark
order command encoding -> OrderEncodingBenchmark
```

V13 execution evidence is interpreted the same way as V12 evidence: allocation
runs use `-prof gc` and latency runs use nanosecond sample-time percentiles.
The parent registry, execution engine, and three built-in execution strategies
are mandatory release-gate surfaces before any V13 low-latency or zero-GC claim.

Traceability placeholders for future implementation tasks:

```text
None.
```

Placeholders are not evidence. They only keep ownership visible until the task
that changes that hot path adds its benchmark class and produces fresh reports.

## Optional JFR Evidence

JFR is optional for simulator and live-wire investigation runs. It is not a
substitute for JMH `0 B/op` evidence, but it can be archived with release notes
when a long-running scenario needs runtime confirmation:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest \
  -Dorg.gradle.jvmargs="-XX:StartFlightRecording=filename=platform-benchmarks/build/reports/jfr/v12-live-wire.jfr,settings=profile"
```

Archive optional recordings under:

```text
platform-benchmarks/build/reports/jfr/
```

## Release Checklist

Before QA/UAT or any public low-latency claim:

```text
run :platform-benchmarks:jmh
run :platform-benchmarks:jmhLatencyReport
run :platform-common:test for ArchUnit/static guardrails
archive both JMH JSON reports
record every non-zero B/op result with owner, reason, classification, remediation task, and exact rerun command
confirm placeholder surfaces have been replaced by real benchmarks for completed production tasks
attach optional JFR only when simulator/live-wire runtime investigation was required
```
