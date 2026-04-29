# NitroJEx - V13 Execution Strategy Layer Implementation Plan

## Version

v4.0.0

## Based On

`nitrojex_implementation_plan_v3.0.0.md` (frozen V12 low-latency evidence baseline)

## Scope

Implements `NitroJEx_Master_Spec_V13.0.md`.

## Source Spec

`NitroJEx_Master_Spec_V13.0.md`

## Rules

- Do not modify frozen V10, V11, or V12 baseline artifacts.
- Do not reuse TASK-001 through TASK-220.
- V13 task cards start at TASK-301.
- Preserve V12 deterministic replay, hot-path allocation policy, benchmark gates, simulator live-wire gates, and production preflight.
- Split trading intent from child-order execution without changing venue plugin or FIX protocol plugin responsibilities.
- Treat V13 as superseding V12 only for the cluster strategy/execution layer; V12 remains the frozen low-latency evidence baseline.
- Preserve V12 external behavior for `MarketMakingStrategy` and `ArbStrategy` when default execution strategies are used.
- Every child order must still pass RiskEngine before submission.
- Every production-code task must include task-owned tests at the same or stronger coverage level as V12.
- No V13 production-code task is complete until expected behavior is fully verified across applicable positive, negative, edge, malformed, capacity, replay, integration, allocation, and documentation categories.

---

# Section 1 - V13 Acceptance Criteria

## Parent Intent and Execution Architecture

AC-V13-EXEC-001 Trading strategies emit parent intents and no longer encode child-order commands directly.

AC-V13-EXEC-002 Execution strategies own child order submission, cancel/replace sequencing, child execution attribution, parent timers, and parent terminal state.

AC-V13-EXEC-003 `ParentOrderRegistry` owns bounded parent state and parent-to-active-child mappings with deterministic capacity counters.

AC-V13-EXEC-004 `OrderManager` remains the child-order lifecycle owner and child `OrderState` includes `parentOrderId`.

AC-V13-EXEC-005 `ExecutionStrategyEngine` validates and dispatches execution strategy plugins separately from trading strategy plugins.

AC-V13-EXEC-006 Unknown or incompatible execution strategy IDs fail startup/config validation with clear errors.

AC-V13-EXEC-007 Production `MessageRouter`/`StrategyEngine` wiring routes market-data ticks to the execution engine while active, routes child execution reports with `OrderState.parentOrderId` to the execution engine before trading-strategy active gating, and routes parent timers by registered owner correlation ID.

AC-V13-EXEC-008 `StrategyContext` no longer exposes child-order encoders to trading strategies after the V13 migration completes.

AC-V13-EXEC-009 Canonical built-in execution strategy IDs are `ImmediateLimit`, `PostOnlyQuote`, and `MultiLegContingent`; default compatibility is `MarketMaking -> PostOnlyQuote`, `Arb -> MultiLegContingent`, and generic one-shot parent intents -> `ImmediateLimit`.

## Built-In Execution Strategies

AC-V13-BUILTIN-001 `ImmediateLimitExecution` handles one-child IOC/limit parent lifecycle.

AC-V13-BUILTIN-002 `PostOnlyQuoteExecution` handles quote child submission, market-data refresh, cancel/replace, and one-tick-deeper retry after post-only reject.

AC-V13-BUILTIN-003 `MultiLegContingentExecution` handles two IOC legs, ordered leg timer, required timer schedule failure, partial-leg imbalance hedge, hedge risk rejection, kill-switch escalation, and parent cooldown reason codes.

## Determinism

AC-V13-DET-001 Replay proves identical ordered SBE input plus identical snapshot produces identical parent state, child command sequence, child order state, strategy callbacks, timer effects, counters, and outbound command results.

AC-V13-DET-002 Parent timers enter the cluster as ordered timer events and never as wall-clock callbacks; owner registration occurs before scheduling, duplicate active correlations are rejected without owner replacement, failed scheduling rolls back owner registration, global non-execution timers do not increment execution unknown-timer counters, and required parent timer failures terminate parents deterministically without live child orders or active child links.

AC-V13-DET-003 Parent snapshot/load preserves parent state, parent-to-child mappings, child attribution, counters, and terminal reason codes.

AC-V13-DET-004 Parent lifecycle failures use primitive reason codes and deterministic terminal events.

AC-V13-DET-005 V12-to-V13 behavior-equivalence tests prove default V13 execution strategies preserve functionally equivalent V12 strategy behavior for risk decisions, aggregate fills, kill-switch state, parent outcomes, and documented counters.

## Allocation and Latency Evidence

AC-V13-ALLOC-001 Parent intent dispatch, parent registry update/query, parent-to-child lookup, execution strategy dispatch, and built-in execution strategy callbacks are allocation-free after warmup.

AC-V13-ALLOC-002 V13 parent/execution hot paths avoid `String`, boxed keys, general-purpose mutable collections, exception construction, and formatted logging on expected outcomes.

AC-V13-ALLOC-002A `OrderState.parentOrderId` is the authoritative hot-path child-to-parent attribution field; no general-purpose auxiliary child-to-parent map is allowed on the hot path.

AC-V13-ALLOC-003 JMH benchmarks publish `-prof gc` allocation output for every new V13 hot-path surface.

AC-V13-ALLOC-004 Selected V13 dispatch paths publish p50, p90, p99, and p99.9 latency evidence.

AC-V13-ALLOC-005 Any non-zero V13 hot-path allocation has owner, reason, path classification, and remediation before zero-allocation claims are allowed.

## Test Coverage and QA/UAT

AC-V13-TEST-001 Every new or modified production class has task-owned tests covering constructors/factories, public methods, state transitions, parser branches, capacity boundaries, exception/counter paths, and failure side effects where applicable.

AC-V13-TEST-002 Unit tests cover positive, negative, edge, malformed, capacity, cancellation race, timer race, risk reject, child reject, parent terminal, and replay cases for each changed behavior.

AC-V13-TEST-003 Integration tests validate strategy-to-execution dispatch, parent registry, order manager, SBE messages, gateway handoff, FIX order entry, simulator execution reports, and parent callbacks.

AC-V13-TEST-004 Live-wire E2E tests prove `MarketMakingStrategy -> PostOnlyQuoteExecution` and `ArbStrategy -> MultiLegContingentExecution` through the local Coinbase simulator.

AC-V13-TEST-005 Real Coinbase QA/UAT is blocked until all V12 gates and all V13 parent/execution tests, replay, E2E, and benchmark gates pass.

AC-V13-TEST-006 V13 does not use real Coinbase QA/UAT as a replacement for automated local coverage.

---

# Section 2 - Mandatory Task-Owned Test Coverage Contract

Every V13 task card inherits this coverage contract. Task-specific test bullets are additions, not replacements.

For every new or modified production behavior, the task must add or update automated coverage for:

```text
positive behavior
negative behavior
edge values and boundary values
malformed or invalid input where parsing/validation is involved
capacity limits and full-capacity behavior where bounded state is involved
safe-drop, reject, status-code, terminal-reason, and counter behavior where expected failure is possible
snapshot/load/recovery where persistent or replayed state is involved
deterministic replay where parent state, child state, strategy output, counters, or ordering changes
integration where behavior crosses strategy, execution engine, parent registry, order manager, SBE, gateway, Aeron, FIX, or simulator boundaries
allocation benchmark coverage where a declared hot path changes
latency/percentile evidence where a latency-sensitive dispatch path changes
V12-to-V13 behavior equivalence where existing strategy behavior is refactored
documentation of non-applicable categories with reason
```

Required V13 case categories:

```text
parent canceled mid-flight while child is working
child rejected with parent leftover
child filled while parent cancel is in flight
timer firing during pending execution report
post-only reject one-tick-deeper retry
multi-leg one-leg-fill imbalance hedge
hedge rejected by risk
hedge rejection kill-switch escalation
parent-state replay determinism
parent snapshot/load round trip
parent capacity full
parent-to-child mapping capacity full
unknown execution strategy startup failure
strategy/execution compatibility startup failure
V12-to-V13 behavior equivalence for migrated strategies
```

No task may claim complete coverage by testing only happy paths. If automation is not practical, the task must document the limitation, owner, exact manual verification command or evidence artifact, and why automation is not practical.

Every task card from TASK-301 through TASK-318 must treat the following as required acceptance criteria, even when the task-specific bullets below add more detail:

```text
1. Satisfy the Section 2 mandatory task-owned coverage contract.
2. Add or update automated tests for every applicable production behavior changed by the task.
3. Cover positive, negative, edge/boundary, malformed/invalid input, capacity, failure/counter, replay, integration, allocation, and latency categories where applicable.
4. Prove expected behavior with assertions, not only compilation or smoke coverage.
5. Run the narrowest relevant module tests plus broader Gradle checks required by the task.
6. Document every non-applicable coverage category with exact reason and owner.
7. Do not defer task-owned coverage to a later task unless the plan explicitly names the later task and the current task documents the deferral.
```

---

# Section 3 - Task Cards

## TASK-301 - Create V13 Documentation Baseline

### Objective

Create the V13 master spec, implementation plan, migration guide, and README references.

### Files to Create

```text
NitroJEx_Master_Spec_V13.0.md
nitrojex_implementation_plan_v4.0.0.md
NitroJEx_V12_to_V13_Migration.md
```

### Files to Update

```text
README.md
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- Active development line points at V13.
- V12 artifacts remain frozen references.
- V13 task IDs start at TASK-301.
- V13 scope explicitly excludes TWAP, VWAP, POV, peg, SOR, new venue plugins, new FIX plugins, and RiskEngine semantic changes.

## TASK-302 - Add Parent SBE Messages and Child Parent ID

### Objective

Add `ParentOrderIntent`, `ParentOrderUpdate`, `ParentOrderTerminal`, and extend child order messages/snapshots with `parentOrderId`.

### Files to Update

```text
platform-common/src/main/resources/messages.xml
platform-common/src/test/java/*
platform-gateway/src/test/java/*
platform-cluster/src/test/java/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-004
- Tests cover encode/decode, default/null parent ID, max parent ID, malformed/unknown template handling, backward-compatible child command behavior, and generated codec regeneration.
- Gateway order-entry tests prove parent ID is preserved internally and not sent to venue FIX unless explicitly supported.

## TASK-303 - Implement ParentOrderRegistry

### Objective

Implement bounded parent state and parent-to-child mapping storage.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ParentOrderRegistry.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ParentOrderState.java
```

### Files to Update

```text
platform-cluster/src/test/java/*
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/AllocationPolicy.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-003
- AC-V13-DET-003
- AC-V13-ALLOC-001
- AC-V13-ALLOC-002A
- Tests cover claim, lookup, state transition, child link/unlink, active child lookup by parent, duplicate link, unknown child, capacity full, snapshot/load, terminal reason codes, and counters.
- JMH proves parent registry update/query and parent-to-child lookup allocation behavior.

## TASK-304 - Add ExecutionStrategy Plugin Contract and Context

### Objective

Define `ExecutionStrategy`, `ExecutionStrategyContext`, and primitive/flyweight views for parent intents and child executions.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategy.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategyContext.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ParentOrderIntentView.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ChildExecutionView.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-002
- AC-V13-DET-002
- AC-V13-ALLOC-002
- Tests cover contract initialization, null dependency rejection, clock/timer access, flyweight reuse, no String conversion requirements, and deterministic child execution view fields.

## TASK-305 - Implement ExecutionStrategyEngine and Registry

### Objective

Implement registration, startup validation, dispatch, timer routing, child execution routing, and parent callbacks.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategyEngine.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategyRegistry.java
```

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContext.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContextImpl.java
platform-cluster/src/test/java/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-005 through AC-V13-EXEC-009
- Tests cover strategy registration, execution registration, canonical built-in IDs, default compatibility matrix, unknown execution strategy, incompatible execution strategy, parent intent dispatch, production market-data dispatch into execution strategies, child execution dispatch using `OrderState.parentOrderId` before trading-strategy active gating, owner-routed timer dispatch by registered correlation ID, duplicate active timer correlation rejection without owner replacement, owner capacity failure before cluster scheduling, owner rollback after cluster scheduling failure, non-execution timer pass-through without unknown-counter noise, cancel dispatch, callback ordering, and counters.
- JMH proves execution engine dispatch allocation behavior.

## TASK-306 - Extend Configuration for Execution Strategy Selection

### Objective

Add `executionStrategy` and per-instrument/per-venue override validation to strategy configuration.

### Files to Update

```text
config/strategies.toml
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/*
platform-common/src/test/java/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-006
- AC-V13-EXEC-009
- Tests cover valid default, valid override, missing execution strategy using strategy-type default, unknown execution strategy, incompatible pair, duplicate override, empty ID, capacity config defaults, canonical ID validation, and clear startup errors.

## TASK-307 - Extend OrderManager Child Attribution

### Objective

Store `parentOrderId` in child `OrderState`, child snapshots, and child command handling.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/*
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/order/*
platform-gateway/src/test/java/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-004
- AC-V13-ALLOC-002A
- Tests cover create child with parent, child execution retains attribution, snapshot/load, terminal child release, duplicate exec report, unknown order, cancel all, and parent ID edge values.
- JMH proves child state transition allocation behavior remains within V12/V13 target.

## TASK-308 - Implement ImmediateLimitExecution

### Objective

Implement one-child IOC/limit execution strategy.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ImmediateLimitExecution.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-BUILTIN-001
- Tests cover accepted child, full fill, partial fill then terminal, reject, parent cancel while child working, child fill while parent cancel pending, risk reject before child command, timer no-op, capacity full, replay, and snapshot/load.
- JMH proves callback dispatch allocation behavior.

## TASK-309 - Implement PostOnlyQuoteExecution

### Objective

Implement post-only quote execution for market-making parents.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/PostOnlyQuoteExecution.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-BUILTIN-002
- Tests cover submit, refresh trigger, cancel/replace, post-only reject retry one tick deeper, retry exhaustion, fill callback, parent cancel, stale market data, timer during pending execution report, risk reject, capacity full, replay, and snapshot/load.
- JMH proves market-data and child-execution callback allocation behavior.

## TASK-310 - Implement MultiLegContingentExecution

### Objective

Implement contingent two-leg arbitrage execution and hedge lifecycle.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/MultiLegContingentExecution.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-BUILTIN-003
- Tests cover both legs filled, one leg rejected, one-leg fill imbalance hedge, hedge risk reject, hedge venue reject, hedge kill-switch escalation, leg timer, required timer schedule failure with no live child orders or active child links, late child execution after terminal parent, parent cancel, child fill during cancel, cooldown reason, replay, snapshot/load, and counters.
- JMH proves leg/timer/hedge callback allocation behavior.

## TASK-311 - Migrate MarketMakingStrategy to QuoteIntent

### Objective

Remove child-order encoding ownership from `MarketMakingStrategy` and submit quote parent intents.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingStrategy.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingStrategyTest.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-001
- AC-V13-EXEC-008
- AC-V13-DET-005
- Tests cover quote intent fields, fair price/spread/inventory math preservation, no direct child command encoding, no child encoder access through `StrategyContext`, parent fill callback, parent terminal callback, cooldown/staleness behavior transferred to execution strategy, V12-to-V13 behavior equivalence, replay, and allocation benchmark update.

## TASK-312 - Migrate ArbStrategy to MultiLegIntent

### Objective

Remove child-order/hedge lifecycle ownership from `ArbStrategy` and submit multi-leg parent intents.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategy.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategyTest.java
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-EXEC-001
- AC-V13-EXEC-008
- AC-V13-DET-005
- Tests cover edge detection, self-cross pre-check, intent fields, threshold preservation, no direct child command encoding, no child encoder access through `StrategyContext`, parent terminal cooldown reason, risk rejection ownership transfer, V12-to-V13 behavior equivalence, replay, and allocation benchmark update.

## TASK-313 - Parent Deterministic Replay Expansion

### Objective

Expand deterministic replay to include parent intents, execution strategies, parent timers, parent snapshots, and child attribution.

### Files to Update

```text
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/DeterministicReplayTest.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-DET-001 through AC-V13-DET-004
- AC-V13-DET-005
- Tests cover MarketMaking/PostOnlyQuote parent, Arb/MultiLeg parent, child rejects, risk rejects, hedge rejection, timer races, owner-routed parent timer replay, child execution parent-state replay while trading callbacks are inactive, snapshot/load, capacity counters, identical V13 outbound command sequences under replay, and V12-to-V13 behavior-equivalence fixtures.

## TASK-314 - Parent/Execution Benchmark Surface

### Objective

Add V13 execution hot paths to allocation policy, benchmark map, JMH, latency reports, and verification gates.

### Files to Update

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/AllocationPolicy.java
platform-benchmarks/src/jmh/java/*
platform-benchmarks/src/test/java/*
platform-benchmarks/README.md
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-ALLOC-001 through AC-V13-ALLOC-005
- Tests prove every V13 hot-path surface has a benchmark owner.
- JMH publishes GC-profiler allocation evidence and latency percentile evidence for execution engine, parent registry, and built-in strategies.

## TASK-315 - Parent Snapshot, Recovery, and Reconciliation Integration

### Objective

Integrate parent state with snapshot/load, recovery coordinator, reconciliation evidence, and operational counters.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/*
platform-cluster/src/test/java/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-DET-003
- Tests cover parent snapshot round trip, recovery after partially filled parent, recovery after hedge pending, reconciliation mismatch, kill-switch on unreconciled parent risk, and operational counters.

## TASK-316 - Live-Wire Parent Intent E2E Tests

### Objective

Prove parent intent flow through execution strategy, order manager, gateway, Coinbase simulator, execution report, and parent callback.

### Files to Update

```text
platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/*
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/*
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- AC-V13-TEST-003
- AC-V13-TEST-004
- Tests prove `MarketMakingStrategy -> QuoteIntent -> PostOnlyQuoteExecution -> OrderManager -> gateway -> Coinbase simulator -> execution report -> parent callback`.
- Tests prove `ArbStrategy -> MultiLegIntent -> MultiLegContingentExecution -> OrderManager -> gateway -> Coinbase simulator -> execution report -> hedge/cooldown callback`.
- Tests cover local simulator only and require no live Coinbase network connectivity.

## TASK-317 - Documentation and Runbook Update

### Objective

Update README, operational runbooks, and migration documentation for parent/execution strategy behavior.

### Files to Update

```text
README.md
config/*
scripts/*
NitroJEx_V12_to_V13_Migration.md
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- Docs explain parent intents, execution strategies, child attribution, configuration, non-goals, test gates, benchmark gates, rollback, and QA/UAT blockers.
- Runbooks cover parent stuck, child stuck, hedge rejected, post-only reject loop, capacity full, and rollback to V12.

## TASK-318 - Final V13 Release Gate

### Objective

Run and document the full V13 verification gate.

### Files to Update

```text
README.md
NitroJEx_V12_to_V13_Migration.md
release evidence documentation or scripts
```

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including automated coverage for all applicable behavior and documented justification for non-applicable categories.
- Full `clean`, `check`, `e2eTest`, `:platform-benchmarks:jmh`, and `:platform-benchmarks:jmhLatencyReport` pass.
- All V13 task-owned tests pass.
- Parent/execution JMH reports are archived.
- Deterministic replay evidence is archived.
- V12-to-V13 behavior-equivalence evidence is archived.
- Live-wire parent intent E2E evidence is archived.
- Real Coinbase QA/UAT remains blocked until V12 and V13 release evidence is complete.

---

# Section 4 - Required Verification Commands

Before V13 QA/UAT:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
```

The benchmark and latency output must be archived with the release evidence. Any non-zero V13 hot-path allocation must be fixed or explicitly reclassified before zero-GC claims are allowed.
