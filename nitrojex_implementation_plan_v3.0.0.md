# NitroJEx - V12 Low-Latency Hardening Implementation Plan

## Version

v3.0.0

## Based On

`nitrojex_implementation_plan_v2.0.0.md` (frozen V11 architecture baseline)

## Scope

Implements `NitroJEx_Master_Spec_V12.0.md`.

## Source Spec

`NitroJEx_Master_Spec_V12.0.md`

## Rules

- Do not modify frozen V10 or V11 baseline artifacts.
- Do not reuse TASK-001 through TASK-199.
- V12 task cards start at TASK-201.
- Preserve V11 external behavior unless a task explicitly documents a migration impact.
- Treat FIX parsing, SBE encode/decode, gateway handoff, Aeron publication, book mutation, order state, risk, strategy ticks, and order encoding as steady-state hot paths.
- Keep startup, config, admin, diagnostics, REST, simulator, tests, and benchmark harness code outside the zero-allocation claim.
- Every production-code task must include task-owned tests at the same or stronger coverage level as V11.
- Real Coinbase QA/UAT is blocked until automated local tests, simulator live-wire tests, deterministic replay tests, and benchmark gates pass.

---

# Section 1 - V12 Acceptance Criteria

## Evidence and Release Claims

AC-V12-EVID-001 JMH benchmarks publish `-prof gc` allocation output for every declared hot-path surface.

AC-V12-EVID-002 Declared steady-state hot paths report `0 B/op` after warmup under documented capacities and event mixes.

AC-V12-EVID-003 No GC occurs during steady-state benchmark measurement windows.

AC-V12-EVID-004 Selected gateway and cluster paths publish p50, p90, p99, and p99.9 latency evidence.

AC-V12-EVID-005 Any non-zero allocation has owner, reason, path classification, and remediation task before zero-GC claims are allowed.

AC-V12-EVID-006 CI or release checklist blocks zero-GC / zero-allocation claims unless current evidence is present.

## Allocation-Free Hot Paths

AC-V12-ALLOC-001 Normal FIX L2/L3 parsing avoids per-event `String`, `CharSequence`, byte-array, exception, and formatted-log allocation.

AC-V12-ALLOC-002 FIX execution-report parsing avoids per-event `String`, byte-array, exception, and formatted-log allocation.

AC-V12-ALLOC-003 Venue order IDs and execution IDs use bounded byte/fingerprint representations with collision-correct byte comparison.

AC-V12-ALLOC-004 `VenueL3Book` add/change/delete and L3-to-L2 derivation are allocation-free after warmup.

AC-V12-ALLOC-005 `ConsolidatedL2Book` update/query is allocation-free after warmup.

AC-V12-ALLOC-006 `OwnOrderOverlay` and `ExternalLiquidityView` update/query paths are allocation-free after warmup.

AC-V12-ALLOC-007 `OrderManager` state transitions and duplicate exec ID checks are allocation-free after warmup.

AC-V12-ALLOC-008 `RiskEngine` decisions are primitive, deterministic, and allocation-free.

AC-V12-ALLOC-009 `StrategyEngine`, `MarketMakingStrategy`, and `ArbStrategy` dispatch/tick paths are allocation-free after warmup.

AC-V12-ALLOC-010 Gateway disruptor handoff, Aeron publication handoff, and order command encoding are allocation-free after warmup.

AC-V12-ALLOC-011 All configured market-view books and hot-path structures are preallocated during startup/warmup or have documented bounded lazy initialization before live trading begins.

AC-V12-ALLOC-012 Expected hot-path failures use counters, status codes, safe drops, or deterministic rejects instead of exception construction and formatted logging.

## Determinism

AC-V12-DET-001 Deterministic replay proves identical ordered SBE input plus identical snapshot produces identical book, order, risk, strategy, counter, and outbound command results.

AC-V12-DET-002 Replay coverage includes L2 events, L3 events, L3-to-L2 derivation, execution reports, duplicate execution reports, rejects, risk rejects, strategy orders, timers, and snapshot/load.

AC-V12-DET-003 Capacity exhaustion behavior is deterministic and visible through counters.

AC-V12-DET-004 REST timing, live networking, operator logging, and wall-clock behavior do not enter deterministic cluster state except through ordered internal events.

## Test Coverage and QA/UAT

AC-V12-TEST-001 Every new or modified production class has task-owned tests covering constructors/factories, public methods, state transitions, parser branches, capacity boundaries, exception/counter paths, and failure side effects where applicable.

AC-V12-TEST-002 Unit tests cover positive, negative, edge, malformed, and capacity cases for each changed behavior.

AC-V12-TEST-003 Integration tests validate gateway, generated FIX codecs, SBE messages, gateway handoff, cluster books, order/risk state, strategy dispatch, and outbound order encoding.

AC-V12-TEST-004 Coinbase Simulator deterministic tests continue to cover L2 and L3 snapshot/add/change/delete/failure flows.

AC-V12-TEST-005 Coinbase Simulator live-wire E2E tests prove L2 and L3 paths through gateway, disruptor, Aeron, cluster state, strategy observation, egress, and gateway order-entry handling.

AC-V12-TEST-006 Allocation benchmarks are added or updated for every task that changes a hot path.

AC-V12-TEST-007 Real Coinbase QA/UAT is blocked until all unit, integration, simulator, live-wire E2E, replay, and benchmark gates pass.

AC-V12-TEST-008 Real Coinbase QA/UAT is not used as a replacement for automated local coverage.

## Security, Operations, and Financial Correctness

AC-V12-OPS-001 Secrets handling, credential rotation, and environment separation are documented and tested where automatable.

AC-V12-OPS-002 Kill switch, rejected-order handling, disconnect/reconnect, stale-market-data, and self-trade prevention behavior have tests and runbook evidence.

AC-V12-OPS-003 Balance, position, order, and execution reconciliation paths have deterministic tests or documented operational checks.

AC-V12-OPS-004 Monitoring, alerting, failover, disaster recovery, deployment, and rollback evidence exists before production connectivity claims.

---

# Section 2 - Task Cards

## 2.1 Mandatory Task-Owned Test Coverage Contract

Every V12 task card inherits this coverage contract. Task-specific test bullets below are additions, not replacements.

For every new or modified production behavior, the task must add or update automated coverage for:

```text
positive behavior
negative behavior
edge values and boundary values
malformed or invalid input where input parsing/validation is involved
capacity limits and full-capacity behavior where bounded state is involved
safe-drop, reject, status-code, and counter behavior where expected failure is possible
snapshot/load/recovery behavior where persistent or replayed state is involved
deterministic replay behavior where cluster state, strategy output, counters, or ordering changes
integration behavior where the change crosses module, SBE, gateway, cluster, Aeron, FIX, or simulator boundaries
allocation benchmark coverage where the task changes a declared hot path
latency/percentile evidence where the task changes a latency-sensitive benchmarked path
documentation of any category that is not applicable, including the reason
```

No V12 production-code task is complete with only happy-path tests. A task that cannot automate a required category must document the limitation, owner, exact manual verification command or evidence artifact, and why automation is not practical.

## TASK-201 - Create V12 Documentation Baseline

### Objective

Create the V12 master spec, implementation plan, migration guide, and README references.

### Status

Complete in the current V12 documentation baseline. Future executors should verify consistency rather than recreate these files from scratch.

### Files to Create

```text
NitroJEx_Master_Spec_V12.0.md
nitrojex_implementation_plan_v3.0.0.md
NitroJEx_V11_to_V12_Migration.md
```

### Files to Update

```text
README.md
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-TEST-007
- Active development line points at V12.
- V11 artifacts remain frozen references.
- V12 task IDs start at TASK-201.
- QA/UAT gate explicitly requires automated local coverage and benchmark evidence.

## TASK-202 - Expand Benchmark Harness and Evidence Reporting

### Objective

Make benchmark evidence complete, automated where practical, and release-gatable.

### Files to Update

```text
platform-benchmarks/build.gradle.kts
platform-benchmarks/README.md
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/*
platform-common/src/test/java/ig/rueishi/nitroj/exchange/architecture/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-EVID-001 through AC-V12-EVID-006
- Benchmarks emit machine-readable and human-readable reports.
- Benchmark parameters document capacities, symbols, venue count, order count, event mix, warmup, measurement iterations, JVM flags, and GC profiler settings.
- TASK-202 owns the complete benchmark surface map for every V12 hot path, including `SBE encode/decode`, `VenueL2Book mutation`, and `StrategyEngine dispatch`.
- TASK-202 adds benchmark classes and report wiring for every required surface where implementation already exists.
- TASK-202 may add explicit benchmark-owner placeholders only as temporary traceability scaffolding for future implementation tasks; placeholders do not satisfy AC-V12-EVID-001, do not satisfy `0 B/op` evidence, and do not unblock QA/UAT.
- Benchmark reports include allocation evidence and latency percentile/histogram evidence where JMH can produce it automatically.
- Adds or wires ArchUnit hot-path guardrail tests for forbidden allocation-heavy or nondeterministic APIs in declared hot-path packages.
- Defines whether JFR runtime evidence is required or optional for simulator/live-wire runs, including artifact location and command when enabled.
- Archives or documents artifact locations for JMH JSON, GC profiler output, latency percentile reports, ArchUnit/static guardrail output, and any JFR recordings required by the release checklist.
- Unit, build-script, or report-existence tests verify benchmark/report task wiring where practical.
- Any evidence that cannot be produced automatically must be listed in a release checklist with the reason, owner, and exact manual command.

## TASK-203 - Add Allocation Policy Test Gate

### Objective

Add tests or checks that map documented hot paths to benchmark classes and prevent drift.

### Files to Update

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/AllocationPolicy.java
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/*
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-EVID-006
- AC-V12-TEST-006
- Tests fail when a declared hot path has no benchmark owner.
- Tests cover hot/cold classification and documentation consistency.

## TASK-204 - Introduce Bounded Text Identity Store

### Objective

Create reusable fixed-byte/fingerprint identity storage for venue order IDs and execution IDs.

### Files to Create

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/BoundedTextIdentity.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/BoundedTextIdentityStore.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-003
- AC-V12-TEST-001
- AC-V12-TEST-002
- Tests cover insert, lookup, remove, collision byte-compare, max length, full capacity, snapshot serialization helpers, and deterministic iteration if exposed.
- Normal identity-store operations are bounded and allocation-free after construction/warmup.

## TASK-205 - Remove String Allocation from FIX L2/L3 Market-Data Parsing

### Objective

Parse L2/L3 market-data symbols and order IDs directly from `DirectBuffer` byte ranges and the bounded identity representation introduced by TASK-204.

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL2MarketDataNormalizer.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL3MarketDataNormalizer.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/*
platform-common/src/main/java/ig/rueishi/nitroj/exchange/registry/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-001
- AC-V12-TEST-001
- AC-V12-TEST-002
- AC-V12-TEST-006
- Tests cover valid L2, valid L3, unknown symbol, malformed message, missing order ID, oversized order ID, and multi-entry messages.
- JMH proves L2/L3 parsing `0 B/op` after warmup.

## TASK-206 - Add Allocation-Free Execution-Report Parsing

### Objective

Remove per-event string and byte-array allocation from gateway execution-report normalization using the bounded identity representation introduced by TASK-204.

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ExecutionHandler.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ExecutionRouter*.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-002
- AC-V12-ALLOC-003
- Tests cover fills, partial fills, cancels, rejects, replaces, malformed reports, oversized IDs, and compact identity handoff to downstream SBE/cluster messages.
- JMH proves gateway execution-report parsing and normalization `0 B/op` after warmup.
- `OrderManagerImpl` state/storage changes remain owned by TASK-211; TASK-206 must not modify cluster order-manager state except for test fixtures required to verify gateway handoff compatibility.

## TASK-207 - Replace VenueL3Book Hot-Path Maps

### Objective

Replace `LinkedHashMap`, `HashMap`, record keys, boxed values, per-event byte arrays, and per-event identity objects in `VenueL3Book`.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/VenueL3Book.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/VenueL3BookTest.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-003
- AC-V12-ALLOC-004
- AC-V12-DET-001
- Tests cover add, change, delete, missing delete, size change, price change, side change, capacity full, ID collision, exact own-order lookup, and derived L2 update.
- JMH proves L3 add/change/delete and L3-to-L2 derivation `0 B/op` after warmup.

## TASK-208 - Replace ConsolidatedL2Book Hot-Path Maps

### Objective

Make consolidated contribution tracking and aggregate queries allocation-free.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ConsolidatedL2Book.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/ConsolidatedL2BookTest.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-005
- Tests cover multi-venue same price, deletes, updates, best bid/ask, capacity full, invalid side, and deterministic query behavior.
- JMH proves update/query `0 B/op` after warmup.

## TASK-209 - Replace OwnOrderOverlay Hot-Path Maps

### Objective

Make own-order tracking and external liquidity queries allocation-free.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlay.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityView.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlayTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityViewTest.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-006
- Tests cover upsert, remove, level subtraction, exact L3 subtraction, L2 approximation, self-cross checks, venue-order-ID collision, capacity full, and stale order cleanup.
- JMH proves update/query `0 B/op` after warmup.

## TASK-210 - Preallocate Market View State

### Objective

Ensure configured market books and liquidity overlays are allocated before live trading starts.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/InternalMarketView.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ClusterMain.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/TradingClusteredService.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-011
- Tests cover startup preallocation, unknown venue/instrument handling, warmup behavior, and no first-tick allocation.

## TASK-211 - Remove OrderManager String/Byte-Array Hot-Path Allocation

### Objective

Use bounded identity storage and reusable buffers for venue order IDs and execution IDs in order state transitions.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderState.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderManagerImpl.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/order/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-003
- AC-V12-ALLOC-007
- Tests cover all order state transitions, duplicate exec IDs, snapshot/load, pool overflow, cancel/replace emission, and replay determinism.
- JMH proves order-manager state transition `0 B/op` after warmup.

## TASK-212 - Allocation-Free Gateway Handoff and Aeron Publication Benchmarks

### Objective

Prove gateway disruptor and Aeron publication handoff are allocation-free after warmup.

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/GatewayDisruptor.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/AeronPublisher.java
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-010
- Tests cover successful claim/publish, full ring, invalid publish, Aeron back pressure, and counter behavior.
- JMH proves handoff paths `0 B/op` after warmup.

## TASK-213 - Allocation-Free Outbound Order Encoding

### Objective

Remove `Long.toString`, timestamp byte-array allocation, and symbol string allocation from outbound FIX order encoding.

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/StandardOrderEntryAdapter.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-010
- Tests cover new, cancel, replace, status query, back pressure, timestamp formatting, symbol lookup, and venue policy enrichment.
- JMH proves order encoding `0 B/op` after warmup.

## TASK-214 - Allocation-Free Strategy Metadata and Ticks

### Objective

Cache strategy metadata arrays and prove real strategy ticks are allocation-free.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingStrategy.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategy.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyEngine.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-009
- Tests cover metadata access, market-data ticks, fills, timers, kill switch, venue recovery, risk rejection, and command emission.
- JMH proves real `MarketMakingStrategy` and `ArbStrategy` ticks `0 B/op` after warmup.

## TASK-215 - Risk Decision Benchmark and Reject-Code Hygiene

### Objective

Prove risk decisions allocate nothing and use primitive reject codes.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/RiskEngineImpl.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/RiskEngineTest.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-008
- Tests cover approved orders, notional limits, position limits, kill switch, reset, boundary values, and deterministic reject codes.
- JMH proves risk decision `0 B/op` after warmup.

## TASK-216 - Hot-Path Counter and Safe-Drop Policy

### Objective

Replace expected hot-path exception/log behavior with counters and status returns.

### Files to Update

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/*
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/*
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-ALLOC-012
- Tests cover unknown symbols, malformed messages, capacity full, disruptor full, Aeron back pressure, unknown order, duplicate execution report, and counter increments.

## TASK-217 - Deterministic Replay Expansion

### Objective

Expand replay coverage to prove V12 determinism across all cluster hot-path state.

### Files to Update

```text
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/DeterministicReplayTest.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-DET-001 through AC-V12-DET-004
- Tests cover L2, L3, execution reports, duplicate reports, risk rejects, strategy commands, timers, snapshot/load, and capacity counters.

## TASK-218 - Coinbase Simulator Live-Wire Gate Completion

### Objective

Ensure automated live-wire simulator tests fully block real Coinbase QA/UAT.

### Files to Update

```text
platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/*
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-TEST-004
- AC-V12-TEST-005
- AC-V12-TEST-007
- Tests prove L2 and L3 live-wire flows through gateway, disruptor, Aeron, cluster, strategy observation, egress, and order entry.

## TASK-219 - REST Boundary and Cold-Path Guardrails

### Objective

Prove REST JSON parsing and REST-owned objects cannot leak into hot-path state.

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseRestPoller.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/messages/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-DET-004
- Tests cover parsed balances, malformed JSON, timeout, retry, conversion to compact internal events, and no direct JSON object handoff to cluster hot paths.

## TASK-220 - Security, Operations, and Financial Correctness Preflight

### Objective

Document and test production-readiness controls required before production connectivity claims.

### Files to Update

```text
README.md
config/*
scripts/*
platform-cluster/src/test/java/*
platform-gateway/src/test/java/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.1, including documented justification for any non-applicable coverage category.
- AC-V12-OPS-001 through AC-V12-OPS-004
- Tests or runbooks cover secrets, credential rotation, kill switch, rejected orders, disconnect/reconnect, stale data, self-trade prevention, reconciliation, monitoring, failover, deployment, and rollback.

---

# Section 3 - Required Verification Commands

Before V12 QA/UAT:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
```

`TASK-202` created the exact latency-report task name above. If a future release
replaces `:platform-benchmarks:jmhLatencyReport` with an equivalent task, this
command list must be updated in that release.

The benchmark and latency output must be archived with the release evidence. Any non-zero hot-path allocation must be fixed or explicitly reclassified before zero-GC claims are allowed.
