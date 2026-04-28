# NitroJEx Master Specification V12.0

## Status

Active Development - Supersedes V11.0

## Based On

`NitroJEx_Master_Spec_V11.0.md` (frozen architecture baseline)

## Implementation Plan

`nitrojex_implementation_plan_v3.0.0.md`

## Key Enhancements

- Low-latency hardening release focused on proof, not new venue breadth.
- Targeted benchmark verification of zero-allocation steady-state hot paths with JMH GC-profiler evidence.
- Deterministic replay evidence for books, orders, risk, strategy decisions, and outbound commands.
- Replacement of remaining hot-path `HashMap`, `LinkedHashMap`, `List`, `String`, record-key, boxed-value, and avoidable object creation.
- Byte/direct-buffer FIX parsing for normal market-data and execution-report paths.
- Bounded venue order ID and execution ID representations that avoid per-event `String` allocation.
- Preallocated market, order, risk, strategy, and gateway handoff state under configured capacity limits.
- Benchmark and CI gates that block zero-GC / zero-allocation claims unless evidence exists.
- V11-level or stronger automated unit, integration, simulator, live-wire, replay, and benchmark coverage before QA/UAT.
- Production-readiness hardening for security, operations, financial correctness, and audit evidence.

---

# 1. Versioning and Baseline Rules

## 1.1 Frozen Baselines

The following files are immutable historical artifacts:

```text
NitroJEx_Master_Spec_V10.0.md
nitrojex_implementation_plan_v1.4.0.md
NitroJEx_Master_Spec_V11.0.md
nitrojex_implementation_plan_v2.0.0.md
NitroJEx_V10_to_V11_Migration.md
```

They must not be edited for V12 work except for explicit archival corrections approved separately. V11 is the frozen architecture baseline for multi-version FIX, venue plugins, Coinbase FIX L3, L3-to-L2 derivation, consolidated L2, own-liquidity views, and simulator coverage.

## 1.2 V12 Scope

V12 is a low-latency hardening and evidence release. It should not add broad new venue or strategy features until the current V11 trading paths are proven deterministic, bounded, and zero-allocation in steady state.

V12 must preserve V11 behavior while changing internal representations where needed to remove hot-path allocation and make latency more predictable.

## 1.3 Task Numbering

V12 implementation tasks must not reuse V10 or V11 task IDs. V12 task cards start at:

```text
TASK-201
```

---

# 2. Professional Claim

Before V12 evidence is complete, the allowed claim remains:

```text
NitroJEx is designed toward low-latency deterministic hot paths and has a
roadmap to verified zero allocation / zero GC behavior. V11 is not yet
benchmark-proven zero-GC.
```

After V12 evidence is complete, the allowed claim becomes:

```text
NitroJEx has benchmark-verified zero-allocation steady-state trading hot
paths under documented capacity limits, with deterministic replay evidence
for the cluster core and explicit cold-path boundaries for startup, config,
admin, diagnostics, simulator, REST, and tests.
```

This claim applies only to declared steady-state hot paths under tested configuration limits. It does not mean the entire repository is allocation-free.

---

# 3. Hot-Path Boundary

The following paths are hot and must be allocation-free after startup and warmup:

```text
FIX L2 market-data parsing
FIX L3 market-data parsing
FIX execution-report parsing
SBE message encode/decode
Gateway disruptor handoff
Aeron publication handoff
VenueL2Book mutation
VenueL3Book mutation
L3-to-L2 derivation
ConsolidatedL2Book mutation
OwnOrderOverlay update/query
ExternalLiquidityView query
OrderManager state transition
RiskEngine decision
StrategyEngine dispatch
MarketMakingStrategy tick
ArbStrategy tick
Order command encoding
```

The following paths are cold/control and may allocate:

```text
TOML config loading
plugin registry construction
startup validation
warmup orchestration
admin CLI
replay tooling setup
CoinbaseExchangeSimulator internals
REST polling and JSON parsing
operator diagnostics
rate-limited monitoring snapshots
exception construction for unrecoverable startup/config failures
tests and benchmark harness code
```

Cold-path objects must not cross into hot-path state unless converted into compact primitive, fixed-buffer, SBE, or flyweight form first.

---

# 4. Allocation Rules

## 4.1 Forbidden on Normal Hot Paths

Normal hot paths must not perform:

```text
new String(...)
Long.toString(...) / Integer.toString(...)
getStringWithoutLengthAscii(...) when it creates a String
String.format(...)
DateTimeFormatter.format(...).getBytes(...)
new byte[] per event
new record key per lookup/update
new object per event
HashMap / LinkedHashMap mutation with object keys
List growth
boxed Long / Integer / Boolean values
exception construction for expected data-quality failures
formatted logging for expected failures
```

## 4.2 Required Hot-Path Storage Patterns

V12 hot-path state must prefer:

```text
arrays indexed by venueId / instrumentId / side
packed primitive keys
bounded primitive maps where direct indexing is impractical
preallocated object pools
fixed-size ring buffers
fixed byte storage for venue text IDs
reusable DirectBuffer / UnsafeBuffer wrappers
scaled long values for price, size, quantity, notional, and fees
counter/status-code failure reporting
```

## 4.3 Capacity Behavior

Every bounded hot-path structure must define capacity behavior:

```text
accept within capacity
reject/drop with counter when full
never resize on the hot path
never allocate an exception for expected capacity exhaustion
produce deterministic results under the same input stream
```

---

# 5. FIX Parsing and Normalization

FIX L2/L3 market-data normalizers and execution-report parsers must parse directly from byte/direct-buffer ranges.

Required behavior:

```text
parse decimals from bytes into scaled long
parse FIX enum tags from byte/char values
resolve symbols through byte-based registry lookup
carry venue order IDs as byte range or fixed-buffer identity
avoid normal-path String/CharSequence allocation
drop malformed messages through counters/status, not exception/log formatting
```

Compatibility String APIs may remain for tests, cold adapters, and migration scaffolding, but benchmarked production paths must avoid them.

---

# 6. Venue Order ID and Execution ID Representation

Venue order IDs and execution IDs may arrive as textual FIX fields, but hot-path storage must use bounded byte/fingerprint representations.

Target representation:

```text
venueId
instrumentId
hash/fingerprint
fixed byte storage
length
collision check using stored bytes
```

Correctness requirements:

- Hash collisions must be resolved by byte comparison.
- Exact own-order reconciliation remains venue- and instrument-scoped.
- Duplicate execution-report detection must be bounded and deterministic.
- Snapshot and recovery must preserve enough bytes to rebuild identity state.

---

# 7. Book and Liquidity State

## 7.1 VenueL3Book

`VenueL3Book` must use bounded allocation-free state for:

```text
order lookup by venue order ID
order side / price / size
level aggregation
add / change / delete
L3-to-L2 derived updates
exact own-order lookup
```

## 7.2 ConsolidatedL2Book

`ConsolidatedL2Book` must preserve venue contribution boundaries without per-event record keys or boxed values.

Best bid/ask reads must be deterministic and allocation-free. If scanning bounded arrays is used, the capacity and worst-case latency must be documented and benchmarked.

## 7.3 OwnOrderOverlay and ExternalLiquidityView

Own-liquidity state must be stored separately from public market books and must support allocation-free:

```text
upsert/remove by clOrdId
optional venue order ID linkage
level own-size query
exact L3 own-order query
same-venue self-cross check
external executable liquidity query
```

---

# 8. Order, Risk, and Strategy State

## 8.1 OrderManager

Order state transitions, execution-report application, duplicate exec ID detection, cancel/replace command emission, and snapshot hot helpers must avoid per-event strings and byte arrays.

Pool overflow must be visible through counters and tests. Normal configured capacity must not overflow.

## 8.2 RiskEngine

Risk decisions must remain primitive, deterministic, and allocation-free. Any reject reason used on the hot path must be a code, not a formatted string.

## 8.3 StrategyEngine and Strategies

Strategy registration remains startup/control path. Dispatch and tick paths are hot.

Strategies must cache any subscribed-instrument and active-venue arrays during initialization. Strategy ticks must not allocate command buffers, arrays, records, strings, or diagnostics on normal paths.

---

# 9. Gateway Handoff and Order Encoding

Gateway disruptor and Aeron publication handoff must be benchmarked and allocation-free after startup.

Outbound order encoding must avoid:

```text
Long.toString(...)
String symbol lookup per send where a byte/flyweight form is available
DateTimeFormatter allocation per send
new byte[] timestamps
formatted log messages for expected back pressure
```

V12 must provide reusable ASCII numeric and timestamp encoders, or use existing protocol APIs that accept reusable byte buffers without allocation.

---

# 10. Determinism

NitroJEx determinism means:

```text
Given the same ordered internal SBE event stream and the same initial snapshot,
the cluster core must produce the same order state, risk state, book state,
strategy decisions, counters relevant to hot-path behavior, and outbound command
sequence.
```

Live networking, wall-clock time, OS scheduling, exchange behavior, REST timing, and operator logging are not deterministic. They must be normalized into ordered internal events before entering deterministic core logic.

Replay tests must include:

```text
L2 market data
L3 market data
L3-to-L2 derivation
execution reports
duplicate execution reports
order rejects
risk rejects
strategy-generated orders
timer-driven strategy behavior
snapshot/load/replay
capacity/full-path counters where applicable
```

---

# 11. Benchmark and Evidence Requirements

V12 must include JMH allocation and latency evidence for:

```text
FIX L2 parsing
FIX L3 parsing
FIX execution-report parsing
SBE encode/decode
Gateway disruptor handoff
Aeron publication handoff
VenueL2Book mutation
VenueL3Book add/change/delete
L3-to-L2 derivation
ConsolidatedL2Book update/query
OwnOrderOverlay update/query
ExternalLiquidityView query
OrderManager state transition
RiskEngine decision
StrategyEngine dispatch
MarketMakingStrategy tick
ArbStrategy tick
order command encoding
```

Required evidence:

```text
JMH results include -prof gc output
0 B/op after warmup for declared hot paths
no GC during steady-state measurement windows
published p50 / p90 / p99 / p99.9 latency and histogram/percentile evidence where applicable
benchmark parameters document capacities and event mix
non-zero allocation has owner, reason, path classification, and remediation task
CI or release checklist blocks zero-GC claims without current evidence
```

The benchmark harness must own the complete required hot-path surface map. At minimum, the initial V12 benchmark expansion must create report ownership for all required surfaces, including `SBE encode/decode`, `VenueL2Book mutation`, and `StrategyEngine dispatch`, so later production tasks update existing benchmark owners instead of creating ad hoc evidence paths. Temporary benchmark-owner placeholders are traceability aids only; they do not satisfy benchmark evidence, `0 B/op`, or QA/UAT gates.

Evidence should be automated through Gradle/JMH/report tasks wherever practical. If a required evidence item cannot be automated, the release checklist must name the owner, exact command, artifact location, and reason automation is not available.

## 11.1 Automated Evidence Stack

V12 evidence must be generated by a repeatable tool stack rather than informal observation.

Required evidence ownership:

```text
JMH
  Primary allocation and latency proof for declared hot paths.

JMH -prof gc
  B/op, allocation rate, GC count, and GC time evidence.

JMH JSON / latency report artifacts
  Archived p50 / p90 / p99 / p99.9 latency and benchmark-parameter evidence.

ArchUnit
  Static hot-path guardrails against forbidden allocation-heavy or nondeterministic APIs.

JUnit unit/integration/E2E/replay tests
  Correctness, deterministic replay, simulator/live-wire flow, counter, and failure behavior.

JFR
  Optional runtime evidence for longer simulator/live-wire runs, including GC events,
  safepoints, allocation pressure, lock contention, and stalls.

Gradle verification tasks
  Repeatable local and CI execution of tests, benchmark reports, static guardrails,
  and release-evidence artifact generation.
```

ArchUnit does not prove latency, and JFR does not replace JMH. The V12 low-latency claim requires benchmark evidence plus static guardrails plus correctness/replay/E2E evidence. JFR is required only when a task or release checklist marks a longer-running runtime scenario as needing system-level evidence.

---

# 12. Test Coverage and QA/UAT Gate

V12 must keep at least the same practical coverage standard as V11 and strengthen it for allocation and determinism.

## 12.1 Required Test Tiers

```text
unit tests
integration tests
Coinbase Simulator deterministic tests
Coinbase Simulator live-wire E2E tests
deterministic replay tests
snapshot/load/replay tests
JMH allocation benchmarks
latency histogram / percentile evidence runs
static/code-review allocation scans
```

## 12.2 Task-Owned Test Requirements

Every V12 production code task must include task-owned tests covering:

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

Private helpers should be covered through public APIs. Package-private test seams are allowed only when they make allocation or deterministic behavior measurable without weakening production design.

## 12.3 QA/UAT Blocker

Real Coinbase QA/UAT must not begin until:

```text
all unit tests pass
all integration tests pass
all Coinbase Simulator tests pass
all Coinbase Simulator live-wire L2 and L3 E2E tests pass
all deterministic replay tests pass
all V12 benchmark gates publish current evidence
all hot-path non-zero allocations are remediated or explicitly reclassified
all security/operations/financial correctness preflight checks are complete
```

Real Coinbase QA/UAT is not a substitute for automated local coverage. It is the final external venue validation stage after local proof.

---

# 13. Security, Operations, and Financial Correctness

V12 must not claim production connectivity readiness until the following are covered:

```text
secrets handling and credential rotation
environment separation for dev / QA / UAT / production
audit trail for order decisions and operator actions
kill switch and kill-switch recovery evidence
risk limit configuration and validation
rejected-order handling
disconnect / reconnect / stale market-data behavior
self-trade prevention validation
balance and position reconciliation
monitoring and alerting
failover and disaster recovery runbooks
deployment evidence and rollback procedure
```

---

# 14. Non-Goals

- Do not modify frozen V10 or V11 baseline specs/plans.
- Do not reuse TASK-001 through TASK-199.
- Do not add new production venue breadth before hot-path proof is complete.
- Do not claim repository-wide zero allocation.
- Do not claim production zero-GC readiness without benchmark evidence.
- Do not use real Coinbase QA/UAT to compensate for missing automated local tests.
- Do not move REST JSON objects, config records, or diagnostics objects into hot-path state.
