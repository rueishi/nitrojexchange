# NitroJEx Master Specification V13.0

## Status

Active Development - Supersedes V12.0 for the cluster strategy/execution layer only

## Based On

`NitroJEx_Master_Spec_V12.0.md` (frozen low-latency evidence baseline)

## Implementation Plan

`nitrojex_implementation_plan_v4.0.0.md`

## Key Enhancements

- First-class execution strategy layer between trading strategies and child order management.
- Trading strategies emit declarative parent intents; execution strategies own child-order lifecycle.
- V13 preserves V12 external strategy behavior for `MarketMakingStrategy` and `ArbStrategy` when configured with default execution strategies.
- Parent-order state, parent-to-child mapping, and parent terminal reporting become deterministic cluster state.
- Built-in execution strategies for immediate limit, post-only quote management, and contingent multi-leg arbitrage execution.
- V12 deterministic replay, hot-path allocation policy, own-order overlay, external-liquidity view, risk gates, and testing pyramid remain mandatory.
- Full task-owned positive, negative, edge, malformed, capacity, failure, replay, integration, allocation, and latency coverage for every V13 behavior.

---

# 1. Versioning and Baseline Rules

## 1.1 Frozen Baselines

The following files are immutable historical artifacts:

```text
NitroJEx_Master_Spec_V10.0.md
nitrojex_implementation_plan_v1.4.0.md
NitroJEx_Master_Spec_V11.0.md
nitrojex_implementation_plan_v2.0.0.md
NitroJEx_Master_Spec_V12.0.md
nitrojex_implementation_plan_v3.0.0.md
NitroJEx_V10_to_V11_Migration.md
NitroJEx_V11_to_V12_Migration.md
```

They must not be edited for V13 work except for explicit archival corrections approved separately. V12 is the frozen low-latency, deterministic replay, benchmark evidence, simulator live-wire, REST-boundary, and production-preflight baseline.

## 1.2 V13 Scope

V13 adds an execution strategy layer. The purpose is architectural separation:

```text
Trading strategy = what position or quote intent the system wants.
Execution strategy = how child orders are worked to realize that intent.
OrderManager = authoritative child-order lifecycle state.
RiskEngine = mandatory pre-trade gate for every child order.
```

V13 is a cluster strategy/execution-layer architectural change. It does not add new venues, new FIX plugins, new market-data depths, new transports, or RiskEngine semantic changes.

V13 must not broaden venue scope or introduce advanced execution algorithms beyond the built-in strategies explicitly listed in this spec.

V13 must preserve V12 external behavior of `MarketMakingStrategy` and `ArbStrategy` when the same ordered SBE event stream, equivalent simulator behavior, and default execution strategies are used. Bit-for-bit SBE stream equivalence between V12 and V13 is not required because parent-order events are added. Behavioral equivalence is required for risk decisions, aggregate fills, parent outcomes, kill-switch state, and documented counters.

## 1.3 Task Numbering

V13 implementation tasks must not reuse V10, V11, or V12 task IDs. V13 task cards start at:

```text
TASK-301
```

---

# 2. Professional Claim

Before V13 evidence is complete, the allowed claim remains:

```text
NitroJEx has a completed V12 low-latency evidence baseline and is adding a
deterministic execution strategy layer that splits trading intent from child
order execution.
```

After V13 evidence is complete, the allowed claim becomes:

```text
NitroJEx supports deterministic parent-order execution strategies: trading
strategies emit parent intents, execution strategies work bounded child-order
lifecycle state, and replay proves identical parent state, child command
sequences, and outbound FIX behavior under documented capacity limits.
```

No V13 claim may state that advanced TWAP, VWAP, POV, peg, or smart-order-routing algorithms are implemented unless a later release adds them with their own evidence.

---

# 3. Architecture Split

## 3.1 Trading Intent Layer

Trading strategies remain cluster-thread deterministic plugins. Their V13 responsibility is to compute desired intent from market, inventory, risk, and strategy configuration state.

They must not:

```text
allocate child order IDs directly
encode child order SBE commands directly
access child-order encoders through StrategyContext
own post-only retry mechanics
own cancel/replace timing
own arbitrage leg sequencing
own imbalance hedge submission
own hedge-rejection kill-switch escalation
```

They may retain:

```text
market-making fair price and spread calculation
inventory skew and quote sizing
arbitrage edge detection
self-cross pre-checks before emitting intent
parent-level cooldown decisions
parent-fill notification handling
strategy-specific intent throttling
```

## 3.2 Execution Layer

Execution strategies run on the cluster thread. They receive parent intents and translate them into child order commands through `OrderManager` and the existing gateway egress path.

Execution strategies own:

```text
parent lifecycle transitions
child order submission sequencing
child cancel/replace sequencing
child execution attribution
parent timers
post-only reject handling
partial-fill leftover behavior
multi-leg imbalance hedging
parent terminal reason codes
parent callbacks to trading strategies
```

## 3.3 Child Order Layer

`OrderManager` remains the child-order source of truth. V13 extends child state with a `parentOrderId` field so child executions can be attributed to parent state without a side lookup.

`OrderManager` must still own:

```text
child OrderState
child state-machine transitions
duplicate execution detection
cancel fan-out for child orders
child snapshot/load
child own-order overlay projection
```

---

# 4. New Abstractions

## 4.1 ParentOrderIntent

`ParentOrderIntent` is a compact SBE message and cluster-thread command surface expressing what a trading strategy wants:

```text
parentOrderId
strategyId
executionStrategyId
intentType
side
instrumentId
primaryVenueId
venueSetId or bounded venue list reference
quantityScaled
limitPriceScaled or referencePriceScaled
timeInForcePreference
urgencyHint
postOnlyPreference
selfTradePolicy
correlationId
```

Intent objects must not be heap allocated on the hot path. Implementations must use SBE flyweights, bounded preallocated slots, primitive structs, or reusable command buffers.

## 4.2 ParentOrderState

`ParentOrderState` is cluster-side deterministic state with this lifecycle:

```text
PENDING
WORKING
PARTIALLY_FILLED
HEDGING
CANCEL_PENDING
DONE
FAILED
CANCELED
EXPIRED
```

State must include:

```text
parentOrderId
strategyId
executionStrategyId
intent fields required for replay
requestedQtyScaled
filledQtyScaled
remainingQtyScaled
averageFillPriceScaled
terminalReasonCode
active child order IDs
last transition cluster time
timer correlation IDs
```

## 4.3 ParentOrderRegistry

`ParentOrderRegistry` is a sibling of `OrderManager`. It owns parent state and parent-to-active-child lists.

`OrderState.parentOrderId` is the authoritative child-to-parent attribution field on the hot path. Hot-path child-to-parent demultiplexing must not require a general-purpose auxiliary map.

It must provide:

```text
claim parent state from bounded pool
lookup by parentOrderId
link child clOrdId to parentOrderId
unlink child order on terminal state
find active child IDs by parentOrderId
snapshot parent state and mappings
load parent state and mappings
capacity counters for parent and child-link exhaustion
deterministic terminal reason codes
```

## 4.4 ExecutionStrategy

Execution strategy plugin contract:

```java
void init(ExecutionStrategyContext ctx);
void onParentIntent(ParentOrderIntentView intent);
void onMarketDataTick(int venueId, int instrumentId, long clusterTimeMicros);
void onChildExecution(ChildExecutionView execution);
void onTimer(long correlationId);
void onCancel(long parentOrderId, byte reasonCode);
```

Views must be flyweight or primitive-backed and must not allocate on dispatch.

## 4.5 ExecutionStrategyContext

The context exposes:

```text
InternalMarketView
ExternalLiquidityView
OwnOrderOverlay
RiskEngine
OrderManager
ParentOrderRegistry
child-order command encoder
deterministic cluster clock
timer scheduler
counters
ID registry
```

Execution strategies must use the context clock and ordered timer scheduler. Wall-clock callbacks are forbidden in deterministic parent state.

Trading-strategy `StrategyContext` no longer exposes child-order encoders to trading strategies after the V13 migration completes. Child-order encoding belongs to execution strategies through `ExecutionStrategyContext`; recovery or administrative cold paths must use a separate non-strategy surface.

## 4.6 ExecutionStrategyEngine

The engine owns registration and dispatch:

```text
register execution strategy plugins
validate executionStrategyId at startup
route ParentOrderIntent to selected execution strategy
route child execution reports to owning parent/execution strategy
route market-data ticks to active execution strategies
route ordered timer events to parent/execution strategy owners
emit ParentOrderUpdate and ParentOrderTerminal messages
dispatch parent callbacks to trading strategies in deterministic order
```

Production routing is mandatory, not test-harness-only. `MessageRouter` must pass
market-data ticks, child execution reports with `OrderState.parentOrderId`, and
ordered timer events into the execution layer. Parent/execution state mutation
from child execution reports must occur before trading-strategy active/leader
callback gating so replay, follower catch-up, and failover reconstruct the same
parent state. Market-data-driven execution actions remain active-leader gated
because they may create child command output.

Unknown execution strategy IDs must fail startup/config validation, not first hot-path use.

Canonical built-in execution strategy IDs:

```text
ImmediateLimit
PostOnlyQuote
MultiLegContingent
```

Default compatibility matrix:

```text
MarketMaking -> PostOnlyQuote
Arb          -> MultiLegContingent
Generic one-shot parent intents -> ImmediateLimit
```

Unsupported trading-strategy / execution-strategy pairings must fail startup validation with clear errors.

---

# 5. Built-In Execution Strategies

## 5.1 ImmediateLimitExecution

Default simple execution strategy.

Behavior:

```text
submits one IOC or limit child order
links child to parent
transitions parent WORKING after child command accepted
marks parent DONE on full terminal fill
marks parent CANCELED or FAILED on terminal non-fill according to reason code
handles parent cancel while child is working
```

## 5.2 PostOnlyQuoteExecution

Pairs with `MarketMakingStrategy`.

Behavior:

```text
submits post-only limit child order
watches market data for refresh triggers
handles cancel/replace lifecycle
handles post-only reject with one-tick-deeper retry
expires parent when quote staleness threshold is exceeded
reports parent fills back to MarketMakingStrategy
```

## 5.3 MultiLegContingentExecution

Pairs with `ArbStrategy`.

Behavior:

```text
submits two IOC legs
runs leg-completion timer as ordered cluster timer event
tracks partial one-leg-fill imbalance
submits hedge child order for leftover exposure
escalates hedge rejection to kill switch
reports parent failure reason for strategy cooldown
reports parent done only after balanced terminal outcome
```

---

# 6. Existing Class Changes

## 6.1 MarketMakingStrategy

`MarketMakingStrategy` stops encoding child-order commands. It computes fair price, spread, inventory skew, and quote sizes as today, then emits `QuoteIntent`, a specialization of `ParentOrderIntent`.

It removes:

```text
child order ID ownership
post-only retry ownership
cancel/replace timing ownership
staleness-driven child cancel ownership
direct SBE child-order command encoding
```

It adds:

```text
executionEngine() submission
parent fill callback
parent terminal callback for quote refresh and inventory bookkeeping
```

## 6.2 ArbStrategy

`ArbStrategy` stops encoding child-order commands. It detects edge using `ExternalLiquidityView`, performs self-cross pre-checks, and emits `MultiLegIntent`.

It removes:

```text
leg sequencing
leg timeout ownership
imbalance hedge submission
hedge rejection kill-switch escalation
direct child-order command encoding
```

It retains:

```text
opportunity detection
threshold checks
cooldown bookkeeping
parent-level terminal reason handling
```

## 6.3 StrategyContext

`StrategyContext` gains:

```java
ExecutionStrategyEngine executionEngine();
```

Trading strategies submit parent intents through this accessor.

## 6.4 OrderManager and OrderState

`OrderState` gains:

```text
parentOrderId
```

`NewOrderCommand` gains:

```text
parentOrderId
```

Gateway order-entry code must preserve compatibility for the field and must not send parent IDs to venue FIX unless a venue explicitly supports such metadata.

## 6.5 StrategyEngine

`StrategyEngine` adds a parallel registration path for execution strategy plugins. Trading strategy registration and execution strategy registration are distinct.

`StrategyEngine` must preserve the layer boundary during production dispatch:

```text
market data: active leader routes to ExecutionStrategyEngine, then trading strategies
child execution: OrderManager updates child state, ExecutionStrategyEngine updates parent state, then active trading strategies receive fill callbacks
timer: ExecutionStrategyEngine receives owner-routed timer events by correlation ID, then trading strategies receive timer callbacks
```

Child execution routing into `ExecutionStrategyEngine` is not gated by trading
strategy active state. Trading-strategy callbacks that can generate new intent
remain active-gated.

---

# 7. Configuration

`config/strategies.toml` gains `executionStrategy` per strategy instance:

```toml
[[strategy]]
id = "mm-btc-coinbase"
type = "MarketMaking"
executionStrategy = "PostOnlyQuote"
```

Overrides are supported:

```toml
[[strategy.override]]
instrument = "BTC-USD"
venue = "COINBASE"
executionStrategy = "PostOnlyQuote"
```

Startup validation must prove:

```text
configured execution strategy ID exists
strategy type is compatible with execution strategy type
required parent capacity is positive
required child-link capacity is positive
timer correlation range does not overlap other cluster components
```

If `executionStrategy` is omitted, the strategy type default is used:

```text
MarketMaking -> PostOnlyQuote
Arb          -> MultiLegContingent
```

Execution-strategy IDs and compatibility metadata are config-time data and must not require String comparisons on the hot path.

---

# 8. SBE Schema Changes

Add:

```text
ParentOrderIntent
ParentOrderUpdate
ParentOrderTerminal
```

Extend:

```text
NewOrderCommand.parentOrderId
OrderStateSnapshot.parentOrderId
```

No existing message semantics may change. Existing consumers that ignore parent fields must continue to behave as V12 child-order consumers.

---

# 9. Determinism Preservation

Parent timers must enter the cluster thread as ordered internal timer events. Execution strategies must use `ctx.clock()` and the cluster timer scheduler for all time queries. Parent timer correlation IDs must be registered to their owning execution strategy before cluster timer scheduling is attempted. Active duplicate timer correlation IDs are invalid and must be rejected without replacing the existing owner. If cluster scheduling fails, the owner registration must be rolled back. If owner registration or scheduling fails for a required parent timer, the parent must terminate deterministically with a primitive failure reason and must not leave live child orders or active parent-child links behind. Production timer dispatch must route by owner. Broad timer fan-out is allowed only in isolated tests that explicitly verify no production dependency on fan-out, and non-execution cluster timers must not increment execution unknown-timer counters.

Replay tests must prove that the same ordered SBE stream plus the same initial state produces identical:

```text
parent state transitions
parent terminal reason codes
parent-to-child mappings
child order command sequence
child order state transitions
risk decisions
strategy callbacks
timer effects
outbound FIX command bytes or equivalent decoded command summaries
counters
snapshot/load state
```

Live networking, wall-clock time, REST timing, operator logging, and simulator scheduling must not enter deterministic parent state except through ordered internal events.

---

# 10. Hot-Path Policy

V13 adds these hot paths to the V12 list:

```text
ParentOrderIntent dispatch
ParentOrderRegistry claim/update/query
parent-to-child lookup
ExecutionStrategyEngine dispatch
ImmediateLimitExecution tick/callback
PostOnlyQuoteExecution market-data and child-execution callbacks
MultiLegContingentExecution leg, timer, and hedge callbacks
ParentOrderUpdate / ParentOrderTerminal encoding
NewOrderCommand parentOrderId encoding
```

The following are forbidden on normal V13 hot paths:

```text
heap allocation for parent intents or parent state
String conversion for strategy/execution IDs
HashMap/LinkedHashMap/List mutation for parent-to-child mappings
wall-clock reads outside context clock
exception construction for expected parent lifecycle outcomes
formatted logging for expected rejects, cancels, or safe drops
```

Expected failures must use primitive status codes, reason codes, counters, and deterministic terminal messages.

---

# 11. Test Coverage and QA/UAT Gate

V13 must keep the V12 coverage standard and strengthen it for parent/child lifecycle behavior.

Every V13 production task must include task-owned tests covering:

```text
positive behavior
negative behavior
edge and boundary values
malformed or invalid input
capacity full behavior
safe-drop / reject / status-code / counter behavior
snapshot/load/recovery
deterministic replay
integration across strategy, execution engine, parent registry, order manager, gateway, FIX, and simulator
allocation benchmark coverage
latency percentile evidence for latency-sensitive dispatch paths
V12-to-V13 behavior equivalence where existing strategy behavior is refactored
documentation of non-applicable categories with exact reason
```

Specific V13 cases that must be automated:

```text
parent canceled mid-flight while child is working
child rejected with parent leftover
child filled while parent cancel is in flight
production market-data tick reaches the owning execution strategy before trading strategy callback
child execution updates parent state even when trading-strategy callbacks are inactive
parent timer dispatch routes only to registered execution-strategy owner
parent timer owner capacity failure does not schedule an unowned cluster timer
duplicate active parent timer correlation does not replace the existing owner
parent timer cluster scheduling failure removes the owner registration
required parent timer scheduling failure terminates parent deterministically
required parent timer scheduling failure leaves no live child order or active child link
timer firing during pending execution report
post-only reject one-tick-deeper retry
multi-leg one-leg-fill imbalance hedge
hedge rejected by risk
hedge rejection triggers kill switch
parent-state replay determinism
parent snapshot/load round trip
capacity-full parent pool
capacity-full parent-to-child mapping
unknown execution strategy ID startup failure
execution strategy compatibility validation
```

Live-wire E2E must prove:

```text
MarketMakingStrategy -> QuoteIntent -> PostOnlyQuoteExecution -> OrderManager -> gateway -> Coinbase simulator -> execution report -> parent callback
ArbStrategy -> MultiLegIntent -> MultiLegContingentExecution -> OrderManager -> gateway -> Coinbase simulator -> execution report -> hedge/cooldown callback
```

No V13 production-code task is complete with only happy-path tests. If a category cannot be automated, the task must document owner, manual command, artifact path, and why automation is not practical.

Behavior-equivalence tests must compare V12 baseline scenario fixtures with V13 default execution strategies and assert functionally equivalent risk decisions, aggregate fills, kill-switch activations, parent outcomes, and documented counters. They must not require identical child client order IDs or identical byte-for-byte SBE streams.

---

# 12. Explicit Non-Goals

V13 does not introduce:

```text
TWAP
VWAP
POV
pegged order algorithms
smart order routing
new venue plugins
new FIX protocol plugins
market-data normalizer changes unrelated to parent execution evidence
RiskEngine semantic changes
direct child-order encoding from trading strategies after migration completes
```

Risk still gates every child order before submission.

---

# 13. Release Gate

Before V13 QA/UAT or production connectivity claims:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
```

The V13 evidence bundle must archive:

```text
unit and integration reports
deterministic replay reports
parent snapshot/load tests
Coinbase simulator live-wire reports
JMH allocation reports with gc profiler
latency percentile reports
security/operations/financial preflight evidence inherited from V12
V12-to-V13 behavior-equivalence reports
```
