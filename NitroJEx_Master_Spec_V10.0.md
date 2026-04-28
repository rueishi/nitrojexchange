# NitroJEx — High-Frequency Liquidity and Arbitrage Infrastructure
## A Multi-Venue, Multi-Strategy Pluggable Prop-Stack

> **NitroJEx** is a low-latency, modular execution stack purpose-built for multi-venue market making and statistical arbitrage. It abstracts exchange-specific complexities into a unified, pluggable framework, allowing for sub-microsecond risk-checks and automated liquidity provision across fragmented markets.

---
## Version 10.0 — Test Catalog Consolidation (Path B: §D.2/§D.3/§D.4 by tier)

**Classification:** Internal — Implementation Reference  
**Target Language:** Java 21 (LTS)  
**FIX Engine:** Artio 0.175 (Apache 2.0)  
**Transport:** Aeron 1.50.0 IPC + Aeron Cluster (Apache 2.0)  
**Collections / Off-Heap:** Agrona 2.4.0 (Apache 2.0)  
**Serialization:** Simple Binary Encoding — SBE 1.37.1 (Apache 2.0)  
**TOML Parsing:** night-config 3.6.7 (replaces toml4j — see §21.1)  
**GC:** Azul Platform Prime (C4) — production; OpenJDK ZGC — development  
**Status:** Final — Implementation Ready  
**Package prefix:** `ig.rueishi.nitroj.exchange` is used throughout this spec for readability. Before committing the first package declaration, replace it with a specific reverse-domain prefix (e.g. `io.nitroj` or `com.yourfirm.nitroj`). The generic prefix will collide in any shared Maven/Gradle repository or multi-project monorepo.

### Changelog

| Version | Key Changes |
|---|---|
| v1.0.0 | Initial specification — QuickFIX/J, Disruptor throughout |
| v2.0.0 | Artio replaces QuickFIX/J; SBE schemas; int IDs on hot path; gateway/cluster split |
| v2.1.0 | Supplement: build config, config schema, missing SBE messages, pool, StrategyContext |
| v3.0.0 | Implementation plan, task cards, CoinbaseExchangeSimulator, test strategy, ACs |
| v4.0.0 | Review remediation: 14 task card gaps fixed, 8 missing test classes, 19 new ACs |
| v5.0.0 | Overflow correction: ScaledMath utility, corrected Section 11.3/11.4/11.5, T-024 |
| v6.0.0 | Supplement sections 22/24/25/28/29/30/31/33/35 merged into master spec; cross-refs updated |
| v6.0 | Three-perspective review: 3 critical + 9 major + 8 moderate + 4 minor findings resolved |
| v7.0 | Full review remediation: 3 critical + 5 major + 5 moderate + 4 minor fixes. New Section 27: JIT Optimization Guidelines (Azul ReadyNow, BackgroundCompilation, PrintCompilation, compile command files). |
| v8.0 | 7 ambiguity fixes: AMB-001 duplicate `StrategyContextImpl` field removed; AMB-002 `WarmupConfig` unified to 3-param class with 100K production iterations; AMB-003 T-009/ET-001 ownership corrected to TASK-030 only; AMB-004 `resetArbState()` extracted from inside `onFill()` to proper private method; AMB-005 `minQuoteSizeFractionBps` added to `MarketMakingConfig` record and TOML; AMB-006 `MessageRouter` sealed-class example marked illustrative (integer switch in §3.4.3 is authoritative); AMB-007 `AdminClient` nonce strategy unified to file-persistence with wall-clock bootstrap. |
| v9.0 | Dependency refresh + 2 additional ambiguity resolutions + Coinbase tag 8013 implementation path committed. §21.1 dependency table updated to current Maven-Central-verified versions: Aeron `1.50.0`, Artio `0.175`, Agrona `2.4.0` (major version bump from 1.x), SBE `1.37.1`. §21.1/§21.2 TOML parser changed from `toml4j:0.7.2` (unmaintained since 2019) to `com.electronwill.night-config:toml:3.6.7`. §14.4 GatewayDisruptor backpressure wording reconciled with AC-GW-004: market-data ring-full drops the tick and increments `DISRUPTOR_FULL_COUNTER`; fill-path retains indefinite spin per §13.3 (AMB-008 resolved). §6.1.3 MarketMaking `adjustedFairValue` now derived from `fairValue` rather than `midPrice`, preserving consistency under the wide-spread fallback branch (AMB-009 resolved). §9.8 Coinbase logon tag 8013 (`CancelOrdersOnDisconnect="Y"`) implementation path committed to dictionary-driven code generation (see §9.8.3). |
| v9.1 | IT-005 `SnapshotRoundTripIntegrationTest` ownership corrected from TASK-034 to TASK-022. TASK-034 now owns its own `FIXReplayToolTest` covering replay-tool-specific behaviour. This alignment matches the plan and eliminates the spec/plan contradiction that existed in V9. |
| v9.2 | Codex-safety micro-patch. No logic changes. (1) §19.6 `buildClusteredService()` code block had `...` placeholder arguments; the plan's TASK-027 is now the authoritative wiring reference. (2) §26.2 `WarmupHarness` concrete class accompanied by an explicit interface extraction note clarifying that the plan splits §26.2's class into a `WarmupHarness` interface (TASK-016 owned) plus a `WarmupHarnessImpl` (TASK-033 owned). |
| v9.3 | WarmupHarness unification + §25.3 bracing repair. Three prior spec sections (§18.5, §25.3, §26.2) showed three incompatible `WarmupHarness` class shapes — different method names, different parameter types, different constructor signatures. V9.3 unifies them against the plan's interface-driven model. Specifically: (1) §26.2 is now the single authoritative definition; the class is renamed `WarmupHarnessImpl` and its public method signature is aligned to the plan's `WarmupHarness` interface as `runGatewayWarmup(FixLibrary)`; `TradingClusteredService` and `WarmupConfig` are injected via the constructor. (2) §25.3's stale two-argument `warmup.runGatewayWarmup(disruptor, clusterClient)` call is replaced with the injection-based one-argument pattern. (3) §25.3's broken Java bracing (nested `buildIngressEndpoints` method inside `main()` with an extra closing brace that prematurely ended `main()`) is repaired — `buildIngressEndpoints` is now a sibling method and the `main()` body is fully enclosed. (4) §18.5 no longer shows a redundant `WarmupHarness` sketch; it forward-references §26.2 as authoritative. (5) TASK-033's "Files to create" entry now lists `WarmupHarnessImpl.java`. No AC changes, no dependency-version changes. |
| v9.4 | Cross-reference correction in Part B TASK-033. Two lines in TASK-033's part-B card pointed at "Section 27.5" when they should have pointed at "Section 27.6" (the authoritative `WarmupConfig` section). §27.5 is "Compile Command Files" and §27.6 is "Verifying C2 Compilation — Updated WarmupConfig". The section-number typo existed in v9.3 and earlier and would have caused cross-referencing developers to open the wrong subsection. No code or AC changes. |
| v9.5 | Warmup-chain straggler sweep. V9.3 unified the `WarmupHarnessImpl` class name and `runGatewayWarmup(FixLibrary)` method signature across §18.5, §25.3, §26.2, and TASK-033. V9.5 catches three references that escaped the V9.3 sweep: (1) the T-021 `warmup_c2Verified_avgIterationBelowThreshold()` test example in §27.6 still called `harness.warmup(service, config)` — updated to `harness.runGatewayWarmup(fixLibrary)`; (2) §22 ConfigManager's file listing pointed `WarmupConfig.java` at "Section 18.5" — updated to Section 27.6 (the authoritative WarmupConfig definition); (3) §27.1's prose description of runtime behaviour used the generic name `WarmupHarness` where it should use the concrete class name `WarmupHarnessImpl`. Plus a documented full-text sweep of the spec and plan for remaining "WarmupHarness" and "warmup(" patterns; items intentionally left unchanged (section-title labels at §13258, §21.3 module tree with annotation, plan topic labels) are called out in this changelog. No code logic, no AC, no dependency-version changes. |
| v9.6 | `WarmupHarnessImpl.config()` accessor added. The V9.5 T-021 test snippet called `harness.config().iterations()` to compute average iteration latency, but the `WarmupHarnessImpl` class definition in §26.2 did not expose a `config()` accessor — only a private `config` field plus the constructor. The test would not have compiled. V9.6 adds a trivial public accessor `public WarmupConfig config() { return config; }` to `WarmupHarnessImpl` in §26.2 so the T-021 snippet matches the class surface. Pure addition — no signature changes, no renames, no AC changes. Regression introduced in V9.5; caught by review; fixed here. |
| v9.7 | IdRegistry session-registration separation of concerns. V9.6 and earlier had `IdRegistryImpl.init(List<VenueConfig>, List<InstrumentConfig>)` reading `v.senderCompId()` and `v.targetCompId()` from each `VenueConfig` to populate the `venueBySessionId` map. But spec §22.2 `venues.toml` never carried those fields — they live in gateway-X.toml under `[fix]` per §22.5, because FIX session identity is tied to the API key (per-gateway config), not venue identity. V9.7 fixes this architecturally: `IdRegistryImpl.init(...)` no longer reads session-identity fields; a new `registerSession(int venueId, long sessionId)` method is added to both the `IdRegistry` interface (§17.7) and implementation (§17.8) — called by the gateway when Artio provides the live `Session.id()` value; cluster-side code never calls it. |
| v9.8 | Part B appendix realigned with V9.7 architecture. V9.7 updated the architectural sections (§17.7 `IdRegistry` interface, §17.8 `IdRegistryImpl` implementation, §22 config records, §G.4 test factories) but the Part B task-card appendix was not migrated and silently contradicted the architectural truth. V9.8 migrates three specific Part B task cards: Part B TASK-004 file listing changed from `ig.rueishi.nitroj.exchange.config/` to `ig.rueishi.nitroj.exchange.common/` matching the authoritative §22 listing; Part B TASK-005 test list extended with the three `registerSession()` cases introduced in V9.7 and explicit "Gateway must call registerSession() after init()" wiring note; Part B TASK-005 "Tests to implement" total count updated from 9 to 12. Pure editorial realignment. |
| v9.9 | `Strategy` interface de-sealed. Removed `sealed` + `permits MarketMakingStrategy, ArbStrategy` clause from §17.1 `Strategy` interface. Reason: the `permits` clause created a compile-time circular dependency between `Strategy.java` (TASK-029) and `MarketMakingStrategy.java` (TASK-030) + `ArbStrategy.java` (TASK-031), forcing those three files to be committed atomically. A full-text grep confirmed zero exhaustive switches on `Strategy` subtypes exist anywhere in the spec or plan. Plan v1.3.15 uses this to move TASK-027 (ClusterMain) to Week 9 cleanly. |
| v9.10 | De-seal propagation to Part B and §17.1 prose. V9.9 updated the canonical `Strategy` interface declaration in §17.1 but the change did not fully propagate into (a) the `sealed` prose sentence immediately following the interface body in §17.1, or (b) the Part B TASK-029 task-card appendix which still described creating a "sealed interface" and still labeled `Strategy.java` as "(sealed interface — Section 17.1)". V9.10 removes the stale wording in both locations. |
| v9.11 | Two targeted corrections in the Strategy contract. §17.1 had two methods for strategy-ID access (`getStrategyId()` abstract + `strategyId()` default); the redundant `getStrategyId()` was removed, with `strategyId()` now the sole contract. Part B TASK-029's "Copy the class from Section 17.9" typo corrected to Section 17.10. |
| v9.12 | `ArbStrategyConfig` / cluster TOML field completion. §6.2.1 defined an 11-field record but §22.5 `[strategy.arb]` only provided 7. V9.12 added the missing 5 fields (`takerFeeScaled`, `baseSlippageBps`, `slippageSlopeBps`, `referenceSize`, `cooldownAfterFailureMicros`) using parallel arrays aligned with `venueIds` for per-venue values; renamed TOML `legTimeoutMicros` → `legTimeoutClusterMicros` to match the record; documented the loader contract and TOML↔record field mapping in §22.6. |
| v9.13 | ConfigManager loader signature and T-002 test appendix cleanup. §22.6 loader return type corrected from `ClusterConfig` to `ClusterNodeConfig`; §D.2 test method names corrected from `loadClusterConfig_*` to `loadCluster_*`; exception type corrected from `ConfigurationException` to `ConfigValidationException`; Part B TASK-004 test case names updated to match. |
| v9.14 | T-002 ConfigManagerTest coverage alignment. Spec Part B TASK-004 and §D.2 (both 9 cases) diverged from the plan's T-002 list (10 cases). V9.14 synced both sides to a 13-case union preserving all unique coverage; naming normalized to the plan's convention. |
| **v10.0 (this)** | **Test catalog consolidation — Path B: tier-separated sections.** The test catalog was scattered across three locations with duplicate name-lists in plan task cards, spec Part B task cards, and spec §D.x — driving drift that required multiple point-release fixes (V9.13 naming, V9.14 coverage). V10.0 establishes a single source of truth: (1) §D.2 "Unit Test Classes" retains T-001..T-024 only; the 5 IT blocks that were misplaced inside §D.2 move to a new §D.3. (2) **New §D.3 "Integration Test Classes"** contains IT-001..IT-007 with tier-level prelude covering integration-test conventions (real SBE encoders, stub Aeron publication, `@Test [INTEGRATION]` tag, Gradle `:integrationTest` task). IT-006 (RestPollerIntegrationTest) and IT-007 (ArbBatchParsingTest) are authored for the first time — these existed only in plan §5.5 ownership table and task-card references, with no spec-level definition. (3) §D.4 "End-to-End Tests" is unchanged in content; ET-001..ET-006 remain with TradingSystemTestHarness fixture definition. (4) Per-class "Ownership" lines added to every T-NNN / IT-NNN / ET-NNN block stating which task card CREATES the class file and which task cards ADD methods (for shared classes like T-021 TradingClusteredServiceTest). Plan v1.4.0 (follow-up, not this release) will rewrite task-card test enumerations as pointers to §D.2/§D.3/§D.4. This is a major-minor bump because it changes the test catalog's structural organization — no prior V9.x point release touched §D.* structure. |

---

# 1. SYSTEM OVERVIEW

## 1.1 Purpose

This platform executes proprietary trading strategies against one or more cryptocurrency exchanges using firm capital. It is not a brokerage, does not hold client funds, and has no external order routing obligations. The system generates alpha via Market Making and Cross-Venue Arbitrage while enforcing strict pre-trade and post-trade risk controls.

## 1.2 Scope

**In scope:**
- Artio-based FIX 4.2 connectivity to external venues (primary: Coinbase Advanced Trade)
- SBE-encoded event transport over Aeron IPC between gateway and cluster
- Market data ingestion, normalization, and internal order book maintenance
- Strategy execution: Market Making and Cross-Venue Arbitrage (pluggable via `Strategy` interface)
- Pre-trade risk enforcement (hard limits, soft alerts, kill switch)
- Order lifecycle management via Artio session + Aeron Cluster state machine
- Position tracking, realized/unrealized PnL calculation
- Aeron Archive–based audit log and deterministic state replay
- Crash recovery, order reconciliation, and safe trading resumption

> **Prerequisite — Coinbase FIX access:** FIX 4.2 connectivity to Coinbase Advanced Trade
> (`fix.exchange.coinbase.com:4198`) requires an institutional account agreement with Coinbase.
> Confirm this agreement and obtain API credentials (key, secret, passphrase) before beginning
> gateway development. Sandbox access (`fix-public.sandbox.exchange.coinbase.com:4198`) is
> available for development and does not require the institutional agreement.

**Out of scope:**
- Client onboarding, KYC, AML reporting
- FX spot hedging beyond crypto-denominated pairs
- Options or futures strategies (spot only)
- GUI front-end (monitoring via shared-memory metrics + Prometheus sidecar)
- Blockchain settlement or custody
- Multi-currency PnL conversion (non-USD quote currencies) — deferred to future version
- Corporate actions (stock splits, reverse splits, dividends) — not applicable to spot crypto; relevant only if equity instruments are added in a future version

## 1.3 Trading Model

Fully proprietary principal trading. All positions are firm capital. No third-party orders, no client allocations, no custody obligations. Pre-funded exchange accounts. Capital exposure governed exclusively by internal risk limits.

## 1.4 Process Topology

```
┌──────────────────────────────────────────────────────────────────┐
│                  GATEWAY PROCESS (per venue)                     │
│                                                                  │
│  Artio FixEngine (TCP/TLS to venue)                              │
│       ↓ onMessage()            ↑ send()                          │
│  MarketDataHandler          OrderCommandHandler                  │
│  ExecutionHandler           (SBE decode → Artio send)           │
│  [stamp ingressNanos]                                            │
│       ↓ SBE encode                  ↑ SBE decode                 │
│  [Disruptor fan-in]         Aeron Subscription (egress)          │
│       ↓                                                          │
│  Aeron Publication (ingress)                                     │
└──────────────────────┬───────────────────────────────────────────┘
                       │  Aeron IPC / UDP
                       ↕
┌──────────────────────┴───────────────────────────────────────────┐
│              AERON CLUSTER — 3 nodes (Raft)                      │
│                                                                  │
│  ClusteredService.onSessionMessage()  [single-threaded]          │
│       ↓ direct dispatch (no Disruptor)                           │
│  StrategyEngine  →  RiskEngine  →  OrderManager                  │
│  PortfolioEngine    RecoveryCoordinator                          │
│       ↓                                                          │
│  cluster.egressPublication.offer(OrderCommand SBE)               │
└──────────────────────────────────────────────────────────────────┘
```

## 1.5 System Boundaries

| Boundary | Mechanism | Notes |
|---|---|---|
| Venue ↔ Gateway | FIX 4.2 over TCP/TLS via Artio | Artio manages session, resend, heartbeat |
| Gateway ↔ Cluster (inbound) | Aeron IPC, SBE-encoded | Gateway is single producer |
| Cluster ↔ Gateway (outbound) | Aeron Cluster egress, SBE-encoded | Cluster is single producer |
| Cluster nodes | Aeron UDP + Raft consensus | 3-node; leader handles all business logic |
| Audit log | Aeron Archive | All ingress messages persisted; replay on restart |
| Metrics | Shared memory (Agrona counters) | Sidecar reads; no JMX on hot path |

---

# 2. BUSINESS REQUIREMENTS

## 2.1 Market Making

### 2.1.1 Objective

Continuously quote two-sided markets on a configured instrument at a configured venue. Earn the bid-ask spread. Manage inventory risk via skewed quotes when position approaches limits.

### 2.1.2 Inputs

| Input | Source | Internal Type |
|---|---|---|
| Top-of-book L2 | InternalMarketView | `long priceScaled` (1e8) |
| Own position | PortfolioEngine (via PositionEvent) | `long netQtyScaled` (1e8) |
| Position limits | Config (int venueId, int instrumentId) | `long maxLongScaled`, `long maxShortScaled` |
| Target spread | Config | `long targetSpreadBps` |
| Quote size | Config | `long baseQuoteSizeScaled` |
| Refresh threshold | Config | `long refreshThresholdBps` |
| Suppression flags | RiskEngine | `boolean killSwitch`, `boolean recoveryLock` |

**All IDs are `int` on the hot path. String lookup occurs only at startup and logging.**

### 2.1.3 Decision Rules

**Fair Value:**
```
midPrice = (bestBid + bestAsk) / 2

if (bestAsk - bestBid) > wideSpreadThresholdBps * midPrice / 10000:
    fairValue = lastTradePrice
else:
    fairValue = midPrice
```

**Inventory Skew:**
```
inventoryRatio = clamp(currentPosition / maxPositionLimit, -1.0, +1.0)
skewBps = inventorySkewFactorBps * inventoryRatio
adjustedFairValue = fairValue - (skewBps * fairValue / 10000)
```

**Spread Model:**
```
halfSpread = targetSpreadBps / 2 * fairValue / 10000
rawBid = adjustedFairValue - halfSpread
rawAsk = adjustedFairValue + halfSpread
ourBid = floorToTick(rawBid, tickSize)
ourAsk = ceilToTick(rawAsk, tickSize)
if ourBid >= ourAsk: ourAsk = ourBid + tickSize
```

**Quote Sizing:**
```
// Inventory skew REDUCES size on the side that would increase exposure.
// When long (inventoryRatio > 0): shrink bidSize, grow askSize → lean toward selling.
// When short (inventoryRatio < 0): shrink askSize, grow bidSize → lean toward buying.
//
// minQuoteSizeFraction: configurable floor for the shrinking side.
// Default = 0.1 (10% of baseSize). Purpose: prevents the shrinking side from
// rounding to zero lot size when inventoryRatio is close to 1.0, which would
// cause full suppression of that side before the position limit is actually hit.
// Set lower (e.g. 0.05) for tighter inventory management; raise (e.g. 0.2) for
// more symmetric quoting. Configured as [strategy.marketMaking] minQuoteSizeFraction.
if inventoryRatio > 0:
    bidSize = baseSize * max(minQuoteSizeFraction, 1 - inventoryRatio)   // floor prevents zero bid
    askSize = baseSize * (1 + inventoryRatio)                             // uncapped grow
else:
    bidSize = baseSize * (1 - inventoryRatio)                             // uncapped grow
    askSize = baseSize * max(minQuoteSizeFraction, 1 + inventoryRatio)   // floor prevents zero ask
bidSize = min(floorToLot(bidSize), maxQuoteSize)
askSize = min(floorToLot(askSize), maxQuoteSize)
// Note: if floorToLot(bidSize) == 0 after the floor, the bid side is fully suppressed
// (size rounds down to zero at lot granularity). This is correct: minQuoteSizeFraction
// is a continuous-domain floor; lot rounding can still produce zero for very small base sizes.
```

**Refresh Logic:**
```
requiresRefresh = false
currentMid = (bestBid + bestAsk) / 2
if abs(currentMid - lastQuotedMid) / lastQuotedMid * 10000 > refreshThresholdBps:
    requiresRefresh = true
if (clusterTimeMicros - lastQuoteTimeMicros) > maxQuoteAgeMicros:
    requiresRefresh = true
if bidSize == 0 || askSize == 0:
    suppress affected side
```

**Note:** `clusterTimeMicros` is `cluster.time()` — deterministic across all nodes. Never `System.nanoTime()` inside ClusteredService.

### 2.1.4 Suppression Conditions

All checks use pre-resolved `int` IDs; no String comparison on hot path:

1. `riskEngine.isKillSwitchActive()` — atomic boolean read
2. `recoveryCoordinator.isInRecovery(venueId)` — atomic boolean read, keyed by `int venueId`
3. `marketView.isStale(venueId, instrumentId)` — staleness tracked by `long lastUpdateClusterTime`
4. Spread exceeds `maxTolerableSpreadBps`
5. `abs(inventoryRatio) >= 1.0` on the exhausted side
6. 3 consecutive risk rejections within 1 second → suppress 5 seconds
7. Computed prices identical to live quotes

### 2.1.5 Outputs / Actions

1. Publish `CancelCommand` SBE message to cluster egress for stale quotes
2. Publish `NewOrderCommand` SBE message to cluster egress for new quotes
3. All commands are deterministic: same input → same output on all cluster nodes

### 2.1.6 State Transitions

```
IDLE        → QUOTING:     first valid market data + risk green + within limits
QUOTING     → REFRESHING:  refresh trigger fired
REFRESHING  → QUOTING:     cancel confirmed + new quote submitted
QUOTING     → SUPPRESSED:  any suppression condition true
SUPPRESSED  → QUOTING:     all suppression conditions false + cooldown elapsed
QUOTING     → IDLE:        strategy disabled in config
```

### 2.1.7 Edge Cases

| Case | Handling |
|---|---|
| Cancel not ACK'd after 500ms (cluster time) | Re-send cancel; track by `long clOrdId` |
| New quote rejected by venue | Log; publish `RiskAlert`; no immediate retry |
| Fill during refresh window | `onFill()` updates position before next quote cycle |
| Market data gap > staleness threshold | `isStale = true`; suppress immediately |
| Unknown ClOrdId in ExecutionReport | Log WARN; discard; ops alert |

### 2.1.8 Strategy-Level Latency Targets and Acceptance Criteria

**Important distinction — two measurement levels:**

| Level | What is measured | Where measured | Target |
|---|---|---|---|
| Strategy-internal | Time inside `onSessionMessage()` from market data receipt to `egressPublication.offer()` | Cluster-service thread | **100µs** |
| End-to-end system | Time from FIX market data receipt at gateway to FIX order receipt at simulator | Gateway in → simulator | 500ms (AC-MM-001/002) |

The end-to-end figure includes Aeron IPC (gateway → cluster → gateway), Artio encoding, and TCP. The strategy-internal figure covers only cluster computation. Both are required — the strategy-internal target ensures the cluster is not the bottleneck; the end-to-end target is the testable acceptance criterion.

**Strategy-internal targets (verified by strategy unit tests and profiler):**
- `onMarketData()` → `egressPublication.offer()` completes within **100µs** of market data delivery to the cluster
- Quote cancel on suppression condition detection completes within **50ms** (strategy sets `cancelLiveQuotes()` before returning from `onMarketData()`)

**End-to-end system acceptance criteria (see Part E for formal test coverage):**
- Two-sided quotes arrive at simulator within **500ms** of first valid market data (AC-MM-001)
- Requote arrives at simulator within **500ms** of market move event (AC-MM-002)
- Kill switch cancels all live quotes within **10ms** end-to-end (AC-MM-007, AC-PF-P-003)
- Market data stale suppression: quotes stop within 1 quote cycle (AC-MM-009)

**Other strategy correctness criteria:**
- Zero heap allocations during quote cycle (verified by async-profiler --alloc) — AC-MM-017
- Inventory skew correct: long position → lower bid and ask (test vectors in Section 20) — AC-MM-004/005
- No quote with size=0 or price≤0 ever submitted — AC-MM-006
- **Note:** Spread capture rate, fill rate, inventory turnover tracked via shared-memory counters but thresholds not specified in v6.0 — deferred to post-launch calibration.

---

## 2.2 Cross-Venue Arbitrage

### 2.2.1 Objective

Detect price discrepancies across venues. Submit simultaneous IOC orders on both sides when net profit (fees + slippage adjusted) exceeds threshold. Hedge any residual exposure immediately.

### 2.2.2 Inputs

| Input | Type | Source |
|---|---|---|
| Best bid/ask per venue | `long priceScaled` | InternalMarketView |
| Taker fee per venue | `long feeScaled` (1e8) | Config, keyed by `int venueId` |
| Slippage model params | `long baseSlippageBps`, `long slopeBps` | Config |
| Min net profit | `long minNetProfitBps` | Config |
| Max arb position | `long maxArbPositionScaled` | Config |
| Current arb exposure | `long netExposureScaled` | PortfolioEngine |

### 2.2.3 Opportunity Formula

```
grossProfit = sellVenueBid - buyVenueAsk

buyFee   = buyVenueAsk  * takerFee[buyVenueId]  / SCALE
sellFee  = sellVenueBid * takerFee[sellVenueId] / SCALE
buySlip  = slippage(buyVenueId,  orderSize)
sellSlip = slippage(sellVenueId, orderSize)

netProfit    = grossProfit - buyFee - sellFee - buySlip - sellSlip
netProfitBps = netProfit * 10000 / buyVenueAsk

if netProfitBps >= minNetProfitBps: EXECUTE
```

### 2.2.4 Leg Execution Rules

1. Both legs submitted in a **single `egressPublication.offer()` call** — both `NewOrderCommand` SBE messages concatenated into one buffer, delivered to the gateway in one Aeron fragment
2. Order type: IOC only — no resting arb orders
3. `attemptId` = `cluster.logPosition()` at moment of execution — deterministic, no UUID
4. Leg gap target: < 1ms — both `NewOrderCommand` bytes arrive at the gateway in the same fragment; the gateway's `onFragment` loop sends both FIX messages without yielding the thread

**Gateway parsing of dual-leg arb batch:**

The `OrderCommandHandler.onFragment()` must iterate through multiple SBE messages within a single Aeron fragment. The standard single-message path is not sufficient for arb batches. Implementation:

```java
// In OrderCommandHandler.onFragment() — called by EgressPollLoop
@Override
public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
    int cursor = offset;
    int end    = offset + length;

    while (cursor < end) {
        messageHeaderDecoder.wrap(buffer, cursor);
        int templateId = messageHeaderDecoder.templateId();
        int headerLen  = messageHeaderDecoder.encodedLength();
        int blockLen   = messageHeaderDecoder.blockLength();
        int version    = messageHeaderDecoder.version();

        switch (templateId) {
            case NewOrderCommandDecoder.TEMPLATE_ID -> {
                newOrderDecoder.wrap(buffer, cursor + headerLen, blockLen, version);
                executionRouter.routeNewOrder(newOrderDecoder);
                cursor += headerLen + newOrderDecoder.encodedLength();   // fixed-length: encodedLength() == blockLen
            }
            case CancelOrderCommandDecoder.TEMPLATE_ID -> {
                cancelDecoder.wrap(buffer, cursor + headerLen, blockLen, version);
                executionRouter.routeCancel(cancelDecoder);
                cursor += headerLen + cancelDecoder.encodedLength();     // variable-length: includes varString venueOrderId
            }
            case ReplaceOrderCommandDecoder.TEMPLATE_ID -> {
                replaceDecoder.wrap(buffer, cursor + headerLen, blockLen, version);
                executionRouter.routeReplace(replaceDecoder);
                cursor += headerLen + replaceDecoder.encodedLength();    // variable-length: includes varString venueOrderId
            }
            case OrderStatusQueryCommandDecoder.TEMPLATE_ID -> {
                statusQueryDecoder.wrap(buffer, cursor + headerLen, blockLen, version);
                executionRouter.routeStatusQuery(statusQueryDecoder);
                cursor += headerLen + statusQueryDecoder.encodedLength(); // variable-length: includes varString venueOrderId
            }
            case BalanceQueryRequestDecoder.TEMPLATE_ID -> {
                balanceQueryDecoder.wrap(buffer, cursor + headerLen, blockLen, version);
                restPoller.onBalanceQueryRequest(balanceQueryDecoder);
                cursor += headerLen + balanceQueryDecoder.encodedLength(); // fixed-length
            }
            case RecoveryCompleteEventDecoder.TEMPLATE_ID -> {
                recoveryDecoder.wrap(buffer, cursor + headerLen, blockLen, version);
                venueStatusHandler.onRecoveryComplete(recoveryDecoder);
                cursor += headerLen + recoveryDecoder.encodedLength();   // fixed-length
            }
            default -> {
                // Unknown templateId in egress — cannot safely advance past variable-length fields.
                // Stop processing this fragment entirely; log for investigation.
                log.warn("Unknown egress templateId: {} at cursor={}; aborting fragment parse", templateId, cursor);
                return;  // break out of while loop entirely — safer than guessing the message size
            }
        }
    }
}
```

**Cursor advancement rule (enforced by pattern above):** Always advance cursor INSIDE the switch case using `headerLen + decoder.encodedLength()` AFTER calling `decoder.wrap()`. Never compute `messageEnd` before the switch — `encodedLength()` is only valid after the decoder has wrapped the buffer and processed any variable-length fields. The `default` branch returns rather than attempting to advance, because `blockLen` alone is not safe for unknown messages that may contain var-data.

**Why `encodedLength()` not `blockLen` for variable-length messages:** SBE `blockLen` covers only the fixed portion of the message body. For messages with `<data>` fields (varString `venueOrderId`), `encodedLength()` = `blockLen` + length prefix (2 bytes) + actual string bytes. Using `blockLen` on these messages leaves the cursor in the middle of the var-data, corrupting the parse of the next message in the fragment.

**Test coverage:** `IT-007 ArbBatchParsingTest` — verify that a single Aeron fragment containing two concatenated `NewOrderCommand` messages results in exactly two `artioSession.send()` calls, each with the correct `clOrdId`, `side`, `price`, and `qty`. Also verify: a fragment containing one `CancelOrderCommand` (variable-length, with `venueOrderId`) followed by one `NewOrderCommand` parses both correctly — i.e., the cursor advances by `headerLen + cancelDecoder.encodedLength()` (not `blockLen`) leaving the cursor exactly at the `NewOrderCommand` header.

### 2.2.5 Hedge Logic

```
imbalance = leg1FillQty - leg2FillQty

if abs(imbalance) > lotSize:
    side = (imbalance > 0) ? SELL : BUY
    venue = (side == SELL) ? leg2VenueId : leg1VenueId
    submit IOC MARKET hedge for abs(imbalance)

if hedge fails (rejected or rate-limited):
    activateKillSwitch("hedge_failure")
    resetArbState()
    // No retry — an unhedged position is a risk event requiring operator review.
    // "Suspend arb strategy" is NOT the behaviour — the kill switch halts ALL trading.
    // Operator must deactivate kill switch via AdminCli after investigating position.
    // cooldownAfterFailureMicros applies after kill switch is deactivated (AC-ARB-011).
```

### 2.2.6 Acceptance Criteria

- Opportunity detected and legs submitted within 2ms of market data delivery
- `attemptId` is always `cluster.logPosition()` — never UUID, never random
- Net profit formula includes fees and slippage (test vectors in Section 20)
- Hedge submitted within 100ms of detecting unbalanced exposure
- No arb order when combined exposure would exceed `maxArbPositionScaled`

---

## 2.3 Strategy Pluggability

The `Strategy` interface (Section 17.1) is the sole plugin contract. `StrategyEngine` holds a `List<Strategy>` and dispatches without knowing concrete types.

**Initial implementations:**

| Class | Interface | Strategy ID (int) |
|---|---|---|
| `MarketMakingStrategy` | `Strategy` | `1` |
| `ArbStrategy` | `Strategy` | `2` |

Strategy IDs are `int` constants. The `strategyId` field on `OrderCommand` and `FillEvent` SBE messages is `int16`, not String.

**Registration at startup (config-driven, not hardcoded):**
```java
List<Strategy> strategies = new ArrayList<>();
if (config.isEnabled(STRATEGY_MARKET_MAKING))
    strategies.add(new MarketMakingStrategy(config.marketMaking()));
if (config.isEnabled(STRATEGY_ARB))
    strategies.add(new ArbStrategy(config.arb()));
strategyEngine.register(strategies);
```

---

## 2.4 Portfolio Requirements

### 2.4.1 Objective

Maintain accurate real-time inventory per `(int venueId, int instrumentId)`. Track realized/unrealized PnL. Source of truth for position data consumed by Risk and Strategy.

### 2.4.2 Acceptance Criteria

- Position updated within one `onSessionMessage()` dispatch of fill event
- PnL formula validated against known test vectors
- Position matches venue balance query within 0.0001 units post-reconciliation

---

## 2.5 Risk Requirements

### 2.5.1 Objective

Enforce hard and soft trading limits. Provide kill switch halting all activity within 10ms.

### 2.5.2 Acceptance Criteria

- All 8 pre-trade checks complete within 5µs (measured; not estimated)
- Kill switch propagates to all strategies within 10ms
- Hard limit breach → immediate rejection with no exceptions
- Risk state survives restart via Aeron Archive replay

---

## 2.6 Recovery Requirements

### 2.6.1 Objective

After gateway crash or FIX disconnect, reconcile all open orders and positions before resuming trading. Cluster state is rebuilt from Aeron Archive replay — no external query required for cluster-internal state.

### 2.6.2 Recovery Phase Sequence

Recovery covers two distinct failure scenarios. Both follow the same phase sequence but differ in whether archive replay is needed.

**Scenario A — Cluster node restart** (crash or new leader election):
archive replay is required before the gateway can connect.

**Scenario B — FIX disconnect only** (cluster still running, venue session dropped):
no replay needed — cluster state is current. Phases 1–2 are trivially complete.

**Ordered phase sequence — only one valid interpretation:**

```
Phase 1 — Archive Replay (Scenario A only):
    Cluster starts → loads latest snapshot → replays log to current position.
    recoveryLock[all venues] = true during replay.
    Cluster opens Aeron ingress session only after replay is complete.
    Gateway Aeron client detects new leader and reconnects.

Phase 2 — FIX Reconnect Initiated:
    Artio initiates TCP reconnect to venue (ReconnectIntervalMs = 5000ms).
    No trading possible: recoveryLock still set.

Phase 3 — FIX Logon Established:
    Artio FIX logon completes.
    Gateway publishes VenueStatusEvent{CONNECTED} to cluster ingress.
    RecoveryCoordinator begins open order reconciliation.

Phase 4 — Order Reconciliation (10s hard timer — kill switch on expiry):
    OrderStatusQueryCommand sent for each live order.
    Venue responds with ExecType=I ExecutionReports.
    Missing fills synthesised; orphans cancelled; state corrected.
    10-second global timer — on timeout: partial results applied, kill switch, no resume.

Phase 5 — Balance Reconciliation (5s hard timer — kill switch on expiry):
    REST GET /accounts; discrepancy evaluated.
    Within tolerance → adjust position.
    Exceeds tolerance → kill switch, no resume.

Phase 6 — Trading Unlocked:
    recoveryLock[venueId] = false.
    RecoveryCompleteEvent published to egress.
    StrategyEngine notified → quotes may resume.
```

**Recovery timing budget — one authoritative table:**

| Phase | Hard timer | On expiry | Counts toward 30s SLA? |
|---|---|---|---|
| Phase 4 — Order reconciliation | **10 seconds** | Kill switch; no Phase 5; no resume | Yes |
| Phase 5 — Balance reconciliation | **5 seconds** | Kill switch; no resume | Yes |
| Phase 6 — Unlock overhead | ~0s | N/A | Yes |
| **Total worst-case (Phases 4+5+6)** | **≤ 15 seconds** | — | Leaves 15s headroom in 30s SLA |

The 30-second SLA (`RECONCILIATION_COMPLETE` within 30 seconds of `LOGON_ESTABLISHED`) is the **outer bound** for the entire Phases 4–6 sequence. The individual phase timers (10s + 5s = 15s maximum) consume at most 15 of the 30 available seconds. The remaining 15 seconds covers venue response latency, REST API latency, and Aeron message round-trips. The 30s SLA does **not** give Phase 4 alone 30 seconds — Phase 4's hard limit is 10 seconds regardless.

**Named timing boundaries** used in acceptance criteria below:
- **`REPLAY_DONE`** — cluster archive replay complete; Aeron session open (Phase 1 end)
- **`RECONNECT_INITIATED`** — Artio begins TCP reconnect attempt (Phase 2 start)
- **`LOGON_ESTABLISHED`** — FIX logon complete; `VenueStatus{CONNECTED}` received by cluster (Phase 3)
- **`RECONCILIATION_COMPLETE`** — `RecoveryCompleteEvent` published (Phase 6)

### 2.6.3 Acceptance Criteria

- **No order submission** from `VenueStatus{DISCONNECTED}` until `RECONCILIATION_COMPLETE`
- **`REPLAY_DONE` before `RECONNECT_INITIATED`** — cluster archive replay completes before FIX reconnect is initiated (Scenario A only; trivially satisfied in Scenario B)
- **`RECONCILIATION_COMPLETE` within 30 seconds of `LOGON_ESTABLISHED`** — reconciliation is explicitly post-logon and must complete within this window
- Missing fills detected and portfolio corrected before `RECONCILIATION_COMPLETE`

---

# 3. SYSTEM ARCHITECTURE

## 3.1 Process Inventory

| Process | Count | Role |
|---|---|---|
| Gateway | 1 per venue | Artio FIX session + Aeron publisher/subscriber |
| Cluster Node | 3 (Raft: 1 leader, 2 followers) | All business logic as ClusteredService |
| Media Driver | 1 per host | Aeron transport (dedicated process, not embedded) |
| Metrics Sidecar | 1 | Reads Agrona shared-memory counters; exports to Prometheus |

## 3.2 Gateway Process — Component Inventory

| Component | Role | Thread |
|---|---|---|
| `ArtioFixEngine` | Manages FIX TCP session, heartbeats, resend | artio-framer-thread |
| `MarketDataHandler` | Artio `onMessage()` callback; SBE-encode; publish to Disruptor | artio-library-thread |
| `ExecutionHandler` | Artio `onMessage()` callback for ExecReports; SBE-encode; publish to Disruptor | artio-library-thread |
| `VenueStatusHandler` | Artio `onLogon()`/`onLogout()` callbacks; publish `VenueStatusEvent` | artio-library-thread |
| `RestPoller` | Polls venue REST API for balances; publishes `BalanceQueryResponse` | rest-poller-thread |
| `GatewayDisruptor` | Multi-producer → single-consumer ring buffer; fan-in to Aeron | gateway-disruptor thread |
| `AeronPublisher` | Single consumer of GatewayDisruptor; publishes SBE to Aeron ingress | gateway-disruptor thread |
| `EgressPollLoop` | Polls Aeron cluster egress; dispatches `OrderCommand` to Artio | gateway-egress-thread |
| `OrderCommandHandler` | Decodes SBE `OrderCommand`; calls `artioSession.send()` | gateway-egress-thread |

## 3.3 Cluster Process — Component Inventory

| Component | Role |
|---|---|
| `ClusteredService` | Aeron Cluster lifecycle + `onSessionMessage()` dispatch |
| `MessageRouter` | Sealed-class switch dispatch on SBE templateId → correct handler |
| `StrategyEngine` | Hosts `List<Strategy>`; dispatches market data and fills |
| `MarketMakingStrategy` | Implements `Strategy` |
| `ArbStrategy` | Implements `Strategy` |
| `InternalMarketView` | L2 book per `(int venueId, int instrumentId)`; Panama off-heap arrays |
| `RiskEngine` | 8-step pre-trade check; kill switch; daily loss tracking |
| `OrderManager` | Order state machine; owns all `OrderState` |
| `PortfolioEngine` | Position + PnL; owns all `Position` |
| `RecoveryCoordinator` | Manages per-venue recovery lock; drives reconciliation via cluster messages |
| `IdRegistry` | Startup-time String→int mapping for venueId and instrumentId |

## 3.4 Component Responsibilities — Key Rules

### 3.4.1 MarketDataHandler (Gateway)

- Called on artio-library-thread via Artio `onMessage()` callback
- **Must complete in < 1µs** — no allocation, no blocking
- Stamps `ingressTimestampNanos = System.nanoTime()` as first action
- SBE-encodes into a pre-allocated `UnsafeBuffer` taken from a pool
- Publishes to GatewayDisruptor via `MULTI_PRODUCER` claim

### 3.4.2 AeronPublisher (Gateway)

- **Single consumer** of GatewayDisruptor — no lock needed
- Calls `aeronPublication.offer(buffer, offset, length)` — zero-copy into Aeron
- **Priority-aware back-pressure policy — fills must never be dropped:**
  - `ExecutionEvent` (templateId=2): **retry indefinitely** — blocking the disruptor thread is the correct behaviour; a backed-up cluster is an ops emergency, not a reason to lose fill data
  - All other messages (market data, venue status, balance response): **spin-retry up to 10µs**, then increment `AERON_BACKPRESSURE_COUNTER` and drop — stale market data is recoverable; a missed fill is a position error
  - Peek at `templateId` via `MessageHeaderDecoder` before the retry loop to select policy

### 3.4.3 MessageRouter (Cluster)

`MessageRouter` is a dedicated class that lives as the `router` field inside
`TradingClusteredService` (Section 3.6). `TradingClusteredService.onSessionMessage()`
delegates every incoming buffer to `router` — it does not dispatch inline.

**Design properties:**
- Called exclusively inside `onSessionMessage()` — single-threaded
- Switch on `int templateId` — zero allocation, no `instanceof`, no String compare
- One pre-allocated SBE decoder instance per message type — reused every dispatch
- ExecutionEvent fan-out is **ordered**: OrderManager → PortfolioEngine → RiskEngine → StrategyEngine

**Full class definition:**

```java
package ig.rueishi.nitroj.exchange.cluster;

/**
 * Decodes SBE templateId and dispatches to the correct handler.
 * All methods called exclusively on the cluster-service thread.
 * Zero allocation after warmup.
 */
public final class MessageRouter {

    private final StrategyEngine        strategyEngine;
    private final RiskEngine            riskEngine;
    private final OrderManager          orderManager;
    private final PortfolioEngine       portfolioEngine;
    private final RecoveryCoordinator   recoveryCoordinator;
    private final AdminCommandHandler   adminCommandHandler;
    private final InternalMarketView    marketView;   // ← added: must be updated before strategy dispatch

    // Pre-allocated decoders — one per message type
    private final MarketDataEventDecoder      mdDecoder      = new MarketDataEventDecoder();
    private final ExecutionEventDecoder       execDecoder    = new ExecutionEventDecoder();
    private final VenueStatusEventDecoder     venueDecoder   = new VenueStatusEventDecoder();
    private final BalanceQueryResponseDecoder balanceDecoder = new BalanceQueryResponseDecoder();
    private final AdminCommandDecoder         adminDecoder   = new AdminCommandDecoder();

    // cluster reference — needed to pass cluster.time() to InternalMarketView.apply()
    private Cluster cluster;

    public MessageRouter(StrategyEngine strategyEngine, RiskEngine riskEngine,
                          OrderManager orderManager, PortfolioEngine portfolioEngine,
                          RecoveryCoordinator recoveryCoordinator,
                          AdminCommandHandler adminCommandHandler,
                          InternalMarketView marketView) {
        this.strategyEngine      = strategyEngine;
        this.riskEngine          = riskEngine;
        this.orderManager        = orderManager;
        this.portfolioEngine     = portfolioEngine;
        this.recoveryCoordinator = recoveryCoordinator;
        this.adminCommandHandler = adminCommandHandler;
        this.marketView          = marketView;
    }

    public void setCluster(Cluster cluster) { this.cluster = cluster; }

    public void dispatch(DirectBuffer buffer, int offset, int blockLen, int version, int hdrLen,
                          int templateId) {
        switch (templateId) {
            case MarketDataEventDecoder.TEMPLATE_ID -> {
                mdDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onMarketData(mdDecoder);
            }
            case ExecutionEventDecoder.TEMPLATE_ID -> {
                execDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onExecution(execDecoder);
            }
            case VenueStatusEventDecoder.TEMPLATE_ID -> {
                venueDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onVenueStatus(venueDecoder);
            }
            case BalanceQueryResponseDecoder.TEMPLATE_ID -> {
                balanceDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onBalanceResponse(balanceDecoder);
            }
            case AdminCommandDecoder.TEMPLATE_ID -> {
                adminDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                onAdminCommand(adminDecoder);
            }
            default -> log.warn("Unknown templateId: {}", templateId);
        }
    }

    // Private handler methods — one per message type.
    // dispatch() calls these after wrapping the decoder from the raw buffer.
    // Each method is at one level of abstraction: pure business logic, no buffer handling.

    private void onMarketData(MarketDataEventDecoder d) {
        // L2 book MUST be updated before strategy dispatch — strategies read
        // getBestBid()/getBestAsk() and isStale() which depend on the updated book.
        marketView.apply(d, cluster.time());
        strategyEngine.onMarketData(d);
    }

    private void onExecution(ExecutionEventDecoder d) {
        // Fan-out in strict order (AC-FO-001/002/003/004):
        // OrderManager first — determines if this execution is a fill.
        boolean isFill = orderManager.onExecution(d);
        // Portfolio and Risk only on fills — position and daily metrics must be updated.
        if (isFill) {
            portfolioEngine.onFill(d);
            riskEngine.onFill(d);
        }
        // Strategy last — reacts to fill with position already updated.
        strategyEngine.onExecution(d, isFill);
    }

    private void onVenueStatus(VenueStatusEventDecoder d) {
        recoveryCoordinator.onVenueStatus(d);
    }

    private void onBalanceResponse(BalanceQueryResponseDecoder d) {
        recoveryCoordinator.onBalanceResponse(d);
    }

    private void onAdminCommand(AdminCommandDecoder d) {
        adminCommandHandler.onAdminCommand(d);
    }
}
```

**See also:** TASK-025 for implementation task card. T-018 MessageRouterTest for test coverage.

### 3.4.4 InternalMarketView (Cluster)

- **Single-writer:** updated exclusively inside `onSessionMessage()` (already single-threaded)
- Backed by **Panama off-heap `MemorySegment`** — not subject to GC
- Exposes `getBestBid(int venueId, int instrumentId)` — direct memory read, no object
- Marks `isStale = true` if `cluster.time() - lastUpdateClusterTime > stalenessThresholdMicros`

### 3.4.5 RiskEngine (Cluster)

- All state is `long` primitives — no objects on hot path
- `isKillSwitchActive()` is a plain `boolean` field — safe because cluster is single-threaded
- Pre-trade check is a direct method call, not a ring buffer round-trip (cluster is already sequenced)
- Publishes `RiskEvent` SBE to cluster egress for gateway audit log

### 3.4.6 OrderManager (Cluster)

- Assigns `clOrdId` = `cluster.logPosition()` — deterministic, unique, no allocation
- All `OrderState` stored in `LongObjectHashMap<OrderState>` (Eclipse Collections) — primitive key, no autoboxing
- On fill: updates `OrderState` fields (`cumFillQty`, `avgFillPrice`, `status`); calls `portfolioEngine.onFill(decoder)` directly (no intermediate FillEvent SBE for normal fills — `ExecutionEventDecoder` IS the fill event). FillEvent SBE is only encoded during reconciliation synthetic fills (Section 16.4) for the audit archive.

### 3.4.7 PortfolioEngine (Cluster)

- Position keyed by `long key = ((long) venueId << 32) | instrumentId` — single primitive, no object
- All arithmetic in scaled `long` — see Section 11

### 3.4.8 RecoveryCoordinator (Cluster)

- Per-venue recovery lock: `boolean[] recoveryLock = new boolean[MAX_VENUES]` — array indexed by `int venueId`
- Drives reconciliation by publishing `OrderStatusRequest` SBE messages to egress → gateway → venue FIX
- On response arrival: dispatched back through cluster ingress as `ExecutionEvent` (execType=ORDER_STATUS, templateId=2)

---

## 3.5 ID Registry Constants

All integer IDs are assigned here and are **immutable after first assignment**. Never reassign an ID. Never reuse a deleted ID. Source of truth: the TOML files in Section 22. This file is validated at startup against those files.

```java
package ig.rueishi.nitroj.exchange.common;

public final class Ids {

    // Venue IDs — never change after first assignment
    public static final int VENUE_COINBASE         = 1;
    public static final int VENUE_COINBASE_SANDBOX = 2;

    // Instrument IDs — never change after first assignment
    public static final int INSTRUMENT_BTC_USD     = 1;
    public static final int INSTRUMENT_ETH_USD     = 2;

    // Strategy IDs
    public static final int STRATEGY_MARKET_MAKING = 1;
    public static final int STRATEGY_ARB           = 2;
    public static final int STRATEGY_ARB_HEDGE     = 3;

    // System limits — must match cluster-node.toml [constants]
    public static final int MAX_VENUES             = 16;
    public static final int MAX_INSTRUMENTS        = 64;
    public static final int MAX_ORDERS_PER_WINDOW  = 1000;

    // Sentinel values
    public static final long INVALID_PRICE         = Long.MIN_VALUE;
    public static final long INVALID_QTY           = Long.MIN_VALUE;
    public static final long SCALE                 = 100_000_000L;  // 1e8

    private Ids() {}
}
```

---

## 3.6 TradingClusteredService — Full Lifecycle

The central cluster component. Implements `io.aeron.cluster.service.ClusteredService`. All business logic enters through `onSessionMessage()`. See Section 19.5 for the startup configuration that launches this service.

```java
public final class TradingClusteredService implements ClusteredService {

    // Business logic components — injected via constructor (see Section 19.6)
    private final StrategyEngine       strategyEngine;
    private final RiskEngine           riskEngine;
    private final OrderManager         orderManager;
    private final PortfolioEngine      portfolioEngine;
    private final RecoveryCoordinator  recoveryCoordinator;
    private final DailyResetTimer      dailyResetTimer;
    private final MessageRouter        router;

    private Cluster cluster;  // set in onStart(); null before cluster starts

    // MessageHeaderDecoder — reads templateId/blockLength/version from each ingress buffer.
    // The message-type decoders (mdDecoder, execDecoder, etc.) live in MessageRouter.
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        strategyEngine.setCluster(cluster);
        riskEngine.setCluster(cluster);
        orderManager.setCluster(cluster);
        portfolioEngine.setCluster(cluster);
        dailyResetTimer.setCluster(cluster);
        router.setCluster(cluster);   // ← router needs cluster.time() for InternalMarketView.apply()

        if (snapshotImage != null) loadSnapshot(snapshotImage);

        dailyResetTimer.scheduleNextReset();
        log.info("ClusteredService started. Role: {}", cluster.role());
    }

    @Override
    public void onSessionMessage(ClientSession session, long timestamp,
                                  DirectBuffer buffer, int offset, int length) {
        headerDecoder.wrap(buffer, offset);
        int templateId = headerDecoder.templateId();
        int blockLen   = headerDecoder.blockLength();
        int version    = headerDecoder.version();
        int hdrLen     = headerDecoder.encodedLength();

        // Single dispatch point — MessageRouter handles all decoding and fan-out.
        router.dispatch(buffer, offset, blockLen, version, hdrLen, templateId);
    }

    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
        dailyResetTimer.onTimer(correlationId, timestamp);       // correlationId 1001
        recoveryCoordinator.onTimer(correlationId, timestamp);   // correlationId 2000+venueId, 3000+venueId
        strategyEngine.onTimer(correlationId);                   // correlationId 4000+ (arb leg timeouts)
    }

    @Override
    public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
        // Write in deterministic order — same order as loadSnapshot
        orderManager.writeSnapshot(snapshotPublication);
        portfolioEngine.writeSnapshot(snapshotPublication);
        riskEngine.writeSnapshot(snapshotPublication);
        recoveryCoordinator.writeSnapshot(snapshotPublication);
    }

    private void loadSnapshot(Image snapshotImage) {
        orderManager.loadSnapshot(snapshotImage);
        portfolioEngine.loadSnapshot(snapshotImage);
        riskEngine.loadSnapshot(snapshotImage);
        recoveryCoordinator.loadSnapshot(snapshotImage);
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        log.info("Cluster role changed to: {}", newRole);
        strategyEngine.setActive(newRole == Cluster.Role.LEADER);
    }

    @Override
    public void onMembershipChange(long logPosition, long timestamp,
                                    ChangeType change, int memberId) {
        log.info("Membership change: {} member={}", change, memberId);
    }

    // Warmup support — see Section 26 (Warmup Harness)
    public void installClusterShim(Cluster shimCluster) {
        this.cluster = shimCluster;
        strategyEngine.setCluster(shimCluster);
        riskEngine.setCluster(shimCluster);
        orderManager.setCluster(shimCluster);
        portfolioEngine.setCluster(shimCluster);
        dailyResetTimer.setCluster(shimCluster);
    }

    public void removeClusterShim() {
        this.cluster = null;
        strategyEngine.setCluster(null);
        riskEngine.setCluster(null);
        orderManager.setCluster(null);
        portfolioEngine.setCluster(null);
        dailyResetTimer.setCluster(null);
    }

    public void resetWarmupState() {
        orderManager.resetAll();
        portfolioEngine.resetAll();
        riskEngine.resetAll();
        recoveryCoordinator.resetAll();
        strategyEngine.resetAll();
    }
}
```

---

# 4. EVENT FLOW

## 4.1 Market Data Flow

```
Venue FIX (TCP)
  → Artio FixEngine (artio-framer-thread: TCP receive + FIX decode)
  → Artio Library callback: MarketDataHandler.onMessage(fixMessage)
      [ingressTimestampNanos = System.nanoTime()]          ← ONLY nanoTime in gateway
      [venueId = IdRegistry.venueId(sessionId)]            ← int lookup, no alloc
      [instrumentId = IdRegistry.instrumentId(symbol)]     ← int lookup, no alloc
      [SBE encode into pooled UnsafeBuffer]
      [GatewayDisruptor.publish(slot)]

GatewayDisruptor (intra-gateway)
  → AeronPublisher.onEvent(slot)
      [aeronPublication.offer(buffer)]                     ← zero-copy
      [publishTimestampNanos = System.nanoTime()]          ← stamped for latency audit

Aeron IPC
  → ClusteredService.onSessionMessage(buffer, offset, length, position, header)
      [MessageRouter.dispatch(buffer, offset)]
      [MarketDataEventDecoder.wrap(buffer, offset + headerLen)]
      [InternalMarketView.apply(decoder)]                  ← off-heap L2 book update
      [StrategyEngine.onMarketData(decoder)]
          [MarketMakingStrategy.onMarketData(decoder)]
          [ArbStrategy.onMarketData(decoder)]
```

**Sequencing guarantee:** Aeron Cluster guarantees all messages arrive in log order. `onSessionMessage()` is called sequentially — no concurrent dispatch. The cluster log position is the global sequence number for all events.

**Latency budget:**
```
FIX wire arrival → artio-framer-thread:  ~1µs  (Artio zero-copy parse)
artio-framer → GatewayDisruptor:         ~0.1µs (ring buffer claim)
GatewayDisruptor → AeronPublisher:       ~25ns  (Disruptor dispatch)
Aeron IPC offer → onSessionMessage:      ~200ns (shared memory)
onSessionMessage → strategy dispatch:    ~100ns (SBE decode + switch)

Total gateway-to-strategy:               ~1.5µs (p99 target: 3µs)
```

## 4.2 Order Submission Flow

```
Strategy (inside onSessionMessage — strategy thread IS the cluster thread)
  → compute intent: side, priceScaled, qtyScaled, venueId, instrumentId
  → RiskEngine.preTradeCheck(intent)          ← direct method call, ~2µs
      [8 checks in sequence — see Section 12]
      → APPROVED or REJECTED (RiskDecision value object — stack allocated)

  if APPROVED:
      clOrdId = cluster.logPosition()         ← deterministic ID
      OrderManager.createOrder(clOrdId, intent)
      SBE encode OrderCommand into egressBuffer
      cluster.egressPublication.offer(egressBuffer)

Aeron Cluster egress
  → EgressPollLoop.onFragment(buffer, offset, length, header)  [gateway-egress-thread]
      [OrderCommandDecoder.wrap(buffer, offset)]
      [OrderCommandHandler.handle(decoder)]
          [Artio session.send(newOrderSingleEncoder)] ← Artio builds FIX bytes
          [sendTimestampNanos = System.nanoTime()]    ← stamped at FIX send
```

**Key design point:** Pre-trade risk check is a **direct method call** inside `onSessionMessage()`. There is no ring buffer round-trip for risk. The cluster's single-threaded execution model makes the Disruptor-based risk round-trip from v1.0 unnecessary and removes ~50µs of latency.

## 4.3 Execution Feedback Flow

```
Venue FIX (TCP)
  → Artio: ExecutionReport received
  → ExecutionHandler.onMessage(execReport)         [artio-library-thread]
      [ingressTimestampNanos = System.nanoTime()]
      [SBE encode ExecutionEvent]
      [GatewayDisruptor.publish(slot)]

  → AeronPublisher → Aeron IPC → ClusteredService.onSessionMessage()
      [MessageRouter → ExecutionEventDecoder]
      [OrderManager.onExecution(decoder)]
          [validate state transition]
          [update OrderState: status, fillQty, fillPrice, venueOrderId]
          if fill:
              [SBE encode FillEvent into local buffer]
              [PortfolioEngine.onFill(fillDecoder)]
                  [update Position: netQty, avgEntryPrice, realizedPnl]
              [RiskEngine.onFill(fillDecoder)]
                  [update dailyVolume, dailyPnl]
                  [check soft limits → publish RiskAlert if breached]
              [StrategyEngine.onFill(fillDecoder)]
                  [MarketMakingStrategy.onFill → recompute inventory]
                  [ArbStrategy.onFill → update ArbAttempt, hedge if needed]
```

**Sequencing guarantee:** All of `OrderManager → PortfolioEngine → RiskEngine → StrategyEngine` execute in strict sequence within a single `onSessionMessage()` call. No barriers needed — the cluster IS the barrier.

## 4.4 Recovery Flow

Two distinct scenarios share the same phase sequence. In Scenario A (cluster restart),
archive replay completes before the gateway connects. In Scenario B (FIX disconnect only),
the cluster is already running and Phases 1–2 are instantaneous.

```
[Trigger: Artio onLogout() callback in gateway]

Gateway:
  → VenueStatusHandler.onLogout(sessionId)
      [build VenueStatusEvent SBE: venueId, status=DISCONNECTED]
      [publish to Aeron ingress]

Cluster (onSessionMessage):
  → RecoveryCoordinator.onVenueStatus(decoder)
      [recoveryLock[venueId] = true]
      [publish RiskEvent SBE: RECOVERY_LOCK_SET to egress]
      [RiskEngine notified: refuse all orders for this venueId]

— Scenario A only: if cluster itself restarted —
  [Cluster replays Aeron Archive from last snapshot to current log position]
  [OrderManager, PortfolioEngine, RiskEngine fully rebuilt deterministically]
  [Cluster opens Aeron ingress session → Gateway Aeron client reconnects]
  → REPLAY_DONE

— All scenarios —
[Artio reconnects automatically per ReconnectIntervalMs config]
  → RECONNECT_INITIATED

Gateway:
  → VenueStatusHandler.onLogon(sessionId)
      [build VenueStatusEvent SBE: venueId, status=CONNECTED]
      [publish to Aeron ingress]
  → LOGON_ESTABLISHED

Cluster (onSessionMessage):
  → RecoveryCoordinator.onVenueStatus(decoder)
      [cluster state is current — either from running state (Scenario B)
       or from completed archive replay (Scenario A)]

  STEP 1: Query open orders via FIX (10s global timeout)
      [publish OrderStatusQueryCommand SBE to egress → gateway → Artio send]
      [venue replies with ExecutionReports (ExecType=I)]
      [each reply flows back through ingress as ExecutionEvent]
      [RecoveryCoordinator.reconcileOrder(executionDecoder) for each]

  STEP 2: Query balances via REST (5s timeout)
      [publish BalanceQueryRequest SBE to egress → gateway REST poller]
      [REST response published as BalanceQueryResponse to ingress]
      [RecoveryCoordinator.reconcileBalance(responseDecoder)]

  STEP 3: Evaluate reconciliation result
      if discrepancy > toleranceUnits:
          activateKillSwitch("reconciliation_failed")
          return  // do NOT resume; recoveryLock stays set
      if discrepancy within tolerance:
          adjust PortfolioEngine positions
          recoveryLock[venueId] = false
          publish RecoveryCompleteEvent SBE to egress
  → RECONCILIATION_COMPLETE

  [Gateway receives RecoveryCompleteEvent → StrategyEngine notified → quotes can resume]
```

---

## 4.5 Admin Command Flow

```
Operator workstation
    → AdminCli tool (platform-tooling process)
    → AdminClient.sendCommand(DEACTIVATE_KILL_SWITCH)
        [encode AdminCommand SBE: commandType, venueId, operatorId, HMAC, nonce]
        [adminPublication.offer(buffer)]

Aeron admin channel ("aeron:udp?endpoint=gateway:9099")
    → Gateway admin Aeron subscription (gateway-egress thread)
        [validate HMAC signature against admin key]
        [if invalid: log ERROR, discard, no forwarding]
        [if valid: re-publish to main Aeron ingress publication]

Aeron IPC ingress
    → ClusteredService.onSessionMessage(AdminCommand templateId=33)
        [MessageRouter → AdminCommandHandler.onAdminCommand(decoder)]
            [Step 1: validate nonce — reject replays]
            [Step 2: validate HMAC — reject forgeries]
            [Step 3: execute command — e.g., riskEngine.deactivateKillSwitch()]
            [Step 4: publish RiskEvent SBE to egress for ops audit]
```

**Sequencing guarantee:** Admin commands enter through `onSessionMessage()` and are
sequenced by the Raft log. If the kill switch is deactivated and a fill event arrives
in the same Raft batch, their relative order is deterministic. No admin command can
race with a business event.

**Security model:** The gateway validates the HMAC before forwarding. Even if the
Aeron admin channel is compromised, a forged command without a valid HMAC is dropped
at the gateway before it reaches the cluster.

---

# 5. DOMAIN OWNERSHIP (STRICT)

## 5.1 Single-Writer Rules

In the cluster, **everything executes on the single `onSessionMessage()` thread**. There is no concurrent access to manage. The single-writer principle is enforced structurally by Aeron Cluster's execution model, not by locks.

| Domain | Owner | Enforcement Mechanism |
|---|---|---|
| Order State | `OrderManager` | Single-threaded cluster dispatch |
| Position / Inventory | `PortfolioEngine` | Single-threaded cluster dispatch |
| Market State (L2 books) | `InternalMarketView` | Single-threaded cluster dispatch |
| Risk State | `RiskEngine` | Single-threaded cluster dispatch |
| Recovery Locks | `RecoveryCoordinator` | Single-threaded cluster dispatch |
| FIX Session State | Artio (gateway process) | Artio library-thread; exposed as `VenueStatusEvent` |
| Ingress Timestamps | Gateway `MarketDataHandler` | `System.nanoTime()` called before Disruptor publish |
| Cluster Timestamps | `cluster.time()` | Deterministic; same on all cluster nodes |

## 5.2 Cross-Process Read Rules

1. **No component in the cluster reads gateway state directly.** All gateway state arrives as SBE messages through Aeron ingress.
2. **No component in the gateway reads cluster state directly.** All cluster output arrives as SBE messages through Aeron egress.
3. **`cluster.time()` is used for all time-based decisions inside the cluster.** `System.nanoTime()` is only called in the gateway for ingress timestamps.
4. **`cluster.logPosition()` is used for all ID generation inside the cluster.** No UUID, no random, no `System.currentTimeMillis()`.

---

# 6. STRATEGY SPECIFICATIONS

## 6.1 Market Making — Full Logic

### 6.1.1 Configuration

```java
// Immutable record — created at startup, read-only during trading
public record MarketMakingConfig(
    int    instrumentId,                    // int, not String
    int    venueId,                         // int, not String
    long   targetSpreadBps,
    long   inventorySkewFactorBps,
    long   baseQuoteSizeScaled,             // * 1e8
    long   maxQuoteSizeScaled,
    long   maxPositionLongScaled,
    long   maxPositionShortScaled,
    long   refreshThresholdBps,
    long   maxQuoteAgeMicros,               // in cluster.time() micros
    long   marketDataStalenessThresholdMicros,
    long   wideSpreadThresholdBps,
    long   maxTolerableSpreadBps,
    long   tickSizeScaled,                  // * 1e8
    long   lotSizeScaled,                   // * 1e8
    // FIX AMB-005 (v8): Added minQuoteSizeFractionBps — was referenced in §2.1.3 as
    // [strategy.marketMaking] minQuoteSizeFraction but missing from this record.
    // Stored as basis points (1 = 0.01%). Default 1000 bps = 10% of base quote size.
    // Example: 500 = 5% floor; 200 = 2% floor. Must be in [1, 9999].
    // Prevents the shrinking quote side from rounding to zero lot size at high inventory ratios.
    long   minQuoteSizeFractionBps          // default 1000 (= 10%)
) {}
```

### 6.1.2 State (per MarketMakingStrategy instance)

```java
// All primitives — zero allocation, fits in < 2 cache lines
private long   lastQuotedMid         = 0;
private long   lastQuoteTimeCluster  = 0;   // cluster.time() at last quote
private long   liveBidClOrdId        = 0;   // 0 = no live bid
private long   liveAskClOrdId        = 0;   // 0 = no live ask
private long   lastKnownPosition     = 0;   // updated by onFill()
private long   suppressedUntilMicros = 0;   // cluster.time()-based
private int    consecutiveRejections = 0;
private long   lastRejectionTimeMicros = 0;
private long   lastTradePriceScaled  = 0;   // updated from EntryType.TRADE ticks

// Cluster reference — populated in init(context); used for cluster.time() and logPosition()
private Cluster cluster;
// Other dependencies populated in init():
private InternalMarketView marketView;
private RiskEngine         riskEngine;
private RecoveryCoordinator recoveryCoordinator;
private PortfolioEngine    portfolioEngine;
```

### 6.1.3 onMarketData() — Full Algorithm

```java
public void onMarketData(MarketDataEventDecoder decoder) {
    // FIX AMB-009 (v9): TRADE ticks update lastTradePriceScaled but do NOT trigger quote refresh.
    // Used as fair-value fallback when spread is wider than wideSpreadThresholdBps.
    if (decoder.entryType() == EntryType.TRADE) {
        lastTradePriceScaled = decoder.priceScaled();
        return;
    }

    if (decoder.venueId() != config.venueId()) return;
    if (decoder.instrumentId() != config.instrumentId()) return;
    if (isSuppressed()) return;

    long bestBid = marketView.getBestBid(config.venueId(), config.instrumentId());
    long bestAsk = marketView.getBestAsk(config.venueId(), config.instrumentId());
    if (bestBid == INVALID || bestAsk == INVALID) return;

    long spread = bestAsk - bestBid;
    long midPrice = (bestBid + bestAsk) / 2;

    // FIX AMB-009 (v9): Select fairValue explicitly BEFORE computing skew so that the
    // wide-spread fallback (lastTradePriceScaled) flows through to the skew base consistently.
    // Previously §6.1.3 computed skew against midPrice regardless of which reference was being
    // quoted against, producing internally-inconsistent quotes under the wide-spread branch.
    long fairValue;
    if (spread * 10000 / midPrice > config.wideSpreadThresholdBps()) {
        if (lastTradePriceScaled > 0) {
            fairValue = lastTradePriceScaled;
        } else {
            suppress(WIDE_SPREAD, WIDE_SPREAD_COOLDOWN_MICROS);
            return;  // no reliable fair value available
        }
    } else {
        fairValue = midPrice;
    }

    if (spread * 10000 / midPrice > config.maxTolerableSpreadBps()) {
        suppress(WIDE_SPREAD, WIDE_SPREAD_COOLDOWN_MICROS);
        return;
    }

    // Inventory skew — all long arithmetic
    long maxLimit = lastKnownPosition >= 0
        ? config.maxPositionLongScaled()
        : config.maxPositionShortScaled();
    // inventoryRatio * 10000 to avoid floating point.
    // Overflow-safe: lastKnownPosition is bounded by maxPositionScaled (config),
    // so inventoryRatioBps stays in [-10000, +10000]. No SafeMulDiv needed here.
    long inventoryRatioBps = lastKnownPosition * 10000 / maxLimit; // range: [-10000, +10000]
    long skewBps = config.inventorySkewFactorBps() * inventoryRatioBps / 10000;
    // FIX AMB-009 (v9): skew base is `fairValue`, not `midPrice`. Under the wide-spread
    // fallback fairValue == lastTradePriceScaled; under normal spreads fairValue == midPrice
    // so this is a strict generalisation of the v8 formula.
    long adjustedFairValue = fairValue - (skewBps * fairValue / 10000);

    long halfSpread = config.targetSpreadBps() * adjustedFairValue / 20000;
    long ourBid = floorToTick(adjustedFairValue - halfSpread, config.tickSizeScaled());
    long ourAsk = ceilToTick(adjustedFairValue + halfSpread, config.tickSizeScaled());
    if (ourBid >= ourAsk) ourAsk = ourBid + config.tickSizeScaled();

    // Size calculation — all long
    long bidSize, askSize;
    // Inventory skew: REDUCE size on the side that increases exposure.
    // inventoryRatioBps > 0 means we are net long → shrink bid, grow ask.
    // inventoryRatioBps < 0 means we are net short → shrink ask, grow bid.
    // FIX AMB-005 (v8): minFloor was hardcoded as 1000 (10%) in v7. Now reads from
    // config.minQuoteSizeFractionBps() so the floor is configurable per Section 2.1.3.
    long minFloor = config.minQuoteSizeFractionBps();  // e.g. 1000 = 10% of 10000
    if (inventoryRatioBps > 0) {
        bidSize = config.baseQuoteSizeScaled() * Math.max(minFloor, 10000 - inventoryRatioBps) / 10000;
        askSize = config.baseQuoteSizeScaled() * (10000 + inventoryRatioBps) / 10000;
    } else {
        bidSize = config.baseQuoteSizeScaled() * (10000 - inventoryRatioBps) / 10000;
        askSize = config.baseQuoteSizeScaled() * Math.max(minFloor, 10000 + inventoryRatioBps) / 10000;
    }
    bidSize = Math.min(floorToLot(bidSize, config.lotSizeScaled()), config.maxQuoteSizeScaled());
    askSize = Math.min(floorToLot(askSize, config.lotSizeScaled()), config.maxQuoteSizeScaled());

    if (!requiresRefresh(ourBid, ourAsk, bidSize, askSize)) return;

    cancelLiveQuotes();
    if (bidSize > 0) submitQuote(Side.BUY,  ourBid, bidSize);
    if (askSize > 0) submitQuote(Side.SELL, ourAsk, askSize);
    lastQuotedMid = midPrice;
    lastQuoteTimeCluster = cluster.time();
}
```

### 6.1.4 isSuppressed()

```java
private boolean isSuppressed() {
    return riskEngine.isKillSwitchActive()
        || recoveryCoordinator.isInRecovery(config.venueId())
        || marketView.isStale(config.venueId(), config.instrumentId())
        || (suppressedUntilMicros > 0 && cluster.time() < suppressedUntilMicros);
}
```

### 6.1.5 submitQuote()

```java
private void submitQuote(byte side, long price, long size) {
    long clOrdId = cluster.logPosition();  // deterministic, unique, no allocation
    OrderManager.createPendingOrder(clOrdId, config.venueId(), config.instrumentId(),
                                    side, OrdType.LIMIT, TimeInForce.GTD, price, size,
                                    STRATEGY_MARKET_MAKING);
    // SBE encode NewOrderCommand
    newOrderEncoder
        .wrapAndApplyHeader(egressBuffer, 0, messageHeaderEncoder)
        .clOrdId(clOrdId)
        .venueId(config.venueId())
        .instrumentId(config.instrumentId())
        .side(side)
        .ordType(OrdType.LIMIT)
        .timeInForce(TimeInForce.GTD)
        .priceScaled(price)
        .qtyScaled(size)
        .strategyId(STRATEGY_MARKET_MAKING);
    cluster.egressPublication().offer(egressBuffer, 0, newOrderEncoder.encodedLength() + HEADER_LENGTH);

    if (side == Side.BUY) liveBidClOrdId = clOrdId;
    else liveAskClOrdId = clOrdId;
}
```

---

### 6.1.6 cancelLiveQuotes()

```java
private void cancelLiveQuotes() {
    if (liveBidClOrdId != 0) {
        cancelEncoder
            .wrapAndApplyHeader(egressBuffer, 0, messageHeaderEncoder)
            .clOrdId(cluster.logPosition())
            .origClOrdId(liveBidClOrdId)
            .venueId(config.venueId())
            .instrumentId(config.instrumentId())
            .side(Side.BUY);
        cluster.egressPublication().offer(egressBuffer, 0,
            cancelEncoder.encodedLength() + HEADER_LENGTH);
        liveBidClOrdId = 0;
    }
    if (liveAskClOrdId != 0) {
        cancelEncoder
            .wrapAndApplyHeader(egressBuffer, 0, messageHeaderEncoder)
            .clOrdId(cluster.logPosition())
            .origClOrdId(liveAskClOrdId)
            .venueId(config.venueId())
            .instrumentId(config.instrumentId())
            .side(Side.SELL);
        cluster.egressPublication().offer(egressBuffer, 0,
            cancelEncoder.encodedLength() + HEADER_LENGTH);
        liveAskClOrdId = 0;
    }
}
```

---

### 6.1.7 requiresRefresh()

```java
// Returns true if the proposed quotes differ enough from current to warrant
// canceling and resubmitting. Prevents unnecessary cancel+quote churn.
private boolean requiresRefresh(long newBid, long newAsk,
                                  long newBidSize, long newAskSize) {
    // No live quotes — always submit
    if (liveBidClOrdId == 0 || liveAskClOrdId == 0) return true;

    // Price moved beyond refresh threshold
    long priceDelta     = Math.abs(newBid - lastQuotedMid);
    long refreshThresh  = lastQuotedMid * config.refreshThresholdBps() / 10000;
    if (priceDelta > refreshThresh) return true;

    // Size changed significantly (> 10% of base quote size)
    long sizeDelta = Math.abs(newBidSize - config.baseQuoteSizeScaled());
    if (sizeDelta > config.baseQuoteSizeScaled() / 10) return true;

    return false;
}
```

---

### 6.1.8 suppress()

```java
// Sets the suppression timer. isSuppressed() checks this on every onMarketData call.
// reason is a byte constant from SuppressReason — logged but not stored.
private void suppress(byte reason, long durationMicros) {
    suppressedUntilMicros = cluster.time() + durationMicros;
    log.debug("MM suppressed for {}µs reason={}", durationMicros, reason);
}
```

---

## 6.2 Arbitrage — Full Logic

### 6.2.1 Configuration

```java
public record ArbStrategyConfig(
    int    instrumentId,
    int[]  venueIds,                        // int[], not String[]
    long   minNetProfitBps,
    long[] takerFeeScaled,                  // indexed by venueId, * 1e8
    long[] baseSlippageBps,
    long[] slippageSlopeBps,
    long   referenceSize,
    long   maxArbPositionScaled,
    long   maxLegSubmissionGapMicros,
    long   legTimeoutClusterMicros,
    long   cooldownAfterFailureMicros
) {}
```

### 6.2.2 ArbAttempt State

```java
// All primitives — no UUID, no String, no Object
private long arbAttemptId       = 0;  // cluster.logPosition() at execution
private int  leg1VenueId        = 0;
private int  leg2VenueId        = 0;
private long leg1ClOrdId        = 0;
private long leg2ClOrdId        = 0;
private byte leg1Status         = LEG_PENDING;
private byte leg2Status         = LEG_PENDING;
private long leg1FillQtyScaled  = 0;
private long leg2FillQtyScaled  = 0;
private long arbCreatedCluster  = 0;  // cluster.time()
private boolean arbActive       = false;
```

### 6.2.3 executeArb() — Batch Egress Publish

```java
private void executeArb(int buyVenueId, int sellVenueId, long size,
                         long buyPrice, long sellPrice) {
    // Read logPosition() exactly once — it returns the same value for all calls
    // within a single onSessionMessage() dispatch. Deriving all three IDs from
    // a single base guarantees they are distinct and deterministic across all
    // cluster nodes and on Aeron Archive replay (fixes AC-ARB-006).
    long base    = cluster.logPosition();
    arbAttemptId = base;
    leg1ClOrdId  = base + 1;
    leg2ClOrdId  = base + 2;
    arbCreatedCluster = cluster.time();
    arbActive = true;

    // Encode both legs into a single egress buffer — minimizes leg gap
    int offset = 0;
    offset += encodeLeg(egressBuffer, offset, buyVenueId,  Side.BUY,  buyPrice,  size, leg1ClOrdId);
    offset += encodeLeg(egressBuffer, offset, sellVenueId, Side.SELL, sellPrice, size, leg2ClOrdId);

    // Single offer — gateway receives both in one fragment
    cluster.egressPublication().offer(egressBuffer, 0, offset);
}
```

### 6.2.4 onFill() — ArbAttempt Update

```java
public void onFill(FillEventDecoder decoder) {
    if (!arbActive) return;
    long clOrdId = decoder.clOrdId();

    if (clOrdId == leg1ClOrdId) {
        leg1FillQtyScaled += decoder.fillQtyScaled();
        if (decoder.isFinal() != BooleanType.FALSE) leg1Status = LEG_DONE;
    } else if (clOrdId == leg2ClOrdId) {
        leg2FillQtyScaled += decoder.fillQtyScaled();
        if (decoder.isFinal() != BooleanType.FALSE) leg2Status = LEG_DONE;
    }

    if (leg1Status == LEG_DONE && leg2Status == LEG_DONE) {
        long imbalance = leg1FillQtyScaled - leg2FillQtyScaled;
        if (Math.abs(imbalance) > config.lotSizeScaled()) {
            submitHedge(imbalance > 0 ? Side.SELL : Side.BUY,
                        Math.abs(imbalance),
                        imbalance > 0 ? leg2VenueId : leg1VenueId);
        }
        resetArbState();
    }
}

// FIX AMB-004 (v8): resetArbState() was incorrectly shown as a nested method inside
// onFill() in v7. Java does not permit nested method definitions. It is a private
// instance method of ArbStrategy, called from onFill() and onTimer().
private void resetArbState() {
    arbAttemptId              = 0;
    leg1VenueId               = 0;  leg2VenueId        = 0;
    leg1ClOrdId               = 0;  leg2ClOrdId        = 0;
    leg1Status                = LEG_PENDING;
    leg2Status                = LEG_PENDING;
    leg1FillQtyScaled         = 0;  leg2FillQtyScaled  = 0;
    arbCreatedCluster         = 0;
    arbActive                 = false;
    activeArbTimeoutCorrelId  = 0;
    // The scheduled timeout timer fires but onTimer() checks arbActive == false
    // and exits immediately — no explicit cancel needed.
}
```

### 6.2.5 ArbStrategy State on Cluster Restart

`ArbStrategy` state (`arbActive`, `leg1ClOrdId`, `leg2ClOrdId`, `leg1FillQtyScaled`, etc.)
is **intentionally not included in the Aeron Archive snapshot**.

`TradingClusteredService.onTakeSnapshot()` snapshots only `OrderManager`, `PortfolioEngine`,
`RiskEngine`, and `RecoveryCoordinator`. `StrategyEngine` and its strategies are excluded.

**Why this is safe:**

On cluster restart, `arbActive` resets to `false` — the strategy believes no arb is in flight.
However, the leg orders themselves ARE preserved in the `OrderManager` snapshot. When the
cluster comes back up, `RecoveryCoordinator` runs its reconciliation sequence and queries the
venue for every open order — including any arb leg that was pending at the time of the crash.
The venue responds with the actual order state:

- Leg filled during downtime → `RecoveryCoordinator` synthesizes a fill (`isSynthetic=true`)
- Leg still open at venue → treated as an orphan → `CancelOrderCommand` published
- Leg already canceled at venue → `OrderManager` force-transitions to CANCELED

The result is that post-restart order state is fully reconciled by `RecoveryCoordinator`
without `ArbStrategy` needing to know an arb was in flight. No duplicate `NewOrderCommand`
is submitted on restart because Aeron Cluster replays **inbound** messages (execution events
from venue) not **outbound** commands — `NewOrderCommand` is never retransmitted by the cluster.

**Operator note:** After a mid-arb crash, the reconciled position may have an imbalance
(one leg filled, one canceled). The imbalance will be reflected in `PortfolioEngine` after
reconciliation. The strategy will quote/hedge based on the actual post-restart position.

---

# 7. ORDER MODEL + STATE MACHINE

## 7.1 Order Fields

```java
// All primitives — no String on hot path
// Fits in approximately 5 cache lines (320 bytes)
public final class OrderState {
    // Identity — all int/long primitives
    long   clOrdId;              // cluster.logPosition() at creation — unique, deterministic
    long   venueClOrdId;         // venue's ClOrdId echo from tag 11 (parsed as long)
    String venueOrderId;         // FIX tag 37 — String unavoidable; stored off hot path
    int    venueId;              // int, not String
    int    instrumentId;         // int, not String
    int    strategyId;           // int, not String

    // Parameters
    byte   side;                 // Side.BUY=1, Side.SELL=2
    byte   ordType;              // OrdType.LIMIT=2, OrdType.MARKET=1
    byte   timeInForce;          // TimeInForce.*
    long   priceScaled;          // * 1e8; 0 for MARKET
    long   qtyScaled;            // * 1e8

    // State
    byte   status;               // OrderStatus.*
    long   cumFillQtyScaled;
    long   leavesQtyScaled;
    long   avgFillPriceScaled;   // VWAP of fills

    // Timestamps — nanos
    long   createdClusterTime;   // cluster.time() at creation
    long   sentNanos;            // System.nanoTime() at FIX send (from gateway VenueStatusEvent)
    long   firstAckNanos;        // from ExecutionEvent.ingressTimestampNanos of first ACK
    long   lastUpdateNanos;
    long   exchangeTimestampNanos; // FIX tag 60

    // Reject info
    int    rejectCode;           // OrdRejReason tag 103
    // rejectReason String stored in separate off-path map; not in hot OrderState

    // Replace chain
    long   replacedByClOrdId;    // if this was replaced
}
```

**`venueOrderId` is the only `String` in `OrderState`.** It is only written once (on first ACK), never compared on the hot path. A separate `LongObjectHashMap<String>` maps `clOrdId → venueOrderId` for cancel requests that need tag 37.

## 7.2 Order Status Constants

```java
// byte constants — no enum boxing on hot path
public final class OrderStatus {
    public static final byte PENDING_NEW     = 0;
    public static final byte NEW             = 1;
    public static final byte PARTIALLY_FILLED = 2;
    public static final byte FILLED          = 3;
    public static final byte PENDING_CANCEL  = 4;
    public static final byte CANCELED        = 5;
    public static final byte PENDING_REPLACE = 6;
    public static final byte REPLACED        = 7;
    public static final byte REJECTED        = 8;
    public static final byte EXPIRED         = 9;

    // Terminal states bitmask for fast check
    private static final long TERMINAL_MASK =
        (1L << FILLED) | (1L << CANCELED) | (1L << REPLACED) |
        (1L << REJECTED) | (1L << EXPIRED);

    public static boolean isTerminal(byte status) {
        return (TERMINAL_MASK & (1L << status)) != 0;
    }
}
```

## 7.3 State Transition Table

| Current | Trigger (SBE ExecType) | Next | Action |
|---|---|---|---|
| PENDING_NEW | `execType=NEW (0)` | NEW | Set venueOrderId, firstAckNanos |
| PENDING_NEW | `execType=REJECTED (8)` | REJECTED | Set rejectCode |
| PENDING_NEW | `execType=FILL (F)` | PARTIALLY_FILLED or FILLED | Apply fill |
| NEW | `execType=FILL (F)`, partial | PARTIALLY_FILLED | Apply partial fill |
| NEW | `execType=FILL (F)`, complete | FILLED | Terminal |
| NEW | cancel command sent | PENDING_CANCEL | — |
| NEW | replace command sent | PENDING_REPLACE | — |
| PARTIALLY_FILLED | `execType=FILL (F)`, complete | FILLED | Terminal |
| PARTIALLY_FILLED | `execType=FILL (F)`, partial | PARTIALLY_FILLED | Apply fill |
| PARTIALLY_FILLED | cancel command sent | PENDING_CANCEL | — |
| PENDING_CANCEL | `execType=CANCELED (4)` | CANCELED | Terminal |
| PENDING_CANCEL | `execType=FILL (F)` | FILLED or PARTIALLY_FILLED | Cancel race; fill wins |
| PENDING_REPLACE | `execType=CANCELED (4)` | PENDING_NEW | Cancel confirmed; OrderManager emits NewOrderCommand for replacement leg |
| PENDING_REPLACE | `execType=REPLACED (5)` | REPLACED | Terminal; new order created (MsgType=G venues only) |
| PENDING_REPLACE | `execType=REJECTED (8)` | NEW | Replace rejected; original order still live |
| PENDING_REPLACE | `execType=FILL (F)` | PARTIALLY_FILLED or FILLED | Fill arrived before cancel ACK; cancel still in flight |
| NEW or PARTIALLY_FILLED | `execType=EXPIRED (C)` | EXPIRED | Terminal |

## 7.4 Invalid Transition Handling

```java
// Inside OrderManager.onExecution():
if (OrderStatus.isTerminal(order.status)) {
    metricsCounters.increment(DUPLICATE_EXECUTION_REPORT_COUNTER);
    return;  // silently discard; terminal orders cannot transition
}

if (!isValidTransition(order.status, execType)) {
    metricsCounters.increment(INVALID_TRANSITION_COUNTER);
    // Publish RiskEvent SBE with type=ORDER_STATE_ERROR
    // Do NOT transition
    // If it's a fill: apply it anyway — fills are revenue, never discard
    if (isFill(execType)) applyFill(order, decoder);
    return;
}
```

## 7.5 Idempotency

- `clOrdId` = `cluster.logPosition()` — globally unique within the cluster lifetime
- `execId` (FIX tag 17) stored in `LongHashSet execIdsSeen` — checked on every ExecutionEvent
- If `execId` already in set: discard with metrics increment
- Set pruned to last 10,000 entries on a sliding window to bound memory

---

## 7.6 OrderState Object Pool

`OrderState` objects are pre-allocated at startup and recycled after each order reaches a terminal state, eliminating GC pressure on the hot path.

### Pool Specification

| Property | Value | Rationale |
|---|---|---|
| Pool size | 2048 | Max concurrent open orders with headroom; power of 2 |
| Pool type | Pre-allocated stack-based | LIFO; hot cache line reuse |
| Overflow policy | Warn + heap allocate | Safety over latency in edge case |
| Reset on return | All primitive fields zeroed; venueOrderId = null | Prevents state bleed |
| Thread safety | None needed | All access on cluster-service thread |

### Pool Implementation

```java
package ig.rueishi.nitroj.exchange.order;

public final class OrderStatePool {

    private static final int POOL_SIZE       = 2048;
    private static final int WARN_THRESHOLD  = POOL_SIZE - 100;

    private final OrderState[] pool = new OrderState[POOL_SIZE];
    private int top = -1;
    private int allocated = 0;  // heap overflow count (for metrics)

    public OrderStatePool() {
        for (int i = 0; i < POOL_SIZE; i++) pool[i] = new OrderState();
        top = POOL_SIZE - 1;
    }

    /** Claim a reset OrderState from the pool. O(1). Never returns null. */
    public OrderState claim() {
        if (top >= 0) {
            OrderState order = pool[top--];
            order.reset();
            return order;
        }
        allocated++;
        metricsCounters.increment(ORDER_POOL_EXHAUSTED_COUNTER);
        log.warn("OrderStatePool exhausted; allocating. Total overflow: {}", allocated);
        return new OrderState();
    }

    /** Return an OrderState to the pool after terminal state. O(1). */
    public void release(OrderState order) {
        if (top < POOL_SIZE - 1) {
            order.reset();
            pool[++top] = order;
        }
        // Overflow object discarded; GC will collect it
    }

    public int available() { return top + 1; }
}
```

### OrderState.reset()

```java
// In OrderState — zeroes every field before pool reuse:
public void reset() {
    clOrdId = 0; venueClOrdId = 0; venueOrderId = null;
    venueId = 0; instrumentId = 0; strategyId = 0;
    side = 0; ordType = 0; timeInForce = 0;
    priceScaled = 0; qtyScaled = 0;
    status = OrderStatus.PENDING_NEW;
    cumFillQtyScaled = 0; leavesQtyScaled = 0; avgFillPriceScaled = 0;
    createdClusterTime = 0; sentNanos = 0; firstAckNanos = 0;
    lastUpdateNanos = 0; exchangeTimestampNanos = 0;
    rejectCode = 0; replacedByClOrdId = 0;
}
```

### OrderManagerImpl Pool Integration

```java
// In OrderManagerImpl:
private final OrderStatePool pool = new OrderStatePool();
private final LongObjectHashMap<OrderState> liveOrders = new LongObjectHashMap<>(2048);

@Override
public void createPendingOrder(long clOrdId, ...) {
    OrderState order = pool.claim();  // no allocation on hot path
    order.clOrdId = clOrdId;
    // ... populate fields
    liveOrders.put(clOrdId, order);
}

// When order reaches terminal state:
private void onTerminal(OrderState order) {
    liveOrders.remove(order.clOrdId);
    pool.release(order);  // return to pool
}

// resetAll() — called by TradingClusteredService.resetWarmupState().
// MUST drain the pool before clearing the live orders map.
// If liveOrders is cleared without releasing each slot, the pool leaks
// those slots and enters live trading below full capacity.
public void resetAll() {
    // ORDERING: pool.release() MUST be called for every live order BEFORE liveOrders.clear().
    // If liveOrders is cleared first, the references are lost and those pool slots leak —
    // the pool enters live trading below full capacity, triggering heap allocation on the
    // first real order submission.
    liveOrders.forEach((clOrdId, order) -> pool.release(order));  // return all slots first
    liveOrders.clear();  // then clear the map
    // pool is now back to full capacity (top == POOL_SIZE - 1 == 2047)
}
```

---

# 8. EVENT SCHEMAS — SBE DEFINITIONS

All inter-process events are SBE-encoded. All intra-cluster communication is direct method calls with SBE decoder parameters. No Java objects cross process boundaries.

## 8.1 SBE Schema Header

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe"
                   package="ig.rueishi.nitroj.exchange.messages"
                   id="1"
                   version="1"
                   semanticVersion="5.2"
                   description="Trading Platform Internal Events"
                   byteOrder="littleEndian">
  <types>
    <primitive name="int8"    primitiveType="int8"/>
    <primitive name="int16"   primitiveType="int16"/>
    <primitive name="int32"   primitiveType="int32"/>
    <primitive name="int64"   primitiveType="int64"/>
    <primitive name="uint8"   primitiveType="uint8"/>

    <type name="priceScaled"      primitiveType="int64" description="Price * 1e8"/>
    <type name="qtyScaled"        primitiveType="int64" description="Qty * 1e8"/>
  
  <!-- Timestamp types — two distinct sources, never interchangeable:
       gatewayNanos  = System.nanoTime() — nanosecond, gateway process, non-deterministic across nodes
       clusterMicros = cluster.time()    — microsecond, cluster process, deterministic on all 3 nodes
       Converting: clusterMicros × 1000 gives nanosecond-scaled value (no sub-microsecond precision added) -->
  <type name="gatewayNanos"     primitiveType="int64" description="System.nanoTime() at the gateway process. Nanosecond resolution. Written by gateway thread only — never cluster.time(), which is microseconds. Do not mix with clusterMicros fields without ×1000 conversion."/>
  <type name="clusterMicros"    primitiveType="int64" description="cluster.time() from Aeron Cluster. Microsecond resolution. Deterministic and identical across all 3 cluster nodes. Written by cluster-service thread only — never System.nanoTime()."  />
    <type name="bps"              primitiveType="int64" description="Basis points * 1"/>
    <type name="venueId"          primitiveType="int32" description="Registry-assigned venue int ID"/>
    <type name="instrumentId"     primitiveType="int32" description="Registry-assigned instrument int ID"/>
    <type name="strategyId"       primitiveType="int16" description="Strategy int constant"/>
    <type name="clOrdIdType"      primitiveType="int64" description="cluster.logPosition()-based order ID"/>

    <enum name="Side" encodingType="int8">
      <validValue name="BUY">1</validValue>
      <validValue name="SELL">2</validValue>
    </enum>
    <enum name="OrdType" encodingType="int8">
      <validValue name="MARKET">1</validValue>
      <validValue name="LIMIT">2</validValue>
    </enum>
    <enum name="TimeInForce" encodingType="int8">
      <validValue name="DAY">0</validValue>
      <validValue name="GTC">1</validValue>
      <validValue name="IOC">3</validValue>
      <validValue name="FOK">4</validValue>
    </enum>
    <enum name="ExecType" encodingType="int8">
      <validValue name="NEW">0</validValue>
      <validValue name="PARTIAL_FILL">1</validValue>
      <validValue name="CANCELED">4</validValue>
      <validValue name="REPLACED">5</validValue>
      <validValue name="REJECTED">8</validValue>
      <validValue name="EXPIRED">12</validValue>
      <validValue name="FILL">15</validValue>  <!-- FIX ExecType 'F' (ASCII 70) mapped to internal enum value 15. The mapping is arbitrary — 15 is an available slot. See ExecutionHandler.mapExecType() for the explicit 'F'→15 translation. -->
      <validValue name="ORDER_STATUS">9</validValue>
    </enum>
    <enum name="EntryType" encodingType="int8">
      <validValue name="BID">0</validValue>
      <validValue name="ASK">1</validValue>
      <validValue name="TRADE">2</validValue>
    </enum>
    <enum name="UpdateAction" encodingType="int8">
      <validValue name="NEW">0</validValue>
      <validValue name="CHANGE">1</validValue>
      <validValue name="DELETE">2</validValue>
    </enum>
    <enum name="VenueStatus" encodingType="int8">
      <validValue name="CONNECTED">1</validValue>
      <validValue name="DISCONNECTED">2</validValue>
    </enum>
    <enum name="BooleanType" encodingType="int8">
      <validValue name="FALSE">0</validValue>
      <validValue name="TRUE">1</validValue>
    </enum>
    <enum name="RiskEventType" encodingType="int8">
      <validValue name="KILL_SWITCH_ACTIVATED">1</validValue>
      <validValue name="KILL_SWITCH_DEACTIVATED">2</validValue>
      <validValue name="SOFT_LIMIT_BREACH">3</validValue>
      <validValue name="HARD_LIMIT_REJECT">4</validValue>
      <validValue name="RECOVERY_LOCK_SET">5</validValue>
      <validValue name="RECOVERY_LOCK_CLEARED">6</validValue>
      <validValue name="ORDER_STATE_ERROR">7</validValue>
    </enum>
  </types>
```

## 8.2 MarketDataEvent (templateId=1) — Gateway → Cluster

```xml
  <sbe:message name="MarketDataEvent" id="1" description="Normalized FIX market data entry">
    <field name="venueId"               id="1"  type="venueId"/>
    <field name="instrumentId"          id="2"  type="instrumentId"/>
    <field name="entryType"             id="3"  type="EntryType"/>
    <field name="updateAction"          id="4"  type="UpdateAction"/>
    <field name="priceScaled"           id="5"  type="priceScaled"/>
    <field name="sizeScaled"            id="6"  type="qtyScaled"/>
    <field name="priceLevel"            id="7"  type="int32"           description="0=best, 1=second, etc."/>
    <field name="ingressTimestampNanos" id="8"  type="gatewayNanos"  description="System.nanoTime() at gateway"/>
    <field name="exchangeTimestampNanos" id="9" type="gatewayNanos"  description="FIX tag 60 converted"/>
    <field name="fixSeqNum"             id="10" type="int32"           description="FIX MsgSeqNum tag 34"/>
  </sbe:message>
```

## 8.3 ExecutionEvent (templateId=2) — Gateway → Cluster

```xml
  <sbe:message name="ExecutionEvent" id="2" description="Normalized FIX ExecutionReport">
    <field name="clOrdId"               id="1"  type="clOrdIdType"/>
    <field name="venueId"               id="2"  type="venueId"/>
    <field name="instrumentId"          id="3"  type="instrumentId"/>
    <field name="execType"              id="4"  type="ExecType"/>
    <field name="side"                  id="5"  type="Side"/>
    <field name="fillPriceScaled"       id="6"  type="priceScaled"     description="tag 31; 0 if no fill"/>
    <field name="fillQtyScaled"         id="7"  type="qtyScaled"       description="tag 32; 0 if no fill"/>
    <field name="cumQtyScaled"          id="8"  type="qtyScaled"       description="tag 14"/>
    <field name="leavesQtyScaled"       id="9"  type="qtyScaled"       description="tag 151"/>
    <field name="rejectCode"            id="10" type="int32"           description="tag 103; 0 if no reject"/>
    <field name="ingressTimestampNanos" id="11" type="gatewayNanos"/>
    <field name="exchangeTimestampNanos" id="12" type="gatewayNanos" description="tag 60"/>
    <field name="fixSeqNum"             id="13" type="int32"/>
    <field name="isFinal"               id="14" type="BooleanType"     description="true if order now closed"/>
    <!-- venueOrderId (tag 37) is variable-length; sent as SBE varDataEncoding -->
    <data name="venueOrderId"           id="15" type="varStringEncoding"/>
    <data name="execId"                 id="16" type="varStringEncoding" description="tag 17 for dedup"/>
  </sbe:message>
```

## 8.4 VenueStatusEvent (templateId=3) — Gateway → Cluster

```xml
  <sbe:message name="VenueStatusEvent" id="3" description="FIX session connect/disconnect">
    <field name="venueId"               id="1"  type="venueId"/>
    <field name="status"                id="2"  type="VenueStatus"/>
    <field name="ingressTimestampNanos" id="3"  type="gatewayNanos"/>
  </sbe:message>
```

## 8.5 BalanceQueryResponse (templateId=4) — Gateway → Cluster

```xml
  <sbe:message name="BalanceQueryResponse" id="4" description="REST balance query result">
    <field name="venueId"               id="1"  type="venueId"/>
    <field name="instrumentId"          id="2"  type="instrumentId"/>
    <field name="balanceScaled"         id="3"  type="qtyScaled"  description="Available balance * 1e8"/>
    <field name="queryTimestampNanos"   id="4"  type="gatewayNanos"/>
  </sbe:message>
```

## 8.6 NewOrderCommand (templateId=10) — Cluster → Gateway

```xml
  <sbe:message name="NewOrderCommand" id="10" description="Order submission command to gateway">
    <field name="clOrdId"               id="1"  type="clOrdIdType"/>
    <field name="venueId"               id="2"  type="venueId"/>
    <field name="instrumentId"          id="3"  type="instrumentId"/>
    <field name="side"                  id="4"  type="Side"/>
    <field name="ordType"               id="5"  type="OrdType"/>
    <field name="timeInForce"           id="6"  type="TimeInForce"/>
    <field name="priceScaled"           id="7"  type="priceScaled"/>
    <field name="qtyScaled"             id="8"  type="qtyScaled"/>
    <field name="strategyId"            id="9"  type="strategyId"/>
  </sbe:message>
```

## 8.7 CancelOrderCommand (templateId=11) — Cluster → Gateway

```xml
  <sbe:message name="CancelOrderCommand" id="11" description="Cancel request to gateway">
    <field name="cancelClOrdId"         id="1"  type="clOrdIdType"  description="New ID for this cancel"/>
    <field name="origClOrdId"           id="2"  type="clOrdIdType"  description="Order being canceled"/>
    <field name="venueId"               id="3"  type="venueId"/>
    <field name="instrumentId"          id="4"  type="instrumentId"/>
    <field name="side"                  id="5"  type="Side"/>
    <field name="originalQtyScaled"     id="6"  type="qtyScaled"/>
    <!-- venueOrderId needed for tag 37 in FIX cancel -->
    <data name="venueOrderId"           id="7"  type="varStringEncoding"/>
  </sbe:message>
```

## 8.8 FillEvent (templateId=20) — Cluster Internal (direct method call, also archived)

```java
// Not a separate SBE message for intra-cluster use.
// The ExecutionEventDecoder IS the fill event — no intermediate object.
// PortfolioEngine.onFill(ExecutionEventDecoder decoder) reads fields directly.
// If publishing externally (e.g., to metrics sidecar), SBE encode:
```

```xml
  <sbe:message name="FillEvent" id="20" description="Fill notification for metrics/audit">
    <field name="clOrdId"               id="1"  type="clOrdIdType"/>
    <field name="venueId"               id="2"  type="venueId"/>
    <field name="instrumentId"          id="3"  type="instrumentId"/>
    <field name="strategyId"            id="4"  type="strategyId"/>
    <field name="side"                  id="5"  type="Side"/>
    <field name="fillPriceScaled"       id="6"  type="priceScaled"/>
    <field name="fillQtyScaled"         id="7"  type="qtyScaled"/>
    <field name="cumFillQtyScaled"      id="8"  type="qtyScaled"/>
    <field name="leavesQtyScaled"       id="9"  type="qtyScaled"/>
    <field name="isFinal"               id="10" type="BooleanType"/>
    <field name="isSynthetic"           id="11" type="BooleanType"  description="True if injected during reconciliation"/>
    <field name="fillTimestampNanos"    id="12" type="gatewayNanos"/>
    <field name="ingressTimestampNanos" id="13" type="gatewayNanos"/>
  </sbe:message>

  <sbe:message name="PositionEvent" id="21" description="Position snapshot for metrics/audit">
    <field name="venueId"               id="1"  type="venueId"/>
    <field name="instrumentId"          id="2"  type="instrumentId"/>
    <field name="netQtyScaled"          id="3"  type="qtyScaled"/>
    <field name="avgEntryPriceScaled"   id="4"  type="priceScaled"/>
    <field name="realizedPnlScaled"     id="5"  type="priceScaled"/>
    <field name="unrealizedPnlScaled"   id="6"  type="priceScaled"/>
    <field name="snapshotClusterTime"   id="7"  type="clusterMicros"/>
    <field name="triggeringClOrdId"     id="8"  type="clOrdIdType"/>
  </sbe:message>

  <sbe:message name="RiskEvent" id="22" description="Risk state change">
    <field name="riskEventType"         id="1"  type="RiskEventType"/>
    <field name="venueId"               id="2"  type="venueId"/>
    <field name="instrumentId"          id="3"  type="instrumentId"/>
    <field name="affectedClOrdId"       id="4"  type="clOrdIdType"  description="0 if not order-specific"/>
    <field name="limitValueScaled"      id="5"  type="int64"/>
    <field name="currentValueScaled"    id="6"  type="int64"/>
    <field name="eventClusterTime"      id="7"  type="clusterMicros"/>
  </sbe:message>
</sbe:messageSchema>
```

## 8.9 OrderStatusQueryCommand (templateId=30) — Cluster → Gateway

Instructs the gateway to send a FIX `OrderStatusRequest` (35=H) for one specific order.

```xml
  <sbe:message name="OrderStatusQueryCommand" id="30"
               description="Instructs gateway to send FIX OrderStatusRequest for one order">
    <field name="clOrdId"               id="1"  type="clOrdIdType"/>
    <field name="venueId"               id="2"  type="venueId"/>
    <field name="instrumentId"          id="3"  type="instrumentId"/>
    <field name="side"                  id="4"  type="Side"/>
    <data  name="venueOrderId"          id="5"  type="varStringEncoding"
           description="Needed for FIX tag 37 in OrderStatusRequest"/>
  </sbe:message>
```

## 8.10 BalanceQueryRequest (templateId=31) — Cluster → Gateway

Instructs the gateway REST poller to call `GET /accounts` on the venue.

```xml
  <sbe:message name="BalanceQueryRequest" id="31"
               description="Instructs gateway REST poller to query account balance">
    <field name="venueId"               id="1"  type="venueId"/>
    <field name="instrumentId"          id="2"  type="instrumentId"/>
    <field name="requestId"             id="3"  type="int64"
           description="cluster.logPosition() at time of request; echoed in response"/>
  </sbe:message>
```

## 8.11 RecoveryCompleteEvent (templateId=32) — Cluster → Gateway

Signals the gateway that recovery is complete and strategies may resume for the venue.

```xml
  <sbe:message name="RecoveryCompleteEvent" id="32"
               description="Signals gateway that recovery is complete; strategies may resume">
    <field name="venueId"                      id="1"  type="venueId"/>
    <field name="recoveryCompleteClusterTime"  id="2"  type="clusterMicros"/>
  </sbe:message>
```

## 8.12 AdminCommand (templateId=33) — External Operator → Cluster

Operator command routed through the gateway admin Aeron publication. Protected by HMAC-SHA256 and monotonic nonce. See Section 24 for the admin interface.

**Nonce strategy (FIX AMB-007, v8):** Two approaches appeared across the spec:
- §8.12 suggested `System.currentTimeMillis() * 1000L` (wall-clock microseconds)
- §24.3 showed a persisted `nonceFile` with a stored counter

**Authoritative resolution:** Use persistent file storage (§24.3 approach) as the primary
mechanism, with wall-clock microseconds as the bootstrap value on first run (when no file
exists). Rationale: the file guarantees monotonicity across process restarts even under
NTP clock steps; the wall-clock bootstrap provides a sensible non-zero starting value.

Bootstrap rule:
```
if nonceFile exists  → load nonce from file (guaranteed > last sent)
if nonceFile absent  → bootstrap: nonce = System.currentTimeMillis() * 1000L
                       (wall-clock microseconds; safe as first value since it's large and positive)
```

Clock-step guard: if `System.currentTimeMillis() * 1000L < loadedNonce`, the clock has
been stepped backward. The CLI must log `ERROR: nonce would decrease; clock stepped backward`
and refuse to send. The operator must either wait for the clock to advance past the stored
nonce, or manually edit the nonce file to a safe future value.

The `nonce` field in `AdminCommand` SBE encodes `(epochSeconds << 32) | sequenceNumber`
as described in §24.5 Nonce Bounds Summary. The `AdminCommandHandler` stale check uses
the upper 32 bits (epochSeconds) to reject nonces older than 24 hours.

```xml
  <!-- AdminCommandType enum — add to <types> section of messages.xml -->
  <enum name="AdminCommandType" encodingType="int8">
    <validValue name="DEACTIVATE_KILL_SWITCH">1</validValue>
    <validValue name="ACTIVATE_KILL_SWITCH">2</validValue>
    <validValue name="PAUSE_STRATEGY">3</validValue>
    <validValue name="RESUME_STRATEGY">4</validValue>
    <validValue name="RESET_DAILY_COUNTERS">5</validValue>
    <validValue name="TRIGGER_SNAPSHOT">6</validValue>
  </enum>

  <sbe:message name="AdminCommand" id="33"
               description="Operator command; routed through gateway admin Aeron publication">
    <field name="commandType"           id="1"  type="AdminCommandType"/>
    <field name="venueId"               id="2"  type="venueId"        description="0 = all venues"/>
    <field name="instrumentId"          id="3"  type="instrumentId"   description="0 = all instruments"/>
    <field name="operatorId"            id="4"  type="int32"/>
    <field name="hmacSignature"         id="5"  type="int64"
           description="Truncated HMAC-SHA256 of (commandType|venueId|instrumentId|operatorId|nonce)"/>
    <field name="nonce"                 id="6"  type="int64"
           description="Monotonically increasing; prevents replay attacks"/>
  </sbe:message>
```

## 8.13 ReplaceOrderCommand (templateId=34) — Cluster → Gateway

For venues that support `MsgType=G` natively: the cluster publishes `ReplaceOrderCommand`
and the gateway calls `routeReplace()` to send `MsgType=G` directly.

For Coinbase (which does not support `MsgType=G`): the cluster `OrderManager` drives
cancel + new sequencing directly. It publishes a `CancelOrderCommand` first, waits for
`ExecType=4`, then publishes a `NewOrderCommand`. The gateway is a pure forwarder in
this path — it never receives or processes a `ReplaceOrderCommand` for Coinbase.
See Section 9.7 for the full sequence and design rationale.

```xml
  <sbe:message name="ReplaceOrderCommand" id="34"
               description="Replace via cancel+new; gateway synthesizes the two FIX messages">
    <field name="origClOrdId"           id="1"  type="clOrdIdType"/>
    <field name="newClOrdId"            id="2"  type="clOrdIdType"/>
    <field name="venueId"               id="3"  type="venueId"/>
    <field name="instrumentId"          id="4"  type="instrumentId"/>
    <field name="side"                  id="5"  type="Side"/>
    <field name="newPriceScaled"        id="6"  type="priceScaled"/>
    <field name="newQtyScaled"          id="7"  type="qtyScaled"/>
    <field name="origQtyScaled"         id="8"  type="qtyScaled"
           description="Required for FIX cancel tag 38"/>
    <data  name="venueOrderId"          id="9"  type="varStringEncoding"
           description="Required for FIX cancel tag 37"/>
  </sbe:message>
```

---

# 9. FIX PROTOCOL MAPPING — ARTIO

## 9.1 FIX Engine: Artio

Artio replaces QuickFIX/J. Key differences in usage:

| Concern | QuickFIX/J (v1.0) | Artio (v2.0) |
|---|---|---|
| Message parsing | `FieldMap.getString(tag)` → allocates `String` | Direct buffer read → no allocation |
| Callback model | object-based FIX callback | `onMessage(long sessionId, DirectBuffer buffer, int offset, int actingBlockLength, int templateId)` |
| Session state | File-based store (sync disk write per message) | Aeron Archive (zero-copy, async) |
| FIX encode (outbound) | Build `Message` object → `Session.sendToTarget()` | SBE-like encoder writes directly to buffer → `session.send()` |
| Threading | Separate IO thread; `Application` callbacks on that thread | `FixLibrary` is poll-based; called on your duty-cycle thread |

## 9.2 Artio Session Configuration

```java
// Gateway startup
EngineConfiguration engineConfig = new EngineConfiguration()
    .libraryAeronChannel("aeron:ipc")
    .logFileDir("./artio-store/" + venueId)
    .replayIndexFileRecordCapacity(4096);

FixEngine fixEngine = FixEngine.launch(engineConfig);

LibraryConfiguration libraryConfig = new LibraryConfiguration()
    .libraryAeronChannels(List.of("aeron:ipc"))
    .sessionAcquireHandler(sessionHandler)
    .sessionExistsHandler(existingSessionHandler);

FixLibrary fixLibrary = FixLibrary.connect(libraryConfig);

// Initiate connection to Coinbase
SessionConfiguration sessionConfig = SessionConfiguration.builder()
    .address("fix.exchange.coinbase.com", 4198)
    .senderCompId("YOUR_COMP_ID")
    .targetCompId("Coinbase")
    .resetSeqNumOnLogon(false)
    .heartbeatIntervalInS(30)
    .build();
fixLibrary.initiate(sessionConfig);
```

## 9.3 ExecutionReport (MsgType=8) — Artio Handler

```java
// Artio provides generated codec classes from FIX XML dictionary
// Zero allocation — reads directly from buffer
public class ExecutionHandler implements SessionHandler {

    private final ExecutionReportDecoder execDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer sbeBuffer = new UnsafeBuffer(new byte[256]);

    @Override
    public Action onMessage(DirectBuffer buffer, int offset, int length,
                            int libraryId, Session session, int sessionType,
                            long timestampInNs, long position, char msgType) {
        if (msgType != '8') return CONTINUE;

        long ingressNanos = System.nanoTime();  // stamp immediately — before any decoding

        execDecoder.decode(buffer, offset, length);

        // Read tag 11 (ClOrdID) as long — Artio allows direct long parse
        long clOrdId = execDecoder.clOrdIDAsLong();  // zero allocation

        // Read tag 150 (ExecType) as char
        char artioExecType = execDecoder.execType();
        byte sbeExecType = mapExecType(artioExecType);

        // Encode SBE ExecutionEvent into pooled buffer
        executionEventEncoder
            .wrapAndApplyHeader(sbeBuffer, 0, headerEncoder)
            .clOrdId(clOrdId)
            .venueId(venueId)
            .instrumentId(IdRegistry.instrumentId(execDecoder.symbol()))
            .execType(sbeExecType)
            .side(mapSide(execDecoder.side()))
            .fillPriceScaled(isFill(sbeExecType) ? scalePrice(execDecoder.lastPx()) : 0)
            .fillQtyScaled(isFill(sbeExecType) ? scaleQty(execDecoder.lastQty()) : 0)
            .cumQtyScaled(scaleQty(execDecoder.cumQty()))
            .leavesQtyScaled(scaleQty(execDecoder.leavesQty()))
            .rejectCode(execDecoder.hasOrdRejReason() ? execDecoder.ordRejReason() : 0)
            .ingressTimestampNanos(ingressNanos)
            .exchangeTimestampNanos(parseTransactTime(execDecoder.transactTime()))
            .fixSeqNum(execDecoder.header().msgSeqNum())
            .isFinal(isTerminalExecType(sbeExecType) ? BooleanType.TRUE : BooleanType.FALSE);

        // Variable-length fields
        executionEventEncoder.putVenueOrderId(execDecoder.orderIDAscii());
        executionEventEncoder.putExecId(execDecoder.execIDAscii());

        // Publish to GatewayDisruptor
        gatewayDisruptor.publish(sbeBuffer, executionEventEncoder.encodedLength() + HEADER_LENGTH);
        return CONTINUE;
    }
}
```

## 9.4 ExecutionReport — Tag-by-Tag Mapping

| FIX Tag | Name | Artio accessor | SBE field | Notes |
|---|---|---|---|---|
| 11 | ClOrdID | `clOrdIDAsLong()` | `clOrdId` (int64) | Parse as long; Coinbase echoes our int64 |
| 17 | ExecID | `execIDAscii()` | `execId` (varString) | Used for dedup only |
| 37 | OrderID | `orderIDAscii()` | `venueOrderId` (varString) | Venue-assigned; needed for cancel tag 37 |
| 39 | OrdStatus | `ordStatus()` | Derived from execType | Do not use independently |
| 54 | Side | `side()` | `side` | '1'→BUY, '2'→SELL |
| 55 | Symbol | `symbol()` | `instrumentId` | Resolved to int via IdRegistry |
| 60 | TransactTime | `transactTime()` | `exchangeTimestampNanos` | Parse UTCTimestamp → epoch nanos |
| 14 | CumQty | `cumQty()` | `cumQtyScaled` | Scaled * 1e8 |
| 31 | LastPx | `lastPx()` | `fillPriceScaled` | 0 if no fill |
| 32 | LastQty | `lastQty()` | `fillQtyScaled` | 0 if no fill |
| 103 | OrdRejReason | `ordRejReason()` | `rejectCode` | Present only on reject |
| 150 | ExecType | `execType()` | `execType` (ExecType enum) | '0'=NEW, 'F'=FILL, '4'=CANCELED, '8'=REJECTED |
| 151 | LeavesQty | `leavesQty()` | `leavesQtyScaled` | Remaining quantity |
| 34 | MsgSeqNum | `header().msgSeqNum()` | `fixSeqNum` | For gap detection |

**Coinbase-specific quirks (unchanged from v1.0):**
- ExecType=`I` (Order Status) for `OrderStatusRequest` responses — map to `ORDER_STATUS` SBE enum; do not transition state
- ExecType=`F` for fills (not `1`); distinguish partial vs full by `LeavesQty == 0`
- Tag 37 (OrderID) is required in cancel requests; always cache it

## 9.5 NewOrderSingle (MsgType=D) — Artio Outbound

```java
// Artio 0.175 generated encoder — fields are populated once, then Session.trySend()
// lets Artio encode, frame, checksum, and publish the FIX message.
public void sendNewOrder(Session session, NewOrderCommandDecoder cmd) {
    newOrderSingleEncoder
        .clOrdID(cmd.clOrdId())              // long → written as ASCII digits
        .handlInst('1')
        .orderQty(unscaleQty(cmd.qtyScaled()))
        .ordType(mapOrdType(cmd.ordType()))
        .price(cmd.ordType() == OrdType.LIMIT ? unscalePrice(cmd.priceScaled()) : DecimalFloat.MISSING_FLOAT)
        .side(mapSide(cmd.side()))
        .symbol(IdRegistry.symbolOf(cmd.instrumentId()))  // int → String; cached, no alloc
        .timeInForce(mapTimeInForce(cmd.timeInForce()))
        .transactTime(epochClock.nanoTime())
        .account(config.account());

    for (int attempt = 0; attempt < 3; attempt++) {
        long result = session.trySend(newOrderSingleEncoder);
        if (result >= 0) {
            sendTimestampNanos = System.nanoTime();  // stamp AFTER accepted by Artio
            return;
        }
        LockSupport.parkNanos(1_000L);
    }

    metricsCounters.increment(ARTIO_BACK_PRESSURE_COUNTER);
    publishRejectEvent(cmd.clOrdId());
}
```

**Artio 0.175 outbound send contract:** TASK-013 must use `Session.trySend(Encoder)`, not
the older draft `tryClaim()/claimBuffer()/commit()` pattern. The generated concrete order
encoders (`NewOrderSingleEncoder`, `OrderCancelRequestEncoder`,
`OrderCancelReplaceRequestEncoder`, and `OrderStatusRequestEncoder`) come from
`platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml`. TASK-013 extends that
dictionary beyond Logon so Artio can generate the outbound application-message encoders used
by `ExecutionRouterImpl`.

| FIX Tag | Name | Value | Source |
|---|---|---|---|
| 35 | MsgType | D | Constant |
| 11 | ClOrdID | ASCII(clOrdId) | `cluster.logPosition()`-based; encoded as ASCII digits |
| 21 | HandlInst | 1 | Constant |
| 38 | OrderQty | unscale(qtyScaled) | SBE field |
| 40 | OrdType | 1=Market, 2=Limit | SBE field |
| 44 | Price | unscale(priceScaled) | SBE field; omitted for MARKET |
| 54 | Side | 1=Buy, 2=Sell | SBE field |
| 55 | Symbol | IdRegistry.symbolOf(id) | Cached lookup; string interned at startup |
| 59 | TimeInForce | 0/1/3/4 | SBE field |
| 60 | TransactTime | now | `epochClock.nanoTime()` |
| 1 | Account | config.account() | Startup config |

## 9.6 OrderCancelRequest (MsgType=F) — Artio Outbound

| FIX Tag | Name | Value | Notes |
|---|---|---|---|
| 35 | MsgType | F | Constant |
| 11 | ClOrdID | ASCII(cancelClOrdId) | New unique ID from `cluster.logPosition()` |
| 41 | OrigClOrdID | ASCII(origClOrdId) | Order being canceled |
| 37 | OrderID | venueOrderId | From `LongObjectHashMap`; needed by Coinbase |
| 54 | Side | 1 or 2 | Must match original |
| 55 | Symbol | IdRegistry.symbolOf(id) | Must match original |
| 38 | OrderQty | original qty | Must match original |
| 60 | TransactTime | now | — |

**Coinbase quirk:** If `venueOrderId` is not yet known (cancel sent before ACK), omit tag 37. Artio handles the session-level retry on reject.

## 9.7 OrderCancelReplaceRequest (MsgType=G)

Coinbase Advanced Trade FIX does not support `MsgType=G`. Replace is implemented as
a cancel + new sequence driven entirely by the cluster `OrderManager`. The gateway
is a **pure forwarder** — it sends whatever commands the cluster publishes and forwards
all execution events back without interpretation.

**Sequence:**

```
Strategy intent → cluster OrderManager:
  1. OrderManager transitions order to PENDING_REPLACE
  2. Publishes CancelOrderCommand to egress
  3. Waits for ExecType=4 (CANCELED) execution event

Gateway:
  4. Receives CancelOrderCommand → sends FIX 35=F to Coinbase
  5. Receives ExecType=4 from Coinbase → publishes ExecutionEvent to cluster

Cluster OrderManager (Section 7.3 — PENDING_REPLACE + ExecType=4):
  6. Transitions original order to REPLACED (terminal)
  7. Calls createPendingOrder() with new clOrdId, price, qty
  8. Publishes NewOrderCommand to egress

Gateway:
  9. Receives NewOrderCommand → sends FIX 35=D to Coinbase
```

**Design rationale — why sequencing is in the cluster, not the gateway:**

The `OrderManager` is the single writer for all order state (Section 5.1). Placing
replace sequencing in the gateway would mean order state diverges from the Aeron
Archive on failover and cannot be replayed deterministically. The 1–3ms additional
latency from two extra Aeron hops is acceptable for replace orders — replace is a
corrective action, not a latency-sensitive entry.

**Latency tradeoff acknowledged:** Gateway-side sequencing (a `pendingReplaces` map)
would be ~microseconds faster. It is not used because: (a) the map is not in the
Aeron Archive so it is lost on failover; (b) late cancel ACKs arriving after a
gateway-side timeout create undetectable state divergence; (c) the additional
complexity required to make the gateway-side approach correct exceeds the latency
benefit for replace orders. The full rationale is documented inline in this section above.

**Future venues supporting MsgType=G:** The `OrderCommandHandler` routing switch
receives a `ReplaceOrderCommand` SBE message. For venues that support native replace,
`routeReplace()` sends `MsgType=G` directly and the `PENDING_REPLACE + ExecType=5`
state machine row handles the response. The cluster sequence above is not used.

---

## 9.8 Coinbase Logon — Complete Implementation

### 9.8.1 Required Tags

Coinbase Advanced Trade FIX (4.2) requires a custom Logon message with:

| FIX Tag | Name | Value | Notes |
|---|---|---|---|
| 35 | MsgType | A | Standard logon |
| 98 | EncryptMethod | 0 | None |
| 108 | HeartBtInt | 30 | Seconds |
| 554 | Password | `<api_passphrase>` | From Coinbase API key setup |
| 96 | RawData | `<signature>` | HMAC-SHA256; see below |
| 95 | RawDataLength | length of RawData | Byte length of signature string |
| **8013** | **CancelOrdersOnDisconnect** | **Y** | **Coinbase-proprietary; ensures open orders are cancelled if the FIX session drops. See §9.8.3 for the dictionary-driven implementation.** |

### 9.8.2 Signature Formula

```
prehash = sendingTime          (tag 52, format: YYYYMMDD-HH:MM:SS.sss)
        + msgType              (tag 35, value: "A")
        + msgSeqNum            (tag 34, as string)
        + senderCompId         (tag 49)
        + targetCompId         (tag 56)
        + password             (tag 554)

signature = Base64(HMAC-SHA256(Base64Decode(apiSecret), UTF8Bytes(prehash)))

RawData (tag 96) = signature string (Base64-encoded, ~44 chars)
RawDataLength (tag 95) = signature.length()
```

**HMAC inputs must be in exact concatenation order with no delimiters.**

### 9.8.3 Artio Implementation — CoinbaseLogonStrategy

**Tag 8013 `CancelOrdersOnDisconnect` — dictionary-driven implementation (V9).**
Tag 8013 is a Coinbase-proprietary FIX field required on every logon; Coinbase cancels
all open orders on disconnect when it is set to `"Y"`. Artio does not expose a runtime
"set-arbitrary-tag" accessor on `LogonEncoder`; instead, Artio generates strongly-typed
setters from the FIX dictionary XML at build time (see Artio wiki "Codecs"). V9 commits
the dictionary-driven approach:

1. The build uses a customised FIX 4.2 dictionary (`platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml`) that extends the standard FIX 4.2 dictionary by declaring field `8013` with name `CancelOrdersOnDisconnect` on the `Logon` message.
2. Artio's code generation produces a strongly-typed setter on the generated `LogonEncoder`: `logon.cancelOrdersOnDisconnect((byte)'Y')`.
3. `CoinbaseLogonStrategy.configureLogon(...)` calls that setter directly (see code below).

Raw-buffer append after `session.send()`, manual FIX checksum recomputation, and
reflection into Artio internals are **explicitly out of scope** — they are brittle
against Artio version upgrades.

```java
package ig.rueishi.nitroj.exchange.gateway;

import uk.co.real_logic.artio.session.SessionCustomisationStrategy;
import uk.co.real_logic.artio.builder.LogonEncoder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Injects Coinbase-specific Logon fields into every outbound Logon message.
 * Registered with Artio LibraryConfiguration.sessionCustomisationStrategy().
 *
 * The `cancelOrdersOnDisconnect` setter below is generated by Artio from the customised
 * Coinbase FIX 4.2 dictionary (see §9.8.3 "dictionary-driven implementation" above).
 */
public final class CoinbaseLogonStrategy implements SessionCustomisationStrategy {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final String apiPassphrase;
    private final byte[] apiSecretDecoded;    // Base64-decoded secret; stored as bytes

    // Logon is not on the hot path — it occurs only at startup and on reconnect.
    // String allocation in configureLogon() is acceptable here.
    // The Mac instance is pre-allocated to avoid repeated getInstance() calls.
    private final Mac mac;
    private final byte[] prehashBytes = new byte[256];
    private final char[] signatureChars = new char[64];

    public CoinbaseLogonStrategy(String apiKey, String apiSecret, String apiPassphrase) {
        this.apiPassphrase    = apiPassphrase;
        this.apiSecretDecoded = Base64.getDecoder().decode(apiSecret);
        try {
            this.mac = Mac.getInstance(HMAC_ALGO);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 not available", e);
        }
    }

    @Override
    public void configureLogon(LogonEncoder logon, long sessionId) {
        String sendingTime = logon.header().sendingTimeAsString();
        String msgSeqNum   = String.valueOf(logon.header().msgSeqNum());
        String senderComp  = logon.header().senderCompIDAsString();
        String targetComp  = logon.header().targetCompIDAsString();

        String prehash = sendingTime + "A" + msgSeqNum + senderComp + targetComp + apiPassphrase;

        try {
            mac.init(new SecretKeySpec(apiSecretDecoded, HMAC_ALGO));
            byte[] hmacBytes = mac.doFinal(prehash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hmacBytes);

            logon.encryptMethod(0);
            logon.heartBtInt(30);
            logon.password(apiPassphrase);
            logon.rawData(signature);
            // rawDataLength (tag 95) is set automatically by Artio from rawData length

            // V9: tag 8013 CancelOrdersOnDisconnect="Y" — generated setter from
            // coinbase-fix42-dictionary.xml. NOT tag 9406 (a historical typo in v7/v8 drafts).
            logon.cancelOrdersOnDisconnect((byte)'Y');

        } catch (Exception e) {
            throw new RuntimeException("Logon signature failed", e);
        }
    }
}
```

### 9.8.4 Registration with Artio

```java
// In GatewayMain.java:
CoinbaseLogonStrategy logonStrategy = new CoinbaseLogonStrategy(
    config.venue(VENUE_COINBASE).apiKey(),
    config.venue(VENUE_COINBASE).apiSecret(),
    config.venue(VENUE_COINBASE).apiPassphrase()
);

LibraryConfiguration libraryConfig = new LibraryConfiguration()
    .libraryAeronChannels(List.of("aeron:ipc"))
    .sessionAcquireHandler(sessionHandler)
    .sessionCustomisationStrategy(logonStrategy)   // ← registered here
    .epochNanoClock(new SystemEpochNanoClock());
```

### 9.8.5 API Credentials Storage

Credentials are NEVER stored in committed config files or written to logs.

```
Production: HashiCorp Vault (or AWS Secrets Manager)
  - Path: secret/trading/coinbase/<venue_id>
  - Fields: api_key, api_secret (Base64), api_passphrase
  - Read at process startup via Vault Java SDK
  - Cached in-memory; never written to disk

Standalone development fallback: process environment
  COINBASE_API_KEY=...
  COINBASE_API_SECRET_BASE64=...
  COINBASE_API_PASSPHRASE=...
  - GatewayMain resolves these only when GatewayConfig lacks concrete credential values.
  - This fallback is for local/dev startup until the production Vault resolver is wired.
```

---

# 10. INTERNAL ORDER BOOK DESIGN

## 10.1 Model

L2 price-aggregated. Up to 20 levels per side. Backed by **Panama `MemorySegment`** (off-heap) — not subject to GC pressure.

## 10.2 Off-Heap Data Structure

```java
public final class L2OrderBook {
    private static final int MAX_LEVELS = 20;
    private static final long BYTES_PER_LEVEL = Long.BYTES * 2; // price + size
    private static final long BID_BASE = 0;
    private static final long ASK_BASE = MAX_LEVELS * BYTES_PER_LEVEL;
    private static final long TOTAL_BYTES = 2 * MAX_LEVELS * BYTES_PER_LEVEL;

    // Off-heap — not reachable by GC
    private final MemorySegment segment;
    private int bidCount = 0;
    private int askCount = 0;
    private long lastUpdateClusterTime = 0;
    private boolean stale = true;

    public L2OrderBook(Arena arena) {
        // 64-byte alignment ensures bid[0] (offset 0) and ask[0] (offset 320 = 5×64)
        // both land on cache-line boundaries. With Long.BYTES (8) alignment the segment
        // start could be at any 8-byte boundary, potentially splitting a hot read across
        // two cache lines. getBestBid() and getBestAsk() are called on every market data
        // tick — cache-line alignment is mandatory for predictable HFT latency.
        this.segment = arena.allocate(TOTAL_BYTES, 64);
    }

    // Zero-allocation price read — direct memory access
    public long getBidPrice(int level) {
        return segment.get(ValueLayout.JAVA_LONG, BID_BASE + level * BYTES_PER_LEVEL);
    }
    public long getBidSize(int level) {
        return segment.get(ValueLayout.JAVA_LONG, BID_BASE + level * BYTES_PER_LEVEL + Long.BYTES);
    }
    public long getAskPrice(int level) {
        return segment.get(ValueLayout.JAVA_LONG, ASK_BASE + level * BYTES_PER_LEVEL);
    }
    public long getAskSize(int level) {
        return segment.get(ValueLayout.JAVA_LONG, ASK_BASE + level * BYTES_PER_LEVEL + Long.BYTES);
    }
    public long getBestBid() { return bidCount > 0 ? getBidPrice(0) : Long.MIN_VALUE; }
    public long getBestAsk() { return askCount > 0 ? getAskPrice(0) : Long.MAX_VALUE; }
    public boolean isStale() { return stale; }
}
```

**Memory layout per book (both sides):**
```
[BID side — 20 * 16 bytes = 320 bytes]
  offset 0:   bid[0].price (long)     ← cache-line boundary (64-byte aligned segment start)
  offset 8:   bid[0].size  (long)
  offset 16:  bid[1].price (long)
  ...
[ASK side — 20 * 16 bytes = 320 bytes, starting at offset 320]
  offset 320: ask[0].price (long)     ← cache-line boundary (320 = 5 × 64)
  ...
Total: 640 bytes = 10 cache lines per book
```

**Bounds checking:** Panama `MemorySegment.get(ValueLayout, offset)` performs bounds checking unconditionally — out-of-bounds access throws `IndexOutOfBoundsException` regardless of `--enable-native-access`. This flag only removes the restriction on calling native methods, not the bounds guard on segment reads. No manual bounds checking is needed.

**Arena lifecycle:** One `Arena.ofConfined()` created on the cluster-service thread at startup. All books allocated from it. `ofConfined()` is the correct choice here for two reasons: (1) it enforces single-thread ownership — any accidental off-thread access to the segment throws `WrongThreadException` immediately, catching concurrency bugs at the point of occurrence; (2) its lifetime is explicit and deterministic, unlike `ofAuto()` which defers deallocation to GC. The arena is closed only on clean shutdown. Metrics sidecar reads position data via Agrona shared-memory counters, not by accessing the segment directly.

## 10.3 Update Algorithm

```java
// Called exclusively inside onSessionMessage() — single-threaded, no locks.
// clusterTimeMicros is passed in from InternalMarketView (which holds the cluster reference)
// so that L2OrderBook remains a pure data structure with no framework dependency.
public void apply(MarketDataEventDecoder decoder, long clusterTimeMicros) {
    lastUpdateClusterTime = clusterTimeMicros;
    stale = false;

    boolean isBid = decoder.entryType() == EntryType.BID;
    long price = decoder.priceScaled();
    long size  = decoder.sizeScaled();

    switch (decoder.updateAction()) {
        case NEW, CHANGE -> insertOrUpdate(isBid, price, size);
        case DELETE       -> delete(isBid, price);
    }
}

private void insertOrUpdate(boolean isBid, long price, long size) {
    int count = isBid ? bidCount : askCount;
    long base = isBid ? BID_BASE : ASK_BASE;
    boolean descending = isBid;  // bids: highest first; asks: lowest first

    // Find existing — linear scan; N=20 fits in L1 cache
    for (int i = 0; i < count; i++) {
        if (segment.get(ValueLayout.JAVA_LONG, base + i * BYTES_PER_LEVEL) == price) {
            segment.set(ValueLayout.JAVA_LONG, base + i * BYTES_PER_LEVEL + Long.BYTES, size);
            return;
        }
    }

    // Insert new level in sorted order
    if (count < MAX_LEVELS) {
        int insertPos = count;
        for (int i = 0; i < count; i++) {
            long p = segment.get(ValueLayout.JAVA_LONG, base + i * BYTES_PER_LEVEL);
            if (descending ? p < price : p > price) { insertPos = i; break; }
        }
        // Shift existing entries right
        for (int i = count; i > insertPos; i--) {
            long srcOffset = base + (i - 1) * BYTES_PER_LEVEL;
            long dstOffset = base + i * BYTES_PER_LEVEL;
            segment.set(ValueLayout.JAVA_LONG, dstOffset,              segment.get(ValueLayout.JAVA_LONG, srcOffset));
            segment.set(ValueLayout.JAVA_LONG, dstOffset + Long.BYTES, segment.get(ValueLayout.JAVA_LONG, srcOffset + Long.BYTES));
        }
        segment.set(ValueLayout.JAVA_LONG, base + insertPos * BYTES_PER_LEVEL,              price);
        segment.set(ValueLayout.JAVA_LONG, base + insertPos * BYTES_PER_LEVEL + Long.BYTES, size);
        if (isBid) bidCount++; else askCount++;
    } else {
        // Book is at MAX_LEVELS capacity. Compare new price against the worst existing level
        // (last entry: lowest bid or highest ask). If the new price is better, evict the worst
        // and insert the new — otherwise discard.
        int worstIdx = count - 1;
        long worstPrice = segment.get(ValueLayout.JAVA_LONG, base + worstIdx * BYTES_PER_LEVEL);
        boolean isBetter = descending ? price > worstPrice : price < worstPrice;
        if (!isBetter) return;  // genuinely worse than all existing levels — discard

        // Find sorted insertion position (worst slot will be overwritten)
        int insertPos = worstIdx;
        for (int i = 0; i < worstIdx; i++) {
            long p = segment.get(ValueLayout.JAVA_LONG, base + i * BYTES_PER_LEVEL);
            if (descending ? p < price : p > price) { insertPos = i; break; }
        }
        // Shift entries from insertPos..worstIdx-1 right by one (evicts worst)
        for (int i = worstIdx; i > insertPos; i--) {
            long srcOffset = base + (i - 1) * BYTES_PER_LEVEL;
            long dstOffset = base + i * BYTES_PER_LEVEL;
            segment.set(ValueLayout.JAVA_LONG, dstOffset,              segment.get(ValueLayout.JAVA_LONG, srcOffset));
            segment.set(ValueLayout.JAVA_LONG, dstOffset + Long.BYTES, segment.get(ValueLayout.JAVA_LONG, srcOffset + Long.BYTES));
        }
        segment.set(ValueLayout.JAVA_LONG, base + insertPos * BYTES_PER_LEVEL,              price);
        segment.set(ValueLayout.JAVA_LONG, base + insertPos * BYTES_PER_LEVEL + Long.BYTES, size);
        // count stays at MAX_LEVELS — no increment
    }
}
```

## 10.4 Staleness Check

```java
// Called by Strategy before every quote cycle.
// currentClusterTimeMicros is passed in from InternalMarketView — L2OrderBook
// holds no cluster reference; all time values are injected at call sites.
public boolean isStale(long stalenessThresholdMicros, long currentClusterTimeMicros) {
    return stale || (currentClusterTimeMicros - lastUpdateClusterTime) > stalenessThresholdMicros;
}
```

## 10.5 Multi-Venue Storage

```java
// In InternalMarketView:
// Key: packed long = ((long) venueId << 32) | instrumentId
// Value: L2OrderBook (off-heap)
private final LongObjectHashMap<L2OrderBook> books = new LongObjectHashMap<>();

private static long bookKey(int venueId, int instrumentId) {
    return ((long) venueId << 32) | (instrumentId & 0xFFFFFFFFL);
}

public long getBestBid(int venueId, int instrumentId) {
    L2OrderBook book = books.get(bookKey(venueId, instrumentId));
    return (book != null && !book.isStale(stalenessThresholdMicros, cluster.time()))
        ? book.getBestBid() : Long.MIN_VALUE;
}
```

---

# 11. PORTFOLIO + PnL

## 11.1 Position Storage

```java
// Key: packed long — no object allocation
// Value: Position — all long primitives
public final class Position {
    long netQtyScaled;          // positive=long, negative=short; * 1e8
    long avgEntryPriceScaled;   // VWAP entry; * 1e8
    long realizedPnlScaled;     // cumulative; * 1e8; quote currency
    long unrealizedPnlScaled;   // mark-to-market; refreshed on market data
    long lastUpdateClusterTime;
    long triggeringClOrdId;
}

// Storage:
private final LongObjectHashMap<Position> positions = new LongObjectHashMap<>();

private static long posKey(int venueId, int instrumentId) {
    return ((long) venueId << 32) | (instrumentId & 0xFFFFFFFFL);
}

// Cluster reference — set by TradingClusteredService.onStart() via setCluster().
// Used in onFill() for cluster.time() timestamp recording.
// MUST be non-null before the first onFill() call.
private Cluster cluster;

public void setCluster(Cluster cluster) { this.cluster = cluster; }
```

## 11.2 onFill() — Called Directly with SBE Decoder (No Object Creation)

```java
// Called inside onSessionMessage() — single-threaded
public void onFill(ExecutionEventDecoder decoder) {
    long key = posKey(decoder.venueId(), decoder.instrumentId());
    Position pos = positions.getIfAbsent(key, Position::new);  // Eclipse Collections

    long fillQty   = decoder.fillQtyScaled();
    long fillPrice = decoder.fillPriceScaled();
    boolean isBuy  = decoder.side() == Side.BUY;

    if (isBuy) updateOnBuy(pos, fillQty, fillPrice);
    else        updateOnSell(pos, fillQty, fillPrice);

    pos.lastUpdateClusterTime = cluster.time();
    pos.triggeringClOrdId = decoder.clOrdId();

    // Notify RiskEngine of new net position — direct call, no ring buffer.
    // updatePositionSnapshot() updates the netPositionSnapshot[] array used by
    // preTradeCheck() CHECK 4 (position limit). Must happen here so the next
    // pre-trade check sees the correct post-fill position immediately.
    //
    // ORDERING GUARANTEE: pos.netQtyScaled is the POST-FILL value at this point.
    // updateOnBuy() and updateOnSell() (Section 11.3) both update pos.netQtyScaled
    // internally before returning — the += fillQty is inside those methods, not here.
    // Do NOT move this call above updateOnBuy/updateOnSell, or the snapshot will
    // contain the pre-fill position and CHECK 4 will see stale state on the next order.
    //
    // IMPORTANT — this is NOT a duplicate of riskEngine.onFill() called by MessageRouter.
    // These are two distinct operations with different responsibilities:
    //   updatePositionSnapshot() → refreshes net position for CHECK 4 (pre-trade, called every order)
    //   riskEngine.onFill()      → updates daily volume counters and soft-limit metrics (post-fill only)
    // Do NOT merge these calls or move position update logic into onFill().
    riskEngine.updatePositionSnapshot(decoder.venueId(), decoder.instrumentId(), pos.netQtyScaled);
}
```

## 11.3 Average Price and Realized PnL

> **IMPORTANT:** All implementations here use `ScaledMath` from `platform-common`
> (see Section 11.5 and `ScaledMath.java`). Direct `price × qty / SCALE` multiplication
> **will overflow** for fills above ~0.014 BTC at $65K ($920 notional threshold). See Section 11.5 and T-024 ScaledMathTest.

```java
import ig.rueishi.nitroj.exchange.common.ScaledMath;

private static final long SCALE = Ids.SCALE;  // 100_000_000L

// ⚠️  NEVER write `a * b / SCALE` directly when both a and b are scaled longs.
// Any price × quantity product overflows Long.MAX_VALUE for fills above ~0.014 BTC at $65K
// at $65K. Use ScaledMath.vwap() for VWAP and ScaledMath.safeMulDiv() for PnL.
// See Section 11.5 for the overflow threshold table and ScaledMath implementation.

private void updateOnBuy(Position pos, long fillQty, long fillPrice) {
    if (pos.netQtyScaled >= 0) {
        // Adding to long position: compute new VWAP.
        // ScaledMath.vwap() handles price × qty overflow via BigDecimal fallback.
        pos.avgEntryPriceScaled = ScaledMath.vwap(
            pos.avgEntryPriceScaled, pos.netQtyScaled, fillPrice, fillQty);
        pos.netQtyScaled += fillQty;

    } else {
        // Covering a short position (buying to close).
        long coveredQty  = Math.min(fillQty, -pos.netQtyScaled);
        long residualQty = fillQty - coveredQty;

        // Short PnL = (entry price − buy-back price) × covered qty / SCALE
        long priceDiff = pos.avgEntryPriceScaled - fillPrice;  // positive = profit
        pos.realizedPnlScaled += ScaledMath.safeMulDiv(
            Math.abs(priceDiff), coveredQty, SCALE) * Long.signum(priceDiff);

        pos.netQtyScaled += fillQty;
        if (residualQty > 0) pos.avgEntryPriceScaled = fillPrice;  // flipped to long
        if (pos.netQtyScaled == 0) pos.avgEntryPriceScaled = 0;
    }
}

private void updateOnSell(Position pos, long fillQty, long fillPrice) {
    if (pos.netQtyScaled <= 0) {
        // Adding to short position: compute new VWAP.
        long absQty = -pos.netQtyScaled;
        pos.avgEntryPriceScaled = ScaledMath.vwap(
            pos.avgEntryPriceScaled, absQty, fillPrice, fillQty);
        pos.netQtyScaled -= fillQty;

    } else {
        // Reducing a long position (selling to close).
        long soldQty     = Math.min(fillQty, pos.netQtyScaled);
        long residualQty = fillQty - soldQty;

        // Long PnL = (sell price − entry price) × sold qty / SCALE
        long priceDiff = fillPrice - pos.avgEntryPriceScaled;  // positive = profit
        pos.realizedPnlScaled += ScaledMath.safeMulDiv(
            Math.abs(priceDiff), soldQty, SCALE) * Long.signum(priceDiff);

        pos.netQtyScaled -= fillQty;
        if (residualQty > 0) pos.avgEntryPriceScaled = fillPrice;  // flipped to short
        if (pos.netQtyScaled == 0) pos.avgEntryPriceScaled = 0;
    }
}
```

## 11.4 Unrealized PnL

```java
// Called on every MarketDataEvent for instruments we hold.
// Direct call from StrategyEngine.onMarketData() after book update.
public void refreshUnrealizedPnl(int venueId, int instrumentId, long markPriceScaled) {
    long key = posKey(venueId, instrumentId);
    Position pos = positions.get(key);
    if (pos == null || pos.netQtyScaled == 0) return;

    long priceDiff;
    long absQty;

    if (pos.netQtyScaled > 0) {
        priceDiff = markPriceScaled - pos.avgEntryPriceScaled;  // positive = profit
        absQty    = pos.netQtyScaled;
    } else {
        priceDiff = pos.avgEntryPriceScaled - markPriceScaled;  // positive = profit
        absQty    = -pos.netQtyScaled;
    }

    // Use safeMulDiv: mark-to-market overflows above ~0.014 BTC at $65K ($920 notional threshold)
    pos.unrealizedPnlScaled = ScaledMath.safeMulDiv(
        Math.abs(priceDiff), absQty, SCALE) * Long.signum(priceDiff);
}
```

## 11.5 Overflow Protection

Values in this system are stored as `long` scaled by 10^8 — the decimal part is
encoded in the integer. Multiplying two scaled longs squares the scale factor:

```
price_scaled × qty_scaled = (price_USD × 10^8) × (qty_BTC × 10^8)
                           = price_USD × qty_BTC × 10^16
```

This overflows `Long.MAX_VALUE` (~9.2 × 10^18) when `price_USD × qty_BTC × 10^16 > 9.2 × 10^18`,
i.e. when **`price_USD × qty_BTC > 920`** in unscaled terms.

At $65,000 BTC that means fills above **0.014 BTC** (~$910 notional) trigger overflow.
The threshold is price-sensitive — as BTC price rises, the threshold in BTC terms falls:

| BTC price | Overflow threshold (BTC qty) | Overflow threshold (notional) |
|---|---|---|
| $65,000 | 0.014 BTC | ~$910 |
| $100,000 | 0.0092 BTC | ~$920 |
| $200,000 | 0.0046 BTC | ~$920 |

The notional threshold stays near $920 regardless of price. The BTC quantity threshold
shrinks as price rises — re-profile if BTC price doubles from the price used in testing.

**Never write `a * b / SCALE` directly when both `a` and `b` are scaled longs.**
Use `ScaledMath.safeMulDiv(a, b, SCALE)` for one price × one quantity.
Use `ScaledMath.vwap(oldPrice, oldQty, fillPrice, fillQty)` for VWAP computation.
Both methods are in `platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ScaledMath.java`.

**Overflow threshold table:**

| Scenario | price_scaled | qty_scaled | Overflows? |
|---|---|---|---|
| 0.001 BTC @ $65K (typical MM) | 6.5e12 | 1e5 | No — $65 notional < $920 (hi=0, fast path) |
| 0.01 BTC @ $65K | 6.5e12 | 1e6 | No — $650 notional < $920 (hi=0, fast path) |
| 0.014 BTC @ $65K | 6.5e12 | 1.4e6 | **Threshold** — ~$910 notional |
| 0.1 BTC @ $65K | 6.5e12 | 1e7 | **Yes** (hi>0, BigDecimal) |
| 1 BTC @ $65K | 6.5e12 | 1e8 | **Yes** |
| 10 BTC @ $1M (extreme) | 1e14 | 1e9 | **Yes** |

**ScaledMath.safeMulDiv() — two-path implementation:**

```java
public static long safeMulDiv(long a, long b, long divisor) {
    long hi = Math.multiplyHigh(a, b);
    long lo = a * b;   // low 64 bits; unsigned bit-pattern is always correct

    if (hi == 0) {
        // Product fits in 64 bits — fast path: zero allocation, ~3 ns
        return Long.divideUnsigned(lo, divisor);
    }

    // hi != 0: product exceeds Long.MAX_VALUE.
    // BigDecimal is exact; allocation (~200–500 ns) is acceptable at fill frequency.
    //
    // MM fills: typical lot 0.001–0.01 BTC ($65–$650) — below threshold, fast path always.
    // Arb fills: leg sizes vary by config. At $65K, any leg > 0.014 BTC hits this path.
    //            At typical arb lot sizes (0.005–0.05 BTC), may or may not trigger.
    //
    // Fill frequency is low (< hundreds/minute even for aggressive arb) so BigDecimal
    // allocation at this rate is negligible for ZGC. Load test must include arb fill
    // scenarios at configured lot sizes to confirm GC pause SLA (< 1ms) is not breached.
    // Re-profile if BTC price or configured lot sizes change significantly.
    return BigDecimal.valueOf(a)
               .multiply(BigDecimal.valueOf(b))
               .divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP)
               .longValueExact();
}

public static long vwap(long oldPrice, long oldQty, long fillPrice, long fillQty) {
    long newQty = oldQty + fillQty;
    if (newQty == 0) return 0L;
    // No fast path — BigDecimal on every call. This is intentional and acceptable:
    // both price×qty products routinely overflow long at typical BTC prices, so the
    // fast path would rarely trigger and the added branch complexity is not worth it.
    // Fill frequency is << 1,000/min even under aggressive arb, so the allocation
    // rate is negligible for ZGC/C4. Re-evaluate only if fill rate exceeds ~10,000/min.
    return BigDecimal.valueOf(oldPrice)
               .multiply(BigDecimal.valueOf(oldQty))
               .add(BigDecimal.valueOf(fillPrice).multiply(BigDecimal.valueOf(fillQty)))
               .divide(BigDecimal.valueOf(newQty), RoundingMode.HALF_UP)
               .longValue();
}
```

See also: `T-024 ScaledMathTest` (Part D.2) for all test vectors including the
1 BTC @ $65K case that confirms the BigDecimal fallback path.

---

# 12. RISK ENGINE

## 12.1 State Fields — All Primitives, No Objects

**Note:** The field declarations below supersede any earlier drafts. Use exactly
these field names — Section 12.2 references them directly.

```java
public final class RiskEngine {

    // Kill switch
    private boolean killSwitchActive = false;

    // Per-venue recovery locks — array indexed by int venueId (size = Ids.MAX_VENUES)
    private final boolean[] recoveryLocks   = new boolean[Ids.MAX_VENUES];

    // Per-venue connectivity — array indexed by int venueId
    private final boolean[] venueConnected  = new boolean[Ids.MAX_VENUES];

    // Per-instrument net position snapshot — one long per instrument.
    // Positive = net long, Negative = net short.
    // Indexed directly by int instrumentId (0-based, matches Ids.INSTRUMENT_* constants).
    // Updated by PortfolioEngine.onFill() via direct call to updatePositionSnapshot().
    private final long[] netPositionSnapshot = new long[Ids.MAX_INSTRUMENTS];

    // Per-instrument hard limits — loaded from RiskConfig at startup
    private final long[] maxLongScaled      = new long[Ids.MAX_INSTRUMENTS];
    private final long[] maxShortScaled     = new long[Ids.MAX_INSTRUMENTS];  // stored as positive
    private final long[] maxOrderScaled     = new long[Ids.MAX_INSTRUMENTS];
    private final long[] maxNotionalScaled  = new long[Ids.MAX_INSTRUMENTS];

    // Global limits
    private int  maxOrdersPerSecond;
    private long maxDailyLossScaled;

    // Daily metrics (reset at midnight via cluster timer — see Section 12.6)
    private long dailyRealizedPnlScaled    = 0;
    private long dailyUnrealizedPnlScaled  = 0;
    private long dailyVolumeScaled         = 0;

    // Rate limiting — sliding window ring buffer
    private final long[] orderTimestampsMicros = new long[Ids.MAX_ORDERS_PER_WINDOW];
    private int  head    = 0;
    private int  count   = 0;
    private static final long WINDOW_MICROS = 1_000_000L;  // 1 second

    // Soft limit threshold — loaded from RiskConfig at startup
    private int softLimitPct = 80;  // Percent of hard limit that triggers a soft alert (default 80)

    // Cluster reference (set by TradingClusteredService.onStart via setCluster())
    private Cluster cluster;
}
```

## 12.2 Pre-Trade Check — 8 Steps, Direct Method Call, No Ring Buffer

```java
// Returns RiskDecision — a value record, stack-allocated by JIT
public RiskDecision preTradeCheck(int venueId, int instrumentId, byte side,
                                   long priceScaled, long qtyScaled, int strategyId) {

    // CHECK 1: Recovery lock (before kill switch — must check even if KS active)
    if (recoveryLocks[venueId])
        return RiskDecision.REJECT_RECOVERY;

    // CHECK 2: Kill switch
    if (killSwitchActive)
        return RiskDecision.REJECT_KILL_SWITCH;

    // CHECK 3: Max order size
    if (qtyScaled > maxOrderScaled[instrumentId])
        return RiskDecision.REJECT_ORDER_TOO_LARGE;

    // CHECK 4: Position limit (net position — see Section 12.1 and 23.1)
    long projectedPos = netPositionSnapshot[instrumentId]
                      + (side == Side.BUY ? qtyScaled : -qtyScaled);
    if (projectedPos > maxLongScaled[instrumentId])
        return RiskDecision.REJECT_MAX_LONG;
    if (projectedPos < -maxShortScaled[instrumentId])
        return RiskDecision.REJECT_MAX_SHORT;

    // CHECK 5: Notional — use ScaledMath for large price×qty products
    long notional = ScaledMath.safeMulDiv(qtyScaled, priceScaled, Ids.SCALE);
    if (notional > maxNotionalScaled[instrumentId])
        return RiskDecision.REJECT_MAX_NOTIONAL;

    // CHECK 6: Order rate limit (sliding window over last 1 second)
    pruneOrderWindow(cluster.time());
    if (count >= maxOrdersPerSecond)
        return RiskDecision.REJECT_RATE_LIMIT;

    // CHECK 7: Daily loss
    long totalDailyLoss = dailyRealizedPnlScaled + dailyUnrealizedPnlScaled;
    if (totalDailyLoss < -maxDailyLossScaled) {
        activateKillSwitch("daily_loss_limit");
        return RiskDecision.REJECT_DAILY_LOSS;
    }

    // CHECK 8: Venue connectivity
    if (!venueConnected[venueId])
        return RiskDecision.REJECT_VENUE_NOT_CONNECTED;

    // Soft limit checks — APPROVE but publish RiskEvent alert to egress
    // Use the correct limit based on position direction to support asymmetric limits.
    long pos = netPositionSnapshot[instrumentId];
    long posLimit = (pos >= 0) ? maxLongScaled[instrumentId] : maxShortScaled[instrumentId];
    long posRatio = (posLimit > 0) ? Math.abs(pos) * 100 / posLimit : 0;
    if (posRatio >= softLimitPct) {
        publishSoftAlert(RiskEventType.SOFT_LIMIT_BREACH, venueId, instrumentId);
    }
    long lossRatio = (maxDailyLossScaled > 0) ? (-totalDailyLoss) * 100 / maxDailyLossScaled : 0;
    if (lossRatio >= softLimitPct) {
        publishSoftAlert(RiskEventType.SOFT_LIMIT_BREACH, venueId, instrumentId);
    }
    // Note: soft limit does NOT reject. Order is approved. The alert is for monitoring only.

    // Approved — record order in rate window
    recordOrderInWindow(cluster.time());
    return RiskDecision.APPROVED;
}
```

## 12.3 RiskDecision — Stack-Allocated Value Record

```java
// JIT will scalar-replace this — zero heap allocation in steady state
public record RiskDecision(boolean approved, byte rejectCode) {
    public static final RiskDecision APPROVED              = new RiskDecision(true,  (byte) 0);
    public static final RiskDecision REJECT_RECOVERY       = new RiskDecision(false, (byte) 1);
    public static final RiskDecision REJECT_KILL_SWITCH    = new RiskDecision(false, (byte) 2);
    public static final RiskDecision REJECT_ORDER_TOO_LARGE = new RiskDecision(false, (byte) 3);
    public static final RiskDecision REJECT_MAX_LONG       = new RiskDecision(false, (byte) 4);
    public static final RiskDecision REJECT_MAX_SHORT      = new RiskDecision(false, (byte) 5);
    public static final RiskDecision REJECT_MAX_NOTIONAL   = new RiskDecision(false, (byte) 6);
    public static final RiskDecision REJECT_RATE_LIMIT     = new RiskDecision(false, (byte) 7);
    public static final RiskDecision REJECT_DAILY_LOSS     = new RiskDecision(false, (byte) 8);
    public static final RiskDecision REJECT_VENUE_NOT_CONNECTED = new RiskDecision(false, (byte) 9);
}
```

## 12.4 Kill Switch

```java
public void activateKillSwitch(String reason) {
    killSwitchActive = true;
    // Publish RiskEvent SBE to egress — ops observability
    riskEventEncoder
        .wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
        .riskEventType(RiskEventType.KILL_SWITCH_ACTIVATED)
        .eventClusterTime(cluster.time());
    cluster.egressPublication().offer(egressBuffer, 0, riskEventEncoder.encodedLength() + HEADER_LENGTH);

    // Cancel all live orders — direct call to OrderManager
    orderManager.cancelAllOrders();
}

// Deactivation: MANUAL ONLY via admin message through cluster ingress
// Admin tool sends AdminCommand SBE message through Aeron ingress
// NEVER automatic deactivation
public void deactivateKillSwitch() {
    killSwitchActive = false;
    // Publish RiskEvent KILL_SWITCH_DEACTIVATED to egress
}

private void publishSoftAlert(byte riskEventType, int venueId, int instrumentId) {
    // Publishes a soft-limit breach alert to cluster egress for ops monitoring.
    // Does NOT reject the order — called only after APPROVED decision.
    // Zero-allocation: uses pre-allocated riskEventEncoder and egressBuffer.
    riskEventEncoder
        .wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
        .riskEventType(riskEventType)
        .venueId(venueId)
        .instrumentId(instrumentId)
        .eventClusterTime(cluster.time());
    cluster.egressPublication().offer(egressBuffer, 0,
        riskEventEncoder.encodedLength() + HEADER_LENGTH);
}
```

## 12.5 Hard vs Soft Limits

| Limit | Type | Action |
|---|---|---|
| Recovery lock | Hard | REJECT all orders for venue |
| Kill switch | Hard | REJECT all; cancel all live |
| Max order size | Hard | REJECT |
| Max long/short position | Hard | REJECT |
| Max notional per order | Hard | REJECT |
| Max orders/second | Hard | REJECT |
| Daily loss limit | Hard | REJECT + activate kill switch |
| Venue not connected | Hard | REJECT |
| Position at 80% of limit | Soft | Allow + publish `RiskEvent.SOFT_LIMIT_BREACH` to egress |
| Daily loss at 50% of limit | Soft | Allow + publish `RiskEvent.SOFT_LIMIT_BREACH` to egress |
| Spread at 90% of max | Soft | Allow + publish alert to strategy |

---

## 12.6 Daily Reset Timer

Aeron Cluster timers are the correct mechanism for time-based events inside a `ClusteredService`. They fire as `onTimerEvent()` calls that travel through the Raft log — identical on all nodes.

```java
package ig.rueishi.nitroj.exchange.cluster;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import java.time.Instant;

public final class DailyResetTimer {

    private static final long DAILY_RESET_CORRELATION_ID = 1001L;  // unique, stable constant

    private Cluster cluster;
    private final RiskEngine riskEngine;
    private final PortfolioEngine portfolioEngine;
    private final EpochClock epochClock;  // Agrona SystemEpochClock — wall-clock millis

    public DailyResetTimer(RiskEngine riskEngine, PortfolioEngine portfolioEngine,
                            EpochClock epochClock) {
        this.riskEngine    = riskEngine;
        this.portfolioEngine = portfolioEngine;
        this.epochClock    = epochClock;
    }

    public void setCluster(Cluster cluster) { this.cluster = cluster; }

    /**
     * Called from TradingClusteredService.onStart().
     * Schedules the next midnight UTC reset.
     */
    public void scheduleNextReset() {
        long nowMs          = epochClock.time();
        long nextMidnightMs = computeNextMidnightUtcMs(nowMs);

        boolean scheduled = cluster.scheduleTimer(DAILY_RESET_CORRELATION_ID, nextMidnightMs);
        if (!scheduled) {
            log.warn("Daily reset timer not scheduled immediately; will retry on next poll");
        }
        log.info("Daily reset scheduled for: {}", Instant.ofEpochMilli(nextMidnightMs));
    }

    /**
     * Called from TradingClusteredService.onTimerEvent().
     * Only handles DAILY_RESET_CORRELATION_ID — all other IDs are ignored.
     */
    public void onTimer(long correlationId, long timestamp) {
        if (correlationId != DAILY_RESET_CORRELATION_ID) return;

        log.info("Daily reset firing at cluster time: {}", timestamp);

        riskEngine.resetDailyCounters();
        portfolioEngine.archiveDailyPnl(cluster.egressPublication());

        scheduleNextReset();  // schedule the next day's reset
    }

    private long computeNextMidnightUtcMs(long nowMs) {
        long msInDay = 86_400_000L;
        return (nowMs / msInDay + 1) * msInDay;
    }
}
```

**Wiring in ClusterMain.buildClusteredService():**
```java
DailyResetTimer timer = new DailyResetTimer(riskEngine, portfolio,
                                             new SystemEpochClock());
```

**Correlation ID rules:**
- `1001L` is reserved for the daily reset. Never reuse it for another timer.
- Arb leg timeout timers use `4000L + (arbAttemptId & 0xFFFFL)` — see Section 6.2.
- Recovery timeout timers use `2000L + venueId` and `3000L + venueId` — see Section 16.

# 13. THREADING MODEL

## 13.1 Gateway Process — Thread Inventory

| Thread Name | Count | Role | Notes |
|---|---|---|---|
| `artio-framer` | 1 | Artio FIX engine TCP IO + session management | Managed by Artio; do not block |
| `artio-library` | 1 | Artio duty-cycle poll; `onMessage()` callbacks | This is where MarketDataHandler runs |
| `rest-poller` | 1 | Polls venue REST API (balance queries) | Sleeps 1s between polls; not latency critical |
| `gateway-disruptor` | 1 | Single consumer of GatewayDisruptor ring buffer; AeronPublisher | CPU-pinned; busy-spin |
| `gateway-egress` | 1 | Polls Aeron cluster egress; dispatches OrderCommands | CPU-pinned; busy-spin |
| `media-driver` | External | Aeron MediaDriver (separate process) | Dedicated CPUs; not managed by gateway JVM |

**Gateway threading rule:** The artio-library thread is the **multi-producer** to the GatewayDisruptor. The gateway-disruptor thread is the **single consumer** that publishes to Aeron. These two CPU-pinned threads must not be preempted — assign to isolated CPUs.

## 13.2 Cluster Process — Thread Inventory

| Thread Name | Count | Role |
|---|---|---|
| `cluster-service` | 1 | `ClusteredService.onSessionMessage()` — ALL business logic |
| `cluster-timer` | 1 | Aeron Cluster timer callbacks (`onTimerEvent()`) |
| `cluster-election` | 1 | Raft leader election (managed by Aeron Cluster) |
| `archive-conductor` | 1 | Aeron Archive — replay and record (separate thread managed by Agrona agent) |
| `media-driver` | External | Aeron MediaDriver (separate process) |

**The single most important threading fact in this system:** All business logic — strategy, risk, order management, portfolio — executes on the **single `cluster-service` thread**. There is no concurrency to manage in the cluster. No locks, no ring buffers between components, no barriers. The Raft log is the synchronizer.

## 13.3 Gateway Disruptor — Intra-Gateway Fan-In

The GatewayDisruptor is a **multi-producer → single-consumer** Disruptor. It exists for one reason: the artio-library thread (running MarketDataHandler, ExecutionHandler, and VenueStatusHandler) and the rest-poller thread (running RestPoller) both need to publish to the same Aeron ingress publication. Aeron publications are **not thread-safe** for concurrent access. The Disruptor serializes all producers onto the single gateway-disruptor consumer thread. Note: AdminCommand does NOT go through the Disruptor — it arrives on the gateway-egress thread via a separate Aeron admin subscription and is forwarded directly to the cluster ingress publication.

```java
// Gateway startup
RingBuffer<GatewaySlot> disruptor = RingBuffer.createMultiProducer(
    GatewaySlot::new,
    4096,                          // 4K slots; each slot is a pre-allocated UnsafeBuffer
    new BusySpinWaitStrategy()     // gateway-disruptor thread is CPU-pinned; busy-spin is correct
);

// Single consumer — AeronPublisher
// Pre-allocated header decoder for templateId peek — no allocation on hot path
private final MessageHeaderDecoder headerPeek = new MessageHeaderDecoder();

BatchEventProcessor<GatewaySlot> publisher = new BatchEventProcessor<>(
    disruptor,
    disruptor.newBarrier(),
    (slot, sequence, endOfBatch) -> {
        headerPeek.wrap(slot.buffer, 0);
        boolean isFill = headerPeek.templateId() == ExecutionEventDecoder.TEMPLATE_ID;

        if (isFill) {
            // ExecutionEvents (fills) must NEVER be dropped.
            // Spin indefinitely — back-pressure here means the cluster is behind,
            // which is an ops emergency requiring immediate intervention.
            // Blocking the disruptor thread is correct: it stops new FIX messages
            // from piling up until the cluster catches up.
            long backPressureStart = System.nanoTime();
            while (aeronPublication.offer(slot.buffer, 0, slot.length) < 0) {
                if (System.nanoTime() - backPressureStart > 1_000_000L) {  // alert every 1ms
                    metricsCounters.increment(AERON_FILL_BACKPRESSURE_COUNTER);
                    backPressureStart = System.nanoTime();
                }
                // No sleep — busy-spin to resume as soon as cluster processes backlog
            }
        } else {
            // Market data, venue status, balance responses: bounded retry.
            // Stale market data is recoverable; a missed fill is a position error.
            long deadline = System.nanoTime() + 10_000L;  // 10µs
            while (aeronPublication.offer(slot.buffer, 0, slot.length) < 0) {
                if (System.nanoTime() > deadline) {
                    metricsCounters.increment(AERON_BACKPRESSURE_COUNTER);
                    break;  // drop market data under sustained back-pressure
                }
            }
        }
        slot.reset();  // clear buffer for reuse
    }
);
```

## 13.4 CPU Pinning — Bare Metal

```java
// Startup — pin threads using JNA call to pthread_setaffinity_np:
ThreadAffinityHelper.pin("artio-library",      CPU_2);
ThreadAffinityHelper.pin("gateway-disruptor",  CPU_3);
ThreadAffinityHelper.pin("gateway-egress",     CPU_4);
ThreadAffinityHelper.pin("cluster-service",    CPU_5);
// artio-framer and media-driver managed separately via taskset

// CPUs 0,1: OS and interrupts
// CPUs 2-6: trading threads (isolated via isolcpus kernel flag)
// CPUs 7+:  media driver, archive conductor
```

## 13.5 Cross-Thread Communication Summary

| From | To | Mechanism |
|---|---|---|
| artio-library → gateway-disruptor | GatewayDisruptor (MultiProducer ring buffer) |
| rest-poller → gateway-disruptor | GatewayDisruptor (MultiProducer) |
| gateway-disruptor → Aeron | `aeronPublication.offer()` (single-threaded consumer) |
| Aeron → cluster-service | `onSessionMessage()` callback (Aeron Cluster single-threaded) |
| cluster-service → Aeron egress | `cluster.egressPublication().offer()` |
| Aeron egress → gateway-egress | `egressSubscription.poll()` |
| gateway-egress → Artio | `artioSession.send()` (library-thread safe) |
| cluster-service → components | Direct method call (same thread) |

**There are no locks, no `synchronized` blocks, and no `java.util.concurrent` blocking structures on any hot path.**

---

# 14. RING BUFFER / EVENT TRANSPORT

## 14.1 Scope — Gateway Only

The Disruptor ring buffer exists **only inside the gateway process** for multi-producer → single-consumer fan-in. It does NOT exist inside the cluster.

## 14.2 GatewayDisruptor Specification

```java
// Slot: pre-allocated, reused on every lap
public final class GatewaySlot {
    // @Contended applied by JVM with -XX:-RestrictContended
    @jdk.internal.vm.annotation.Contended
    public final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]); // max SBE message size
    public int length = 0;

    public void reset() { length = 0; }
}

// Ring buffer
RingBuffer<GatewaySlot> = RingBuffer.createMultiProducer(
    GatewaySlot::new,
    4096,                          // power of 2; 4K * 512 bytes = 2MB total
    new BusySpinWaitStrategy()
);
```

## 14.3 Producer Pattern — Pre-Claim, Encode In-Place

```java
// In MarketDataHandler.onMessage():
long seq = ringBuffer.next();  // claim next slot; blocks if full (busy-spin)
try {
    GatewaySlot slot = ringBuffer.get(seq);
    // Encode SBE directly into slot.buffer — no intermediate copy
    marketDataEventEncoder
        .wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
        .venueId(venueId)
        // ... other fields
    slot.length = marketDataEventEncoder.encodedLength() + HEADER_LENGTH;
} finally {
    ringBuffer.publish(seq);  // make visible to consumer
}
```

## 14.4 Backpressure Policy

**Two producer paths, two behaviours (V9 wording; supersedes v8 §14.4 text):**

- **Market-data producers (artio-library thread publishing `MarketDataEvent` / `VenueStatusEvent` / `BalanceQueryResponse`):** On ring full, the producer calls `ringBuffer.tryNext()`, receives `-1`, increments `DISRUPTOR_FULL_COUNTER`, and **discards the current message**. The producer thread is never blocked. This policy matches AC-GW-004 ("Ring buffer full: market data tick discarded; artio-library thread not blocked") and is the authoritative behaviour for all non-fill traffic.
- **Fill producers (execution-event path):** On ring full, the producer busy-spins on `ringBuffer.next()` indefinitely. Dropping a fill is a position-reconciliation event, not a back-pressure event; blocking the producer until the consumer catches up is correct. This matches the fill branch of §13.3.
- **Consumer (AeronPublisher) back-pressure signalling:** If the consumer thread cannot keep up with Aeron IPC publication, the cluster is behind. Alert ops immediately via shared-memory counter. Alert threshold: `ringBuffer.remainingCapacity() < 512` (< 12.5% of 4096).

**Rationale (V9, was AMB-008):** The previous §14.4 wording ("Ring buffer full → block producer (busy-spin)") contradicted AC-GW-004 and would have starved FIX heartbeats during sustained market-data bursts — the artio-library thread is responsible for both ingress parsing and heartbeat processing. Dropping market-data ticks under back-pressure is the correct engineering choice because market data is naturally refreshed on the next tick, while a missed heartbeat causes a session disconnect. Fill events are the exception because they carry non-replaceable position state; for them, blocking the producer is correct.

## 14.5 Memory Reuse

- Slots are pre-allocated at startup. The ring buffer factory creates 4096 `GatewaySlot` instances.
- SBE encoders write into `slot.buffer` — no additional buffers allocated.
- The consumer calls `slot.reset()` after publishing to Aeron, clearing `length = 0`.
- **No `new` call occurs on the hot path after warmup.**

---

## 14.6 GatewaySlot Buffer Size Proof

Every SBE message that travels through the GatewayDisruptor must fit in a 512-byte slot. The table below proves this for all message types including the new messages added in Section 8.9–8.13.

| Message | Fixed Fields | Variable Fields | Header | Total | Fits in 512? |
|---|---|---|---|---|---|
| MarketDataEvent | 10 × 8 = 80 bytes | none | 8 bytes | 88 bytes | ✅ |
| ExecutionEvent | 14 × 8 = 112 bytes | venueOrderId(36) + execId(36) + 2×len(4) = 80 bytes | 8 bytes | 200 bytes | ✅ |
| VenueStatusEvent | 3 × 8 = 24 bytes | none | 8 bytes | 32 bytes | ✅ |
| BalanceQueryResponse | 4 × 8 = 32 bytes | none | 8 bytes | 40 bytes | ✅ |
| AdminCommand | 6 × 8 = 48 bytes | none | 8 bytes | 56 bytes | ✅ |
| OrderStatusQueryCommand | 4 × 8 = 32 bytes | venueOrderId(36) + len(2) = 38 bytes | 8 bytes | 78 bytes | ✅ |
| RecoveryCompleteEvent | 2 × 8 = 16 bytes | none | 8 bytes | 24 bytes | ✅ |
| NewOrderCommand (×2 arb batch) | 2 × (9×8 + 8) = 160 bytes | none | — | 160 bytes | ✅ |

**Maximum observed message: ExecutionEvent at 200 bytes. 512-byte slot provides 2.5× headroom.**

Arb leg batch (2 × `NewOrderCommand` published in a single `egressPublication.offer()`): 160 bytes — fits in a single Aeron MTU (8192 bytes). No fragmentation.

---

# 15. TIMESTAMP MODEL

## 15.1 Two Clocks — Strict Separation

| Clock | Where Used | API | Properties |
|---|---|---|---|
| `System.nanoTime()` | Gateway only | `System.nanoTime()` | Monotonic; relative; JVM-local; ~20ns resolution |
| `cluster.time()` | Cluster only | `cluster.time()` | Deterministic; same on all nodes; microsecond resolution; not wall-clock |
| Hardware NIC timestamp | Bare metal deployment | Via `SO_TIMESTAMPING` socket option | ~1-10ns resolution; stamped at wire arrival |
| Epoch wall clock (audit) | Both (off hot path) | `EpochNanoClock` (Agrona) | Wall-clock nanos; for human-readable audit logs |

**Rule:** `System.nanoTime()` is called **only in the gateway** and only for `ingressTimestampNanos`. It is **never called inside `ClusteredService`**.

## 15.2 Timestamps Per Event

The fields below are **local Java variables** captured at key points — they are NOT SBE
message fields unless explicitly stored in a message schema. They are named here for
reference in the latency formula in Section 15.4.

```
// Gateway inbound (artio-library thread):
long ingressTimestampNanos   = System.nanoTime();    // first line of onMessage(); stored in SBE gatewayNanos field
long exchangeTimestampNanos  = parseUTC(fixTag60);   // from FIX tag 60; stored in SBE gatewayNanos field
long publishTimestampNanos   = System.nanoTime();    // gateway-disruptor thread, before aeronPublication.offer(); local only

// ClusteredService.onSessionMessage() (cluster-service thread):
long clusterReceiveTimeMicros      = cluster.time(); // first line of dispatch; local only
long strategyDecisionTimeMicros    = cluster.time(); // when strategy produces OrderIntent; local only
long riskApprovedTimeMicros        = cluster.time(); // after preTradeCheck returns APPROVED; local only

// Gateway outbound (gateway-egress thread):
long fixSendTimestampNanos         = System.nanoTime(); // immediately after artioSession.send(); local only
```

**SBE-stored timestamps** (persistent in the event stream — see Section 8):
- `ingressTimestampNanos` → stored in `MarketDataEvent.ingressTimestampNanos` (type `gatewayNanos`)
- `exchangeTimestampNanos` → stored in `MarketDataEvent.exchangeTimestampNanos` (type `gatewayNanos`)
- All cluster timestamps → stored as `clusterMicros` type fields in cluster-emitted events

## 15.3 Hardware NIC Timestamps (Bare Metal)

```java
// Enable on the Artio FIX socket at gateway startup:
// SO_TIMESTAMPING with SOF_TIMESTAMPING_RX_HARDWARE | SOF_TIMESTAMPING_SOFTWARE flags
// Requires: Solarflare NIC + OpenOnload, or Mellanox ConnectX with PTP

// NIC hardware timestamp arrives in ancillary message data (cmsg)
// Artio exposes this via EpochNanoClock override:
LibraryConfiguration libraryConfig = new LibraryConfiguration()
    .epochNanoClock(new HardwareTimestampClock(socketFd))  // custom implementation
    // ... other config

// HardwareTimestampClock reads from SO_TIMESTAMPING cmsg
// Falls back to System.nanoTime() if hardware timestamp not available
```

**Purpose:** Hardware timestamps allow accurate measurement of **wire-to-strategy latency** — the time from when the first byte of an `ExecutionReport` arrived at the NIC to when the strategy's `onFill()` was called. This is the true HFT measurement, not software-to-software.

## 15.4 Latency Measurement

```
// All latency metrics published as HDR Histogram to shared memory:

wire_to_strategy_latency = clusterReceiveTimeMicros - (ingressTimestampNanos / 1000)
// Note: approximate because nanoTime and cluster.time() are different clocks;
// use only for trend analysis, not absolute measurement

strategy_decision_latency = strategyDecisionTimeMicros - clusterReceiveTimeMicros

risk_check_latency = riskApprovedTimeMicros - strategyDecisionTimeMicros

fix_send_latency = fixSendTimestampNanos - (riskApprovedTimeMicros * 1000)
// Gateway-side; nanoTime-based; accurate within process

exchange_roundtrip = ingressTimestampNanos(ExecReport) - fixSendTimestampNanos(NewOrder)
// Measures: FIX encode + network + exchange + network + FIX decode
```

## 15.5 UTC Timestamp Parsing — Zero Allocation

```java
// Artio provides UTC timestamp parsing directly from buffer — no String allocation:
// execDecoder.transactTime() returns an AsciiSequenceView
// Use Artio's UtcTimestampDecoder:

private final UtcTimestampDecoder utcDecoder = new UtcTimestampDecoder();

long parseTransactTime(AsciiSequenceView tsView) {
    return utcDecoder.decode(tsView.buffer(), tsView.offset(), tsView.length()) * 1_000_000L;
    // Returns epoch millis; multiply by 1e6 for nanos
}
```

## 15.6 Clock Skew Monitoring

- NTP daemon runs on all gateway hosts with PTP reference clock
- Alert if `|exchangeTimestampNanos - System.currentTimeMillis() * 1_000_000L| > 50_000_000` (50ms)
- Skew exported as Agrona counter; Prometheus sidecar reads and alerts

---

# 16. RECOVERY & RECONCILIATION

## 16.1 Cluster State Rebuild from Aeron Archive

This is the key advantage of Aeron Cluster over the v1.0 design. The cluster rebuilds its entire internal state from the archived log **before** any FIX reconnect attempt.

```
Startup (or leader election of new node):

Step 1: Aeron Cluster starts; finds latest snapshot in Archive
    [Snapshot contains: full OrderState map, Position map, RiskEngine state]
    [Loaded via ClusteredService.onLoadSnapshot(SnapshotLoader loader)]

Step 2: Replay log entries since snapshot
    [All messages since last snapshot replayed through onSessionMessage()]
    [OrderManager, PortfolioEngine, RiskEngine fully rebuilt deterministically]
    [recoveryLock[all venues] = true during replay]

Step 3: Cluster is ready; opens Aeron session for gateways to connect
    [Gateway detects cluster leader via ClusterMarkFile or configured endpoint]
    [Gateway Aeron client connects to cluster ingress]
```

## 16.2 Snapshot Policy

```java
// In ClusteredService:
@Override
public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
    // Serialize OrderManager state
    orderManager.writeSnapshot(snapshotPublication);
    // Serialize PortfolioEngine state
    portfolioEngine.writeSnapshot(snapshotPublication);
    // Serialize RiskEngine state (daily PnL, daily volume)
    riskEngine.writeSnapshot(snapshotPublication);
    // RecoveryCoordinator state (recovery locks)
    recoveryCoordinator.writeSnapshot(snapshotPublication);
}

// Snapshot trigger: every N messages OR every 60 seconds (cluster timer)
// Configured via: ClusteredServiceContainer.Context.snapshotIntervalNs()
```

## 16.3 Reconnect Sequence (per venue gateway)

```
[Trigger: Gateway detects artio-library onLogout() for venue]

Gateway:
    Step 1: Publish VenueStatusEvent{DISCONNECTED} to Aeron ingress

Cluster onSessionMessage():
    Step 2: RecoveryCoordinator.onVenueStatus(DISCONNECTED)
        → recoveryLock[venueId] = true
        → RiskEngine: CHECK 1 will now reject all orders for this venueId

[Artio reconnects automatically; ReconnectIntervalMs = 5000]

[After successful FIX logon:]
Gateway:
    Step 3: Publish VenueStatusEvent{CONNECTED} to Aeron ingress

Cluster onSessionMessage():
    Step 4: RecoveryCoordinator.onVenueStatus(CONNECTED)
        → cluster state is current: either from ongoing operation (Scenario B)
          or from completed archive replay before this gateway connected (Scenario A — REPLAY_DONE)
        → only need to reconcile: what happened at venue during downtime?

    Step 5: Open order reconciliation
        For each orderId with status PENDING_NEW, NEW, PARTIALLY_FILLED, PENDING_CANCEL:
            Publish OrderStatusQueryCommand SBE to egress
            Gateway receives → sends FIX OrderStatusRequest (35=H) via Artio
            Venue responds → ExecutionReport (ExecType=I) flows back as ExecutionEvent

        TIMEOUT — GLOBAL, 10 seconds from when first query is sent:
            Timer correlationId = 2000 + venueId (registered via cluster.scheduleTimer())
            Timer is GLOBAL across all orders — not per-order.
            Timer is cancelled (cluster.cancelTimer()) when ALL pending queries are answered.
            If timer fires before all responses received:
                → The partial results already received ARE applied (fills synthesized,
                  orphans cancelled, states updated for the orders that did respond)
                → Any orders with no response are left in their current state (unknown)
                → activateKillSwitch("recovery_timeout") — do NOT resume trading
                → log ERROR with count of answered vs unanswered queries
                → RecoveryCompleteEvent is NOT published
            Rationale: partial reconciliation is better than none — applying known fills
            prevents position divergence for responded orders. Unknown orders require
            manual investigation before trading can safely resume.

    Step 6: On each OrderStatusResponse (ExecutionEvent with ExecType=ORDER_STATUS):
        Case A: venue=FILLED, internal=OPEN
            → synthesize FillEvent; apply to PortfolioEngine; mark FILLED
            → set isSynthetic=true on FillEvent for audit
        Case B: venue=CANCELED, internal=OPEN
            → force transition to CANCELED
        Case C: venue=OPEN, internal=OPEN
            → no action; state consistent
        Case D: venueOrderId not recognized internally
            → publish CancelOrderCommand to egress; cancel the orphan
        When all pending queries answered → cancel timer; advance to Step 7

    Step 7: Balance reconciliation
        Publish BalanceQueryRequest SBE to egress
        Gateway REST poller executes HTTP GET /accounts
        Response published as BalanceQueryResponse to ingress

        TIMEOUT — 5 seconds from when BalanceQueryRequest is published:
            Timer correlationId = 3000 + venueId
            If timer fires before BalanceQueryResponse received:
                → activateKillSwitch("recovery_timeout")
                → log ERROR
                → RecoveryCompleteEvent is NOT published

        RecoveryCoordinator.reconcileBalance():
            discrepancy = |venueBalance - internalPosition / SCALE|
            if discrepancy > reconciliationToleranceUnits:
                activateKillSwitch("reconciliation_failed")
                return
            else:
                portfolioEngine.adjustPosition(venueId, instrumentId, venueBalance)
                log WARN adjustment

    Step 8: Resume (only reached if Steps 5–7 all succeed without timeout)
        recoveryLock[venueId] = false
        Publish RecoveryCompleteEvent SBE to egress
        Gateway receives → StrategyEngine.onVenueRecovered(venueId)

    Overall SLA: Steps 4–8 must complete within 30 seconds of VenueStatus{CONNECTED}
        (AC-RC-002). The individual phase timeouts (10s + 5s = 15s) leave headroom for
        Artio reconnect time and REST poll latency within the 30-second budget.
```

## 16.4 Missing Fill Detection

```java
// In RecoveryCoordinator.reconcileOrder():
OrderState order = orderManager.getOrder(clOrdId);
long internalFilled = order.cumFillQtyScaled;
long venueFilled    = decoder.cumQtyScaled();   // from ExecType=I report

if (venueFilled > internalFilled) {
    long missingQty   = venueFilled - internalFilled;
    long venueAvgPx   = decoder.avgPxScaled();  // FIX tag 6, scaled

    // Synthesize FillEvent as SBE for audit
    fillEventEncoder
        .wrapAndApplyHeader(auditBuffer, 0, headerEncoder)
        .clOrdId(clOrdId)
        .venueId(order.venueId)
        .instrumentId(order.instrumentId)
        .side(order.side)
        .fillQtyScaled(missingQty)
        .fillPriceScaled(venueAvgPx)
        .isSynthetic(BooleanType.TRUE)
        .ingressTimestampNanos(cluster.time() * 1000L);

    // Apply to portfolio (direct call — same thread)
    portfolioEngine.onFill(fillEventDecoder);
    orderManager.applyFill(clOrdId, missingQty, venueAvgPx, true);
}
```

## 16.5 Trading Resume Conditions

All of the following must be true before `recoveryLock[venueId]` is cleared:

1. `VenueStatusEvent{CONNECTED}` received from gateway
2. All open orders queried and reconciled (no pending `OrderStatusQuery`) — OR order query timeout fired and kill switch activated (trading does NOT resume in this case)
3. Balance reconciliation returned `SUCCESS` or `ADJUSTED_WITHIN_TOLERANCE` — OR balance query timeout fired and kill switch activated
4. `killSwitchActive == false`
5. Market data received within `stalenessThresholdMicros` for all configured instruments

**Timeout outcomes — trading does NOT resume:**

| Timeout | Trigger | Action |
|---|---|---|
| Order query timeout (10s) | Not all orders responded | Apply partial results; kill switch; no RecoveryCompleteEvent |
| Balance query timeout (5s) | No REST response | Kill switch; no RecoveryCompleteEvent |
| Balance mismatch | Discrepancy > tolerance | Kill switch; no RecoveryCompleteEvent |

In all timeout cases `recoveryLock[venueId]` remains `true`. Manual operator intervention
(via AdminCli `deactivate-kill-switch`) is required to resume trading after investigation.

---

## 16.6 Snapshot Design Rules

1. Each component writes a **length-prefixed block**: 4-byte `int` block length followed by raw bytes
2. The reader reads the 4-byte length then reads exactly that many bytes — no delimiter guessing
3. Each component uses its own SBE message schema for snapshot records
4. Snapshot field ordering must be **identical** between `writeSnapshot` and `loadSnapshot`
5. Fields not needed for state reconstruction (e.g., `lastUpdateNanos`) are omitted

**Component write order in `TradingClusteredService.onTakeSnapshot()` (Section 3.6):**
```
1. OrderManager
2. PortfolioEngine
3. RiskEngine
4. RecoveryCoordinator
```
`loadSnapshot()` must read in the same order.

---

## 16.7 Snapshot SBE Messages

These three messages are added to `messages.xml` alongside the operational messages in Section 8.

```xml
<!-- templateId=50: One record per live order in OrderManager snapshot -->
<sbe:message name="OrderStateSnapshot" id="50">
  <field name="clOrdId"              id="1"  type="clOrdIdType"/>
  <field name="venueId"              id="2"  type="venueId"/>
  <field name="instrumentId"         id="3"  type="instrumentId"/>
  <field name="strategyId"           id="4"  type="strategyId"/>
  <field name="side"                 id="5"  type="Side"/>
  <field name="ordType"              id="6"  type="OrdType"/>
  <field name="timeInForce"          id="7"  type="TimeInForce"/>
  <field name="status"               id="8"  type="int8"/>
  <field name="priceScaled"          id="9"  type="priceScaled"/>
  <field name="qtyScaled"            id="10" type="qtyScaled"/>
  <field name="cumFillQtyScaled"     id="11" type="qtyScaled"/>
  <field name="leavesQtyScaled"      id="12" type="qtyScaled"/>
  <field name="avgFillPriceScaled"   id="13" type="priceScaled"/>
  <field name="createdClusterTime"   id="14" type="clusterMicros"/>
  <data  name="venueOrderId"         id="15" type="varStringEncoding"/>
</sbe:message>

<!-- templateId=51: One record per position in PortfolioEngine snapshot -->
<sbe:message name="PositionSnapshot" id="51">
  <field name="venueId"              id="1"  type="venueId"/>
  <field name="instrumentId"         id="2"  type="instrumentId"/>
  <field name="netQtyScaled"         id="3"  type="qtyScaled"/>
  <field name="avgEntryPriceScaled"  id="4"  type="priceScaled"/>
  <field name="realizedPnlScaled"    id="5"  type="priceScaled"/>
</sbe:message>

<!-- templateId=52: RiskEngine daily state snapshot -->
<sbe:message name="RiskStateSnapshot" id="52">
  <field name="killSwitchActive"       id="1"  type="BooleanType"/>
  <field name="dailyRealizedPnlScaled" id="2"  type="int64"/>
  <field name="dailyVolumeScaled"      id="3"  type="int64"/>
  <field name="snapshotClusterTime"    id="4"  type="clusterMicros"/>
</sbe:message>
```

---

## 16.8 OrderManager Snapshot Write/Load

```java
// In OrderManagerImpl:
@Override
public void writeSnapshot(ExclusivePublication pub) {
    writeInt(pub, liveOrders.size());  // write count first

    liveOrders.forEachValue(order -> {
        orderStateSnapshotEncoder
            .wrapAndApplyHeader(snapshotBuffer, 0, headerEncoder)
            .clOrdId(order.clOrdId)
            .venueId(order.venueId)
            .instrumentId(order.instrumentId)
            .strategyId(order.strategyId)
            .side(order.side)
            .ordType(order.ordType)
            .timeInForce(order.timeInForce)
            .status(order.status)
            .priceScaled(order.priceScaled)
            .qtyScaled(order.qtyScaled)
            .cumFillQtyScaled(order.cumFillQtyScaled)
            .leavesQtyScaled(order.leavesQtyScaled)
            .avgFillPriceScaled(order.avgFillPriceScaled)
            .createdClusterTime(order.createdClusterTime)
            .putVenueOrderId(order.venueOrderId != null ? order.venueOrderId : "");

        int totalLen = orderStateSnapshotEncoder.encodedLength() + HEADER_LENGTH;
        writeBlock(pub, snapshotBuffer, totalLen);
    });
}

@Override
public void loadSnapshot(Image snapshotImage) {
    int count = readInt(snapshotImage);
    for (int i = 0; i < count; i++) {
        int len = readBlock(snapshotImage, snapshotBuffer);
        headerDecoder.wrap(snapshotBuffer, 0);
        orderStateSnapshotDecoder.wrap(snapshotBuffer, headerDecoder.encodedLength(),
                                       headerDecoder.blockLength(), headerDecoder.version());
        OrderState order = orderPool.claim();  // from pool — no allocation
        order.clOrdId           = orderStateSnapshotDecoder.clOrdId();
        order.venueId           = orderStateSnapshotDecoder.venueId();
        order.instrumentId      = orderStateSnapshotDecoder.instrumentId();
        order.strategyId        = orderStateSnapshotDecoder.strategyId();
        order.side              = orderStateSnapshotDecoder.side();
        order.status            = orderStateSnapshotDecoder.status();
        order.priceScaled       = orderStateSnapshotDecoder.priceScaled();
        order.qtyScaled         = orderStateSnapshotDecoder.qtyScaled();
        order.cumFillQtyScaled  = orderStateSnapshotDecoder.cumFillQtyScaled();
        order.leavesQtyScaled   = orderStateSnapshotDecoder.leavesQtyScaled();
        order.avgFillPriceScaled = orderStateSnapshotDecoder.avgFillPriceScaled();
        order.createdClusterTime = orderStateSnapshotDecoder.createdClusterTime();
        order.venueOrderId      = orderStateSnapshotDecoder.venueOrderId();
        liveOrders.put(order.clOrdId, order);
    }
}

// Utility: length-prefix write/read
private void writeBlock(ExclusivePublication pub, DirectBuffer buf, int len) {
    lengthBuffer.putInt(0, len, ByteOrder.LITTLE_ENDIAN);
    while (pub.offer(lengthBuffer, 0, 4) < 0) Thread.onSpinWait();
    while (pub.offer(buf, 0, len) < 0) Thread.onSpinWait();
}

private int readBlock(Image image, MutableDirectBuffer buf) {
    while (image.poll(lengthFragment, 1) == 0) Thread.onSpinWait();
    int len = lastLengthBuffer.getInt(0, ByteOrder.LITTLE_ENDIAN);
    while (image.poll(dataFragment, 1) == 0) Thread.onSpinWait();
    return len;
}

private void writeInt(ExclusivePublication pub, int value) {
    lengthBuffer.putInt(0, value, ByteOrder.LITTLE_ENDIAN);
    while (pub.offer(lengthBuffer, 0, 4) < 0) Thread.onSpinWait();
}

private int readInt(Image image) {
    while (image.poll(lengthFragment, 1) == 0) Thread.onSpinWait();
    return lastLengthBuffer.getInt(0, ByteOrder.LITTLE_ENDIAN);
}
```

---

## 16.9 Audit Log Retention Policy

Aeron Archive is used as the primary audit log. All cluster ingress messages
(market data, execution events, admin commands) are persisted to the archive.

| Property | Value | Rationale |
|---|---|---|
| Retention period | 90 days rolling | Operational review; regulatory guidance for crypto prop trading |
| Maximum archive size | 500 GB per node | Based on ~50 MB/day at 10K msg/sec; 90 days × 50 MB = 4.5 GB + headroom |
| Pruning trigger | Archive exceeds 80% of `maxArchiveSizeBytes` config | `AeronArchive.pruneArchive()` called by a scheduled cluster timer |
| Off-archive backup | Cold storage (S3 or equivalent) before pruning | Preserves full history; required for post-incident investigation |
| Snapshot frequency | Every 60 seconds (configurable via `snapshotIntervalS`) | Bounds replay time on restart |

**Pruning implementation:** A dedicated cluster timer (correlationId=1002L) fires daily.
`RecoveryCoordinator` calls `aeronArchive.listRecordings()` to find recordings older than
the retention window and calls `aeronArchive.purgeRecording(recordingId)` for each.

**Compliance note:** This system trades firm capital only. No client transaction records
are held. The 90-day retention covers operational review requirements. If regulatory
requirements change, update `retentionDays` in `cluster-node.toml [archive]`.

---

# 17. JAVA INTERFACES

## 17.1 Strategy Interface

```java
package ig.rueishi.nitroj.exchange.strategy;

/**
 * Plugin contract for trading strategies.
 * All methods are called exclusively on the cluster-service thread.
 * MUST NOT block. MUST NOT allocate on hot path.
 *
 * V9.9: this is a plain (non-sealed) interface. Earlier drafts declared
 * `sealed interface Strategy permits MarketMakingStrategy, ArbStrategy`,
 * but that `permits` clause created a three-way compile-time cycle between
 * Strategy.java and its subclasses that prevented AI-agent task-at-a-time
 * execution. No exhaustive switches on Strategy subtypes exist anywhere in
 * the codebase, so dropping `sealed` is functionally equivalent. Polymorphism
 * (`for (Strategy s : strategies) s.onMarketData(...)`) works identically.
 */
public interface Strategy {

    /**
     * Called for every MarketDataEvent relevant to this strategy.
     * Contract: must complete in < 20µs. Zero allocation.
     * @param decoder SBE decoder wrapping the cluster message buffer.
     *                Valid only for duration of this call — do NOT store reference.
     */
    void onMarketData(MarketDataEventDecoder decoder);

    /**
     * Called when a fill is received for an order belonging to this strategy.
     * Contract: must complete in < 5µs.
     * @param decoder SBE ExecutionEvent decoder.
     */
    void onFill(ExecutionEventDecoder decoder);

    /**
     * Called when a venue order is rejected (pre-trade or venue-level).
     */
    void onOrderRejected(long clOrdId, byte rejectCode, int venueId);

    /**
     * Called when kill switch is activated. Must suppress all order generation immediately.
     */
    void onKillSwitch();

    /** Called when kill switch is deactivated. */
    void onKillSwitchCleared();

    /**
     * Called after recovery completes for a venue.
     * Strategy may resume quoting for this venueId only after this call.
     */
    void onVenueRecovered(int venueId);

    /**
     * Called when a position update occurs (after every fill).
     * @param venueId        int venue ID
     * @param instrumentId   int instrument ID
     * @param netQtyScaled   current net position * 1e8
     * @param avgEntryScaled current average entry price * 1e8
     */
    void onPositionUpdate(int venueId, int instrumentId, long netQtyScaled, long avgEntryScaled);

    /**
     * Return instruments this strategy subscribes to.
     * Called once at startup. Result is stable — no hot-path recomputation.
     */
    int[] subscribedInstrumentIds();

    /**
     * Return venues this strategy is active on.
     */
    int[] activeVenueIds();

    /**
     * Return the strategy's integer ID (`Ids.STRATEGY_*` constant — never a String).
     * This is the SOLE contract for strategy ID access (V9.11: the redundant
     * `getStrategyId()` abstract method was removed).
     * Used by `StrategyEngine.pauseStrategy()` and `resumeStrategy()` for filtering.
     * Each concrete strategy MUST override this to return its `Ids.STRATEGY_*` value.
     * The default return of 0 is a safety floor — strategies that forget to override
     * will be invisible to `pauseStrategy(id)` rather than silently matching some
     * other strategy's ID.
     */
    default int strategyId() { return 0; }

    /**
     * Initialize with dependencies. Called once before first onMarketData().
     * Allocation is permitted here.
     */
    void init(StrategyContext context);

    /**
     * Called when a cluster timer fires. Used by ArbStrategy for leg timeouts.
     * Default implementation is a no-op — only override if the strategy registers timers.
     * @param correlationId the ID passed to cluster.scheduleTimer()
     */
    default void onTimer(long correlationId) {}

    /** Called on graceful shutdown. Must cancel all live orders synchronously. */
    void shutdown();
}
```

**Strategy registration (V9.9+).** `Strategy` is a plain interface, not a sealed type. New strategies implement `Strategy` directly and are registered with `StrategyEngine` via `strategyEngine.register(new MyStrategy(config, idRegistry))` inside `ClusterMain.buildClusteredService()` (§19.6) gated by a config-driven `if (config.strategy().xxx().enabled())` block. There is no `permits` list to maintain. The earlier `sealed`+`permits` design was dropped in V9.9 because it forced three-way atomic co-delivery of `Strategy.java` + `MarketMakingStrategy.java` + `ArbStrategy.java`, which blocked AI-agent task-at-a-time execution; no exhaustive switch on `Strategy` subtypes exists anywhere in the codebase, so the compile-time guarantee the `sealed` gave was unused.

## 17.2 RiskEngine Interface

```java
package ig.rueishi.nitroj.exchange.risk;

public interface RiskEngine {

    /**
     * Synchronous pre-trade check. Direct method call on cluster-service thread.
     * MUST complete in < 5µs. MUST NOT allocate.
     * Returns a pre-allocated RiskDecision constant.
     */
    RiskDecision preTradeCheck(int venueId, int instrumentId, byte side,
                                long priceScaled, long qtyScaled, int strategyId);

    /**
     * Update position snapshot after a fill. Direct call from PortfolioEngine.
     * MUST NOT allocate.
     */
    void updatePositionSnapshot(int venueId, int instrumentId, long netQtyScaled);

    /**
     * Update daily PnL metrics after a fill.
     * Called from MessageRouter's ExecutionEvent fan-out (same path as onFill).
     * PortfolioEngine calls this indirectly via riskEngine.onFill() which reads
     * realized PnL delta from the decoder.
     */
    void updateDailyPnl(long realizedPnlDeltaScaled);

    /**
     * Set recovery lock for a venue. Called by RecoveryCoordinator.onVenueStatus().
     * When true: all orders for venueId are blocked by CHECK 1.
     */
    void setRecoveryLock(int venueId, boolean locked);

    /** Returns current daily realized PnL scaled * 1e8. Used in test done-when assertions. */
    long getDailyPnlScaled();

    /** Activate kill switch. Thread-safe only because cluster is single-threaded. */
    void activateKillSwitch(String reason);

    /** Deactivate kill switch. Requires operator command via cluster ingress. */
    void deactivateKillSwitch();

    /** Lock-free read — plain boolean field in single-threaded cluster. */
    boolean isKillSwitchActive();

    /** Write state to Aeron Archive snapshot publication. */
    void writeSnapshot(ExclusivePublication snapshotPublication);

    /** Load state from Aeron Archive snapshot. Called on cluster restart. */
    void loadSnapshot(Image snapshotImage);

    /** Reset daily counters. Called by cluster timer at midnight. */
    void resetDailyCounters();

    /**
     * Provide cluster reference needed for egress publication.
     * Called once from TradingClusteredService.onStart() and installClusterShim().
     */
    void setCluster(Cluster cluster);

    /**
     * Update daily volume and PnL metrics after a fill.
     * Called from MessageRouter immediately after PortfolioEngine.onFill().
     * MUST NOT allocate.
     */
    void onFill(ExecutionEventDecoder decoder);

    /**
     * Reset all state to initial values. Warmup harness use only.
     * Called from TradingClusteredService.resetWarmupState().
     */
    void resetAll();
}
```

## 17.3 PortfolioEngine Interface

```java
package ig.rueishi.nitroj.exchange.portfolio;

public interface PortfolioEngine {

    /**
     * Apply fill to position. Direct method call on cluster-service thread.
     * MUST NOT allocate. Updates avgEntryPrice, realizedPnl, netQty.
     * After update, calls riskEngine.updatePositionSnapshot() directly.
     */
    void onFill(ExecutionEventDecoder decoder);

    /**
     * Refresh unrealized PnL for an instrument. Called on each market data tick.
     */
    void refreshUnrealizedPnl(int venueId, int instrumentId, long markPriceScaled);

    /**
     * Returns net position scaled * 1e8. Zero-allocation: reads from primitive array.
     */
    long getNetQtyScaled(int venueId, int instrumentId);

    /**
     * Returns average entry price scaled * 1e8. Zero-allocation.
     */
    long getAvgEntryPriceScaled(int venueId, int instrumentId);

    /**
     * Force-adjust position. Called by RecoveryCoordinator ONLY.
     * @param balanceUnscaled venue balance as raw double (from REST API)
     */
    void adjustPosition(int venueId, int instrumentId, double balanceUnscaled);

    /**
     * Returns total realized PnL across all positions, scaled * 1e8.
     */
    long getTotalRealizedPnlScaled();

    /**
     * Returns total unrealized PnL across all positions, scaled * 1e8.
     */
    long getTotalUnrealizedPnlScaled();

    /** Snapshot to Aeron Archive. */
    void writeSnapshot(ExclusivePublication snapshotPublication);

    /** Restore from Aeron Archive snapshot. */
    void loadSnapshot(Image snapshotImage);

    /**
     * Archive today's realized PnL snapshot to egress for external reporting.
     * Called by DailyResetTimer (Section 12.6) before daily counters are zeroed.
     * Publishes a PositionEvent SBE per instrument to the metrics sidecar channel.
     */
    void archiveDailyPnl(Publication egressPublication);

    /**
     * Provide cluster reference needed for cluster.time() in onFill().
     * Called once from TradingClusteredService.onStart() and installClusterShim().
     */
    void setCluster(Cluster cluster);

    /**
     * Reset all state to initial values. Used by warmup harness only.
     * See Section 3.6 (TradingClusteredService.resetWarmupState()).
     */
    void resetAll();
}
```

```java
package ig.rueishi.nitroj.exchange.order;

public interface OrderManager {

    /**
     * Create a new order in PENDING_NEW state.
     * Called by Strategy after risk approval.
     * MUST NOT allocate on subsequent calls (reuse pooled OrderState objects).
     */
    void createPendingOrder(long clOrdId, int venueId, int instrumentId,
                             byte side, byte ordType, byte timeInForce,
                             long priceScaled, long qtyScaled, int strategyId);

    /**
     * Apply an execution event to the order state machine.
     * Called from MessageRouter via ClusteredService.onSessionMessage() dispatch.
     * Returns true if this execution was a fill (MessageRouter then calls
     * PortfolioEngine.onFill() and RiskEngine.onFill()).
     * Returns false for non-fill events (NEW, CANCELED, REJECTED, etc.).
     */
    boolean onExecution(ExecutionEventDecoder decoder);

    /**
     * Returns the current status byte for an order. Zero-allocation.
     * Returns OrderStatus.UNKNOWN if clOrdId not found.
     */
    byte getStatus(long clOrdId);

    /**
     * Returns the venueOrderId String for an order.
     * Used only for cancel/replace commands that need FIX tag 37.
     * Off hot path — String return is acceptable.
     */
    String getVenueOrderId(long clOrdId);

    /**
     * Returns all live (non-terminal) clOrdIds for a venue.
     * Used during recovery. Returns primitive long array — no boxing.
     */
    long[] getLiveOrderIds(int venueId);

    /**
     * Force-transition an order (recovery use only).
     */
    void forceTransition(long clOrdId, byte newStatus, String reason);

    /**
     * Cancel all live orders. Called by kill switch activation.
     * Publishes CancelOrderCommand SBE to egress for each live order.
     */
    void cancelAllOrders();

    /** Object pool: return OrderState to pool after terminal transition. */
    void returnToPool(long clOrdId);

    void writeSnapshot(ExclusivePublication snapshotPublication);
    void loadSnapshot(Image snapshotImage);

    /**
     * Reset all state to initial values. Used by warmup harness only.
     * See Section 3.6 (TradingClusteredService.resetWarmupState()).
     */
    void resetAll();
}
```

**Note on pool usage:** `createPendingOrder()` claims an `OrderState` from the `OrderStatePool` (Section 7.6). When the order reaches a terminal state (`FILLED`, `CANCELED`, `REJECTED`, `REPLACED`, `EXPIRED`), the implementation calls `pool.release(order)` to return it. The interface method `returnToPool(clOrdId)` is the external handle for this. The pool is internal to `OrderManagerImpl` — no other component calls it directly.

## 17.5 RecoveryCoordinator Interface

```java
package ig.rueishi.nitroj.exchange.cluster;

public interface RecoveryCoordinator {

    /**
     * Handle VenueStatusEvent from MessageRouter.
     * CONNECTED: clears recovery lock, resumes trading.
     * DISCONNECTED: sets recovery lock, begins reconciliation sequence.
     */
    void onVenueStatus(VenueStatusEventDecoder decoder);

    /**
     * Handle BalanceQueryResponse from MessageRouter.
     * Called during AWAITING_BALANCE phase of recovery.
     */
    void onBalanceResponse(BalanceQueryResponseDecoder decoder);

    /**
     * Route timer events from TradingClusteredService.onTimerEvent().
     * Handles order-query timeout (correlId = 2000 + venueId)
     * and balance-query timeout (correlId = 3000 + venueId).
     */
    void onTimer(long correlationId, long timestamp);

    /** Returns true if the venue is currently in recovery (lock set). */
    boolean isInRecovery(int venueId);

    /** Snapshot current recovery state to Aeron Archive. */
    void writeSnapshot(ExclusivePublication snapshotPublication);

    /** Restore recovery state from Aeron Archive snapshot. */
    void loadSnapshot(Image snapshotImage);

    /** Reset all state. Warmup harness use only. */
    void resetAll();
}
```

## 17.6 ExecutionRouter Interface (Gateway Process)

```java
package ig.rueishi.nitroj.exchange.gateway;

/**
 * Lives in the gateway process. Receives decoded SBE OrderCommands from Aeron egress,
 * encodes them as FIX messages, and sends via Artio.
 */
public interface ExecutionRouter {

    /**
     * Route a new order to the venue via Artio.
     * Called on gateway-egress thread.
     * MUST NOT allocate after warmup. Uses Artio 0.175 generated encoders with
     * Session.trySend(Encoder); the older tryClaim/claimBuffer pattern is not
     * available as a public Session API in Artio 0.175.
     */
    void routeNewOrder(NewOrderCommandDecoder cmd);

    /**
     * Route a cancel request.
     * venueOrderId fetched from local cache (populated on first ACK).
     */
    void routeCancel(CancelOrderCommandDecoder cmd);

    /**
     * Route a replace request for venues that support MsgType=G natively.
     * For Coinbase (which does not support MsgType=G), the cluster OrderManager
     * drives cancel+new sequencing — this method is not called. See Section 9.7.
     */
    void routeReplace(ReplaceOrderCommandDecoder cmd);

    /**
     * Returns true if Artio session for this venueId is currently logged on.
     */
    boolean isVenueConnected(int venueId);

    /**
     * Return the Artio Session for a given venue.
     */
    Session getSession(int venueId);
}
```

## 17.7 IdRegistry Interface

```java
package ig.rueishi.nitroj.exchange.registry;

/**
 * Maps String venue/instrument identifiers to int IDs and back.
 * Populated at startup from configuration. Read-only on hot path.
 * No allocation after init.
 */
public interface IdRegistry {

    /** Returns int venueId for a given Artio Session.id() value.
     *  @throws IllegalStateException if session not in registry (programming error). */
    int venueId(long sessionId);

    /** Returns int instrumentId for a FIX symbol string.
     *  Returns 0 for unknown symbols — not a programming error; caller must handle. */
    int instrumentId(CharSequence symbol);

    /** Returns the FIX symbol String for an int instrumentId. Cached; never null. */
    String symbolOf(int instrumentId);

    /** Returns the display name String for an int venueId. */
    String venueNameOf(int venueId);

    /**
     * Register a live Artio session ID for a venue. Called by the gateway AFTER
     * {@link #init(List, List)} has run and when Artio exposes the Session.id()
     * value during session creation/acquisition/logon. The gateway typically calls
     * this once per gateway process per venue it connects to. The cluster NEVER
     * calls this — the cluster has no FIX sessions.
     *
     * <p>After registration, {@link #venueId(long)} returns {@code venueId} for the
     * registered Artio Session.id() value.
     *
     * @throws IllegalStateException if the same sessionId is registered twice for
     *         different venueIds — programming error.
     */
    void registerSession(int venueId, long sessionId);
}
```

## 17.8 IdRegistry Implementation

```java
package ig.rueishi.nitroj.exchange.registry;

import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Object2IntHashMap;

/**
 * Maps FIX String identifiers to internal int IDs.
 * Populated at startup from venues.toml and instruments.toml (Section 22).
 * ALL methods are read-only after init() — zero allocation on hot path.
 */
public final class IdRegistryImpl implements IdRegistry {

    // Object2IntHashMap from Agrona — open-addressing, no boxing
    private final Object2IntHashMap<String> venueByName        = new Object2IntHashMap<>(0);
    private final Object2IntHashMap<String> instrumentBySymbol = new Object2IntHashMap<>(0);

    // Reverse maps — array indexed by ID: O(1) lookup, no HashMap overhead
    private final String[] venueNames        = new String[Ids.MAX_VENUES];
    private final String[] instrumentSymbols = new String[Ids.MAX_INSTRUMENTS];

    // Artio Session.id() → venueId. Long2LongHashMap is used because Agrona 2.4.0
    // does not provide Long2IntHashMap; venue IDs are cast back to int after lookup.
    private final Long2LongHashMap venueBySessionId = new Long2LongHashMap(0);

    public void init(List<VenueConfig> venues, List<InstrumentConfig> instruments) {
        // Populate venueByName + venueNames[] from venues.toml (§22.2).
        // FIX session identity is NOT read here. Live Artio Session.id() values are
        // known only when Artio creates/acquires/logs on a Session and are added via
        // registerSession() by gateway-side code (see §17.8 registerSession contract).
        for (VenueConfig v : venues) {
            venueByName.put(v.name(), v.id());
            venueNames[v.id()] = v.name();
        }
        for (InstrumentConfig i : instruments) {
            instrumentBySymbol.put(i.symbol(), i.id());
            instrumentSymbols[i.id()] = i.symbol();
        }
    }

    @Override
    public void registerSession(int venueId, long sessionId) {
        // Called by the gateway AFTER init() has run and Artio exposes Session.id().
        // Cluster-side code never calls this — cluster has no FIX sessions.
        long existing = venueBySessionId.get(sessionId);
        if (existing != 0 && existing != venueId) {
            throw new IllegalStateException(
                "Session " + sessionId + " already registered for venue " + existing +
                "; cannot re-register for venue " + venueId);
        }
        venueBySessionId.put(sessionId, venueId);
    }

    @Override
    public int venueId(long sessionId) {
        long id = venueBySessionId.get(sessionId);
        if (id == 0) throw new IllegalStateException("Unknown session: " + sessionId);
        return (int)id;
    }

    @Override
    public int instrumentId(CharSequence symbol) {
        int id = instrumentBySymbol.getOrDefault(symbol.toString(), 0);
        if (id == 0) log.warn("Unknown instrument symbol: {}", symbol);
        return id;  // 0 = unknown; caller silently discards
    }

    @Override
    public String symbolOf(int instrumentId) {
        return instrumentSymbols[instrumentId];  // O(1), no alloc
    }

    @Override
    public String venueNameOf(int venueId) {
        return venueNames[venueId];
    }
}
```

**Gateway-side wiring (for implementers).** The gateway process calls `init()` during
startup to load venue and instrument names. It calls `registerSession()` later, when
Artio provides the live `Session.id()` value during session creation/acquisition/logon.
This avoids depending on a non-existent Artio session-identity object and keeps the
session hot-path lookup primitive.

```java
private static IdRegistryImpl buildIdRegistry(
    List<VenueConfig> venues,
    List<InstrumentConfig> instruments) {
    IdRegistryImpl idRegistry = new IdRegistryImpl();
    // GatewayConfig is process configuration only. Venue/instrument registry
    // data is loaded separately via ConfigManager.loadVenues(...) and
    // ConfigManager.loadInstruments(...).
    idRegistry.init(venues, instruments);
    return idRegistry;
}

// Called from gateway-side Artio session creation/acquisition/logon handling.
private static void registerArtioSession(IdRegistryImpl idRegistry, GatewayConfig config, Session session) {
    idRegistry.registerSession(config.venueId(), session.id());
}
```

**Cluster-side wiring.** The cluster builds `IdRegistry` via `ClusterMain.buildClusteredService()`
(§19.6) and calls only `init(venues, instruments)` — never `registerSession(...)`. Cluster
code never calls `venueId(long)` because no FIX sessions exist in the cluster process.
The cluster's `venueBySessionId` map remains empty, which is correct.

## 17.9 StrategyContext

Dependency container passed to `Strategy.init()`. Provides all cluster-thread-safe references a strategy needs. Allocation is permitted inside `init()`; not on the hot path.

```java
package ig.rueishi.nitroj.exchange.strategy;

public interface StrategyContext {

    /** Access to L2 order books. Read-only on cluster-service thread. */
    InternalMarketView marketView();

    /** Pre-trade risk check. Direct call — O(1). */
    RiskEngine riskEngine();

    /** Order creation and state query. */
    OrderManager orderManager();

    /** Position and PnL query. */
    PortfolioEngine portfolioEngine();

    /** Recovery state query. */
    RecoveryCoordinator recoveryCoordinator();

    /** Cluster reference for cluster.time(), cluster.logPosition(), egressPublication(). */
    Cluster cluster();

    /** Pre-allocated egress buffer for this strategy instance. 1KB; not shared. */
    UnsafeBuffer egressBuffer();

    /** Pre-allocated SBE message header encoder. */
    MessageHeaderEncoder headerEncoder();

    /** Pre-allocated NewOrderCommand SBE encoder for this strategy. */
    NewOrderCommandEncoder newOrderEncoder();

    /** Pre-allocated CancelOrderCommand SBE encoder for this strategy. */
    CancelOrderCommandEncoder cancelOrderEncoder();

    /** ID registry for String↔int lookups (init-time only). */
    IdRegistry idRegistry();

    /** Agrona counters for strategy-specific metrics. */
    CountersManager counters();
}

// Concrete implementation — constructed by ClusterMain.buildClusteredService()
public record StrategyContextImpl(
    InternalMarketView        marketView,
    RiskEngine                riskEngine,
    OrderManager              orderManager,
    PortfolioEngine           portfolioEngine,
    RecoveryCoordinator       recoveryCoordinator,
    Cluster                   cluster,
    UnsafeBuffer              egressBuffer,
    MessageHeaderEncoder      headerEncoder,
    NewOrderCommandEncoder    newOrderEncoder,
    CancelOrderCommandEncoder cancelOrderEncoder,
    IdRegistry                idRegistry,
    CountersManager           counters
) implements StrategyContext {}
// FIX AMB-001 (v8): Removed duplicate `CountersManager counters` field — copy-paste error in v7.
```

---

## 17.10 StrategyEngine

`StrategyEngine` holds all registered `Strategy` instances and fans out lifecycle
events to each. It is constructed in `ClusterMain.buildClusteredService()` (Section 19.6)
and wired as a field of `TradingClusteredService` (Section 3.6).

```java
package ig.rueishi.nitroj.exchange.strategy;

/**
 * Dispatches cluster lifecycle events to all registered Strategy instances.
 * All methods called on the single cluster-service thread — no synchronization needed.
 */
public final class StrategyEngine {

    private final List<Strategy> strategies = new ArrayList<>(4);
    private Cluster cluster;
    private boolean active = false;  // true only on LEADER node

    // --- Registration and lifecycle ---

    /** Register a strategy. Called once at startup before onStart(). */
    public void register(Strategy strategy) {
        strategies.add(strategy);
    }

    /**
     * Provide cluster reference to all strategies via their StrategyContext.
     * Called from TradingClusteredService.onStart() and installClusterShim().
     */
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Activate or deactivate all strategies based on cluster role.
     * Called from TradingClusteredService.onRoleChange().
     * Only the LEADER node should generate orders.
     */
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            for (Strategy s : strategies) s.onKillSwitch();
        } else {
            for (Strategy s : strategies) s.onKillSwitchCleared();
        }
    }

    /** Pause a specific strategy by strategyId. */
    public void pauseStrategy(int strategyId) {
        strategies.stream()
            .filter(s -> s.strategyId() == strategyId)
            .forEach(Strategy::onKillSwitch);
    }

    /** Resume a specific strategy by strategyId. */
    public void resumeStrategy(int strategyId) {
        strategies.stream()
            .filter(s -> s.strategyId() == strategyId)
            .forEach(Strategy::onKillSwitchCleared);
    }

    // --- Event fan-out ---

    public void onMarketData(MarketDataEventDecoder decoder) {
        if (!active) return;
        for (Strategy s : strategies) s.onMarketData(decoder);
    }

    /**
     * Called by MessageRouter after OrderManager/Portfolio/Risk have processed.
     * @param isFill true if this execution was a fill; strategies may skip non-fills
     */
    public void onExecution(ExecutionEventDecoder decoder, boolean isFill) {
        if (!active) return;
        for (Strategy s : strategies) {
            if (isFill) s.onFill(decoder);
        }
    }

    /** Route cluster timer event to all strategies (e.g., ArbStrategy leg timeout). */
    public void onTimer(long correlationId) {
        for (Strategy s : strategies) s.onTimer(correlationId);
    }

    /** Notify all strategies that a venue has recovered and trading may resume. */
    public void onVenueRecovered(int venueId) {
        for (Strategy s : strategies) s.onVenueRecovered(venueId);
    }

    /**
     * Notify all strategies of a position update.
     * Called by PortfolioEngine after adjustPosition() during recovery reconciliation.
     */
    public void onPositionUpdate(int venueId, int instrumentId,
                                  long netQtyScaled, long avgEntryScaled) {
        for (Strategy s : strategies) s.onPositionUpdate(venueId, instrumentId,
                                                          netQtyScaled, avgEntryScaled);
    }

    /** Reset all strategy state. Warmup harness use only. */
    public void resetAll() {
        for (Strategy s : strategies) {
            s.shutdown();       // cancel any live orders / pending state
        }
        active = false;
        cluster = null;
    }
}
```

**Note:** `Strategy.strategyId()` is a new default method returning `Ids.STRATEGY_*` —
add `int strategyId()` to the `Strategy` interface (Section 17.1) as a default
returning `0` for unnamed strategies, overridden by each concrete implementation.

---

# 18. IMPLEMENTATION CONSTRAINTS

## 18.1 Zero-Allocation Rules — Hot Path Definition

The **hot path** is defined as any code path reachable from:
- `ClusteredService.onSessionMessage()` — all cluster business logic
- `MarketDataHandler.onMessage()` — gateway market data normalization
- `ExecutionHandler.onMessage()` — gateway execution normalization
- `GatewaySlot` consumer — AeronPublisher
- `gateway-egress` poll loop — OrderCommandHandler

**Allocation rules for hot path:**

| Construct | Rule |
|---|---|
| `new Object()` | BANNED — no exceptions |
| `new long[]`, `new byte[]` | BANNED unless called once at startup |
| `String` concatenation | BANNED — use char[] or Agrona `MutableDirectBuffer` |
| Autoboxing `int → Integer` | BANNED — use Eclipse Collections primitives |
| Lambda/anonymous class | BANNED unless provably constant-folded by JIT |
| Enum `.values()` | BANNED — allocates array; use `EnumSet` or pre-cached array |
| `instanceof` with cast | Allowed; no allocation; use sealed switch for dispatch |
| Record instantiation | Allowed ONLY for `RiskDecision` — JIT will scalar-replace |
| `Math.multiplyHigh()` | Allowed — intrinsic, no allocation |
| SBE decoder `.wrap()` | Allowed — zero-copy buffer wrap |

## 18.2 Numeric Precision

| Value | Scale | Type | Max Safe Value |
|---|---|---|---|
| Price | 1e8 | `long` | ~$92 billion (Long.MAX / 1e8) |
| Quantity | 1e8 | `long` | ~92 billion units |
| PnL | 1e8 | `long` | ~$92 billion |
| Fee rate | 1e8 | `long` | 100% = 1e8 |
| Basis points | 1 | `long` | Long.MAX |

**Overflow rule:** Any multiplication where both operands may exceed `1e9` (scaled) must use `Math.multiplyHigh()` to check for 128-bit overflow, or decompose into safe sub-operations.

**Rounding:**
```java
// Deterministic rounding — same result on all cluster nodes
static long floorToTick(long price, long tickSize) {
    return (price / tickSize) * tickSize;
}
static long ceilToTick(long price, long tickSize) {
    return ((price + tickSize - 1) / tickSize) * tickSize;
}
// NEVER: Math.round(), BigDecimal on hot path
```

## 18.3 Modern Java Constructs — Mandatory Usage

### @Contended for False Sharing Prevention

```java
// Apply to any field read by one thread and written by another.
// In gateway: GatewaySlot fields accessed by producer (artio-library) and consumer (disruptor)
@jdk.internal.vm.annotation.Contended
public final class GatewaySlot {
    public final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
    public int length = 0;
}
// JVM flag required: -XX:-RestrictContended
```

### Sealed Classes for Event Hierarchy

```java
// FIX AMB-006 (v8): The snippet below is an ILLUSTRATIVE example of sealed-class usage
// in Java 21 — it is NOT the authoritative MessageRouter dispatch implementation.
//
// The authoritative MessageRouter dispatch is an integer switch(templateId) defined in
// Section 3.4.3. That switch handles all 5 inbound message types including AdminCommandDecoder
// (templateId=33), which this 4-type sealed-interface example omits.
//
// Do NOT implement MessageRouter using the sealed-class switch below.
// The sealed interface ClusterMessage is defined here for conceptual illustration only.

// Cluster dispatch — compile-time exhaustiveness; JIT devirtualizes switch
public sealed interface ClusterMessage
    permits MarketDataEventDecoder, ExecutionEventDecoder,
            VenueStatusEventDecoder, BalanceQueryResponseDecoder {}

// Illustrative only — real dispatch is in MessageRouter.dispatch() via switch(templateId):
switch (message) {
    case MarketDataEventDecoder md   -> strategyEngine.onMarketData(md);
    case ExecutionEventDecoder   ex  -> orderManager.onExecution(ex);
    case VenueStatusEventDecoder vs  -> recoveryCoordinator.onVenueStatus(vs);
    case BalanceQueryResponseDecoder b -> recoveryCoordinator.onBalanceResponse(b);
}
// No default needed — sealed permits list is the compile-time guarantee
// NOTE: AdminCommandDecoder is intentionally absent here; it is handled in MessageRouter.dispatch()
```

### Records for Value Objects

```java
// Config — immutable, created at startup
public record MarketMakingConfig(int venueId, int instrumentId, long targetSpreadBps, ...) {}

// Snapshots — immutable copies published to metrics sidecar
public record PositionSnapshot(int venueId, int instrumentId,
                                long netQtyScaled, long avgEntryPriceScaled,
                                long realizedPnlScaled, long unrealizedPnlScaled,
                                long snapshotClusterTime) {}

// RiskDecision — pre-allocated constants; JIT scalar-replaces
public record RiskDecision(boolean approved, byte rejectCode) {
    public static final RiskDecision APPROVED = new RiskDecision(true, (byte) 0);
    // ...
}
```

### Panama Foreign Memory API for L2 Book

```java
// Required JVM flags:
// --add-modules jdk.incubator.foreign (Java 21: stable, no flag needed)
// --enable-native-access=ALL-UNNAMED

// L2 book arena — global, closed only on shutdown
private static final Arena BOOK_ARENA = Arena.ofShared();

// In InternalMarketView:
L2OrderBook book = new L2OrderBook(BOOK_ARENA);  // off-heap allocation
// GC never sees book.segment — no GC pressure from L2 books regardless of book count
```

## 18.4 GC Strategy

### Development: OpenJDK ZGC

```bash
-XX:+UseZGC
-XX:+ZGenerational              # Java 21: generational ZGC; lower overhead
-Xmx8g -Xms8g                   # Pre-allocate; no heap growth at runtime
-XX:+AlwaysPreTouch             # Touch all pages at startup; no page faults later
-XX:ZCollectionInterval=5       # GC every 5s; keep heap reclaimed
-XX:-RestrictContended          # Enable @Contended
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED   # For Unsafe if needed
-XX:+UseNUMA                    # NUMA-aware allocation (multi-socket servers)
```

### Production: Azul Platform Prime (C4 Collector)

```bash
# Azul Zing JVM flags:
-XX:+UseC4                      # Continuously Concurrent Compacting Collector
                                # Guaranteed 0ms GC pauses — no stop-the-world
-Xmx16g -Xms16g
-XX:+AlwaysPreTouch
-XX:+FalseShareingDetector      # Azul-specific; detects false sharing at runtime
-XX:SelfHealingEnabled=true     # Azul-specific; adaptive optimization
```

**Azul C4 vs ZGC:**

| Property | ZGC (OpenJDK) | Azul C4 |
|---|---|---|
| Max GC pause | < 1ms (target) | 0ms (guaranteed) |
| Throughput overhead | ~5% | ~10% |
| Cost | Free | Commercial license |
| Java 21 support | Yes | Yes |
| Recommendation | Development | Production trading |

## 18.5 JVM Warmup (Required Before Live Trading)

**Problem:** JIT compilation requires ~10,000–100,000 method invocations before C2-compiled native code runs. During warmup, latency is 10–100x higher than steady state. Trading during warmup is unacceptable.

**Authoritative implementation is §26.2.** The v7 / v8 drafts of this section included a
standalone `WarmupHarness` class body, but §26.2 ("WARMUP HARNESS — CORRECTED
IMPLEMENTATION") supersedes it with the `WarmupClusterShim`-based approach that
bypasses the cluster transport layer. V9.3 removes the redundant class body here to
eliminate a third, inconsistent `WarmupHarness` definition that contradicted §25.3 and
§26.2. All implementation details — including `WarmupClusterShim`, the `WarmupHarnessImpl`
class, synthetic message encoding, and `resetWarmupState()` semantics — now live in §26.2.

Core invariants that still apply at this section's scope:

- Warmup must complete before any FIX logon attempt (AC-SY-006).
- `WarmupConfig.production()` = 100,000 iterations with `requireC2Verified=true` and
  `thresholdNanos=500` (see §27.6 for the class definition).
- `WarmupConfig.development()` = 10,000 iterations, `requireC2Verified=false` — used in
  unit and integration tests.
- After warmup, `service.resetWarmupState()` MUST be called before `removeClusterShim()`
  (§26.2) — otherwise synthetic OrderState and Position entries persist into live trading.

**Warmup configuration:**
```java
// FIX AMB-002 (v8): Supersedes the 2-parameter version that appeared here in v7.
// Authoritative definition is Section 27.6 (WarmupConfig with thresholdNanos).
// production() uses 100,000 iterations (not 200,000) — rationale in Section 27.6.
public final class WarmupConfig {

    private final int     iterations;
    private final boolean requireC2Verified;
    private final long    thresholdNanos;    // avg ns/iter above which a WARN is logged

    public WarmupConfig(int iterations, boolean requireC2Verified, long thresholdNanos) {
        this.iterations        = iterations;
        this.requireC2Verified = requireC2Verified;
        this.thresholdNanos    = thresholdNanos;
    }

    // Production: 100,000 iterations, 500ns threshold.
    // With -XX:-BackgroundCompilation (Session 1): C2 guaranteed by ~15,000 iterations;
    // remaining 85,000 populate caches and stabilise branch profiles for ReadyNow.
    // With ReadyNow (Session 2+): already C2 from profile; all 100,000 run at peak speed.
    public static WarmupConfig production() {
        return new WarmupConfig(100_000, true, 500);
    }

    // Development: 10,000 iterations — fast startup; C2 not fully triggered.
    public static WarmupConfig development() {
        return new WarmupConfig(10_000, false, 0);
    }

    public int     iterations()        { return iterations; }
    public boolean requireC2Verified() { return requireC2Verified; }
    public long    thresholdNanos()    { return thresholdNanos; }
}
```

## 18.6 Metrics — Shared Memory, Not JMX

JMX causes allocation and GC pressure. All metrics are written to Agrona `CountersReader` (shared memory):

```java
// Counters defined at startup:
CountersManager counters = new CountersManager(...);

int MARKET_DATA_COUNT      = counters.allocate("market.data.count");
int ORDER_SUBMIT_COUNT     = counters.allocate("order.submit.count");
int FILL_COUNT             = counters.allocate("fill.count");
int RISK_REJECT_COUNT      = counters.allocate("risk.reject.count");
int AERON_BACKPRESSURE     = counters.allocate("aeron.backpressure.count");
int KILL_SWITCH_ACTIVE     = counters.allocate("risk.kill_switch.active");

// Increment: single atomic store — no allocation
counters.incrementOrdered(FILL_COUNT);

// HDR Histogram for latency:
Histogram latencyHistogram = new Histogram(3_600_000_000L, 3);
// Written to shared memory via mapped file; sidecar reads and exports
```

---

# 19. DEPLOYMENT MODEL

## 19.1 Local Development

```yaml
# docker-compose.yml
services:
  media-driver:
    image: trading-platform-media-driver:latest
    network_mode: host
    ipc: host  # required for Aeron IPC shared memory

  gateway-coinbase:
    image: trading-platform-gateway:latest
    depends_on: [media-driver]
    network_mode: host
    ipc: host
    environment:
      VENUE: COINBASE_SANDBOX
      AERON_DIR: /dev/shm/aeron

  cluster-node-0:
    image: trading-platform-cluster:latest
    depends_on: [media-driver]
    network_mode: host
    ipc: host
    environment:
      NODE_ID: 0
      CLUSTER_MEMBERS: "localhost:9010,localhost:9020,localhost:9030"
```

Local targets Coinbase Advanced Sandbox (fix.sandbox.exchange.coinbase.com, port 4198).

## 19.2 Kubernetes (Staging Only — Not Production)

Kubernetes introduces networking overhead and jitter incompatible with production latency targets. Use for staging and integration testing only.

```yaml
# Key K8s constraints for Trading Platform:
spec:
  template:
    spec:
      hostNetwork: true        # required for Aeron IPC across pods
      hostIPC: true            # required for Aeron shared memory
      priorityClassName: high-priority
      containers:
        - name: cluster-node
          resources:
            requests: { cpu: "4", memory: "16Gi" }
            limits:   { cpu: "4", memory: "16Gi" }  # guaranteed QoS class
          securityContext:
            capabilities:
              add: ["SYS_NICE", "NET_ADMIN"]   # for CPU affinity syscalls
```

## 19.3 Bare Metal (Production — Mandatory)

### Hardware Specification

| Component | Specification | Rationale |
|---|---|---|
| CPU | AMD EPYC 9654 or Intel Xeon w9-3595X | ≥8 isolated cores; high single-thread performance |
| RAM | 128 GB DDR5 ECC | Pre-touch entire heap at startup; no page faults |
| NIC | Solarflare XtremeScale X2522 or Mellanox ConnectX-7 | Hardware timestamping + kernel bypass |
| Storage | Samsung 990 Pro NVMe (≥2TB) | Aeron Archive WAL; low-latency fsync |
| Network | 10 GbE direct cross-connect to venue | No switches; minimize hop count |

### OS Configuration

```bash
# /etc/default/grub
GRUB_CMDLINE_LINUX="isolcpus=2-8 nohz_full=2-8 rcu_nocbs=2-8
                    intel_idle.max_cstate=0 processor.max_cstate=0
                    transparent_hugepage=never
                    numa_balancing=disable
                    irqaffinity=0,1"
update-grub

# Huge pages for JVM (2MB pages, 4096 pages = 8GB)
sysctl -w vm.nr_hugepages=4096
echo 4096 > /sys/kernel/mm/hugepages/hugepages-2048kB/nr_hugepages

# IRQ affinity — push all interrupts to CPUs 0-1
for irq in $(ls /proc/irq/); do
    echo 3 > /proc/irq/$irq/smp_affinity 2>/dev/null  # CPUs 0 and 1 only
done

# NIC interrupt affinity — Solarflare
onload_tool affinity --set-interrupt-cpu 1 ethX

# Disable hyperthreading (BIOS or at runtime)
echo off > /sys/devices/system/cpu/smt/control

# CPU frequency scaling — performance governor
for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    echo performance > $cpu
done
```

### Kernel Bypass Networking

```bash
# Option 1: Solarflare OpenOnload (recommended for FIX TCP)
# Install: onload_install
# Run gateway with: onload java -cp ... GatewayMain
# Effect: FIX TCP bypass kernel; ~10µs round-trip reduction

# Option 2: SO_BUSY_POLL (no driver change; incremental improvement)
sysctl -w net.core.busy_poll=50
sysctl -w net.core.busy_read=50
# Set on socket: setsockopt(fd, SOL_SOCKET, SO_BUSY_POLL, 50)

# Option 3: DPDK (for custom NIC drivers; most complex; not recommended for FIX TCP)

# TCP tuning (applies regardless of bypass choice):
sysctl -w net.ipv4.tcp_nodelay=1         # Disable Nagle
sysctl -w net.core.rmem_max=134217728    # 128MB receive buffer
sysctl -w net.core.wmem_max=134217728    # 128MB send buffer
sysctl -w net.ipv4.tcp_low_latency=1
```

### Aeron Media Driver Configuration (Bare Metal)

```java
// Dedicated Aeron Media Driver process — not embedded in trading JVM
MediaDriver.Context ctx = new MediaDriver.Context()
    .termBufferSparseFile(false)           // pre-allocate; no sparse files
    .conductorIdleStrategy(new BusySpinIdleStrategy())
    .receiverIdleStrategy(new BusySpinIdleStrategy())
    .senderIdleStrategy(new BusySpinIdleStrategy())
    .threadingMode(ThreadingMode.DEDICATED)
    .dirDeleteOnStart(false)
    .aeronDirectoryName("/dev/shm/aeron") // tmpfs; never hits disk
    .mtuLength(8192);                      // match NIC MTU for IPC

MediaDriver driver = MediaDriver.launch(ctx);
```

## 19.4 Colocation

- Deploy at **Equinix NY5** (New York) for Coinbase US venue
- Use **direct cross-connect** (not internet) to venue FIX gateway
- Target one-way FIX latency: < 100µs (cross-connect), vs ~5ms (internet)
- Use PTP (IEEE 1588) hardware clock synchronization between trading server and venue
- Verify NTP sync: `chronyc tracking` — target offset < 1µs with PTP source

---

## 19.5 Cluster Fault Tolerance and Node Recovery

This section explains why Aeron Cluster was chosen and how node failure is handled.
It is the operational reference for the 3-node Raft topology.

### Single-Node Failure — Automatic Recovery

A 3-node Raft cluster tolerates **one node failure** with no manual intervention and no
trading interruption. When the leader fails:

1. The two surviving followers detect the leader timeout (Aeron Cluster default: ~1 second)
2. They run Raft leader election — one follower becomes the new leader
3. The new leader resumes processing inbound messages
4. **Trading continues** — the gateway's Aeron client reconnects to the new leader automatically

This is fully automated. No operator action is required. AC-SY-003 specifies the leader
election SLA: completes within 500ms.

This automatic failover is the primary reason Aeron Cluster was chosen over a simpler
single-node or active-standby architecture. The Raft log guarantees that any state
committed by the old leader is present on at least one follower — the new leader has
complete, current state before it begins processing.

### Two-Node Failure — Quorum Loss, Trading Halts by Design

If **two nodes fail simultaneously**, the surviving single node cannot form a quorum
(Raft requires ⌊N/2⌋ + 1 = 2 nodes for a 3-node cluster). It correctly refuses to
process any writes. **Trading halts.**

This is correct behaviour, not a system defect. Operating without quorum would risk
processing order commands on stale state — a risk unacceptable in a trading system.
It is better to halt than to trade with potentially diverged state.

**Recovery procedure (manual):**

1. Restart failed nodes — each node replays the Aeron Archive to rebuild state
2. Nodes rejoin the cluster via the `clusterMembers` config endpoint list
3. Aeron Cluster synchronises the rejoining node's log position with the leader
4. Once quorum is restored, the leader resumes processing — trading resumes automatically
5. Run `RecoveryCoordinator` reconciliation if FIX sessions were disrupted during the outage

### RPO and RTO

| Metric | Value | Basis |
|---|---|---|
| RPO (Recovery Point Objective) | **Zero** | Raft requires quorum acknowledgment before any commit — no committed state can be lost |
| RTO — single-node failure | **< 2 seconds** | Leader election (< 500ms) + gateway reconnect + in-flight order reconciliation |
| RTO — two-node failure | **2–10 minutes** | Manual node restart + archive replay + cluster sync + FIX reconnect + RecoveryCoordinator |

RPO is zero by construction — this is the core guarantee of the Raft consensus algorithm
and is the primary reason Aeron Cluster was selected. A committed order, fill, or position
update will survive any single-node failure with no data loss.

### Quorum Health Monitoring

Quorum health monitoring belongs in the infrastructure layer, not in the application code.
Recommended monitoring stack:

- **Aeron Cluster admin tooling** — `ClusterTool.listMembers()` reports live cluster membership; poll every 5 seconds
- **Prometheus alerting** — alert if cluster member count drops below 3 (endangered quorum) or below 2 (quorum lost)
- **Disk space alert on archive nodes** — Aeron Archive is append-only; alert at 80% disk capacity; the archive must not fill up during a trading session
- **JVM heap alert** — alert at 70% heap occupancy; GC pressure above this level will cause measurable latency increases

The application does not need to probe quorum on each order — this would add latency to
the hot path with no benefit. The infrastructure layer handles quorum monitoring independently.

---

## 19.6 Cluster Node Startup Code

The `ClusterMain` class launches the full Aeron Cluster node. Each of the three nodes uses this code with its own `cluster-node-N.toml` config (Section 22.5).

```java
package ig.rueishi.nitroj.exchange.cluster;

public final class ClusterMain {

    public static void main(String[] args) throws Exception {
        ClusterNodeConfig config = ConfigManager.loadCluster(args[0]);
        validateStartupConditions(config);

        // Step 1: Aeron Media Driver (external process preferred; embedded for dev mode)
        MediaDriver.Context driverCtx = new MediaDriver.Context()
            .aeronDirectoryName(config.aeronDir())
            .termBufferSparseFile(false)
            .conductorIdleStrategy(new BusySpinIdleStrategy())
            .receiverIdleStrategy(new BusySpinIdleStrategy())
            .senderIdleStrategy(new BusySpinIdleStrategy())
            .threadingMode(ThreadingMode.DEDICATED)
            .dirDeleteOnStart(false)
            .mtuLength(8192);

        // Step 2: Archive context
        AeronArchive.Context archiveCtx = new AeronArchive.Context()
            .aeronDirectoryName(config.aeronDir())
            .controlChannel("aeron:udp?endpoint=" + config.archiveEndpoint())
            .controlStreamId(100)
            .recordingEventsChannel("aeron:udp?control-mode=dynamic|control="
                                    + config.archiveEndpoint())
            .localControlChannel("aeron:ipc")
            .localControlStreamId(101);

        // Single barrier instance — must be shared between the container context
        // (which receives the OS SIGTERM) and main() (which blocks waiting for it).
        // Two separate instances will cause main() to hang on shutdown.
        ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        // Step 3: Clustered service context
        ClusteredServiceContainer.Context containerCtx = new ClusteredServiceContainer.Context()
            .aeronDirectoryName(config.aeronDir())
            .archiveContext(archiveCtx.clone())
            .clusterDirectoryName(config.snapshotDir())
            .clusteredService(buildClusteredService(config))
            .serviceId(0)
            .serviceName("trading-platform")
            .shutdownSignalBarrier(barrier);   // ← shared instance

        // Step 4: Consensus module context
        ConsensusModule.Context consensusCtx = new ConsensusModule.Context()
            .aeronDirectoryName(config.aeronDir())
            .archiveContext(archiveCtx.clone())
            .clusterDirectoryName(config.snapshotDir())
            .clusterMemberId(config.nodeId())
            .clusterMembers(config.clusterMembers())
            .ingressChannel("aeron:udp?endpoint=" + config.ingressEndpoint())
            .logChannel("aeron:udp?control-mode=manual|alias=log")
            .memberStatusChannel("aeron:udp?endpoint=" + config.consensusEndpoint())
            .snapshotIntervalNs(TimeUnit.SECONDS.toNanos(config.snapshotIntervalS()))
            .sessionTimeoutNs(TimeUnit.SECONDS.toNanos(10));

        // Step 5: Launch
        try (
            MediaDriver            driver    = MediaDriver.launch(driverCtx);
            Archive                archive   = Archive.launch(archiveCtx.conclude());
            ConsensusModule        consensus = ConsensusModule.launch(consensusCtx);
            ClusteredServiceContainer container =
                ClusteredServiceContainer.launch(containerCtx)
        ) {
            log.info("Cluster node {} started. Members: {}",
                      config.nodeId(), config.clusterMembers());
            barrier.await();   // ← same instance registered in containerCtx above
        }
    }

    private static ClusteredService buildClusteredService(
        ClusterNodeConfig config,
        List<VenueConfig> venues,
        List<InstrumentConfig> instruments) {
        IdRegistryImpl idRegistry = new IdRegistryImpl();
        idRegistry.init(venues, instruments);

        RiskEngineImpl      riskEngine = new RiskEngineImpl(config.risk(), idRegistry);
        PortfolioEngine     portfolio  = new PortfolioEngineImpl(riskEngine);
        OrderManagerImpl    orderMgr   = new OrderManagerImpl(config.orderPool());
        RecoveryCoordinator recovery   = new RecoveryCoordinatorImpl(orderMgr, portfolio,
                                                                      riskEngine);
        StrategyEngine      strategies = new StrategyEngine(riskEngine, orderMgr,
                                                             portfolio, recovery);

        if (config.strategy().marketMaking().enabled())
            strategies.register(new MarketMakingStrategy(config.strategy().marketMaking(),
                                                          idRegistry));
        if (config.strategy().arb().enabled())
            strategies.register(new ArbStrategy(config.strategy().arb(), idRegistry));

        // Pre-populate Position entries for all configured (venueId, instrumentId) pairs.
        // This eliminates first-fill heap allocation — Position::new would otherwise be called
        // on the cluster-service thread during the first live fill, introducing GC activity
        // at the most operationally sensitive moment.
        for (int venueId : idRegistry.allVenueIds()) {
            for (int instrumentId : idRegistry.allInstrumentIds()) {
                portfolio.initPosition(venueId, instrumentId);
            }
        }
        // PortfolioEngineImpl.initPosition() calls positions.getIfAbsent(key, Position::new)
        // during startup — before any cluster message is processed — ensuring the LongObjectHashMap
        // contains pre-allocated Position entries for all known keys at trading start.

        DailyResetTimer timer = new DailyResetTimer(riskEngine, portfolio,
                                                     new SystemEpochClock());

        return new TradingClusteredService(strategies, riskEngine, orderMgr,
                                            portfolio, recovery, timer, idRegistry, config);
    }

    private static void validateStartupConditions(ClusterNodeConfig config) {
        File aeronDir = new File(config.aeronDir());
        if (!aeronDir.exists()) {
            throw new IllegalStateException(
                "Aeron directory not found: " + aeronDir +
                "\nStart Media Driver first or check aeronDir config.");
        }
        log.info("Starting cluster node {} | members: {} | ingress: {}",
                  config.nodeId(), config.clusterMembers(), config.ingressEndpoint());
    }
}
```

> **V9.2 note — authoritative wiring is in the plan.** The `buildClusteredService()`
> body above is an illustrative sketch that predates the introduction of `MessageRouter`
> (§3.4.3), `AdminCommandHandler` (§24.4), and `StrategyContextImpl` (§17.9). The
> fully-specified factory — including every constructor argument, the
> `AdminCommandHandler` back-wire to `StrategyEngine`, and the `MessageRouter` 7-arg
> construction — is documented in **TASK-027 of the implementation plan**. Implementers
> should treat the plan's TASK-027 block as authoritative for exact constructor arity
> and ordering; this section remains as the textual-overview reference for the
> startup-sequence intent (launch MediaDriver → Archive → ConsensusModule →
> ClusteredServiceContainer).

**Three-node port assignments** (from cluster-node-N.toml, Section 22.5):

| Node | Ingress | Consensus | Log | Archive |
|---|---|---|---|---|
| 0 | localhost:9010 | localhost:20000 | localhost:30000 | localhost:8010 |
| 1 | localhost:9020 | localhost:20001 | localhost:30001 | localhost:8011 |
| 2 | localhost:9030 | localhost:20002 | localhost:30002 | localhost:8012 |

---

# 20. TESTING & ACCEPTANCE

## 20.1 Unit Tests

All tests use **JUnit 5 + AssertJ**. No Mockito on hot-path components — use concrete test doubles with pre-allocated state.

### RiskEngine Tests

```java
@Test void preTradeChecksAreInCorrectOrder() {
    // Given: recovery lock set AND kill switch active AND order too large
    riskEngine.setRecoveryLock(VENUE_A, true);
    riskEngine.activateKillSwitch("test");
    // When: check with oversized order
    RiskDecision d = riskEngine.preTradeCheck(VENUE_A, BTC_USD, Side.BUY,
                                               100_00000000L, 9999_00000000L, STRATEGY_MM);
    // Then: recovery must be returned, not kill switch or size
    assertEquals(RiskDecision.REJECT_RECOVERY, d);
    // Verify check order is deterministic
}

@Test void maxPositionRejectsProjectedBreach() {
    riskEngine.updatePositionSnapshot(VENUE_A, BTC_USD, 90_00000000L);  // 0.9 BTC
    // maxLong = 1.0 BTC = 100_00000000L
    RiskDecision d = riskEngine.preTradeCheck(VENUE_A, BTC_USD, Side.BUY,
                                               65000_00000000L, 15_00000000L, STRATEGY_MM);
    // 0.9 + 0.15 = 1.05 > 1.0 → reject
    assertEquals(RiskDecision.REJECT_MAX_LONG, d);
}
```

### MarketMakingStrategy Tests (Test Vectors)

```java
@Test void inventorySkewLongPosition() {
    // Given: position = 0.5 BTC, maxLong = 1.0 BTC → inventoryRatio = 0.5
    // Given: inventorySkewFactor = 5 bps, fairValue = 65000_00000000L
    // skewBps = 5 * 0.5 = 2.5
    // adjustedFairValue = 65000 * (1 - 2.5/10000) = 65000 * 0.99975 = 64983.75
    // = 64983_75000000L (scaled)
    strategy.setLastKnownPosition(50_00000000L);  // 0.5 BTC
    strategy.onMarketData(buildMdEvent(64999_00000000L, 65001_00000000L));
    verify(clusterEgress).containsBid(below(65000_00000000L));
    verify(clusterEgress).containsAsk(below(65001_00000000L));
}

@Test void noQuoteWhenSizeRoundsToZero() {
    // Given: inventoryRatio = 1.0 (at max long); askSize formula → 10% of base
    // Given: base = 10000 (0.0001 BTC scaled), 10% = 1000, lotSize = 10000
    // floor(1000 / 10000) * 10000 = 0 → no ask submitted
    strategy.setLastKnownPosition(config.maxPositionLongScaled());
    strategy.onMarketData(buildMdEvent(64999_00000000L, 65001_00000000L));
    verify(clusterEgress).containsBid(any());
    verify(clusterEgress, never()).containsAsk(any());
}

@Test void suppressionOnKillSwitch() {
    riskEngine.activateKillSwitch("test");
    strategy.onMarketData(buildMdEvent(64999_00000000L, 65001_00000000L));
    verify(clusterEgress, never()).offer(any());
}
```

### ArbStrategy Tests (Test Vectors)

```java
@Test void opportunityDetectionWithFees() {
    // Given: venueA ask = 65000.00, venueB bid = 65002.00
    // grossProfit = 2.00 USD (for 1 BTC)
    // takerFeeA = 0.006, takerFeeB = 0.006
    // buyFee  = 65000 * 0.006 = 390.00
    // sellFee = 65002 * 0.006 = 390.012
    // netProfit = 2.00 - 390.00 - 390.012 = -778.012 → NEGATIVE → no trade
    assertNoArbExecuted(65000_00000000L, 65002_00000000L);
}

@Test void opportunityWithNarrowFees() {
    // Given: venueA ask = 65000.00, venueB bid = 65200.00
    // grossProfit = 200 USD
    // fees = ~780 USD → still negative!
    // For actual arb: need gross > 2 * fee * price
    // At 60bps fees: need 120bps spread minimum for zero net
    // At minNetProfitBps=10: need 130bps spread
    // 65000 + 65000*0.013 = 65845 → set venueB bid = 65850
    assertArbExecuted(65000_00000000L, 65850_00000000L);
}

@Test void arbAttemptIdIsClusterLogPosition() {
    strategy.onMarketData(buildOpportunityMdEvent());
    long capturedAttemptId = captureArbAttemptId();
    assertEquals(EXPECTED_LOG_POSITION, capturedAttemptId);
    // NOT a UUID; NOT random; deterministic
}
```

### PortfolioEngine Tests (Test Vectors)

```java
@Test void avgPriceOnMultipleBuys() {
    portfolio.onFill(buildFill(Side.BUY, 10_00000000L, 65000_00000000L));
    portfolio.onFill(buildFill(Side.BUY, 10_00000000L, 65100_00000000L));
    // avg = (65000*10 + 65100*10) / 20 = 65050
    assertEquals(65050_00000000L, portfolio.getAvgEntryPriceScaled(VENUE_A, BTC_USD));
}

@Test void realizedPnlOnLongClose() {
    portfolio.onFill(buildFill(Side.BUY,  10_00000000L, 65000_00000000L));
    portfolio.onFill(buildFill(Side.SELL, 10_00000000L, 65500_00000000L));
    // realizedPnl = (65500 - 65000) * 10 / 1e8 = 500 * 10 / 1e8 = 50 USD
    // scaled: 50 * 1e8 = 50_00000000L
    assertEquals(50_00000000L, portfolio.getTotalRealizedPnlScaled());
    assertEquals(0L, portfolio.getNetQtyScaled(VENUE_A, BTC_USD));
}

@Test void shortPositionPnl() {
    portfolio.onFill(buildFill(Side.SELL, 5_00000000L, 65000_00000000L));
    portfolio.onFill(buildFill(Side.BUY,  5_00000000L, 64500_00000000L));
    // realizedPnl = (65000 - 64500) * 5 / 1e8 = 500 * 5 / 1e8 = 25 USD scaled
    assertEquals(25_00000000L, portfolio.getTotalRealizedPnlScaled());
}
```

## 20.2 Replay Tests

```java
@Test void replayProductionFIXLog() {
    // Capture real FIX log → convert to SBE stream → replay through ClusteredService
    SBEReplayFile replay = SBEReplayFile.from("logs/2024-01-15.sbe");
    TestClusteredService service = new TestClusteredService();

    replay.replay(service, speedMultiplier = 10);

    // Assertions:
    assertNoDuplicateFills(service.fills);
    assertNoInvalidTransitions(service.orderStates);
    assertPositionMatchesBaseline(service.portfolio, KNOWN_EOD_POSITION_2024_01_15);
    assertPnlWithinTolerance(service.portfolio.getTotalRealizedPnlScaled(),
                              KNOWN_EOD_PNL_2024_01_15, TOLERANCE_SCALED);
}
```

## 20.3 Fault Injection Tests

```java
@Test void clusterLeaderFailover() {
    // Start 3-node cluster
    // Inject leader crash: kill cluster node 0 process
    // Verify: new leader elected within 500ms
    // Verify: gateway reconnects to new leader
    // Verify: FIX session to venue UNCHANGED (still live)
    // Verify: first order after failover uses correct clOrdId
    //         (cluster.logPosition() continues from correct offset)
}

@Test void gatewayProcessCrash() {
    // Inject gateway process kill
    // Verify: VenueStatusEvent{DISCONNECTED} NOT received (gateway died before sending)
    // Verify: cluster detects gateway session timeout via Aeron client heartbeat
    // Verify: recoveryLock set after timeout
    // Gateway restarts → FIX reconnects → reconciliation runs
    // Verify: all orders reconciled; position correct
}

@Test void missingFillDetectedOnReconcile() {
    // 1. Submit order; partial fill processed
    // 2. Simulate gateway crash (fill report in flight but not received)
    // 3. Gateway restarts; FIX reconnects
    // 4. OrderStatusRequest response shows 100% fill
    // 5. Verify: synthesized FillEvent with isSynthetic=true
    // 6. Verify: position matches venue balance
}

@Test void killSwitchOnDailyLossLimit() {
    // 1. Configure maxDailyLoss = 100 USD scaled
    // 2. Inject fills that produce realized loss of 101 USD
    // 3. Verify: killSwitchActive = true after next preTradeCheck
    // 4. Verify: all live orders canceled (CancelOrderCommand on egress)
    // 5. Verify: no new orders accepted
}

@Test void highRateChaos_marketDataPlusFills_noFillsDropped() {
    // @RequiresProductionEnvironment — run on colocation hardware, not CI loopback
    //
    // 1. Start system with 3-node cluster
    // 2. Configure simulator: 10,000 market data events/sec + 100 fills/sec simultaneously
    //    (worst-case: AeronPublisher handling both types at peak rate)
    // 3. Run for 60 seconds
    // Verify: ALL fills received by cluster (simulator fill count == cluster portfolio fill count)
    // Verify: zero AERON_FILL_BACKPRESSURE_COUNTER increments
    // Verify: ring buffer never full (MARKET_DATA_BACKPRESSURE_COUNTER == 0)
    // Verify: portfolio net position correct at T=60s (no missed fills, no duplicate fills)
    // Note: market data may be selectively dropped under back-pressure (acceptable);
    //       fills must be 100% delivered (AeronPublisher indefinite retry for templateId=2)
}

@Test void mediaDriverRestart_clusterRecovers_tradingResumes() {
    // Simulate media driver process restart (equivalent to the Aeron transport layer failing):
    // 1. Start system; establish live quotes
    // 2. Kill the Aeron media driver process (not the cluster JVM)
    // 3. Verify: Aeron IPC sessions closed; cluster detects disconnect
    // 4. Restart media driver process
    // 5. Verify: cluster Aeron client reconnects; gateway Aeron client reconnects
    // 6. Verify: Aeron Archive resumes recording from correct log position (no gap)
    // 7. Verify: recovery coordinator runs; trading resumes
    // Note: media driver restart is distinct from cluster node restart — tests the
    //       Aeron layer's reconnect path without triggering Raft leader election
}

@Test void quorumLoss_tradingHalts_restoredAfterRestart() {
    // 1. Start 3-node cluster; establish live quotes
    // 2. Kill 2 of 3 nodes simultaneously (quorum loss)
    // Verify: surviving node refuses to process any cluster messages (Section 19.5)
    // Verify: gateway detects cluster unavailability; recoveryLock set; no new orders
    // 3. Restart both failed nodes; nodes replay Aeron Archive to current log position
    // 4. Nodes rejoin cluster; quorum restored
    // Verify: leader resumes processing; trading resumes automatically
    // Verify: no orders lost; no duplicate orders; position consistent
    // Note: "trading halts" on quorum loss is correct behaviour, not a defect (Section 19.5)
}
```

## 20.4 Performance Validation

### Environment Requirement

> **SLA measurements must be validated on production-equivalent hardware.**
> E2E tests (ET-001-4 kill switch 10ms, ET-003-1 recovery 30s) run against
> `CoinbaseExchangeSimulator` on loopback TCP (~10-50µs round-trip). These tests
> will trivially pass because loopback is orders of magnitude faster than the
> production cross-connect (~100-300µs one-way at Equinix NY5).
>
> **Required post-deployment validation:** Re-run the timing assertions in
> ET-001-4 and ET-003-1 on the colocation infrastructure with real cross-connect
> latency. Optionally simulate realistic network conditions during CI via:
> ```bash
> # Add 150µs ± 20µs jitter to loopback (requires root / CAP_NET_ADMIN):
> tc qdisc add dev lo root netem delay 150us 20us
> # Remove after testing:
> tc qdisc del dev lo root
> ```
> Tests tagged `@RequiresProductionEnvironment` must pass on colocation hardware
> before the system goes live. They are excluded from standard CI (`excludeTags`).

### Latency Targets (p99, measured under 10K msg/sec sustained load)

| Metric | Target | Measurement |
|---|---|---|
| FIX wire → gateway SBE publish | < 3µs | `ingressNanos → publishNanos` (gateway-side nanoTime delta) |
| Gateway SBE → cluster `onSessionMessage` | < 1µs | Aeron IPC; measured via Aeron latency reporter |
| `onSessionMessage` dispatch → strategy decision | < 5µs | `clusterReceiveTime → strategyDecisionTime` |
| Pre-trade risk check (8 checks) | < 5µs | `strategyDecision → riskApproved` |
| Risk approved → FIX send | < 3µs | `riskApproved → fixSendNanos` (gateway-side) |
| **Total end-to-end (market data → FIX send)** | **< 20µs** | Sum of above |
| Kill switch propagation | < 10ms | `killSwitchActivated → allCancelsSent` |
| Recovery + reconciliation | < 30s | `reconnectNanos → tradingResumedNanos` |

### Load Test

```bash
# Synthetic load generator: 10,000 market data updates/second for 300 seconds
# Measure latency histogram via HDR Histogram; output at p50/p99/p99.9/p99.99/max
# Validation criteria:
#   p99 < 20µs end-to-end
#   p99.9 < 100µs
#   max < 1ms (ZGC target); max < 100µs (Azul C4 target)
#   zero GC pauses > 1ms with ZGC
#   zero GC pauses > 0ms with Azul C4
#   zero ring buffer full conditions
#   zero Aeron back-pressure events
```

### Allocation Verification (Required Before Production)

```bash
# Run load test for 60 seconds; measure allocation rate:
async-profiler -d 60 -e alloc -f alloc.html <gateway-pid>
async-profiler -d 60 -e alloc -f alloc.html <cluster-pid>

# Acceptance criteria:
# Gateway hot path (MarketDataHandler, ExecutionHandler, AeronPublisher):
#   zero allocations per message after warmup
# Cluster hot path (onSessionMessage and all called methods):
#   zero allocations per message after warmup

# Method-level verification for RiskEngine.preTradeCheck:
# Must appear with 0 allocation rate in async-profiler output
```

### Warmup Gate (Required Before Live FIX Connectivity)

The warmup harness (Section 26.2) MUST complete and log `"Warmup complete"` before the gateway establishes any FIX session to a live venue. This is enforced in the startup sequence:

```java
// GatewayMain.start():
// warmupHarness is a WarmupHarnessImpl (§26.2) constructed at startup with the
// cluster-side TradingClusteredService and WarmupConfig.production() injected.
warmupHarness.runGatewayWarmup(fixLibrary);  // blocks until done
log.info("Warmup complete. Enabling FIX connectivity.");
artioFixEngine.initiate(coinbaseSessionConfig);  // only after warmup
```

---

*End of Master Specification v7.0*

*All sections are implementation-ready. Engineers implement directly from this document.*
*No QuickFIX/J. No String IDs on hot path. No UUID in cluster. No Disruptor inside cluster.*
*Stack: Artio + SBE + Aeron Cluster + Panama + Azul C4 + Eclipse Collections primitives.*
# SUPPLEMENT v2.1.0 — Remaining Standalone Sections

The following sections have no single corresponding master spec section.
Sections 22, 24, 25, 28, 29, 30, 31, 33, and 35 have been merged into their
respective master spec sections (see Section Index).

---

# 21. DEPENDENCY VERSIONS & BUILD CONFIGURATION

## 21.1 Pinned Dependency Versions

All versions pinned as of November 2025 (V9 refresh). Verified against Maven Central.
Do not upgrade without full regression.

| Library | Group | Artifact | Version | License |
|---|---|---|---|---|
| Aeron | `io.aeron` | `aeron-all` | `1.50.0` | Apache 2.0 |
| Aeron Cluster | `io.aeron` | `aeron-cluster` | `1.50.0` | Apache 2.0 |
| Aeron Archive | `io.aeron` | `aeron-archive` | `1.50.0` | Apache 2.0 |
| Agrona | `org.agrona` | `agrona` | `2.4.0` | Apache 2.0 |
| Artio | `uk.co.real-logic` | `artio-core` | `0.175` | Apache 2.0 |
| Artio Codecs | `uk.co.real-logic` | `artio-codecs` | `0.175` | Apache 2.0 |
| SBE Tool | `uk.co.real-logic` | `sbe-tool` | `1.37.1` | Apache 2.0 |
| SBE All | `uk.co.real-logic` | `sbe-all` | `1.37.1` | Apache 2.0 |
| LMAX Disruptor | `com.lmax` | `disruptor` | `4.0.0` | Apache 2.0 |
| Eclipse Collections | `org.eclipse.collections` | `eclipse-collections` | `11.1.0` | Apache 2.0 |
| HdrHistogram | `org.hdrhistogram` | `HdrHistogram` | `2.2.2` | Public Domain |
| TOML parser | `com.electronwill.night-config` | `toml` | `3.6.7` | LGPL-3.0 |
| JUnit 5 | `org.junit.jupiter` | `junit-jupiter` | `5.10.2` | EPL 2.0 |
| AssertJ | `org.assertj` | `assertj-core` | `3.25.3` | Apache 2.0 |
| async-profiler | N/A (agent JAR) | N/A | `3.0` | Apache 2.0 |

**Aeron ↔ Artio compatibility note:**
Artio `0.175` is built against Aeron `1.50.0` and Agrona `2.4.0`. These three versions
are a verified compatible set per the `artio-codecs` 0.175 POM on Maven Central. Never
mix Aeron and Artio versions without checking Artio's published POM for the declared
Aeron dependency version.

**Agrona 2.x compatibility note (V9 change):**
Agrona advanced from 1.x to 2.x during the dependency refresh. Breaking changes relative
to 1.21.2 include removal of `org.agrona.concurrent.SigInt` (use `ShutdownSignalBarrier`
instead), stricter validation in `SystemUtil.parseDuration` / `parseSize` (reject
negatives), and behavioural fixes in `DeadlineTimerWheel` and several primitive-key
maps. TASK-005 (IdRegistry), TASK-017 (InternalMarketView), TASK-018 (RiskEngine),
and every other component using Agrona collections must be validated against 2.4.0
as part of Spike-1 (plan §Spike-1).

**TOML parser note (V9 change):**
V9 replaces `com.moandjiezana:toml4j:0.7.2` (no releases since 2019; mis-handles arrays
of tables, which `venues.toml` uses) with `com.electronwill.night-config:toml:3.6.7`
(actively maintained, TOML 1.0 compliant). Parser class: `com.electronwill.nightconfig.toml.TomlParser`.
All §22 ConfigManager loading code uses night-config's API.

## 21.2 Gradle Build File

```kotlin
// build.gradle.kts (root)
plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val aeronVersion      = "1.50.0"
val artioVersion      = "0.175"
val sbeVersion        = "1.37.1"
val agronaVersion     = "2.4.0"
val disruptorVersion  = "4.0.0"
val eclipseColVersion = "11.1.0"
val hdrHistogramVersion = "2.2.2"
val nightConfigVersion  = "3.6.7"

allprojects {
    group   = "ig.rueishi.nitroj.exchange"
    version = "2.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf(
            "--enable-preview",
            "--add-modules", "jdk.incubator.foreign",
            "-Xlint:all"
        ))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs(
            "--enable-preview",
            "-XX:-RestrictContended",
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-modules", "jdk.incubator.foreign",
            "--enable-native-access=ALL-UNNAMED"
        )
    }
}

// :platform-common — shared SBE generated classes, IdRegistry, constants
// :platform-gateway — gateway process (Artio + Aeron publisher)
// :platform-cluster — cluster process (Aeron Cluster + all business logic)
// :platform-tooling — admin CLI, warmup harness, replay tools
```

## 21.3 Module Structure

```
platform/
├── platform-common/          SBE schemas + generated decoders/encoders
│   ├── src/main/resources/
│   │   └── messages.xml      SBE schema (Section 8)
│   └── src/generated/        SBE generated Java (do not edit manually)
│
├── platform-gateway/         Gateway process
│   ├── GatewayMain.java
│   ├── ArtioFixEngine.java
│   ├── MarketDataHandler.java
│   ├── ExecutionHandler.java
│   ├── VenueStatusHandler.java
│   ├── OrderCommandHandler.java
│   ├── RestPoller.java
│   ├── GatewayDisruptor.java
│   └── CoinbaseLogonStrategy.java   ← Section 9.8
│
├── platform-cluster/         Cluster process
│   ├── ClusterMain.java
│   ├── TradingClusteredService.java
│   ├── MessageRouter.java
│   ├── StrategyEngine.java
│   ├── MarketMakingStrategy.java
│   ├── ArbStrategy.java
│   ├── InternalMarketView.java
│   ├── RiskEngine.java
│   ├── OrderManager.java
│   ├── PortfolioEngine.java
│   ├── RecoveryCoordinator.java
│   ├── IdRegistry.java
│   ├── DailyResetTimer.java         ← Section 12.6
│   └── AdminCommandHandler.java     ← Section 24 (AdminCommandHandler — in cluster)
│
└── platform-tooling/
    ├── AdminCli.java                ← Section 24.2
    ├── WarmupHarnessImpl.java       ← Section 26.2 (implements WarmupHarness interface per plan TASK-016)
    └── FIXReplayTool.java
```

## 21.4 SBE Code Generation (Gradle Task)

```kotlin
// In platform-common/build.gradle.kts:
configurations { create("sbeCodegen") }

dependencies {
    "sbeCodegen"("uk.co.real-logic:sbe-tool:$sbeVersion")
}

tasks.register<JavaExec>("generateSBE") {
    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    classpath = configurations["sbeCodegen"]
    args = listOf(
        "src/main/resources/messages.xml"
    )
    systemProperties["sbe.output.dir"]          = "src/generated/java"
    systemProperties["sbe.target.language"]      = "Java"
    systemProperties["sbe.java.generate.interfaces"] = "false"
}

tasks.named("compileJava") { dependsOn("generateSBE") }
```

---


---

# 22. CONFIGURATION SCHEMA

## 22.1 Config File Format: TOML

TOML is chosen over YAML for: no indentation ambiguity, strong types, explicit arrays.

**File locations:**
- Gateway: `config/gateway-<venueId>.toml`
- Cluster: `config/cluster-node-<nodeId>.toml`
- Shared: `config/instruments.toml`, `config/venues.toml`

## 22.2 venues.toml — ID Registry Source of Truth

```toml
# Integer IDs are assigned here and are IMMUTABLE after first assignment.
# Never reassign an ID. Never reuse a deleted ID.

[[venue]]
id        = 1
name      = "COINBASE"
fixHost   = "fix.exchange.coinbase.com"
fixPort   = 4198
sandbox   = false

[[venue]]
id        = 2
name      = "COINBASE_SANDBOX"
fixHost   = "fix.sandbox.exchange.coinbase.com"
fixPort   = 4198
sandbox   = true

# Future venues continue from id=3
```

## 22.3 instruments.toml — Instrument ID Registry

```toml
# Same immutability rule as venue IDs.

[[instrument]]
id          = 1
symbol      = "BTC-USD"      # FIX tag 55 value
baseCurrency  = "BTC"
quoteCurrency = "USD"
tickSize    = "0.01"         # In quote currency; stored as string to avoid float
lotSize     = "0.00001"      # Minimum order size in base currency
priceScale  = 100000000      # 1e8
qtyScale    = 100000000      # 1e8

[[instrument]]
id          = 2
symbol      = "ETH-USD"
baseCurrency  = "ETH"
quoteCurrency = "USD"
tickSize    = "0.01"
lotSize     = "0.0001"
priceScale  = 100000000
qtyScale    = 100000000
```

## 22.4 gateway-1.toml — Gateway Process Config

```toml
[process]
venueId     = 1              # Must match venues.toml id
nodeRole    = "gateway"
aeronDir    = "/dev/shm/aeron"
logDir      = "./log/gateway-1"

[cluster]
ingressChannel  = "aeron:udp?endpoint=localhost:9010"
egressChannel   = "aeron:udp?endpoint=localhost:20000"
# All 3 cluster nodes listed for failover
members = [
    "aeron:udp?endpoint=localhost:9010",
    "aeron:udp?endpoint=localhost:9020",
    "aeron:udp?endpoint=localhost:9030"
]

[fix]
senderCompId        = "YOUR_SENDER_COMP_ID"
targetCompId        = "Coinbase"
heartbeatIntervalS  = 30
reconnectIntervalMs = 5000
resetSeqNumOnLogon  = false
artioLogDir         = "./artio-store/venue-1"
artioReplayCapacity = 4096

[credentials]
# Never store actual secrets here. Values are Vault paths.
# GatewayMain may use COINBASE_API_KEY, COINBASE_API_SECRET_BASE64, and
# COINBASE_API_PASSPHRASE as a standalone dev fallback when no Vault resolver is wired.
vaultPath           = "secret/trading/coinbase/venue-1"

[rest]
baseUrl             = "https://api.exchange.coinbase.com"
pollIntervalMs      = 1000
timeoutMs           = 5000

[cpu]
artioLibraryThread  = 2
gatewayDisruptorThread = 3
gatewayEgressThread = 4

[disruptor]
ringBufferSize      = 4096   # Must be power of 2
slotSizeBytes       = 512

[metrics]
counterFileDir      = "/dev/shm/metrics/gateway-1"
histogramOutputMs   = 5000
```

## 22.5 cluster-node-0.toml — Cluster Node Config

```toml
[process]
nodeId      = 0
nodeRole    = "cluster"
aeronDir    = "/dev/shm/aeron"
archiveDir  = "./archive/node-0"
snapshotDir = "./snapshot/node-0"

[cluster]
# Aeron Cluster member string format:
# id,ingressEndpoint,consensusEndpoint,logEndpoint,archiveEndpoint
members = """
    0,localhost:9010,localhost:20000,localhost:30000,localhost:8010|
    1,localhost:9020,localhost:20001,localhost:30001,localhost:8011|
    2,localhost:9030,localhost:20002,localhost:30002,localhost:8012
"""
ingressChannel  = "aeron:udp?endpoint=localhost:9010"
logChannel      = "aeron:udp?control-mode=manual|alias=log"
archiveChannel  = "aeron:udp?endpoint=localhost:8010"
snapshotIntervalS = 60

[risk]
[risk.global]
maxOrdersPerSecond  = 100
maxDailyLossUsd     = 10000.00    # Stored as string; loaded as long scaled * 1e8

[risk.instrument.1]   # BTC-USD
maxOrderSizeBtc     = "1.0"
maxLongPositionBtc  = "5.0"
maxShortPositionBtc = "5.0"
maxNotionalUsd      = "500000.00"
softLimitPct        = 80          # Percent of hard limit for soft alert

[risk.instrument.2]   # ETH-USD
maxOrderSizeEth     = "10.0"
maxLongPositionEth  = "50.0"
maxShortPositionEth = "50.0"
maxNotionalUsd      = "200000.00"
softLimitPct        = 80

[strategy.market_making]
enabled             = true
venueId             = 1
instrumentId        = 1
targetSpreadBps     = 10
inventorySkewFactorBps = 5
baseQuoteSizeBtc    = "0.01"
maxQuoteSizeBtc     = "0.1"
refreshThresholdBps = 2
maxQuoteAgeMicros   = 5000000     # 5 seconds
marketDataStalenessThresholdMicros = 2000000
wideSpreadThresholdBps = 50
maxTolerableSpreadBps  = 200
# FIX AMB-005 (v8): Added minQuoteSizeFractionBps — configurable floor for the shrinking
# quote side. 1000 = 10% of baseQuoteSize. Range: [1, 9999]. See Section 6.1.1.
minQuoteSizeFractionBps = 1000

[strategy.arb]
enabled             = false        # Disabled until MM is stable
venueIds            = [1, 2]
instrumentId        = 1
minNetProfitBps     = 5
maxArbPositionBtc   = "0.1"        # parsed via parseScaled() into record field maxArbPositionScaled
maxLegSubmissionGapMicros = 1000
legTimeoutClusterMicros   = 5000000   # legTimeoutMicros pre-V9.12; renamed to match §6.2.1 record

# Per-venue arrays — MUST be the same length as venueIds and align by index.
# Example: venueIds=[1,2]; takerFeeScaled=["0.0060","0.0040"] means
#   venueId 1 → 60 bps taker fee, venueId 2 → 40 bps taker fee.
# ConfigManager.loadCluster() validates alignment and parses the decimal strings
# via parseScaled() into long[] indexed by venueId (zero-filled for absent venues).
takerFeeScaled   = ["0.0060", "0.0040"]   # decimal strings; * 1e8 after parseScaled()
baseSlippageBps  = [2, 3]                 # long bps — no scaling
slippageSlopeBps = [1, 1]                 # long bps per unit of referenceSize — no scaling

# Cross-venue scalars
referenceSize               = "1.0"        # BTC; parseScaled() → long referenceSize
cooldownAfterFailureMicros  = 10000000     # 10 seconds — wait after leg-timeout before next attempt

[cpu]
clusterServiceThread = 5

[metrics]
counterFileDir      = "/dev/shm/metrics/cluster-0"
histogramOutputMs   = 5000

[constants]
maxVenues           = 16
maxInstruments      = 64
```

## 22.6 Configuration Loading — ConfigManager

```java
public final class ConfigManager {
    private static final long SCALE = 100_000_000L;

    // Load from TOML using com.electronwill.night-config:toml:3.6.7 (V9 change; replaces toml4j)
    public static ClusterNodeConfig loadCluster(String path) {
        try (FileConfig config = FileConfig.of(path)) {
            config.load();
            return ClusterNodeConfig.of(config);
        }
    }

    // Parse decimal string to scaled long — no floating point
    // "1.5" → 150_000_000L at scale 1e8
    public static long parseScaled(String decimal) {
        String[] parts = decimal.split("\\.");
        long whole = Long.parseLong(parts[0]) * SCALE;
        if (parts.length == 1) return whole;
        String frac = parts[1];
        // Pad or truncate to 8 decimal places
        while (frac.length() < 8) frac += "0";
        frac = frac.substring(0, 8);
        return whole + Long.parseLong(frac);
    }
}
```

**`ArbStrategyConfig` loading contract (V9.12 clarification):** The `[strategy.arb]`
TOML block uses parallel arrays to populate the record's per-venue `long[]` fields
(`takerFeeScaled`, `baseSlippageBps`, `slippageSlopeBps`). `ConfigManager.loadCluster()`:

1. Reads `venueIds` as an `int[]`.
2. Reads `takerFeeScaled` as a string array, parses each element via `parseScaled()`, and
   builds a `long[]` indexed by `venueId` (so `takerFeeScaled[venueIds[i]] =
   parseScaled(tomlTakerFeeScaled[i])`). Venues not in `venueIds` get zero.
3. Same pattern for `baseSlippageBps` (direct long parse) and `slippageSlopeBps`
   (direct long parse).
4. Validates that all three per-venue arrays have the same length as `venueIds`.
   If any length differs, throws `ConfigValidationException` naming the offending field.
5. Parses scalars: `referenceSize = parseScaled("1.0")`, `maxArbPositionScaled =
   parseScaled(maxArbPositionBtc)`, `cooldownAfterFailureMicros` and
   `legTimeoutClusterMicros` are direct `long` reads.
6. Constructs the `ArbStrategyConfig` record with all 11 fields populated.

**Field-name mapping between TOML and record (V9.12):**

| TOML field | Record field | Conversion |
|---|---|---|
| `venueIds = [1, 2]` | `venueIds` | direct `int[]` |
| `instrumentId = 1` | `instrumentId` | direct `int` |
| `minNetProfitBps = 5` | `minNetProfitBps` | direct `long` |
| `takerFeeScaled = ["0.0060", "0.0040"]` | `takerFeeScaled[]` | `parseScaled()` each; indexed by venueId |
| `baseSlippageBps = [2, 3]` | `baseSlippageBps[]` | indexed by venueId |
| `slippageSlopeBps = [1, 1]` | `slippageSlopeBps[]` | indexed by venueId |
| `referenceSize = "1.0"` | `referenceSize` | `parseScaled()` |
| `maxArbPositionBtc = "0.1"` | `maxArbPositionScaled` | `parseScaled()` (note rename) |
| `maxLegSubmissionGapMicros = 1000` | `maxLegSubmissionGapMicros` | direct `long` |
| `legTimeoutClusterMicros = 5000000` | `legTimeoutClusterMicros` | direct `long` |
| `cooldownAfterFailureMicros = 10000000` | `cooldownAfterFailureMicros` | direct `long` |
| `enabled = false` | *(not in record)* | read separately by `ClusterMain.buildClusteredService()` to decide whether to register `ArbStrategy` |

---

# 23. POSITION SNAPSHOT — CORRECTED DATA STRUCTURE

## 23.1 Corrected RiskEngine Position Storage

> **Note:** This section provides historical correction context and the `updatePositionSnapshot()`
> implementation body. **The authoritative field declarations are in Section 12.1.**
> **The authoritative interface is in Section 17.2.** When in doubt, Section 12.1 and 17.2 take
> precedence over any field names shown below.

```java
public final class RiskEngine {

    // Position snapshot: one long per instrument (net position).
    // Positive = long, Negative = short.
    // Indexed by instrumentId (0-based, matches Ids.INSTRUMENT_* constants).
    // Size: MAX_INSTRUMENTS = 64.
    private final long[] netPositionSnapshot = new long[Ids.MAX_INSTRUMENTS];

    // Hard limits per instrument — loaded from config at startup
    private final long[] maxLongScaled  = new long[Ids.MAX_INSTRUMENTS];
    private final long[] maxShortScaled = new long[Ids.MAX_INSTRUMENTS];  // stored as positive
    private final long[] maxOrderScaled = new long[Ids.MAX_INSTRUMENTS];
    private final long[] maxNotionalScaled = new long[Ids.MAX_INSTRUMENTS];

    // CHECK 4 corrected:
    private RiskDecision checkPosition(int instrumentId, byte side, long qtyScaled) {
        long current = netPositionSnapshot[instrumentId];  // direct array access by int ID
        long projected = current + (side == Side.BUY ? qtyScaled : -qtyScaled);

        if (projected > maxLongScaled[instrumentId])
            return RiskDecision.REJECT_MAX_LONG;
        if (projected < -maxShortScaled[instrumentId])
            return RiskDecision.REJECT_MAX_SHORT;
        return null;  // no violation
    }

    @Override
    public void setRecoveryLock(int venueId, boolean locked) {
        recoveryLocks[venueId] = locked;
    }

    @Override
    public long getDailyPnlScaled() {
        return dailyRealizedPnlScaled;
    }

    @Override
    public void updatePositionSnapshot(int venueId, int instrumentId, long netQtyScaled) {
        // instrumentId is the direct array index — O(1), no hash, no boxing
        netPositionSnapshot[instrumentId] = netQtyScaled;
    }
}
```

**Why no venueId dimension in risk position check:**
The risk check is instrument-level, not venue-level. If we're long 1 BTC on Coinbase and short 0.5 BTC on Kraken, our net instrument exposure is +0.5 BTC. The risk limit applies to this net figure. Per-venue position limits, if needed, are a soft limit added as CHECK 4b.

## 23.2 Rate Limiting — Sliding Window Corrected

```java
// Sliding window: array of timestamps, ring-buffer style
private final long[] orderTimestampsMicros = new long[Ids.MAX_ORDERS_PER_WINDOW]; // 1000 slots
private int head = 0;
private int count = 0;
private static final long WINDOW_MICROS = 1_000_000L;  // 1 second

private void pruneOrderWindow(long nowMicros) {
    // Remove entries older than 1 second
    while (count > 0) {
        int tail = (head - count + Ids.MAX_ORDERS_PER_WINDOW) % Ids.MAX_ORDERS_PER_WINDOW;
        if (nowMicros - orderTimestampsMicros[tail] > WINDOW_MICROS) {
            count--;
        } else {
            break;
        }
    }
}

private void recordOrderInWindow(long nowMicros) {
    orderTimestampsMicros[head] = nowMicros;
    head = (head + 1) % Ids.MAX_ORDERS_PER_WINDOW;
    count++;
}
```

---

# 24. OPERATOR ADMIN INTERFACE

## 24.1 Architecture

Admin commands travel through a **dedicated Aeron publication** in the gateway process — not the main market data ingress. This prevents a flood of admin commands from delaying market data. The cluster subscribes to both the main ingress and the admin ingress.

```
AdminCLI (platform-tooling process)
    → Aeron Publication (admin channel: "aeron:udp?endpoint=gateway:9099")
    → Gateway admin subscription
    → Gateway validates HMAC signature
    → Gateway re-publishes to cluster ingress (with ADMIN templateId)
    → ClusteredService.onSessionMessage()
    → MessageRouter → AdminCommandHandler
```

## 24.2 AdminCLI Tool

```java
package ig.rueishi.nitroj.exchange.tooling;

/**
 * Command-line admin tool. Run on same host as gateway or any host with
 * network access to the admin Aeron endpoint.
 *
 * Usage:
 *   java -cp platform-tooling.jar AdminCli <command> [options]
 *
 * Commands:
 *   kill-switch activate   --reason "manual"
 *   kill-switch deactivate
 *   strategy pause   --id 1
 *   strategy resume  --id 1
 *   snapshot trigger
 *   daily-counters reset
 */
public final class AdminCli {

    public static void main(String[] args) throws Exception {
        AdminConfig config = AdminConfig.load("config/admin.toml");
        AdminClient client = new AdminClient(config);

        String command = args[0] + " " + args[1];
        switch (command) {
            case "kill-switch activate"   -> client.activateKillSwitch(findArg(args, "--reason"));
            case "kill-switch deactivate" -> client.deactivateKillSwitch();
            case "strategy pause"         -> client.pauseStrategy(parseInt(findArg(args, "--id")));
            case "strategy resume"        -> client.resumeStrategy(parseInt(findArg(args, "--id")));
            case "snapshot trigger"       -> client.triggerSnapshot();
            case "daily-counters reset"   -> client.resetDailyCounters();
            default -> System.err.println("Unknown command: " + command);
        }
    }
}
```

## 24.3 AdminClient — HMAC-Signed Commands

```java
public final class AdminClient implements AutoCloseable {

    private final Aeron aeron;
    private final Publication adminPublication;
    private final Mac mac;
    private long nonce;  // monotonically increasing; stored in local state file

    public AdminClient(AdminConfig config) throws Exception {
        aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(config.aeronDir()));
        adminPublication = aeron.addPublication(config.adminChannel(), config.adminStreamId());

        mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.adminHmacKey(), "HmacSHA256"));

        nonce = loadNonce(config.nonceFile());  // persisted to disk; never repeat
    }

    public void deactivateKillSwitch() {
        sendCommand(AdminCommandType.DEACTIVATE_KILL_SWITCH, 0, 0);
    }

    private void sendCommand(byte commandType, int venueId, int instrumentId) {
        long currentNonce = nonce++;
        saveNonce(currentNonce + 1);  // persist before sending

        long signature = computeHmac(commandType, venueId, instrumentId,
                                      config.operatorId(), currentNonce);

        adminCommandEncoder
            .wrapAndApplyHeader(sendBuffer, 0, headerEncoder)
            .commandType(commandType)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .operatorId(config.operatorId())
            .hmacSignature(signature)
            .nonce(currentNonce);

        long result = adminPublication.offer(sendBuffer, 0,
                                              adminCommandEncoder.encodedLength() + HEADER_LENGTH);
        if (result < 0) throw new RuntimeException("Admin command not accepted: " + result);
        System.out.println("Command sent: " + commandType + " nonce=" + currentNonce);
    }

    private long computeHmac(byte cmd, int venue, int instr, int opId, long nonce) {
        // Pack all fields into a byte array in declaration order; compute HMAC-SHA256.
        // Truncate to 64 bits by reading the first 8 bytes of the 32-byte output as a
        // big-endian long (Java ByteBuffer default). AdminCli must use identical byte
        // order — see Section 24.2. 64-bit truncation is within NIST SP 800-107 guidance.
        // The packed layout (17 bytes): cmd(1) + venue(4) + instr(4) + opId(4) + nonce(8)
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(cmd).putInt(venue).putInt(instr).putInt(opId).putLong(nonce);
        byte[] hmac = mac.doFinal(buf.array());
        return ByteBuffer.wrap(hmac).getLong();  // big-endian; first 8 of 32 bytes
    }
}
```

## 24.4 AdminCommandHandler — In Cluster

```java
package ig.rueishi.nitroj.exchange.cluster;

public final class AdminCommandHandler {

    // Nonce replay protection — bounded ring buffer + HashSet for O(1) lookup.
    // Nonce format: (epochSeconds << 32) | sequenceNumber.
    // The 24h stale check at the inbound side rejects old nonces, but without
    // eviction the HashSet grows without bound. The ring buffer tracks insertion
    // order so the oldest entry is evicted when the window is full.
    //
    // Capacity: 10,000 nonces = ~6.9 commands/minute sustained over 24h.
    // At typical admin rates (< 100 commands/day) this window is never fully used.
    // If the window fills (10,000 commands in 24h), oldest nonces are evicted — they
    // are also beyond the 24h stale window so the security guarantee is maintained.
    private static final int NONCE_WINDOW_SIZE = 10_000;
    private final long[]       nonceRing    = new long[NONCE_WINDOW_SIZE];  // insertion order
    private final LongHashSet  seenNonces   = new LongHashSet(NONCE_WINDOW_SIZE * 2);
    private int                nonceHead    = 0;
    private int                nonceCount   = 0;

    private final Mac mac;  // initialized with admin HMAC key from config

    private boolean addNonce(long nonce) {
        if (seenNonces.contains(nonce)) return false;  // replay

        // Evict oldest if window full
        if (nonceCount == NONCE_WINDOW_SIZE) {
            seenNonces.remove(nonceRing[nonceHead]);
            nonceCount--;
        }
        nonceRing[nonceHead] = nonce;
        nonceHead = (nonceHead + 1) % NONCE_WINDOW_SIZE;
        seenNonces.add(nonce);
        nonceCount++;
        return true;
    }

    public void onAdminCommand(AdminCommandDecoder decoder) {
        // Step 1: Validate nonce (replay protection + stale check)
        long nonce = decoder.nonce();
        long epochSeconds = nonce >>> 32;
        long nowSeconds   = epochClock.time() / 1000;
        if (nowSeconds - epochSeconds > 86_400L) {
            log.warn("Stale admin nonce rejected: age={}s nonce={}", nowSeconds - epochSeconds, nonce);
            return;
        }
        if (!addNonce(nonce)) {
            log.warn("Replayed admin command rejected: nonce={}", nonce);
            return;
        }

        // Step 2: Validate HMAC signature
        long expectedSig = computeHmac(decoder.commandType(), decoder.venueId(),
                                        decoder.instrumentId(), decoder.operatorId(), nonce);
        if (expectedSig != decoder.hmacSignature()) {
            log.error("Admin command signature invalid: operator={}", decoder.operatorId());
            return;
        }

        // Step 3: Audit log — write before executing so the command is recorded
        // even if execution throws. Travels through Raft log → on all nodes + Archive.
        log.info("ADMIN_CMD operator={} commandType={} venueId={} instrumentId={} nonce={}",
                  decoder.operatorId(), decoder.commandType(),
                  decoder.venueId(), decoder.instrumentId(), nonce);

        // Step 4: Execute
        switch (decoder.commandType()) {
            case AdminCommandType.DEACTIVATE_KILL_SWITCH ->
                riskEngine.deactivateKillSwitch();
            case AdminCommandType.ACTIVATE_KILL_SWITCH ->
                riskEngine.activateKillSwitch("operator:" + decoder.operatorId());
            case AdminCommandType.PAUSE_STRATEGY ->
                strategyEngine.pauseStrategy(decoder.instrumentId());
            case AdminCommandType.RESUME_STRATEGY ->
                strategyEngine.resumeStrategy(decoder.instrumentId());
            case AdminCommandType.TRIGGER_SNAPSHOT ->
                cluster.takeSnapshot();
            case AdminCommandType.RESET_DAILY_COUNTERS ->
                riskEngine.resetDailyCounters();
        }
    }
}
```

**Audit log note:** The `log.info("ADMIN_CMD ...")` line above is the command audit trail.
Because `AdminCommandHandler.onAdminCommand()` runs inside `onSessionMessage()` on the
cluster-service thread, the log statement executes deterministically through the Raft log —
it is replicated to all nodes and persisted in the Aeron Archive. This provides a
tamper-evident audit record of every operator command: who sent it (`operatorId`), what
it was (`commandType`), what it targeted (`venueId`, `instrumentId`), and when (`nonce`
encodes the epoch timestamp in the upper 32 bits — see Section 24.3).

Rejected commands (replay or invalid HMAC) are logged at WARN/ERROR level above and are
also persisted via the same mechanism. A complete audit trail of both accepted and rejected
commands is available by replaying the Aeron Archive.

---

## 24.5 Admin Security: Key Rotation, OperatorId Whitelist, and Nonce Bounds

### Key Rotation Procedure

The admin HMAC key must be rotated periodically (recommended: every 90 days) and after
any suspected credential exposure. The cluster stores the verification key in
`cluster-node.toml [admin].hmacKeyBase64Dev` (dev) or Vault (production).

**Rotation procedure — must be followed exactly to avoid command gaps:**

```
Step 1: Generate new key
    openssl rand -base64 32 > new-admin-key.b64
    # Store new-admin-key.b64 in Vault at secret/trading/admin/hmac-key-new

Step 2: Deploy to cluster (rolling, one node at a time)
    # For each cluster node:
    #   Update cluster-node-N.toml [admin].hmacKeyBase64Dev (or Vault path)
    #   Restart node — remaining nodes still use old key
    # During this window, BOTH keys must be accepted.
    # AdminCommandHandler must support dual-key validation during rotation:

private boolean validateHmacDualKey(AdminCommandDecoder decoder, long nonce) {
    long expectedOld = computeHmac(decoder, nonce, oldHmacKey);
    long expectedNew = computeHmac(decoder, nonce, newHmacKey);
    return decoder.hmacSignature() == expectedOld
        || decoder.hmacSignature() == expectedNew;
}
    # After all 3 nodes restarted: remove old key; disable dual-key validation.

Step 3: Update AdminCli config
    # Update admin.toml hmacKeyVaultPath to point to new key
    # Test: send one kill-switch activate + deactivate to verify new key works

Step 4: Remove old key from Vault
    # Only after Step 3 verified

Step 5: Document rotation in operational log
    # Record: rotation date, reason, operator who performed it
```

**`newHmacKey` presence** in cluster config activates dual-key mode. An absent
`newHmacKey` disables it (single-key mode). This field is never stored in the Aeron
Archive — it is loaded from config at startup only.

### OperatorId Whitelist

The `operatorId` field in `AdminCommand` identifies which operator sent the command.
A valid HMAC from an unknown `operatorId` is currently accepted. Adding a whitelist
provides defense-in-depth: even if the HMAC key is compromised, commands from
unknown operator IDs are rejected.

**Add to `cluster-node.toml` `[admin]` section:**
```toml
[admin]
hmacKeyVaultPath   = "secret/trading/admin/hmac-key"
allowedOperatorIds = [1, 2, 3]   # whitelist; reject any operatorId not in this list
```

**Add to `AdminCommandHandler.onAdminCommand()` after HMAC validation:**
```java
// Step 2b: Validate operatorId whitelist
if (!config.admin().allowedOperatorIds().contains(decoder.operatorId())) {
    log.error("Admin command from unknown operatorId={} rejected", decoder.operatorId());
    return;
}
```

**Add test to T-012:**
```
unknownOperatorId_commandRejected_afterHmacValidation()
// Given: valid HMAC; operatorId=99 not in allowedOperatorIds=[1,2,3]
// When:  onAdminCommand(decoder)
// Then:  rejected; no state change; ERROR logged
```

### Nonce Bounds Summary

| Property | Value | Notes |
|---|---|---|
| Window | 24 hours | Nonces older than 24h rejected regardless of HMAC |
| Ring buffer capacity | 10,000 entries | ~6.9 commands/minute over 24h; typical usage < 10/day |
| Eviction | Oldest entry evicted when ring full | Oldest is also beyond 24h window — security maintained |
| Format | `(epochSeconds << 32) \| sequenceNumber` | Upper 32 bits = timestamp; lower 32 = sequence |
| Storage | `nonceRing[10,000]` + `LongHashSet` | Ring for eviction order; HashSet for O(1) lookup |

---

# 25. ARTIO DUTY CYCLE — GATEWAY POLL LOOP

## 25.1 artio-library Thread Poll Loop

```java
package ig.rueishi.nitroj.exchange.gateway;

/**
 * The artio-library thread's main loop.
 * Artio is poll-based — you must call fixLibrary.poll() repeatedly.
 * This thread is CPU-pinned and uses BusySpinIdleStrategy.
 */
public final class ArtioLibraryLoop implements Runnable {

    private static final int FRAGMENT_LIMIT = 10;  // max messages per poll()

    private final FixLibrary      fixLibrary;
    private final IdleStrategy    idleStrategy = new BusySpinIdleStrategy();
    private volatile boolean      running      = true;

    @Override
    public void run() {
        // Pin this thread to configured CPU
        ThreadAffinityHelper.pin(Thread.currentThread(), config.cpu().artioLibraryThread());

        log.info("artio-library thread started; pinned to CPU {}", config.cpu().artioLibraryThread());

        while (running) {
            // poll() returns number of work items processed
            // Returns 0 when no messages pending — idle strategy kicks in
            int workCount = fixLibrary.poll(FRAGMENT_LIMIT);
            idleStrategy.idle(workCount);
            // BusySpinIdleStrategy.idle(0) = spin-wait; idle(>0) = reset spin counter
        }

        fixLibrary.close();
        log.info("artio-library thread stopped");
    }

    public void stop() { running = false; }
}
```

## 25.2 gateway-egress Thread Poll Loop

```java
public final class EgressPollLoop implements Runnable {

    private static final int FRAGMENT_LIMIT = 10;

    private final Subscription  egressSubscription;  // Aeron cluster egress
    private final OrderCommandHandler commandHandler;
    private final IdleStrategy  idleStrategy = new BusySpinIdleStrategy();
    private volatile boolean    running      = true;

    @Override
    public void run() {
        ThreadAffinityHelper.pin(Thread.currentThread(), config.cpu().gatewayEgressThread());

        // Pre-allocate fragment handler — no lambda; no anonymous class allocation
        FragmentHandler handler = commandHandler::onFragment;

        while (running) {
            int workCount = egressSubscription.poll(handler, FRAGMENT_LIMIT);
            idleStrategy.idle(workCount);
        }
    }
}
```

## 25.3 Gateway Startup Sequence (GatewayMain)

```java
public final class GatewayMain {

    public static void main(String[] args) throws Exception {
        GatewayConfig config = ConfigManager.loadGateway(args[0]);
        List<VenueConfig> venues = ConfigManager.loadVenues(args[1]);
        List<InstrumentConfig> instruments = ConfigManager.loadInstruments(args[2]);

        // Step 1: Connect to Aeron Media Driver (external process)
        Aeron aeron = Aeron.connect(new Aeron.Context()
            .aeronDirectoryName(config.aeronDir())
            .idleStrategy(new SleepingMillisIdleStrategy(1)));

        // Step 2: Build components — components are constructed BEFORE the cluster client
        // so the egress listener can reference `cmdHandler` when the cluster connects.
        IdRegistryImpl      idRegistry    = buildIdRegistry(venues, instruments);
        OrderCommandHandler cmdHandler;           // set after router/rest-poller hooks below
        GatewayDisruptor    disruptor;            // set after clusterClient below
        MarketDataHandler   mdHandler;            // set after disruptor below
        ExecutionHandler    execHandler;          // set after disruptor below
        VenueStatusHandler  venueHandler;         // set after disruptor below

        // Step 3: Connect to Aeron Cluster as a client. The egress listener must be
        // passed at construction time; `cmdHandler` declared in Step 2 above is the
        // target for all cluster → gateway egress fragments.
        AeronCluster.Context clusterCtx = new AeronCluster.Context()
            .aeronDirectoryName(config.aeronDir())
            .ingressChannel("aeron:udp")
            .ingressEndpoints(buildIngressEndpoints(config.cluster().members()))
            .egressChannel("aeron:udp?endpoint=localhost:0")       // ephemeral port
            .egressListener(new GatewayEgressListener(cmdHandler));

        AeronCluster clusterClient = AeronCluster.connect(clusterCtx);

        // Step 4: Build remaining components now that clusterClient exists
        disruptor    = new GatewayDisruptor(config.disruptor(), clusterClient);
        mdHandler    = new MarketDataHandler(idRegistry, disruptor);
        execHandler  = new ExecutionHandler(idRegistry, disruptor);
        venueHandler = new VenueStatusHandler(idRegistry, disruptor);
        RestPoller restPoller = new RestPoller(config.rest(), config.credentials(), idRegistry, disruptor);
        ExecutionRouter executionRouter = new ExecutionRouterImpl(/* Artio session/sender wiring */);
        cmdHandler = new OrderCommandHandler(
            executionRouter,
            restPoller,
            venueHandler::onRecoveryComplete,
            System.getLogger(OrderCommandHandler.class.getName()));

        // Step 5: Start Artio
        GatewayConfig resolvedConfig = GatewayMain.withResolvedCredentials(config, System.getenv());
        CoinbaseLogonStrategy logonStrategy = new CoinbaseLogonStrategy(
            resolvedConfig.credentials().apiKey(),
            resolvedConfig.credentials().secretBase64(),
            resolvedConfig.credentials().passphrase());

        EngineConfiguration engineConfig = new EngineConfiguration()
            .libraryAeronChannel("aeron:ipc")
            .logFileDir(config.fix().artioLogDir())
            .replayIndexFileRecordCapacity(config.fix().artioReplayCapacity());

        LibraryConfiguration libraryConfig = new LibraryConfiguration()
            .libraryAeronChannels(List.of("aeron:ipc"))
            .sessionAcquireHandler(new SessionAcquireHandler(mdHandler, execHandler, venueHandler))
            .sessionCustomisationStrategy(logonStrategy)
            .epochNanoClock(new SystemEpochNanoClock());

        FixEngine  fixEngine  = FixEngine.launch(engineConfig);
        FixLibrary fixLibrary = FixLibrary.connect(libraryConfig);

        // Step 6: Run warmup harness BEFORE FIX logon (AC-SY-006).
        //
        // `WarmupHarness` is the interface owned by TASK-016 (plan); the concrete
        // `WarmupHarnessImpl` (§26.2) is constructed with the cluster-side service and
        // config at startup, then exposed through the interface. Plan TASK-016 /
        // TASK-033 describe the injection wiring; for this example we call the
        // single-argument interface method directly.
        WarmupHarness warmupHarness = new WarmupHarnessImpl(
            /* TradingClusteredService */ buildWarmupClusterService(config),
            config.warmup());
        warmupHarness.runGatewayWarmup(fixLibrary);

        // Step 7: Enable FIX connectivity AFTER warmup
        fixLibrary.initiate(buildSessionConfig(config));

        // Step 8: Start poll loops on pinned threads
        Thread artioThread  = Thread.ofPlatform().name("artio-library")
                                    .start(new ArtioLibraryLoop(fixLibrary, config));
        Thread egressThread = Thread.ofPlatform().name("gateway-egress")
                                    .start(new EgressPollLoop(clusterClient.egressSubscription(),
                                                               cmdHandler, config));

        // Step 9: Start GatewayDisruptor consumer
        disruptor.start();

        log.info("Gateway started for venue: {}", config.venueId());
        // Single barrier instance — reused for OS signal receipt and main() blocking.
        ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        barrier.await();

        // Graceful shutdown
        artioThread.interrupt();
        egressThread.interrupt();
        disruptor.stop();
        fixLibrary.close();
        fixEngine.close();
        clusterClient.close();
        aeron.close();
    }

    /** Converts "host1:port1,host2:port2,host3:port3" into the Aeron-Cluster
     *  ingress-endpoints form "0=host1:port1,1=host2:port2,2=host3:port3".
     *  Example output: "0=localhost:9010,1=localhost:9020,2=localhost:9030" */
    private static String buildIngressEndpoints(String members) {
        String[] parts = members.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(i).append('=').append(parts[i].trim());
        }
        return sb.toString();
    }
}
```

> **V9.3 repair note — §25.3.** The code block above is a repaired version of the
> v8/v9.2 §25.3 sketch, which had three defects: (a) `buildIngressEndpoints` was declared
> nested inside `main()`, which Java does not permit, and its closing brace prematurely
> ended `main()` — so every line after it was in class scope and the block didn't compile;
> (b) the `egressListener` referenced a variable named `commandHandler` that didn't exist
> anywhere (the actual variable was `cmdHandler`); (c) the `WarmupHarness` call used a
> two-argument signature that contradicted both §26.2's method signature and the plan's
> interface. All three are now corrected. The exact `WarmupHarnessImpl` wiring —
> including how `buildWarmupClusterService(config)` constructs the cluster-side service
> — is documented in plan TASK-016 and TASK-033; the above shows the intent, not the
> final wiring.

---

# 26. WARMUP HARNESS — CORRECTED IMPLEMENTATION

The v2.0.0 warmup called `service.onSessionMessage()` directly — this is not how Aeron Cluster works. The corrected approach bypasses the cluster transport layer and calls the business logic directly via a `WarmupClusterShim`.

## 26.1 WarmupClusterShim

```java
package ig.rueishi.nitroj.exchange.tooling;

/**
 * Implements just enough of the Cluster interface to allow business
 * logic components to call cluster.time() and cluster.logPosition()
 * during warmup, without a real cluster running.
 */
public final class WarmupClusterShim implements Cluster {

    private long simulatedTime     = System.currentTimeMillis() * 1000L;  // micros
    private long simulatedPosition = 1_000_000L;  // start from non-zero

    @Override
    public long time() { return simulatedTime += 10L; }  // advance 10µs per call

    @Override
    public long logPosition() { return simulatedPosition++; }

    @Override
    public Publication egressPublication() { return NOOP_PUBLICATION; }

    @Override
    public boolean scheduleTimer(long correlationId, long deadlineMs) { return true; }

    // All other Cluster methods: no-op for warmup
    @Override public ClientSession getClientSession(long clusterSessionId) { return null; }
    @Override public void requestClose() {}
    @Override public Cluster.Role role() { return Cluster.Role.LEADER; }
    // ... etc
}
```

## 26.2 WarmupHarnessImpl

> **V9.3 note.** The concrete class below is the authoritative warmup implementation.
> It matches the plan's `WarmupHarness` interface (owned by TASK-016) and is named
> `WarmupHarnessImpl` to reflect that. The v8 / v9.2 drafts of this section showed a
> class named `WarmupHarness` with method `warmup(TradingClusteredService, WarmupConfig)`;
> v9.3 renames the class and the public method to align with the plan's one-argument
> interface signature (`runGatewayWarmup(FixLibrary)`). `TradingClusteredService` and
> `WarmupConfig` are now injected via the constructor; the `FixLibrary` parameter is
> the interface-level input from the gateway startup (see §25.3).

```java
package ig.rueishi.nitroj.exchange.tooling;

import ig.rueishi.nitroj.exchange.gateway.WarmupHarness;          // interface from TASK-016
import ig.rueishi.nitroj.exchange.cluster.TradingClusteredService;
import ig.rueishi.nitroj.exchange.common.WarmupConfig;
import uk.co.real_logic.artio.library.FixLibrary;

/**
 * Authoritative warmup implementation. Drives synthetic market-data and execution
 * events through {@link TradingClusteredService#onSessionMessage} to trigger C2
 * compilation of the hot paths before live trading begins.
 */
public final class WarmupHarnessImpl implements WarmupHarness {

    private final TradingClusteredService service;
    private final WarmupConfig            config;

    public WarmupHarnessImpl(TradingClusteredService service, WarmupConfig config) {
        this.service = service;
        this.config  = config;
    }

    /** Accessor for the injected {@link WarmupConfig}.
     *  Used by T-021's warmup timing test to compute average iteration latency from
     *  the configured iteration count. Safe to call from any thread (final field). */
    public WarmupConfig config() { return config; }

    @Override
    public void runGatewayWarmup(FixLibrary fixLibrary) {
        log.info("Starting warmup: {} iterations", config.iterations());

        // Install shim cluster — must be done before any strategy accesses cluster.time()
        WarmupClusterShim shim = new WarmupClusterShim();
        service.installClusterShim(shim);  // sets cluster field on all components

        UnsafeBuffer warmupBuffer = new UnsafeBuffer(new byte[512]);
        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        MarketDataEventEncoder mdEncoder   = new MarketDataEventEncoder();
        ExecutionEventEncoder  execEncoder = new ExecutionEventEncoder();

        long start = System.nanoTime();

        for (int i = 0; i < config.iterations(); i++) {
            // Synthesize MarketDataEvent — alternating bid/ask
            encodeSyntheticMarketData(warmupBuffer, headerEncoder, mdEncoder,
                                       Ids.VENUE_COINBASE_SANDBOX, Ids.INSTRUMENT_BTC_USD,
                                       65000_00000000L + i, 65001_00000000L + i, i);

            // Call the business logic dispatch directly (bypassing Aeron)
            service.onSessionMessage(
                WARMUP_SESSION,
                shim.time(),
                warmupBuffer, 0, warmupBuffer.capacity()
            );

            // Synthesize execution feedback every 10 market data events
            if (i % 10 == 0) {
                encodeSyntheticExecReport(warmupBuffer, headerEncoder, execEncoder,
                                           i, ExecType.NEW);
                service.onSessionMessage(WARMUP_SESSION, shim.time(),
                                          warmupBuffer, 0, warmupBuffer.capacity());
            }

            // Keep the FixLibrary's duty cycle alive so its event loop doesn't stall
            // during long warmup runs. This call is non-blocking.
            fixLibrary.poll(1);
        }

        long elapsed = System.nanoTime() - start;
        log.info("Warmup complete: {} iterations in {}ms ({} ns/iter)",
                  config.iterations(), elapsed / 1_000_000, elapsed / config.iterations());

        if (config.requireC2Verified()) {
            verifyC2Compilation();
        }

        // MUST be called before removeClusterShim(). Warmup drives real business logic:
        // OrderManager accumulates synthetic OrderState entries, PortfolioEngine builds
        // synthetic positions. Without this reset those entries persist into live trading.
        service.resetWarmupState();

        // Remove shim — real cluster will be set by onStart()
        service.removeClusterShim();
    }

    private void verifyC2Compilation() {
        // Check via Hotspot Whitebox API (test scope) or log analysis.
        // Minimum: log a warning if avg iter time > threshold (indicates not yet C2).
        // In production build: skip Whitebox; rely on iteration count being sufficient.
        log.info("C2 verification: iteration count ({}) sufficient for C2 compilation",
                  WarmupConfig.production().iterations());
    }
}
```

---

*End of Supplement — Standalone Sections Only*
# IMPLEMENTATION PLAN, TASK CARDS & TESTING STRATEGY
## Trading Platform v2.1.0 — Developer Edition

**Audience:** Core Java developer. No prior FIX, Aeron, SBE, or HFT experience required.
This document tells you exactly what to build, in what order, and how to verify it works.

**Reading guide:**
- Read the Master Spec v2.1 alongside this document
- Every task card references the spec section that defines the behaviour
- Build in the order shown — later tasks depend on earlier ones
- Do not skip the unit tests — they are part of the task, not optional

---

# PART A: PRIMER FOR CORE JAVA DEVELOPERS

Before writing any code, read these four concepts. They recur everywhere.

## A.1 What is SBE?

SBE (Simple Binary Encoding) is like Protobuf but with zero allocation.
You define message schemas in XML. A code generator produces Java encoder/decoder classes.
Instead of creating objects, you wrap a pre-allocated byte buffer and read/write fields directly.

```java
// Normal Java (allocates):
MyMessage msg = new MyMessage();
msg.setPrice(65000.0);
byte[] bytes = serialize(msg);   // allocates

// SBE (zero allocation):
priceEncoder.wrap(buffer, 0);    // points at pre-allocated buffer
priceEncoder.priceScaled(6500000000000L);  // writes 8 bytes directly into buffer
// No object created. No GC pressure.
```

**Your job:** Run the SBE gradle task once. It generates all encoder/decoder classes.
Never edit generated files. Only edit `messages.xml`.

## A.2 What is Aeron?

Aeron is a messaging library. Think of it like a very fast queue between processes.
Instead of TCP sockets or blocking queues, it uses shared memory (IPC) or UDP.

```java
// Publishing (like writing to a queue):
Publication pub = aeron.addPublication("aeron:ipc", 1001);
pub.offer(buffer, 0, length);   // zero-copy write to shared memory

// Subscribing (like reading from a queue):
Subscription sub = aeron.addSubscription("aeron:ipc", 1001);
sub.poll(myHandler, 10);        // reads up to 10 messages, calls myHandler for each
```

**Your job:** You never configure raw Aeron directly. Artio and Aeron Cluster wrap it.
You interact with Aeron only through `cluster.egressPublication().offer()` in the cluster.

## A.3 What is Aeron Cluster?

Aeron Cluster runs your business logic as a replicated state machine across 3 nodes.
You implement one interface: `ClusteredService`. The framework calls `onSessionMessage()`
for every incoming message in the same order on all 3 nodes. This guarantees consistency.

```java
// You implement this:
public class TradingClusteredService implements ClusteredService {

    @Override
    public void onSessionMessage(ClientSession session, long timestamp,
                                  DirectBuffer buffer, int offset, int length) {
        // ALL your business logic goes here.
        // This is called in exactly the same order on all 3 cluster nodes.
        // Never call System.nanoTime() here. Use cluster.time() instead.
    }
}
```

**Your job:** Implement `TradingClusteredService` and wire all components to it.

## A.4 What is Artio?

Artio is a FIX protocol engine. Think of it as a library that manages a FIX TCP connection.
You implement callback interfaces. Artio calls your callbacks when FIX messages arrive.

```java
// You implement this (simplified):
public class MyHandler implements SessionHandler {
    @Override
    public Action onMessage(DirectBuffer buffer, int offset, int length,
                            int libraryId, Session session, ..., char msgType) {
        // Called when a FIX message arrives from the exchange
        // msgType='8' means ExecutionReport
        // msgType='W' means MarketDataSnapshotFullRefresh
        return CONTINUE;
    }
}
```

**Your job:** Implement `MarketDataHandler` and `ExecutionHandler` using this pattern.

---

# PART B: IMPLEMENTATION TASK CARDS

Tasks are ordered. Each task lists: what to build, spec reference, inputs, outputs, and done criteria.

---

## EPIC 1: FOUNDATION
*Build the shared infrastructure. No business logic yet.*

---

### TASK-001: Project Scaffold & Build System
**Effort:** 1 day
**Depends On:** None
**Test class:** None — verified transitively by `./gradlew build` succeeding and `./gradlew :platform-common:test` running
**Spec ref:** Section 21.2, 21.3

**What to build:**
Create the Gradle multi-module project with the exact structure in Section 21.3.
Set up Java 21 toolchain with all JVM flags from Section 21.2.

**Steps:**
1. Create root `build.gradle.kts` exactly as in Section 21.2
2. Create 4 submodule directories: `platform-common`, `platform-gateway`, `platform-cluster`, `platform-tooling`
3. Create `settings.gradle.kts` including all 4 modules
4. Create each module's `build.gradle.kts` with correct dependencies
5. Run `./gradlew build` — must succeed with no errors
6. Run `./gradlew :platform-common:dependencies` — verify all versions resolved

**Files to create:**
```
build.gradle.kts
settings.gradle.kts
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
platform-common/build.gradle.kts
platform-gateway/build.gradle.kts
platform-cluster/build.gradle.kts
platform-tooling/build.gradle.kts
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/      (empty placeholder)
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/    (empty placeholder)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/    (empty placeholder)
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/simulator/  (empty placeholder)
```

**Add to root build.gradle.kts — e2eTest source set and task:**
```kotlin
subprojects {
    sourceSets {
        create("e2eTest") {
            java.srcDir("src/e2eTest/java")
            compileClasspath += sourceSets["main"].output + sourceSets["test"].output
            runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
        }
    }
    tasks.register<Test>("e2eTest") {
        description = "Runs end-to-end tests with CoinbaseExchangeSimulator"
        group = "verification"
        testClassesDirs = sourceSets["e2eTest"].output.classesDirs
        classpath = sourceSets["e2eTest"].runtimeClasspath
        useJUnitPlatform { includeTags("E2E") }
    }
    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("E2E")                          // E2E runs via e2eTest task only
            excludeTags("SlowTest")                     // SlowTest runs in CI pre-release pipeline only
            excludeTags("RequiresProductionEnvironment") // must run on colocation hardware; see Section 20.4
        }
    }
}
```

**Add `night-config` to platform-common/build.gradle.kts:**
```kotlin
implementation("com.electronwill.night-config:toml:3.6.7")
// Replaces toml4j:0.7.2 which has had no releases since 2019.
// night-config is actively maintained, TOML 1.0 compliant, and handles
// arrays of tables correctly (a known toml4j limitation).
// API change: use com.electronwill.nightconfig.toml.TomlParser instead of
// com.moandjiezana.toml.Toml. See ConfigManager for updated parsing calls.
```

**Done when:**
- `./gradlew build` succeeds
- `./gradlew :platform-common:test` runs (0 tests, 0 failures)
- `./gradlew e2eTest` task exists and runs (0 tests until TASK-016 complete)
- Java version reported as 21 by `./gradlew -version`

---

### TASK-002: SBE Schema & Code Generation
**Effort:** 1 day
**Depends On:** TASK-001
**Test class:** Verified transitively — all SBE encoders/decoders exercised by T-001 through T-024 and IT-001 through IT-005. No standalone unit test: generated code is not tested in isolation.
**Spec ref:** Sections 8.1–8.13

**What to build:**
Create `messages.xml` with ALL SBE message definitions from Sections 8 and 25.
Configure the Gradle SBE generation task. Verify generated classes compile.

**Steps:**
1. Create `platform-common/src/main/resources/messages.xml`
2. Copy all `<types>` definitions from Section 8.1
3. Add all `<sbe:message>` definitions from Sections 8.2–8.8
4. Add the 5 messages defined in Sections 8.9–8.13
5. Add the 3 snapshot messages from Section 16.7
6. Add the `AdminCommandType` enum from Section 8.12
7. Run `./gradlew :platform-common:generateSBE`
8. Verify `src/generated/java/ig/rueishi/nitroj/exchange/messages/` contains encoder/decoder pairs

**Files to create:**
```
platform-common/src/main/resources/messages.xml
```

**Generated files (do not edit):**
```
platform-common/src/generated/java/ig/rueishi/nitroj/exchange/messages/
    MarketDataEventEncoder.java
    MarketDataEventDecoder.java
    ExecutionEventEncoder.java
    ExecutionEventDecoder.java
    VenueStatusEventEncoder.java
    VenueStatusEventDecoder.java
    NewOrderCommandEncoder.java
    NewOrderCommandDecoder.java
    CancelOrderCommandEncoder.java
    CancelOrderCommandDecoder.java
    OrderStatusQueryCommandEncoder.java
    OrderStatusQueryCommandDecoder.java
    BalanceQueryRequestEncoder.java
    BalanceQueryRequestDecoder.java
    BalanceQueryResponseEncoder.java
    BalanceQueryResponseDecoder.java
    RecoveryCompleteEventEncoder.java
    RecoveryCompleteEventDecoder.java
    AdminCommandEncoder.java
    AdminCommandDecoder.java
    ReplaceOrderCommandEncoder.java
    ReplaceOrderCommandDecoder.java
    FillEventEncoder.java
    FillEventDecoder.java
    PositionEventEncoder.java
    PositionEventDecoder.java
    RiskEventEncoder.java
    RiskEventDecoder.java
    OrderStateSnapshotEncoder.java
    OrderStateSnapshotDecoder.java
    PositionSnapshotEncoder.java
    PositionSnapshotDecoder.java
    RiskStateSnapshotEncoder.java
    RiskStateSnapshotDecoder.java
    MessageHeaderEncoder.java
    MessageHeaderDecoder.java
    (plus enum classes: Side, OrdType, TimeInForce, ExecType, EntryType,
     UpdateAction, VenueStatus, BooleanType, RiskEventType, AdminCommandType)
```

**Done when:**
- `./gradlew :platform-common:generateSBE` succeeds with no errors
- `./gradlew :platform-common:compileJava` succeeds (generated classes compile)

---

### TASK-003: Constants and Ids
**Effort:** 0.5 days
**Depends On:** TASK-002
**Spec ref:** Section 3.5
**Test class:** T-003 OrderStatusTest, T-024 ScaledMathTest

**What to build:**
`Ids.java` — all integer ID constants used system-wide.
`OrderStatus.java` — byte constants for order status.
`Side.java` — not the SBE enum; a utility class for readable constants.

**Files to create:**
```
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/Ids.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/OrderStatus.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ScaledMath.java  (Section 11.5)
```

**Implementation:** Copy exactly from Section 3.5 and Section 7.2.


**Tests to implement:**
```
T-003 OrderStatusTest — CREATE this file:
  isTerminal_filled_returnsTrue()
  isTerminal_canceled_returnsTrue()
  isTerminal_replaced_returnsTrue()
  isTerminal_rejected_returnsTrue()
  isTerminal_expired_returnsTrue()
  isTerminal_new_returnsFalse()
  isTerminal_partiallyFilled_returnsFalse()
  isTerminal_pendingNew_returnsFalse()
  isTerminal_pendingCancel_returnsFalse()
  allStatusConstants_uniqueValues()
```
```
T-024 ScaledMathTest — CREATE this file:
  safeMulDiv_hiZero_typicalMMLot_exact()
  safeMulDiv_hiZero_zeroQty_returnsZero()
  safeMulDiv_hiZero_unitQty_returnsPrice()
  safeMulDiv_hiNonZero_1BtcAt65K_exact()
  safeMulDiv_hiNonZero_10BtcAt1M_exact()
  safeMulDiv_hiNonZero_largePriceDiff_noPnlWrap()
  vwap_twoEqualLotsAtDifferentPrices_correctAverage()
  vwap_firstFill_oldQtyZero_returnsFillPrice()
  vwap_largePosition_noOverflow()
  vwap_unequalLots_vwapWeightedCorrectly()
  acceptanceCriteria_PF009_noSilentWrapForLargePriceAndQty()
```

**Done when:**
- `Ids.SCALE == 100_000_000L`
- `Ids.MAX_VENUES == 16`
- `Ids.MAX_INSTRUMENTS == 64`
- `OrderStatus.isTerminal(OrderStatus.FILLED) == true`
- `OrderStatus.isTerminal(OrderStatus.NEW) == false`
- `ScaledMath.safeMulDiv(100_000_000L, 6_500_000_000_000L, Ids.SCALE) == 6_500_000_000_000L`
- `ScaledMath.vwap(6_500_000_000_000L, 10_000_000L, 6_510_000_000_000L, 10_000_000L) == 6_505_000_000_000L`

---

### TASK-004: Configuration — TOML Loading
**Effort:** 2 days
**Depends On:** TASK-001
**Test class:** T-002 ConfigManagerTest
**Spec ref:** Section 22

**What to build:**
Config model classes and TOML loader.

**Files to create:**
```
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/
    ConfigManager.java            (includes ConfigValidationException, required())
    ClusterNodeConfig.java        (includes singleNodeForTest() factory)
    GatewayConfig.java            (includes forTest() factory — Section G.4)
    FixConfig.java                (carries per-gateway senderCompId / targetCompId — §22.5)
    RestConfig.java
    CredentialsConfig.java
    CpuConfig.java                (includes noPinning() factory)
    DisruptorConfig.java
    WarmupConfig.java             (includes development() factory — Section 27.6)
    VenueConfig.java              (§22.2 fields: id, name, fixHost, fixPort, sandbox — NO FIX session identity; see §17.8 for why)
    InstrumentConfig.java         (§22.3 fields: id, symbol, baseAsset, quoteAsset)
    RiskConfig.java               (includes InstrumentRisk inner record)
    MarketMakingConfig.java       (record — Section 6.1.1)
    ArbStrategyConfig.java        (record — Section 6.2.1)
```

**Key method:**
```java
// In ConfigManager:
public static long parseScaled(String decimal)  // "1.5" → 150_000_000L
```

**TOML library:** `com.electronwill.night-config:toml:3.6.7` — add to `platform-common/build.gradle.kts` (V9: replaces toml4j per §21.1).

**Additional files required (not in file list above):**
```
config/admin.toml          — admin tool connection + security config
```

**admin.toml:**
```toml
[connection]
aeronDir      = "/dev/shm/aeron"
adminChannel  = "aeron:udp?endpoint=gateway:9099"
adminStreamId = 9099

[security]
operatorId        = 1
hmacKeyVaultPath  = "secret/trading/admin/hmac-key"
hmacKeyBase64Dev  = "REPLACE_WITH_BASE64_KEY_FOR_DEV_ONLY"
nonceFile         = "./state/admin-nonce.dat"
allowedOperatorIds = [1, 2, 3]   # whitelist — reject any operatorId not listed; empty = reject all
# newHmacKeyVaultPath = ""       # uncomment during key rotation; remove after rotation complete
```

**Add to ConfigManager — validation exception and required() helper:**
```java
public class ConfigValidationException extends RuntimeException {
    public ConfigValidationException(String fieldPath, String message) {
        super("Config validation failed at [" + fieldPath + "]: " + message);
    }
}

public static <T> T required(T value, String fieldPath) {
    if (value == null) throw new ConfigValidationException(fieldPath, "required field is missing");
    return value;
}
```

**Add to ClusterNodeConfig — test factory:**
```java
/** Single-node cluster config for integration/E2E tests. */
public static ClusterNodeConfig singleNodeForTest() {
    return ClusterNodeConfig.builder()
        .nodeId(0)
        .aeronDir("/dev/shm/aeron-test-" + ProcessHandle.current().pid())
        .archiveDir("./build/test-archive")
        .snapshotDir("./build/test-snapshot")
        .clusterMembers("0,localhost:29010,localhost:29020,localhost:29030,localhost:29040")
        .ingressEndpoint("localhost:29010")
        .snapshotIntervalS(3600)
        .build();
}
```


**Tests to implement:**
```
T-002 ConfigManagerTest — CREATE this file (13 cases; see §D.2 for Given/When/Then bodies):
  parseScaled_wholeNumber_correct()
  parseScaled_decimal_correct()
  parseScaled_eightDecimalPlaces_exact()
  parseScaled_zeroValue_returnsZero()
  parseScaled_zeroFraction_correct()
  parseScaled_nineDecimalPlaces_truncatesAt8()
  parseScaled_largeValue_noOverflow()
  loadCluster_validToml_allFieldsPopulated()
  loadCluster_missingOptionalField_usesDefault()
  loadCluster_missingRequiredField_throwsConfigValidationException()
  loadGateway_validToml_allFieldsPopulated()
  forTest_noCpuPinning_developmentWarmup()
  minQuoteSizeFractionBps_loadedFromToml()
```

**Done when:**
- `ConfigManager.parseScaled("1.0") == 100_000_000L`
- `ConfigManager.parseScaled("0.01") == 1_000_000L`
- `ConfigManager.parseScaled("65432.10") == 6_543_210_000_000L`
- Loading `cluster-node-0.toml` produces `ClusterNodeConfig` with correct values
- `ConfigManager.required(null, "field")` throws `ConfigValidationException`
- `ClusterNodeConfig.singleNodeForTest()` returns valid config with loopback addresses

---

### TASK-005: IdRegistry
**Effort:** 1 day
**Depends On:** TASK-004
**Test class:** T-001 IdRegistryImplTest
**Spec ref:** Section 17.7

**What it does:**
Implements the `IdRegistry` — a lookup table that maps between human-readable names
(venue names, instrument symbols, and live Artio `Session.id()` values) and the integer IDs used everywhere
in the system. Loaded once at startup from config. After `init()`, all lookups are
zero-allocation array reads. Two contracts are critical: `venueId(long sessionId)` throws
`IllegalStateException` for unknown sessions (programming error — all sessions must be
registered when Artio exposes `Session.id()`); `instrumentId(symbol)` returns 0 for unknown symbols (not an error —
venues may send unexpected instruments which the system silently ignores).

**Files to create:**
```
platform-common/src/main/java/ig/rueishi/nitroj/exchange/registry/
    IdRegistry.java         (interface — Section 17.7)
    IdRegistryImpl.java     (implementation — Section 17.8)
```

**Interface behavior — must be exact:**
```
venueId(long sessionId) — throws IllegalStateException for unknown sessions.
                          This is always a programming error; live Artio sessions
                          must be registered as soon as Session.id() is available.
instrumentId(symbol)    — returns 0 for unknown symbols (NOT a programming error).
                          Venues may send instruments not in config; silently ignored.
```


**Tests to implement:**
```
T-001 IdRegistryImplTest — CREATE this file:
  knownVenueName_returnsId()
  unknownVenueName_returnsZero()
  knownSymbol_returnsInstrumentId()
  unknownSymbol_returnsZero_doesNotThrow()
  symbolOf_knownId_returnsSymbol()
  symbolOf_zeroId_returnsNull()
  venueId_knownSessionId_returnsId_afterRegisterSession()
  venueId_unknownSessionId_throwsIllegalState()
  afterInit_noAllocationsOnHotPath()
  registerSession_sameSessionIdDifferentVenue_throwsIllegalState() ← V9.7/V10: duplicate-guard
  registerSession_sameSessionIdSameVenue_ok()                      ← V9.7/V10: re-register same session ID is no-op
  init_doesNotPopulateVenueBySession()                         ← V9.7/V10: init() does not touch venueBySessionId
```

**Gateway wiring (V9.7 / §17.8):**
The gateway calls `idRegistry.registerSession(venueId, session.id())` from the Artio
session creation/acquisition/logon path after
`GatewayMain.buildIdRegistry(venues, instruments)` has called `init()`. The cluster
never calls `registerSession()` — the cluster has no FIX sessions. See §17.8
"Gateway-side wiring" for the reference helper.

**Done when:**
- `idRegistry.instrumentId("BTC-USD") == 1`
- `idRegistry.symbolOf(1).equals("BTC-USD")`
- `idRegistry.venueId(coinbaseSessionId) == 1` **after the gateway has called `registerSession(venueId, session.id())`**
- Unknown symbol returns 0 (does NOT throw)
- Unknown session throws `IllegalStateException` (does NOT return 0)
- Re-registering the same session ID for a different venueId throws
- Zero allocations after `init()` (verify with allocation-tracking test)

---

## EPIC 2: GATEWAY PROCESS
*Build the FIX connectivity layer.*

---

### TASK-006: CoinbaseExchangeSimulator
**Effort:** 5 days
**Depends On:** TASK-002, TASK-003
**Test class:** T-015 CoinbaseExchangeSimulatorTest
**Spec ref:** Part C of this document (CoinbaseExchangeSimulator full implementation)

**What to build:**
A complete FIX acceptor (server) that simulates Coinbase Advanced Trade.
This is needed for ALL automated tests. Build it before any real gateway code.

**Files to create:**
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/
    CoinbaseExchangeSimulator.java
    SimulatorSession.java           (inner class in C.4)
    SimulatorConfig.java            (includes defaults() and forPort() — Section C.6)
    SimulatorScenario.java          (interface — Section C.3)
    FillSchedule.java               (delayed fill control — Section C.3)
    MarketDataPublisher.java        (publishes synthetic market data — Section C.5)
```

**Full implementation spec:** See Part C of this document.


**Tests to implement:**
```
T-015 CoinbaseExchangeSimulatorTest — CREATE this file:
  simulator_starts_acceptsConnection()
  fillMode_immediate_fillSentAfterAck()
  fillMode_partial_twoFillsSent()
  fillMode_noFill_ackSentNoFill()
  fillMode_delayed_fillSentAfterConfiguredDelay()
  setMarket_nextTickReflectsNewPrices()
  orderStatusRequest_openOrder_statusReportSent()
  assertOrderReceived_orderPresent_passes()
  reset_clearsAllState()
  fillMode_rejectAll_rejectSentAfterAck()
  cancel_unknownOrder_cancelRejectSent()
  orderStatusRequest_unknownOrder_unknownStatusSent()
  assertOrderReceived_orderAbsent_throws()
  scheduleDisconnect_disconnectsAfterDelay()
  fillMode_disconnectOnFill_sessionDroppedOnFirstFill()
```

**Done when:**
- `CoinbaseExchangeSimulator.start()` completes without exception; FIX logon succeeds
- `FillMode.IMMEDIATE` → fill sent within 10ms of order ACK
- `FillMode.PARTIAL_THEN_FULL` → two fills sent at 50% + 50%
- `FillMode.REJECT_ALL` → reject sent; no fill
- `FillMode.NO_FILL` → ACK sent; no fill within 2 seconds
- `FillMode.DELAYED_FILL` → fill sent after `FillSchedule.delayMs`
- `FillMode.DISCONNECT_ON_FILL` → FIX session dropped on first fill
- `setMarket(bid, ask)` → next market data tick uses new prices
- `assertOrderReceived(side, price, qty)` → passes when order present; throws when absent
- `reset()` → clears all orders, fills, rejects, and logon count
- All 14 tests in T-015 pass

---

### TASK-007: Gateway Disruptor (Fan-In)
**Effort:** 1 day
**Depends On:** TASK-002
**Test class:** IT-001 GatewayDisruptorIntegrationTest
**Spec ref:** Sections 13.3, 14

**What to build:**
The multi-producer ring buffer inside the gateway process.

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    GatewaySlot.java
    GatewayDisruptor.java
```

**GatewaySlot:** Pre-allocated, `@Contended`, 512-byte `UnsafeBuffer`. See Section 14.2.
**GatewayDisruptor:** `MultiProducer`, 4096 slots, `BusySpinWaitStrategy`. See Section 13.3.


**Tests to implement:**
```
IT-001 GatewayDisruptorIntegrationTest — CREATE this file:
  multipleProducers_messagesConsumedInOrder()
  singleProducer_highThroughput_noDrops()
  backpressure_alertFires_whenNearlyFull()
  slotReset_afterConsume_fieldsZeroed()
  ringFull_tryPublishReturnsFalse_counterIncremented()
  exactlyRingBufferSizeMessages_noDrops()
  producerAndConsumerSameThread_noDeadlock()
  executionEvent_neverDropped_underBackPressure()
  marketDataEvent_droppedUnderSustainedBackpressure_fillNeverDropped()
```

**Done when:**
- `GatewayDisruptor.publish(buffer, length)` is callable from multiple threads without exception
- Messages consumed in order
- Zero allocations per publish after warmup
- Backpressure alert fires when < 512 slots remaining
- `ExecutionEvent` (templateId=2) retries indefinitely under back-pressure; never dropped
- `MarketDataEvent` dropped after 10µs under sustained back-pressure; `AERON_BACKPRESSURE_COUNTER` incremented
- `AeronPublisher` peeks at `templateId` via pre-allocated `MessageHeaderDecoder` before retry loop (no allocation)

---

### TASK-008: CoinbaseLogonStrategy
**Effort:** 1 day
**Depends On:** TASK-001
**Test class:** T-014 CoinbaseLogonStrategyTest
**Spec ref:** Section 9.8

**What it does:**
Implements the Coinbase-specific FIX Logon handler. Coinbase requires HMAC-SHA256
authentication in the Logon message using the API key, passphrase, and secret.
`CoinbaseLogonStrategy` implements Artio's `LogonCustomiser` interface and intercepts
the outbound Logon to add tags 96 (RawData = HMAC signature), 554 (Password =
passphrase), and 8013 (CancelOrdersOnDisconnect). The HMAC is computed from:
`timestamp + 'A' + MsgSeqNum + SenderCompID + TargetCompID + passphrase`
encoded in Base64. Credentials are loaded from Vault in production (Section 9).

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    CoinbaseLogonStrategy.java
```


**Tests to implement:**
```
T-014 CoinbaseLogonStrategyTest — CREATE this file:
  onLogon_signatureGenerated_correctHmacSha256()
  onLogon_timestampInTag52_currentEpochSeconds()
  onLogon_tag554_rawSecret_notBase64Encoded()
  onLogon_tag96_prehashString_correctFormat()
  onLogon_invalidApiKey_throwsConfigValidationException()
  onLogon_signatureDifferentEachCall_timestampChanges()
  onLogon_emptyPassphrase_handledGracefully()
```

**Done when:**
- HMAC-SHA256 signature computed correctly (verified against Coinbase sandbox)
- `configureLogon()` sets tags 96, 95, 554, 98 on logon message
- Logon succeeds on Coinbase sandbox (verified by live test or simulator)

---

### TASK-009: MarketDataHandler
**Effort:** 2 days
**Depends On:** TASK-002, TASK-005, TASK-007
**Test class:** T-016 MarketDataHandlerTest
**Spec ref:** Sections 3.4.1, 4.1, 9.3

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    MarketDataHandler.java
```

**What it does:**
Implements Artio `SessionHandler`. On FIX MsgType=W (Snapshot) or MsgType=X (IncrementalRefresh):
1. Records `ingressTimestampNanos = System.nanoTime()` — FIRST line, before any decoding
2. Decodes FIX fields using Artio-generated decoder
3. Resolves `instrumentId = idRegistry.instrumentId(symbol)`
4. SBE-encodes into `GatewaySlot`
5. Publishes to `GatewayDisruptor` via non-blocking `tryPublish()`

**MsgType=X — repeating group iteration (one SBE event per entry):**
```
Tag 268 (NoMDEntries) = count of repeating group entries.
For each entry: decode tags 269, 270, 271, 279, 1023, 55.
Each entry → one separate GatewayDisruptor publication.
Artio API: decoder.noMDEntries() returns group count; call .next() to advance.
```

**FIX tags to decode per entry:**
- Tag 55 (Symbol) → instrumentId via IdRegistry
- Tag 269 (MDEntryType) → EntryType (0=Bid, 1=Ask, 2=Trade)
- Tag 270 (MDEntryPx) → priceScaled
- Tag 271 (MDEntrySize) → sizeScaled
- Tag 279 (MDUpdateAction) → UpdateAction
- Tag 1023 (MDPriceLevel) → priceLevel
- Tag 60 (TransactTime) → exchangeTimestampNanos

**Back-pressure — do NOT block the artio-library thread:**
```java
// Use tryPublish() — non-blocking; discard tick if ring full
boolean published = gatewayDisruptor.tryPublish(slotBuffer, length);
if (!published) {
    metricsCounters.increment(DISRUPTOR_FULL_COUNTER);
    // Discard this tick — market data is lossy by design; orders are not
}
```

**Price scaling without allocation (no String alloc via Artio DecimalFloat):**
```java
private static long scaleDecimal(DecimalFloat df) {
    int shift = 8 + df.scale();   // scale() is negative for decimal places
    if (shift >= 0) return df.value() * pow10(shift);
    else            return df.value() / pow10(-shift);
}
private static final long[] POW10 = {
    1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L
};
private static long pow10(int n) { return n < POW10.length ? POW10[n] : 0L; }
```

**SBE encode pattern (write directly into pre-claimed GatewaySlot buffer):**
```java
GatewaySlot slot = disruptor.claimSlot();  // returns null if ring full
if (slot == null) {
    metricsCounters.increment(DISRUPTOR_FULL_COUNTER);
    return;
}
mdEventEncoder
    .wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
    .venueId(venueId)
    .instrumentId(instrumentId)
    .entryType(entryType)
    .updateAction(updateAction)
    .priceScaled(priceScaled)
    .sizeScaled(sizeScaled)
    .priceLevel(priceLevelOrZero)
    .ingressTimestampNanos(ingressNanos)
    .exchangeTimestampNanos(parseTransactTime(entry.transactTime()))
    .fixSeqNum(header.msgSeqNum());
slot.length = mdEventEncoder.encodedLength() + HEADER_LENGTH;
disruptor.publishSlot(slot);
```


**Tests to implement:**
```
T-016 MarketDataHandlerTest — CREATE this file:
  msgTypeW_singleEntry_bidEntry_correctSBEPublished()
  msgTypeW_singleEntry_askEntry_correctSBEPublished()
  msgTypeX_multipleEntries_allPublishedSeparately()
  ingressTimestampNanos_setBeforeDecoding()
  unknownSymbol_notPublishedToDisruptor_warnLogged()
  disruptorFull_messageDiscarded_counterIncremented()
  msgType8_ignored_notPublished()
  priceScaling_decimalPrice_scaledCorrectly()
  exchangeTimestamp_parsedFromTag60_inNanos()
  msgTypeX_singleEntry_publishedOnce()
  priceScaling_zeroPrice_publishedAsZero()
  msgTypeW_zeroEntries_nothingPublished()
```

**Done when:**
- MsgType=W with 2 book entries → 2 separate SBE publications
- MsgType=X with 3 incremental entries → 3 separate SBE publications
- `ingressTimestampNanos` set before any other processing
- Zero allocations per message (except initial Artio handshake)
- Unknown symbol → log WARN, do NOT publish to Disruptor
- Ring buffer full → tick discarded, DISRUPTOR_FULL_COUNTER incremented, artio-library thread NOT blocked
- MsgType=8 (ExecutionReport at wrong handler) → ignored, returns CONTINUE, no SBE publish

---

### TASK-010: ExecutionHandler
**Effort:** 2 days
**Depends On:** TASK-002, TASK-005, TASK-007
**Test class:** T-017 ExecutionHandlerTest
**Spec ref:** Sections 3.4.2, 9.3, 9.4

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    ExecutionHandler.java
```

**What it does:**
Implements Artio `SessionHandler`. On FIX MsgType=8:
1. Records `ingressTimestampNanos = System.nanoTime()` — FIRST line
2. Decodes ALL fields from Section 9.4 mapping table
3. SBE-encodes `ExecutionEvent` into `GatewaySlot`
4. Publishes to `GatewayDisruptor`

**Coinbase quirks to handle:**
- `ExecType=I` (Order Status) → set `execType=ORDER_STATUS` in SBE; do NOT treat as state change
- `ExecType=F` with `LeavesQty=0` → `isFinal=TRUE`
- `ExecType=F` with `LeavesQty>0` → `isFinal=FALSE` (partial fill)
- `ExecType=5` (Replaced) → `isFinal=TRUE`; the old order is terminal; new order ID in tag 11
- Tag 103 (OrdRejReason): decode conditionally — only present on `ExecType=8` (Rejected):
```java
byte rejectCode = (execDecoder.execType() == '8' && execDecoder.hasOrdRejReason())
                  ? (byte) execDecoder.ordRejReason() : 0;
```


**Tests to implement:**
```
T-017 ExecutionHandlerTest — CREATE this file:
  execTypeNew_correctSBEFields()
  execTypeFill_fullFill_isFinalTrue()
  execTypeFill_partialFill_isFinalFalse()
  execTypeRejected_rejectCodePopulated()
  execTypeRejected_noRejectCode_rejectCodeZero()
  execTypeCanceled_isFinalTrue()
  execTypeReplaced_isFinalTrue()
  execTypeOrderStatus_execTypeOrderStatusInSBE()
  clOrdId_parsedAsLong_fromTag11()
  ingressTimestampNanos_setBeforeDecoding()
  venueOrderId_varLengthField_populatedFromTag37()
  execId_varLengthField_populatedFromTag17()
  missingTag103_rejectCodeZero()
  execTypeFill_zeroLeavesQty_isFinalTrue()
  execTypeFill_largeFillQty_noOverflow()
  clOrdId_maxLongValue_parsedCorrectly()
```

**Done when:**
- All 9.4 tag mappings produce correct SBE fields
- `execType='F'` + `LeavesQty=0` → `isFinal=TRUE`
- `execType='I'` → `execType=ORDER_STATUS` in SBE
- `execType='5'` → `ExecType.REPLACED`, `isFinal=TRUE`
- Tag 103 decoded only when `execType='8'`; zero on all other exec types
- `ingressTimestampNanos` is first operation

---

### TASK-011: VenueStatusHandler
**Effort:** 0.5 days
**Depends On:** TASK-002, TASK-005, TASK-007
**Test class:** T-023 VenueStatusHandlerTest
**Spec ref:** Sections 3.4.2, 4.4

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    VenueStatusHandler.java
```

**What it does:**
Implements Artio `SessionAcquireHandler` (not a simple logon callback — Artio's actual API):
```java
// VenueStatusHandler implements SessionAcquireHandler:
@Override
public SessionHandler onSessionAcquired(Session session, SessionInfo sessionInfo) {
    int venueId = idRegistry.venueId(sessionInfo.sessionId());
    publishVenueStatus(venueId, VenueStatus.CONNECTED);
    return new VenueSessionHandler(venueId, this);  // inner class handles ongoing events
}

// VenueSessionHandler (inner class) handles disconnect and heartbeat timeout:
class VenueSessionHandler implements SessionHandler {
    @Override
    public Action onDisconnect(int libraryId, Session session) {
        publishVenueStatus(this.venueId, VenueStatus.DISCONNECTED);
        return CONTINUE;
    }
    // onMessage() returns CONTINUE for all other message types (no-op)
}
```
Note: Artio heartbeat timeout fires `onDisconnect()` — covered by the above.


**Tests to implement:**
```
T-023 VenueStatusHandlerTest — CREATE this file:
  onSessionAcquired_publishesConnectedEvent()
  onDisconnect_publishesDisconnectedEvent()
  onSessionAcquired_venueIdResolvedFromSessionId()
  onSessionAcquired_unknownSessionId_throws()
  onDisconnect_correctVenueId_inEvent()
  multipleLogons_sameSession_onlyOneConnectedEvent()
  onDisconnect_afterDisconnect_idempotent()
```

**Done when:**
- `onSessionAcquired` produces `VenueStatusEvent{CONNECTED}` with correct venueId
- `onDisconnect` produces `VenueStatusEvent{DISCONNECTED}` with correct venueId
- Artio heartbeat timeout → onDisconnect fires → DISCONNECTED event published
- venueId resolved from `SessionInfo.sessionId()` via IdRegistry (throws on unknown session)

---

### TASK-012: AeronPublisher (Disruptor Consumer)
**Effort:** 1 day
**Depends On:** TASK-007
**Test class:** IT-001 GatewayDisruptorIntegrationTest
**Spec ref:** Section 14.3, 14.4

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    AeronPublisher.java
```

**What it does:**
Single consumer of `GatewayDisruptor`. Reads each `GatewaySlot` and calls
`aeronPublication.offer(buffer, 0, length)`. Handles back-pressure (spin-retry up to 10µs).

**Concrete back-pressure + reset implementation:**
```java
// In AeronPublisher.onEvent(GatewaySlot slot, long sequence, boolean endOfBatch):
long spinStart = System.nanoTime();
long result;
do {
    result = aeronPublication.offer(slot.buffer, 0, slot.length);
    if (result > 0) break;
    Thread.onSpinWait();
} while (System.nanoTime() - spinStart < 10_000L);  // 10µs budget

if (result < 0) {
    metricsCounters.increment(AERON_BACKPRESSURE_COUNTER);
}
slot.reset();  // MUST call reset even if offer failed — prevents stale data on reuse
```


**Tests to implement:**
```
IT-001 GatewayDisruptorIntegrationTest — EXISTS (created by TASK-007); verify all pass, do not recreate:
  multipleProducers_messagesConsumedInOrder()
  singleProducer_highThroughput_noDrops()
  backpressure_alertFires_whenNearlyFull()
  slotReset_afterConsume_fieldsZeroed()
  ringFull_tryPublishReturnsFalse_counterIncremented()
  exactlyRingBufferSizeMessages_noDrops()
  producerAndConsumerSameThread_noDeadlock()
```

**Done when:**
- Every slot published to Disruptor appears on Aeron subscription (no drops in test)
- Back-pressure counter increments when Aeron is slow
- `slot.reset()` called after each publish, including back-pressured cases
- Spin uses `Thread.onSpinWait()` (not `Thread.sleep()` — that would block the thread)

---

### TASK-013: OrderCommandHandler (Egress)
**Effort:** 1.5 days
**Depends On:** TASK-002, TASK-005, TASK-008, TASK-010
**Test class:** T-019 OrderCommandHandlerTest
**Spec ref:** Sections 3.4, 9.5, 9.6, 9.7, Section 8.13

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    OrderCommandHandler.java
    ExecutionRouter.java            (interface — Section 17.6)
    ExecutionRouterImpl.java
```

Note: `PendingReplaceEntry.java` and `CancelAckListener.java` are NOT needed.
Replace sequencing is owned by the cluster `OrderManager` (Section 9.7). The gateway
is a pure forwarder — it routes whatever commands the cluster publishes.

**What it does:**
Polls Aeron cluster egress and routes SBE commands to Artio. The gateway is a pure
forwarder — it decodes whatever the cluster publishes and sends the corresponding FIX message.

**Critical: onFragment must iterate through the entire buffer.**

A single Aeron fragment may contain multiple concatenated SBE messages. This occurs
specifically for arb leg pairs (Section 6.2.3 — both legs encoded into one offer).
The `onFragment` implementation MUST loop until the buffer is exhausted:

```java
@Override
public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
    final int endOffset = offset + length;
    while (offset < endOffset) {
        headerDecoder.wrap(buffer, offset);
        int templateId = headerDecoder.templateId();
        int blockLen   = headerDecoder.blockLength();
        int version    = headerDecoder.version();
        int hdrLen     = headerDecoder.encodedLength();

        switch (templateId) {
            case NewOrderCommandDecoder.TEMPLATE_ID -> {
                newOrderDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                executionRouter.routeNewOrder(newOrderDecoder);
                // NewOrderCommand is fixed-length (no varString fields) — blockLen is the full body.
                offset += hdrLen + blockLen;
            }
            case CancelOrderCommandDecoder.TEMPLATE_ID -> {
                cancelDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                executionRouter.routeCancel(cancelDecoder);
                // CancelOrderCommand has a varString venueOrderId field — use encodedLength()
                // not blockLen, so the variable-length tail is included in the advance.
                offset += hdrLen + cancelDecoder.encodedLength();
            }
            // ... other cases; always prefer decoder.encodedLength() over blockLen
            // for any message that contains <data> (varString) fields in the SBE schema.
            default -> {
                log.warn("Unknown templateId {} in egress fragment", templateId);
                break;  // cannot advance safely — stop processing this fragment
            }
        }
    }
}
```

**Offset advancement rule:** Use `hdrLen + blockLen` ONLY for messages with no `<data>` (variable-length)
fields in the SBE schema. Use `hdrLen + decoder.encodedLength()` for any message with `<data>` fields —
`encodedLength()` accounts for both the fixed block and all variable-length data. The current cluster→gateway
commands: `NewOrderCommand` is fixed-length (safe to use `blockLen`); `CancelOrderCommand` and
`ReplaceOrderCommand` have a `venueOrderId` varString — MUST use `decoder.encodedLength()`.

A developer who processes only the first message in a fragment will silently drop the
second arb leg — the buy order fires but the sell never reaches the venue, creating
an unhedged position. This is a correctness requirement, not an optimisation.

**FIX back-pressure retry — Session.trySend(Encoder) may return < 0:**
```java
// Populate the generated encoder once, then retry Artio trySend up to 3 times.
for (int attempt = 0; attempt < 3; attempt++) {
    long result = session.trySend(encoder);
    if (result >= 0) { return; }
    LockSupport.parkNanos(1_000L);
}
// All retries failed: increment ARTIO_BACK_PRESSURE_COUNTER; treat as pre-send failure
metricsCounters.increment(ARTIO_BACK_PRESSURE_COUNTER);
// publish RejectEvent to cluster so OrderManager transitions order to REJECTED
```


**Tests to implement:**
```
T-019 OrderCommandHandlerTest — CREATE this file:
  newOrderCommand_producesCorrectFIXNewOrderSingle()
  newOrderCommand_allRequiredTagsPresent()
  cancelCommand_producesCorrectFIXCancelRequest()
  cancelCommand_allRequiredTagsPresent()
  replaceCommand_mgsTypeGVenue_routeReplaceCalledDirectly()
  replaceCommand_coinbase_notRouted_clusterOwnsSequencing()
  orderStatusQueryCommand_sends35H()
  balanceQueryRequest_enqueuedToRestPoller()
  recoveryCompleteEvent_loggedOnly_noFIXAction()
  artioBackPressure_retriesTrySendUpToThreeTimes()
  artioBackPressure_allTrySendRetriesFail_rejectEventPublished()
  newOrder_marketOrder_tag44Omitted()
  noPendingReplacesMap_noStateInGateway()
  arbLegBatch_twoNewOrderCommandsOneFragment_bothFIXSent()
  // Given: one Aeron fragment containing two concatenated NewOrderCommand SBE messages
  //        (leg1: BUY venueA, leg2: SELL venueB — packed by ArbStrategy)
  // When:  onFragment(buffer, offset, totalLength, header)
  // Then:  artioSession.send() called exactly TWICE
  //        leg1: side=BUY, clOrdId=logPos, venue=A; leg2: side=SELL, clOrdId=logPos+1, venue=B
  //        No FIX send dropped; no cursor arithmetic error
```

**Done when:**
- `NewOrderCommand` → FIX `NewOrderSingle` with tags: 35=D, 11, 21=1, 38, 40, 44(limit only), 54, 55, 59, 60, 1
- `CancelOrderCommand` → FIX `OrderCancelRequest` with tags: 35=F, 11, 41, 37, 54, 55, 38, 60
- `ReplaceOrderCommand` → FIX `MsgType=G` for venues that support it; for Coinbase this command is never sent (cluster sends cancel then new as separate commands — see Section 9.7)
- `OrderStatusQueryCommand` → FIX `OrderStatusRequest` (35=H) with tag 11 and tag 37 if venueOrderId present
- `BalanceQueryRequest` → delegated to `RestPoller.enqueue(requestId)`; not sent as FIX
- `RecoveryCompleteEvent` → log INFO only; no FIX action
- `Session.trySend(Encoder)` back-pressure: retries 3× with 1µs sleep; if all fail → ARTIO_BACK_PRESSURE_COUNTER incremented, reject event published to cluster
- No `pendingReplaces` map — no stateful replace sequencing in gateway

---

### TASK-014: RestPoller
**Effort:** 1 day
**Depends On:** TASK-002, TASK-004
**Test class:** T-022 RestPollerTest
**Spec ref:** Section 22.4 (`[rest]` config), Section 16.3 Step 7

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    RestPoller.java
```

**What it does:**
Runs on `rest-poller-thread`. On `BalanceQueryRequest` from egress:
Calls `GET /accounts` on Coinbase REST API. Parses JSON response.
Publishes `BalanceQueryResponse` SBE messages (one per currency) to `GatewayDisruptor`.

**JSON library:** `org.json:json:20240303`
Add to platform-gateway/build.gradle.kts: `implementation("org.json:json:20240303")`

**Response format and parsing:**
```java
// GET /accounts returns JSON array:
// [{"id":"..","currency":"BTC","balance":"0.12","available":"0.12","hold":"0.00"}, ...]
JSONArray accounts = new JSONArray(responseBody);
for (int i = 0; i < accounts.length(); i++) {
    JSONObject acct = accounts.getJSONObject(i);
    String currency = acct.getString("currency");
    String balance  = acct.getString("available");  // use 'available', NOT 'balance'
    int instrumentId = idRegistry.instrumentId(currency + "-USD");
    if (instrumentId == 0) continue;  // not a configured instrument
    long balanceScaled = ConfigManager.parseScaled(balance);
    // ... publish BalanceQueryResponse SBE
}
```

**Coinbase REST authentication headers (separate from FIX credentials):**
```java
// CB-ACCESS-SIGN = Base64(HMAC-SHA256(apiSecret, timestamp+method+path))
private HttpRequest buildAuthenticatedRequest(String path, String method) {
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String prehash   = timestamp + method.toUpperCase() + path;
    String signature = Base64.getEncoder().encodeToString(
        mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
    return HttpRequest.newBuilder()
        .uri(URI.create(config.rest().baseUrl() + path))
        .header("CB-ACCESS-KEY",        credentials.apiKey())
        .header("CB-ACCESS-SIGN",       signature)
        .header("CB-ACCESS-TIMESTAMP",  timestamp)
        .header("CB-ACCESS-PASSPHRASE", credentials.passphrase())
        .GET()
        .timeout(Duration.ofMillis(config.rest().timeoutMs()))
        .build();
}
```


**Tests to implement:**
```
T-022 RestPollerTest — CREATE this file:
  balanceRequest_httpSuccess_responsePublished()
  balanceRequest_parsesAllCurrencies()
  balanceRequest_httpTimeout_errorSentinelPublished()
  balanceRequest_http401_errorSentinelPublished()
  balanceRequest_http500_errorSentinelPublished()
  authHeader_cbAccessSign_computedCorrectly()
  authHeader_cbAccessTimestamp_currentEpochSeconds()
  unknownCurrency_skipped_noResponsePublished()
  available_usedNotBalance_forReconciliation()
  balanceRequest_zeroBalance_publishedAsZero()
  balanceRequest_twoConfiguredCurrencies_twoResponsesPublished()
  authHeader_signatureChanges_ifTimestampDiffers()
```

**Done when:**
- REST call produces correct `BalanceQueryResponse` SBE for each configured currency
- Uses `available` field (not `balance`) from JSON response
- Authentication headers present (CB-ACCESS-KEY, CB-ACCESS-SIGN, CB-ACCESS-TIMESTAMP, CB-ACCESS-PASSPHRASE)
- Request timeout after `config.rest().timeoutMs()`
- On HTTP error: publish `BalanceQueryResponse` with `balanceScaled=-1` (sentinel for error)
- Unknown currency symbol (not in IdRegistry): skipped silently

---

### TASK-015: ArtioLibraryLoop & EgressPollLoop
**Effort:** 1 day
**Depends On:** TASK-001
**Test class:** ET-006 FullSystemSmokeTest (startup verification)
**Spec ref:** Section 25

**What it does:**
Implements two gateway poll loops. `ArtioLibraryLoop` is the tight poll loop calling
`fixLibrary.poll()` on the `artio-library` thread — this is how Artio processes inbound
FIX messages, heartbeats, and session management. `EgressPollLoop` runs on the
`gateway-egress` thread and polls the Aeron cluster egress subscription for commands
published by the cluster (`NewOrderCommand`, `CancelOrderCommand`, etc.) and dispatches
them to `OrderCommandHandler`. Both loops run as `while(running)` spin loops with
`Thread.onSpinWait()` for CPU yielding. Section 25 has the full implementation.

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    ArtioLibraryLoop.java
    EgressPollLoop.java
    ThreadAffinityHelper.java
```

**ThreadAffinityHelper — add net.openhft:affinity dependency:**
Add to platform-gateway/build.gradle.kts: `implementation("net.openhft:affinity:3.23.3")`

```java
import net.openhft.affinity.AffinityLock;

public final class ThreadAffinityHelper {
    public static void pin(Thread thread, int cpu) {
        if (cpu <= 0) return;   // 0 = no pinning (dev mode)
        try {
            AffinityLock.acquireLock(cpu);
            log.info("Thread {} pinned to CPU {}", thread.getName(), cpu);
        } catch (Exception e) {
            log.warn("CPU pinning failed for {} to CPU {}: {}", thread.getName(), cpu, e.getMessage());
            // Non-fatal: continue without pinning
        }
    }
}
```


**Tests to implement:**
```
ET-006 FullSystemSmokeTest — CREATE this file:
  fullSystemSmoke_startsAndTrades_noExceptions()
```

**Done when:**
- `ArtioLibraryLoop` calls `fixLibrary.poll(10)` in tight loop with `BusySpinIdleStrategy`
- `EgressPollLoop` polls `egressSubscription` in tight loop
- Thread names match config (`artio-library`, `gateway-egress`)
- CPU pinning applied if `config.cpu()` values are non-zero
- CPU pinning failure is non-fatal (logs WARN and continues)

---

### TASK-016: GatewayMain — Full Startup
**Effort:** 2 days
**Depends On:** TASK-006 through TASK-015
**Test class:** ET-006, ET-002
**Spec ref:** Section 25.3

**What it does:**
Wires all gateway components together into a runnable process. `GatewayMain.main()`
loads config from TOML, creates all components (`IdRegistry`, `AeronPublisher`,
`MarketDataHandler`, `ExecutionHandler`, `VenueStatusHandler`, `OrderCommandHandler`,
`RestPoller`, `CoinbaseLogonStrategy`), starts the `WarmupHarness`, then initiates the
Artio FIX session to Coinbase. The startup sequence must be exact: warmup completes
before any FIX logon attempt (AC-SY-006). Shutdown hooks ensure clean teardown.

**Files to create:**
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/
    GatewayMain.java
```

**Startup sequence must follow Section 25.3 exactly:**
1. Load config
2. Connect to Aeron Media Driver
3. Connect to Aeron Cluster client
4. Build all components
5. Start Artio FixEngine + FixLibrary
6. Run warmup harness (Section 26)
7. Initiate FIX session (AFTER warmup)
8. Start poll loops
9. Await shutdown signal


**Tests to implement:**
```
ET-006 FullSystemSmokeTest — EXISTS (created by TASK-015); verify all pass, do not recreate:
  fullSystemSmoke_startsAndTrades_noExceptions()
```
```
ET-002 ExecutionFeedbackE2ETest — CREATE this file:
  newOrder_ack_orderStateNew()
  partialFill_orderPartiallyFilled_portfolioUpdated()
  venueReject_orderRejected_noPositionChange()
  cancelRace_fillArrivesBeforeCancelAck_positionUpdated()
```

**Done when:**
- Gateway starts without exceptions
- FIX session established with CoinbaseExchangeSimulator (TASK-006)
- Market data events appear on Aeron subscription after FIX logon
- Graceful shutdown on SIGTERM

---

## EPIC 3: CLUSTER — INFRASTRUCTURE

---

### TASK-017: InternalMarketView
**Effort:** 2 days
**Depends On:** TASK-002, TASK-003
**Test class:** T-004 L2OrderBookTest
**Spec ref:** Sections 3.4.4, 10

**What it does:**
Implements the off-heap L2 order book that the strategies read for best bid/ask prices.
`InternalMarketView` maintains one `L2OrderBook` per (venueId, instrumentId) pair.
Each `L2OrderBook` is backed by a Panama `MemorySegment` (off-heap) — GC cannot touch
it. Books are updated by `onMarketData()` (called by `MessageRouter`). Strategies call
`getBestBid()` / `getBestAsk()` on every market data tick. The staleness check flags
books that have not been updated within `stalenessThresholdMicros` — strategies suppress
quoting when any required book is stale.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    L2OrderBook.java
    InternalMarketView.java
```

**`L2OrderBook`:** Panama off-heap `MemorySegment`. 20 levels per side.
Array layout from Section 10.2. Update algorithm from Section 10.3.

**`InternalMarketView`:** `LongObjectHashMap<L2OrderBook>` keyed by `packKey(venueId, instrumentId)`.

**Arena lifecycle in test context:**
```java
// In L2OrderBookTest — Panama off-heap must be released after each test
protected Arena testArena;

@BeforeEach void createArena() { testArena = Arena.ofConfined(); }
@AfterEach  void closeArena()  { testArena.close(); }

// Pass testArena to L2OrderBook constructor in tests:
L2OrderBook book = new L2OrderBook(venueId, instrumentId, testArena);
```
Failing to close the Arena leaks native memory — the test suite will eventually OOM.


**Tests to implement:**
```
T-004 L2OrderBookTest — CREATE this file:
  insertBid_singleLevel_bestBidCorrect()
  insertAsk_singleLevel_bestAskCorrect()
  insertBid_multiplelevels_sortedDescending()
  insertAsk_multiplelevels_sortedAscending()
  updateExistingLevel_sizeChanged_priceUnchanged()
  deleteBestBid_secondLevelBecomesBest()
  deleteBestAsk_secondLevelBecomesBest()
  deleteMiddleLevel_remainingLevelsCompact()
  insertBidBetweenLevels_insertedAtCorrectPosition()
  getBestBid_emptyBook_returnsInvalidSentinel()
  getBestAsk_emptyBook_returnsInvalidSentinel()
  deleteNonExistentLevel_noException_bookUnchanged()
  insertAtMaxLevels_worsePrice_discarded()
  insertAtMaxLevels_betterPrice_evictsWorstAndInserts()   // M-1: verifies eviction fix
  insertExactlyMaxLevels_allStored()
  insertMaxLevelsPlusOne_bestPricesPreserved()
  updateLevel_sizeToZero_levelRemoved()
  staleness_noUpdateAfterThreshold_isStaleTrue()          // call: isStale(thresholdMicros, currentTimeMicros)
  staleness_updateReceived_isStaleCleared()               // call: apply(decoder, clusterTimeMicros)
  offHeap_noGCPressure_heapUnchanged()
```

**Signature note (C-1 fix):** `apply()` and `isStale()` now take time as parameters — no cluster reference inside `L2OrderBook`:
```java
// In tests pass time explicitly:
book.apply(mdDecoder, mockCluster.time());
assertFalse(book.isStale(STALENESS_MICROS, mockCluster.time()));
```

**Done when:**
- `getBestBid(1, 1)` returns correct top-of-book price after updates
- `getBestAsk(1, 1)` returns correct ask
- Inserting a better bid at level 0 shifts existing levels correctly
- Deleting a level removes it; remaining levels compact
- `isStale()` returns `true` if no update for > `stalenessThresholdMicros`
- Off-heap: adding 1000 books causes zero GC (verified by `System.gc()` + heap check)
- Test `@AfterEach` closes Arena; no native memory leak between test methods

---

### TASK-018: RiskEngine
**Effort:** 2 days
**Depends On:** TASK-002, TASK-003, TASK-004
**Test class:** T-005, ET-005
**Spec ref:** Sections 12.1, 12.2–12.5, 23.1, 23.2, 17.2

**What it does:**
Implements the pre-trade risk engine. `RiskEngineImpl.preTradeCheck()` runs 8 checks
in strict order (CHECK 1 through CHECK 8) — the order is part of the specification.
CHECK 1 (recovery lock) takes priority over CHECK 2 (kill switch) which takes priority
over all others. Each check is a `long` comparison against a pre-loaded limit. No
allocation on the hot path. Soft limits (CHECK 4 position at 80%, CHECK 7 daily loss
at 50%) publish a `RiskEvent` SBE to cluster egress for monitoring but do NOT reject
the order. Hard limits reject. The kill switch rejects all orders regardless of check order.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    RiskEngine.java             (interface — Section 17.2)
    RiskEngineImpl.java
    RiskDecision.java           (record — Section 12.3)
```

**Field declarations: use Section 12.1 (corrected in this document) + Section 23.1 for the position arrays and limit arrays. Section 12.2 (corrected names) for the 8-check logic. Section 23.2 for `pruneOrderWindow()` and `recordOrderInWindow()` implementations.**

**RiskEngineImpl constructor — limit loading from config:**
```java
public RiskEngineImpl(RiskConfig config, IdRegistry idRegistry) {
    for (RiskConfig.InstrumentRisk ir : config.instruments()) {
        int id = idRegistry.instrumentId(ir.symbol() + "-USD");
        if (id == 0) throw new ConfigValidationException("risk.instrument",
                               "Unknown symbol: " + ir.symbol());
        maxLongScaled[id]     = ConfigManager.parseScaled(ir.maxLongPosition());
        maxShortScaled[id]    = ConfigManager.parseScaled(ir.maxShortPosition());
        maxOrderScaled[id]    = ConfigManager.parseScaled(ir.maxOrderSize());
        maxNotionalScaled[id] = ConfigManager.parseScaled(ir.maxNotional());
    }
    this.maxOrdersPerSecond = config.global().maxOrdersPerSecond();
    this.maxDailyLossScaled = ConfigManager.parseScaled(config.global().maxDailyLossUsd());
}
```


**Tests to implement:**
```
T-005 RiskEngineTest — CREATE this file:
  check1_recoveryLock_takesOverKillSwitch()
  check2_killSwitch_takesOverOrderSize()
  check3_orderTooLarge_accepted_belowLimit()
  check3_orderTooLarge_rejected_atLimit()
  check4_projectedLong_accepted_belowLimit()
  check4_projectedLong_rejected_exactlyAtLimit()
  check4_projectedLong_rejected_aboveLimit()
  check4_projectedShort_rejected_belowMinusLimit()
  check5_notional_rejected_aboveMax()
  check5_notional_accepted_belowMax()
  check6_rateLimit_rejected_atLimit()
  check6_rateLimit_accepted_belowLimit()
  check6_rateLimit_slidingWindow_oldOrdersExpire()
  check7_dailyLoss_rejected_exceedsMax()
  check7_dailyLoss_activatesKillSwitch()
  check8_venueNotConnected_rejected()
  check8_venueConnected_approved()
  activateKillSwitch_isActiveTrue()
  deactivateKillSwitch_isActiveFalse()
  killSwitch_allVenuesAffected()
  recoveryLock_onlyAffectedVenue_otherVenueAllowed()
  softLimit_position80Pct_approvedWithAlert()
  softLimit_dailyLoss50Pct_approvedWithAlert()
  dailyReset_clearsAllDailyCounters()
  dailyReset_doesNotClearKillSwitch()
  dailyReset_doesNotClearRecoveryLock()
  snapshot_writeAndLoad_killSwitchPreserved()
  snapshot_writeAndLoad_dailyPnlPreserved()
```
```
ET-005 RiskE2ETest — CREATE this file:
  dailyLossLimit_exceeded_killSwitchActivated()
  orderRateLimit_exceeded_ordersRejectedByRisk()
  killSwitch_deactivatedByAdmin_tradingResumes()
```

**Done when:**
- All 8 checks fire in correct order (CHECK 1 before CHECK 2, etc.)
- `preTradeCheck()` returns in < 5µs (measured in test)
- Kill switch blocks all orders
- Recovery lock blocks orders for that venue only
- Position projection uses net position (not per-venue)
- Daily reset zeroes `dailyRealizedPnlScaled`
- Constructor correctly loads all per-instrument limits from `RiskConfig`
- Unknown instrument symbol in risk config throws `ConfigValidationException`

---

### TASK-019: OrderStatePool
**Effort:** 1 day
**Depends On:** TASK-003
**Test class:** T-006 OrderStatePoolTest
**Spec ref:** Section 7.6

**What it does:**
Implements a pre-allocated object pool for `OrderState` objects. The pool is a
fixed-size array of 2048 pre-created `OrderState` instances (allocated once at startup).
`claim()` pops from the top of the stack; `release()` pushes back. If all 2048 are
claimed, the pool heap-allocates a new instance and increments an overflow counter.
`OrderState.reset()` zeroes every primitive field and nulls `venueOrderId` before
returning an object from the pool. This eliminates GC pressure on order submission.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/
    OrderState.java
    OrderStatePool.java
```

**`OrderState.reset()`** must zero every field. See Section 7.6.
**Pool size:** 2048. **Overflow:** warn + heap allocate.


**Tests to implement:**
```
T-006 OrderStatePoolTest — CREATE this file:
  claim_returnsPreAllocatedObject()
  claim_objectIsReset_allFieldsZero()
  release_returnsObjectToPool()
  releaseAndClaim_sameObjectReturned()
  claimAll2048_noException()
  claim2049th_heapAllocates_counterIncremented()
  release_afterHeapOverflow_poolReceivesObject()
  orderStateReset_allPrimitiveFieldsZero()
  orderStateReset_venueOrderIdNull()
```

**Done when:**
- `pool.claim()` returns pre-allocated object
- `pool.release(order)` returns it to pool
- After `claim()` + `reset()` + inspect: all fields are zero/null/0
- Claiming 2049 objects: 2048 from pool + 1 from heap (metrics counter incremented)

---

### TASK-020: OrderManager
**Effort:** 3 days
**Depends On:** TASK-002, TASK-019
**Test class:** T-007 OrderManagerTest
**Spec ref:** Sections 7, 7.6, 17.4

**What it does:**
Implements the cluster-side order lifecycle manager. `OrderManagerImpl` maintains a
`LongObjectHashMap<OrderState>` of all live orders keyed by `clOrdId`. On each
`ExecutionEvent` (called by `MessageRouter`) it transitions the order through the state
machine from Section 7.3 and returns `true` if the execution was a fill (so the caller
knows to call `portfolioEngine.onFill()`). Duplicate `execId` detection uses an
`ObjectHashSet<String>` with a sliding window of 10,000 entries. On cluster restart via
the PENDING_REPLACE + ExecType=4 transition, publishes a `NewOrderCommand` to egress.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/
    OrderManager.java           (interface — Section 17.4)
    OrderManagerImpl.java
```

**State machine from Section 7.3 — every transition must be implemented.**
**`LongObjectHashMap<OrderState>`** keyed by `clOrdId`. No `HashMap<Long, ...>`.

**execId deduplication — use ObjectHashSet<String> with sliding window:**
```java
// Ring buffer of execIds in insertion order — used to evict oldest entries
// when the window slides past 10,000. Size matches the HashSet capacity.
private final String[]        execIdWindow  = new String[EXEC_ID_WINDOW_SIZE];  // ring buffer
private final ObjectHashSet<String> seenExecIds = new ObjectHashSet<>(EXEC_ID_WINDOW_SIZE * 2);
private int execIdHead = 0;  // next write position in ring buffer

private static final int EXEC_ID_WINDOW_SIZE = 10_000;

private boolean isDuplicateExecId(String execId) {
    if (seenExecIds.contains(execId)) return true;

    // Evict the oldest entry if window is full
    String oldest = execIdWindow[execIdHead];
    if (oldest != null) seenExecIds.remove(oldest);

    // Insert new entry
    execIdWindow[execIdHead] = execId;
    seenExecIds.add(execId);
    execIdHead = (execIdHead + 1) % EXEC_ID_WINDOW_SIZE;
    return false;
}
// Note: execId is a String (allocated from Artio's execIDAscii()). Allocation is
// acceptable here — fills arrive at << 1,000/min even under aggressive arb, so
// the allocation rate is negligible for ZGC/C4. The ring buffer ensures the HashSet
// never exceeds EXEC_ID_WINDOW_SIZE entries.
```
// execId (FIX tag 17) is a String — LongHashSet cannot store String.
// Use Agrona's ObjectHashSet with bounded sliding window:
private final ObjectHashSet<String> execIdsSeen = new ObjectHashSet<>(10_000);
private final ArrayDeque<String>    execIdQueue  = new ArrayDeque<>(10_000);
private static final int            MAX_EXEC_IDS = 10_000;

private boolean isDuplicateExecId(String execId) {
    if (execIdsSeen.contains(execId)) return true;
    execIdsSeen.add(execId);
    execIdQueue.addLast(execId);
    if (execIdQueue.size() > MAX_EXEC_IDS) {
        execIdsSeen.remove(execIdQueue.removeFirst());
    }
    return false;
}
// execId Strings are allocated (FIX tag 17 is variable-length).
// This is acceptable — execIds are stored once per fill, not per market data tick.
```


**Tests to implement:**
```
T-007 OrderManagerTest — CREATE this file:
  pendingNew_execTypeNew_transitionsToNew()
  pendingNew_execTypeRejected_transitionsToRejected()
  pendingNew_execTypeFill_immediate_transitionsToFilled()
  new_partialFill_transitionsToPartiallyFilled()
  new_fullFill_transitionsToFilled()
  partiallyFilled_fullFill_transitionsToFilled()
  partiallyFilled_anotherPartialFill_staysPartiallyFilled()
  new_cancelSent_transitionsToPendingCancel()
  pendingCancel_cancelConfirm_transitionsToCanceled()
  pendingCancel_fillBeforeCancelAck_transitionsToFilled()
  new_expired_transitionsToExpired()
  filled_execReport_loggedAndDiscarded()
  canceled_execReport_loggedAndDiscarded()
  rejected_execReport_loggedAndDiscarded()
  unknownTransition_stateUnchanged_errorLogged()
  duplicateExecId_secondReportDiscarded()
  duplicateExecId_metricsCounterIncremented()
  fill_cumQtyUpdated()
  fill_leavesQtyDecremented()
  fill_avgFillPriceVwapCorrect()
  fill_onTerminalOrder_fillAppliedAnyway()
  createOrder_poolObjectClaimed()
  terminalState_orderReturnedToPool()
  cancelAllOrders_eachLiveOrderGetsCancelCommand()
  cancelAllOrders_terminalOrdersNotCanceled()
  snapshot_allLiveOrdersWritten()
  snapshot_terminalOrdersNotWritten()
  snapshot_loadRestoresAllFields()
  snapshot_loadRestoresVenueOrderId()
```

**Done when:**
- All valid transitions from Section 7.3 produce correct next state
- All invalid transitions log ERROR and do not transition
- Fill on terminal order → WARN and discard
- Duplicate `execId` → discard with metrics increment (uses ObjectHashSet<String>)
- `cancelAllOrders()` publishes `CancelOrderCommand` for each live order
- Snapshot write/load round-trip preserves all `OrderState` fields

---

### TASK-021: PortfolioEngine
**Effort:** 2 days
**Depends On:** TASK-003 (ScaledMath), TASK-002 (SBE), TASK-018 (RiskEngine interface)
**Test class:** T-008 PortfolioEngineTest, T-024 ScaledMathTest
**Spec ref:** Sections 11, 17.3

**What it does:**
Implements position tracking, average entry price, and PnL calculation.
`PortfolioEngineImpl` holds a `LongObjectHashMap<Position>` keyed by a packed
`(venueId, instrumentId)` long. On each fill it calls `updateOnBuy()` or `updateOnSell()`
which update `netQtyScaled`, `avgEntryPriceScaled` (VWAP), and `realizedPnlScaled` using
`ScaledMath`. After updating, it calls `riskEngine.updatePositionSnapshot()` with the
POST-fill `netQtyScaled` so the next pre-trade CHECK 4 sees the correct position. The
position is keyed as `((long) venueId << 32) | instrumentId` — a single primitive, no object.


**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/portfolio/
    Position.java
    PortfolioEngine.java        (interface — Section 17.3)
    PortfolioEngineImpl.java
```

**`PortfolioEngine` interface must include `initPosition(int venueId, int instrumentId)`.**
This method is called from `ClusterMain.buildClusteredService()` at startup to pre-populate
the `LongObjectHashMap` with `Position` entries for all configured pairs. This eliminates
first-fill heap allocation on the cluster-service thread. Implementation:
```java
public void initPosition(int venueId, int instrumentId) {
    long key = posKey(venueId, instrumentId);
    positions.getIfAbsent(key, Position::new);  // allocates once at startup; never again
}
```

**Key formulas — use ScaledMath, not direct multiplication (Section 11.3):**
- VWAP update: `ScaledMath.vwap(oldPrice, oldQty, fillPrice, fillQty)`
- Long close PnL: `ScaledMath.safeMulDiv(Math.abs(fillPrice - avgEntry), soldQty, SCALE) * Long.signum(fillPrice - avgEntry)`
- Short close PnL: `ScaledMath.safeMulDiv(Math.abs(avgEntry - fillPrice), coveredQty, SCALE) * Long.signum(avgEntry - fillPrice)`

**Explicit test vectors (implement these in T-008 PortfolioEngineTest):**
```
Vector 1 — avgPrice on two buys (avgPriceOnMultipleBuys)
  Fill 1: BUY 0.1 BTC @ $65,000  → avgEntry = 6500000000000L; netQty = 10000000L
  Fill 2: BUY 0.1 BTC @ $65,100  → avgEntry = 6505000000000L; netQty = 20000000L

Vector 2 — realizedPnl on long close (realizedPnlOnLongClose)
  Fill 1: BUY  0.1 BTC @ $65,000 → avgEntry = 6500000000000L
  Fill 2: SELL 0.1 BTC @ $65,500 → realizedPnl = 5000000000L ($50 × 1e8); netQty = 0

Vector 3 — short position PnL (shortPositionPnl)
  Fill 1: SELL 0.05 BTC @ $65,000 → avgEntry = 6500000000000L (short)
  Fill 2: BUY  0.05 BTC @ $64,500 → realizedPnl = 2500000000L ($25 × 1e8); netQty = 0

Vector 4 — position flip long→short (positionFlip)
  Fill 1: BUY  0.1 BTC @ $65,000 → netQty = +10000000L; avgEntry = 6500000000000L
  Fill 2: SELL 0.2 BTC @ $65,200 → close 0.1 long + open 0.1 short:
    realizedPnl = 2000000000L ($20 × 1e8)
    netQty = -10000000L; avgEntry = 6520000000000L
```


**Tests to implement:**
```
T-008 PortfolioEngineTest — CREATE this file:
  buy_longPosition_avgPriceCorrect()
  buy_twoFills_vwapAvgPrice()
  sell_longClose_realizedPnlCorrect()
  sell_shortOpen_avgPriceTracked()
  buy_shortCover_realizedPnlCorrect()
  buy_closesShortAndGoesLong_residualQtyHandled()
  sell_closesLongAndGoesShort_residualQtyHandled()
  buy_exactlyClosesShort_netQtyZero()
  sell_exactlyClosesLong_netQtyZero()
  netQtyZero_afterFullClose_avgPriceZero()
  unrealizedPnl_longPosition_markAboveEntry_positive()
  unrealizedPnl_longPosition_markBelowEntry_negative()
  unrealizedPnl_shortPosition_markBelowEntry_positive()
  unrealizedPnl_shortPosition_markAboveEntry_negative()
  unrealizedPnl_zeroPosition_alwaysZero()
  multipleFills_sameSide_vwapAccumulates()
  riskEngine_notifiedAfterEachFill()
  overflowProtection_largePriceAndQty_noSilentWrap()
  snapshot_positionPreserved()
  snapshot_realizedPnlPreserved()
  snapshot_multiplePositions_allRestored()
  initPosition_preAllocatesForAllPairs_noFirstFillAllocation()
  // Given: initPosition(venueId=1, instrumentId=1) called at startup
  // When:  first fill arrives for (venue=1, instrument=1)
  // Then:  no new Position object allocated (getIfAbsent returns existing entry)
  //        Verify via: LongObjectHashMap size unchanged before and after onFill()
```
```
T-024 ScaledMathTest — EXISTS (created by TASK-003); verify all pass, do not recreate:
  safeMulDiv_hiZero_typicalMMLot_exact()
  safeMulDiv_hiZero_zeroQty_returnsZero()
  safeMulDiv_hiZero_unitQty_returnsPrice()
  safeMulDiv_hiNonZero_1BtcAt65K_exact()
  safeMulDiv_hiNonZero_10BtcAt1M_exact()
  safeMulDiv_hiNonZero_largePriceDiff_noPnlWrap()
  vwap_twoEqualLotsAtDifferentPrices_correctAverage()
  vwap_firstFill_oldQtyZero_returnsFillPrice()
  vwap_largePosition_noOverflow()
  vwap_unequalLots_vwapWeightedCorrectly()
  acceptanceCriteria_PF009_noSilentWrapForLargePriceAndQty()
```

**Done when:**
- Test vectors from Section 20.3 all pass (avg price, realized PnL, short PnL)
- All 4 explicit test vectors above produce exact scaled values
- Position flips from long to short correctly (and vice versa)
- `netQtyScaled == 0` after fully closing a position
- `realizedPnl` accumulates correctly across multiple fills
- `unrealizedPnl` updates on `refreshUnrealizedPnl()` call
- Overflow: use `ScaledMath.safeMulDiv()` and `ScaledMath.vwap()` from `platform-common/ScaledMath.java` (see Section 11.5). Do NOT write `a * b / SCALE` directly.
- All T-008 test vectors produce correct results via ScaledMath — verified additionally by code review confirming no raw `* SCALE` multiplication in `updateOnBuy`, `updateOnSell`, or `refreshUnrealizedPnl`
- `cluster` field populated via `setCluster()` before first `onFill()` call
- `initPosition(venueId, instrumentId)` pre-populates all configured pairs at startup; no allocation on first live fill
- All T-024 `ScaledMathTest` cases pass (hi=0 fast path and hi≠0 BigDecimal path)
- `ScaledMath.vwap(0, 0, fillPrice, fillQty) == fillPrice` (first fill edge case)
- Zero heap allocations for fills below ~0.014 BTC at $65K / $920 notional (hi == 0 path; no BigDecimal)
- Correct result for 1 BTC at $65K (hi != 0 path; BigDecimal used)

---

### TASK-022: RecoveryCoordinator
**Effort:** 3 days
**Depends On:** TASK-002, TASK-020, TASK-021
**Test class:** T-011 RecoveryCoordinatorTest, ET-003 RecoveryE2ETest, **IT-005 SnapshotRoundTripIntegrationTest** (V9.1: ownership moved here from TASK-034)
**Spec ref:** Sections 16, 17.5 (interface)

**What it does:**
Implements the venue reconnect and reconciliation manager. `RecoveryCoordinatorImpl`
owns the per-venue `recoveryLock[]` array and the `RecoveryState` enum per venue.
When a venue disconnects, it sets `recoveryLock[venueId] = true` (blocking all new
orders). On reconnect, it drives the 8-step reconciliation sequence from Section 16.3:
query all open orders → reconcile responses (synthesize missing fills, cancel orphans)
→ query balance → compare against internal position → clear lock → publish
`RecoveryCompleteEvent`. Phase timeouts (10s order query, 5s balance) fire the kill switch.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    RecoveryCoordinator.java        (interface — Section 17.5)
    RecoveryCoordinatorImpl.java
```

**State machine per venue — RecoveryState enum:**
```java
public enum RecoveryState {
    IDLE,
    AWAITING_RECONNECT,   // disconnect received; waiting for CONNECTED VenueStatusEvent
    QUERYING_ORDERS,      // OrderStatusQueryCommand sent; awaiting ExecutionEvent responses
    AWAITING_BALANCE,     // BalanceQueryRequest sent; awaiting BalanceQueryResponse
    RECONCILING,          // processing responses; calculating discrepancies
}

private final RecoveryState[] venueState = new RecoveryState[Ids.MAX_VENUES];
// Initialize all to IDLE in constructor
```

**Per-phase timeouts (registered via cluster.scheduleTimer()):**
```
Order query timeout:   10 seconds — correlationId = 2000 + venueId
Balance query timeout:  5 seconds — correlationId = 3000 + venueId
On timeout: log ERROR, activateKillSwitch("recovery_timeout"), do NOT resume trading.
Cancel timer via cluster.cancelTimer() when response arrives.
```


**Tests to implement:**
```
T-011 RecoveryCoordinatorTest — CREATE this file:
  onDisconnect_venueStateAwaitingReconnect()
  onReconnect_orderQuerySent_stateQueryingOrders()
  orderQueryResponse_matches_tradingResumes()
  balanceQueryResponse_withinTolerance_positionAdjusted()
  recoveryComplete_eventPublishedToEgress()
  missingFill_syntheticFillCreated_isSyntheticTrue()
  orphanOrder_cancelCommandPublished()
  balanceMismatch_exceedsTolerance_killSwitchActivated()
  orderQueryTimeout_10s_killSwitchActivated()
  balanceQueryTimeout_5s_killSwitchActivated()
  orderQueryTimeout_timerCorrelId_2000PlusVenueId()
  balanceQueryTimeout_timerCorrelId_3000PlusVenueId()
  multipleVenues_disconnectOne_otherVenueUnaffected()
  doubleDisconnect_sameVenue_stateNotCorrupted()
  reconnect_duringActiveRecovery_handledGracefully()
  zeroOrders_atVenue_emptyReconciliation_resumes()
```

**IT-005 SnapshotRoundTripIntegrationTest** (V9.1: ownership moved here from TASK-034) — CREATE this file:
```
  snapshot_writeAndLoad_orderManagerState()       [AC-OL-008]
  snapshot_writeAndLoad_portfolioState()          [AC-PF-010]
  snapshot_writeAndLoad_riskState()
  snapshot_writeAndLoad_recoveryState()
  fullState_writeAndLoad_allComponentsConsistent()
  snapshot_corruptedBuffer_loadsDefaultState()
  snapshot_emptyPositions_loadProducesZeroNetQty()
  snapshot_roundTrip_killSwitchStatePreserved()
```

IT-005 exercises the snapshot write/load round-trip produced by TASK-020 (OrderManager),
TASK-021 (PortfolioEngine), TASK-018 (RiskEngine) and driven through the reload path
owned by this card. Owned here because TASK-022 is the integration point for all three
snapshot writers plus the reload path. Classes under test: `OrderManagerImpl`,
`PortfolioEngineImpl`, `RiskEngineImpl` (see Part G test→class mapping).

**Done when:**
- `isInRecovery(venueId)` returns true during reconnect sequence
- `venueState[venueId]` transitions correctly through all RecoveryState values
- Missing fill detected and synthesized as `isSynthetic=TRUE` FillEvent
- Orphan order at venue triggers `CancelOrderCommand` egress
- Order query timeout (10s) → kill switch activated
- Balance query timeout (5s) → kill switch activated
- Balance mismatch > tolerance → `activateKillSwitch("reconciliation_failed")`
- Balance within tolerance → position adjusted + `recoveryLock` cleared
- `RecoveryCompleteEvent` published to egress after successful reconciliation
- **IT-005 SnapshotRoundTripIntegrationTest passes all 8 cases** (V9.1)

---

### TASK-023: DailyResetTimer
**Effort:** 0.5 days
**Depends On:** TASK-018
**Test class:** T-013 DailyResetTimerTest
**Spec ref:** Section 12.6

**What it does:**
Implements the midnight daily counter reset. `DailyResetTimer` schedules a cluster
timer via `cluster.scheduleTimer(correlationId=1001, deadlineMs)` at startup targeting
the next midnight UTC. On `onTimerEvent(correlationId=1001)` it calls
`riskEngine.resetDailyCounters()` to zero `dailyVolumeScaled` and `dailyRealizedPnlScaled`,
then takes an Aeron Archive snapshot (`cluster.takeSnapshot()`) for audit, then schedules
the next midnight timer. The timer uses `cluster.time()` — deterministic across all nodes.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    DailyResetTimer.java
```

**EpochClock dependency (Agrona wall-clock milliseconds):**
```java
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;

// Constructor requires EpochClock — injected by ClusterMain.buildClusteredService():
public DailyResetTimer(RiskEngine riskEngine, PortfolioEngine portfolio,
                        EpochClock epochClock) { ... }

// In ClusterMain:
DailyResetTimer timer = new DailyResetTimer(riskEngine, portfolio, new SystemEpochClock());
```


**Tests to implement:**
```
T-013 DailyResetTimerTest — CREATE this file:
  scheduleNextReset_callsClusterScheduleTimer_correlId1001()
  onTimer_correctCorrelationId_dailyCountersReset()
  onTimer_correctCorrelationId_archiveCalledBeforeReset()
  afterReset_reschedulesNextMidnight()
  onTimer_wrongCorrelationId_ignored()
  onTimer_wrongCorrelationId_countersNotReset()
  nextMidnight_calledJustBeforeMidnight_schedulesCorrectly()
  nextMidnight_calledJustAfterMidnight_schedulesNextDay()
  setCluster_null_onTimerDoesNotThrow()
```

**Done when:**
- `scheduleNextReset()` calls `cluster.scheduleTimer(1001L, nextMidnightMs)`
- `onTimer(1001L, ...)` calls `riskEngine.resetDailyCounters()`
- `onTimer(9999L, ...)` is ignored (not our correlationId)
- After reset: `riskEngine.getDailyPnlScaled() == 0`
- `epochClock` is `SystemEpochClock` in production, mockable in tests

---

### TASK-024: AdminCommandHandler
**Effort:** 1 day
**Depends On:** TASK-002, TASK-018
**Test class:** T-012 AdminCommandHandlerTest
**Spec ref:** Section 24.4

**What it does:**
Implements the cluster-side handler for operator admin commands. `AdminCommandHandler`
validates three things before executing: (1) the nonce is within the 24h window (timestamp
check); (2) the nonce has not been seen before — stored in a bounded ring buffer + `LongHashSet`
(prevents replay attacks and unbounded memory growth); (3) the HMAC-SHA256 signature is correct
(computed from the command fields using the pre-loaded admin key); (4) the `operatorId` is in
the configured whitelist. Only after all checks pass does it execute the command. Every accepted
command is audit-logged before execution (Section 24.4). See Section 24.5 for key rotation
procedure, operatorId whitelist config, and nonce bounds table.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    AdminCommandHandler.java
```

**HMAC key loading (from [admin] section of cluster-node.toml):**
```java
// In AdminCommandHandler constructor:
String base64Key = config.admin().hmacKeyBase64Dev();  // dev only
byte[] hmacKey   = Base64.getDecoder().decode(base64Key);
mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
// Production: load from Vault at startup using Vault Java SDK
```

**Nonce eviction — encode timestamp to bound nonce set size:**
```java
// Nonce format: (epochSeconds << 32) | sequenceNumber
// Reject if timestamp portion is older than 24 hours — natural eviction
private boolean isNonceAcceptable(long nonce) {
    long epochSeconds = nonce >>> 32;
    long nowSeconds   = epochClock.time() / 1000;
    if (nowSeconds - epochSeconds > 86400) return false;   // too old
    if (seenNonces.contains(nonce))        return false;   // replay
    return true;
}

// AdminCli nonce generation must encode timestamp in upper 32 bits:
private long nextNonce() {
    long epochSeconds = System.currentTimeMillis() / 1000;
    return (epochSeconds << 32) | (seqCounter++ & 0xFFFFFFFFL);
}
```


**Tests to implement:**
```
T-012 AdminCommandHandlerTest — CREATE this file:
  validHmac_activateKillSwitch_killSwitchSet()
  validHmac_deactivateKillSwitch_killSwitchCleared()
  validHmac_triggerSnapshot_snapshotCalled()
  validHmac_pauseStrategy_strategyPaused()
  validHmac_resumeStrategy_strategyResumed()
  validHmac_resetDailyCounters_countersCleared()
  wrongHmac_commandRejected_stateUnchanged()
  emptyHmac_commandRejected()
  tamperedPayload_hmacInvalid_rejected()
  replayedNonce_commandRejected_warnLogged()
  staleNonce_olderThan24h_rejected()
  nonceTimestampEncoded_upperBits_validWindow()
  activateKillSwitch_alreadyActive_idempotent()
  deactivateKillSwitch_notActive_idempotent()
  nonceRingBuffer_afterWindowFull_oldNonceEvictedAndReaccepted()
  nonceRingBuffer_sizeNeverExceedsCapacity()
  unknownOperatorId_commandRejected_afterHmacValidation()
  knownOperatorId_commandAccepted()
  dualKeyMode_oldKeyStillAccepted_duringRotation()
  dualKeyMode_newKeyAccepted_duringRotation()
  singleKeyMode_onlyCurrentKeyAccepted()
```

**Done when:**
- Replayed nonce → rejected with log WARN
- Stale nonce (> 24h old) → rejected with log WARN
- Invalid HMAC → rejected with log ERROR
- Unknown `operatorId` → rejected with log ERROR (after HMAC check)
- `seenNonces` never exceeds `NONCE_WINDOW_SIZE` (10,000) entries — ring buffer evicts oldest
- `DEACTIVATE_KILL_SWITCH` → `riskEngine.deactivateKillSwitch()` called
- `ACTIVATE_KILL_SWITCH` → `riskEngine.activateKillSwitch(...)` called
- `TRIGGER_SNAPSHOT` → `cluster.takeSnapshot()` called
- HMAC key loaded from `[admin].hmacKeyBase64Dev` in dev; Vault path in production
- Dual-key mode: both old and new key accepted when `newHmacKey` is configured (key rotation)
- `allowedOperatorIds` whitelist loaded from `[admin]` config section; empty list = reject all

---

### TASK-025: MessageRouter
**Effort:** 1 day
**Depends On:** TASK-017, TASK-018, TASK-019, TASK-020, TASK-021, TASK-022, TASK-024
**Test class:** T-018, IT-003, IT-004
**Spec ref:** Section 3.4.3 (full class definition including constructor and all methods)

**What it does:**
Implements the single dispatch point for all inbound cluster messages. `MessageRouter`
has one public method: `dispatch(buffer, offset, blockLen, version, hdrLen, templateId)`.
It reads the `templateId`, wraps the correct pre-allocated SBE decoder, and calls the
appropriate private handler method. The `onMarketData()` private method calls
`marketView.apply(decoder, cluster.time())` **first**, then `strategyEngine.onMarketData(decoder)` —
the L2 book must be current before any strategy reads `getBestBid()`/`getBestAsk()`.
The `onExecution()` private method enforces the strict fan-out order: `OrderManager`
first (determines if fill), then `PortfolioEngine.onFill()` and `RiskEngine.onFill()`
only if fill, then `StrategyEngine` last. `TradingClusteredService.onSessionMessage()`
calls only `router.dispatch()`.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    MessageRouter.java
```

**Implementation:** Copy the complete `MessageRouter` class from Section 3.4.3.
Do not write dispatch logic inline in `TradingClusteredService` — it must delegate
to this class via the `router` field.


**Tests to implement:**
```
T-018 MessageRouterTest — CREATE this file:
  templateId1_updatesL2BookBeforeStrategyDispatch()
  // Given: InternalMarketView mock + StrategyEngine mock with call-order recorder
  // When:  dispatch(buffer, templateId=1)
  // Then:  marketView.apply() observed BEFORE strategyEngine.onMarketData()
  //        (verifies C-2 fix — L2 book must be current when strategy reads it)
  templateId1_dispatchesToStrategyEngine()
  templateId2_withFill_dispatchesToOrderManagerPortfolioRiskStrategy()
  templateId2_noFill_doesNotCallPortfolioOrRisk()
  templateId2_fillDispatchOrder_orderManagerBeforePortfolio()
  templateId3_dispatchesToRecoveryCoordinator()
  templateId4_dispatchesToRecoveryCoordinator()
  templateId33_dispatchesToAdminCommandHandler()
  unknownTemplateId_warnLogged_noException()
  zeroAllocation_perDispatch()
  templateId2_fillOnTerminalOrder_stillCallsStrategy()
  multipleDispatch_decoderReused_noStateLeakBetweenMessages()
```
```
IT-003 RiskGateIntegrationTest — CREATE this file:
  approvedOrder_orderCommandPublishedToEgress()
  rejectedOrder_noOrderCommandPublished()
  killSwitch_strategyGeneratesIntent_riskBlocksAll()
  recoveryLock_venue1_venue2OrdersAllowed()
  softLimit_80pctOfMaxPosition_alertPublished_orderAllowed()
```
```
IT-004 FillCycleIntegrationTest — CREATE this file:
  fill_orderManagerTransitions_portfolioUpdated_riskNotified()
  fill_strategyOnFillCalled_positionReflected()
  multipleFills_sameOrder_cumQtyAccumulates()
  nonFillExecution_portfolioNotUpdated_riskNotNotified()
```

**Done when:**
- `templateId=1` → `marketView.apply(mdDecoder, cluster.time())` called FIRST, then `strategyEngine.onMarketData(mdDecoder)` (AC-FO-005)
- `templateId=2` fan-out **in this exact order**: `orderManager.onExecution()` first; if returns `true` (fill), then `portfolioEngine.onFill()`, then `riskEngine.onFill()`, then `strategyEngine.onExecution()`
- `templateId=3` → `recoveryCoordinator.onVenueStatus(decoder)`
- `templateId=4` → `recoveryCoordinator.onBalanceResponse(decoder)`
- `templateId=33` → `adminCommandHandler.onAdminCommand(decoder)`
- Unknown templateId → log WARN, no exception
- All T-018 MessageRouterTest cases pass including fan-out ordering tests

---

### TASK-026: TradingClusteredService
**Effort:** 2 days
**Depends On:** TASK-025, TASK-023, TASK-024
**Test class:** T-021 TradingClusteredServiceTest
**Spec ref:** Section 3.6

**What it does:**
Implements the `ClusteredService` entrypoint that Aeron Cluster calls for every message.
`TradingClusteredService` wires `MessageRouter`, `StrategyEngine`, `RiskEngine`,
`OrderManager`, `PortfolioEngine`, `RecoveryCoordinator`, `DailyResetTimer`,
and `AdminCommandHandler` together. It implements `onSessionMessage()` (routes to
`MessageRouter`), `onTimerEvent()` (routes to DailyResetTimer, RecoveryCoordinator,
StrategyEngine), `onTakeSnapshot()` (writes all 4 component snapshots in order),
`onRoleChange()` (activates/deactivates strategies based on leader status).

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    TradingClusteredService.java
```

**Implements:** `io.aeron.cluster.service.ClusteredService`

**All lifecycle methods from Section 3.6:**
- `onStart()` — install cluster, load snapshot, schedule timer
- `onSessionMessage()` — call `MessageRouter.dispatch()`
- `onTimerEvent()` — call `DailyResetTimer.onTimer()`, `RecoveryCoordinator.onTimer()`, `StrategyEngine.onTimer()` in that order
- `onTakeSnapshot()` — call all `writeSnapshot()` methods in order
- `onRoleChange()` — set `strategyEngine.setActive(isLeader)`


**Tests to implement:**
```
T-021 TradingClusteredServiceTest — CREATE this file:
  onStart_nullSnapshot_allStateZeroed()
  onStart_withSnapshot_stateRestored()
  onRoleChange_toFollower_strategiesDeactivated()
  onRoleChange_toLeader_strategiesActivated()
  onTimerEvent_dailyResetId_timerForwarded()
  onTimerEvent_arbTimeoutId_timerForwarded()
  onTimerEvent_recoveryOrderQueryTimeoutId_timerForwarded()
  onTimerEvent_recoveryBalanceQueryTimeoutId_timerForwarded()
  installClusterShim_propagatedToAllComponents()
  removeClusterShim_clusterNullInAllComponents()
  resetWarmupState_allComponentsReset()
  onTakeSnapshot_writesAllComponentsInOrder()
  installClusterShim_realClusterAlreadySet_throws()
  onRoleChange_toLeaderTwice_idempotent()
  onTimerEvent_unknownCorrelId_ignoredSilently()
```

**Done when:**
- `onStart()` with null snapshot: all state zeroed
- `onStart()` with snapshot: state matches what was written
- `onRoleChange(FOLLOWER)` → strategies stop producing orders
- `onRoleChange(LEADER)` → strategies resume

---

### TASK-027: ClusterMain — Full Startup
**Effort:** 2 days
**Depends On:** TASK-026
**Test class:** ET-003 RecoveryE2ETest (3-node startup)
**Spec ref:** Section 19.5

**What it does:**
Wires all cluster components into a runnable process. `ClusterMain.main()` loads config,
creates all components (RiskEngine, OrderManager, PortfolioEngine, RecoveryCoordinator,
DailyResetTimer, AdminCommandHandler, MessageRouter, StrategyEngine, MarketMakingStrategy,
ArbStrategy), creates `TradingClusteredService`, and launches the Aeron Cluster node
using `ClusteredMediaDriver` and `AeronCluster`. All three cluster nodes use this same
class with different node-specific TOML config (Section 22.5). This task has no unit
test of its own — correctness is validated by ET-003 RecoveryE2ETest.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/
    ClusterMain.java
config/cluster-node-1.toml
config/cluster-node-2.toml
```

**Startup sequence from Section 19.6.**

**cluster-node-1.toml** (identical to cluster-node-0.toml except):
```toml
[process]
nodeId = 1
ingressEndpoint   = "localhost:9020"
consensusEndpoint = "localhost:20001"
logEndpoint       = "localhost:30001"
archiveEndpoint   = "localhost:8011"
```

**cluster-node-2.toml** (identical to cluster-node-0.toml except):
```toml
[process]
nodeId = 2
ingressEndpoint   = "localhost:9030"
consensusEndpoint = "localhost:20002"
logEndpoint       = "localhost:30002"
archiveEndpoint   = "localhost:8012"
```


**Tests to implement:**
```
T-021 TradingClusteredServiceTest — EXISTS (created by TASK-026); verify all pass, do not recreate:
  onStart_nullSnapshot_allStateZeroed()
  onStart_withSnapshot_stateRestored()
  onRoleChange_toFollower_strategiesDeactivated()
  onRoleChange_toLeader_strategiesActivated()
  onTimerEvent_dailyResetId_timerForwarded()
  onTimerEvent_arbTimeoutId_timerForwarded()
  onTimerEvent_recoveryOrderQueryTimeoutId_timerForwarded()
  onTimerEvent_recoveryBalanceQueryTimeoutId_timerForwarded()
  installClusterShim_propagatedToAllComponents()
  removeClusterShim_clusterNullInAllComponents()
  resetWarmupState_allComponentsReset()
  onTakeSnapshot_writesAllComponentsInOrder()
  installClusterShim_realClusterAlreadySet_throws()
  onRoleChange_toLeaderTwice_idempotent()
  onTimerEvent_unknownCorrelId_ignoredSilently()
```

**Done when:**
- Single node starts with no errors
- 3-node cluster starts: one becomes leader, two become followers
- `cluster-node-1.toml` and `cluster-node-2.toml` present in config/
- `validateStartupConditions()` throws on missing aeronDir before any cluster code runs
- Shutdown on SIGTERM is graceful

---

## EPIC 4: STRATEGIES

---

### TASK-028: StrategyContext
**Effort:** 0.5 days
**Depends On:** TASK-017 through TASK-022
**Test class:** T-021 TradingClusteredServiceTest (init wiring verification)
**Spec ref:** Section 17.8 (StrategyContext record)

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/
    StrategyContext.java            (interface)
    StrategyContextImpl.java        (record — see Section 17.8)
```

**What it builds:** `StrategyContext` is the value object passed to every `Strategy.init()`.
It provides read-only access to `InternalMarketView`, `OrderManager`, `PortfolioEngine`,
`RiskEngine`, `Cluster`, and `IdRegistry` — everything a strategy needs without direct wiring.


**Tests to implement:**
```
T-020 StrategyEngineTest — CREATE this file:
  onMarketData_dispatchedToAllStrategies()
  onFill_dispatchedToAllStrategies()
  onKillSwitch_dispatchedToAllStrategies()
  onKillSwitchCleared_dispatchedToAllStrategies()
  onVenueRecovered_dispatchedToAllStrategies()
  onExecution_noFill_strategyOnFillNotCalled()
  setActive_false_strategiesReceiveKillSwitch()
  setActive_true_strategiesReceiveKillSwitchCleared()
  pauseStrategy_specificStrategy_otherStrategiesUnaffected()
  resumeStrategy_specificStrategy_resumes()
  onMarketData_inactive_noDispatch()
  onExecution_inactiveButFill_onFillNotCalled()
  pauseStrategy_unknownId_noEffect()
  setActive_sameValue_idempotent()
  register_afterSetActive_newStrategyActivatedCorrectly()
  onTimer_inactive_stillRouted_clusterDeterminism()
```
```
IT-002 L2BookToStrategyIntegrationTest — CREATE this file:
  marketDataEvent_updatesBook_strategySeesNewPrices()
  bookStale_strategySuppress_noOrders()
  marketDataThenFill_positionUpdated_skewApplied()
  multipleInstruments_bookUpdates_onlyCorrectStrategyNotified()
```

**Done when:**
- `StrategyContextImpl` is a Java record with all fields from Section 17.8
- `MarketMakingStrategy.init(ctx)` stores context and uses `ctx.marketView()` / `ctx.riskEngine()` / etc.
- `T-021 installClusterShim_propagatedToAllComponents` passes (cluster field wired through context)

---

### TASK-029: Strategy Interface & StrategyEngine
**Effort:** 1.5 days
**Depends On:** TASK-028
**Test class:** T-020, IT-002
**Spec ref:** Section 17.1 (Strategy interface), Section 17.10 (StrategyEngine class)

**What it does:**
Creates the `Strategy` interface (plain, non-sealed since V9.9) and the `StrategyEngine` that manages all
registered strategies. `StrategyEngine` holds a list of `Strategy` instances and fans
out all events (market data, fills, kill switch, venue recovery, timer) to every registered
strategy. When `setActive(false)` is called (cluster becomes follower), it calls
`onKillSwitch()` on all strategies — strategies must stop quoting on followers.
`pauseStrategy(id)` and `resumeStrategy(id)` affect only the matching strategy ID.
Timer events route to all strategies regardless of `active` — timers must fire on
followers for state consistency.

**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/
    Strategy.java                   (plain interface — Section 17.1; V9.9 removed `sealed`+`permits`)
    StrategyEngine.java             (class — Section 17.10)
```

**Strategy.java:** Copy the interface from Section 17.1. Plain (non-sealed) since V9.9 so it compiles standalone without requiring `MarketMakingStrategy` / `ArbStrategy` to exist yet. Includes:
`onMarketData`, `onFill`, `onOrderRejected`, `onKillSwitch`, `onKillSwitchCleared`,
`onVenueRecovered`, `onPositionUpdate`, `subscribedInstrumentIds`, `activeVenueIds`,
`init`, `shutdown`, `strategyId` (default 0), `onTimer` (default no-op).

**StrategyEngine.java:** Copy the class from Section 17.10. Implements:
`register`, `setCluster`, `setActive`, `pauseStrategy`, `resumeStrategy`,
`onMarketData`, `onExecution`, `onTimer`, `onVenueRecovered`, `resetAll`.


**Tests to implement:**
```
// FIX AMB-003 (v8): T-009 MarketMakingStrategyTest and ET-001 MarketMakingE2ETest are
// owned exclusively by TASK-030 (MarketMakingStrategy). They were incorrectly duplicated
// here in v7. TASK-029 owns only T-020 StrategyEngineTest.

T-020 StrategyEngineTest — CREATE this file:
  register_strategyAddedToList()
  setActive_false_callsOnKillSwitchOnAllStrategies()
  setActive_true_strategiesReceiveKillSwitchCleared()
  pauseStrategy_specificStrategy_otherStrategiesUnaffected()
  resumeStrategy_specificStrategy_resumes()
  onMarketData_inactive_noDispatch()
  onExecution_inactiveButFill_onFillNotCalled()
  pauseStrategy_unknownId_noEffect()
  setActive_sameValue_idempotent()
  register_afterSetActive_newStrategyActivatedCorrectly()
  onTimer_inactive_stillRouted_clusterDeterminism()
```
```
IT-002 L2BookToStrategyIntegrationTest — CREATE this file:
  marketDataEvent_updatesBook_strategySeesNewPrices()
  bookStale_strategySuppress_noOrders()
  marketDataThenFill_positionUpdated_skewApplied()
  multipleInstruments_bookUpdates_onlyCorrectStrategyNotified()
```

**Done when:**
- `register()` adds strategy to internal list
- `setActive(false)` → calls `onKillSwitch()` on all strategies
- `setActive(true)` → calls `onKillSwitchCleared()` on all strategies
- `onMarketData()` does nothing when `active == false`
- `onExecution()` calls `onFill()` on strategies only when `isFill == true`
- `onTimer()` routed to all strategies regardless of `active` flag (timers must fire on followers too for state consistency)
- `pauseStrategy(id)` / `resumeStrategy(id)` affect only the matching strategy

---

### TASK-030: MarketMakingStrategy
**Effort:** 4 days
**Depends On:** TASK-029, TASK-017, TASK-018, TASK-020
**Test class:** T-009, ET-001
**Spec ref:** Sections 6.1, 2.1

**What it does:**
Implements the market making strategy. On each `onMarketData()` call, `MarketMakingStrategy`
reads best bid/ask from `InternalMarketView`, computes inventory skew from `PortfolioEngine`
position, calculates adjusted fair value, derives bid/ask prices using tick rounding, checks
if a quote refresh is needed via `requiresRefresh()`, cancels live quotes via
`cancelLiveQuotes()`, then submits new quotes via `submitQuote()`. All arithmetic uses
long scaled integers — no floating point. Section 6.1 has the complete algorithm with code.


**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/
    MarketMakingStrategy.java
```

**Algorithm from Section 6.1.3 — implement step by step:**
1. `isSuppressed()` — 4 conditions from Section 6.1.4
2. Fetch `bestBid`, `bestAsk` from `InternalMarketView`
3. Compute `inventoryRatioBps` (integer arithmetic — no double)
4. Compute `adjustedFairValue` with skew
5. Compute `ourBid`, `ourAsk` with tick rounding
6. Compute `bidSize`, `askSize` with lot rounding
7. Check `requiresRefresh()` — return early if no refresh needed
8. Cancel live quotes via `CancelOrderCommand` egress
9. Submit new quotes via `NewOrderCommand` egress

**All clOrdIds from `cluster.logPosition()`.**


**Tests to implement:**
```
T-009 MarketMakingStrategyTest — CREATE this file:
  fairValue_symmetricSpread_midprice()
  inventorySkew_longPosition_lowerBidAndAsk()
  inventorySkew_shortPosition_higherBidAndAsk()
  inventorySkew_zeroPosition_noAdjustment()
  quoteSize_longInventory_smallerBid_largerAsk()
  quoteSize_shortInventory_smallerAsk_largerBid()
  tickRounding_bidRoundedDown()
  tickRounding_askRoundedUp()
  bidAskNonCrossing_skewedPricesFixed()
  suppression_killSwitch_noQuotesSubmitted()
  suppression_recoveryLock_noQuotesSubmitted()
  suppression_staleMarketData_noQuotesSubmitted()
  suppression_wideSpread_noQuotesSubmitted()
  suppression_cleared_quotesResume()
  suppression_cooldown_noQuotesDuringCooldown()
  suppression_cooldownExpired_quotesResume()
  zeroSize_bidSideAtMaxLong_bidNotSubmitted()
  zeroSize_askSideAtMaxShort_askNotSubmitted()
  refresh_priceUnchanged_noNewQuote()
  refresh_priceMovedBeyondThreshold_newQuote()
  refresh_timeExceededMaxAge_newQuote()
  refresh_cancelSentBeforeNewQuote()
  refresh_liveQuotesIdentical_noNewQuote()
  wrongVenueId_ignored()
  wrongInstrumentId_ignored()
  clOrdId_usesClusterLogPosition()
  clOrdId_eachQuoteUnique()
```

**Done when:**
- Test vectors from Section 20.1 all pass
- Zero allocation per `onMarketData()` call after warmup
- Suppression conditions each independently suppress quoting
- Live quotes are not resubmitted if prices unchanged
- `lastTradePriceScaled` updated from EntryType=TRADE ticks; used as fairValue fallback when spread is wide
- Wide-spread with no last trade price → suppression (not crash)
- 3 consecutive rejections within 1 second → 5-second suppression (AC-ST-005)
- Fill resets rejection counter to 0 (AC-ST-006)

**lastTradePrice handling:**
```java
// In onMarketData():
if (decoder.entryType() == EntryType.TRADE) {
    lastTradePriceScaled = decoder.priceScaled();
    return;  // TRADE ticks do not trigger quote refresh
}
// Fair value selection:
long fairValue;
if (spread * 10000 / midPrice > config.wideSpreadThresholdBps()) {
    if (lastTradePriceScaled > 0) fairValue = lastTradePriceScaled;
    else { suppress(WIDE_SPREAD, FALLBACK_COOLDOWN_MICROS); return; }
} else { fairValue = midPrice; }
```

**Rejection counter — in onOrderRejected():**
```java
@Override
public void onOrderRejected(long clOrdId, byte rejectCode, int venueId) {
    if (venueId != config.venueId()) return;
    consecutiveRejections++;
    if (consecutiveRejections >= 3) {
        suppressedUntilMicros = cluster.time() + 5_000_000L;
        consecutiveRejections = 0;
    }
}
// onFill() resets: consecutiveRejections = 0;
```

---

### TASK-031: ArbStrategy
**Effort:** 4 days
**Depends On:** TASK-029, TASK-017, TASK-018, TASK-020
**Test class:** T-010, ET-004
**Spec ref:** Sections 6.2, 2.2

**What it does:**
Implements the cross-venue arbitrage strategy. `ArbStrategy.onMarketData()` checks all
venue pairs for a profitable spread using the net profit formula from Section 6.2.3 (including
taker fees and slippage). On opportunity detection, it publishes both legs in a single
`egressPublication.offer()` call to minimise leg gap. It tracks the active arb attempt
via `arbActive`, `leg1ClOrdId`, `leg2ClOrdId`. On fill of both legs it computes the imbalance
and hedges if needed. A cluster timer (`legTimeoutClusterMicros`) fires if either leg is
not confirmed within the timeout and triggers cancel + hedge.


**Files to create:**
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/
    ArbStrategy.java
```

**Algorithm from Section 6.2:**
1. `isSuppressed()` — same as MM
2. Scan all venue pairs for opportunity (nested loop, `int` venue IDs)
3. Compute `netProfitBps` with fee and slippage model
4. If profitable: `executeArb()` — encode both legs in single buffer
5. `onFill()` — update `ArbAttempt` state, hedge if imbalanced
6. Leg timeout: if either leg still `PENDING` after `legTimeoutClusterMicros`, cancel + hedge

**`attemptId = cluster.logPosition()` — NEVER UUID.**


**Tests to implement:**
```
T-010 ArbStrategyTest — CREATE this file:
  opportunityDetection_highFees_noTradeExecuted()
  opportunityDetection_sufficientSpread_tradeExecuted()
  netProfitFormula_includesFeesAndSlippage()
  netProfitFormula_belowMinThreshold_noTrade()
  legSubmission_bothLegsInSingleBuffer()
  legSubmission_leg1IsBuyOnCheaperVenue()
  legSubmission_leg2IsSellOnExpensiveVenue()
  legSubmission_ordType_IOC()
  attemptId_isClusterLogPosition_notUUID()
  hedge_leg1FilledLeg2Not_sellHedgeSubmitted()
  hedge_leg2FilledLeg1Not_buyHedgeSubmitted()
  hedge_balanced_noHedge()
  hedge_imbalanceBelowLotSize_ignored()
  hedge_failure_killSwitchActivated()
  fill_partialLeg1_arbActiveUntilBothFinal()
  fill_bothLegsFinal_arbStateReset()
  fill_wrongClOrdId_ignored()
  suppression_activeArb_newOpportunityIgnored()
  suppression_killSwitch_noTradeExecuted()
  legTimeout_pendingLegCanceled_hedgeSubmitted()
```
```
ET-004 ArbStrategyE2ETest — CREATE this file:
  arbOpportunity_detected_bothLegsSubmitted()
  arbLeg1FillLeg2Cancel_hedgeSubmitted()
  arbHedgeFails_killSwitchActivated()
  arbLegTimeout_pendingLegCanceled_imbalanceHedged()
```

**Done when:**
- Opportunity formula matches test vectors from Section 20.1
- Both legs submitted in single `egressPublication.offer()` call
- Hedge submitted when leg imbalance > lotSize
- Kill switch activated if hedge fails
- `arbActive` reset to false after both legs terminal
- `slippageScaled()` implemented with linear fee model
- Leg timeout registered via `cluster.scheduleTimer()` on each arb attempt (correlationId = 4000 + arbAttemptId)
- `onTimer()` cancels pending legs, hedges imbalance, calls `resetArbState()`

**Slippage model:**
```java
private long slippageScaled(int venueId, long sizeScaled) {
    long baseSlipBps = config.baseSlippageBps()[venueId];
    long slopeBps    = config.slippageSlopeBps()[venueId];
    long refSize     = config.referenceSize();
    long slippageBps = baseSlipBps + slopeBps * sizeScaled / refSize;
    long midPrice    = (marketView.getBestBid(venueId, config.instrumentId())
                      + marketView.getBestAsk(venueId, config.instrumentId())) / 2;
    return midPrice * slippageBps / 10000;
}
```

**Leg timeout via cluster.scheduleTimer():**
```java
private static final long ARB_TIMEOUT_BASE = 4000L;

// In executeArb(): register timeout after submitting both legs
long correlId = ARB_TIMEOUT_BASE + (arbAttemptId & 0xFFFFL);
cluster.scheduleTimer(correlId,
    cluster.time() / 1000 + config.legTimeoutClusterMicros() / 1000);
activeArbTimeoutCorrelId = correlId;

// onTimer() — from StrategyEngine routing via default Strategy.onTimer():
@Override
public void onTimer(long correlationId) {
    if (correlationId != activeArbTimeoutCorrelId || !arbActive) return;
    if (leg1Status == LEG_PENDING) submitCancel(leg1ClOrdId, leg1VenueId);
    if (leg2Status == LEG_PENDING) submitCancel(leg2ClOrdId, leg2VenueId);
    long imbalance = leg1FillQtyScaled - leg2FillQtyScaled;
    if (Math.abs(imbalance) > config.lotSizeScaled())
        submitHedge(imbalance > 0 ? Side.SELL : Side.BUY,
                    Math.abs(imbalance),
                    imbalance > 0 ? leg2VenueId : leg1VenueId);
    resetArbState();
}
```

---

## EPIC 5: ADMIN & TOOLING

---

### TASK-032: AdminCli & AdminClient
**Effort:** 2 days
**Depends On:** TASK-002, TASK-024
**Test class:** T-012 AdminCommandHandlerTest (command signing)
**Spec ref:** Section 24

**What it does:**
Implements the operator command-line tool. `AdminCli` is a standalone JAR that reads
a subcommand (`activate-kill-switch`, `deactivate-kill-switch`, `pause-strategy`, etc.),
builds a signed `AdminCommand` SBE message using `AdminClient`, and publishes it to the
cluster's Aeron admin channel. `AdminClient` handles HMAC signing, monotonic nonce
generation, and nonce persistence to disk (so nonces survive process restarts and never
repeat). Credentials loaded from `config/admin.toml`. Section 24 has the full
implementation.

**Files to create:**
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/
    AdminCli.java
    AdminClient.java
    AdminConfig.java
```


**Tests to implement:**
```
T-012 AdminCommandHandlerTest — EXISTS (created by TASK-024); verify all pass, do not recreate:
  validHmac_activateKillSwitch_killSwitchSet()
  validHmac_deactivateKillSwitch_killSwitchCleared()
  validHmac_triggerSnapshot_snapshotCalled()
  validHmac_pauseStrategy_strategyPaused()
  validHmac_resumeStrategy_strategyResumed()
  validHmac_resetDailyCounters_countersCleared()
  wrongHmac_commandRejected_stateUnchanged()
  emptyHmac_commandRejected()
  tamperedPayload_hmacInvalid_rejected()
  replayedNonce_commandRejected_warnLogged()
  staleNonce_olderThan24h_rejected()
  nonceTimestampEncoded_upperBits_validWindow()
  activateKillSwitch_alreadyActive_idempotent()
  deactivateKillSwitch_notActive_idempotent()
```

**Done when:**
- `AdminCli activate-kill-switch` builds and signs a valid `AdminCommand` SBE; cluster activates kill switch
- `AdminCli deactivate-kill-switch` deactivates kill switch (requires correct HMAC)
- Invalid HMAC → command rejected; cluster state unchanged
- Nonce encoded with timestamp in upper 32 bits (Section 24 format)
- `AdminConfig` loaded from `config/admin.toml`
- All T-012 AdminCommandHandlerTest signing cases pass

---

### TASK-033: WarmupHarness
**Effort:** 2 days
**Depends On:** TASK-026
**Test class:** T-021 TradingClusteredServiceTest (shim wiring + C2 verification)
**Spec ref:** Section 26 (implementation), Section 27 (JIT optimization guidelines)

**What it does:**
Implements the JVM warmup harness that runs before any live FIX connectivity. The warmup
calls `TradingClusteredService.onSessionMessage()` in a loop with pre-built SBE test
messages (market data + execution events + admin commands) using a `WarmupClusterShim`
that implements the `Cluster` interface without a real cluster. The goal is to trigger
JIT C2 compilation of all hot paths before trading begins. After warmup, all component
state is reset via `service.resetWarmupState()` and the shim is removed. Section 26
has the full implementation including the shim. Section 27 specifies the JVM flags
(`-XX:-BackgroundCompilation`, ReadyNow, compile command file) that make warmup deterministic.

**Files to create:**
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/
    WarmupHarnessImpl.java      (implements the WarmupHarness interface — see plan TASK-016)
    WarmupClusterShim.java
    WarmupConfig.java           (production: 100K iterations; development: 10K — see Section 27.6)
```

**`config/hotspot_compiler`** (in repository root, not a Java file):
```
inline ig/rueishi/nitroj/exchange/cluster/MessageRouter dispatch
inline ig/rueishi/nitroj/exchange/common/ScaledMath safeMulDiv
inline ig/rueishi/nitroj/exchange/cluster/RiskEngineImpl preTradeCheck
dontinline ig/rueishi/nitroj/exchange/common/ScaledMath vwap
```

**Tests to implement:**
```
T-021 TradingClusteredServiceTest — EXISTS (created by TASK-026); verify all pass, do not recreate:
  onStart_nullSnapshot_allStateZeroed()
  onStart_withSnapshot_stateRestored()
  onRoleChange_toFollower_strategiesDeactivated()
  onRoleChange_toLeader_strategiesActivated()
  onTimerEvent_dailyResetId_timerForwarded()
  onTimerEvent_arbTimeoutId_timerForwarded()
  onTimerEvent_recoveryOrderQueryTimeoutId_timerForwarded()
  onTimerEvent_recoveryBalanceQueryTimeoutId_timerForwarded()
  installClusterShim_propagatedToAllComponents()
  removeClusterShim_clusterNullInAllComponents()
  resetWarmupState_allComponentsReset()
  onTakeSnapshot_writesAllComponentsInOrder()
  installClusterShim_realClusterAlreadySet_throws()
  onRoleChange_toLeaderTwice_idempotent()
  onTimerEvent_unknownCorrelId_ignoredSilently()
  warmup_c2Verified_avgIterationBelowThreshold()  ← @SlowTest; CI pre-release only (Section 27.6)
```

**Done when:**
- `WarmupHarnessImpl.runGatewayWarmup(fixLibrary)` completes `config.iterations()` cycles without exception (config injected via the impl's constructor per §26.2)
- `WarmupClusterShim` implements `Cluster`; `time()` monotonically increasing; `logPosition()` unique longs; `egressPublication()` returns noop
- `service.installClusterShim(shim)` wired to all components; `removeClusterShim()` nulls all cluster references
- `service.resetWarmupState()` called BEFORE `removeClusterShim()`; all component state zeroed
- `WarmupConfig.production()` = 100,000 iterations, `requireC2Verified=true`, threshold=500ns
- `WarmupConfig.development()` = 10,000 iterations, `requireC2Verified=false` — used in all unit/integration tests
- `verifyC2Compilation()` logs WARN and does NOT throw if avg iteration > threshold — calling code decides whether to halt
- `config/hotspot_compiler` file created and referenced in production startup script via `-XX:CompileCommandFile`
- GatewayMain log shows "Warmup complete" before FIX session initiation (AC-SY-006; verified by ET-006)

---

### TASK-034: FIX Replay Tool
**Effort:** 2 days
**Depends On:** TASK-002
**Test class:** T-FIXREPLAY FIXReplayToolTest (unit tests on the replay tool itself)
**Spec ref:** Section 16.9 (Audit log retention); SBE schemas Section 8

**What it does:**
Implements a command-line tool that reads Aeron Archive SBE messages and outputs a
human-readable audit log of every execution event, order command, and cluster timer.
`FIXReplayTool` uses `AeronArchive` to replay recorded messages from a specified log
position range and decodes each SBE buffer using the generated decoders. Output includes
timestamps (cluster time), message type, key fields, and a sequence number. Useful for
post-trade reconciliation and incident investigation. The tool does NOT modify the archive.


**Files to create:**
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/
    FIXReplayTool.java
    SBEReplayFile.java
```


**Tests to implement:**
```
FIXReplayToolTest — CREATE this file:
  sbeReplayFile_seekToPosition_nextReturnsExpectedRecord()
  sbeReplayFile_truncatedTrailingRecord_warnLoggedAndContinues()
  fixReplayTool_allTemplateIds_formattedCorrectly()
  fixReplayTool_unknownTemplateId_unknownLineEmitted()
  fixReplayTool_emptyArchive_noOutputNoException()
  fixReplayTool_rangeStartEnd_filtersRecordsCorrectly()
```

> **V9.1 correction:** Earlier revisions of this spec listed `IT-005 SnapshotRoundTripIntegrationTest`
> as TASK-034's test class. That was incorrect — IT-005 exercises snapshot write/load behaviour
> produced by TASK-020/021/018 and driven through the reload path in TASK-022, not any behaviour
> the FIX replay tool produces. IT-005 is now owned by TASK-022 (see that card's "Tests to
> implement" block). TASK-034 owns only `FIXReplayToolTest`.

**Done when:**
- `FIXReplayTool` reads a binary SBE audit log file and prints human-readable order events
- Handles all SBE message types defined in Section 8 (ExecutionEvent, NewOrderCommand, etc.)
- `SBEReplayFile` supports sequential read and seek-by-timestamp
- Invalid/truncated records: logged with WARN, replay continues (not aborted)
- `FIXReplayToolTest` passes all 6 unit test cases listed above

---

# PART C: COINBASE EXCHANGE SIMULATOR

## C.1 Purpose

The `CoinbaseExchangeSimulator` is a **FIX 4.2 acceptor** (server) that:
- Accepts FIX logon from the gateway (validates HMAC signature)
- Sends synthetic market data (configurable tick stream)
- Receives `NewOrderSingle`, `OrderCancelRequest`, `OrderStatusRequest`
- Responds with `ExecutionReport` (ACK, fill, reject, cancel confirm)
- Supports programmable scenarios: instant fill, partial fill, reject, delayed fill, disconnect

It enables **fully automated end-to-end tests** without Coinbase sandbox connectivity.

## C.2 Architecture

```
Test JVM
  ├── TradingSystem (gateway + cluster in embedded mode)
  │     └── FIX initiator → TCP → CoinbaseExchangeSimulator FIX acceptor
  └── CoinbaseExchangeSimulator
        ├── Artio FixEngine (acceptor mode)
        ├── SimulatorOrderBook
        ├── MarketDataPublisher (sends FIX 35=W on schedule)
        └── ScenarioController (programmatic fill/reject control)
```

## C.3 Full Implementation

```java
package ig.rueishi.nitroj.exchange.simulator;

import uk.co.real_logic.artio.engine.FixEngine;
import uk.co.real_logic.artio.engine.EngineConfiguration;
import uk.co.real_logic.artio.library.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Embeddable Coinbase FIX simulator for automated testing.
 *
 * Usage in tests:
 *   CoinbaseExchangeSimulator sim = CoinbaseExchangeSimulator.builder()
 *       .port(9898)
 *       .instrument("BTC-USD", 65000.00, 65001.00)
 *       .fillMode(FillMode.IMMEDIATE)
 *       .build();
 *   sim.start();
 *   // ... run test ...
 *   sim.stop();
 *   sim.assertOrderReceived("BUY", 65000.00, 0.01);
 */
public final class CoinbaseExchangeSimulator {

    public enum FillMode {
        IMMEDIATE,          // Fill every order instantly at limit price
        PARTIAL_THEN_FULL,  // First fill is 50%, second fill is remaining
        REJECT_ALL,         // Reject every order with OrdRejReason=0
        NO_FILL,            // ACK orders but never fill (for timeout tests)
        DELAYED_FILL,       // Fill after configurable delay
        DISCONNECT_ON_FILL  // Disconnect FIX session when first fill would occur
    }

    private final SimulatorConfig        config;
    private final FillMode               fillMode;
    private final FixEngine              fixEngine;
    private final FixLibrary             fixLibrary;
    private final SimulatorSessionHandler sessionHandler;
    private final MarketDataPublisher    mdPublisher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Test assertion support
    private final CopyOnWriteArrayList<ReceivedOrder>  receivedOrders  = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ReceivedCancel> receivedCancels = new CopyOnWriteArrayList<>();
    private final AtomicInteger                        fillCount       = new AtomicInteger();
    private final AtomicInteger                        rejectCount     = new AtomicInteger();
    private final AtomicInteger                        logonCount      = new AtomicInteger();

    // Pending orders awaiting fill (keyed by clOrdId string)
    private final ConcurrentHashMap<String, SimOrder> pendingOrders = new ConcurrentHashMap<>();

    // Current market (configurable)
    private volatile double currentBid;
    private volatile double currentAsk;

    private CoinbaseExchangeSimulator(Builder builder) {
        this.config   = builder.config;
        this.fillMode = builder.fillMode;
        this.currentBid = builder.initialBid;
        this.currentAsk = builder.initialAsk;

        EngineConfiguration engineConfig = new EngineConfiguration()
            .libraryAeronChannel("aeron:ipc")
            .logFileDir(builder.config.logDir())
            .bindTo("localhost", builder.config.port())
            .acceptorSequenceNumbers(new InMemorySequenceNumbers());

        this.fixEngine    = FixEngine.launch(engineConfig);
        this.fixLibrary   = connectLibrary();
        this.sessionHandler = new SimulatorSessionHandler(this);
        this.mdPublisher  = new MarketDataPublisher(this);
    }

    public void start() {
        mdPublisher.startPublishing(config.marketDataIntervalMs());
    }

    public void stop() {
        mdPublisher.stop();
        scheduler.shutdown();
        fixLibrary.close();
        fixEngine.close();
    }

    // -------------------------------------------------------------------------
    // Scenario control — call from test thread
    // -------------------------------------------------------------------------

    /** Move the market. Next market data tick will reflect new prices. */
    public void setMarket(double bid, double ask) {
        currentBid = bid;
        currentAsk = ask;
    }

    /** Schedule a disconnect-then-reconnect after given delay. */
    public void scheduleDisconnect(long delayMs) {
        scheduler.schedule(() -> {
            sessionHandler.currentSession().requestDisconnect();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Inject a fill for a specific order (used in NO_FILL mode to control timing). */
    public void injectFill(String clOrdId, double fillPrice, double fillQty) {
        SimOrder order = pendingOrders.get(clOrdId);
        if (order != null) {
            sessionHandler.sendFill(order, fillPrice, fillQty);
        }
    }

    // -------------------------------------------------------------------------
    // FIX message processing — called by SimulatorSessionHandler
    // -------------------------------------------------------------------------

    void onNewOrderSingle(Session session, NewOrderSingleDecoder nos) {
        String clOrdId = nos.clOrdIDAsString();
        double limitPx = nos.hasPrice() ? nos.price() : 0.0;
        double orderQty = nos.orderQty();
        char side = nos.side();

        SimOrder order = new SimOrder(clOrdId, nos.orderIDAsString(), side, limitPx, orderQty,
                                       nos.timeInForce(), session);
        receivedOrders.add(new ReceivedOrder(clOrdId, side, limitPx, orderQty,
                                              System.currentTimeMillis()));
        pendingOrders.put(clOrdId, order);

        // Send ACK (ExecType=0 NEW)
        sessionHandler.sendAck(order);

        // Apply fill mode
        switch (fillMode) {
            case IMMEDIATE          -> sessionHandler.sendFill(order, limitPx, orderQty);
            case PARTIAL_THEN_FULL  -> {
                double halfQty = orderQty / 2.0;
                sessionHandler.sendFill(order, limitPx, halfQty);
                scheduler.schedule(() -> sessionHandler.sendFill(order, limitPx, halfQty),
                                    100, TimeUnit.MILLISECONDS);
            }
            case REJECT_ALL         -> sessionHandler.sendReject(order, 0, "Test reject");
            case NO_FILL            -> { /* do nothing — order stays pending */ }
            case DELAYED_FILL       -> scheduler.schedule(
                                            () -> sessionHandler.sendFill(order, limitPx, orderQty),
                                            config.fillDelayMs(), TimeUnit.MILLISECONDS);
            case DISCONNECT_ON_FILL -> scheduleDisconnect(0);
        }
    }

    void onOrderCancelRequest(Session session, OrderCancelRequestDecoder cancel) {
        String origClOrdId = cancel.origClOrdIDAsString();
        receivedCancels.add(new ReceivedCancel(cancel.clOrdIDAsString(), origClOrdId));

        SimOrder order = pendingOrders.remove(origClOrdId);
        if (order != null) {
            sessionHandler.sendCancelConfirm(order, cancel.clOrdIDAsString());
        } else {
            sessionHandler.sendCancelReject(cancel.clOrdIDAsString(), origClOrdId,
                                             "Unknown order");
        }
    }

    void onOrderStatusRequest(Session session, OrderStatusRequestDecoder req) {
        String clOrdId = req.clOrdIDAsString();
        SimOrder order = pendingOrders.get(clOrdId);
        if (order != null) {
            sessionHandler.sendOrderStatus(order);  // ExecType=I
        } else {
            sessionHandler.sendOrderStatusUnknown(clOrdId);
        }
    }

    void onLogon(Session session) {
        logonCount.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Test assertion helpers — call from test thread after test completes
    // -------------------------------------------------------------------------

    public void assertOrderReceived(char side, double price, double qty) {
        boolean found = receivedOrders.stream().anyMatch(o ->
            o.side == side &&
            Math.abs(o.limitPrice - price) < 0.000001 &&
            Math.abs(o.qty - qty) < 0.000001
        );
        if (!found) {
            throw new AssertionError("Expected order not received: side=" + side +
                                      " price=" + price + " qty=" + qty +
                                      "\nActual orders: " + receivedOrders);
        }
    }

    public void assertCancelReceived(String origClOrdId) {
        boolean found = receivedCancels.stream().anyMatch(c -> c.origClOrdId.equals(origClOrdId));
        if (!found) throw new AssertionError("Expected cancel for: " + origClOrdId);
    }

    public void assertOrderCount(int expected) {
        if (receivedOrders.size() != expected)
            throw new AssertionError("Expected " + expected + " orders, got " + receivedOrders.size());
    }

    public void assertNoOrdersReceived() { assertOrderCount(0); }

    public int getFillCount()  { return fillCount.get(); }
    public int getLogonCount() { return logonCount.get(); }

    public void reset() {
        receivedOrders.clear();
        receivedCancels.clear();
        pendingOrders.clear();
        fillCount.set(0);
        rejectCount.set(0);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private SimulatorConfig config  = SimulatorConfig.defaults();
        private FillMode   fillMode     = FillMode.IMMEDIATE;
        private double     initialBid   = 65000.00;
        private double     initialAsk   = 65001.00;

        public Builder port(int port)            { config = config.withPort(port);    return this; }
        public Builder fillMode(FillMode mode)   { this.fillMode = mode;              return this; }
        public Builder initialMarket(double bid, double ask) {
            initialBid = bid; initialAsk = ask; return this;
        }
        public Builder fillDelayMs(long ms)      { config = config.withFillDelay(ms); return this; }

        public CoinbaseExchangeSimulator build() {
            return new CoinbaseExchangeSimulator(this);
        }
    }

    // -------------------------------------------------------------------------
    // Internal data classes
    // -------------------------------------------------------------------------

    public record ReceivedOrder(String clOrdId, char side, double limitPrice, double qty, long receivedMs) {}
    public record ReceivedCancel(String clOrdId, String origClOrdId) {}

    static final class SimOrder {
        final String  clOrdId;
        final String  venueOrderId;
        final char    side;
        final double  limitPrice;
        double        remainingQty;
        double        cumFillQty;
        final char    timeInForce;
        final Session session;
        int           fillSequence;  // for partial fill tracking

        SimOrder(String clOrdId, String venueOrderId, char side, double limitPrice,
                  double qty, char tif, Session session) {
            this.clOrdId      = clOrdId;
            this.venueOrderId = "SIM-" + clOrdId;  // simulator-assigned order ID
            this.side         = side;
            this.limitPrice   = limitPrice;
            this.remainingQty = qty;
            this.cumFillQty   = 0.0;
            this.timeInForce  = tif;
            this.session      = session;
        }
    }
}
```

## C.4 SimulatorSessionHandler

```java
package ig.rueishi.nitroj.exchange.simulator;

public final class SimulatorSessionHandler implements SessionAcquireHandler {

    private final CoinbaseExchangeSimulator simulator;
    private Session currentSession;
    // Counts trySend() failures — exposed for test assertions to diagnose dropped ExecReports
    final AtomicInteger backPressureCount = new AtomicInteger();

    // SBE-encoded FIX message builders (Artio acceptor side uses different encoders)
    private final ExecutionReportEncoder execEncoder = new ExecutionReportEncoder();

    @Override
    public SessionHandler onSessionAcquired(Session session, SessionInfo sessionInfo) {
        this.currentSession = session;
        simulator.onLogon(session);
        return new SimMessageHandler();
    }

    void sendAck(SimOrder order) {
        // FIX ExecutionReport: ExecType=0, OrdStatus=0
        sendExecReport(order, '0', '0', order.remainingQty, 0.0, 0.0, "");
    }

    void sendFill(SimOrder order, double fillPrice, double fillQty) {
        order.cumFillQty   += fillQty;
        order.remainingQty -= fillQty;
        char ordStatus = order.remainingQty <= 0.0000001 ? '2' : '1';  // FILLED or PARTIALLY_FILLED
        sendExecReport(order, 'F', ordStatus, order.remainingQty, fillPrice, fillQty, "");
        simulator.fillCount.incrementAndGet();
        if (ordStatus == '2') simulator.pendingOrders.remove(order.clOrdId);
    }

    void sendReject(SimOrder order, int rejReason, String text) {
        sendExecReport(order, '8', '8', 0.0, 0.0, 0.0, text);
        // Set tag 103 = rejReason
        simulator.rejectCount.incrementAndGet();
        simulator.pendingOrders.remove(order.clOrdId);
    }

    void sendCancelConfirm(SimOrder order, String cancelClOrdId) {
        // ExecType=4, OrdStatus=4
        sendExecReport(order, '4', '4', 0.0, 0.0, 0.0, "");
        simulator.pendingOrders.remove(order.clOrdId);
    }

    void sendOrderStatus(SimOrder order) {
        // ExecType=I (Order Status), OrdStatus based on current state
        char ordStatus = order.cumFillQty > 0 ? '1' : '0';
        sendExecReport(order, 'I', ordStatus, order.remainingQty,
                        0.0, order.cumFillQty, "");
    }

    void sendOrderStatusUnknown(String clOrdId) {
        // OrderCancelReject for unknown order
        sendOrderCancelReject(clOrdId, clOrdId, '1', "UNKNOWN");
    }

    private void sendExecReport(SimOrder order, char execType, char ordStatus,
                                 double leavesQty, double lastPx, double lastQty, String text) {
        // Retry up to 3 times — matches gateway back-pressure pattern (TASK-013).
        // Silent drop causes test flakiness: gateway order stays in PENDING_NEW forever.
        long result = -1;
        for (int attempt = 0; attempt < 3; attempt++) {
            execEncoder
                .orderID(order.venueOrderId)
            .clOrdID(order.clOrdId)
            .execID("EXEC-" + System.nanoTime())
            .execType(execType)
            .ordStatus(ordStatus)
            .side(order.side)
            .leavesQty(String.format("%.8f", leavesQty))
            .cumQty(String.format("%.8f", order.cumFillQty))
            .avgPx(String.format("%.2f", order.cumFillQty > 0 ? order.limitPrice : 0.0));

            if (lastPx > 0)  execEncoder.lastPx(String.format("%.2f", lastPx));
            if (lastQty > 0) execEncoder.lastQty(String.format("%.8f", lastQty));
            if (!text.isEmpty()) execEncoder.text(text);

            result = currentSession.trySend(execEncoder);
            if (result >= 0) return;
            LockSupport.parkNanos(1_000L);  // 1µs
        }
        backPressureCount.incrementAndGet();
        log.warn("sendExecReport back-pressure: dropped execType={} for clOrdId={}",
                  execType, order.clOrdId);
    }

    Session currentSession() { return currentSession; }

    private class SimMessageHandler implements SessionHandler {
        private final NewOrderSingleDecoder    nosDecoder    = new NewOrderSingleDecoder();
        private final OrderCancelRequestDecoder cancelDecoder = new OrderCancelRequestDecoder();
        private final OrderStatusRequestDecoder statusDecoder = new OrderStatusRequestDecoder();

        @Override
        public Action onMessage(DirectBuffer buffer, int offset, int length,
                                 int libraryId, Session session, int sessionType,
                                 long timestampInNs, long position, char msgType) {
            switch (msgType) {
                case 'D' -> simulator.onNewOrderSingle(session,
                                nosDecoder.decode(buffer, offset, length));
                case 'F' -> simulator.onOrderCancelRequest(session,
                                cancelDecoder.decode(buffer, offset, length));
                case 'H' -> simulator.onOrderStatusRequest(session,
                                statusDecoder.decode(buffer, offset, length));
                case 'A' -> { /* logon handled by onSessionAcquired */ }
                default  -> { /* ignore */ }
            }
            return CONTINUE;
        }
    }
}
```

## C.5 MarketDataPublisher

```java
package ig.rueishi.nitroj.exchange.simulator;

public final class MarketDataPublisher {

    private final CoinbaseExchangeSimulator simulator;
    private final ScheduledExecutorService  scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?>              task;

    // FIX market data snapshot encoder
    private final MarketDataSnapshotFullRefreshEncoder mdEncoder =
        new MarketDataSnapshotFullRefreshEncoder();

    public void startPublishing(long intervalMs) {
        task = scheduler.scheduleAtFixedRate(this::publishTick, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (task != null) task.cancel(false);
        scheduler.shutdown();
    }

    private void publishTick() {
        Session session = simulator.sessionHandler.currentSession();
        if (session == null || !session.isActive()) return;

        // FIX 35=W MarketDataSnapshotFullRefresh
        // Tag 55  = "BTC-USD"
        // Tag 268 = 2 (2 entries: bid + ask)
        // Entry 1: Tag 269=0 (Bid), Tag 270=bid price, Tag 271=bid size
        // Entry 2: Tag 269=1 (Ask), Tag 270=ask price, Tag 271=ask size
        mdEncoder
            .symbol("BTC-USD")
            .noMDEntries(2)
            .next().mDEntryType('0').mDEntryPx(String.format("%.2f", simulator.currentBid))
                   .mDEntrySize("0.1")
            .next().mDEntryType('1').mDEntryPx(String.format("%.2f", simulator.currentAsk))
                   .mDEntrySize("0.1");

        session.trySend(mdEncoder);
    }
}
```

## C.6 SimulatorConfig — Test Factory Methods

The following static factory methods must be added to `SimulatorConfig.java`. They are used by `TradingSystemTestHarness` (Part D.4) and all E2E tests.

```java
// Add to SimulatorConfig:

/** Default config for local testing on port 19898. */
public static SimulatorConfig defaults() {
    return SimulatorConfig.builder()
        .port(19898)
        .logDir("./build/test-simulator-fix")
        .marketDataIntervalMs(100)
        .fillDelayMs(50)
        .senderCompId("TEST_TARGET")  // matches gateway targetCompId
        .targetCompId("TEST_SENDER")  // matches gateway senderCompId
        .build();
}

/** Override port on defaults — for parallel test isolation. */
public static SimulatorConfig forPort(int port) {
    return defaults().toBuilder().port(port).build();
}
```

**Note:** `senderCompId` / `targetCompId` must be the mirror image of the gateway's `FixConfig`:
- Simulator `senderCompId` = gateway `targetCompId` = `"TEST_TARGET"`
- Simulator `targetCompId` = gateway `senderCompId` = `"TEST_SENDER"`

---

# PART D: TESTING STRATEGY

## D.1 Test Taxonomy

Every test is tagged with one of:
- `[UNIT]` — tests one class in isolation; no network; no Aeron
- `[INTEGRATION]` — tests two or more classes together; may use embedded Aeron IPC
- `[E2E]` — full system with CoinbaseExchangeSimulator; FIX sessions active

All tests run in CI on `./gradlew test`. E2E tests run on `./gradlew e2eTest`.

## D.2 Unit Test Classes

### §D.2 Conventions (V10.0)

**Scope.** §D.2 specifies the 24 T-NNN unit test classes. Each T-NNN block is the
authoritative specification of its test class: test method names, Given/When/Then bodies,
setup fixture state, and file location.

**What a T-NNN block contains:**
- **Class name** as the `### T-NNN: ClassName` heading.
- **Ownership line** stating which task card CREATES the test class file and any
  additional task cards that ADD methods to the same file (for shared test classes).
- **Setup comment** describing the fixture state assumed by all test methods in the class
  (the implementer authors `@BeforeEach` from this).
- **Each `@Test [UNIT] void methodName()` declaration** with a Given/When/Then comment
  specifying the test intent. The implementer writes the actual assertion code translating
  each G/W/T into AssertJ/JUnit.

**What §D.2 does NOT contain** (the implementer provides these):
- `package` declarations and `import` statements (derived from the class's file path).
- `@BeforeEach` method bodies (derived from the Setup comment).
- The actual Java class wrapper and field declarations.
- Assertion-library-specific syntax (AssertJ `assertThat`, JUnit `assertEquals`, etc.).

**Test run convention.** Unit tests run in `./gradlew test` (fast, no external dependencies,
milliseconds per test). Integration tests run in `./gradlew :platform-X:integrationTest`
(see §D.3). End-to-end tests run in `./gradlew e2eTest` (see §D.4).

**Authoring contract.** §D.2 is the single source of truth for unit test specifications.
Plan task cards and spec Part B task cards reference §D.2 by test class name; they do not
duplicate test method lists. If a plan task card and §D.2 disagree, §D.2 wins.

---

### T-001: IdRegistryImplTest

**Ownership:** CREATED by TASK-005. No other task cards add methods.

```java
// Setup for all tests: IdRegistry loaded with venueId=1 "COINBASE",
//   instrumentId=1 "BTC-USD", instrumentId=2 "ETH-USD", sessionId=coinbaseSessionId

@Test [UNIT] void knownVenueName_returnsId()
// Given: registry initialised with venue name "COINBASE" → venueId=1
// When:  idRegistry.venueId("COINBASE")
// Then:  returns 1

@Test [UNIT] void unknownVenueName_returnsZero()
// Given: registry has no entry for "KRAKEN"
// When:  idRegistry.venueId("KRAKEN")
// Then:  returns 0 (not a programming error; silently ignored)

@Test [UNIT] void knownSymbol_returnsInstrumentId()
// Given: registry has "BTC-USD" → instrumentId=1
// When:  idRegistry.instrumentId("BTC-USD")
// Then:  returns 1

@Test [UNIT] void unknownSymbol_returnsZero_doesNotThrow()
// Given: registry has no entry for "XRP-USD"
// When:  idRegistry.instrumentId("XRP-USD")
// Then:  returns 0; no exception thrown

@Test [UNIT] void symbolOf_knownId_returnsSymbol()
// Given: instrumentId=1 maps to "BTC-USD"
// When:  idRegistry.symbolOf(1)
// Then:  returns "BTC-USD"

@Test [UNIT] void symbolOf_zeroId_returnsNull()
// When:  idRegistry.symbolOf(0)
// Then:  returns null (0 is the sentinel for "unknown")

@Test [UNIT] void venueId_knownSessionId_returnsId()
// Given: Artio Session.id() for Coinbase registered as venueId=1
// When:  idRegistry.venueId(coinbaseSessionId)
// Then:  returns 1

@Test [UNIT] void venueId_unknownSessionId_throwsIllegalState()
// Given: Session.id() was never registered (programming error — live sessions must be registered)
// When:  idRegistry.venueId(unknownSessionId)
// Then:  throws IllegalStateException

@Test [UNIT] void afterInit_noAllocationsOnHotPath()
// Given: registry fully initialised
// When:  call instrumentId() and venueId() 10,000 times using AllocationInstrumentation
// Then:  zero heap allocations (array lookups only)
```


### T-002: ConfigManagerTest

**Ownership:** CREATED by TASK-004. No other task cards add methods.

```java
@Test [UNIT] void parseScaled_wholeNumber_correct()
// When:  ConfigManager.parseScaled("65000")
// Then:  returns 6_500_000_000_000L  (65000 × 10^8)

@Test [UNIT] void parseScaled_decimal_correct()
// When:  ConfigManager.parseScaled("0.01")
// Then:  returns 1_000_000L

@Test [UNIT] void parseScaled_eightDecimalPlaces_exact()
// When:  ConfigManager.parseScaled("0.00000001")
// Then:  returns 1L  (minimum representable unit)

@Test [UNIT] void parseScaled_zeroValue_returnsZero()
// When:  ConfigManager.parseScaled("0")
// Then:  returns 0L

@Test [UNIT] void parseScaled_zeroFraction_correct()
// When:  ConfigManager.parseScaled("1.0")
// Then:  returns 100_000_000L  (trailing-zero fraction normalizes to whole)

@Test [UNIT] void parseScaled_nineDecimalPlaces_truncatesAt8()
// When:  ConfigManager.parseScaled("1.123456789")
// Then:  returns 112_345_678L  (9th decimal truncated, not rounded)

@Test [UNIT] void parseScaled_largeValue_noOverflow()
// When:  ConfigManager.parseScaled("99999999.99999999")
// Then:  returns 9_999_999_999_999_999L; no ArithmeticException

@Test [UNIT] void loadCluster_validToml_allFieldsPopulated()
// Given: valid cluster-node-0.toml with all required fields
// When:  ConfigManager.loadCluster("cluster-node-0.toml")
// Then:  all fields non-null/non-zero; maxOrdersPerSecond=100; softLimitPct=80

@Test [UNIT] void loadCluster_missingOptionalField_usesDefault()
// Given: TOML missing optional field "cooldownAfterFailureMicros"
// When:  ConfigManager.loadCluster(tomlPath)
// Then:  field set to documented default (0 = no cooldown)

@Test [UNIT] void loadCluster_missingRequiredField_throwsConfigValidationException()
// Given: TOML missing required field "maxDailyLossUsd"
// When:  ConfigManager.loadCluster(tomlPath)
// Then:  throws ConfigValidationException with field name in message

@Test [UNIT] void loadGateway_validToml_allFieldsPopulated()
// Given: valid gateway-1.toml with all required fields (venueId, aeronDir, [fix], [credentials], [rest], [cpu], [disruptor], [metrics])
// When:  ConfigManager.loadGateway("gateway-1.toml")
// Then:  GatewayConfig returned; config.venueId()==1; config.fix().senderCompId() non-null;
//        config.cpu() non-null (even if all-zero); config.warmup() == WarmupConfig.production() shape.
// Note:  Mirrors loadCluster_validToml_allFieldsPopulated for the gateway side.

@Test [UNIT] void forTest_noCpuPinning_developmentWarmup()
// Given: no TOML file (factory path, not loader path)
// When:  GatewayConfig cfg = GatewayConfig.forTest()
// Then:  cfg.cpu().equals(CpuConfig.noPinning())  — all CPU affinity fields zero
// And:   cfg.warmup().iterations() == 10_000  — WarmupConfig.development() per §27.6
// And:   cfg.warmup().requireC2Verified() == false
// And:   cfg.aeronDir() contains ProcessHandle.current().pid()  — unique per test process
// Note:  Verifies the §G.4 forTest() factory produces a testable config without fighting
//        for CPU cores or requiring the slow 100K-iteration production warmup.

@Test [UNIT] void minQuoteSizeFractionBps_loadedFromToml()
// Given: cluster-node-0.toml with [strategy.market_making] minQuoteSizeFractionBps = 1000
// When:  ConfigManager.loadCluster(tomlPath)
// Then:  config.strategy().marketMaking().minQuoteSizeFractionBps() == 1000L
// Note:  AMB-005 regression test. Prior to v8, MarketMakingConfig lacked this field and the
//        §6.1.3 algorithm hardcoded 1000. V8 added the record field and TOML key; this test
//        locks the load path against silent regression if the field is accidentally omitted
//        from the record or the TOML parser.
```


### T-003: OrderStatusTest

**Ownership:** CREATED by TASK-003. No other task cards add methods.

```java
@Test [UNIT] void isTerminal_filled_returnsTrue()
// When:  OrderStatus.isTerminal(OrderStatus.FILLED)
// Then:  returns true

@Test [UNIT] void isTerminal_canceled_returnsTrue()
// When:  OrderStatus.isTerminal(OrderStatus.CANCELED)
// Then:  returns true

@Test [UNIT] void isTerminal_replaced_returnsTrue()
// When:  OrderStatus.isTerminal(OrderStatus.REPLACED)
// Then:  returns true

@Test [UNIT] void isTerminal_rejected_returnsTrue()
// When:  OrderStatus.isTerminal(OrderStatus.REJECTED)
// Then:  returns true

@Test [UNIT] void isTerminal_expired_returnsTrue()
// When:  OrderStatus.isTerminal(OrderStatus.EXPIRED)
// Then:  returns true

@Test [UNIT] void isTerminal_new_returnsFalse()
// When:  OrderStatus.isTerminal(OrderStatus.NEW)
// Then:  returns false (order is still live)

@Test [UNIT] void isTerminal_partiallyFilled_returnsFalse()
// When:  OrderStatus.isTerminal(OrderStatus.PARTIALLY_FILLED)
// Then:  returns false

@Test [UNIT] void isTerminal_pendingNew_returnsFalse()
// When:  OrderStatus.isTerminal(OrderStatus.PENDING_NEW)
// Then:  returns false

@Test [UNIT] void isTerminal_pendingCancel_returnsFalse()
// When:  OrderStatus.isTerminal(OrderStatus.PENDING_CANCEL)
// Then:  returns false

@Test [UNIT] void allStatusConstants_uniqueValues()
// When:  collect all OrderStatus byte constants into a Set
// Then:  no duplicates; all values in range [0, 9]
```


### T-004: L2OrderBookTest

**Ownership:** CREATED by TASK-017. No other task cards add methods.

```java
// L2OrderBook: off-heap Panama MemorySegment; MAX_LEVELS=20
// Prices and quantities in scaled longs (SCALE=100_000_000L)
// Bids sorted descending; asks sorted ascending

// Positive cases
@Test [UNIT] void insertBid_singleLevel_bestBidCorrect()
// When:  book.update(BID, price=6_500_000_000_000L, size=100_000_000L)
// Then:  book.getBestBid() == 6_500_000_000_000L

@Test [UNIT] void insertAsk_singleLevel_bestAskCorrect()
// When:  book.update(ASK, price=6_500_100_000_000L, size=50_000_000L)
// Then:  book.getBestAsk() == 6_500_100_000_000L

@Test [UNIT] void insertBid_multiplelevels_sortedDescending()
// When:  insert bids at $65,000; $64,999; $65,001 (out of order)
// Then:  level[0]=6_500_100_000_000L; level[1]=6_500_000_000_000L; level[2]=6_499_900_000_000L
//        (best bid = $65,001 at top)

@Test [UNIT] void insertAsk_multiplelevels_sortedAscending()
// When:  insert asks at $65,001; $65,003; $65,002 (out of order)
// Then:  level[0]=6_500_100_000_000L; level[1]=6_500_200_000_000L; level[2]=6_500_300_000_000L
//        (best ask = $65,001 at top; ascending order)

@Test [UNIT] void updateExistingLevel_sizeChanged_priceUnchanged()
// Given: bid at $65,000 with size=100_000_000L
// When:  book.update(BID, price=$65,000, size=200_000_000L)
// Then:  getBestBid() still == $65,000 (price unchanged); size updated to 200_000_000L

@Test [UNIT] void deleteBestBid_secondLevelBecomesBest()
// Given: bids at $65,001 and $65,000
// When:  book.update(BID, price=$65,001, size=0)  (size=0 means delete)
// Then:  getBestBid() == 6_500_000_000_000L ($65,000 is now best)

@Test [UNIT] void deleteBestAsk_secondLevelBecomesBest()
// Given: asks at $65,001 and $65,002
// When:  book.update(ASK, price=$65,001, size=0)
// Then:  getBestAsk() == 6_500_200_000_000L

@Test [UNIT] void deleteMiddleLevel_remainingLevelsCompact()
// Given: bids at $65,002; $65,001; $65,000
// When:  delete $65,001 (middle level)
// Then:  bidCount==2; levels are $65,002 and $65,000 (no gap)

@Test [UNIT] void insertBidBetweenLevels_insertedAtCorrectPosition()
// Given: bids at $65,002 and $65,000
// When:  insert bid at $65,001
// Then:  levels in order: $65,002; $65,001; $65,000 (inserted in sorted position)

// Negative cases
@Test [UNIT] void getBestBid_emptyBook_returnsInvalidSentinel()
// Given: empty book (no bids inserted)
// When:  book.getBestBid()
// Then:  returns Long.MIN_VALUE (INVALID sentinel — strategies check for this)

@Test [UNIT] void getBestAsk_emptyBook_returnsInvalidSentinel()
// Given: empty book (no asks)
// Then:  book.getBestAsk() == Long.MIN_VALUE

@Test [UNIT] void deleteNonExistentLevel_noException_bookUnchanged()
// Given: bid at $65,000 only
// When:  book.update(BID, price=$64,999, size=0)  (price not in book)
// Then:  no exception; getBestBid() still == $65,000; bidCount unchanged

@Test [UNIT] void insertAtMaxLevels_worsePrice_discarded()
// Given: 20 bid levels filled (MAX_LEVELS=20); worst bid at $64,000
// When:  insert bid at $63,000 (worse than all existing)
// Then:  bidCount still 20; $63,000 not stored; existing levels unchanged

// Edge cases
@Test [UNIT] void insertExactlyMaxLevels_allStored()
// When:  insert 20 bid levels at different prices
// Then:  bidCount == 20; all 20 levels accessible; no exception

@Test [UNIT] void insertMaxLevelsPlusOne_bestPricesPreserved()
// Given: 20 bid levels from $65,020 down to $65,001
// When:  insert 21st bid at $65,100 (best price so far)
// Then:  $65,100 stored; worst level ($65,001) evicted; top 20 best prices preserved

@Test [UNIT] void updateLevel_sizeToZero_levelRemoved()
// Given: bid at $65,000 with size=100_000_000L
// When:  book.update(BID, price=$65,000, size=0)
// Then:  bidCount decremented by 1; $65,000 no longer in book

@Test [UNIT] void staleness_noUpdateAfterThreshold_isStaleTrue()
// Given: book last updated at cluster.time()=T; stalenessThresholdMicros=1_000_000 (1s)
// When:  isStale() called at cluster.time()=T+1_000_001
// Then:  isStale()==true

@Test [UNIT] void staleness_updateReceived_isStaleCleared()
// Given: book is stale (no recent updates)
// When:  book.update(...) called with new data
// Then:  isStale()==false (lastUpdateClusterTime reset)

@Test [UNIT] void offHeap_noGCPressure_heapUnchanged()
// Given: MemorySegment backing store is off-heap
// When:  10,000 book.update() operations performed
// Then:  heap allocation counter unchanged (verified via AllocationInstrumentation);
//        all data written directly to off-heap segment
```


### T-005: RiskEngineTest

**Ownership:** CREATED by TASK-018. No other task cards add methods.

```java
// Constants used throughout:
// maxPositionLong = 1 BTC = 100_000_000L scaled
// maxOrderSize    = 0.5 BTC = 50_000_000L scaled
// maxDailyLoss    = $10,000 = 1_000_000_000_000L scaled
// maxOrdersPerSec = 100
// price           = $65,000 = 6_500_000_000_000L scaled
// VENUE_A=1, BTC_USD=1, STRATEGY_MM=1

// Check ordering (CRITICAL — must fire in correct order)
@Test [UNIT] void check1_recoveryLock_takesOverKillSwitch()
// Given: recoveryLock[VENUE_A]=true AND killSwitch=true AND order size > max
// When:  preTradeCheck(VENUE_A, BTC_USD, BUY, price, qty=200_000_000L, MM)
// Then:  returns REJECT_RECOVERY (CHECK 1 wins; kill switch is CHECK 2)

@Test [UNIT] void check2_killSwitch_takesOverOrderSize()
// Given: recoveryLock=false, killSwitch=true, order size > max
// When:  preTradeCheck(...)
// Then:  returns REJECT_KILL_SWITCH (CHECK 2 wins over CHECK 3)

@Test [UNIT] void check3_orderTooLarge_accepted_belowLimit()
// Given: qty=49_000_000L (0.49 BTC < 0.5 BTC limit)
// When:  preTradeCheck(VENUE_A, BTC_USD, BUY, price, qty=49_000_000L, MM)
// Then:  returns APPROVED

@Test [UNIT] void check3_orderTooLarge_rejected_atLimit()
// Given: qty=50_000_001L (just over 0.5 BTC limit)
// When:  preTradeCheck(...)
// Then:  returns REJECT_ORDER_TOO_LARGE

@Test [UNIT] void check4_projectedLong_accepted_belowLimit()
// Given: current netQty=90_000_000L (0.9 BTC), maxLong=100_000_000L
// When:  BUY qty=5_000_000L → projected=0.95 BTC < 1 BTC limit
// Then:  returns APPROVED

@Test [UNIT] void check4_projectedLong_rejected_exactlyAtLimit()
// Given: netQty=90_000_000L, maxLong=100_000_000L
// When:  BUY qty=10_000_000L → projected=1.0 BTC = exactly limit
// Then:  returns REJECT_MAX_LONG (≥ limit rejects)

@Test [UNIT] void check4_projectedLong_rejected_aboveLimit()
// Given: netQty=90_000_000L, maxLong=100_000_000L
// When:  BUY qty=15_000_000L → projected=1.05 BTC > limit
// Then:  returns REJECT_MAX_LONG

@Test [UNIT] void check4_projectedShort_rejected_belowMinusLimit()
// Given: netQty=-90_000_000L (0.9 BTC short), maxShort=100_000_000L
// When:  SELL qty=15_000_000L → projected=-1.05 BTC < -limit
// Then:  returns REJECT_MAX_SHORT

@Test [UNIT] void check5_notional_rejected_aboveMax()
// Given: maxNotional = $50,000 = 5_000_000_000_000L scaled
// When:  BUY qty=10_000_000L (0.1 BTC) @ $65K → notional=$6,500 (under max)
//        BUY qty=100_000_000L (1 BTC) @ $65K → notional=$65,000 (over max)
// Then:  first APPROVED; second REJECT_NOTIONAL_TOO_LARGE

@Test [UNIT] void check5_notional_accepted_belowMax()
// Given: same maxNotional=$50,000
// When:  BUY qty=10_000_000L @ $65K → notional=$6,500 < $50,000
// Then:  returns APPROVED

@Test [UNIT] void check6_rateLimit_rejected_atLimit()
// Given: maxOrdersPerSecond=100; 100 orders already submitted this second
// When:  preTradeCheck() for order 101
// Then:  returns REJECT_RATE_LIMIT

@Test [UNIT] void check6_rateLimit_accepted_belowLimit()
// Given: 99 orders submitted this second
// When:  preTradeCheck() for order 100
// Then:  returns APPROVED

@Test [UNIT] void check6_rateLimit_slidingWindow_oldOrdersExpire()
// Given: 100 orders submitted in second N; 1 second elapses
// When:  preTradeCheck() in second N+1
// Then:  returns APPROVED (sliding window reset)

@Test [UNIT] void check7_dailyLoss_rejected_exceedsMax()
// Given: dailyLoss already at $10,001 (> $10,000 max)
// When:  preTradeCheck()
// Then:  returns REJECT_DAILY_LOSS

@Test [UNIT] void check7_dailyLoss_activatesKillSwitch()
// Given: daily loss exceeds max
// When:  preTradeCheck() called
// Then:  killSwitch activated automatically AND returns REJECT_KILL_SWITCH on next call

@Test [UNIT] void check8_venueNotConnected_rejected()
// Given: riskEngine.setRecoveryLock(VENUE_A, true) — venue in recovery
// (Different from CHECK 1: this tests the venue-connected flag, not recovery lock)
// When:  preTradeCheck() for VENUE_A
// Then:  returns REJECT_VENUE_NOT_CONNECTED

@Test [UNIT] void check8_venueConnected_approved()
// Given: venue connected, all other limits within range
// When:  preTradeCheck()
// Then:  returns APPROVED

// Kill switch behaviour
@Test [UNIT] void activateKillSwitch_isActiveTrue()
// When:  riskEngine.activateKillSwitch("test")
// Then:  isKillSwitchActive() == true

@Test [UNIT] void deactivateKillSwitch_isActiveFalse()
// Given: kill switch active
// When:  riskEngine.deactivateKillSwitch()
// Then:  isKillSwitchActive() == false

@Test [UNIT] void killSwitch_allVenuesAffected()
// Given: kill switch active
// When:  preTradeCheck for VENUE_A and VENUE_B
// Then:  both return REJECT_KILL_SWITCH

@Test [UNIT] void recoveryLock_onlyAffectedVenue_otherVenueAllowed()
// Given: recoveryLock[VENUE_A]=true; recoveryLock[VENUE_B]=false
// When:  preTradeCheck for VENUE_A → REJECT_RECOVERY
//        preTradeCheck for VENUE_B → APPROVED
// Then:  lock is per-venue, not system-wide

// Soft limits
@Test [UNIT] void softLimit_position80Pct_approvedWithAlert()
// Given: netQty=80_000_000L (exactly 80% of 100_000_000L limit)
// When:  BUY qty=1_000_000L (small order, won't breach hard limit)
// Then:  returns APPROVED; RiskEvent.SOFT_LIMIT_BREACH published to egress

@Test [UNIT] void softLimit_dailyLoss50Pct_approvedWithAlert()
// Given: dailyLoss at $5,000 (exactly 50% of $10,000 max)
// When:  preTradeCheck()
// Then:  returns APPROVED; soft limit alert published

// Reset
@Test [UNIT] void dailyReset_clearsAllDailyCounters()
// Given: dailyLoss=$5,000; ordersThisSecond=50; dailyVolume=100 BTC
// When:  riskEngine.resetDailyCounters()
// Then:  all three counters = 0

@Test [UNIT] void dailyReset_doesNotClearKillSwitch()
// Given: killSwitch=true; dailyLoss at limit
// When:  resetDailyCounters()
// Then:  isKillSwitchActive() still true

@Test [UNIT] void dailyReset_doesNotClearRecoveryLock()
// Given: recoveryLock[VENUE_A]=true
// When:  resetDailyCounters()
// Then:  recoveryLock[VENUE_A] still true

// Snapshot
@Test [UNIT] void snapshot_writeAndLoad_killSwitchPreserved()
// Given: killSwitch=true; write snapshot; create new RiskEngineImpl; load snapshot
// Then:  isKillSwitchActive() == true on new instance

@Test [UNIT] void snapshot_writeAndLoad_dailyPnlPreserved()
// Given: dailyPnl=$3,000; write snapshot; load snapshot
// Then:  getDailyPnlScaled() == 300_000_000_000_000L (3000 × 10^8)
```


### T-006: OrderStatePoolTest

**Ownership:** CREATED by TASK-019. No other task cards add methods.

```java
// POOL_SIZE = 2048; WARN_THRESHOLD = POOL_SIZE - 100 = 1948

@Test [UNIT] void claim_returnsPreAllocatedObject()
// When:  pool.claim()
// Then:  returns non-null OrderState; object is from pre-allocated array (same reference on re-claim after release)

@Test [UNIT] void claim_objectIsReset_allFieldsZero()
// When:  pool.claim()
// Then:  order.clOrdId==0, order.priceScaled==0, order.status==0, order.venueOrderId==null

@Test [UNIT] void release_returnsObjectToPool()
// Given: order = pool.claim(); order.clOrdId = 999L
// When:  pool.release(order)
// Then:  pool.claim() returns same reference; clOrdId==0 (reset on recycle)

@Test [UNIT] void releaseAndClaim_sameObjectReturned()
// Given: a = pool.claim(); pool.release(a)
// When:  b = pool.claim()
// Then:  a == b (same object reference; stack-based LIFO)

@Test [UNIT] void claimAll2048_noException()
// When:  claim 2048 objects in a loop
// Then:  no exception; all 2048 returned (non-null); overflow counter == 0

@Test [UNIT] void claim2049th_heapAllocates_counterIncremented()
// Given: all 2048 pool slots claimed
// When:  pool.claim() for the 2049th object
// Then:  returns new heap-allocated OrderState; overflow counter incremented to 1

@Test [UNIT] void release_afterHeapOverflow_poolReceivesObject()
// Given: pool empty; one heap-allocated order; pool.release(heapOrder)
// When:  pool.claim()
// Then:  returns heapOrder (pool accepts any OrderState, not just its originals)

@Test [UNIT] void orderStateReset_allPrimitiveFieldsZero()
// Given: order with clOrdId=123, priceScaled=65000_00000000L, status=FILLED
// When:  order.reset()
// Then:  all long/int/byte fields == 0

@Test [UNIT] void orderStateReset_venueOrderIdNull()
// Given: order.venueOrderId = "abc123"
// When:  order.reset()
// Then:  order.venueOrderId == null
```


### T-007: OrderManagerTest

**Ownership:** CREATED by TASK-020. No other task cards add methods.

```java
// Helpers: buildExec(clOrdId, execType, qty, price) creates ExecutionEventDecoder
// PENDING_NEW=0, NEW=1, PARTIALLY_FILLED=2, FILLED=3,
// CANCELED=4, REJECTED=8, PENDING_CANCEL=5, EXPIRED=6

// State machine — all valid transitions
@Test [UNIT] void pendingNew_execTypeNew_transitionsToNew()
// Given: createPendingOrder(clOrdId=1000L, ...)
// When:  onExecution(buildExec(1000L, ExecType=0 NEW, qty=0, price=0))
// Then:  getStatus(1000L) == OrderStatus.NEW; returns false (not a fill)

@Test [UNIT] void pendingNew_execTypeRejected_transitionsToRejected()
// When:  onExecution(buildExec(1000L, ExecType=8 REJECTED))
// Then:  getStatus(1000L) == REJECTED; order returned to pool

@Test [UNIT] void pendingNew_execTypeFill_immediate_transitionsToFilled()
// Given: order qty=10_000_000L
// When:  onExecution(buildExec(1000L, FILL, fillQty=10_000_000L))
// Then:  getStatus(1000L) == FILLED; returns true (is a fill); order returned to pool

@Test [UNIT] void new_partialFill_transitionsToPartiallyFilled()
// Given: order in NEW state, qty=10_000_000L
// When:  onExecution(FILL, fillQty=5_000_000L, isFinal=false)
// Then:  getStatus == PARTIALLY_FILLED; cumFillQty=5_000_000L; returns true

@Test [UNIT] void new_fullFill_transitionsToFilled()
// Given: order qty=10_000_000L in NEW state
// When:  onExecution(FILL, fillQty=10_000_000L, isFinal=true)
// Then:  getStatus == FILLED; returns true; pool recycled

@Test [UNIT] void partiallyFilled_fullFill_transitionsToFilled()
// Given: PARTIALLY_FILLED, cumFill=5_000_000L, remaining=5_000_000L
// When:  onExecution(FILL, fillQty=5_000_000L, isFinal=true)
// Then:  getStatus == FILLED; cumFillQty=10_000_000L

@Test [UNIT] void partiallyFilled_anotherPartialFill_staysPartiallyFilled()
// Given: cumFill=3_000_000L
// When:  onExecution(FILL, fillQty=3_000_000L, isFinal=false)
// Then:  getStatus == PARTIALLY_FILLED; cumFillQty=6_000_000L

@Test [UNIT] void new_cancelSent_transitionsToPendingCancel()
// Given: order in NEW state
// When:  orderManager marks PENDING_CANCEL (internal transition — cancel command sent)
// Then:  getStatus == PENDING_CANCEL

@Test [UNIT] void pendingCancel_cancelConfirm_transitionsToCanceled()
// When:  onExecution(ExecType=4 CANCELED)
// Then:  getStatus == CANCELED; returns false; pool recycled

@Test [UNIT] void pendingCancel_fillBeforeCancelAck_transitionsToFilled()
// Given: PENDING_CANCEL state
// When:  onExecution(FILL, qty=full)
// Then:  getStatus == FILLED; returns true (fill wins over cancel)

@Test [UNIT] void new_expired_transitionsToExpired()
// When:  onExecution(ExecType=C EXPIRED)
// Then:  getStatus == EXPIRED; pool recycled

@Test [UNIT] void filled_execReport_loggedAndDiscarded()
// Given: order in FILLED (terminal) state
// When:  onExecution(any ExecType)
// Then:  status unchanged; DUPLICATE_EXECUTION_REPORT_COUNTER incremented

@Test [UNIT] void canceled_execReport_loggedAndDiscarded()
// Given: order in CANCELED state
// When:  onExecution(ExecType=NEW)
// Then:  status unchanged; counter incremented

@Test [UNIT] void rejected_execReport_loggedAndDiscarded()
// Given: REJECTED state
// When:  onExecution(any)
// Then:  unchanged; counter incremented

@Test [UNIT] void unknownTransition_stateUnchanged_errorLogged()
// Given: order in PENDING_NEW state
// When:  onExecution(ExecType=4 CANCELED) — invalid from PENDING_NEW
// Then:  status still PENDING_NEW; INVALID_TRANSITION_COUNTER incremented

@Test [UNIT] void duplicateExecId_secondReportDiscarded()
// Given: execId="EXEC-001" already processed (filled)
// When:  onExecution with same execId="EXEC-001"
// Then:  second report discarded silently; state unchanged

@Test [UNIT] void duplicateExecId_metricsCounterIncremented()
// When:  duplicate execId processed
// Then:  DUPLICATE_EXEC_ID_COUNTER == 1

@Test [UNIT] void execIdSlidingWindow_afterWindowFull_oldEntryNotDuplicate()
// Given: 10,000 unique execIds processed sequentially (fills the ring buffer completely)
// When:  execId that was first-ever processed arrives again
// Then:  NOT detected as duplicate — ring buffer evicted it; seenExecIds no longer contains it
// Verifies M-5 fix: ObjectHashSet never grows beyond EXEC_ID_WINDOW_SIZE (10,000 entries)

@Test [UNIT] void fill_cumQtyUpdated()
// Given: two fills: fillQty=3_000_000L and fillQty=4_000_000L
// Then:  cumFillQtyScaled == 7_000_000L

@Test [UNIT] void fill_leavesQtyDecremented()
// Given: order qty=10_000_000L; fill qty=3_000_000L
// Then:  leavesQtyScaled == 7_000_000L

@Test [UNIT] void fill_avgFillPriceVwapCorrect()
// Given: fill1: 5_000_000L @ 6_500_000_000_000L; fill2: 5_000_000L @ 6_510_000_000_000L
// Then:  avgFillPriceScaled == 6_505_000_000_000L (equal-weight VWAP)

@Test [UNIT] void fill_onTerminalOrder_fillAppliedAnyway()
// Given: order FILLED (terminal)
// When:  another FILL arrives
// Then:  fill IS applied (fills are revenue — never discarded per Section 7.4)

@Test [UNIT] void createOrder_poolObjectClaimed()
// When:  createPendingOrder(...)
// Then:  pool.overflowCount() unchanged; order is from pre-allocated pool

@Test [UNIT] void terminalState_orderReturnedToPool()
// Given: order transitions to FILLED
// Then:  pool size increases by 1 (object recycled)

@Test [UNIT] void cancelAllOrders_eachLiveOrderGetsCancelCommand()
// Given: 3 live orders (PENDING_NEW, NEW, PARTIALLY_FILLED)
// When:  cancelAllOrders()
// Then:  3 CancelOrderCommand SBE messages published to cluster egress

@Test [UNIT] void cancelAllOrders_terminalOrdersNotCanceled()
// Given: 2 live orders + 1 FILLED (terminal)
// When:  cancelAllOrders()
// Then:  only 2 CancelOrderCommand messages published

@Test [UNIT] void snapshot_allLiveOrdersWritten()
// Given: 5 live orders
// When:  writeSnapshot(publication); loadSnapshot(image)
// Then:  getLiveOrderIds() returns same 5 clOrdIds

@Test [UNIT] void snapshot_terminalOrdersNotWritten()
// Given: 3 live + 2 terminal orders
// When:  snapshot round-trip
// Then:  loaded state has 3 live orders; 2 terminal not present

@Test [UNIT] void snapshot_loadRestoresAllFields()
// Given: order with specific clOrdId, venueId, side, price, qty, status
// When:  snapshot round-trip
// Then:  all fields identical after load

@Test [UNIT] void snapshot_loadRestoresVenueOrderId()
// Given: order.venueOrderId = "abc-123-def" (String; set on first ACK)
// When:  snapshot round-trip
// Then:  getVenueOrderId(clOrdId) == "abc-123-def"

@Test [UNIT] void pendingReplace_execTypeCanceled4_emitsNewOrderCommand()
// Given: order in PENDING_REPLACE state (replace command was sent)
// When:  onExecution(ExecType=4 CANCELED) — cancel of old leg confirmed
// Then:  getStatus == PENDING_NEW (new leg submitted); NewOrderCommand published to egress
```


### T-008: PortfolioEngineTest

**Ownership:** CREATED by TASK-021. No other task cards add methods.

```java
// SCALE = 100_000_000L
// BTC price helpers: $65,000 = 6_500_000_000_000L scaled; 0.1 BTC = 10_000_000L scaled

@Test [UNIT] void buy_longPosition_avgPriceCorrect()
// Given: empty position
// When:  onFill(BUY, qty=10_000_000L, price=6_500_000_000_000L)
// Then:  netQtyScaled=10_000_000L; avgEntryPriceScaled=6_500_000_000_000L

@Test [UNIT] void buy_twoFills_vwapAvgPrice()
// Given: Fill 1: BUY 0.1 BTC @ $65,000 → avgEntry=$65,000
// When:  Fill 2: BUY 0.1 BTC @ $65,100
// Then:  avgEntryPriceScaled=6_505_000_000_000L (equal-weight VWAP); netQty=20_000_000L

@Test [UNIT] void sell_longClose_realizedPnlCorrect()
// Given: long 0.1 BTC @ $65,000; position avgEntry=6_500_000_000_000L
// When:  onFill(SELL, qty=10_000_000L, price=6_510_000_000_000L)
// Then:  realizedPnlScaled = (65100-65000) × 0.1 × 10^8 = 1_000_000_000L ($10)

@Test [UNIT] void sell_shortOpen_avgPriceTracked()
// Given: empty position
// When:  onFill(SELL, qty=10_000_000L, price=6_500_000_000_000L)
// Then:  netQtyScaled=-10_000_000L; avgEntryPriceScaled=6_500_000_000_000L (short entry)

@Test [UNIT] void buy_shortCover_realizedPnlCorrect()
// Given: short 0.1 BTC @ $65,000; avgEntry=6_500_000_000_000L
// When:  onFill(BUY, qty=10_000_000L, price=6_490_000_000_000L) — cover at $64,900
// Then:  realizedPnl = (65000-64900) × 0.1 × 10^8 = 1_000_000_000L ($10 profit)

@Test [UNIT] void buy_closesShortAndGoesLong_residualQtyHandled()
// Given: short 0.05 BTC (netQty=-5_000_000L)
// When:  BUY 0.1 BTC — closes 0.05 short + opens 0.05 long
// Then:  netQtyScaled=5_000_000L; realizedPnl for closing portion computed; new long avgEntry set

@Test [UNIT] void sell_closesLongAndGoesShort_residualQtyHandled()
// Given: long 0.05 BTC (netQty=5_000_000L)
// When:  SELL 0.1 BTC — closes long + opens short
// Then:  netQtyScaled=-5_000_000L; realizedPnl for closed portion computed

@Test [UNIT] void buy_exactlyClosesShort_netQtyZero()
// Given: short 0.1 BTC
// When:  BUY 0.1 BTC exactly
// Then:  netQtyScaled==0; avgEntryPriceScaled==0

@Test [UNIT] void sell_exactlyClosesLong_netQtyZero()
// Given: long 0.1 BTC
// When:  SELL exactly 0.1 BTC
// Then:  netQtyScaled==0; avgEntryPriceScaled==0

@Test [UNIT] void netQtyZero_afterFullClose_avgPriceZero()
// When:  position fully closed (netQtyScaled==0)
// Then:  avgEntryPriceScaled==0 (no stale price left over)

@Test [UNIT] void unrealizedPnl_longPosition_markAboveEntry_positive()
// Given: long 0.1 BTC @ $65,000 (avgEntry=6_500_000_000_000L)
// When:  refreshUnrealizedPnl(markPrice=6_510_000_000_000L)
// Then:  unrealizedPnlScaled = 1_000_000_000L ($10 profit)

@Test [UNIT] void unrealizedPnl_longPosition_markBelowEntry_negative()
// Given: long 0.1 BTC @ $65,000
// When:  refreshUnrealizedPnl(markPrice=6_490_000_000_000L)
// Then:  unrealizedPnlScaled = -1_000_000_000L ($10 loss)

@Test [UNIT] void unrealizedPnl_shortPosition_markBelowEntry_positive()
// Given: short 0.1 BTC @ $65,000
// When:  refreshUnrealizedPnl(markPrice=6_490_000_000_000L) — mark below entry = profit for short
// Then:  unrealizedPnlScaled = 1_000_000_000L

@Test [UNIT] void unrealizedPnl_shortPosition_markAboveEntry_negative()
// Given: short 0.1 BTC @ $65,000
// When:  refreshUnrealizedPnl(markPrice=6_510_000_000_000L)
// Then:  unrealizedPnlScaled = -1_000_000_000L

@Test [UNIT] void unrealizedPnl_zeroPosition_alwaysZero()
// Given: netQtyScaled==0
// When:  refreshUnrealizedPnl(any mark price)
// Then:  unrealizedPnlScaled==0

@Test [UNIT] void multipleFills_sameSide_vwapAccumulates()
// Given: 4 BUY fills at different prices
// When:  all applied via onFill()
// Then:  avgEntryPriceScaled == correct VWAP across all 4 fills

@Test [UNIT] void riskEngine_notifiedAfterEachFill()
// When:  onFill() called
// Then:  riskEngine.updatePositionSnapshot() called with POST-fill netQtyScaled

@Test [UNIT] void overflowProtection_largePriceAndQty_noSilentWrap()
// Given: fillQty=100_000_000L (1 BTC), fillPrice=6_500_000_000_000L ($65K)
// When:  onFill() — product = 6.5e20 > Long.MAX_VALUE; triggers BigDecimal path
// Then:  correct result returned; no ArithmeticException; no silent wrap

@Test [UNIT] void snapshot_positionPreserved()
// Given: netQty=20_000_000L; avgEntry=6_505_000_000_000L
// When:  snapshot write + load round-trip
// Then:  new instance has same netQty and avgEntry

@Test [UNIT] void snapshot_realizedPnlPreserved()
// Given: realizedPnl=5_000_000_000_000L ($5,000)
// When:  snapshot round-trip
// Then:  realizedPnl preserved in new instance

@Test [UNIT] void snapshot_multiplePositions_allRestored()
// Given: positions for BTC-USD on VENUE_A and ETH-USD on VENUE_A
// When:  snapshot round-trip
// Then:  both positions restored with correct keys
```


### T-009: MarketMakingStrategyTest

**Ownership:** CREATED by TASK-030. No other task cards add methods.

```java
// Setup: config with targetSpreadBps=10, inventorySkewFactorBps=5,
//   maxPositionLong=100_000_000L, baseQuoteSizeScaled=1_000_000L (0.01 BTC),
//   tickSizeScaled=100_000L ($0.001), refreshThresholdBps=2
// Market: bestBid=6_499_900_000_000L ($64,999), bestAsk=6_500_100_000_000L ($65,001)
// midPrice=6_500_000_000_000L; spread=200_000_000L (0.3 bps — narrow)

@Test [UNIT] void fairValue_symmetricSpread_midprice()
// Given: zero position (no skew)
// When:  onMarketData(bid=$64,999, ask=$65,001)
// Then:  adjustedFairValue == midPrice == 6_500_000_000_000L

@Test [UNIT] void inventorySkew_longPosition_lowerBidAndAsk()
// Given: position=50_000_000L (0.5 BTC long, 50% of max)
//        skewBps = 5 × 0.5 = 2.5 bps
// When:  onMarketData(...)
// Then:  adjustedFairValue < midPrice; both bid and ask prices shift down

@Test [UNIT] void inventorySkew_shortPosition_higherBidAndAsk()
// Given: position=-50_000_000L (short 0.5 BTC)
// When:  onMarketData(...)
// Then:  adjustedFairValue > midPrice; prices shift up

@Test [UNIT] void inventorySkew_zeroPosition_noAdjustment()
// Given: netQtyScaled==0
// When:  onMarketData(...)
// Then:  adjustedFairValue == midPrice exactly

@Test [UNIT] void quoteSize_longInventory_smallerBid_largerAsk()
// Given: inventoryRatioBps=5000 (50% long — already long, need to reduce)
// When:  size computed
// Then:  bidSize < baseQuoteSizeScaled (smaller bid discourages buying more)
//        askSize > baseQuoteSizeScaled (larger ask encourages selling to reduce inventory)

@Test [UNIT] void quoteSize_shortInventory_smallerAsk_largerBid()
// Given: inventoryRatioBps=-5000 (50% short — already short, need to reduce)
// Then:  askSize < base (smaller ask discourages selling more)
//        bidSize > base (larger bid encourages buying to cover short)

@Test [UNIT] void tickRounding_bidRoundedDown()
// Given: computed bid = $64,999.9999
// Then:  actual bid = $64,999.999 (floor to tick — never bid above computed)

@Test [UNIT] void tickRounding_askRoundedUp()
// Given: computed ask = $65,000.0001
// Then:  actual ask = $65,000.001 (ceiling — never ask below computed)

@Test [UNIT] void bidAskNonCrossing_skewedPricesFixed()
// Given: extreme skew causing ourBid >= ourAsk
// Then:  ourAsk = ourBid + tickSizeScaled (spec enforces minimum 1-tick spread)

@Test [UNIT] void suppression_killSwitch_noQuotesSubmitted()
// Given: riskEngine.isKillSwitchActive() == true
// When:  onMarketData(...)
// Then:  zero SBE messages published to egressPublication

@Test [UNIT] void suppression_recoveryLock_noQuotesSubmitted()
// Given: recoveryCoordinator.isInRecovery(venueId) == true
// When:  onMarketData(...)
// Then:  no quotes; no cancels

@Test [UNIT] void suppression_staleMarketData_noQuotesSubmitted()
// Given: marketView.isStale(venueId, instrumentId) == true
// When:  onMarketData(...)
// Then:  no quotes submitted

@Test [UNIT] void suppression_wideSpread_noQuotesSubmitted()
// Given: bid=$64,000, ask=$66,000 → spread=2000 bps >> maxTolerableSpreadBps
// When:  onMarketData(...)
// Then:  suppress() called; no quotes; suppressedUntilMicros set

@Test [UNIT] void suppression_cleared_quotesResume()
// Given: suppression timer expired (cluster.time() > suppressedUntilMicros)
// When:  onMarketData(narrow spread)
// Then:  quotes submitted normally

@Test [UNIT] void suppression_cooldown_noQuotesDuringCooldown()
// Given: 3 consecutive risk rejections within 1 second → 5-second suppress
// When:  onMarketData() called 100ms later
// Then:  no quotes (still in cooldown)

@Test [UNIT] void suppression_cooldownExpired_quotesResume()
// Given: 5-second suppress started; cluster.time() advanced by 6 seconds
// When:  onMarketData()
// Then:  quotes submitted

@Test [UNIT] void zeroSize_bidSideAtMaxLong_bidNotSubmitted()
// Given: position == maxPositionLong (inventoryRatioBps=10000)
//        bidSize formula → floor(0, lotSize) = 0
// When:  onMarketData()
// Then:  SELL quote submitted; BUY quote NOT submitted (size=0)

@Test [UNIT] void zeroSize_askSideAtMaxShort_askNotSubmitted()
// Given: position == maxPositionShort (inventoryRatioBps=-10000)
// Then:  BUY quote submitted; SELL quote NOT submitted

@Test [UNIT] void refresh_priceUnchanged_noNewQuote()
// Given: live quotes at same prices as new computation
// When:  requiresRefresh() returns false (price delta < refreshThresholdBps)
// Then:  no cancel; no new quote submitted

@Test [UNIT] void refresh_priceMovedBeyondThreshold_newQuote()
// Given: lastQuotedMid=$65,000; new midPrice=$65,100 (15 bps > 2 bps threshold)
// When:  onMarketData(...)
// Then:  cancelLiveQuotes() called then submitQuote() for both sides

@Test [UNIT] void refresh_timeExceededMaxAge_newQuote()
// Given: lastQuoteTimeCluster very old (> maxQuoteAgeMicros)
// When:  onMarketData(...)
// Then:  refresh triggered even if price unchanged

@Test [UNIT] void refresh_cancelSentBeforeNewQuote()
// Given: live quotes exist; refresh needed
// When:  onMarketData triggers refresh
// Then:  CancelOrderCommand SBE published BEFORE NewOrderCommand SBE in egress

@Test [UNIT] void refresh_liveQuotesIdentical_noNewQuote()
// Given: new bid==liveBid and new ask==liveAsk (identical prices)
// Then:  no cancel; no new quote (avoid cancel-new churn)

@Test [UNIT] void wrongVenueId_ignored()
// Given: strategy configured for venueId=1
// When:  onMarketData(decoder.venueId()==2)
// Then:  returns immediately; no quotes; no state change

@Test [UNIT] void wrongInstrumentId_ignored()
// Given: strategy configured for instrumentId=1
// When:  onMarketData(decoder.instrumentId()==2)
// Then:  returns immediately

@Test [UNIT] void clOrdId_usesClusterLogPosition()
// When:  submitQuote() called; cluster.logPosition() returns 5000L
// Then:  NewOrderCommand.clOrdId == 5000L

@Test [UNIT] void clOrdId_eachQuoteUnique()
// When:  bid and ask quotes submitted on same onMarketData() call
// Then:  liveBidClOrdId != liveAskClOrdId
```


### T-010: ArbStrategyTest

**Ownership:** CREATED by TASK-031. No other task cards add methods.

```java
// Setup: takerFee=60bps=6_000_000L scaled (0.006), minNetProfitBps=10
// legTimeoutClusterMicros=5_000_000 (5 seconds)
// VENUE_A=1 (buy venue), VENUE_B=2 (sell venue)
// At 60bps fees: break-even requires spread > 120bps

@Test [UNIT] void opportunityDetection_highFees_noTradeExecuted()
// Given: venueA ask=$65,000; venueB bid=$65,002 (3bps spread)
//        fees = 60bps each side → net = 3 - 120 = -117 bps → loss
// When:  onMarketData(both venues)
// Then:  no NewOrderCommand published; arbActive==false

@Test [UNIT] void opportunityDetection_sufficientSpread_tradeExecuted()
// Given: venueA ask=$65,000; venueB bid=$65,850 (130bps spread > 120bps break-even)
//        netProfit ≈ 10 bps > minNetProfitBps=10
// When:  onMarketData(both venues)
// Then:  two NewOrderCommand SBE published in single egressPublication.offer(); arbActive==true

@Test [UNIT] void netProfitFormula_includesFeesAndSlippage()
// Given: specific prices and slippage params
// When:  opportunity evaluated
// Then:  netProfit = grossProfit - buyFee - sellFee - buySlip - sellSlip (Section 6.2.3)

@Test [UNIT] void arbAttemptId_usesClusterLogPosition()
// Given: cluster.logPosition() returns 1000L when executeArb() called
// When:  opportunity detected and executed
// Then:  arbAttemptId=1000L; leg1ClOrdId=1001L; leg2ClOrdId=1002L (base+0, +1, +2)

@Test [UNIT] void bothLegsInSingleEgressOffer()
// When:  executeArb() called
// Then:  egressPublication.offer() called exactly once (both legs encoded into single buffer)

@Test [UNIT] void fill_partialLeg1_arbActiveUntilBothFinal()
// Given: arbActive=true; leg1 filled partially (isFinal=false)
// When:  onFill(leg1ClOrdId, partial)
// Then:  arbActive still true; leg2 still pending

@Test [UNIT] void fill_bothLegsFinal_noImbalance_arbReset()
// Given: leg1FillQty == leg2FillQty (balanced)
// When:  both legs reach LEG_DONE status
// Then:  no hedge submitted; resetArbState() called; arbActive==false

@Test [UNIT] void fill_imbalanced_hedgeSubmitted()
// Given: leg1FillQty=10_000_000L; leg2FillQty=5_000_000L (imbalance=5_000_000L)
// When:  both legs LEG_DONE
// Then:  submitHedge(SELL, 5_000_000L, leg2VenueId) called; hedge IOC MARKET order submitted

@Test [UNIT] void hedgeFailure_killSwitchActivated()
// Given: hedge IOC order rejected by RiskEngine (e.g., recovery lock)
// When:  onFill() triggers hedge; hedge fails
// Then:  riskEngine.activateKillSwitch("hedge_failure") called; arbActive==false

@Test [UNIT] void legTimeout_pendingLegCanceled_hedgeSubmitted()
// Given: leg1 filled; leg2 still PENDING after 5 seconds (legTimeoutClusterMicros)
// When:  onTimerEvent(correlationId=arbTimeoutCorrelId)
// Then:  CancelOrderCommand for leg2 published; hedge submitted for filled portion; resetArbState()

@Test [UNIT] void legTimeout_wrongCorrelId_ignored()
// Given: arbActive=true; activeArbTimeoutCorrelId=4001L
// When:  onTimerEvent(correlationId=9999L)
// Then:  no cancel; no hedge; arbActive unchanged

@Test [UNIT] void noNewArb_whileArbActive()
// Given: arbActive=true (previous arb in flight)
// When:  onMarketData detects another opportunity
// Then:  no new NewOrderCommand published (only one active arb at a time)

@Test [UNIT] void slippageModel_linearFormula()
// Given: baseSlippageBps=5, slopeBps=2, orderSize=10_000_000L, referenceSize=5_000_000L
//        slippage = baseSlippageBps + slopeBps × (orderSize/referenceSize - 1)
// When:  slippageScaled(venueId, orderSize) computed
// Then:  result matches linear formula from Section 6.2.5

@Test [UNIT] void onMarketData_inactive_noArb()
// Given: strategyEngine.setActive(false) → strategy deactivated
// When:  onMarketData() with clear opportunity
// Then:  no trade executed

@Test [UNIT] void timerRegistered_onEachArbExecution()
// When:  executeArb() called
// Then:  cluster.scheduleTimer(correlationId, legTimeoutDeadline) called once

@Test [UNIT] void base_clOrdIds_derived_from_single_logPosition()
// Given: cluster.logPosition() called inside executeArb()
// When:  arbAttemptId, leg1ClOrdId, leg2ClOrdId assigned
// Then:  all three derived from single base read; no separate logPosition() calls
```


### T-011: RecoveryCoordinatorTest

**Ownership:** CREATED by TASK-022. No other task cards add methods.

```java
// VENUE_A=1; ordersInFlight: clOrdId 100,101,102 in NEW state

@Test [UNIT] void onDisconnect_venueStateAwaitingReconnect()
// When:  onVenueStatus(DISCONNECTED, venueId=VENUE_A)
// Then:  isInRecovery(VENUE_A)==true; recoveryLock[VENUE_A]==true

@Test [UNIT] void onReconnect_orderQuerySent_stateQueryingOrders()
// Given: AWAITING_RECONNECT state
// When:  onVenueStatus(CONNECTED, venueId=VENUE_A)
// Then:  OrderStatusQueryCommand SBE published for each live order (3 messages);
//        venueState == QUERYING_ORDERS; timer scheduled (correlationId=2001)

@Test [UNIT] void orderQueryResponse_matches_tradingResumes()
// Given: QUERYING_ORDERS; 3 outstanding queries
// When:  3 ExecutionEvents arrive (ExecType=I, all status=OPEN matching internal)
// Then:  all 3 reconciled; BalanceQueryRequest published; state=AWAITING_BALANCE

@Test [UNIT] void orderQueryResponse_missingFill_syntheticFillApplied()
// Given: internal order cumFill=0; venue reports cumFill=10_000_000L (missed fill)
// When:  ExecutionEvent (ExecType=I) with cumQty=10_000_000L arrives
// Then:  synthetic FillEvent published (isSynthetic=true); portfolio updated

@Test [UNIT] void orderQueryResponse_orphanAtVenue_cancelSent()
// Given: venue reports orderId unknown to internal OrderManager
// When:  ExecutionEvent (ExecType=I) for unrecognised clOrdId
// Then:  CancelOrderCommand published for orphan order

@Test [UNIT] void orderQueryResponse_venueCanceled_orderForceCanceled()
// Given: internal order status=NEW; venue status=CANCELED
// When:  ExecType=I with ordStatus=CANCELED
// Then:  forceTransition(clOrdId, CANCELED) called; no synthetic fill

@Test [UNIT] void balanceReconciliation_withinTolerance_tradingResumes()
// Given: venue balance=1.0005 BTC; internal=1.0000 BTC (within tolerance)
// When:  BalanceQueryResponse received
// Then:  adjustPosition() called with delta; recoveryLock cleared;
//        RecoveryCompleteEvent published; isInRecovery==false

@Test [UNIT] void balanceReconciliation_exceedsTolerance_killSwitchActivated()
// Given: venue balance=1.5 BTC; internal=1.0 BTC (50% discrepancy > tolerance)
// When:  BalanceQueryResponse
// Then:  activateKillSwitch("reconciliation_failed"); lock NOT cleared; no RecoveryCompleteEvent

@Test [UNIT] void orderQueryTimeout_10s_killSwitchActivated()
// Given: QUERYING_ORDERS; timer registered with correlationId=2001
// When:  onTimerEvent(correlationId=2001) fires (10s elapsed)
// Then:  partial results already applied; activateKillSwitch("recovery_timeout");
//        lock NOT cleared; no RecoveryCompleteEvent

@Test [UNIT] void orderQueryTimeout_timerCorrelId_2000PlusVenueId()
// Given: venueId=VENUE_A=1
// When:  order query phase starts
// Then:  cluster.scheduleTimer called with correlationId=2001 (2000+1)

@Test [UNIT] void balanceQueryTimeout_5s_killSwitchActivated()
// Given: AWAITING_BALANCE; timer correlationId=3001
// When:  onTimerEvent(correlationId=3001)
// Then:  activateKillSwitch("recovery_timeout")

@Test [UNIT] void recoveryCompleteEvent_publishedOnSuccess()
// Given: all orders reconciled; balance within tolerance
// When:  reconciliation completes
// Then:  RecoveryCompleteEvent SBE published to egress

@Test [UNIT] void snapshot_recoveryStatePreserved()
// Given: venueState[VENUE_A]=QUERYING_ORDERS; 2 pending queries
// When:  snapshot write + load round-trip
// Then:  state restored; pending query count preserved

@Test [UNIT] void multipleVenues_independentRecovery()
// Given: VENUE_A disconnected; VENUE_B still connected
// When:  recovery runs for VENUE_A
// Then:  isInRecovery(VENUE_B)==false; orders for VENUE_B unaffected

@Test [UNIT] void disconnectDuringRecovery_resetAndRestart()
// Given: already in QUERYING_ORDERS for VENUE_A
// When:  another DISCONNECTED event arrives for VENUE_A
// Then:  state reset to AWAITING_RECONNECT; previous queries abandoned

@Test [UNIT] void idleVenue_connectedEvent_noQuerySent()
// Given: venueState[VENUE_A]==IDLE (never disconnected)
// When:  onVenueStatus(CONNECTED)
// Then:  no OrderStatusQueryCommand; no timer; no state change
```


---

### T-012: AdminCommandHandlerTest

**Ownership:** CREATED by TASK-024. ADDS methods from: TASK-032.

```java
// HMAC key: 32-byte test key loaded in @BeforeEach
// operatorId=42; nonce starts at 1000L

@Test [UNIT] void validHmac_activateKillSwitch_killSwitchSet()
// Given: correct HMAC for ACTIVATE_KILL_SWITCH, operatorId=42, nonce=1000
// When:  onAdminCommand(decoder)
// Then:  riskEngine.isKillSwitchActive()==true

@Test [UNIT] void validHmac_deactivateKillSwitch_killSwitchCleared()
// Given: kill switch active; correct HMAC for DEACTIVATE_KILL_SWITCH
// When:  onAdminCommand(decoder)
// Then:  riskEngine.isKillSwitchActive()==false

@Test [UNIT] void validHmac_triggerSnapshot_snapshotCalled()
// Given: correct HMAC for TRIGGER_SNAPSHOT
// When:  onAdminCommand(decoder)
// Then:  cluster.takeSnapshot() called once

@Test [UNIT] void validHmac_pauseStrategy_strategyPaused()
// Given: correct HMAC for PAUSE_STRATEGY, instrumentId=1 (BTC-USD)
// When:  onAdminCommand(decoder)
// Then:  strategyEngine.pauseStrategy(1) called

@Test [UNIT] void validHmac_resumeStrategy_strategyResumed()
// Given: correct HMAC for RESUME_STRATEGY, instrumentId=1
// When:  onAdminCommand(decoder)
// Then:  strategyEngine.resumeStrategy(1) called

@Test [UNIT] void validHmac_resetDailyCounters_countersCleared()
// Given: correct HMAC for RESET_DAILY_COUNTERS
// When:  onAdminCommand(decoder)
// Then:  riskEngine.resetDailyCounters() called

@Test [UNIT] void wrongHmac_commandRejected_stateUnchanged()
// Given: HMAC computed with wrong key (tampered)
// When:  onAdminCommand(decoder)
// Then:  kill switch NOT changed; error logged; no state change

@Test [UNIT] void emptyHmac_commandRejected()
// Given: hmacSignature=0L
// When:  onAdminCommand(decoder)
// Then:  rejected immediately

@Test [UNIT] void tamperedPayload_hmacInvalid_rejected()
// Given: correct HMAC computed for commandType=DEACTIVATE; payload changed to ACTIVATE
// When:  onAdminCommand(decoder)
// Then:  HMAC mismatch → rejected; kill switch not activated

@Test [UNIT] void replayedNonce_commandRejected_warnLogged()
// Given: nonce=1000L already in seenNonces set
// When:  second command with same nonce=1000L
// Then:  rejected before HMAC check; WARN logged; state unchanged

@Test [UNIT] void staleNonce_olderThan24h_rejected()
// Given: nonce encodes timestamp 25 hours ago in upper 32 bits
// When:  onAdminCommand(decoder)
// Then:  rejected (replay window = 24h)

@Test [UNIT] void nonceTimestampEncoded_upperBits_validWindow()
// Given: nonce = (currentEpochSeconds << 32) | sequenceNumber
// When:  onAdminCommand with current timestamp
// Then:  accepted (within 24h window)

@Test [UNIT] void activateKillSwitch_alreadyActive_idempotent()
// Given: kill switch already active
// When:  ACTIVATE_KILL_SWITCH command
// Then:  no exception; isKillSwitchActive() still true

@Test [UNIT] void deactivateKillSwitch_notActive_idempotent()
// Given: kill switch not active
// When:  DEACTIVATE_KILL_SWITCH command
// Then:  no exception; isKillSwitchActive() still false

@Test [UNIT] void nonceRingBuffer_afterWindowFull_oldNonceEvictedAndReaccepted()
// Given: NONCE_WINDOW_SIZE (10,000) unique nonces all sent successfully
// When:  the very first nonce is sent again
// Then:  accepted (evicted from ring; no longer in seenNonces HashSet)
// Verifies: seenNonces never grows beyond NONCE_WINDOW_SIZE entries

@Test [UNIT] void nonceRingBuffer_sizeNeverExceedsCapacity()
// Given: 20,000 unique valid nonces sent in sequence
// When:  checked after each batch of 1,000
// Then:  seenNonces.size() never exceeds 10,000 at any point

@Test [UNIT] void unknownOperatorId_commandRejected_afterHmacValidation()
// Given: valid HMAC; operatorId=99 not in allowedOperatorIds=[1,2,3]
// When:  onAdminCommand(decoder)
// Then:  rejected after HMAC check passes; no state change; ERROR logged

@Test [UNIT] void knownOperatorId_commandAccepted()
// Given: valid HMAC; operatorId=1 (in allowedOperatorIds=[1,2,3])
// When:  onAdminCommand(decoder)
// Then:  accepted; command executes normally

@Test [UNIT] void dualKeyMode_oldKeyStillAccepted_duringRotation()
// Given: AdminCommandHandler configured with both oldHmacKey and newHmacKey (rotation in progress)
// When:  command signed with oldHmacKey
// Then:  accepted (dual-key mode — both keys valid during rotation window)

@Test [UNIT] void dualKeyMode_newKeyAccepted_duringRotation()
// Given: dual-key mode active
// When:  command signed with newHmacKey
// Then:  accepted

@Test [UNIT] void singleKeyMode_onlyCurrentKeyAccepted()
// Given: only one HMAC key configured (rotation complete; old key removed)
// When:  command signed with old key (now invalid)
// Then:  rejected with HMAC mismatch
```


---

### T-013: DailyResetTimerTest

**Ownership:** CREATED by TASK-023. No other task cards add methods.

```java
// cluster.time() returns micros; midnight UTC calculation uses cluster.time()

@Test [UNIT] void scheduleNextReset_callsClusterScheduleTimer_correlId1001()
// When:  dailyResetTimer.setCluster(cluster) → scheduleNextReset() called internally
// Then:  cluster.scheduleTimer(1001L, nextMidnightMs) called once

@Test [UNIT] void onTimer_correctCorrelationId_dailyCountersReset()
// When:  onTimer(correlationId=1001L, timestamp=midnight)
// Then:  riskEngine.resetDailyCounters() called

@Test [UNIT] void onTimer_correctCorrelationId_archiveCalledBeforeReset()
// When:  onTimer(correlationId=1001L)
// Then:  cluster.takeSnapshot() called BEFORE riskEngine.resetDailyCounters()
//        (snapshot captures end-of-day state before reset)

@Test [UNIT] void onTimer_nextMidnightScheduled()
// When:  onTimer(1001L) fires
// Then:  cluster.scheduleTimer(1001L, nextMidnight+1day) called (next day's reset)

@Test [UNIT] void onTimer_wrongCorrelId_noReset()
// When:  onTimer(correlationId=9999L)
// Then:  resetDailyCounters() NOT called; scheduleTimer NOT called again

@Test [UNIT] void nextMidnight_calculatedCorrectly()
// Given: cluster.time() = 11:30 PM UTC (in micros)
// When:  scheduleNextReset()
// Then:  timer deadline = next midnight UTC (30 minutes later, not 24h+30m later)

@Test [UNIT] void nextMidnight_justPassedMidnight_next24h()
// Given: cluster.time() = 00:01 AM UTC
// When:  scheduleNextReset()
// Then:  timer deadline = next midnight (23h59m later, not 1 minute ago)

@Test [UNIT] void setCluster_scheduleTimerOnce()
// When:  setCluster() called
// Then:  exactly one scheduleTimer call (not multiple)

@Test [UNIT] void dailyReset_doesNotClearKillSwitch()
// Given: kill switch active
// When:  onTimer(1001L) → resetDailyCounters() called
// Then:  isKillSwitchActive() still true (confirmed via mock risk engine)
```


---

### T-014: CoinbaseLogonStrategyTest

**Ownership:** CREATED by TASK-008. No other task cards add methods.

```java
// Test credentials: apiKey="test-key", apiSecret=base64("secret-32-bytes"),
//   apiPassphrase="passphrase"

@Test [UNIT] void onLogon_signatureGenerated_correctHmacSha256()
// Given: MsgSeqNum=1, SenderCompID="client", TargetCompID="COINBASE",
//        sendingTime="1620000000"
// When:  onLogon(logonEncoder) called
// Then:  logon.rawData() == Base64(HMAC-SHA256(Base64Decode(apiSecret),
//                                  "1620000000A1COINBASECLIENT" + passphrase))

@Test [UNIT] void onLogon_timestampInTag52_currentEpochSeconds()
// When:  onLogon() called at known time
// Then:  tag 52 (sendingTime) contains current epoch seconds as string

@Test [UNIT] void onLogon_tag554_rawSecret_notBase64Encoded()
// Then:  logon.password() == apiPassphrase (plain string, not encoded)

@Test [UNIT] void onLogon_encryptMethod_zero()
// Then:  logon.encryptMethod() == 0 (no encryption tag)

@Test [UNIT] void onLogon_heartbeatInterval_30()
// Then:  logon.heartBtInt() == 30 (seconds)

@Test [UNIT] void onLogon_cancelOrdersOnDisconnect_tag8013()
// Then:  logon.cancelOrdersOnDisconnect("Y") set (tag 8013)

@Test [UNIT] void invalidSecret_exceptionThrown()
// Given: apiSecret not valid Base64
// When:  new CoinbaseLogonStrategy(config) called
// Then:  throws exception at construction time (fail fast)
```


---

### T-015: CoinbaseExchangeSimulatorTest

**Ownership:** CREATED by TASK-006. No other task cards add methods.

```java
// Simulator runs as FIX acceptor (server); tests connect as FIX initiator (client)

@Test [UNIT] void simulator_starts_acceptsConnection()
// When:  simulator.start(); FIX client connects
// Then:  FIX logon handshake completes; session established

@Test [UNIT] void fillMode_immediate_fillSentAfterAck()
// Given: simulator.setFillMode(IMMEDIATE)
// When:  client sends NewOrderSingle
// Then:  client receives ExecType=0 (Ack) then ExecType=F (Fill) in sequence

@Test [UNIT] void fillMode_partial_twoFillsSent()
// Given: simulator.setFillMode(PARTIAL_THEN_FULL); order qty=0.1 BTC
// When:  NewOrderSingle submitted
// Then:  client receives Ack → PartialFill (0.05 BTC) → Fill (remaining 0.05 BTC)

@Test [UNIT] void fillMode_reject_orderRejected()
// Given: simulator.setFillMode(REJECT_ALL)
// When:  NewOrderSingle submitted
// Then:  client receives ExecType=8 (Rejected); no fill

@Test [UNIT] void orderCancelRequest_cancelConfirmSent()
// Given: live order; client sends 35=F OrderCancelRequest
// When:  simulator processes
// Then:  client receives ExecType=4 (Canceled); order removed from sim state

@Test [UNIT] void orderStatusRequest_openOrder_executionReportSent()
// Given: live order clOrdId=1000
// When:  client sends 35=H OrderStatusRequest with tag11=1000
// Then:  client receives ExecType=I with current order state

@Test [UNIT] void orderStatusRequest_unknownOrder_orderCancelRejectSent()
// Given: no order with clOrdId=9999
// When:  client sends 35=H for clOrdId=9999
// Then:  client receives 35=9 (OrderCancelReject) with OrdStatus=4 (Unknown)

@Test [UNIT] void simulatorFillPrice_configurable()
// Given: simulator.setFillPrice(6_510_000_000_000L) ($65,100)
// When:  order submitted and filled
// Then:  ExecutionReport LastPx=65100.00000000

@Test [UNIT] void heartbeat_sentIfNoActivity()
// Given: FIX session idle for > HeartBtInt seconds
// When:  timer elapses
// Then:  simulator sends 35=0 Heartbeat to client

@Test [UNIT] void testRequest_testRequestSent_heartbeatResponseReceived()
// When:  simulator sends 35=1 TestRequest with TestReqID="test-1"
// Then:  client responds with 35=0 Heartbeat containing TestReqID="test-1"

@Test [UNIT] void backPressure_trySend_failureCounted()
// Given: SimulatorSessionHandler.backPressureCount
// When:  trySend() fails all 3 retries (simulated by mock session)
// Then:  backPressureCount incremented; WARN logged; no silent drop with zero count

@Test [UNIT] void multipleClients_independentSessions()
// Given: 2 simultaneous FIX client connections
// When:  each submits independent orders
// Then:  fills sent to correct client only; no cross-session confusion

@Test [UNIT] void sessionReset_ordersCleared()
// Given: 3 live orders
// When:  simulator.reset()
// Then:  all orders cleared; pendingOrders map empty

@Test [UNIT] void logon_invalidApiKey_logoutSent()
// Given: client sends Logon with wrong HMAC signature
// When:  simulator validates
// Then:  simulator sends 35=5 Logout; session disconnected

@Test [UNIT] void fillPrice_usesLimitPrice_whenNotConfigured()
// Given: simulator not configured with explicit fill price
//        order limit price = 65000.00000000
// When:  filled
// Then:  LastPx == order's limit price
```


---

### T-016: MarketDataHandlerTest

**Ownership:** CREATED by TASK-009. No other task cards add methods.

```java
// FIX MsgType=W (Market Data Snapshot); venueId=1, instrumentId=1 (BTC-USD)

@Test [UNIT] void msgTypeW_singleEntry_bidEntry_correctSBEPublished()
// Given: FIX message with MDEntryType=0 (bid), MDEntryPx=65000.0, MDEntrySize=1.5
// When:  onMessage(session, msg) called
// Then:  Disruptor receives MarketDataEvent SBE with:
//        side=BID, priceScaled=6_500_000_000_000L, qtyScaled=150_000_000L

@Test [UNIT] void msgTypeW_singleEntry_askEntry_correctSBEPublished()
// Given: MDEntryType=1 (ask), MDEntryPx=65001.0, MDEntrySize=0.5
// Then:  SBE side=ASK, priceScaled=6_500_100_000_000L, qtyScaled=50_000_000L

@Test [UNIT] void msgTypeX_multipleEntries_allPublishedSeparately()
// Given: FIX MsgType=X (incremental refresh) with 3 entries (2 bids, 1 ask)
// When:  onMessage called
// Then:  3 separate SBE events published to Disruptor (one per entry)

@Test [UNIT] void venueId_setFromSessionId()
// Given: Artio Session.id() maps to venueId=1 via IdRegistry
// When:  any market data message
// Then:  MarketDataEvent.venueId == 1

@Test [UNIT] void instrumentId_setFromSymbol()
// Given: FIX tag 55=BTC-USD → instrumentId=1
// When:  message processed
// Then:  MarketDataEvent.instrumentId == 1

@Test [UNIT] void unknownSymbol_ignored()
// Given: FIX tag 55=XRP-USD (not in registry → instrumentId=0)
// When:  onMessage called
// Then:  no SBE published to Disruptor

@Test [UNIT] void ingressTimestampNanos_setFromSystemNanoTime()
// When:  message processed
// Then:  MarketDataEvent.ingressTimestampNanos > 0; approximately == System.nanoTime()

@Test [UNIT] void disruptorFull_backpressureAlert_noBlockingCaller()
// Given: Disruptor ring buffer full (all slots claimed)
// When:  onMessage called
// Then:  message discarded; MARKET_DATA_BACKPRESSURE_COUNTER incremented;
//        Artio thread NOT blocked (non-blocking offer only)

@Test [UNIT] void zeroPriceEntry_ignored()
// Given: MDEntryPx=0.0 (invalid price)
// When:  onMessage
// Then:  no SBE published

@Test [UNIT] void zeroSizeEntry_ignored()
// Given: MDEntrySize=0.0 (delete level with size 0)
// When:  onMessage
// Then:  published with qty=0 (delete signal passed through to L2Book)

@Test [UNIT] void deleteEntry_mdUpdateAction3_publishedAsDelete()
// Given: MDUpdateAction=3 (DELETE) for existing bid level
// When:  onMessage
// Then:  SBE with size=0 published (L2Book will remove the level)

@Test [UNIT] void nonMarketDataMsgType_ignored()
// Given: FIX MsgType=8 (ExecutionReport) arrives on this handler by mistake
// When:  onMessage
// Then:  no SBE published; no exception
```


---

### T-017: ExecutionHandlerTest

**Ownership:** CREATED by TASK-010. No other task cards add methods.

```java
// FIX MsgType=8 (ExecutionReport); venueId=1, instrumentId=1

@Test [UNIT] void execTypeNew_correctSBEFields()
// Given: ExecType=0 (New), ClOrdID=1000, OrderID=venue-001, Symbol=BTC-USD
// When:  onMessage(session, execReport)
// Then:  ExecutionEvent SBE: execType=0, clOrdId=1000L, isFinal=false

@Test [UNIT] void execTypeFill_fullFill_isFinalTrue()
// Given: ExecType=F, OrdStatus=2 (Filled), LeavesQty=0, LastQty=0.1, LastPx=65000
// When:  onMessage
// Then:  isFinal=true; fillQtyScaled=10_000_000L; fillPriceScaled=6_500_000_000_000L

@Test [UNIT] void execTypeFill_partialFill_isFinalFalse()
// Given: ExecType=F, OrdStatus=1 (PartialFill), LeavesQty>0
// When:  onMessage
// Then:  isFinal=false; fillQtyScaled correct from LastQty

@Test [UNIT] void execTypeRejected_isFinalTrue()
// Given: ExecType=8 (Rejected), OrdRejReason=0
// When:  onMessage
// Then:  isFinal=true; rejectCode set from tag 103

@Test [UNIT] void execTypeCanceled_isFinalTrue()
// Given: ExecType=4 (Canceled)
// When:  onMessage
// Then:  isFinal=true

@Test [UNIT] void clOrdId_parsedFromTag11AsLong()
// Given: ClOrdID="1234567890"
// Then:  clOrdId==1234567890L (parsed as long, no String stored on hot path)

@Test [UNIT] void venueOrderId_capturedFromTag37()
// Given: OrderID="abc-123-def" (FIX tag 37)
// Then:  ExecutionEvent.venueOrderId=="abc-123-def" (stored as String — unavoidable)

@Test [UNIT] void ingressTimestampNanos_set()
// When:  execReport processed
// Then:  ingressTimestampNanos > 0

@Test [UNIT] void disruptorFull_executionReport_retried()
// Given: Disruptor ring buffer full
// When:  execution report arrives (must not be dropped)
// Then:  handler retries (spin-wait) until space available; report delivered

@Test [UNIT] void exchangeTimestamp_tag60_capturedInSBE()
// Given: TransactTime tag60 = "20240101-12:00:00.000"
// Then:  ExecutionEvent.exchangeTimestampNanos contains parsed value

@Test [UNIT] void unknownClOrdId_publishedWithWarning()
// Given: ClOrdID not in OrderManager (e.g., venue-generated order)
// When:  onMessage
// Then:  SBE still published (OrderManager will handle the unknown ID)

@Test [UNIT] void nonExecutionReportMsgType_ignored()
// Given: MsgType=W (MarketData) arrives on ExecutionHandler
// When:  onMessage
// Then:  no SBE published; no exception

@Test [UNIT] void execTypeOrderStatus_isFinalFalse()
// Given: ExecType=I (Order Status — response to status request)
// Then:  isFinal=false (informational, not terminal)

@Test [UNIT] void fillQtyZero_nonFillReport_fillFieldsZero()
// Given: ExecType=0 (New) — not a fill
// Then:  fillQtyScaled==0; fillPriceScaled==0

@Test [UNIT] void cumQty_correctlyScaled()
// Given: CumQty="0.05000000" (8 decimal places)
// Then:  cumQtyScaled == 5_000_000L

@Test [UNIT] void pendingReplace_execType4_canceledPublished()
// Given: ExecType=4 (Canceled) for an order in PENDING_REPLACE state
// Then:  SBE published normally; OrderManager handles the state transition
```


---

### T-018: MessageRouterTest

**Ownership:** CREATED by TASK-025. No other task cards add methods.

```java
// MessageRouter has one public method: dispatch(buffer, offset, blockLen, version, hdrLen, templateId)
// All tests call dispatch() with a real SBE-encoded buffer

@Test [UNIT] void templateId1_dispatchesToStrategyEngine()
// Given: SBE buffer with templateId=MarketDataEventDecoder.TEMPLATE_ID
// When:  router.dispatch(buffer, ...)
// Then:  strategyEngine.onMarketData(decoder) called once;
//        no other component called

@Test [UNIT] void templateId2_withFill_dispatchesToOrderManagerPortfolioRiskStrategy()
// Given: ExecutionEvent SBE with ExecType=FILL (isFinal=true)
// When:  dispatch(...)
// Then:  orderManager.onExecution() called first → returns true
//        portfolioEngine.onFill() called second
//        riskEngine.onFill() called third
//        strategyEngine.onExecution() called fourth with isFill=true

@Test [UNIT] void templateId2_noFill_doesNotCallPortfolioOrRisk()
// Given: ExecutionEvent SBE with ExecType=NEW (not a fill)
// When:  dispatch(...)
// Then:  orderManager.onExecution() called → returns false
//        portfolioEngine.onFill() NOT called
//        riskEngine.onFill() NOT called
//        strategyEngine.onExecution() called with isFill=false

@Test [UNIT] void templateId3_dispatchesToRecoveryCoordinator_venueStatus()
// Given: VenueStatusEvent SBE
// When:  dispatch(...)
// Then:  recoveryCoordinator.onVenueStatus() called

@Test [UNIT] void templateId4_dispatchesToRecoveryCoordinator_balance()
// Given: BalanceQueryResponse SBE
// When:  dispatch(...)
// Then:  recoveryCoordinator.onBalanceResponse() called

@Test [UNIT] void templateId33_dispatchesToAdminCommandHandler()
// Given: AdminCommand SBE
// When:  dispatch(...)
// Then:  adminCommandHandler.onAdminCommand() called

@Test [UNIT] void unknownTemplateId_logsWarnOnly()
// Given: templateId=999 (unknown)
// When:  dispatch(...)
// Then:  log.warn called; no component called; no exception

@Test [UNIT] void executionFanout_orderIsStrict()
// Given: ExecutionEvent with FILL
// When:  dispatch(...)
// Then:  verify call ORDER using a recording mock: OM → Portfolio → Risk → Strategy
//        NO other ordering is acceptable

@Test [UNIT] void decoderReuse_wrappedFreshEachDispatch()
// When:  dispatch() called twice with different buffers
// Then:  decoder correctly reads second buffer (pre-allocated decoder reused without stale state)

@Test [UNIT] void noAllocation_afterWarmup()
// When:  dispatch() called 1000 times with valid SBE buffers
// Then:  zero heap allocations (all decoders pre-allocated)

@Test [UNIT] void singlePublicMethod_dispatch_only()
// Then:  MessageRouter has no public methods other than dispatch() and the constructor
//        (private handler methods are not accessible from test; tested indirectly)
```


---

### T-019: OrderCommandHandlerTest

**Ownership:** CREATED by TASK-013. No other task cards add methods.

```java
// OrderCommandHandler polls Aeron cluster egress and routes SBE commands to Artio

@Test [UNIT] void newOrderCommand_producesCorrectFIXNewOrderSingle()
// Given: NewOrderCommand SBE: clOrdId=1000, venueId=1, side=BUY, ordType=LIMIT,
//        priceScaled=6_500_000_000_000L, qtyScaled=10_000_000L, tif=GTD
// When:  handler processes fragment
// Then:  Artio session receives FIX 35=D with:
//        tag11=1000, tag54=1 (BUY), tag40=2 (LIMIT), tag44=65000.00000000,
//        tag38=0.10000000, tag59=1 (GTD)

@Test [UNIT] void newOrderCommand_allRequiredTagsPresent()
// Given: NewOrderCommand with all fields set
// When:  FIX message built
// Then:  tags 35, 11, 21, 38, 40, 54, 55, 59, 60, 1 all present and non-empty

@Test [UNIT] void cancelCommand_producesCorrectFIXCancelRequest()
// Given: CancelOrderCommand SBE: clOrdId=2000, origClOrdId=1000, venueId=1
// When:  handler processes
// Then:  FIX 35=F with tag11=2000 (new cancel id), tag41=1000 (original), tag37=venueOrderId

@Test [UNIT] void replaceCommand_mgsTypeGVenue_routeReplaceCalledDirectly()
// Given: ReplaceOrderCommand for a venue that supports MsgType=G
// When:  handler processes
// Then:  Artio sends FIX 35=G (MsgType=G) directly to venue

@Test [UNIT] void replaceCommand_coinbase_notRouted_clusterOwnsSequencing()
// Given: ReplaceOrderCommand for Coinbase (no MsgType=G support)
// When:  handler receives command
// Then:  no FIX message sent (cluster OrderManager drives cancel+new; see Section 9.7)

@Test [UNIT] void orderStatusQueryCommand_sends35H()
// Given: OrderStatusQueryCommand SBE: clOrdId=1000, venueId=1
// When:  handler processes
// Then:  FIX 35=H sent with tag11=1000; tag37=venueOrderId if available

@Test [UNIT] void balanceQueryRequest_enqueuedToRestPoller()
// Given: BalanceQueryRequest SBE
// When:  handler processes
// Then:  restPoller.enqueue(requestId) called; no FIX message sent (REST not FIX)

@Test [UNIT] void recoveryCompleteEvent_loggedOnly_noFIXAction()
// Given: RecoveryCompleteEvent SBE
// When:  handler processes
// Then:  log.info called; no Artio session.send() called

@Test [UNIT] void artioBackPressure_retriesUpToThreeTimes()
// Given: session.trySend() returns negative (back-pressure) twice
// When:  NewOrderCommand processed
// Then:  trySend() called 3 times total; on 3rd success, message sent

@Test [UNIT] void artioBackPressure_allRetriesFail_rejectEventPublished()
// Given: session.trySend() always returns negative (all 3 attempts fail)
// When:  NewOrderCommand processed
// Then:  ARTIO_BACK_PRESSURE_COUNTER incremented; RejectEvent published to cluster

@Test [UNIT] void newOrder_marketOrder_tag44Omitted()
// Given: NewOrderCommand with ordType=MARKET
// When:  FIX built
// Then:  tag44 (price) NOT present in FIX message

@Test [UNIT] void noPendingReplacesMap_noStateInGateway()
// Given: OrderCommandHandler fully initialised
// When:  inspect internal state
// Then:  no pendingReplaces map field exists; gateway is pure forwarder for replace sequencing

@Test [UNIT] void venueNotConnected_commandDropped_errorLogged()
// Given: executionRouter.isVenueConnected(venueId) == false
// When:  NewOrderCommand arrives
// Then:  FIX not sent; error logged
```


---

### T-020: StrategyEngineTest

**Ownership:** CREATED by TASK-029. No other task cards add methods.

```java
// Setup: 2 test strategies registered: strategy1 (id=1, BTC-USD), strategy2 (id=2, ETH-USD)

@Test [UNIT] void onMarketData_dispatchedToAllStrategies()
// Given: marketDataDecoder for BTC-USD (instrumentId=1)
// When:  strategyEngine.onMarketData(decoder)
// Then:  strategy1.onMarketData() called (subscribes to BTC-USD)
//        strategy2.onMarketData() NOT called (subscribes to ETH-USD only)

@Test [UNIT] void onFill_dispatchedToAllStrategies()
// Given: fillDecoder for any instrumentId
// When:  strategyEngine.onExecution(decoder, isFill=true)
// Then:  strategy1.onFill() and strategy2.onFill() both called

@Test [UNIT] void onKillSwitch_dispatchedToAllStrategies()
// When:  strategyEngine.setActive(false)
// Then:  strategy1.onKillSwitch() and strategy2.onKillSwitch() both called

@Test [UNIT] void onKillSwitchCleared_dispatchedToAllStrategies()
// Given: active=false
// When:  strategyEngine.setActive(true)
// Then:  strategy1.onKillSwitchCleared() and strategy2.onKillSwitchCleared() both called

@Test [UNIT] void onVenueRecovered_dispatchedToAllStrategies()
// When:  strategyEngine.onVenueRecovered(venueId=1)
// Then:  strategy1.onVenueRecovered(1) called; strategy2.onVenueRecovered(1) called

@Test [UNIT] void onExecution_noFill_strategyOnFillNotCalled()
// Given: executionDecoder for non-fill (ExecType=NEW)
// When:  strategyEngine.onExecution(decoder, isFill=false)
// Then:  strategy1.onFill() NOT called; strategy1.onExecution() called if it exists

@Test [UNIT] void setActive_false_strategiesReceiveKillSwitch()
// When:  setActive(false)
// Then:  onKillSwitch() called on all strategies; active==false

@Test [UNIT] void setActive_true_strategiesReceiveKillSwitchCleared()
// Given: active=false
// When:  setActive(true)
// Then:  onKillSwitchCleared() called; active==true

@Test [UNIT] void pauseStrategy_specificStrategy_otherStrategiesUnaffected()
// When:  pauseStrategy(id=1)
// Then:  strategy1 paused (no further market data dispatched to it)
//        strategy2 still receives market data normally

@Test [UNIT] void resumeStrategy_specificStrategy_resumes()
// Given: strategy1 paused
// When:  resumeStrategy(id=1)
// Then:  strategy1 receives market data again on next onMarketData()

@Test [UNIT] void onMarketData_inactive_noDispatch()
// Given: active=false (cluster is follower)
// When:  onMarketData()
// Then:  no strategy receives onMarketData() (followers must not quote)

@Test [UNIT] void onExecution_inactiveButFill_onFillNotCalled()
// Given: active=false
// When:  onExecution(isFill=true)
// Then:  onFill() NOT called (inactive cluster does not update strategy position view)

@Test [UNIT] void pauseStrategy_unknownId_noEffect()
// When:  pauseStrategy(id=999) — no strategy has this id
// Then:  no exception; existing strategies unaffected

@Test [UNIT] void setActive_sameValue_idempotent()
// Given: active=true
// When:  setActive(true) again
// Then:  onKillSwitchCleared() NOT called again; no side effects

@Test [UNIT] void register_afterSetActive_newStrategyActivatedCorrectly()
// Given: active=true
// When:  register(newStrategy) after setActive(true) already called
// Then:  newStrategy.onKillSwitchCleared() called during init(); strategy receives events

@Test [UNIT] void onTimer_inactive_stillRouted_clusterDeterminism()
// Given: active=false (follower)
// When:  onTimer(correlationId=4001L)
// Then:  strategy.onTimer() still called on followers
//        (timers maintain state consistency across all nodes)
```


---

### T-021: TradingClusteredServiceTest

**Ownership:** CREATED by TASK-026. ADDS methods from: TASK-033.

```java
// Uses WarmupClusterShim; all components are real instances (not mocks)
// except egressPublication which uses a capture buffer

@Test [UNIT] void onStart_nullSnapshot_allStateZeroed()
// Given: snapshotImage=null (fresh start, no prior snapshot)
// When:  onStart(cluster, null)
// Then:  all component state zeroed; no loadSnapshot called; cluster reference stored

@Test [UNIT] void onStart_withSnapshot_stateRestored()
// Given: pre-written snapshot with known order state
// When:  onStart(cluster, snapshotImage)
// Then:  loadSnapshot() called on all 4 components in correct order

@Test [UNIT] void onRoleChange_toFollower_strategiesDeactivated()
// When:  onRoleChange(Cluster.Role.FOLLOWER)
// Then:  strategyEngine.setActive(false); all strategies get onKillSwitch()

@Test [UNIT] void onRoleChange_toLeader_strategiesActivated()
// Given: role=FOLLOWER (deactivated)
// When:  onRoleChange(Cluster.Role.LEADER)
// Then:  strategyEngine.setActive(true)

@Test [UNIT] void onTimerEvent_dailyResetId_timerForwarded()
// When:  onTimerEvent(correlationId=1001L, timestamp=midnight)
// Then:  dailyResetTimer.onTimer(1001L, timestamp) called

@Test [UNIT] void onTimerEvent_arbTimeoutId_timerForwarded()
// When:  onTimerEvent(correlationId=4001L)
// Then:  strategyEngine.onTimer(4001L) called

@Test [UNIT] void onTimerEvent_recoveryOrderQueryTimeoutId_timerForwarded()
// When:  onTimerEvent(correlationId=2001L)
// Then:  recoveryCoordinator.onTimer(2001L, timestamp) called

@Test [UNIT] void onTimerEvent_recoveryBalanceQueryTimeoutId_timerForwarded()
// When:  onTimerEvent(correlationId=3001L)
// Then:  recoveryCoordinator.onTimer(3001L, timestamp) called

@Test [UNIT] void installClusterShim_propagatedToAllComponents()
// Given: WarmupClusterShim shim
// When:  service.installClusterShim(shim)
// Then:  riskEngine.cluster==shim, orderManager.cluster==shim,
//        portfolioEngine.cluster==shim, strategyEngine.cluster==shim

@Test [UNIT] void removeClusterShim_clusterNullInAllComponents()
// Given: shim installed
// When:  service.removeClusterShim()
// Then:  all component cluster fields == null

@Test [UNIT] void resetWarmupState_allComponentsReset()
// Given: components with state (orders, positions, risk counters)
// When:  service.resetWarmupState()
// Then:  all components have zeroed state (pool refilled, maps empty, counters zero)

@Test [UNIT] void onTakeSnapshot_writesAllComponentsInOrder()
// When:  onTakeSnapshot(publication)
// Then:  snapshot bytes written in order: OrderManager → Portfolio → RiskEngine → RecoveryCoordinator

@Test [UNIT] void installClusterShim_realClusterAlreadySet_throws()
// Given: real cluster already set via onStart()
// When:  installClusterShim() called (warmup after real start — programming error)
// Then:  throws IllegalStateException

@Test [UNIT] void onRoleChange_toLeaderTwice_idempotent()
// Given: role=LEADER
// When:  onRoleChange(LEADER) again
// Then:  setActive(true) called; no double-activation side effect

@Test [UNIT] void onTimerEvent_unknownCorrelId_ignoredSilently()
// When:  onTimerEvent(correlationId=99999L)
// Then:  no exception; no component method called; no state change

@Test [UNIT] @Tag("SlowTest") void warmup_c2Verified_avgIterationBelowThreshold()
// Given: production warmup config (100,000 iterations; -XX:-BackgroundCompilation active)
// When:  new WarmupHarnessImpl(service, WarmupConfig.production()).runGatewayWarmup(fixLibrary) completes
// Then:  average iteration time < 500ns (confirms C2 compilation complete)
// NOTE:  Excluded from standard `./gradlew test`; runs in CI pre-release pipeline only.
//        Requires production-equivalent hardware and JVM flags — see Section 27.5.
//        Tag: @SlowTest — excluded via: useJUnitPlatform { excludeTags("SlowTest") }
```


---

### T-022: RestPollerTest

**Ownership:** CREATED by TASK-014. No other task cards add methods.

```java
// RestPoller makes HTTP GET /accounts to Coinbase; uses mock HTTP client in tests

@Test [UNIT] void balanceRequest_httpSuccess_responsePublished()
// Given: mock HTTP returns 200 with body: [{"currency":"BTC","available":"1.5"}]
// When:  restPoller.poll() processes enqueued request
// Then:  BalanceQueryResponse SBE published to Aeron ingress:
//        currency=BTC, availableScaled=150_000_000L (1.5 × 10^8)

@Test [UNIT] void balanceRequest_parsesAllCurrencies()
// Given: HTTP returns BTC=1.0, USD=10000.0, ETH=5.0
// When:  poll()
// Then:  3 BalanceQueryResponse SBE messages published (one per currency)

@Test [UNIT] void balanceRequest_httpTimeout_errorSentinelPublished()
// Given: HTTP call times out (> timeoutMs=5000)
// When:  poll()
// Then:  BalanceQueryResponse SBE published with error sentinel value (-1L)

@Test [UNIT] void balanceRequest_http4xx_errorSentinelPublished()
// Given: HTTP returns 401 Unauthorized (bad API key)
// When:  poll()
// Then:  error sentinel published; ERROR logged

@Test [UNIT] void balanceRequest_http5xx_retryOnce()
// Given: HTTP returns 503 once, then 200 on retry
// When:  poll()
// Then:  balance correctly published (one retry attempted)

@Test [UNIT] void availableBalance_notTotal_usedForScaling()
// Given: response: {"available":"0.9", "hold":"0.1", "balance":"1.0"}
// When:  parsed
// Then:  availableScaled=90_000_000L (uses "available", not "balance")

@Test [UNIT] void cbAccessSign_correctHmacSha256()
// Given: known timestamp, path="/accounts", method=GET, body=""
// When:  request headers built
// Then:  CB-ACCESS-SIGN = Base64(HMAC-SHA256(Base64Decode(secret),
//                                 timestamp+"GET"+"/accounts"+""))

@Test [UNIT] void cbAccessTimestamp_currentEpochSeconds()
// When:  request built
// Then:  CB-ACCESS-TIMESTAMP header = current Unix epoch seconds as string

@Test [UNIT] void requestId_associatedWithResponse()
// Given: requestId=42L enqueued
// When:  response received
// Then:  BalanceQueryResponse.requestId == 42L

@Test [UNIT] void emptyQueue_noPollAttempt()
// Given: no requests enqueued
// When:  poll() called
// Then:  no HTTP call made; no SBE published

@Test [UNIT] void noAllocation_onCachedResponse()
// Given: same currency seen twice
// When:  second poll()
// Then:  no new String allocation for currency parsing

@Test [UNIT] void scaledParsing_eightDecimalPlaces()
// Given: available="0.00000001"
// Then:  availableScaled==1L (minimum unit)
```


---

### T-023: VenueStatusHandlerTest

**Ownership:** CREATED by TASK-011. No other task cards add methods.

```java
@Test [UNIT] void onSessionAcquired_publishesConnectedEvent()
// Given: Artio calls onSessionAcquired(session) for venueId=1
// When:  handler invoked
// Then:  VenueStatusEvent SBE published to Disruptor with:
//        venueId=1, status=CONNECTED

@Test [UNIT] void onDisconnect_publishesDisconnectedEvent()
// Given: Artio calls onDisconnected(session) for venueId=1
// When:  handler invoked
// Then:  VenueStatusEvent SBE: venueId=1, status=DISCONNECTED

@Test [UNIT] void onSessionAcquired_venueIdResolvedFromSessionId()
// Given: Artio Session.id() maps to venueId=2 via IdRegistry
// When:  onSessionAcquired(coinbaseSession2)
// Then:  VenueStatusEvent.venueId == 2

@Test [UNIT] void unknownSession_ignored()
// Given: Artio Session.id() not in IdRegistry (unknown session)
// When:  onSessionAcquired(unknownSession)
// Then:  no SBE published; WARN logged

@Test [UNIT] void logon_publishesConnectedWithTimestamp()
// When:  session acquired after successful logon
// Then:  event timestamp captured from System.nanoTime() at moment of publish

@Test [UNIT] void multipleDisconnects_eachPublished()
// Given: session disconnects twice (e.g., flapping)
// When:  onDisconnected() called twice
// Then:  2 DISCONNECTED events published (cluster sees each)

@Test [UNIT] void sessionHandler_implementsArtioInterface()
// Then:  VenueStatusHandler implements Artio SessionAcquireHandler interface
//        (compile-time check via type assertion)
```


---

### T-024: ScaledMathTest

**Ownership:** CREATED by TASK-003. No other task cards add methods.

```java
// SCALE = 100_000_000L

@Test [UNIT] void safeMulDiv_hiZero_typicalMMLot_exact()
// Given: a=6_500_000_000_000L ($65K scaled), b=1_000_000L (0.01 BTC scaled)
//        product = 6.5e18 < Long.MAX_VALUE (hi==0, fast path)
// When:  ScaledMath.safeMulDiv(a, b, SCALE)
// Then:  returns 65_000_000_000_000L ($650 scaled); zero allocation

@Test [UNIT] void safeMulDiv_hiZero_zeroQty_returnsZero()
// When:  safeMulDiv(6_500_000_000_000L, 0L, SCALE)
// Then:  returns 0L

@Test [UNIT] void safeMulDiv_hiZero_unitQty_returnsPrice()
// When:  safeMulDiv(6_500_000_000_000L, 1L, SCALE)
// Then:  returns 65L (smallest representable notional)

@Test [UNIT] void safeMulDiv_hiNonZero_1BtcAt65K_exact()
// Given: a=6_500_000_000_000L, b=100_000_000L (1 BTC scaled)
//        product = 6.5e20 > Long.MAX_VALUE → BigDecimal path
// When:  safeMulDiv(a, b, SCALE)
// Then:  returns 6_500_000_000_000L ($65,000 scaled); correct despite overflow

@Test [UNIT] void safeMulDiv_hiNonZero_10BtcAt1M_exact()
// Given: a=100_000_000_000_000L ($1M scaled), b=1_000_000_000L (10 BTC scaled)
// When:  safeMulDiv(a, b, SCALE)
// Then:  returns 1_000_000_000_000_000_000L ($10M scaled); no silent wrap

@Test [UNIT] void safeMulDiv_hiNonZero_largePriceDiff_noPnlWrap()
// Given: large fill that would silently wrap with naive a*b/SCALE
// When:  safeMulDiv(...)
// Then:  result is positive (no sign-flip from wrap)

@Test [UNIT] void vwap_twoEqualLotsAtDifferentPrices_correctAverage()
// Given: oldPrice=6_500_000_000_000L, oldQty=10_000_000L,
//        fillPrice=6_510_000_000_000L, fillQty=10_000_000L
// When:  ScaledMath.vwap(oldPrice, oldQty, fillPrice, fillQty)
// Then:  returns 6_505_000_000_000L (equal-weight average of $65,000 and $65,100)

@Test [UNIT] void vwap_firstFill_oldQtyZero_returnsFillPrice()
// Given: oldQty=0 (no prior position)
// When:  vwap(anyOldPrice, 0, fillPrice=6_500_000_000_000L, fillQty=10_000_000L)
// Then:  returns 6_500_000_000_000L (fill price is the new average)

@Test [UNIT] void vwap_largePosition_noOverflow()
// Given: oldPrice=6_500_000_000_000L, oldQty=100_000_000L (1 BTC),
//        fillPrice=6_510_000_000_000L, fillQty=100_000_000L
// When:  vwap(...)
// Then:  correct result; no overflow exception

@Test [UNIT] void vwap_unequalLots_vwapWeightedCorrectly()
// Given: oldQty=10_000_000L @ $65,000; fillQty=30_000_000L @ $65,200
//        expected = (0.1×65000 + 0.3×65200) / 0.4 = $65,150
// Then:  vwap returns 6_515_000_000_000L

@Test [UNIT] void acceptanceCriteria_PF009_noSilentWrapForLargePriceAndQty()
// This is the AC-PF-009 acceptance test vector
// Given: price=6_500_000_000_000L ($65K), qty=100_000_000L (1 BTC)
// When:  safeMulDiv(price, qty, SCALE)
// Then:  result > 0 (no silent negative wrap); == 6_500_000_000_000L
```


## D.3 Integration Test Classes

### §D.3 Conventions (V10.0)

**Scope.** §D.3 specifies the 7 IT-NNN integration test classes. Integration tests exercise
interactions between 2-4 components with real wiring — real SBE encoders/decoders, real
Disruptor, real Agrona collections — but with external I/O mocked or stubbed (stub Aeron
Publication, stub HTTP server, in-memory Aeron Archive).

**What an IT-NNN block contains:**
- **Class name** as the `### IT-NNN: ClassName` heading.
- **Ownership line** stating which task card CREATES the test class file and any
  additional task cards that ADD methods.
- **Setup comment** at the top of the code block describing fixture state, mock/stub
  choices, and component wiring assumed by every test method.
- **Each `@Test [INTEGRATION] void methodName()` declaration** with a Given/When/Then
  comment specifying the test intent.

**Test run convention.** Integration tests run in `./gradlew :platform-cluster:integrationTest`
or `:platform-gateway:integrationTest` (module-local source-set defined in TASK-001). Unit
tests (§D.2) run in `./gradlew test` — the default task set excludes integration. The
`integrationTest` task applies its own JUnit platform filter: `includeTags("INTEGRATION")`.
Tests take 10ms–5s each (typical); total IT suite 30s–3min per module.

**Authoring contract.** §D.3 is the single source of truth for integration test
specifications. Plan task cards and spec Part B task cards reference §D.3 by test class
name; they do not duplicate test method lists. If a plan task card and §D.3 disagree,
§D.3 wins.

---

### IT-001: GatewayDisruptorIntegrationTest

**Ownership:** CREATED by TASK-007 (GatewayDisruptor + GatewaySlot). EXTENDED by TASK-012
(adds back-pressure retry tests involving AeronPublisher).

```java
// Integration test: real Disruptor + real AeronPublisher + stub Aeron Publication

@Test [INTEGRATION] void multipleProducers_messagesConsumedInOrder()
// Given: 3 producers (MarketDataHandler, ExecutionHandler, VenueStatusHandler)
//        each publishing 100 SBE messages
// When:  all run concurrently; consumer drains ring buffer
// Then:  all 300 messages consumed; no message lost; no duplicates

@Test [INTEGRATION] void singleProducer_highThroughput_noDrops()
// Given: single producer publishing 10,000 MarketDataEvent SBEs at max rate
// When:  consumer processes all
// Then:  all 10,000 received; ring buffer never blocks producer

@Test [INTEGRATION] void backpressure_alertFires_whenNearlyFull()
// Given: consumer paused (simulating slow Aeron publication)
//        producer publishes until ring buffer > 75% full
// When:  75% threshold crossed
// Then:  MARKET_DATA_BACKPRESSURE_COUNTER incremented (not 0)

@Test [INTEGRATION] void sbeEncodeDecode_roundTrip_fieldsPreserved()
// Given: MarketDataEvent with specific priceScaled, qtyScaled, venueId
// When:  encoded by producer → consumed by AeronPublisher → decoded from Aeron
// Then:  all fields preserved exactly

@Test [INTEGRATION] void executionEvent_neverDropped_underBackPressure()
// Given: Aeron publication simulating sustained back-pressure (always returns BACK_PRESSURED)
// When:  ExecutionEvent (templateId=2 — fill) arrives at AeronPublisher
// Then:  AeronPublisher spins indefinitely until Aeron eventually accepts (mock unblocks after 50ms)
// Then:  ExecutionEvent IS delivered — never dropped
// Then:  AERON_FILL_BACKPRESSURE_COUNTER incremented at least once (alert fired)
// Then:  AERON_BACKPRESSURE_COUNTER (market data counter) NOT incremented

@Test [INTEGRATION] void marketDataEvent_droppedUnderSustainedBackpressure_fillNeverDropped()
// This test verifies the priority-aware retry policy (Section 3.4.2) end-to-end.
// Given: Aeron publication simulating back-pressure for 20µs before clearing
// When:  simultaneously publish: 1 MarketDataEvent + 1 ExecutionEvent (fill) through Disruptor
// Then:  ExecutionEvent ALWAYS delivered (indefinite retry for templateId=2)
// Then:  MarketDataEvent MAY be dropped after 10µs timeout (templateId=1, 3, 4)
// Then:  AERON_BACKPRESSURE_COUNTER incremented for any dropped market data
// Then:  AERON_FILL_BACKPRESSURE_COUNTER incremented for fill back-pressure (not dropped)
// Implementation note: peek at templateId in slot buffer using MessageHeaderDecoder
// before entering retry loop — no allocation needed (pre-allocated headerPeek field)

@Test [INTEGRATION] void disruptor_ringBuffer_powerOfTwo_1024()
// Then:  ring buffer size == 1024 (power of 2 as required by Disruptor)

@Test [INTEGRATION] void concurrentPublish_noDataRace_sbeFieldsCorrect()
// Given: 2 threads publishing simultaneously to different Disruptor slots
// When:  consumer reads both messages
// Then:  no field corruption (SBE decoder wraps correct buffer offset)
```


---

### IT-002: L2BookToStrategyIntegrationTest

**Ownership:** CREATED by TASK-029 (Strategy Interface + StrategyEngine). No other task cards
add methods.

```java
// Integration: real InternalMarketView + real MarketMakingStrategy + real MessageRouter

@Test [INTEGRATION] void marketDataEvent_updatesBook_strategySeesNewPrices()
// Given: L2 book initialised with bid=65000, ask=65001; MM strategy registered
// When:  MarketDataEvent arrives with new bid=64998 updates book
// Then:  strategy.onMarketData() sees getBestBid() == 64998 and getBestAsk() == 65001

@Test [INTEGRATION] void bookStale_strategySuppress_noOrders()
// Given: L2 book hasn't received a tick in > stalenessThresholdMicros
// When:  strategy's onTimer fires (or next non-stale dispatch attempt)
// Then:  strategy suppresses quoting; zero egress orders

@Test [INTEGRATION] void marketDataThenFill_positionUpdated_skewApplied()
// Given: flat position; MM strategy active; live two-sided quotes
// When:  fill received for BUY side → position goes long
// Then:  next market-data event triggers re-quote with inventory skew applied (bid shrinks)

@Test [INTEGRATION] void multipleInstruments_bookUpdates_onlyCorrectStrategyNotified()
// Given: two MM strategies: one on BTC-USD, one on ETH-USD
// When:  MarketDataEvent for BTC-USD arrives
// Then:  BTC-USD strategy.onMarketData() called; ETH-USD strategy not called
```


---

### IT-003: RiskGateIntegrationTest

**Ownership:** CREATED by TASK-025 (MessageRouter). No other task cards add methods.

```java
// Integration: real RiskEngineImpl + real OrderManagerImpl + real MessageRouter

@Test [INTEGRATION] void approvedOrder_orderCommandPublishedToEgress()
// Given: RiskEngine allows order (under all limits, no kill switch)
// When:  strategy generates order intent; router dispatches to RiskEngine → OrderManager
// Then:  NewOrderCommand SBE published to egressPublication (captured via CapturingPublication)

@Test [INTEGRATION] void rejectedOrder_noOrderCommandPublished()
// Given: RiskEngine pre-trade check fails (order too large)
// When:  router dispatches order intent
// Then:  zero bytes written to egressPublication; OrderManager never creates OrderState

@Test [INTEGRATION] void killSwitch_strategyGeneratesIntent_riskBlocksAll()
// Given: killSwitch = true
// When:  strategy attempts to submit order (via router)
// Then:  RiskEngine rejects; no egress command

@Test [INTEGRATION] void softLimit_80pctOfMaxPosition_alertPublished_orderAllowed()
// Given: position = 80% of maxPositionLong; new BUY order pushes to 85%
// When:  router dispatches
// Then:  NewOrderCommand published (soft limit, not hard); RiskEvent (ALERT) also published

@Test [INTEGRATION] void recoveryLock_venue1_venue2OrdersAllowed()
// Given: recoveryLock[venue=1] = true; recoveryLock[venue=2] = false
// When:  strategy submits orders for both venues
// Then:  venue=1 order rejected (recovery lock); venue=2 order published
```


---

### IT-004: FillCycleIntegrationTest

**Ownership:** CREATED by TASK-021 (PortfolioEngine). No other task cards add methods.

```java
// Integration: real OrderManager + real PortfolioEngine + real RiskEngine

@Test [INTEGRATION] void fill_orderManagerTransitions_portfolioUpdated_riskNotified()
// Given: live NEW order in OrderManager
// When:  ExecutionEvent (FILL) arrives; router dispatches to OM → Portfolio → Risk → Strategy
// Then:  OrderManager state transitions to FILLED
// Then:  PortfolioEngine position updated with fill quantity + VWAP
// Then:  RiskEngine.updatePositionSnapshot() called with new net position
// Then:  Strategy.onFill() called exactly once

@Test [INTEGRATION] void fill_strategyOnFillCalled_positionReflected()
// Given: strategy is BTC-USD MM with base position flat
// When:  BUY fill arrives (0.01 BTC @ $65K)
// Then:  strategy's getPosition(BTC_USD) == 0.01 BTC after onFill
// Then:  realizedPnl unchanged (opening trade)

@Test [INTEGRATION] void multipleFills_sameOrder_cumQtyAccumulates()
// Given: NEW order for 0.05 BTC; 2 partial fills of 0.02 BTC each arrive
// When:  both execution events processed in sequence
// Then:  OrderManager state: PARTIALLY_FILLED; cumQty == 0.04; leavesQty == 0.01
// Then:  Portfolio netQty == 0.04
// Then:  Portfolio avgEntry reflects VWAP of both fills

@Test [INTEGRATION] void nonFillExecution_portfolioNotUpdated_riskNotNotified()
// Given: live NEW order
// When:  ExecutionReport arrives with execType=CANCELED (not a fill)
// Then:  OrderManager transitions NEW → CANCELED
// Then:  PortfolioEngine.onFill() NOT called
// Then:  RiskEngine.updatePositionSnapshot() NOT called
// Then:  Strategy.onFill() NOT called
```


---

### IT-005: SnapshotRoundTripIntegrationTest

**Ownership:** CREATED by TASK-022 (RecoveryCoordinator). No other task cards add methods.
(V9.1: ownership moved here from TASK-034 where it was misassigned.)

```java
// Integration: real snapshot write + real snapshot load across all 4 components
// Uses real Aeron Archive in embedded mode (not a mock)

@Test [INTEGRATION] void snapshot_writeAndLoad_orderManagerState()
// Given: 3 live orders (PENDING_NEW, PARTIALLY_FILLED, NEW) with specific field values
// When:  orderManager.writeSnapshot(publication); orderManager2.loadSnapshot(image)
// Then:  all 3 orders present in orderManager2 with identical state

@Test [INTEGRATION] void snapshot_writeAndLoad_portfolioState()
// Given: 2 positions: BTC-USD (netQty=50_000_000L, avgEntry=$65K),
//                     ETH-USD (netQty=-10_000_000L, realizedPnl=$100)
// When:  portfolio write + load round-trip
// Then:  both positions restored exactly

@Test [INTEGRATION] void snapshot_writeAndLoad_riskState()
// Given: killSwitch=true, dailyPnl=$3,000, recoveryLock[1]=true
// When:  riskEngine write + load round-trip
// Then:  killSwitch=true, dailyPnl preserved, recoveryLock preserved

@Test [INTEGRATION] void snapshot_writeAndLoad_recoveryCoordinatorState()
// Given: VENUE_A in QUERYING_ORDERS state; 2 pending queries
// When:  recoveryCoordinator write + load round-trip
// Then:  venueState restored; pending query count preserved

@Test [INTEGRATION] void snapshot_allComponents_writeInOrder()
// When:  TradingClusteredService.onTakeSnapshot() called
// Then:  bytes written in correct order:
//        OrderManager header → Portfolio header → RiskEngine header → Recovery header
//        (wrong order would corrupt loadSnapshot() on restart)

@Test [INTEGRATION] void snapshot_loadAfterFreshStart_allZero()
// Given: new component instances; no prior snapshot
// When:  loadSnapshot(null)
// Then:  all state zero (no NPE; safe start)

@Test [INTEGRATION] void snapshot_multipleRoundTrips_stable()
// Given: write snapshot → load → write again → load again
// When:  both loaded states compared
// Then:  identical (idempotent round-trip; no state drift)

@Test [INTEGRATION] void snapshot_terminalOrders_notIncluded()
// Given: 2 FILLED (terminal) orders + 1 NEW (live) order
// When:  snapshot write + load round-trip
// Then:  only the 1 live order present after load;
//        terminal orders not written (pool recycled; no ghost orders)

@Test [INTEGRATION] void snapshot_loadRestoresPoolAvailability()
// Given: snapshot with 3 live orders written; fresh OrderManager + fresh pool (2048 available)
// When:  loadSnapshot(image) — restores the 3 live orders via createPendingOrder()
// Then:  pool.available() == 2045 (2048 - 3)
// This verifies: loadSnapshot correctly claims slots from the pool (not bypassing it),
// so no allocation occurs on the first real fill after restart.

@Test [INTEGRATION] void resetAll_drainsPoolBeforeReset()
// Given: 5 orders created via createPendingOrder() (pool.available() == 2043)
// When:  orderManager.resetAll()  (called by TradingClusteredService.resetWarmupState())
// Then:  pool.available() == 2048 (all 5 slots returned before reset)
// This verifies: resetAll() calls pool.release() for each live order before clearing
// liveOrders map — no pool leak after warmup reset
```


---

### IT-006: RestPollerIntegrationTest

**Ownership:** CREATED by TASK-014 (RestPoller). No other task cards add methods.

```java
// Integration: real RestPoller + stub HTTP server (WireMock or similar)
// Tests exercise the REST balance-query path end-to-end including HMAC signing,
// concurrent polling, and timeout/error handling — but without going over the
// network to a real Coinbase sandbox.

@Test [INTEGRATION] void balanceQuery_validResponse_publishedToIngress()
// Given: stub HTTP server returns 200 with body {"BTC":{"balance":"1.50000000"}}
//        (well-formed Coinbase balance payload, Base64-signed if signature check is asserted)
// When:  RestPoller.pollBalance(venueId=1) invoked
// Then:  BalanceQueryResponse SBE published via the gateway's GatewayDisruptor;
//        parsed balanceScaled == 150_000_000L (1.5 BTC * 1e8)

@Test [INTEGRATION] void balanceQuery_http500_errorSentinelPublished()
// Given: stub HTTP server returns 500 Internal Server Error
// When:  RestPoller.pollBalance(venueId=1) invoked
// Then:  BalanceQueryResponse published with errorCode != 0 (error sentinel);
//        balanceScaled == 0L (caller treats as unknown)

@Test [INTEGRATION] void balanceQuery_timeout_errorSentinelPublished()
// Given: stub HTTP server configured to delay response past config.rest().timeoutMs()
// When:  RestPoller.pollBalance(venueId=1) invoked
// Then:  within (timeoutMs + 200ms grace) the sentinel response is published;
//        errorCode indicates TIMEOUT; no exception propagates

@Test [INTEGRATION] void concurrentBalanceRequests_serialized_noRaceCondition()
// Given: 5 concurrent calls to pollBalance(venueId=1) from 5 test threads
// When:  all 5 invocations race
// Then:  all 5 publish exactly one BalanceQueryResponse each;
//        no dropped or duplicated responses;
//        HMAC signatures are all distinct (each uses a fresh timestamp)

@Test [INTEGRATION] void hmacHeader_cbAccessTimestamp_withinOneSecondOfNow()
// Given: stub HTTP server captures incoming request headers
// When:  RestPoller.pollBalance(venueId=1) invoked
// Then:  captured CB-ACCESS-TIMESTAMP header is within ±1s of System.currentTimeMillis()/1000
//        (prevents stale-clock replay attacks)

@Test [INTEGRATION] void hmacHeader_signatureDifferentIfTimestampDiffers()
// Given: two back-to-back RestPoller calls; clock advances between them
// When:  both requests captured at stub server
// Then:  captured CB-ACCESS-SIGN headers differ (HMAC input includes timestamp)

@Test [INTEGRATION] void balanceRequest_zeroBalance_publishedAsZero()
// Given: stub returns {"BTC":{"balance":"0.00000000"}}
// When:  pollBalance invoked
// Then:  BalanceQueryResponse has balanceScaled == 0L; errorCode == 0 (success, not error)
```


---

### IT-007: ArbBatchParsingTest

**Ownership:** CREATED by TASK-013 (OrderCommandHandler + ExecutionRouter). No other task
cards add methods.

```java
// Integration: real OrderCommandHandler + real ExecutionRouter + stubbed Artio Session.
// Verifies that when ArbStrategy publishes both arb legs in a single Aeron IPC fragment
// (one buffer containing two NewOrderCommand SBE messages), the gateway correctly
// splits them into two separate FIX NewOrderSingle sends — see Spec §2.2.4 (arb dual-leg
// fragment parsing; M-2 fix from v4.0.0).

@Test [INTEGRATION] void singleFragment_twoNewOrderCommands_twoFixSends_correctClOrdIds()
// Given: single Aeron fragment containing 2 NewOrderCommand SBE messages:
//        leg1: clOrdId=1001, venueId=1, instrumentId=1, side=BUY,  qty=0.01, price=65000
//        leg2: clOrdId=1002, venueId=2, instrumentId=1, side=SELL, qty=0.01, price=65010
// When:  OrderCommandHandler.onFragment(buffer, offset, length)
// Then:  ExecutionRouter.route() called exactly twice;
//        first call: routed NewOrderCommand has clOrdId=1001, side=BUY;
//        second call: routed NewOrderCommand has clOrdId=1002, side=SELL;
//        ExecutionRouterImpl emits two distinct FIX NewOrderSingle (35=D) messages to the stub
//        Artio Session, one per venueId.

@Test [INTEGRATION] void singleFragment_cancelThenNew_cursorAdvancesCorrectly()
// Given: single Aeron fragment containing 1 CancelOrderCommand + 1 NewOrderCommand
// When:  OrderCommandHandler.onFragment() processes the fragment
// Then:  both commands are routed in order;
//        the cursor advances correctly after CancelOrderCommand (which has a varString
//        venueOrderId per §8.7) using encoder.encodedLength(), not blockLen alone;
//        NewOrderCommand is decoded starting at the correct offset.

@Test [INTEGRATION] void singleFragment_unknownTemplateId_fragmentProcessingStops_noException()
// Given: Aeron fragment with a NewOrderCommand followed by an unknown templateId=99
// When:  OrderCommandHandler.onFragment() processes the fragment
// Then:  the valid NewOrderCommand is routed;
//        the unknown template is logged (WARN) and skipped;
//        no exception propagates out of onFragment.

@Test [INTEGRATION] void multiFragmentBatch_reassemblyCorrect_noCrossFragmentCorruption()
// Given: Aeron MTU-sized fragment containing 1 NewOrderCommand, followed by a second
//        fragment containing 2 more NewOrderCommands
// When:  OrderCommandHandler.onFragment() called for both fragments in sequence
// Then:  three distinct FIX NewOrderSingle messages emitted to the stub Artio Session,
//        each with the correct clOrdId from its originating SBE message;
//        no state leaks between the two onFragment() invocations (decoder is reset
//        between calls via the per-fragment headerPeek field).

@Test [INTEGRATION] void emptyFragment_noRouting_noException()
// Given: Aeron fragment with zero bytes of SBE content
// When:  OrderCommandHandler.onFragment(buffer, offset=0, length=0)
// Then:  ExecutionRouter.route() not called; no exception.
```


---

## D.4 End-to-End Tests

### §D.4 Conventions (V10.0)

**Scope.** §D.4 specifies the 6 ET-NNN end-to-end test classes. E2E tests exercise the
full system: real `CoinbaseExchangeSimulator` over FIX, real gateway process, real
3-node Aeron Cluster (or single-node-for-test variant), real strategies, real
business logic — all running in the same JVM via `TradingSystemTestHarness`.

**What an ET-NNN block contains:**
- **Class name** as the `#### ET-NNN: ClassName` heading (four hashes — nested under
  "### E2E Test Classes").
- **Ownership line** stating which task card CREATES the test class file and any
  additional task cards that ADD methods.
- **Each `@Test [E2E] void methodName()` declaration** with a Given/When/Then comment
  specifying the test intent, plus:
  - An `// ET-NNN-X → AC-YYY` trailing comment on the method-signature line linking the
    test to the acceptance criterion it verifies (see Part E).
  - A `// TIMING NOTE:` block where applicable, explaining the colocation-vs-loopback
    assertion split (some timing assertions are `@RequiresProductionEnvironment`-tagged
    and only run on real hardware; see §20.4).

**Test run convention.** E2E tests run in `./gradlew e2eTest` (the `e2eTest` source set
defined in TASK-001 / §21.2). They are excluded from default `./gradlew test`. Typical
duration: 2s–30s per test; full suite ~3min. Timing-assertive tests carrying the
`@RequiresProductionEnvironment` tag are additionally excluded from CI-commodity-hardware
runs and only run in the pre-release colocation pipeline (§6.8 phase model).

**Authoring contract.** §D.4 is the single source of truth for E2E test specifications.
Plan task cards and spec Part B task cards reference §D.4 by test class name; they do not
duplicate test method lists. If a plan task card and §D.4 disagree, §D.4 wins.

---

**Fixture: `TradingSystemTestHarness`** (below) is the shared E2E fixture used by every
ET-NNN test class. It is CREATED by TASK-006 and EXTENDED by TASK-016 (adds gateway
startup wiring). It is not a test class itself — it has no `@Test` methods — but it is
specified here because all ET-NNN blocks depend on it.

### TradingSystemTestHarness

```java
package ig.rueishi.nitroj.exchange.test;

public final class TradingSystemTestHarness implements AutoCloseable {

    private final CoinbaseExchangeSimulator simulator;
    private final GatewayMain               gateway;
    private final ClusterMain               cluster;
    private final AeronCluster              clusterClient;

    public static TradingSystemTestHarness start(SimulatorConfig simConfig,
                                                   GatewayConfig gwConfig,
                                                   ClusterNodeConfig clusterConfig) {
        // 1. Start MediaDriver (embedded, shared)
        // 2. Start CoinbaseExchangeSimulator
        // 3. Start ClusterMain (single node for E2E)
        // 4. Start GatewayMain (connects to cluster + simulator)
        // 5. Wait for FIX logon confirmed (max 5 seconds)
        // 6. Return harness
    }

    public CoinbaseExchangeSimulator simulator() { return simulator; }

    public void waitForQuotes(int expectedCount, long timeoutMs) {
        // Poll simulator.receivedOrders until expectedCount reached or timeout
    }

    public void waitForFills(int expectedCount, long timeoutMs) {
        // Poll simulator.fillCount
    }

    public long getPortfolioNetQty(int venueId, int instrumentId) {
        // Query via JMX or shared-memory counter
    }

    @Override
    public void close() {
        gateway.shutdown();
        cluster.shutdown();
        simulator.stop();
    }
}
```

### E2E Test Classes

#### ET-001: MarketMakingE2ETest

**Ownership:** CREATED by TASK-030. No other task cards add methods.

```java
@Test [E2E] void mmStrategy_onMarketData_submitsTwoSidedQuotes() {  // ET-001-1 → AC-MM-001, AC-MM-003, AC-MM-016
    // Given: simulator sending BTC-USD market data bid=65000, ask=65001
    // When: system starts and MM strategy activates
    // Then: within 500ms, simulator receives BUY and SELL NewOrderSingle
    // Assert: bid price < ask price; sizes > 0; TIF = GTD; clOrdId monotonically increasing (not UUID)
}

@Test [E2E] void mmStrategy_marketMoves_requotesWithinThreshold() {  // ET-001-2 → AC-MM-002, AC-MM-015
    // Given: live two-sided quotes at 65000/65001
    // When: simulator sends new market data bid=64990, ask=64991 (> refreshThreshold)
    // Then: within 500ms, simulator receives cancels for old quotes + new quotes
    // Assert: cancel arrives BEFORE new order (old quotes cleared before requote)
}

@Test [E2E] void mmStrategy_fillReceived_inventorySkewsNextQuote() {  // ET-001-3 → AC-MM-004
    // Given: live quote at 65000 (BUY)
    // When: simulator sends fill for BUY order (0.01 BTC)
    // Then: next bid price lower than unskewed fair value (inventory skew applied)
}

@Test [E2E] void mmStrategy_killSwitchActivated_quotesCancel() {  // ET-001-4 → AC-MM-007, AC-MM-008, AC-PF-P-003, AC-OL-006
    // Given: live two-sided quotes
    // When: AdminCli activates kill switch
    // Then: within 10ms, simulator receives cancel requests for both quotes
    // Then: no new quotes submitted for ≥ 1 second
    // TIMING NOTE: this is the ONLY test that measures the 10ms kill-switch propagation SLA (AC-PF-P-003)
    //
    // @RequiresProductionEnvironment for the 10ms assertion:
    // Under loopback TCP the kill switch will propagate in < 1ms, trivially passing.
    // The 10ms SLA is set for cross-connect production latency (~100-300µs one-way).
    // On CI loopback: assert cancels received (correctness only; no timing assertion).
    // On colocation hardware: assert cancels received within 10ms (full SLA validation).
}

@Test [E2E] void mmStrategy_marketDataStale_quotesCancel() {  // ET-001-5 → AC-MM-009
    // Given: live two-sided quotes
    // When: simulator stops sending market data for > stalenessThresholdMicros
    // Then: strategy suppresses; existing quotes canceled on next refresh
}

@Test [E2E] void mmStrategy_wideSpread_suppressesQuotes() {  // ET-001-6 → AC-MM-010
    // Given: simulator sends bid=65000, ask=66000 (wide spread)
    // Then: no quotes submitted within 500ms
}

@Test [E2E] void mmStrategy_positionAtMaxLong_askSideZeroSize_suppressed() {  // ET-001-7 → AC-MM-006
    // Given: position = maxLongPosition via synthetic fills
    // When: market data arrives
    // Then: only BID quote suppressed (no new bids); ASK still quoted
}
```

#### ET-002: ExecutionFeedbackE2ETest

**Ownership:** CREATED by TASK-016. No other task cards add methods.

```java
@Test [E2E] void newOrder_ack_orderStateNew() {
    // Given: gateway connects to simulator
    // When: strategy submits quote
    // Then: simulator sends ACK (ExecType=0)
    // Then: OrderManager transitions to NEW
}

@Test [E2E] void partialFill_orderPartiallyFilled_portfolioUpdated() {
    // Simulator: FillMode.PARTIAL_THEN_FULL
    // Given: BUY order for 0.02 BTC submitted
    // When: simulator sends 50% fill (0.01 BTC)
    // Then: OrderManager status = PARTIALLY_FILLED
    // Then: Portfolio netQty = 0.01 * 1e8 (scaled)
    // Then: Second fill arrives; OrderManager status = FILLED
    // Then: Portfolio netQty = 0.02 * 1e8
}

@Test [E2E] void venueReject_orderRejected_noPositionChange() {
    // Simulator: FillMode.REJECT_ALL
    // When: strategy submits quote
    // Then: OrderManager status = REJECTED
    // Then: Portfolio unchanged
    // Then: RiskEngine consecutive rejection counter incremented
}

@Test [E2E] void cancelRace_fillArrivesBeforeCancelAck_positionUpdated() {
    // Simulator: delayed cancel confirm (100ms after order filled)
    // When: strategy cancels order at same time fill arrives
    // Then: OrderManager handles correctly (FILLED, not CANCELED)
    // Then: portfolio reflects fill
}
```

#### ET-003: RecoveryE2ETest

**Ownership:** CREATED by TASK-022. No other task cards add methods.

```java
@Test [E2E] void gatewayDisconnect_reconnects_reconciles_resumesTrading() {
    // Given: system running; live quotes submitted
    // When: simulator.scheduleDisconnect(0) — FIX session drops
    // Then: VenueStatusEvent{DISCONNECTED} arrives at cluster
    // Then: recoveryLock set; no new orders for venue
    // Then: Artio reconnects (within 5 seconds)
    // Then: OrderMassStatusRequest sent by gateway
    // Then: Simulator responds with order statuses
    // Then: Reconciliation completes
    // Then: recoveryLock cleared; trading resumes
    // Assert: total downtime < 10 seconds (correctness; within 30s SLA)
    //
    // @RequiresProductionEnvironment for the 30s SLA assertion (AC-RC-002):
    // Under loopback, reconciliation completes in < 1s. The 30s SLA accounts for
    // production REST API latency, cross-connect round-trips, and Artio reconnect
    // timing. Assert correctness (reconciliation completes) on CI loopback.
    // Assert timing (< 30s) on colocation hardware.
}

@Test [E2E] void reconnect_missingFill_detectedAndApplied() {
    // Given: order submitted and partially filled during "downtime"
    // Simulator: configure to report order as FILLED in OrderStatusRequest
    //            but we never sent the fill ExecutionReport
    // When: reconnect + reconciliation
    // Then: synthetic FillEvent created with isSynthetic=true
    // Then: portfolio updated to match venue reported fill
}

@Test [E2E] void reconnect_orphanAtVenue_canceledBySystem() {
    // Simulator: has an order the cluster doesn't know about
    // (simulate by injecting order directly into simulator state)
    // When: reconciliation runs
    // Then: CancelOrderCommand sent for orphan order
}

@Test [E2E] void reconnect_balanceMismatch_killSwitchActivated() {
    // Simulator: REST /accounts returns balance different by > tolerance
    // When: balance reconciliation
    // Then: kill switch activated
    // Then: no trading resumes
}

@Test [E2E] void clusterLeaderFailover_gatewaySeamless_fixSessionContinues() {
    // Requires 3-node cluster setup
    // Given: system running; gateway connected to leader
    // When: leader node stopped (simulated crash)
    // Then: Aeron Cluster elects new leader (< 500ms)
    // Then: gateway Aeron client reconnects to new leader
    // Then: FIX session to simulator: UNCHANGED (still live)
    // Then: trading continues without FIX reconnect
    // Assert: no orders lost; no duplicate orders
}

@Test [E2E] void tag8013_cancelOrdersOnDisconnect_reconciliationHandlesVenueCancels() {
    // Verifies: Coinbase cancels all open orders on FIX disconnect (tag 8013=Y behavior)
    // and RecoveryCoordinator correctly processes the resulting CANCELED status responses.
    //
    // Given: system running; 3 live open orders (PENDING_NEW, NEW, PARTIALLY_FILLED)
    // When:  simulator configured to cancel all open orders on FIX logout
    //        (simulating Coinbase's tag 8013=Y CancelOrdersOnDisconnect behavior)
    //        simulator.scheduleDisconnect(cancelAllOnLogout=true)
    // Then:  FIX session drops; VenueStatus{DISCONNECTED} received by cluster
    // Then:  Artio reconnects (within 5 seconds)
    // Then:  RecoveryCoordinator sends OrderStatusRequest for each open order
    // Then:  simulator reports all 3 orders as CANCELED (ExecType=4)
    // Then:  RecoveryCoordinator applies Case B: venue=CANCELED, internal=OPEN
    //        → forces all 3 orders to CANCELED state
    // Then:  pool.available() increases by 3 (all slots returned on forced CANCELED)
    // Then:  recoveryLock cleared; RecoveryCompleteEvent published; trading resumes
    // Assert: no orphan orders in liveOrders map; position unchanged (partial fills preserved)
}
```

#### ET-004: ArbStrategyE2ETest

**Ownership:** CREATED by TASK-031. No other task cards add methods.

```java
@Test [E2E] void arbOpportunity_detected_bothLegsSubmitted() {
    // Given: two simulator instances (two venues)
    // When: price discrepancy > minNetProfitBps (after fees)
    // Then: BUY order on cheaper venue received by simulator-A
    // Then: SELL order on expensive venue received by simulator-B
    // Then: both legs submitted within 2ms of each other
}

@Test [E2E] void arbLeg1FillLeg2Cancel_hedgeSubmitted() {
    // Simulator-A: IMMEDIATE fill
    // Simulator-B: REJECT_ALL
    // When: arb legs submitted
    // Then: leg1 fills; leg2 rejected
    // Then: hedge SELL order submitted on simulator-A
}

@Test [E2E] void arbHedgeFails_killSwitchActivated() {
    // Simulator-A: IMMEDIATE fill
    // Simulator-B: REJECT_ALL
    // Simulator-A (for hedge): also REJECT_ALL
    // When: hedge submitted and rejected
    // Then: kill switch activated
    // Then: no further orders on any venue
}

@Test [E2E] void arbLegTimeout_pendingLegCanceled_imbalanceHedged() {
    // Simulator-A: IMMEDIATE fill; Simulator-B: NO_FILL (ACK but no fill)
    // When: arb legs submitted; leg1 fills immediately; leg2 never fills
    // Wait: legTimeoutClusterMicros elapses (use fast-clock shim)
    // Then: cancel sent for pending leg2
    // Then: hedge SELL submitted on simulator-A for the imbalance
    // Then: arbActive == false (state reset)
}
```

#### ET-005: RiskE2ETest

**Ownership:** CREATED by TASK-018. No other task cards add methods.

```java
@Test [E2E] void dailyLossLimit_exceeded_killSwitchActivated() {
    // Configure maxDailyLoss = tiny value (e.g., $1 USD)
    // When: fill at loss > $1
    // Then: kill switch activated on next risk check
    // Then: all live orders canceled
    // NOTE: this test verifies CORRECTNESS of the kill switch trigger mechanism only.
    // It does NOT assert the 10ms propagation timing (AC-PF-P-003) — that is ET-001-4.
}

@Test [E2E] void orderRateLimit_exceeded_ordersRejectedByRisk() {
    // Configure maxOrdersPerSecond = 5
    // When: strategy tries to submit 6 quotes in 1 second
    // Then: first 5 reach simulator; 6th rejected by RiskEngine
    // Then: simulator receives exactly 5 orders
}

@Test [E2E] void killSwitch_deactivatedByAdmin_tradingResumes() {
    // Given: kill switch active
    // When: AdminCli deactivate-kill-switch command sent
    // Then: kill switch deactivated
    // Then: strategy resumes quoting on next market data tick
}
```

#### ET-006: FullSystemSmokeTest

**Ownership:** CREATED by TASK-016. No other task cards add methods.

```java
@Test [E2E] void fullSystemSmoke_startsAndTrades_noExceptions() {
    // Given: 3-node Aeron Cluster + 1 gateway + CoinbaseExchangeSimulator all started
    //        simulator configured with FillMode.PARTIAL (10% fill rate)
    //        cluster reaches leader election within 500ms (AC-SY-003)
    //
    // When:  system runs for 30 seconds with simulator sending 1,000 market data ticks
    //        spread = 2 bps (narrow enough for MM strategy to quote)
    //
    // Then (in order):
    //   1. FIX logon completes within 10 seconds of gateway start (AC-SY-001)
    //   2. First two-sided quotes arrive at simulator within 500ms of first market data tick (AC-MM-001)
    //   3. After 10% simulator fills: portfolio.netQtyScaled != 0 (fills reach cluster)
    //   4. No ERROR or higher log entries in gateway, cluster-node-0, cluster-node-1, cluster-node-2 logs
    //   5. At T=30s: simulator still receiving quote updates (strategy still live)
    //   6. Graceful shutdown: all threads terminated within 5 seconds
    //   7. netstat: no open ports remain after shutdown (AC-SY-002)
}
```

---

# PART E: ACCEPTANCE CRITERIA

## E.1 Format

Each criterion has:
- **AC-ID**: unique identifier
- **Statement**: exactly what must be true
- **Test Coverage**: which test(s) verify it
- **Measurable**: how to measure (time, count, boolean)

---

## E.2 Market Making Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-MM-001 | Two-sided quotes submitted within 500ms of first valid market data after startup | ET-001-1 | Time delta: first MD → first order in simulator |
| AC-MM-002 | Quotes are requoted within 500ms when market moves > refreshThresholdBps | ET-001-2 | Time delta: MD event → new order in simulator |
| AC-MM-003 | All quotes have bid price strictly less than ask price | ET-001-1, T-009 | Assert ourBid < ourAsk on every submit |
| AC-MM-004 | Inventory skew formula: long position reduces bid and ask prices | T-009, ET-001-3 | adjustedFairValue < fairValue when pos > 0 |
| AC-MM-005 | Inventory skew formula: short position raises bid and ask prices | T-009 | adjustedFairValue > fairValue when pos < 0 |
| AC-MM-006 | Zero-size quotes are never submitted | T-009, ET-001-7 | Assert size > 0 on every NewOrderSingle |
| AC-MM-007 | Kill switch cancels all live quotes within 10ms | ET-001-4 | Time delta: kill switch activated → cancel received by simulator |
| AC-MM-008 | Kill switch blocks all new quotes after activation | ET-001-4 | No new orders after kill switch for 1 second |
| AC-MM-009 | Market data stale suppression: quotes stop within 1 quote cycle | ET-001-5 | No orders after staleness threshold |
| AC-MM-010 | Wide spread suppression: no quotes when spread > maxTolerableSpreadBps | ET-001-6, T-009 | Simulator receives 0 orders during wide spread |
| AC-MM-011 | Prices rounded to tick size: bid rounded down, ask rounded up | T-009 | bid % tickSize == 0; ask % tickSize == 0 |
| AC-MM-012 | Quote sizes rounded to lot size | T-009 | size % lotSize == 0 |
| AC-MM-013 | Strategy only quotes on configured venueId and instrumentId | T-009 | Wrong venue/instrument events ignored |
| AC-MM-014 | No redundant quotes: identical prices/sizes not resubmitted | T-009 | No new order if refresh check returns false |
| AC-MM-015 | Old quotes canceled before new quotes submitted on refresh | ET-001-2 | Cancel received before new order in simulator |
| AC-MM-016 | clOrdId is cluster.logPosition() — not random, not UUID | T-009, ET-001-1 | clOrdId monotonically increasing; no UUID format |
| AC-MM-017 | Zero allocations per onMarketData() call after warmup | T-009 | async-profiler alloc rate = 0 on hot path |

## E.3 Arbitrage Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-ARB-001 | Opportunity formula includes taker fees and slippage before submitting | T-010, ET-004-1 | Test vectors from Section 20.1 all pass |
| AC-ARB-002 | Below minNetProfitBps threshold: no order submitted | T-010 | Simulator receives 0 orders |
| AC-ARB-003 | Both arb legs submitted in single Aeron offer call | T-010, IT-007 | Single offer() call encodes both orders (T-010 — cluster side); two FIX sends from one fragment (IT-007 — gateway side) |
| AC-ARB-004 | Leg 1 is always BUY on cheaper venue; Leg 2 is always SELL on expensive venue | T-010, ET-004-1 | Leg sides and venues consistent with price direction |
| AC-ARB-005 | Both legs are IOC (TimeInForce=3) | T-010 | FIX tag 59 = '3' on both legs |
| AC-ARB-006 | attemptId uses cluster.logPosition() — never UUID | T-010 | Assert attemptId is long matching log position |
| AC-ARB-007 | Leg imbalance > lotSize triggers hedge within 100ms | T-010, ET-004-2 | Time delta: imbalance detected → hedge submitted |
| AC-ARB-008 | Hedge failure triggers kill switch | T-010, ET-004-3 | killSwitchActive = true after hedge reject |
| AC-ARB-009 | No new arb while one attempt is active | T-010 | Only one arb per cycle |
| AC-ARB-010 | Leg timeout: pending leg canceled and hedged within `legTimeoutClusterMicros` | T-010 | Timer fires; cancel + hedge submitted |
| AC-ARB-011 | After hedge failure and kill switch activated, new arb attempts suppressed for `cooldownAfterFailureMicros` before resuming | T-010 | No arb during cooldown; attempts resume after window |

## E.4 Order Lifecycle Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-OL-001 | All valid state transitions from Section 7.3 produce correct next state | T-007 | All transition rows pass |
| AC-OL-002 | Invalid state transitions are logged and do not change state | T-007 | State unchanged after invalid event |
| AC-OL-003 | Fills on terminal orders are applied (revenue never discarded) | T-007 | Fill applied even to FILLED order |
| AC-OL-004 | Duplicate execId (tag 17) is discarded with metrics increment | T-007 | Second report with same execId → counter +1; state unchanged |
| AC-OL-005 | clOrdId is unique and monotonically increasing system-wide | T-007, T-009 | No duplicate clOrdId across 10,000 orders |
| AC-OL-006 | cancelAllOrders() cancels every live (non-terminal) order | T-007 | Cancel published for each live order |
| AC-OL-007 | OrderState pool: zero new allocations after warmup | T-006, T-007 | Pool available count stable; no GC from orders |
| AC-OL-008 | Snapshot/restore: all live OrderState fields match after restart | IT-005, T-007 | Field-by-field comparison after load |

## E.5 Portfolio & PnL Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-PF-001 | VWAP average price correct for multiple buys (test vector) | T-008 | Exact match to spec Section 20.3 test vector |
| AC-PF-002 | Realized PnL correct on long close (test vector) | T-008 | Exact match to spec test vector |
| AC-PF-003 | Realized PnL correct on short close (test vector) | T-008 | Exact match to spec test vector |
| AC-PF-004 | Position correctly flips long → short (residual qty handled) | T-008 | netQty sign and magnitude correct |
| AC-PF-005 | netQty = 0 after full close; avgEntryPrice reset to 0 | T-008 | Both fields exactly 0 |
| AC-PF-006 | Unrealized PnL correct for long position, mark above entry | T-008 | Formula: (mark - entry) * qty / SCALE |
| AC-PF-007 | Unrealized PnL correct for short position, mark below entry | T-008 | Formula: (entry - mark) * qty / SCALE |
| AC-PF-008 | RiskEngine notified after every fill | T-008, IT-004 | updatePositionSnapshot called on every onFill() |
| AC-PF-009 | No arithmetic overflow for large prices and quantities | T-008, **T-024 ScaledMathTest** | 1 BTC at $65K and 10 BTC at $1M: correct results verified against BigDecimal truth |
| AC-PF-010 | Snapshot/restore: positions and realized PnL match exactly | IT-005 | All Position fields match after load |

## E.6 Risk Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-RK-001 | Pre-trade checks fire in exact order defined in Section 12.2 | T-005 | Recovery lock (1) before kill switch (2); etc. |
| AC-RK-002 | Kill switch rejects all orders for all venues | T-005, ET-005 | 0 orders submitted after activation |
| AC-RK-003 | Recovery lock rejects orders for affected venue only | T-005, IT-003 | Other venue orders still accepted |
| AC-RK-004 | Max position check uses net projected position | T-005 | Long + pending buy projected; reject if > max |
| AC-RK-005 | Rate limit: sliding window expires old orders correctly | T-005 | Orders > 1s ago not counted |
| AC-RK-006 | Daily loss limit breach activates kill switch automatically | T-005, ET-005-1 | killSwitchActive after loss > maxDailyLoss |
| AC-RK-007 | Soft limits publish alert without rejecting | T-005 | Order approved + RiskEvent published |
| AC-RK-008 | Daily reset clears daily counters but not kill switch | T-005, T-013 | dailyPnl=0; killSwitch unchanged |
| AC-RK-009 | preTradeCheck() completes in < 5µs (p99) | T-005 | Measured with System.nanoTime() in test |
| AC-RK-010 | Kill switch deactivation requires operator admin command | ET-005-3, T-012 | Cannot deactivate without valid AdminCommand |

## E.7 Recovery Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-RC-001 | No order submitted during recovery window for affected venue | ET-003-1 | 0 new orders between disconnect and RecoveryComplete |
| AC-RC-002 | `RECONCILIATION_COMPLETE` within 30 seconds of `LOGON_ESTABLISHED` | ET-003-1 | Time delta: VenueStatus{CONNECTED} receipt → RecoveryCompleteEvent publication |
| AC-RC-003 | Missing fill detected: synthetic FillEvent with isSynthetic=true | ET-003-2, T-011 | isSynthetic flag true; portfolio updated |
| AC-RC-004 | Orphan order at venue triggers cancel command | ET-003-3, T-011 | CancelOrderCommand published for orphan |
| AC-RC-005 | Balance mismatch > tolerance triggers kill switch | ET-003-4, T-011 | killSwitchActive after mismatch |
| AC-RC-006 | Balance mismatch within tolerance: position adjusted, trading resumes | T-011 | adjustPosition called; recoveryLock cleared |
| AC-RC-007 | RecoveryCompleteEvent published after successful reconciliation | ET-003-1, T-011 | Event present on egress |
| AC-RC-008 | `REPLAY_DONE` before `RECONNECT_INITIATED` — archive replay completes before FIX reconnect is initiated (Scenario A: cluster restart) | ET-003-5 | Archive replay verifiably complete before Artio attempts TCP reconnect |
| AC-RC-009 | FIX session unchanged during cluster leader election | ET-003-5 | Simulator logs no disconnect during leader change |

## E.8 FIX Protocol Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-FX-001 | Coinbase Logon signature validates on sandbox | T-014, ET-001 | FIX logon succeeds (no logout with tag 58) |
| AC-FX-002 | NewOrderSingle contains all required tags from Section 9.5 | T-015 (simulator), ET-001 | Simulator decodes all 10 required tags |
| AC-FX-003 | OrderCancelRequest contains tags 11, 41, 37, 54, 55, 38 | T-015 (simulator), ET-001 | Simulator decodes 6 required tags |
| AC-FX-004 | Coinbase replace (MsgType=G) implemented as cancel+new | T-019 | No MsgType=G in FIX log; cancel followed by new |
| AC-FX-005 | ExecType=I (Order Status) does not trigger state transition | T-010 | Order state unchanged after ExecType=I |
| AC-FX-006 | ExecType=F + LeavesQty=0 sets isFinal=TRUE | T-010, ET-002-1 | isFinal=TRUE in SBE ExecutionEvent |
| AC-FX-007 | ExecType=F + LeavesQty>0 sets isFinal=FALSE | T-010, ET-002-2 | isFinal=FALSE for partial fills |

## E.9 Performance Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-PF-P-001 | Zero allocations on hot path after 200K-iteration warmup | T-009, T-010, T-030, T-031 | async-profiler --alloc: 0 b/op on hot methods |
| AC-PF-P-002 | Risk check completes in < 5µs (p99) | T-005, performance test | `System.nanoTime()` delta before/after |
| AC-PF-P-003 | Kill switch propagation < 10ms | ET-001-4 | Time delta activation → all cancels received at simulator within 10ms; ET-001-4 is the only test that measures this timing; ET-005-1 verifies trigger correctness only (not timing). Measured end-to-end from AdminCommand receipt at gateway to the last CancelOrderRequest arriving at the venue (simulator). |
| AC-PF-P-004 | Recovery completes < 30 seconds | ET-003-1 | Wall clock time |
| AC-PF-P-005 | No GC pause > 1ms during 5-minute load test (ZGC) | performance test | `-Xlog:gc*` log; no pause > 1ms |
| AC-PF-P-006 | OrderStatePool: no heap allocation for orders after warmup | T-006, T-007 | Pool available count stable; GC count stable |

## E.10 System Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-SY-001 | System starts and accepts FIX connection within 10 seconds | ET-006 | Startup time measured |
| AC-SY-002 | Graceful shutdown: all threads stop; no port leaks | ET-006 | Thread count returns to 0; netstat clean |
| AC-SY-003 | 3-node cluster: leader election completes within 500ms | ET-003-5 | Aeron Cluster election timer |
| AC-SY-004 | Admin kill switch deactivation via signed AdminCommand | ET-005-3, T-012 | Command with correct HMAC succeeds |
| AC-SY-005 | Admin command with wrong HMAC is rejected | T-012 | Command rejected; state unchanged |
| AC-SY-006 | Warmup harness must complete before FIX session initiated | ET-006 | ET-006 smoke test verifies "Warmup complete" log appears before FIX logon in startup sequence; also verified by TASK-033 done-when criterion |
| AC-SY-007 | Daily counters reset at midnight via cluster timer | T-013, ET (manual) | dailyPnl=0 after timer fires |

## E.11 Gateway Layer Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-GW-001 | MarketDataHandler stamps `ingressTimestampNanos` as first operation before any FIX tag decoding | T-016 | Timestamp field populated before all others in SBE output |
| AC-GW-002 | FIX MsgType=X incremental refresh: each entry published as separate MarketDataEvent SBE | T-016 | 3 entries in X → 3 GatewaySlot publishes |
| AC-GW-003 | Unknown FIX symbol: event discarded, WARN logged, no exception, no ring buffer publish | T-016 | DISRUPTOR_FULL_COUNTER unchanged; WARN in log |
| AC-GW-004 | Ring buffer full: market data tick discarded (artio-library thread not blocked) | T-016 | DISRUPTOR_FULL_COUNTER incremented; thread continues |
| AC-GW-005 | ExecutionReport tag 103 decoded only when ExecType='8'; zero on all other types | T-017 | rejectCode=0 for ExecType=F, =I, =4, =5 |
| AC-GW-006 | OrderCommandHandler: Artio back-pressure on send → retry 3× with 1µs sleep → reject event to cluster | T-019 | ARTIO_BACK_PRESSURE_COUNTER incremented after 3 failures |
| AC-GW-007 | ReplaceOrderCommand: cancel sent first; new order sent only after cancel ACK received | T-019, ET-002 | FIX log shows cancel before new; no new sent until ExecType=4 |

## E.12 Strategy Internal Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-ST-001 | MarketMakingStrategy uses lastTradePrice as fairValue fallback when spread > wideSpreadThreshold | T-009 | Quote generated at lastTradePrice basis when spread wide |
| AC-ST-002 | lastTradePrice updated only from EntryType=TRADE market data events | T-009 | Bid/Ask ticks do not update lastTradePriceScaled |
| AC-ST-003 | No lastTradePrice and wide spread: strategy suppresses quoting (does not crash) | T-009 | 0 orders submitted; no exception thrown |
| AC-ST-004 | Rejection counter incremented in onOrderRejected() | T-009 | consecutiveRejections increases by 1 per call |
| AC-ST-005 | 3 consecutive rejections → 5-second suppression window | T-009, ET-001 | suppressedUntilMicros set; 0 quotes during window |
| AC-ST-006 | Fill resets rejection counter to 0 | T-009 | consecutiveRejections == 0 after onFill() |
| AC-ST-007 | ArbStrategy leg timeout fires via cluster.scheduleTimer() | T-010 | Timer registered; cancel + hedge submitted on fire |
| AC-ST-008 | ArbStrategy slippage model: linear formula produces correct value for test vector | T-010 | Computed slippage matches expected scaled value |

## E.13 Fan-Out Ordering Acceptance Criteria

| AC-ID | Statement | Test Coverage | Measurable |
|---|---|---|---|
| AC-FO-001 | On ExecutionEvent: OrderManager.onExecution() called before PortfolioEngine.onFill() | T-018 | Call sequence verified with mock in-order recorder |
| AC-FO-002 | On ExecutionEvent: PortfolioEngine.onFill() called before RiskEngine.onFill() | T-018 | Call sequence verified with mock in-order recorder |
| AC-FO-003 | On ExecutionEvent: RiskEngine.onFill() called before StrategyEngine.onExecution() | T-018 | Call sequence verified with mock in-order recorder |
| AC-FO-004 | Non-fill ExecutionEvent: PortfolioEngine and RiskEngine not called | T-018 | 0 calls to onFill() on non-fill exec types (NEW, CANCELED, REJECTED) |
| AC-FO-005 | On MarketDataEvent: InternalMarketView.apply() called before StrategyEngine.onMarketData() | T-018, IT-002 | L2 book updated before strategy reads bestBid/bestAsk; verified by call-order recorder (T-018) and integration test reading live book values (IT-002) |

---

# PART F: TEST-TO-ACCEPTANCE-CRITERIA MAPPING

## F.1 Complete Mapping Table

| AC-ID | Primary Test | Secondary Tests | Gap if Any |
|---|---|---|---|
| AC-MM-001 | ET-001-1 | — | — |
| AC-MM-002 | ET-001-2 | T-009 (refresh logic) | — |
| AC-MM-003 | T-009 (bidAskNonCrossing) | ET-001-1 | — |
| AC-MM-004 | T-009 (inventorySkewLong) | ET-001-3 | — |
| AC-MM-005 | T-009 (inventorySkewShort) | — | — |
| AC-MM-006 | T-009 (zeroSize) | ET-001-7 | — |
| AC-MM-007 | ET-001-4 | T-005 (kill switch) | Time measurement required |
| AC-MM-008 | ET-001-4 | — | — |
| AC-MM-009 | ET-001-5 | T-009 (staleness) | — |
| AC-MM-010 | ET-001-6 | T-009 (wideSpread) | — |
| AC-MM-011 | T-009 (tickRounding) | — | — |
| AC-MM-012 | T-009 (lotRounding) | — | — |
| AC-MM-013 | T-009 (wrongVenueId) | — | — |
| AC-MM-014 | T-009 (refresh_priceUnchanged) | — | — |
| AC-MM-015 | ET-001-2 | T-009 (cancelBeforeNew) | — |
| AC-MM-016 | T-009 (clOrdId) | ET-001-1 | — |
| AC-MM-017 | T-009 (zeroAlloc) | Performance test | async-profiler required |
| AC-ARB-001 | T-010 (opportunityDetection) | ET-004-1 | Both spec test vectors |
| AC-ARB-002 | T-010 (belowThreshold) | — | — |
| AC-ARB-003 | T-010, IT-007 | — | Cluster side: single offer(); gateway side: two FIX sends |
| AC-ARB-004 | T-010 (leg1IsBuy) | ET-004-1 | — |
| AC-ARB-005 | T-010 (tifIOC) | ET-004-1 | — |
| AC-ARB-006 | T-010 (attemptIdLogPos) | — | Key determinism test |
| AC-ARB-007 | T-010 (hedgeOnImbalance) | ET-004-2 | Time measurement |
| AC-ARB-008 | T-010 (hedgeFail) | ET-004-3 | — |
| AC-ARB-009 | T-010 (activeArbIgnored) | — | — |
| AC-ARB-010 | T-010 (legTimeout) | ET-004-4 | Timeout fires; cancel + hedge submitted |
| AC-ARB-011 | T-010 (cooldown) | — | — |
| AC-OL-001 | T-007 (all transitions) | ET-002-1 | All 17 rows in table |
| AC-OL-002 | T-007 (invalidTransitions) | — | — |
| AC-OL-003 | T-007 (fillOnTerminal) | — | — |
| AC-OL-004 | T-007 (duplicateExecId) | — | — |
| AC-OL-005 | T-007 (clOrdIdUnique) | — | 10K order stress test |
| AC-OL-006 | T-007 (cancelAllOrders) | ET-001-4 | — |
| AC-OL-007 | T-006 (pool) | T-007 | — |
| AC-OL-008 | IT-005 | T-007 | — |
| AC-PF-001 | T-008 (avgPrice vector) | — | Exact value match |
| AC-PF-002 | T-008 (realizedPnlLong) | — | Exact value match |
| AC-PF-003 | T-008 (realizedPnlShort) | — | Exact value match |
| AC-PF-004 | T-008 (positionFlip) | — | — |
| AC-PF-005 | T-008 (netQtyZero) | — | — |
| AC-PF-006 | T-008 (unrealizedLong) | — | — |
| AC-PF-007 | T-008 (unrealizedShort) | — | — |
| AC-PF-008 | T-008, IT-004 | — | — |
| AC-PF-009 | T-024 (safeMulDiv_hiNonZero_1BtcAt65K_exact) | T-008 (overflow) | hi=0 and hi≠0 paths both covered |
| AC-PF-010 | IT-005 | T-008 | — |
| AC-RK-001 | T-005 (order) | — | All 8 checks ordered |
| AC-RK-002 | T-005 (killSwitch) | ET-005 | — |
| AC-RK-003 | T-005 (recoveryLock) | IT-003 | — |
| AC-RK-004 | T-005 (projectedPos) | — | Net position formula |
| AC-RK-005 | T-005 (rateWindow) | — | Sliding window expiry |
| AC-RK-006 | T-005 (dailyLoss) | ET-005-1 | — |
| AC-RK-007 | T-005 (softLimit) | — | — |
| AC-RK-008 | T-005 | T-013 | dailyPnl=0; killSwitch unchanged |
| AC-RK-009 | T-005 | Performance test | Timed assertion |
| AC-RK-010 | ET-005-3, T-012 | — | — |
| AC-RC-001 | ET-003-1 | T-011 | Order count during recovery window |
| AC-RC-002 | ET-003-1 | IT-006 | Timed assertion; IT-006 verifies timeout doesn't hang |
| AC-RC-003 | ET-003-2, T-011 | — | isSynthetic flag |
| AC-RC-004 | ET-003-3, T-011 | — | — |
| AC-RC-005 | ET-003-4, T-011 | — | — |
| AC-RC-006 | T-011 | — | — |
| AC-RC-007 | ET-003-1, T-011 | — | — |
| AC-RC-008 | ET-003-5 | — | 3-node required |
| AC-RC-009 | ET-003-5 | — | Simulator log check |
| AC-FX-001 | T-014 | ET-001 | Sandbox or simulator logon |
| AC-FX-002 | T-015 | ET-001 | All 10 tags verified |
| AC-FX-003 | T-015 | ET-001 | All 6 tags verified |
| AC-FX-004 | T-019 | — | No MsgType=G in FIX log |
| AC-FX-005 | T-010 | ET-002 | State unchanged |
| AC-FX-006 | T-010 | ET-002-2 | isFinal flag |
| AC-FX-007 | T-010 | ET-002-2 | isFinal flag |
| AC-PF-P-001 | T-009, T-010 | Performance test | async-profiler |
| AC-PF-P-002 | T-005 | Performance test | nanoTime delta |
| AC-PF-P-003 | ET-001-4 | — | 10ms timing; ET-005-1 is trigger-only |
| AC-PF-P-004 | ET-003-1 | — | s measurement |
| AC-PF-P-005 | Performance test | — | GC log analysis |
| AC-PF-P-006 | T-006, T-007 | Performance test | Pool count stable |
| AC-SY-001 | ET-006 | — | Startup timer |
| AC-SY-002 | ET-006 | — | Thread + port check |
| AC-SY-003 | ET-003-5 | — | Aeron election timer |
| AC-SY-004 | T-012 | ET-005-3 | — |
| AC-SY-005 | T-012 | — | — |
| AC-SY-006 | ET-006 | — | Log line ordering verified in smoke test |
| AC-SY-007 | T-013 | — | Timer fired check |
| AC-GW-001 | T-016 | — | — |
| AC-GW-002 | T-016 | — | — |
| AC-GW-003 | T-016 | — | — |
| AC-GW-004 | T-016 | — | — |
| AC-GW-005 | T-017 | — | — |
| AC-GW-006 | T-019 | IT-006 | Back-pressure: also verified at HTTP layer |
| AC-GW-007 | T-019 | ET-002 | — |
| AC-ST-001 | T-009 | — | — |
| AC-ST-002 | T-009 | — | — |
| AC-ST-003 | T-009 | ET-001 | Wide-spread with no lastTradePrice → 0 quotes + no crash |
| AC-ST-004 | T-009 | — | — |
| AC-ST-005 | T-009 | ET-001 | — |
| AC-ST-006 | T-009 | — | — |
| AC-ST-007 | T-010 | — | — |
| AC-ST-008 | T-010 | — | — |
| AC-FO-001 | T-018 | — | — |
| AC-FO-002 | T-018 | — | — |
| AC-FO-003 | T-018 | — | — |
| AC-FO-004 | T-018 | — | — |
| AC-FO-005 | T-018, IT-002 | — | Call order + live book value |

---

# PART G: TEST IMPLEMENTATION GUIDE FOR CORE JAVA DEVELOPERS

## G.1 Test Base Classes

Every test module should use these base classes.

```java
// For unit tests with no Aeron:
public abstract class UnitTestBase {

    // A fake cluster that provides predictable time and log position
    protected static final Cluster MOCK_CLUSTER = new MockCluster();
    protected static final long INITIAL_LOG_POS = 1_000_000L;
    protected static final long INITIAL_TIME_MICROS = 1_700_000_000_000_000L;

    // Pre-allocated SBE buffers for building test events
    protected final UnsafeBuffer testBuffer = new UnsafeBuffer(new byte[1024]);
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    protected static class MockCluster implements Cluster {
        private long logPosition = INITIAL_LOG_POS;
        private long time = INITIAL_TIME_MICROS;

        @Override public long logPosition() { return logPosition++; }
        @Override public long time() { return time += 100; }  // advance 100µs per call
        @Override public Publication egressPublication() { return CAPTURING_PUBLICATION; }
        @Override public boolean scheduleTimer(long id, long deadline) { return true; }
        // ... all other methods: no-op
    }

    // Captures egress SBE messages for assertion
    protected static final CapturingPublication CAPTURING_PUBLICATION = new CapturingPublication();
}

// For E2E tests:
public abstract class E2ETestBase {

    protected TradingSystemTestHarness harness;
    protected CoinbaseExchangeSimulator simulator;

    @BeforeEach
    void startSystem() {
        simulator = CoinbaseExchangeSimulator.builder()
            .port(19898)
            .fillMode(FillMode.IMMEDIATE)
            .initialMarket(65000.00, 65001.00)
            .build();
        simulator.start();

        harness = TradingSystemTestHarness.start(
            SimulatorConfig.forPort(19898),
            GatewayConfig.forTest(),
            ClusterNodeConfig.singleNodeForTest()
        );
    }

    @AfterEach
    void stopSystem() throws Exception {
        harness.close();
        simulator.stop();
    }
}
```

## G.2 SBE Test Helpers

```java
public final class SBETestHelper {

    public static void encodeMarketData(UnsafeBuffer buf,
                                         MessageHeaderEncoder hdr,
                                         int venueId, int instrumentId,
                                         byte entryType, long priceScaled, long sizeScaled) {
        MarketDataEventEncoder enc = new MarketDataEventEncoder();
        enc.wrapAndApplyHeader(buf, 0, hdr)
           .venueId(venueId)
           .instrumentId(instrumentId)
           .entryType(entryType)
           .updateAction(UpdateAction.NEW)
           .priceScaled(priceScaled)
           .sizeScaled(sizeScaled)
           .priceLevel(0)
           .ingressTimestampNanos(System.nanoTime())
           .exchangeTimestampNanos(System.nanoTime())
           .fixSeqNum(1);
    }

    public static void encodeExecution(UnsafeBuffer buf,
                                        MessageHeaderEncoder hdr,
                                        long clOrdId, int venueId, int instrumentId,
                                        byte execType, byte side,
                                        long fillPrice, long fillQty, long cumQty, long leavesQty,
                                        boolean isFinal) {
        ExecutionEventEncoder enc = new ExecutionEventEncoder();
        enc.wrapAndApplyHeader(buf, 0, hdr)
           .clOrdId(clOrdId)
           .venueId(venueId)
           .instrumentId(instrumentId)
           .execType(execType)
           .side(side)
           .fillPriceScaled(fillPrice)
           .fillQtyScaled(fillQty)
           .cumQtyScaled(cumQty)
           .leavesQtyScaled(leavesQty)
           .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
           .ingressTimestampNanos(System.nanoTime())
           .exchangeTimestampNanos(System.nanoTime());
        enc.putVenueOrderId("VEN-" + clOrdId);
        enc.putExecId("EXEC-" + clOrdId);
    }
}
```

## G.3 CapturingPublication

```java
/** Captures SBE messages published to cluster egress. For test assertions. */
public final class CapturingPublication implements Publication {

    private final List<byte[]> captured = new ArrayList<>();

    @Override
    public long offer(DirectBuffer buffer, int offset, int length) {
        byte[] copy = new byte[length];
        buffer.getBytes(offset, copy);
        captured.add(copy);
        return captured.size();  // simulate sequence number
    }

    public List<byte[]> getCaptured() { return captured; }

    public int countByTemplateId(int templateId) {
        MessageHeaderDecoder hdr = new MessageHeaderDecoder();
        return (int) captured.stream().filter(bytes -> {
            UnsafeBuffer buf = new UnsafeBuffer(bytes);
            hdr.wrap(buf, 0);
            return hdr.templateId() == templateId;
        }).count();
    }

    public <T extends MessageDecoderFlyweight> T getFirst(int templateId, T decoder) {
        // Find first captured message with given templateId; wrap decoder
        for (byte[] bytes : captured) {
            UnsafeBuffer buf = new UnsafeBuffer(bytes);
            MessageHeaderDecoder hdr = new MessageHeaderDecoder();
            hdr.wrap(buf, 0);
            if (hdr.templateId() == templateId) {
                decoder.wrap(buf, hdr.encodedLength(), hdr.blockLength(), hdr.version());
                return decoder;
            }
        }
        throw new AssertionError("No message with templateId: " + templateId);
    }

    public void clear() { captured.clear(); }
}
```

---

## G.4 GatewayConfig.forTest() — Test Factory

`GatewayConfig.forTest()` must be added as a static factory method on `GatewayConfig`. It is used by `TradingSystemTestHarness` (Part D.4) to configure the gateway process for E2E tests — no CPU pinning, small ring buffer, development-mode warmup, and loopback REST endpoint.

```java
// Add to GatewayConfig:
public static GatewayConfig forTest() {
    return GatewayConfig.builder()
        .venueId(Ids.VENUE_COINBASE_SANDBOX)
        .aeronDir("/dev/shm/aeron-test-" + ProcessHandle.current().pid())
        .fix(FixConfig.builder()
            .senderCompId("TEST_SENDER")
            .targetCompId("TEST_TARGET")
            .heartbeatIntervalS(5)
            .reconnectIntervalMs(1000)
            .artioLogDir("./build/test-artio-" + ProcessHandle.current().pid())
            .artioReplayCapacity(256)
            .build())
        .credentials(CredentialsConfig.hardcoded("test-key", "dGVzdA==", "test-pass"))
        .rest(RestConfig.builder()
            .baseUrl("http://localhost:18080")
            .pollIntervalMs(500)
            .timeoutMs(2000)
            .build())
        .cpu(CpuConfig.noPinning())
        .disruptor(DisruptorConfig.builder().ringBufferSize(256).slotSizeBytes(512).build())
        .warmup(WarmupConfig.development())   // 10K iterations — fast startup in tests
        .build();
}

// Add to CpuConfig:
public static CpuConfig noPinning() {
    return new CpuConfig(0, 0, 0, 0, 0);  // all zeros = no pinning
}
```

**`ProcessHandle.current().pid()`** in `aeronDir` ensures each test JVM uses a distinct Aeron directory, preventing interference when tests run in parallel.


---

## G.5 Unit Test → Class Mapping

Every unit test class maps to the primary class(es) it exercises. Classes in **bold** are the primary subject; others are collaborators mocked or used directly.

| Test Class | Primary Class(es) | Module |
|---|---|---|
| T-001 IdRegistryImplTest | **IdRegistryImpl** | platform-common |
| T-002 ConfigManagerTest | **ConfigManager**, ConfigValidationException | platform-common |
| T-003 OrderStatusTest | **OrderStatus**, **ScaledMath** | platform-common |
| T-004 L2OrderBookTest | **L2OrderBook**, InternalMarketView | platform-cluster |
| T-005 RiskEngineTest | **RiskEngineImpl** | platform-cluster |
| T-006 OrderStatePoolTest | **OrderStatePool**, OrderState | platform-cluster |
| T-007 OrderManagerTest | **OrderManagerImpl** | platform-cluster |
| T-008 PortfolioEngineTest | **PortfolioEngineImpl**, Position | platform-cluster |
| T-009 MarketMakingStrategyTest | **MarketMakingStrategy** | platform-cluster |
| T-010 ArbStrategyTest | **ArbStrategy** | platform-cluster |
| T-011 RecoveryCoordinatorTest | **RecoveryCoordinatorImpl**, RecoveryState | platform-cluster |
| T-012 AdminCommandHandlerTest | **AdminCommandHandler**, **AdminCommand** (SBE) | platform-cluster |
| T-013 DailyResetTimerTest | **DailyResetTimer** | platform-cluster |
| T-014 CoinbaseLogonStrategyTest | **CoinbaseLogonStrategy** | platform-gateway |
| T-015 CoinbaseExchangeSimulatorTest | **CoinbaseExchangeSimulator**, SimulatorConfig, SimulatorScenario, FillSchedule | platform-tooling |
| T-016 MarketDataHandlerTest | **MarketDataHandler** | platform-gateway |
| T-017 ExecutionHandlerTest | **ExecutionHandler** | platform-gateway |
| T-018 MessageRouterTest | **MessageRouter**, InternalMarketView | platform-cluster |
| T-019 OrderCommandHandlerTest | **OrderCommandHandler**, **ExecutionRouterImpl** | platform-gateway |
| T-020 StrategyEngineTest | **StrategyEngine** | platform-cluster |
| T-021 TradingClusteredServiceTest | **TradingClusteredService**, WarmupClusterShim | platform-cluster |
| T-022 RestPollerTest | **RestPoller** | platform-gateway |
| T-023 VenueStatusHandlerTest | **VenueStatusHandler** | platform-gateway |
| T-024 ScaledMathTest | **ScaledMath** | platform-common |


**Integration tests — classes under test:**

| Test Class | Classes Under Test | Scope |
|---|---|---|
| IT-001 GatewayDisruptorIntegrationTest | **GatewayDisruptor**, **AeronPublisher**, GatewaySlot | Multi-thread ring buffer |
| IT-002 L2BookToStrategyIntegrationTest | **InternalMarketView**, **MarketMakingStrategy**, MessageRouter | Market data → strategy dispatch |
| IT-003 RiskGateIntegrationTest | **RiskEngineImpl**, **OrderManagerImpl**, MessageRouter | Risk pre-trade → order lifecycle |
| IT-004 FillCycleIntegrationTest | **OrderManagerImpl**, **PortfolioEngineImpl**, **RiskEngineImpl** | Full fill → portfolio → risk update |
| IT-005 SnapshotRoundTripIntegrationTest | **OrderManagerImpl**, **PortfolioEngineImpl**, **RiskEngineImpl** | Snapshot write/load round-trip |
| IT-006 RestPollerIntegrationTest | **RestPoller** | HTTP concurrency and timeout |
| IT-007 ArbBatchParsingTest | **OrderCommandHandler**, **ExecutionRouterImpl** | Two SBE messages in one Aeron fragment → two FIX sends |


---

# 27. JIT OPTIMIZATION GUIDELINES

## 27.1 The JIT Predictability Problem

Java's JIT compiler is driven by internal heuristics — call counts, branch profiles, type
feedback, inlining budgets. These heuristics are not exposed, not guaranteed across JVM
versions, and not strictly deterministic. The synthetic warmup loop in Section 26 accumulates
enough call counts to trigger C2 compilation, but "enough" is a heuristic, not a guarantee.
Three specific risks remain even after a successful warmup:

**Deoptimization (deopt traps):** C2 compiles methods under assumptions. If an assumption
is violated at runtime — for example, a second concrete type appears where only one was seen
during warmup — the JVM discards the compiled code, falls back to interpreted mode, and
recompiles. This produces a latency spike of tens of milliseconds with no prior warning.
In a live trading session this is indistinguishable from a network issue unless
`-XX:+PrintCompilation` is active.

**Non-deterministic compilation timing:** The JIT compiler runs on background threads that
compete for CPU with business logic. Under load, compilation can be delayed past the end of
the warmup loop. A method may still be in C1 tier when the first real market data arrives
and transition to C2 mid-session, producing a brief latency blip.

**Profile invalidation after warmup reset:** `WarmupHarnessImpl` calls `resetWarmupState()`
after the warmup loop. This clears order and position state but does NOT affect JIT profiles
— compiled code remains compiled. This is correct. However, if warmup exercises code paths
that are not exercised in live trading (e.g. synthetic fills that trigger arb hedge logic
when arb is not configured), those compiled methods carry branch profiles that may not
match live behavior. Tailor the synthetic warmup events to match the configured strategy.

---

## 27.2 JVM Flags for Deterministic Warmup

Add the following flags to the production JVM launch scripts. These apply to both the
gateway process and each cluster node process.

```bash
# ---- Session 1 only (no ReadyNow profile yet) ----

# Force JIT compilation to happen synchronously on the calling thread during warmup.
# Without this flag, C2 compilation runs on a background thread — the warmup loop
# may complete before compilation finishes.
# Remove once ReadyNow is generating profiles — ReadyNow replays eagerly on startup
# and BackgroundCompilation would compete with it unnecessarily.
-XX:-BackgroundCompilation

# ---- All sessions (always active) ----

# Log every JIT compilation event. Redirect to a dedicated compile log file.
# Correlate with latency spike logs to diagnose deopt events and tier transitions.
-XX:+PrintCompilation

# Log deoptimization events specifically — these are the latency-spike events.
-XX:+TraceDeoptimization

# Lower C2 threshold from ~10K-15K to 5K invocations.
# Trade-off: faster C2 during initial warmup; marginally less optimised output.
# Verify with async-profiler that output quality is still sufficient.
-XX:CompileThreshold=5000
-XX:Tier4InvocationThreshold=5000

# Increase inline depth for the onSessionMessage hot chain.
# Default is 9; hot path is 6 levels deep (onSessionMessage → dispatch →
# onMarketData → Strategy → RiskEngine → egressPublication.offer).
# 15 gives headroom for the full chain to compile into one unit.
-XX:MaxInlineLevel=15
```

**Startup script pattern:**

```bash
#!/bin/bash
# cluster-node-start.sh

# Session 1 flag — remove after first ReadyNow profile is generated
FIRST_SESSION_FLAGS="-XX:-BackgroundCompilation"

JVM_PROD_FLAGS="-XX:+PrintCompilation -XX:+TraceDeoptimization \
                -XX:CompileThreshold=5000 -XX:Tier4InvocationThreshold=5000 \
                -XX:MaxInlineLevel=15"

# Redirect compile log — never mix with application log
java $FIRST_SESSION_FLAGS $JVM_PROD_FLAGS \
     -Xlog:jit+compilation*=info:file=logs/jit-$(date +%Y%m%d).log:time,level \
     -cp platform-cluster.jar \
     ig.rueishi.nitroj.exchange.cluster.ClusterMain \
     config/cluster-node-0.toml
```

---

## 27.3 Azul ReadyNow — Deterministic JIT Profile Persistence

This is the most significant JIT optimization available for production Java HFT. Azul
Platform Prime (the specified JVM) ships ReadyNow as a first-class feature of the platform
as of 2024–2025. The spec currently uses Azul Platform Prime for C4 GC only — ReadyNow
is the second essential feature and should not be treated as optional.

### What ReadyNow Does

ReadyNow records, during a live trading session, exactly which methods were compiled, at
what tier, with what inlining decisions, and with what branch profiles. It writes this
record to a `.rnp` profile file at session end (or on a configurable flush interval).

On the next startup, ReadyNow **replays** the profile: before `main()` is called, it
pre-compiles every method from the saved profile on background threads. By the time the
application starts, the entire hot path is already at C2 — the warmup loop's sole
remaining job is to populate caches, pre-fault memory pages, and stabilise branch
counters, not to drive JIT compilation.

**Result after the first production session:** every subsequent startup has a deterministic,
pre-compiled hot path within seconds. The risk of a deopt or late-compilation latency spike
approaches zero for any code path exercised in a prior session.

### Three-Generation Profile Training

A single session's profile may not cover all reachable hot paths — a quiet day may not
exercise the arb hedge path; an extreme volatility day may exercise unusual risk branches.
To build a robust profile, blend across multiple sessions:

```bash
# ReadyNow Orchestrator (bundled with Azul Platform Prime) — merge last 3 daily profiles:
readynow-orchestrator merge \
    --input  profiles/nitroj-$(hostname)-$(date -d '3 days ago' +%Y%m%d).rnp \
    --input  profiles/nitroj-$(hostname)-$(date -d '2 days ago' +%Y%m%d).rnp \
    --input  profiles/nitroj-$(hostname)-$(date -d '1 day ago' +%Y%m%d).rnp \
    --output profiles/nitroj-$(hostname)-merged.rnp \
    --strategy union   # union: include any method seen in ANY session
                       # intersection: only methods seen in ALL sessions (more conservative)

# Use merged profile for today's startup:
-XX:+UseReadyNow -XX:ReadyNowProfileFile=profiles/nitroj-$(hostname)-merged.rnp
```

Three generations (union) is the recommended baseline. Add a fourth (the oldest) if the
strategy has multiple operating modes that each appear only on certain days.

**Tip:** Run the merge step as a cron job at 06:00 UTC, before market open, so the merged
profile is ready before the cluster starts.

### Configuration

```bash
# Enable ReadyNow profile recording (add to all JVM launch flags):
-XX:+UseReadyNow
-XX:ReadyNowProfileFile=profiles/nitroj-$(hostname)-$(date +%Y%m%d).rnp

# On startup — load merged profile if available; fall back to yesterday's single profile:
MERGED=profiles/nitroj-$(hostname)-merged.rnp
YESTERDAY=profiles/nitroj-$(hostname)-$(date -d '1 day ago' +%Y%m%d).rnp

if [ -f "$MERGED" ]; then
    READYNOW_FLAGS="-XX:+UseReadyNow -XX:ReadyNowProfileFile=$MERGED"
elif [ -f "$YESTERDAY" ]; then
    READYNOW_FLAGS="-XX:+UseReadyNow -XX:ReadyNowProfileFile=$YESTERDAY"
else
    READYNOW_FLAGS="-XX:+UseReadyNow"  # Session 1: no profile yet; one will be created
fi

java $READYNOW_FLAGS $JVM_PROD_FLAGS ...
```

### Profile Management Rules

- **Never share profiles across hosts** with different CPU microarchitectures — ReadyNow
  profiles include CPU-specific optimizations (AVX2 vs SSE4.2, etc.). Each node generates
  its own profile.
- **Validate weekly:** `readynow-validate` (bundled with Azul Platform Prime) checks that
  the saved profile still matches the deployed bytecode. Stale profiles from a prior build
  version silently degrade to standard JIT behavior — not an error, but a missed optimization.
- **Profile rotation:** Keep the last 7 daily profiles per node. Delete older ones.
  Archive one representative profile per major code version for regression analysis.
- **After any deployment** that changes hot-path bytecode: delete the merged profile and
  let the first session after deployment regenerate it from scratch.

### ReadyNow Orchestrator

The ReadyNow Orchestrator is an Azul Platform Prime component (available as of 2024) that
automates the profile lifecycle — recording, merging, validating, and distributing profiles
across a cluster — without manual cron jobs.

```bash
# Start the Orchestrator sidecar alongside each cluster node:
readynow-orchestrator start \
    --profile-dir   profiles/ \
    --merge-window  3d \
    --merge-strategy union \
    --validate-on-load true \
    --distribute-to  node1:9900,node2:9900,node3:9900  # push merged profile to all peers
```

When the Orchestrator is active, manual merge scripts are unnecessary — it handles the
three-generation merge automatically after each session, validates the result, and
distributes it to all nodes before the next market open. Enable it in production; use
the manual merge script only in development or when the Orchestrator is unavailable.

### Interaction with `-XX:-BackgroundCompilation`

ReadyNow compiles methods eagerly on background threads before `main()` is called.
`-XX:-BackgroundCompilation` prevents background compilation — using both together means
ReadyNow's background compilation is also blocked, which defeats ReadyNow.

**Rule:**
- Session 1 (no profile): use `-XX:-BackgroundCompilation` — warmup loop drives synchronous C2
- Session 2+ (profile exists): remove `-XX:-BackgroundCompilation` — ReadyNow pre-compiles

```
Session 1 (no profile):   -XX:-BackgroundCompilation + warmup loop → profile written
Session 2+ (profile):     ReadyNow pre-compiles → warmup loop just populates caches
                          -XX:-BackgroundCompilation NOT set (would block ReadyNow)
```

---

## 27.4 Azul Cloud Native Compiler (CNC)

> **Available as of Azul Platform Prime 23.x (2024).** This section is forward-looking —
> CNC is production-ready but requires a separate CNC server process. It is an enhancement
> over ReadyNow, not a replacement. Evaluate after the system is stable on ReadyNow.

The Cloud Native Compiler is a remote compilation server that offloads C2 compilation from
the JVM process to a dedicated server (or cluster of servers). Compiled code is transmitted
back to the JVM as native code and installed without going through the JVM's own compiler
threads.

**Why it matters for NitroJEx:**
- JVM compiler threads compete with the cluster-service thread for CPU. On an isolated CPU
  set, the OS scheduler still occasionally schedules compiler threads on cores adjacent to
  trading cores, causing cache evictions. CNC eliminates this entirely — no compiler
  threads in the JVM process.
- CNC shares compiled code across all cluster nodes. If node 0 compiled `preTradeCheck()`,
  nodes 1 and 2 receive the same compiled artifact — no per-node compilation variance.
- CNC-compiled code is typically of equal or better quality than C2 output, since the
  CNC server runs without the JVM's compilation budget pressure.

**Setup (production reference):**

```bash
# Start CNC server (dedicated compilation host — not a trading node):
azul-cnc-server start \
    --listen-addr 0.0.0.0:9801 \
    --compiler-threads 8 \
    --cache-dir /var/cnc-cache \
    --cache-size 4GB

# Add to trading JVM flags:
-XX:+UseAzulCompiler \
-XX:AzulCompilerAddress=cnc-host:9801

# CNC + ReadyNow together: ReadyNow provides the profile;
# CNC compiles from that profile to native code;
# JVM installs the compiled code without running its own compiler threads.
```

**When NOT to use CNC:** If the CNC server is unavailable, the JVM automatically falls
back to standard C2 compilation — the system continues trading, just without the
off-process compilation benefit. Ensure the CNC server is monitored independently and
does not share hardware with trading nodes.

---

## 27.5 Compile Command Files

For specific methods where the JIT makes suboptimal inlining decisions, a compile command
file provides explicit overrides without recompiling the application. These are applied
regardless of whether CNC or standard C2 is used.

```bash
# File: config/hotspot_compiler
# Format: command class_pattern method_pattern

# Force inline the SBE decoder hot path — default inlining budget may exclude these
# if they appear too large (>35 bytecodes). These are called on every market data event.
inline ig/rueishi/nitroj/exchange/cluster/MessageRouter dispatch
inline ig/rueishi/nitroj/exchange/common/ScaledMath safeMulDiv
inline ig/rueishi/nitroj/exchange/cluster/RiskEngineImpl preTradeCheck

# Exclude methods that allocate from inlining — keeps the allocation-free assertion
# visible to the profiler. Do NOT inline BigDecimal paths onto the hot path.
dontinline ig/rueishi/nitroj/exchange/common/ScaledMath vwap
```

```bash
# Add to JVM flags:
-XX:CompileCommandFile=config/hotspot_compiler
```

**Review discipline:** Compile command overrides survive JVM version upgrades but may become
unnecessary or counterproductive if the JIT improves its heuristics. Review on every Azul
Platform Prime major version upgrade. Verify with async-profiler that overrides are not
introducing new allocation or extending the hot path.

---

## 27.6 Verifying C2 Compilation — Updated WarmupConfig

The existing `WarmupConfig.requireC2Verified()` check logs a warning if average iteration
time exceeds 500ns. This is improved with the following:

```java
// FIX AMB-002 (v8): This is the authoritative WarmupConfig definition. Section 18.5
// previously showed a 2-parameter record with production()=200,000 iterations.
// That definition is superseded by this class. Key differences:
//   - 3 parameters: iterations, requireC2Verified, thresholdNanos
//   - production() uses 100,000 iterations (not 200,000)
//   - thresholdNanos enables the avg-iteration check in verifyC2Compilation()
//   - Class (not record) so verifyC2Compilation() can be a private method
//
// See Section 18.5 for the updated class body (corrected in v8 to match this definition).
public final class WarmupConfig {

    // Production: 100,000 iterations.
    // With -XX:-BackgroundCompilation (Session 1): C2 guaranteed by ~15,000 iterations;
    // remaining 85,000 populate caches and stabilise branch profiles for ReadyNow.
    // With ReadyNow (Session 2+): already C2 from profile; all 100,000 run at peak speed.
    public static WarmupConfig production() {
        return new WarmupConfig(100_000, true, 500);  // 500ns threshold
    }

    // Development: 10,000 iterations — fast startup; C2 not fully triggered.
    public static WarmupConfig development() {
        return new WarmupConfig(10_000, false, 0);
    }

    private void verifyC2Compilation(long elapsedNanos, int iterations) {
        long avgNanos = elapsedNanos / iterations;
        if (avgNanos > thresholdNanos) {
            log.warn("Warmup avg iteration {}ns > threshold {}ns. " +
                     "C2 may not be fully compiled. " +
                     "Check: (1) -XX:-BackgroundCompilation set (Session 1); " +
                     "(2) ReadyNow profile loaded (Session 2+); " +
                     "(3) CNC server reachable if UseAzulCompiler is set. " +
                     "Do NOT go live until resolved.",
                     avgNanos, thresholdNanos);
        } else {
            log.info("Warmup verified: avg {}ns/iter — C2 compilation confirmed.", avgNanos);
        }
    }
}
```

**Acceptance criterion (add to T-021 TradingClusteredServiceTest):**

```java
@Test @Tag("SlowTest") void warmup_c2Verified_avgIterationBelowThreshold() {
    // Given: production warmup config (100,000 iterations, -XX:-BackgroundCompilation active)
    // And:   a WarmupHarnessImpl constructed with (service, WarmupConfig.production())
    //        and a FixLibrary reference obtained from the test harness
    // When:  runGatewayWarmup(fixLibrary) completes
    // Then:  average iteration time < 500ns
    // Excluded from standard ./gradlew test; runs in CI pre-release pipeline only.
    long start = System.nanoTime();
    harness.runGatewayWarmup(fixLibrary);
    long avgNs = (System.nanoTime() - start) / harness.config().iterations();
    assertThat(avgNs).isLessThan(500L);
}
```

---

## 27.7 Summary: JIT Optimization Stack

| Layer | Mechanism | When active | Benefit |
|---|---|---|---|
| Synthetic warmup | Section 26 — `WarmupHarness` | Every startup | Populates caches; stabilises branch profiles |
| Synchronous compilation | `-XX:-BackgroundCompilation` | Session 1 only | Guarantees C2 complete before go-live when no profile exists |
| ReadyNow | Profile persistence + three-gen merge via Orchestrator | Session 2+ | Deterministic pre-compiled startup; eliminates warmup non-determinism |
| CNC | Remote compilation server | Session 2+ (optional) | Removes compiler threads from JVM; shared compiled code across nodes |
| Compile commands | `hotspot_compiler` file | Always | Overrides suboptimal inlining on specific hot methods |
| Monitoring | `-XX:+PrintCompilation`, `-XX:+TraceDeoptimization` | Always (production) | Diagnoses mid-session deopt spikes |
| Verification | `WarmupConfig.requireC2Verified()` | Every startup | Confirms C2 complete; blocks go-live if not |

**Operational rule:** A cluster node that fails `requireC2Verified()` must not be promoted
to leader. It should log `FATAL: C2 not verified — node will not trade`, keep
`recoveryLock[all venues] = true`, and wait for operator intervention. Trading with an
unwarmed node silently degrades latency for the entire session.

---

# PART H: TASK ORDER SUMMARY

```
Week 1-2:  Foundation
  TASK-001  Project scaffold
  TASK-002  SBE schema + generation
  TASK-003  Constants (Ids, OrderStatus)
  TASK-004  Configuration (TOML)
  TASK-005  IdRegistry

Week 3:    Simulator (needed for all tests)
  TASK-006  CoinbaseExchangeSimulator

Week 4-5:  Gateway
  TASK-007  GatewayDisruptor
  TASK-008  CoinbaseLogonStrategy
  TASK-009  MarketDataHandler
  TASK-010  ExecutionHandler
  TASK-011  VenueStatusHandler
  TASK-012  AeronPublisher
  TASK-013  OrderCommandHandler + ExecutionRouter
  TASK-014  RestPoller
  TASK-015  ArtioLibraryLoop + EgressPollLoop

Week 5 (cont):  Gateway completion
  TASK-016  GatewayMain (wires all gateway components into runnable process)

Week 6-7:  Cluster Infrastructure
  TASK-017  InternalMarketView (L2 book)
  TASK-018  RiskEngine
  TASK-019  OrderStatePool
  TASK-020  OrderManager
  TASK-021  PortfolioEngine
  TASK-022  RecoveryCoordinator
  TASK-023  DailyResetTimer
  TASK-024  AdminCommandHandler
  TASK-025  MessageRouter
  TASK-026  TradingClusteredService
  TASK-027  ClusterMain

Week 8-9:  Strategies
  TASK-028  StrategyContext
  TASK-029  Strategy interface + StrategyEngine
  TASK-030  MarketMakingStrategy
  TASK-031  ArbStrategy

Week 10:   Admin + Tooling
  TASK-032  AdminCli + AdminClient
  TASK-033  WarmupHarness
  TASK-034  FIX Replay Tool

Week 11-12: Integration + E2E Testing
  IT-001 GatewayDisruptorIntegrationTest
  IT-002 L2BookToStrategyIntegrationTest
  IT-003 RiskGateIntegrationTest
  IT-004 FillCycleIntegrationTest
  IT-005 SnapshotRoundTripIntegrationTest
  IT-006 RestPollerIntegrationTest
  IT-007 ArbBatchParsingTest          ← arb dual-leg fragment parsing (M-2 fix)
  All ET-* tests
  Performance validation
  3-node cluster tests
```

**Total estimated effort: 12 weeks for 1 senior Java developer.**
**Recommended: 2 developers — one on gateway (Tasks 6-16), one on cluster (Tasks 17-27) — reduces to 8 weeks.**
