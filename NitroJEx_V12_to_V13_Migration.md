# NitroJEx V12 to V13 Migration

## 1. Overview

This document describes the differences between `NitroJEx_Master_Spec_V12.0.md` and `NitroJEx_Master_Spec_V13.0.md`, including compatibility expectations, internal breaking changes, validation requirements, and rollout strategy.

V12 is the frozen low-latency evidence baseline. V13 is the execution strategy release for the cluster strategy/execution layer: it splits trading intent from child-order execution while preserving V12 determinism, hot-path allocation rules, replay evidence, simulator live-wire gates, and production preflight.

V13 must preserve V12 external behavior for `MarketMakingStrategy` and `ArbStrategy` when configured with default execution strategies. Bit-for-bit SBE stream equivalence is not required because parent-order events are added; behavioral equivalence is required for risk decisions, aggregate fills, kill-switch state, parent outcomes, and documented counters.

## 2. Breaking Internal Changes

- Trading strategies stop directly encoding child order commands.
- Trading strategies lose hot-path access to child-order encoders through `StrategyContext` after migration completes.
- Trading strategies emit parent intents to an execution strategy engine.
- Parent order state becomes first-class deterministic cluster state.
- `OrderState` gains `parentOrderId` for child attribution.
- `NewOrderCommand` gains `parentOrderId`.
- Parent-to-active-child mappings are owned by `ParentOrderRegistry`; child-to-parent attribution is owned by `OrderState.parentOrderId`.
- Execution strategy plugins own child submission, cancel/replace sequencing, timer handling, child execution attribution, and parent terminal events.
- Strategy configuration must name an execution strategy or inherit a validated default.
- Deterministic replay expands to parent state, execution strategy callbacks, and parent timers.
- V13 hot-path benchmark surfaces expand to parent registry and execution strategy dispatch.

## 3. Preserved V12 Behavior

- V12 low-latency hot-path allocation policy remains.
- V12 benchmark and latency gates remain.
- V12 deterministic replay principles remain.
- FIX protocol plugin model remains.
- Venue plugin model remains.
- Coinbase venue plugin remains the first-class venue implementation.
- L2/L3 market-data normalizers remain unchanged except where tests need parent-flow integration.
- RiskEngine semantics remain unchanged.
- RiskEngine gates every child order.
- OrderManager remains the child-order lifecycle source of truth.
- OwnOrderOverlay and ExternalLiquidityView remain the strategy-facing liquidity safety surfaces.
- REST remains cold/side path.
- Real Coinbase QA/UAT remains blocked until automated local evidence passes.

## 4. New V13 Capabilities

- `ParentOrderIntent` SBE message for declarative trading intent.
- `ParentOrderUpdate` and `ParentOrderTerminal` SBE messages for deterministic parent reporting.
- `ParentOrderRegistry` for bounded parent lifecycle and parent-to-active-child mapping.
- `ExecutionStrategy` plugin contract.
- `ExecutionStrategyContext` with deterministic clock, timer, parent registry, market view, liquidity view, risk, order manager, command encoder, and counters.
- `ExecutionStrategyEngine` for registration, validation, production market-data dispatch, owner-routed timer routing, child execution routing, and parent callbacks.
- `ImmediateLimitExecution` for one-child simple order execution.
- `PostOnlyQuoteExecution` for market-making post-only quote lifecycle.
- `MultiLegContingentExecution` for arbitrage leg execution and imbalance hedge handling.
- Canonical built-in execution strategy IDs are `ImmediateLimit`, `PostOnlyQuote`, and `MultiLegContingent`.
- Parent-order deterministic replay and snapshot/load coverage.
- Parent-intent live-wire E2E coverage through the local Coinbase simulator.

## 5. Module Changes

| Module | Change |
|--------|--------|
| `platform-common` | Adds parent-order SBE messages, parent IDs on child commands/snapshots, config fields, and allocation policy surfaces. |
| `platform-cluster` | Adds parent registry, execution strategy engine, execution strategy plugins, parent replay, parent snapshots, and strategy migrations. |
| `platform-gateway` | Preserves parent IDs internally on child commands while keeping venue FIX semantics unchanged unless explicitly supported. |
| `platform-benchmarks` | Adds parent registry, execution engine, and built-in execution strategy JMH surfaces. |
| `platform-tooling` | Adds live-wire parent-intent E2E flows through the local Coinbase simulator. |
| `config` | Adds execution strategy selection and override configuration. |
| `scripts` | Extends release/preflight evidence commands where needed. |

## 6. Compatibility

| Compatibility Question | Answer |
|------------------------|--------|
| Are V12 spec and plan modified? | No. They remain frozen once V13 docs are created. |
| Does V13 change public trading strategy behavior? | Yes internally: strategies emit parent intents instead of direct child commands. External trading goals remain the same. |
| Does V13 change venue plugins? | No. Venue plugins remain responsible for venue behavior, not parent execution strategy logic. |
| Does V13 change FIX protocol plugins? | No. FIX protocol mechanics remain independent. |
| Does V13 change RiskEngine semantics? | No. Risk still gates every child order. |
| Does V13 add TWAP/VWAP/POV/peg/SOR? | No. Those are V14+ or separate future work. |
| Are V12 configs directly compatible? | They need deterministic defaults or migration for `executionStrategy`. Unknown IDs must fail startup validation. |
| What are the default execution strategies? | `MarketMaking -> PostOnlyQuote`, `Arb -> MultiLegContingent`, and generic one-shot parent intents -> `ImmediateLimit`. |
| Does parent state affect hot-path allocation claims? | Yes. Parent registry, parent dispatch, and execution strategy callbacks become declared V13 hot paths. |
| Can QA/UAT start before parent/execution benchmarks pass? | No. V12 and V13 gates must both pass. |

## 7. Rollout Strategy

1. Freeze and archive V12 artifacts:
   - `NitroJEx_Master_Spec_V12.0.md`
   - `nitrojex_implementation_plan_v3.0.0.md`
   - `NitroJEx_V11_to_V12_Migration.md`
2. Create V13 documentation baseline and README pointers.
3. Add parent SBE messages and child `parentOrderId`.
4. Implement bounded `ParentOrderRegistry`.
5. Add `ExecutionStrategy`, context, views, and engine.
6. Extend strategy configuration with execution strategy selection and overrides.
7. Extend child order attribution in `OrderManager`.
8. Implement `ImmediateLimitExecution`.
9. Implement `PostOnlyQuoteExecution`.
10. Implement `MultiLegContingentExecution`.
11. Migrate `MarketMakingStrategy` to emit quote parent intents; where practical, keep the old market-making child-order path behind a temporary migration flag until behavior-equivalence evidence passes, then remove direct child-encoder access for the migrated path.
12. Migrate `ArbStrategy` to emit multi-leg parent intents; where practical, keep the old arbitrage leg/hedge path behind a temporary migration flag until behavior-equivalence evidence passes, then remove direct child-encoder access for the migrated path.
13. Expand deterministic replay for parent state and execution strategy dispatch.
14. Add V12-to-V13 behavior-equivalence fixtures for default execution strategies.
15. Add V13 benchmark surfaces and latency reports.
16. Integrate parent snapshot/recovery/reconciliation.
17. Add live-wire parent-intent E2E tests.
18. Update documentation and runbooks.
19. Run the full V13 verification gate.
20. Begin real Coinbase QA/UAT only after all V12 and V13 automated gates pass.

## 8. Rollback Strategy

Rollback is artifact/config based:

- Keep V12 artifacts and binaries available.
- Keep V13 strategy config separate or clearly migrated from V12 strategy config.
- If parent registry or execution engine behavior is incorrect, revert to V12 binaries and V12 strategy config.
- During migration, use temporary feature flags only to compare old and new strategy behavior; remove residual direct-encoder access before V13 completion.
- If execution strategy benchmark evidence regresses, block the V13 zero-allocation claim and remediate before QA/UAT.
- If live-wire parent-intent E2E fails, do not use real Coinbase QA/UAT as a substitute.

Rollback procedure:

1. Stop V13 cluster and gateway processes.
2. Preserve V13 logs, parent snapshots, parent registry counters, child
   `OrderState.parentOrderId` evidence, and simulator/live-wire failures.
3. Reconcile all live venue orders and balances. Cancel or flatten externally if
   any child order cannot be attributed to a valid parent.
4. Restore frozen V12 binaries and V12 strategy configuration.
5. Run the V12 preflight gate before resuming any strategy traffic.

Rollback is not a way to hide unreconciled parent/child risk. If a V13 parent is
stuck, hedging, or missing child attribution, the kill switch remains active
until reconciliation is complete.

## 9. Required Testing Standard

V13 requires full expected-result coverage, not just compile-level coverage. For each changed behavior, task-owned tests must cover:

```text
positive behavior
negative behavior
edge values
malformed or invalid inputs
capacity full behavior
safe reject / safe drop / terminal reason codes
parent cancel races
child execution races
timer races
risk rejects
child venue rejects
snapshot/load
deterministic replay
integration across strategy, execution, order, gateway, FIX, and simulator
allocation benchmarks
latency reports where dispatch latency matters
V12-to-V13 behavior-equivalence fixtures for migrated strategies
```

Required execution-specific cases:

```text
parent canceled mid-flight while child is working
child rejected with parent leftover
child filled while parent cancel is in flight
timer firing during pending execution report
post-only reject one-tick-deeper retry
multi-leg one-leg-fill imbalance hedge
hedge rejected by risk
hedge rejected by venue
hedge rejection triggers kill switch
parent-state replay determinism
parent snapshot/load round trip
capacity-full parent pool
capacity-full parent-to-child mapping
unknown execution strategy startup failure
incompatible trading/execution strategy startup failure
```

`OrderState.parentOrderId` is the authoritative hot-path child-to-parent attribution field. `ParentOrderRegistry` owns parent state and parent-to-active-child lists; a general-purpose child-to-parent map is not allowed on the hot path.

## 10. Pre-QA/UAT Gate

Real Coinbase QA/UAT is blocked until all of the following pass:

```text
V12 unit, integration, replay, simulator, live-wire, benchmark, and preflight evidence
V13 parent/execution unit tests
V13 parent/execution integration tests
V13 deterministic replay tests
V13 parent snapshot/load tests
V13 parent-intent live-wire E2E tests
V13 JMH allocation benchmarks
V13 latency histogram / percentile evidence
V12-to-V13 behavior-equivalence evidence
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

The benchmark and latency output must be archived with release evidence. Any non-zero V13 hot-path allocation must be fixed or explicitly reclassified before zero-allocation readiness claims are allowed.

Release evidence location:

```text
release-evidence/v13/README.md
release-evidence/v13/jmh-allocation-results.json
release-evidence/v13/jmh-latency-results.json
release-evidence/v13/v12-to-v13-behavior-equivalence-test.xml
release-evidence/v13/deterministic-replay-test.xml
release-evidence/v13/v13-parent-intent-live-wire-e2e.xml
release-evidence/v13/test-results/platform-cluster-test/
release-evidence/v13/test-results/platform-tooling-e2eTest/
```

## 11. Operational Runbooks

### Parent Stuck

Evidence to collect:

```text
parentOrderId
parent status and terminalReasonCode
active child IDs from ParentOrderRegistry
matching OrderState.parentOrderId values
parent timer correlation ID
registered execution timer owner
last parent transition cluster time
```

If a child is live, cancel the child before forcing the parent terminal. If no
child is live and no timer can complete the parent, terminate the parent as
`EXECUTION_ABORTED` and record the counter evidence. If child attribution is
missing or contradictory, activate the kill switch.

### Execution Dispatch Wiring

Production dispatch must preserve this order:

```text
market data: InternalMarketView updates first, then active ExecutionStrategyEngine market-data callback, then active trading strategies
child execution: OrderManager updates child state, portfolio/risk fill state updates, ExecutionStrategyEngine updates parent state, then active trading strategies receive fill callbacks
timer: ExecutionStrategyEngine routes by registered timer owner, then trading strategies receive timer callbacks
```

Child execution parent-state updates are not gated by trading-strategy active
state. Market-data-driven execution actions remain active-leader gated because
they can create child command output. Parent timer correlation IDs must be
registered with the owning execution strategy before cluster scheduling is
attempted. Active duplicate timer correlation IDs must be rejected without
replacing the current owner. If scheduling fails, the owner registration must be
removed. If a required parent timer cannot be registered or scheduled, terminate
the parent deterministically, leave no live child orders or active child links,
and record counter evidence. Broad timer fan-out is not a production dispatch
mode, and unrelated cluster timers must not pollute execution unknown-timer
counters.

### Child Stuck

Use `OrderState.parentOrderId` as the source of truth. If the parent registry has
no matching active parent, the child is unreconciled risk. Keep the kill switch
active, cancel the venue child if live, and reconcile balances before clearing
the condition.

### Hedge Rejected

For `MultiLegContingentExecution`, any hedge risk reject or hedge venue reject
is terminal parent failure. Required action is kill switch activation, venue
order-status reconciliation, balance reconciliation, and `ArbStrategy` cooldown
from the parent terminal callback.

### Post-Only Reject Loop

`PostOnlyQuoteExecution` performs one deterministic one-tick-deeper retry. More
than one retry is intentionally out of scope for V13. If rejects continue, leave
the parent failed, preserve reject evidence, and let market-making suppression or
operator intervention decide when quoting resumes.

### Capacity Full

Parent registry capacity, parent-child link capacity, and order-state pool
capacity are hard deterministic bounds. Capacity full is a release blocker for
the affected configuration unless the limit is intentionally raised and all
tests, replay, snapshot/recovery, and JMH gates are rerun.

### Rollback To V12

Rollback requires V12 binaries, V12 config, order/balance reconciliation, and
V12 preflight evidence. Real Coinbase QA/UAT must not start from a partially
rolled-back or unreconciled V13 parent state.

## 12. Configuration Notes

V13 strategy configuration must select an execution strategy explicitly or rely
on validated defaults:

```text
MarketMaking -> PostOnlyQuote
Arb          -> MultiLegContingent
one-shot     -> ImmediateLimit
```

Unknown execution strategy IDs and incompatible trading/execution pairs fail at
startup. Per-instrument or per-venue overrides are cold-path configuration and
must not enter the hot path as map lookups.

## 13. Traceability

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

V13 task IDs begin at:

```text
TASK-301
```

No V13 task may reuse a V10, V11, or V12 task ID.
