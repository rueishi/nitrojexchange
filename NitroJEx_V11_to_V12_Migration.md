# NitroJEx V11 to V12 Migration

## 1. Overview

This document describes the differences between `NitroJEx_Master_Spec_V11.0.md` and `NitroJEx_Master_Spec_V12.0.md`, including compatibility expectations, internal breaking changes, validation requirements, and rollout strategy.

V11 is the frozen architecture baseline. V12 is the low-latency hardening and evidence release focused on deterministic replay, benchmark-proven zero-allocation steady-state hot paths, and pre-QA/UAT proof gates.

## 2. Breaking Internal Changes

- Hot-path market-data and execution-report parsing moves from transitional `String` APIs toward byte/direct-buffer identity handling.
- Venue order IDs and execution IDs move from Java `String` storage toward bounded fixed-byte/fingerprint storage.
- `VenueL3Book`, `ConsolidatedL2Book`, and `OwnOrderOverlay` replace general-purpose object-key maps with bounded primitive/flyweight structures.
- Market-view state is preallocated during startup/warmup instead of created on first live tick where practical.
- Expected hot-path failures move from exceptions/log formatting to counters, status codes, safe drops, or deterministic rejects.
- Outbound order encoding replaces per-send numeric strings and timestamp byte arrays with reusable encoders or byte-buffer APIs.
- Benchmark evidence becomes a release gate, not optional diagnostics.

## 3. Preserved V11 Behavior

- FIX protocol plugin model remains.
- Venue plugin model remains.
- Coinbase venue plugin remains the first-class venue implementation.
- Configurable L2/L3 market-data model remains.
- L3-to-L2 derivation remains.
- Consolidated L2 remains optional and does not replace per-venue executable books.
- Own-order state remains separate from public market books.
- Arbitrage must continue to use external executable liquidity and self-cross protection.
- Coinbase Simulator and live-wire validation remain mandatory before real Coinbase QA/UAT.

## 4. New V12 Capabilities

- Benchmark-verified `0 B/op` steady-state hot-path evidence under documented capacity limits.
- Allocation-free FIX L2/L3 parsing on normal paths.
- Allocation-free execution-report parsing on normal paths.
- Bounded collision-correct venue order ID and execution ID storage.
- Allocation-free L3 book mutation and L3-to-L2 derivation.
- Allocation-free consolidated L2, own-order overlay, and external-liquidity queries.
- Allocation-free order-manager transitions, risk decisions, strategy ticks, gateway handoff, Aeron publication, and order encoding.
- Expanded deterministic replay tests covering books, orders, risk, strategies, timers, snapshots, rejects, and counters.
- CI/release evidence gates for zero-GC claims.
- Stronger security, operations, financial correctness, and runbook readiness before production connectivity claims.

## 5. Module Changes

| Module | Change |
|--------|--------|
| `platform-common` | Adds bounded text identity/fingerprint storage, allocation policy test hooks, and compact identity helpers. |
| `platform-gateway` | Removes normal-path string allocation from FIX parsing and outbound order encoding; adds allocation-free handoff benchmarks and counter-based failure handling. |
| `platform-cluster` | Replaces object-map hot-path book/order/liquidity structures with bounded primitive/flyweight state; expands deterministic replay and snapshot coverage. |
| `platform-benchmarks` | Becomes release evidence owner for hot-path allocation and latency proof. |
| `platform-tooling` | Maintains simulator/live-wire coverage and supports V12 replay/evidence flows. |
| `config` | Documents capacity settings used for preallocation, benchmarks, and QA/UAT gates. |
| `scripts` | Supports reproducible verification and operational preflight where needed. |

## 6. Compatibility

| Compatibility Question | Answer |
|------------------------|--------|
| Are V11 spec and plan modified? | No. They remain frozen. |
| Does V12 change public strategy concepts? | No. It hardens existing market-making and arbitrage behavior. |
| Does V12 add new venue scope? | Not by default. Hot-path proof comes first. |
| Are V11 configs directly compatible? | Yes for the initial V12 documentation and benchmark-harness tasks. Any new capacity/preallocation setting introduced later must provide a deterministic default or a clear startup validation error in the owning task. |
| Are old String-based helpers removed? | They may remain for tests/cold adapters, but benchmarked hot paths must avoid them. |
| Is REST JSON part of the zero-allocation claim? | No. REST remains cold/side path and must convert to compact internal events before entering hot state. |
| Can QA/UAT start before benchmarks pass? | No. Automated tests, replay, live-wire simulator tests, and benchmark evidence must pass first. |
| Does `0 B/op` mean the whole repository allocates nothing? | No. It only applies to declared steady-state hot paths under documented capacities. |

## 7. Rollout Strategy

1. Freeze and archive V11 artifacts:
   - `NitroJEx_Master_Spec_V11.0.md`
   - `nitrojex_implementation_plan_v2.0.0.md`
   - `NitroJEx_V10_to_V11_Migration.md`
2. Create V12 documentation baseline and README pointers.
3. Expand benchmark harness and allocation evidence reporting.
4. Add allocation policy drift tests that map hot paths to benchmark owners.
5. Introduce bounded byte/fingerprint identity storage.
6. Remove string allocation from FIX L2/L3 market-data parsing using the bounded identity representation.
7. Remove string allocation from execution-report parsing using the bounded identity representation.
8. Replace `VenueL3Book` hot-path maps.
9. Replace `ConsolidatedL2Book` hot-path maps.
10. Replace `OwnOrderOverlay` hot-path maps.
11. Preallocate configured market-view state during startup/warmup.
12. Remove order-manager string and byte-array hot-path allocation.
13. Prove gateway disruptor and Aeron publication handoff allocation behavior.
14. Remove outbound order encoding allocation.
15. Prove real strategy ticks and risk decisions are allocation-free.
16. Replace expected hot-path exception/log behavior with counters/status.
17. Expand deterministic replay coverage.
18. Complete Coinbase Simulator live-wire L2/L3 gates.
19. Prove REST JSON boundary isolation.
20. Complete security, operations, financial correctness, and runbook preflight.
21. Run the full V12 verification gate.
22. Begin real Coinbase QA/UAT only after all automated gates pass.

## 8. Rollback Strategy

Rollback is artifact/config based:

- Keep V11 artifacts and binaries available.
- Keep V12 capacity/preallocation config separate from frozen V11 config where behavior differs.
- If a V12 internal representation causes correctness or operational issues, revert to V11 binaries and V11 config.
- If benchmark evidence regresses, do not roll back automatically; block the zero-GC claim and remediate before QA/UAT.

## 9. Pre-QA/UAT Gate

Real Coinbase QA/UAT is blocked until all of the following pass:

```text
unit tests
integration tests
Coinbase Simulator deterministic tests
Coinbase Simulator live-wire L2 E2E tests
Coinbase Simulator live-wire L3 E2E tests
deterministic replay tests
snapshot/load/replay tests
JMH allocation benchmarks
latency histogram / percentile evidence
security/operations/financial correctness preflight
```

Required commands:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
```

`TASK-202` created the benchmark and latency report task names above. If a future
release replaces `:platform-benchmarks:jmhLatencyReport` with an equivalent
automated report task, this migration command list must be updated in that
release.

## 10. Traceability

V10 task IDs remain:

```text
TASK-001 through TASK-034
```

V11 task IDs begin at:

```text
TASK-101
```

V12 task IDs begin at:

```text
TASK-201
```

No V12 task may reuse a V10 or V11 task ID.
