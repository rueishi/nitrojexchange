# NitroJEx — Execution-Ready Implementation Plan
**Source spec:** `NitroJEx_Master_Spec_V10.0.md` (authoritative golden source)
**Plan version:** 1.4.0
**Language:** Java 21 (LTS) · Build: Gradle 8 multi-module (4 modules per spec §21.3) · GC: ZGC (dev) / Azul C4 (prod)

---

# SECTION 1 — INTRODUCTION

This document contains execution-ready task cards for AI and human developers implementing NitroJEx.
Every task card is self-contained: a developer with only this file and the spec can implement every
card without re-opening the spec except for conflict resolution or edge-case deep-dives.

## 1.1 Scope Summary

Two JVM processes (Gateway + 3-node Aeron Cluster), two trading strategies (Market Making + Arb),
full pre-trade risk, order lifecycle, portfolio, crash-recovery, admin tooling, warmup harness,
FIX replay tool, and a CoinbaseExchangeSimulator for automated E2E tests.
Package prefix: `ig.rueishi.nitroj.exchange` — **replace with your firm prefix before first commit**.

## 1.2 Complete Acceptance Criteria — All 105 ACs

### E.2 Market Making
AC-MM-001 Two-sided quotes submitted within 500ms of first valid market data after startup
AC-MM-002 Quotes are requoted within 500ms when market moves > refreshThresholdBps
AC-MM-003 All quotes have bid price strictly less than ask price
AC-MM-004 Inventory skew formula: long position reduces bid and ask prices
AC-MM-005 Inventory skew formula: short position raises bid and ask prices
AC-MM-006 Zero-size quotes are never submitted
AC-MM-007 Kill switch cancels all live quotes within 10ms
AC-MM-008 Kill switch blocks all new quotes after activation
AC-MM-009 Market data stale suppression: quotes stop within 1 quote cycle
AC-MM-010 Wide spread suppression: no quotes when spread > maxTolerableSpreadBps
AC-MM-011 Prices rounded to tick size: bid rounded down, ask rounded up
AC-MM-012 Quote sizes rounded to lot size
AC-MM-013 Strategy only quotes on configured venueId and instrumentId
AC-MM-014 No redundant quotes: identical prices/sizes not resubmitted
AC-MM-015 Old quotes canceled before new quotes submitted on refresh
AC-MM-016 clOrdId is cluster.logPosition() — not random, not UUID
AC-MM-017 Zero allocations per onMarketData() call after warmup

### E.3 Arbitrage
AC-ARB-001 Opportunity formula includes taker fees and slippage before submitting
AC-ARB-002 Below minNetProfitBps threshold: no order submitted
AC-ARB-003 Both arb legs submitted in single Aeron offer call
AC-ARB-004 Leg 1 is always BUY on cheaper venue; Leg 2 is always SELL on expensive venue
AC-ARB-005 Both legs are IOC (TimeInForce=3)
AC-ARB-006 attemptId uses cluster.logPosition() — never UUID
AC-ARB-007 Leg imbalance > lotSize triggers hedge within 100ms
AC-ARB-008 Hedge failure triggers kill switch
AC-ARB-009 No new arb while one attempt is active
AC-ARB-010 Leg timeout: pending leg canceled and hedged within `legTimeoutClusterMicros`
AC-ARB-011 After hedge failure and kill switch activated, new arb attempts suppressed for `cooldownAfterFailureMicros` before resuming

### E.4 Order Lifecycle
AC-OL-001 All valid state transitions from Section 7.3 produce correct next state
AC-OL-002 Invalid state transitions are logged and do not change state
AC-OL-003 Fills on terminal orders are applied (revenue never discarded)
AC-OL-004 Duplicate execId (tag 17) is discarded with metrics increment
AC-OL-005 clOrdId is unique and monotonically increasing system-wide
AC-OL-006 cancelAllOrders() cancels every live (non-terminal) order
AC-OL-007 OrderState pool: zero new allocations after warmup
AC-OL-008 Snapshot/restore: all live OrderState fields match after restart

### E.5 Portfolio & PnL
AC-PF-001 VWAP average price correct for multiple buys (test vector)
AC-PF-002 Realized PnL correct on long close (test vector)
AC-PF-003 Realized PnL correct on short close (test vector)
AC-PF-004 Position correctly flips long → short (residual qty handled)
AC-PF-005 netQty = 0 after full close; avgEntryPrice reset to 0
AC-PF-006 Unrealized PnL correct for long position, mark above entry
AC-PF-007 Unrealized PnL correct for short position, mark below entry
AC-PF-008 RiskEngine notified after every fill
AC-PF-009 No arithmetic overflow for large prices and quantities
AC-PF-010 Snapshot/restore: positions and realized PnL match exactly

### E.6 Risk
AC-RK-001 Pre-trade checks fire in exact order defined in Section 12.2
AC-RK-002 Kill switch rejects all orders for all venues
AC-RK-003 Recovery lock rejects orders for affected venue only
AC-RK-004 Max position check uses net projected position
AC-RK-005 Rate limit: sliding window expires old orders correctly
AC-RK-006 Daily loss limit breach activates kill switch automatically
AC-RK-007 Soft limits publish alert without rejecting
AC-RK-008 Daily reset clears daily counters but not kill switch
AC-RK-009 preTradeCheck() completes in < 5µs (p99)
AC-RK-010 Kill switch deactivation requires operator admin command

### E.7 Recovery
AC-RC-001 No order submitted during recovery window for affected venue
AC-RC-002 `RECONCILIATION_COMPLETE` within 30 seconds of `LOGON_ESTABLISHED`
AC-RC-003 Missing fill detected: synthetic FillEvent with isSynthetic=true
AC-RC-004 Orphan order at venue triggers cancel command
AC-RC-005 Balance mismatch > tolerance triggers kill switch
AC-RC-006 Balance mismatch within tolerance: position adjusted, trading resumes
AC-RC-007 RecoveryCompleteEvent published after successful reconciliation
AC-RC-008 `REPLAY_DONE` before `RECONNECT_INITIATED` — archive replay completes before FIX reconnect is initiated (Scenario A: cluster restart)
AC-RC-009 FIX session unchanged during cluster leader election

### E.8 FIX Protocol
AC-FX-001 Coinbase Logon signature validates on sandbox
AC-FX-002 NewOrderSingle contains all required tags from Section 9.5
AC-FX-003 OrderCancelRequest contains tags 11, 41, 37, 54, 55, 38
AC-FX-004 Coinbase replace (MsgType=G) implemented as cancel+new
AC-FX-005 ExecType=I (Order Status) does not trigger state transition
AC-FX-006 ExecType=F + LeavesQty=0 sets isFinal=TRUE
AC-FX-007 ExecType=F + LeavesQty>0 sets isFinal=FALSE

### E.9 Performance
AC-PF-P-001 Zero allocations on hot path after 200K-iteration warmup
AC-PF-P-002 Risk check completes in < 5µs (p99)
AC-PF-P-003 Kill switch propagation < 10ms
AC-PF-P-004 Recovery completes < 30 seconds
AC-PF-P-005 No GC pause > 1ms during 5-minute load test (ZGC)
AC-PF-P-006 OrderStatePool: no heap allocation for orders after warmup

### E.10 System
AC-SY-001 System starts and accepts FIX connection within 10 seconds
AC-SY-002 Graceful shutdown: all threads stop; no port leaks
AC-SY-003 3-node cluster: leader election completes within 500ms
AC-SY-004 Admin kill switch deactivation via signed AdminCommand
AC-SY-005 Admin command with wrong HMAC is rejected
AC-SY-006 Warmup harness must complete before FIX session initiated
AC-SY-007 Daily counters reset at midnight via cluster timer

### E.11 Gateway Layer
AC-GW-001 MarketDataHandler stamps `ingressTimestampNanos` as first operation before any FIX tag decoding
AC-GW-002 FIX MsgType=X incremental refresh: each entry published as separate MarketDataEvent SBE
AC-GW-003 Unknown FIX symbol: event discarded, WARN logged, no exception, no ring buffer publish
AC-GW-004 Ring buffer full: market data tick discarded (artio-library thread not blocked)
AC-GW-005 ExecutionReport tag 103 decoded only when ExecType='8'; zero on all other types
AC-GW-006 OrderCommandHandler: Artio back-pressure on send → retry 3× with 1µs sleep → reject event to cluster
AC-GW-007 ReplaceOrderCommand: cancel sent first; new order sent only after cancel ACK received

### E.12 Strategy Internal
AC-ST-001 MarketMakingStrategy uses lastTradePrice as fairValue fallback when spread > wideSpreadThreshold
AC-ST-002 lastTradePrice updated only from EntryType=TRADE market data events
AC-ST-003 No lastTradePrice and wide spread: strategy suppresses quoting (does not crash)
AC-ST-004 Rejection counter incremented in onOrderRejected()
AC-ST-005 3 consecutive rejections → 5-second suppression window
AC-ST-006 Fill resets rejection counter to 0
AC-ST-007 ArbStrategy leg timeout fires via cluster.scheduleTimer()
AC-ST-008 ArbStrategy slippage model: linear formula produces correct value for test vector

### E.13 Fan-Out Ordering
AC-FO-001 On ExecutionEvent: OrderManager.onExecution() called before PortfolioEngine.onFill()
AC-FO-002 On ExecutionEvent: PortfolioEngine.onFill() called before RiskEngine.onFill()
AC-FO-003 On ExecutionEvent: RiskEngine.onFill() called before StrategyEngine.onExecution()
AC-FO-004 Non-fill ExecutionEvent: PortfolioEngine and RiskEngine not called
AC-FO-005 On MarketDataEvent: InternalMarketView.apply() called before StrategyEngine.onMarketData()


---

# SECTION 2 — RECOMMENDED IMPLEMENTATION ORDER

Cards that share a row can execute in parallel. Every card lists its hard blockers.

| Week | Task ID | Task Name | Blocked By |
|---|---|---|---|
| 1 | TASK-001 | Project Scaffold | — |
| 1 | TASK-002 | SBE Schema + Code Generation | TASK-001 |
| 1 | TASK-003 | Constants (Ids, OrderStatus, ScaledMath) | TASK-001 |
| 1 | TASK-004 | Configuration (TOML + ConfigManager) | TASK-001, TASK-003 |
| 1 | TASK-005 | IdRegistry | TASK-001, TASK-003, TASK-004 |
| 2 | TASK-006 | CoinbaseExchangeSimulator | TASK-002, TASK-004 |
| 3 | TASK-007 | GatewayDisruptor + GatewaySlot | TASK-002, TASK-003 |
| 3 | TASK-008 | CoinbaseLogonStrategy | TASK-004, TASK-005 |
| 3 | TASK-009 | MarketDataHandler | TASK-002, TASK-005, TASK-007 |
| 3 | TASK-010 | ExecutionHandler | TASK-002, TASK-005, TASK-007 |
| 3 | TASK-011 | VenueStatusHandler | TASK-002, TASK-005, TASK-007 |
| 4 | TASK-012 | AeronPublisher | TASK-002, TASK-007 |
| 4 | TASK-013 | OrderCommandHandler + ExecutionRouter | TASK-002, TASK-005, TASK-008, TASK-010 |
| 4 | TASK-014 | RestPoller | TASK-002, TASK-004, TASK-005, TASK-007 |
| 4 | TASK-015 | ArtioLibraryLoop + EgressPollLoop | TASK-001, TASK-013 |
| 5 | TASK-016 | GatewayMain | TASK-004, TASK-005, TASK-006 through TASK-015 |
| 5 | TASK-017 | InternalMarketView (L2 Book) | TASK-002, TASK-003 |
| 5 | TASK-018 | RiskEngine | TASK-002, TASK-003, TASK-004 |
| 5 | TASK-019 | OrderStatePool | TASK-003 |
| 6 | TASK-020 | OrderManager | TASK-002, TASK-003, TASK-019 |
| 6 | TASK-021 | PortfolioEngine | TASK-002, TASK-003, TASK-018 |
| 6 | TASK-022 | RecoveryCoordinator | TASK-002, TASK-018, TASK-020, TASK-021 |
| 6 | TASK-023 | DailyResetTimer | TASK-018, TASK-021 |
| 6 | TASK-024 | AdminCommandHandler | TASK-002, TASK-003, TASK-018 |
| 6 | TASK-025 | MessageRouter | TASK-002, TASK-017, TASK-018, TASK-020, TASK-021, TASK-022, TASK-024 |
| 7 | TASK-026 | TradingClusteredService | TASK-018, TASK-020, TASK-021, TASK-022, TASK-023, TASK-024, TASK-025, TASK-029 |
| 9 | TASK-027 | ClusterMain | TASK-004, TASK-005, TASK-017, TASK-018, TASK-019, TASK-020, TASK-021, TASK-022, TASK-023, TASK-024, TASK-025, TASK-026, TASK-028, TASK-029, TASK-030, TASK-031 |
| 7 | TASK-028 | StrategyContext | TASK-002, TASK-005, TASK-017, TASK-018, TASK-020, TASK-021, TASK-022 |
| 8 | TASK-029 | Strategy Interface + StrategyEngine | TASK-002, TASK-028, TASK-024 (StrategyEngineControl interface) |
| 8 | TASK-030 | MarketMakingStrategy | TASK-002, TASK-003, TASK-004, TASK-017, TASK-018, TASK-020, TASK-028, TASK-029 |
| 8 | TASK-031 | ArbStrategy | TASK-002, TASK-003, TASK-004, TASK-017, TASK-018, TASK-028, TASK-029 |
| 9 | TASK-032 | AdminCli + AdminClient | TASK-002, TASK-004, TASK-024 |
| 9 | TASK-033 | WarmupHarness | TASK-002, TASK-003, TASK-016 (interface), TASK-026 (impl deps) |
| 9 | TASK-034 | FIX Replay Tool | TASK-001, TASK-002 |
| 10–11 | IT-001 | GatewayDisruptorIntegrationTest | TASK-012 |
| 10–11 | IT-002 | L2BookToStrategyIntegrationTest | TASK-017, TASK-030 |
| 10–11 | IT-003 | RiskGateIntegrationTest | TASK-018, TASK-020, TASK-025 |
| 10–11 | IT-004 | FillCycleIntegrationTest | TASK-020, TASK-021, TASK-018 |
| 10–11 | IT-005 | SnapshotRoundTripIntegrationTest | TASK-020, TASK-021, TASK-022 |
| 10–11 | IT-006 | RestPollerIntegrationTest | TASK-014 |
| 10–11 | IT-007 | ArbBatchParsingTest | TASK-013 |
| 12 | ET-001–006 | All E2E Test Suites | TASK-016, TASK-027, TASK-033 |

**Parallel opportunities:**
- TASK-003 and TASK-004 are independent of each other and can run simultaneously with TASK-002 once TASK-001 is done. TASK-005 consumes both, so it runs last in Week 1 (still fits the week-1 budget because TASK-003 and TASK-004 are short).
- TASK-007 through TASK-011 (gateway handlers) can all run in parallel.
- TASK-017 through TASK-024 (cluster components) can mostly run in parallel.
- TASK-030 and TASK-031 can run in parallel after TASK-029 (V9.9: `Strategy` is a plain interface, so no sealed-permits co-delivery constraint).
- TASK-027, TASK-032, TASK-033, TASK-034 can all run in parallel in Week 9. TASK-027 (`ClusterMain`) is at Week 9 rather than Week 7 because its `buildClusteredService()` factory instantiates concrete `MarketMakingStrategy` and `ArbStrategy` (TASK-030/031, Week 8).

**Recommended team split (2 developers, 9-week target):**
- Developer A: TASK-001 through TASK-016 (gateway + scaffold)
- Developer B: TASK-017 through TASK-031 (cluster infrastructure + strategies)
- Both: TASK-027 (ClusterMain integration), TASK-032, TASK-033, TASK-034 in Week 9; integration and E2E tests in Weeks 10–11.


---

# SECTION 3 — STANDARD EXECUTION RULES

These rules apply to every task card without exception.

## 3.1 Branch Naming
```
feature/TASK-NNN-short-description     # new work
fix/TASK-NNN-short-description         # bug fix in an existing card
test/TASK-NNN-short-description        # test-only changes
```

## 3.2 Commit Message Format
```
TASK-NNN: <imperative summary, ≤72 chars>

[optional body — why, not what]
[AC-ID list if this commit closes an AC: Closes: AC-MM-001, AC-MM-003]
```

## 3.3 Test Coverage Minimum
- Unit tests (T-*): **≥ 90% line coverage** on the primary class(es) under test.
- Integration tests (IT-*): cover all happy-path and at least one failure path per component boundary.
- E2E tests (ET-*): correctness assertions always required; timing assertions (< 10ms, < 500ms) are
  tagged `@RequiresProductionEnvironment` and skipped in CI loopback unless explicitly enabled.
- `@Tag("SlowTest")` on any test running > 5 seconds. Slow tests excluded from `./gradlew test`;
  included in `./gradlew ciTest` (pre-release pipeline).

## 3.4 Linting and Formatting
- Checkstyle: Google Java Style with max line length 120.
- `./gradlew check` must pass before any PR merge (Checkstyle + tests).
- No `@SuppressWarnings("unchecked")` or `@SuppressWarnings("rawtypes")` without a comment.
- Hot-path methods must not contain `new`, autoboxing, lambda captures, or `String` concatenation
  (verified by a custom Checkstyle rule that fails on these patterns in classes annotated `@HotPath`).

## 3.5 Definition of "A Passing Card"
A card is **complete** when all of the following are true:
1. All files listed in "Expected Modified Files" have been created or modified.
2. All files listed in "Do Not Modify" are unchanged (verified by `git diff --name-only`).
3. Every AC listed in the card's "Acceptance Criteria" section is verified by a passing test.
4. `./gradlew check` passes with zero errors and zero warnings on the affected module(s).
5. No regression: the full test suite (`./gradlew test`) passes green.
6. The "Completion Checklist" is fully checked.

## 3.6 Handling a Spec Ambiguity Discovered Mid-Execution
1. **Stop.** Do not silently resolve the ambiguity.
2. Check `NitroJEx_Master_Spec_V10.0.md` — the changelog and `// FIX AMBxxx` comments may already
   address it.
3. If not addressed in V9: document the ambiguity in a comment in your branch with format
   `// UNRESOLVED-AMB: <description> — blocked pending spec owner decision`.
4. Raise a spec issue with: location, description, affected ACs, and a proposed minimal resolution.
5. Do not merge the card until the ambiguity is resolved or explicitly deferred.


---

# SECTION 4 — TASK CARDS

---

## Task Card TASK-001: Project Scaffold

### Task Description
Create the complete Gradle 8 multi-module build, folder structure, Docker Compose file,
CI skeleton, and production JVM startup scripts. No business logic. After this card,
all other cards can compile their modules independently.

### Spec References
Spec §19.1 (docker-compose), §18.3 (JVM flags), §18.4 (GC config), §22 (TOML files overview),
§27.2 (JIT flags), §27.5 (compile command file).

### Required Existing Inputs
None. This is the root card.

### Expected Modified Files
```
build.gradle.kts                           (root — dependency versions, subprojects block)
settings.gradle.kts                        (declares 4 modules per spec §21.3)
gradle/wrapper/gradle-wrapper.properties
gradle/wrapper/gradle-wrapper.jar
gradlew
gradlew.bat
platform-common/build.gradle.kts
platform-gateway/build.gradle.kts
platform-cluster/build.gradle.kts
platform-tooling/build.gradle.kts
config/hotspot_compiler
config/cluster-node-0.toml   (skeleton — populated by TASK-004)
config/cluster-node-1.toml   (skeleton)
config/cluster-node-2.toml   (skeleton)
config/gateway-1.toml        (skeleton)
config/venues.toml            (skeleton)
config/instruments.toml       (skeleton)
config/admin.toml             (skeleton)
docker-compose.yml
scripts/cluster-node-start.sh
scripts/gateway-start.sh
.github/workflows/ci.yml      (or equivalent)
```

### Do Not Modify
Nothing else exists yet.

### Related Files (Read-Only Reference)
`NitroJEx_Master_Spec_V10.0.md` §19 (Deployment Model), §27.2 (JVM flags).

### Authoritative Spec Excerpts

**Module layout (Spec §21.3 — exactly 4 subprojects):**
```
platform-common/        SBE schemas + generated decoders/encoders; shared Ids, OrderStatus,
                        ScaledMath, ConfigManager, IdRegistry. Shared test utilities
                        (SbeTestMessageBuilder, CapturingPublication) live under src/test.
platform-gateway/       Gateway process (Artio + Aeron publisher)
platform-cluster/       Cluster process (Aeron Cluster + all business logic)
platform-tooling/       Simulator, WarmupHarnessImpl, AdminCli, FIXReplayTool, FIX replay,
                        TradingSystemTestHarness (shared integration/E2E harness)
```

**Pinned dependency versions (Spec §21.1 — "Do not upgrade without full regression"):**
| Library | Group | Artifact | Version |
|---|---|---|---|
| Aeron | `io.aeron` | `aeron-all` / `aeron-cluster` / `aeron-archive` | `1.50.0` |
| Agrona | `org.agrona` | `agrona` | `2.4.0` |
| Artio | `uk.co.real-logic` | `artio-core` / `artio-codecs` | `0.175` |
| SBE Tool | `uk.co.real-logic` | `sbe-tool` / `sbe-all` | `1.37.1` |
| LMAX Disruptor | `com.lmax` | `disruptor` | `4.0.0` |
| Eclipse Collections | `org.eclipse.collections` | `eclipse-collections` | `11.1.0` |
| HdrHistogram | `org.hdrhistogram` | `HdrHistogram` | `2.2.2` |
| JUnit 5 | `org.junit.jupiter` | `junit-jupiter` | `5.10.2` |
| AssertJ | `org.assertj` | `assertj-core` | `3.25.3` |
| night-config (TOML) | `com.electronwill.night-config` | `toml` | `3.6.7` |

**Aeron ↔ Artio compatibility:** Artio `0.175` is built against Aeron `1.50.0` and Agrona `2.4.0`. These three versions form a verified compatible set per the `artio-codecs` 0.175 POM on Maven Central. Never mix versions without checking Artio's published POM for its declared Aeron dependency.

**Why night-config, not toml4j:** Spec V10.0 §21.1 pins `night-config:toml:3.6.7` in place of `toml4j:0.7.2` (which has had no releases since 2019 and mis-handles arrays of tables — a construct used by `venues.toml`). night-config is actively maintained and TOML 1.0 compliant. Parser class: `com.electronwill.nightconfig.toml.TomlParser` (not `com.moandjiezana.toml.Toml`).

**Root `build.gradle.kts` — key elements (Spec §21.2):**
```kotlin
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
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

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

    // Dedicated E2E source set — runs only via ./gradlew e2eTest
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
    // Pre-release pipeline task: includes @SlowTest but still excludes @E2E and @RequiresProductionEnvironment
    tasks.register<Test>("ciTest") {
        description = "Pre-release CI test suite — includes SlowTest, excludes E2E and production-only tests"
        group = "verification"
        useJUnitPlatform {
            excludeTags("E2E")
            excludeTags("RequiresProductionEnvironment")
            // SlowTest is INCLUDED here (unlike the default `test` task)
        }
    }
    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("E2E")                           // E2E via e2eTest task only
            excludeTags("SlowTest")                      // SlowTest runs in pre-release pipeline only
            excludeTags("RequiresProductionEnvironment") // colocation hardware only (see §20.4)
        }
    }
}
```

**hotspot_compiler (compile command file — Spec §27.5):**
```
inline ig/rueishi/nitroj/exchange/cluster/MessageRouter dispatch
inline ig/rueishi/nitroj/exchange/common/ScaledMath safeMulDiv
inline ig/rueishi/nitroj/exchange/cluster/RiskEngineImpl preTradeCheck
dontinline ig/rueishi/nitroj/exchange/common/ScaledMath vwap
```

**Production JVM flags (cluster-node-start.sh — Spec §27.2):**
```bash
FIRST_SESSION_FLAGS="-XX:-BackgroundCompilation"
JVM_PROD_FLAGS="-XX:+PrintCompilation -XX:+TraceDeoptimization \
                -XX:CompileThreshold=5000 -XX:Tier4InvocationThreshold=5000 \
                -XX:MaxInlineLevel=15 \
                -XX:CompileCommandFile=config/hotspot_compiler \
                -XX:-RestrictContended \
                --enable-native-access=ALL-UNNAMED"
```

### Implementation Guidelines
- Dependency versions are declared in the **root** `build.gradle.kts` per Spec §21.2 (no separate BOM module — Spec §21.3 defines exactly 4 subprojects).
- Every module's `build.gradle.kts` must declare `useJUnitPlatform()` in the test block (inherited from the `subprojects` block above).
- SBE code generation lives in `platform-common` via the SBE Gradle plugin — other modules depend on the generated sources (see TASK-002).
- `@jdk.internal.vm.annotation.Contended` requires `-XX:-RestrictContended` — already present in the `subprojects` JVM args above; also required in production startup scripts.
- Panama off-heap requires `--enable-native-access=ALL-UNNAMED` — already present above; also required in production startup scripts.
- The `config/hotspot_compiler` file must be in the repo root and referenced by the startup scripts via `-XX:CompileCommandFile=config/hotspot_compiler`.
- The `e2eTest` source set is mandatory (Spec TASK-001) — it isolates E2E tests from `./gradlew test` so CI passes fast by default.

### Acceptance Criteria
- AC-PF-P-005: No GC pause > 1ms during 5-minute load test (ZGC). This card establishes the ZGC and C4 configuration in the startup scripts that makes this criterion achievable.

### INPUT → OUTPUT Paths
- **Positive path:** `./gradlew build` compiles all modules with zero errors.
- **Negative path:** Missing dependency version in BOM → compile error with clear module/artifact reference.
- **Failure path:** Docker Compose `media-driver` fails to start → gateway and cluster containers fail with clear log message.

### Implementation Steps
1. Create `settings.gradle.kts` declaring exactly 4 modules: `platform-common`, `platform-gateway`, `platform-cluster`, `platform-tooling` (Spec §21.3).
2. Create root `build.gradle.kts` with the pinned dependency versions (Spec §21.1), `allprojects`/`subprojects` blocks, and the `e2eTest` source set + task definition per Spec §21.2 / TASK-001.
3. Create per-module `build.gradle.kts` with correct inter-module dependencies. Generated SBE output lives at `platform-common/src/generated/java` (Spec §21.3, §21.4).
4. Create `config/` directory with skeleton TOML files (populated content in TASK-004).
5. Create `config/hotspot_compiler` with the 4 directives from Spec §27.5.
6. Create `docker-compose.yml` per Spec §19.1 (media-driver, gateway-coinbase, cluster-node-0/1/2).
7. Create `scripts/cluster-node-start.sh` and `scripts/gateway-start.sh` with JVM flags from Spec §27.2.
8. Create `.github/workflows/ci.yml` running `./gradlew check` on push; `./gradlew e2eTest` in a separate optional stage.
9. Verify `./gradlew e2eTest` task is registered (runs zero tests until TASK-016 wires the harness).
10. Verify Java 21 toolchain via `./gradlew -version` and `./gradlew :platform-common:dependencies`.

### Test Implementation Steps
No tests for this card. Verification: `./gradlew build` green; `docker-compose up --no-start` succeeds without errors.

### Out of Scope
No business logic. No SBE schemas (TASK-002). No TOML content (TASK-004).

### Completion Checklist
- [ ] Files changed: settings.gradle.kts, root build.gradle.kts, 4 per-module build.gradle.kts files, config skeletons, docker-compose.yml, startup scripts, CI config
- [ ] Tests added/updated: none
- [ ] All ACs verified: AC-PF-P-005 (configuration established)
- [ ] Assumptions made: Gradle 8.x wrapper included; JDK 21 available in CI
- [ ] Blockers identified: none
- [ ] Follow-up items: Populate TOML content (TASK-004); add SBE plugin (TASK-002); verify `./gradlew e2eTest` task is registered and runs 0 tests.

---

## Task Card TASK-002: SBE Schema + Code Generation

### Task Description
Author the complete SBE message schema XML covering all 14 wire messages between gateway
and cluster (templateIds 1–4, 10–11, 20–22, 30–34) **plus the 3 snapshot messages used by
recovery** (Spec §16.7). Configure the SBE Gradle plugin to generate Java encoders/decoders
into `platform-common/src/generated/java`. All generated classes become inputs for every
subsequent task.

### Spec References
Spec §8 (all subsections 8.1–8.13) — wire messages; Spec §16.7 — snapshot messages;
Spec §21.4 — SBE Gradle task configuration.

### Required Existing Inputs
- `TASK-001`: `platform-common/build.gradle.kts`, `settings.gradle.kts`, root `build.gradle.kts`

### Expected Modified Files
```
platform-common/src/main/resources/messages.xml            (Spec §21.3 path — not sbe-schemas/)
platform-common/build.gradle.kts                            (add SBE Gradle task per §21.4)
```

### Do Not Modify
All other source files from TASK-001.

### Related Files (Read-Only Reference)
Spec §8 (schemas), §3.4.3 (templateId constants used by MessageRouter).

### Authoritative Spec Excerpts

**All wire message types (templateId → name → direction), per Spec §8.2–§8.13:**
```
 1  MarketDataEvent          Gateway → Cluster         (Spec §8.2)
 2  ExecutionEvent           Gateway → Cluster         (Spec §8.3; varString venueOrderId + execId)
 3  VenueStatusEvent         Gateway → Cluster         (Spec §8.4)
 4  BalanceQueryResponse     Gateway → Cluster         (Spec §8.5)
10  NewOrderCommand          Cluster → Gateway         (Spec §8.6 — fixed-length, no varString)
11  CancelOrderCommand       Cluster → Gateway         (Spec §8.7 — varString venueOrderId)
20  FillEvent                Cluster internal / audit  (Spec §8.8)
21  PositionEvent            Cluster → metrics         (Spec §8.8)
22  RiskEvent                Cluster → egress (ops)    (Spec §8.8)
30  OrderStatusQueryCommand  Cluster → Gateway         (Spec §8.9 — varString venueOrderId)
31  BalanceQueryRequest      Cluster → Gateway         (Spec §8.10)
32  RecoveryCompleteEvent    Cluster → Gateway         (Spec §8.11)
33  AdminCommand             External → Cluster        (Spec §8.12 — via gateway admin channel)
34  ReplaceOrderCommand      Cluster → Gateway         (Spec §8.13 — varString venueOrderId)
```

**Snapshot messages (Spec §16.7) — required for TASK-020 / TASK-021 / TASK-018 snapshot/restore:**
```
50  OrderStateSnapshot       Cluster → Archive         (AC-OL-008, IT-005)
51  PositionSnapshot         Cluster → Archive         (AC-PF-010, IT-005)
52  RiskStateSnapshot        Cluster → Archive         (IT-005)
```

**Total: 14 wire messages + 3 snapshot messages = 17 encoder/decoder pairs.** See Spec TASK-002 "Generated files" list for the full enumeration.

**Critical: messages with varString `venueOrderId` (templateIds 2, 11, 30, 34) require
`encoder.encodedLength()` — NOT `blockLen` — for cursor advancement. See Spec §2.2.4.**

**Enum types required in `<types>` section (Spec §8.1):**
Side (BUY=1, SELL=2), OrdType (MARKET=1, LIMIT=2), TimeInForce (DAY=0, GTC=1, IOC=3, FOK=4),
ExecType (NEW=0, PARTIAL_FILL=1, CANCELED=4, REPLACED=5, REJECTED=8, EXPIRED=12, FILL=15, ORDER_STATUS=9),
EntryType (BID=0, ASK=1, TRADE=2), UpdateAction (NEW=0, CHANGE=1, DELETE=2),
VenueStatus (CONNECTED=1, DISCONNECTED=2), BooleanType (FALSE=0, TRUE=1),
RiskEventType (KILL_SWITCH_ACTIVATED=1 ... ORDER_STATE_ERROR=7),
AdminCommandType (DEACTIVATE_KILL_SWITCH=1 ... TRIGGER_SNAPSHOT=6).

**Timestamp types — two distinct sources (Spec §8.1):**
```xml
<type name="gatewayNanos"  primitiveType="int64"/>  <!-- System.nanoTime() at gateway only -->
<type name="clusterMicros" primitiveType="int64"/>  <!-- cluster.time() at cluster only -->
```
These must never be mixed. Convert `clusterMicros × 1000` for nanosecond-scaled value (no sub-microsecond precision added).

### Implementation Guidelines
- Use SBE schema `id="1"`, `version="1"`, `byteOrder="littleEndian"`, `package="ig.rueishi.nitroj.exchange.messages"` (Spec §8.1).
- All `<field>` elements with `type="varStringEncoding"` require a `varStringEncoding` composite type in `<types>`.
- SBE generation runs via a Gradle `JavaExec` task named `generateSBE`, configured per Spec §21.4. Output goes to `platform-common/src/generated/java` (not `build/generated-src/sbe/` — Spec §21.3 places generated sources inside the module's `src/` tree so they're visible in IDE source tabs).
- Wire the task into the build: `tasks.named("compileJava") { dependsOn("generateSBE") }` (Spec §21.4).
- After generation, verify `MarketDataEventDecoder.TEMPLATE_ID == 1`, `ExecutionEventDecoder.TEMPLATE_ID == 2`, `NewOrderCommandDecoder.TEMPLATE_ID == 10`, `CancelOrderCommandDecoder.TEMPLATE_ID == 11`, `BalanceQueryResponseDecoder.TEMPLATE_ID == 4`.

### Acceptance Criteria
None directly — all other ACs depend on correct SBE schemas. The card is done when `./gradlew :platform-common:generateSBE` succeeds and all 17 decoder/encoder pairs are generated (14 wire + 3 snapshot).

### INPUT → OUTPUT Paths
- **Positive path:** `messages.xml` valid → SBE generator produces 34+ Java files → compiles clean.
- **Negative path:** Missing `<data>` element on varString field → SBE generator error with line number.
- **Failure path:** SBE tool version mismatch → Gradle task fails with classpath resolution error.

### Implementation Steps
1. Create `platform-common/src/main/resources/messages.xml` with full content from Spec §8.1–§8.13 (all 14 wire messages + all enum types from §8.1).
2. Append the 3 snapshot messages from Spec §16.7 (`OrderStateSnapshot`, `PositionSnapshot`, `RiskStateSnapshot`).
3. Add the SBE generation task to `platform-common/build.gradle.kts` per Spec §21.4:
   ```kotlin
   configurations { create("sbeCodegen") }
   dependencies { "sbeCodegen"("uk.co.real-logic:sbe-tool:$sbeVersion") }
   tasks.register<JavaExec>("generateSBE") {
       mainClass.set("uk.co.real_logic.sbe.SbeTool")
       classpath = configurations["sbeCodegen"]
       args = listOf("src/main/resources/messages.xml")
       systemProperties["sbe.output.dir"]               = "src/generated/java"
       systemProperties["sbe.target.language"]           = "Java"
       systemProperties["sbe.java.generate.interfaces"]  = "false"
   }
   tasks.named("compileJava") { dependsOn("generateSBE") }
   ```
4. Add `sourceSets.main.java.srcDirs += "src/generated/java"` so generated classes are part of main source set.
5. Run `./gradlew :platform-common:generateSBE` and verify all 17 encoder/decoder pairs generated.
6. Confirm `CancelOrderCommandDecoder.encodedLength()` returns `blockLen + 2 + venueOrderId.length` (variable-length).
7. Confirm `NewOrderCommandDecoder.encodedLength() == blockLen` (fixed-length, no varString).

### Test Implementation Steps
No test class for this card. Verification: compilation succeeds; spot-check TEMPLATE_ID constants.

### Out of Scope
No business logic. Schema only.

### Completion Checklist
- [ ] Files changed: `platform-common/src/main/resources/messages.xml`, `platform-common/build.gradle.kts`
- [ ] Tests added/updated: none
- [ ] All ACs verified: n/a (enabler card)
- [ ] Assumptions made: `sbe-tool:1.37.1` resolves from Maven Central; `NewOrderCommand` remains fixed-length (no varString additions later)
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-003: Constants (Ids, OrderStatus, ScaledMath)

### Task Description
Implement the three shared constant/utility classes: `Ids` (all integer ID constants),
`OrderStatus` (byte constants + terminal mask), and `ScaledMath` (`safeMulDiv` + `vwap`).
These are immutable utility classes consumed by every other module.

### Spec References
Spec §3.5 (Ids), §7.2 (OrderStatus), §11.3/11.4/11.5 (ScaledMath).

### Required Existing Inputs
- `TASK-001`: `platform-common/build.gradle.kts`

### Expected Modified Files
```
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/Ids.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/OrderStatus.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ScaledMath.java
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/OrderStatusTest.java   (T-003)
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/ScaledMathTest.java     (T-024)
platform-common/src/test/java/ig/rueishi/nitroj/exchange/test/SbeTestMessageBuilder.java  (shared test utility under platform-common test sources)
```

### Do Not Modify
Nothing else from prior cards.

### Related Files (Read-Only Reference)
Generated SBE classes from TASK-002 (used by SbeTestMessageBuilder).

### Authoritative Spec Excerpts

**Ids.java — full class (Spec §3.5):**
```java
public final class Ids {
    public static final int  VENUE_COINBASE          = 1;
    public static final int  VENUE_COINBASE_SANDBOX  = 2;
    public static final int  INSTRUMENT_BTC_USD      = 1;
    public static final int  INSTRUMENT_ETH_USD      = 2;
    public static final int  STRATEGY_MARKET_MAKING  = 1;
    public static final int  STRATEGY_ARB            = 2;
    public static final int  STRATEGY_ARB_HEDGE      = 3;
    public static final int  MAX_VENUES              = 16;
    public static final int  MAX_INSTRUMENTS         = 64;
    public static final int  MAX_ORDERS_PER_WINDOW   = 1000;
    public static final long INVALID_PRICE           = Long.MIN_VALUE;
    public static final long INVALID_QTY             = Long.MIN_VALUE;
    public static final long SCALE                   = 100_000_000L;  // 1e8
    private Ids() {}
}
```

**OrderStatus.java — full class (Spec §7.2):**
```java
public final class OrderStatus {
    public static final byte PENDING_NEW      = 0;
    public static final byte NEW              = 1;
    public static final byte PARTIALLY_FILLED = 2;
    public static final byte FILLED           = 3;
    public static final byte PENDING_CANCEL   = 4;
    public static final byte CANCELED         = 5;
    public static final byte PENDING_REPLACE  = 6;
    public static final byte REPLACED         = 7;
    public static final byte REJECTED         = 8;
    public static final byte EXPIRED          = 9;
    private static final long TERMINAL_MASK =
        (1L << FILLED) | (1L << CANCELED) | (1L << REPLACED) |
        (1L << REJECTED) | (1L << EXPIRED);
    public static boolean isTerminal(byte status) {
        return (TERMINAL_MASK & (1L << status)) != 0;
    }
    private OrderStatus() {}
}
```

**ScaledMath.java — key contracts (Spec §11.3–11.5):**
```java
public final class ScaledMath {
    // Fast path: if hi==0, product fits in 64 bits — use Long.divideUnsigned
    // Slow path: hi!=0, product > Long.MAX_VALUE — use BigDecimal (allocates ~200–500ns)
    public static long safeMulDiv(long a, long b, long divisor) { ... }

    // Always BigDecimal — both price×qty products routinely overflow at BTC prices.
    // dontinline in hotspot_compiler — keeps allocation off the hot path profile.
    public static long vwap(long oldPrice, long oldQty, long fillPrice, long fillQty) { ... }

    // Tick rounding helpers — deterministic, no allocation
    public static long floorToTick(long price, long tickSize) { return (price / tickSize) * tickSize; }
    public static long ceilToTick(long price, long tickSize) { return ((price + tickSize - 1) / tickSize) * tickSize; }
    public static long floorToLot(long qty, long lotSize) { return (qty / lotSize) * lotSize; }
}
```

### Implementation Guidelines
- `safeMulDiv`: use `Math.multiplyHigh(a, b)` to detect 128-bit overflow. If `hi == 0`, fast path: `Long.divideUnsigned(a * b, divisor)`. If `hi != 0`, slow path: BigDecimal with `RoundingMode.HALF_UP`. See Spec §11 for the exact implementation.
- `vwap`: always BigDecimal. Return `0L` if `newQty == 0`. See Spec §11.
- `SbeTestMessageBuilder`: provides static factory methods `encodeMarketData()`, `encodeExecution()` used across all test classes. Depends on generated SBE encoders from TASK-002.

### Acceptance Criteria
- AC-PF-009 is owned by TASK-021 (PortfolioEngine). TASK-003's T-024 ScaledMathTest is a supporting test that exercises the overflow protection primitive (1 BTC @ $65K test vector), but does not own the AC.

### INPUT → OUTPUT Paths
- **Positive path:** `safeMulDiv(100_000_000L, 6_500_000_000_000L, SCALE)` → `650_000_000_000L` (correct).
- **Edge path:** `safeMulDiv(0, anyValue, SCALE)` → `0L`.
- **Negative path:** `vwap(price, qty, fillPrice, 0)` where `newQty == 0` → `0L` (no divide-by-zero).
- **Exception path:** `safeMulDiv(Long.MAX_VALUE, Long.MAX_VALUE, 1)` → BigDecimal path, `ArithmeticException` if result exceeds `Long.MAX_VALUE` (expected — callers must not produce such inputs).

### Implementation Steps
1. Create `Ids.java` — copy exact constants from Spec §3.5. `private` constructor.
2. Create `OrderStatus.java` — copy constants + `TERMINAL_MASK` + `isTerminal()` from Spec §7.2.
3. Create `ScaledMath.java` — implement `safeMulDiv`, `vwap`, `floorToTick`, `ceilToTick`, `floorToLot` per Spec §11.
4. Create `SbeTestMessageBuilder.java` under `platform-common/src/test/java/ig/rueishi/nitroj/exchange/test/` — static helpers used by T-003, T-009, T-010, etc. Exposed as a test-fixture from `platform-common` (other modules consume via `testImplementation(testFixtures(project(":platform-common")))` or a `java-test-fixtures` plugin configuration in `platform-common/build.gradle.kts`).

### Test Implementation Steps
1. **T-003 OrderStatusTest**: verify `isTerminal()` for all 10 status values; verify TERMINAL_MASK bit arithmetic is correct.
2. **T-024 ScaledMathTest** (Spec §D.2):
   - `safeMulDiv_hiZero_fastPath_correct()` — small values, no BigDecimal
   - `safeMulDiv_hiNonZero_1BtcAt65K_exact()` — `safeMulDiv(100_000_000L, 6_500_000_000_000L, SCALE)` == `650_000_000_000L`
   - `safeMulDiv_bothMaxLong_bigDecimalPath_noSilentWrap()`
   - `vwap_twoFills_correctWeightedAverage()`
   - `vwap_newQtyZero_returnsZero()`
   - `floorToTick_roundsDown()`, `ceilToTick_roundsUp()`
   - `floorToLot_roundsDown()`

### Out of Scope
No configuration parsing (TASK-004). No ID registration (TASK-005).

### Completion Checklist
- [ ] Files changed: Ids.java, OrderStatus.java, ScaledMath.java, OrderStatusTest.java, ScaledMathTest.java, SbeTestMessageBuilder.java
- [ ] Tests added/updated: T-003, T-024
- [ ] All ACs verified: none owned directly. T-024 provides supporting verification for AC-PF-009 (full ownership TASK-021).
- [ ] Assumptions made: BigDecimal allocation in vwap is acceptable at fill frequency (< 1000/min)
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-004: Configuration (TOML + ConfigManager)

### Task Description
Implement `ConfigManager` (TOML loading via **night-config** per Spec V10.0 §21.1) and all
config record classes for both the gateway and cluster processes. Populate the skeleton
TOML files from TASK-001 with full content. This card makes every component configurable
at startup.

> **Note on TOML library.** Spec V10.0 §21.1 pins `com.electronwill.night-config:toml:3.6.7`
> in place of `toml4j:0.7.2` (no releases since 2019, mis-handles arrays of tables).
> Parser class: `com.electronwill.nightconfig.toml.TomlParser` — **not** `com.moandjiezana.toml.Toml`.

### Spec References
Spec §22 (all TOML files + config record locations), §22.6 (ConfigManager), §6.1.1 (MarketMakingConfig), §6.2.1 (ArbStrategyConfig), §27.6 (WarmupConfig — 3-param class per AMB-002 fix), §G.4 (GatewayConfig.forTest() factory + CpuConfig.noPinning()).

### Required Existing Inputs
- `TASK-001`: build files, skeleton TOML files
- `TASK-003`: `Ids.java` (for constant references)

### Expected Modified Files
```
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ConfigManager.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/GatewayConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ClusterNodeConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/VenueConfig.java                    (record — consumed by IdRegistryImpl.init() in TASK-005)
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/InstrumentConfig.java               (record — consumed by IdRegistryImpl.init() in TASK-005)
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/RiskConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/MarketMakingConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ArbStrategyConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/FixConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/CpuConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/DisruptorConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/RestConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/CredentialsConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/AdminConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/WarmupConfig.java                  (per Spec §22 and §27.6 — includes production()/development() factories)
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ConfigValidationException.java
config/cluster-node-0.toml     (full content)
config/cluster-node-1.toml
config/cluster-node-2.toml
config/gateway-1.toml
config/venues.toml
config/instruments.toml
config/admin.toml
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/ConfigManagerTest.java  (T-002)
```

### Do Not Modify
`Ids.java`, `OrderStatus.java`, `ScaledMath.java`.

### Authoritative Spec Excerpts

**ConfigManager.parseScaled() — Spec §22.6:**
```java
public static long parseScaled(String decimal) {
    String[] parts = decimal.split("\\.");
    long whole = Long.parseLong(parts[0]) * SCALE;
    if (parts.length == 1) return whole;
    String frac = parts[1];
    while (frac.length() < 8) frac += "0";
    frac = frac.substring(0, 8);
    return whole + Long.parseLong(frac);
}
// "1.5" → 150_000_000L; "0.01" → 1_000_000L; "65000.00" → 6_500_000_000_000L
```

**MarketMakingConfig record — Spec §6.1.1 + AMB-005 fix:**
```java
public record MarketMakingConfig(
    int  instrumentId, int venueId,
    long targetSpreadBps, long inventorySkewFactorBps,
    long baseQuoteSizeScaled, long maxQuoteSizeScaled,
    long maxPositionLongScaled, long maxPositionShortScaled,
    long refreshThresholdBps, long maxQuoteAgeMicros,
    long marketDataStalenessThresholdMicros,
    long wideSpreadThresholdBps, long maxTolerableSpreadBps,
    long tickSizeScaled, long lotSizeScaled,
    long minQuoteSizeFractionBps   // v8 addition — default 1000 (10%)
) {}
```

**GatewayConfig.forTest() — Spec §G.4:**
Add as a static factory method on `GatewayConfig`. Uses `Ids.VENUE_COINBASE_SANDBOX`, no CPU
pinning (`CpuConfig.noPinning()` — all zeros), `WarmupConfig.development()`, loopback REST URL.

### Implementation Guidelines
- Use **`com.electronwill.night-config:toml:3.6.7`** (Spec V10.0 §21.1). Parser: `com.electronwill.nightconfig.toml.TomlParser`. Do NOT use `toml4j` — Spec V10.0 pins night-config as the only TOML dependency.
- `ConfigValidationException` extends `RuntimeException` — thrown on invalid/missing required fields.
- All config records are immutable — use Java records where possible.
- `ConfigManager.loadCluster(path)` and `ConfigManager.loadGateway(path)` are the two entry points.
- `GatewayConfig.forTest()` must use `ProcessHandle.current().pid()` in the Aeron dir path to avoid collisions in parallel tests.

### Acceptance Criteria
None directly. Enables all other cards.

### INPUT → OUTPUT Paths
- **Positive path:** Valid TOML file → all config records populated with correct types.
- **Negative path:** `risk.instrument.1.maxOrderSizeBtc` missing → `ConfigValidationException` with field path.
- **Edge path:** `"0.01"` → `1_000_000L` (8 significant fractional digits).
- **Failure path:** File not found → `ConfigValidationException("File not found: " + path)`.

### Implementation Steps
1. Implement `ConfigValidationException`.
2. Implement `ConfigManager.parseScaled()` per spec. Validate: negative strings, empty fraction.
3. Implement all config records (`MarketMakingConfig`, `ArbStrategyConfig`, `RiskConfig`, etc.).
4. Implement `VenueConfig` and `InstrumentConfig` records per Spec §22. `VenueConfig` fields match `venues.toml` (§22.2): `id, name, fixHost, fixPort, sandbox` — **no FIX session identity here** (senderCompId / targetCompId live in `FixConfig` per §22.5 because they're tied to the API key, not the venue). `InstrumentConfig` fields match `instruments.toml` (§22.3): `id, symbol, baseAsset, quoteAsset`. Both are consumed by `IdRegistryImpl.init(List<VenueConfig>, List<InstrumentConfig>)` in TASK-005 for name↔id mapping.
5. Implement `WarmupConfig` as a 3-parameter immutable class per Spec §27.6 (AMB-002 fix) — `iterations`, `requireC2Verified`, `thresholdNanos`. Static factories: `production()` returns `(100_000, true, 500)`; `development()` returns `(10_000, false, 0)`.
6. Implement `GatewayConfig` with `forTest()` factory (Spec §G.4). The factory calls `CpuConfig.noPinning()` and `WarmupConfig.development()`, both defined in this card.
7. Implement `CpuConfig.noPinning()` (all zeros) per Spec §G.4.
8. Implement `ConfigManager.loadCluster()` and `ConfigManager.loadGateway()`.
9. Populate all TOML files with full content from Spec §22.3–22.5 + AMB-005 (add `minQuoteSizeFractionBps = 1000`).

### Test Implementation Steps
**T-002 ConfigManagerTest** (13 cases; see Spec V10.0 §D.2 for full Given/When/Then bodies):
- `parseScaled_wholeNumber_correct()` — `"65000"` → `6_500_000_000_000L`
- `parseScaled_decimal_correct()` — `"0.01"` → `1_000_000L`
- `parseScaled_eightDecimalPlaces_exact()` — `"0.00000001"` → `1L`
- `parseScaled_zeroValue_returnsZero()` — `"0"` → `0L`
- `parseScaled_zeroFraction_correct()` — `"1.0"` → `100_000_000L` (trailing-zero normalization)
- `parseScaled_nineDecimalPlaces_truncatesAt8()` — `"1.123456789"` → `112_345_678L`
- `parseScaled_largeValue_noOverflow()` — `"99999999.99999999"` → `9_999_999_999_999_999L`; no ArithmeticException
- `loadCluster_validToml_allFieldsPopulated()`
- `loadCluster_missingOptionalField_usesDefault()` — e.g. TOML missing `cooldownAfterFailureMicros` → field defaults to 0L
- `loadCluster_missingRequiredField_throwsConfigValidationException()`
- `loadGateway_validToml_allFieldsPopulated()`
- `forTest_noCpuPinning_developmentWarmup()` — verifies `GatewayConfig.forTest()` factory per Spec §G.4
- `minQuoteSizeFractionBps_loadedFromToml()` — verifies AMB-005 fix

### Out of Scope
No ID registry (TASK-005). No business logic.

### Completion Checklist
- [ ] Files changed: all 16 config Java classes (ConfigManager, ConfigValidationException, ClusterNodeConfig, GatewayConfig, RiskConfig, MarketMakingConfig, ArbStrategyConfig, FixConfig, CpuConfig, DisruptorConfig, RestConfig, CredentialsConfig, AdminConfig, WarmupConfig, **VenueConfig**, **InstrumentConfig**), all TOML files, T-002
- [ ] Tests added/updated: T-002 (13 test cases)
- [ ] All ACs verified: n/a (enabler card)
- [ ] Assumptions made: night-config v3.6.7 handles all TOML constructs we use (arrays of tables, inline tables, integers, strings)
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-005: IdRegistry

### Task Description
Implement the `IdRegistry` interface and `IdRegistryImpl` — the startup-time bidirectional
map between String identifiers (venue names, instrument symbols, FIX session IDs) and their
`int` constants. After startup, all hot-path code uses `int` IDs only; String lookups only
occur during initialization and logging.

### Spec References
Spec §3.5 (ID constants), §22.2 (venues.toml), §22.3 (instruments.toml), **§17.7 (IdRegistry interface), §17.8 (IdRegistryImpl)**.

### Required Existing Inputs
- `TASK-001`: build files
- `TASK-003`: `Ids.java`
- `TASK-004`: `ConfigManager`, `GatewayConfig`, `ClusterNodeConfig`, **`VenueConfig`** (consumed by `init()` — fields: `id, name, fixHost, fixPort, sandbox`), **`InstrumentConfig`** (consumed by `init()` — fields: `id, symbol, baseAsset, quoteAsset`)

### Expected Modified Files
```
platform-common/src/main/java/ig/rueishi/nitroj/exchange/registry/IdRegistry.java          (interface — package `ig.rueishi.nitroj.exchange.registry` per Spec §17.7)
platform-common/src/main/java/ig/rueishi/nitroj/exchange/registry/IdRegistryImpl.java      (package `ig.rueishi.nitroj.exchange.registry` per Spec §17.8)
platform-common/src/test/java/ig/rueishi/nitroj/exchange/registry/IdRegistryImplTest.java  (T-001)
```

### Do Not Modify
`Ids.java`, `ConfigManager.java`, TOML files.

### Authoritative Spec Excerpts

**IdRegistry interface (Spec §17.7) — verbatim signatures (V9.7: `registerSession` added):**
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
     * init has run and when Artio exposes the Session.id() value during session
     * creation/acquisition/logon. The cluster NEVER calls this.
     *
     * @throws IllegalStateException if the same sessionId is registered twice for
     *         different venueIds.
     */
    void registerSession(int venueId, long sessionId);
}
```

**Rationale for exact signatures (DO NOT substitute types):**
- `long sessionId` (from Artio `Session.id()`): uses the real Artio 0.175 session identity API and keeps the hot-path lookup primitive/no-boxing.
- `CharSequence` (not `String`): allows zero-allocation reads from Artio's `AsciiBuffer` without `.toString()`. Using `String` forces allocation on the hot path and breaks AC-PF-P-001.
- Unknown-session → `IllegalStateException`; unknown-symbol → returns 0. The asymmetry is deliberate: an unknown session ID means the gateway has failed to register a live Artio session (programming error), while an unknown symbol is a data error recoverable by logging and discarding.
- Method names `symbolOf` / `venueNameOf` are used throughout Spec §9.5, §17.8, and other callsites. Renaming them breaks every downstream reference.
- `init(venues, instruments)` signature does NOT read or register FIX session identity (V9.7 fix). Live Artio `Session.id()` values are wired in via `registerSession()` from the gateway only after Artio creates/acquires/logs on a session.

**IdRegistryImpl storage (Spec §17.8) — zero-boxing:**
```java
package ig.rueishi.nitroj.exchange.registry;

import org.agrona.collections.Object2IntHashMap;
import org.agrona.collections.Long2LongHashMap;

public final class IdRegistryImpl implements IdRegistry {

    // Object2IntHashMap from Agrona — open-addressing, NO autoboxing on lookup.
    // HashMap<String,Integer> is INCORRECT — would autobox every lookup.
    private final Object2IntHashMap<String> venueByName        = new Object2IntHashMap<>(0);
    private final Object2IntHashMap<String> instrumentBySymbol = new Object2IntHashMap<>(0);

    // Reverse maps — arrays indexed by ID: O(1) lookup, no HashMap overhead
    private final String[] venueNames        = new String[Ids.MAX_VENUES];
    private final String[] instrumentSymbols = new String[Ids.MAX_INSTRUMENTS];

    // Artio Session.id() → venueId. Agrona 2.4.0 has Long2LongHashMap, not Long2IntHashMap,
    // so venue IDs are stored as long and cast back to int after lookup.
    // Gateway populates via registerSession(); cluster never populates and never reads.
    private final Long2LongHashMap venueBySessionId = new Long2LongHashMap(0);

    public void init(List<VenueConfig> venues, List<InstrumentConfig> instruments) {
        // Populate name↔id maps from venues.toml + instruments.toml (§22.2–22.3).
        // FIX session identity is NOT read here (§22.2 does not carry it); live
        // Artio Session.id() values are registered by gateway-side session handling.
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
        // Called by the gateway after Artio exposes Session.id(); never called from cluster.
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
        return id;  // 0 = unknown; caller discards event
    }

    @Override public String symbolOf(int instrumentId) { return instrumentSymbols[instrumentId]; }
    @Override public String venueNameOf(int venueId)   { return venueNames[venueId]; }
}
```

### Implementation Guidelines
- Backing maps: **`Object2IntHashMap<String>` for name/symbol → id** and **`Long2LongHashMap` for Artio sessionId → venueId** from Agrona (Spec §17.8) — NOT boxed Java `HashMap` variants. Java's boxed maps autobox primitive IDs on put/get, violating zero-allocation on the hot path.
- Reverse lookups use plain `String[]` indexed by ID — O(1), no allocation.
- `venueId(long sessionId)` throws `IllegalStateException` on unknown (per spec §17.7 contract). `instrumentId(CharSequence)` returns 0 on unknown and logs WARN — callers must check for 0 and discard the event.
- `CharSequence` parameter for `instrumentId()` is deliberate — allows zero-allocation reads from Artio `AsciiBuffer` views. The `.toString()` call inside the implementation is a fallback for map key hashing; callers should prefer passing `AsciiBuffer` slices that support `CharSequence` directly.
- `init(List<VenueConfig>, List<InstrumentConfig>)` is called at startup by ClusterMain/GatewayMain — allocation is permitted inside `init()`; not on the hot path.
- No `validate()` method in the interface (it is not in spec §17.7). Startup validation is performed by ClusterMain/GatewayMain comparing `Ids.*` constants against loaded mappings and failing fast via `ConfigValidationException`.

### Acceptance Criteria
None directly. Enables all ID-based lookups in every other card.

### INPUT → OUTPUT Paths
- **Positive path (symbol):** `registry.instrumentId("BTC-USD")` → `1` after startup load.
- **Negative path (symbol):** `registry.instrumentId("UNKNOWN-PAIR")` → `0` + WARN log.
- **Positive path (session):** `registry.venueId(coinbaseSessionId)` → `1` after gateway session handling has registered the Artio `Session.id()` value.
- **Exception path (session):** `registry.venueId(unknownSession)` → `IllegalStateException` (programming error: misconfigured venue).
- **Zero-alloc path:** `registry.instrumentId(asciiBufferSlice)` where `asciiBufferSlice` is a `CharSequence` view of Artio's buffer → no allocation if symbol is cached.

### Implementation Steps
1. Create `IdRegistry.java` interface with the five methods from Spec §17.7 verbatim (`venueId(long)`, `instrumentId(CharSequence)`, `symbolOf(int)`, `venueNameOf(int)`, `registerSession(int, long)`). No renames, no added methods. Package: `ig.rueishi.nitroj.exchange.registry`.
2. Create `IdRegistryImpl.java` using `Object2IntHashMap<String>` for name→id maps, `Long2LongHashMap` for sessionId→venueId, and `String[]` for id→name arrays. Same package. `init(venues, instruments)` populates name↔id maps from the config records but does NOT populate `venueBySessionId` (V9.7/V10 fix).
3. Implement `registerSession(venueId, sessionId)` per Spec §17.8 — adds the `session.id() → venueId` entry to `venueBySessionId`. Throws `IllegalStateException` if the same session ID is re-registered for a different venueId.
4. Downstream wiring: the gateway calls `registerSession()` from Artio session creation/acquisition/logon handling after `init()`, when `Session.id()` is available. Cluster-side code (ClusterMain) never calls `registerSession()`. See Spec §17.8 "Gateway-side wiring".
5. Startup validation is performed by the callers (ClusterMain, GatewayMain) — they compare `Ids.*` constants to registry contents and throw `ConfigValidationException` on mismatch. No `validate()` method on the interface.

### Test Implementation Steps
**T-001 IdRegistryImplTest:**
- `venueId_sessionIdForCoinbase_returns1_afterRegisterSession()` — verifies the gateway-wiring path
- `venueId_unknownSession_throwsIllegalStateException()`
- `registerSession_sameSessionId_differentVenueId_throwsIllegalStateException()` — V9.7/V10 duplicate-guard
- `registerSession_sameSessionId_sameVenueId_idempotent()` — re-registering same session ID is a no-op
- `init_doesNotPopulateVenueBySession()` — V9.7/V10 behaviour: `venueId(long)` throws for any session before `registerSession()` is called
- `instrumentId_btcUsd_returns1()`
- `instrumentId_unknownSymbol_returns0()`
- `instrumentId_asciiBufferSlice_noAllocation()` — passes a `CharSequence` (not `String`) to verify the zero-alloc path
- `symbolOf_id1_returnsBtcUsd()`
- `venueNameOf_id1_returnsCoinbase()`
- `init_populatesNameMapsOnly()` — venueByName and instrumentBySymbol populated; venueBySessionId empty
- `init_reverseArrayMatchesForwardMap()`

### Out of Scope
No FIX session management (TASK-008/011 trigger the logon; TASK-016 wires the gateway-side Artio session callback that calls `registerSession()`). Callers of the registry perform startup `Ids.*` cross-validation — this card does not do it.

### Completion Checklist
- [ ] Files changed: IdRegistry.java, IdRegistryImpl.java, T-001 (all under `platform-common/.../registry/` per Spec §17.7–17.8)
- [ ] Tests added/updated: T-001 (12 test cases)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: venue/instrument count fits in `Ids.MAX_VENUES` and `MAX_INSTRUMENTS`; Agrona `Object2IntHashMap` and `Long2LongHashMap` versions match Spec §21.1 (agrona 2.4.0 — verified source-compatible with the plan's usage; Agrona 2.4.0 does not provide `Long2IntHashMap`)
- [ ] Blockers identified: none
- [ ] Follow-up items: TASK-016 `GatewayMain.buildIdRegistry()` calls `init()` and gateway-side Artio session handling calls `registerSession(venueId, session.id())`; TASK-027 `ClusterMain.buildClusteredService()` calls `init()` only (cluster never calls `registerSession()`)


---

## Task Card TASK-006: CoinbaseExchangeSimulator

### Task Description
Build the embeddable FIX 4.2 acceptor simulator used by all E2E tests. It accepts FIX logon
from the gateway, publishes synthetic market data, and responds to orders with configurable
fill scenarios (IMMEDIATE, PARTIAL_THEN_FULL, REJECT_ALL, NO_FILL, DELAYED_FILL,
DISCONNECT_ON_FILL). Supports programmatic test assertion via `assertOrderReceived()`.

### Spec References
Spec §C (all subsections C.1–C.3), Spec §D.4 (TradingSystemTestHarness).

### Required Existing Inputs
- `TASK-002`: generated SBE classes (not used directly but simulator FIX messages must match)
- `TASK-004`: `GatewayConfig` (for `TradingSystemTestHarness.start()` which consumes `GatewayConfig.forTest()`); `FixConfig` (for the simulator's acceptor session identity)

> **Note:** `SimulatorConfig` is owned and created by THIS card (TASK-006) — see Expected Modified Files below. It lives in `platform-tooling` alongside the other simulator classes, not in `platform-common` with the TASK-004 config records.

### Expected Modified Files
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseExchangeSimulator.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/SimulatorConfig.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/SimulatorOrderBook.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/SimulatorSessionHandler.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/MarketDataPublisher.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/ScenarioController.java
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/test/TradingSystemTestHarness.java
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseExchangeSimulatorTest.java (T-015)
```

### Do Not Modify
All platform-common, platform-gateway, platform-cluster source files.

### Related Files (Read-Only Reference)
Spec §9.5 (FIX NewOrderSingle tags), §9.3 (ExecutionReport tags), §C.2 (simulator architecture).

### Authoritative Spec Excerpts

**CoinbaseExchangeSimulator builder API (Spec §C.3):**
```java
CoinbaseExchangeSimulator sim = CoinbaseExchangeSimulator.builder()
    .port(9898)
    .instrument("BTC-USD", 65000.00, 65001.00)
    .fillMode(FillMode.IMMEDIATE)
    .build();
sim.start();
// ... test body ...
sim.stop();
sim.assertOrderReceived("BUY", 65000.00, 0.01);
```

**FillMode enum:**
```java
IMMEDIATE         // Fill every order instantly at limit price
PARTIAL_THEN_FULL // First fill 50%, then remaining
REJECT_ALL        // Reject with OrdRejReason=0
NO_FILL           // ACK but never fill (timeout tests)
DELAYED_FILL      // Fill after configurable delay (ms)
DISCONNECT_ON_FILL // Disconnect FIX session when first fill would occur
```

**Test assertion fields:**
```java
// Thread-safe collections for assertions:
CopyOnWriteArrayList<ReceivedOrder>  receivedOrders
CopyOnWriteArrayList<ReceivedCancel> receivedCancels
AtomicInteger fillCount, rejectCount, logonCount
```

**TradingSystemTestHarness.start() sequence (Spec §D.4):**
1. Start embedded MediaDriver (shared)
2. Start CoinbaseExchangeSimulator
3. Start ClusterMain (single-node for E2E)
4. Start GatewayMain (connects to cluster + simulator)
5. Wait for FIX logon confirmed (max 5 seconds)
6. Return harness

### Implementation Guidelines
- Simulator runs Artio in acceptor mode. Use `EngineConfiguration.acceptorConfigurations()`.
- `MarketDataPublisher` sends FIX MsgType=W (snapshot) at startup, then MsgType=X (incremental) on schedule.
- `scheduleDisconnect(delay)` and `scheduleDisconnect(cancelAllOnLogout)` — needed for ET-003.
- `assertOrderReceived()` uses `Awaitility` with 2-second timeout polling `receivedOrders`.
- `TradingSystemTestHarness` lives under `platform-tooling/src/test/java/.../test/` and is exposed as a test fixture (`java-test-fixtures` plugin in `platform-tooling/build.gradle.kts`) so both `platform-cluster` and `platform-gateway` test code can consume it via `testImplementation(testFixtures(project(":platform-tooling")))`. This replaces the earlier `platform-test` module idea and keeps the build to the 4-module structure in Spec §21.3.

### Acceptance Criteria
Enables all ET-* tests but owns no ACs directly. Card is done when T-015 passes.

### INPUT → OUTPUT Paths
- **Positive path:** FIX logon → simulator sends MsgType=W → gateway receives market data → `marketDataCount > 0`.
- **Edge path:** `FillMode.PARTIAL_THEN_FULL` → two ExecutionReports per order (50% + 50%).
- **Negative path:** `FillMode.REJECT_ALL` → ExecutionReport ExecType=8 OrdRejReason=0.
- **Failure path:** Simulator port in use → `IOException` with port number in message.

### Implementation Steps
1. Create `SimulatorConfig` with builder: port, instrument(s), fillMode, marketDataIntervalMs.
2. Create `SimulatorOrderBook` — tracks pending orders by clOrdId; supports fill/reject/cancel.
3. Create `SimulatorSessionHandler` implementing Artio `SessionHandler` — routes FIX messages to SimulatorOrderBook.
4. Create `MarketDataPublisher` — sends configurable bid/ask stream at fixed interval.
5. Create `ScenarioController` — schedules disconnects, fill delays, and cancel-on-logout behavior.
6. Create `CoinbaseExchangeSimulator` — wires all components, exposes builder + assertion API.
7. Create `TradingSystemTestHarness` under `platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/test/` per Spec §D.4; expose as test fixture so gateway and cluster modules can consume it.

### Test Implementation Steps
**T-015 CoinbaseExchangeSimulatorTest:**
- `logon_accepted_logonCountIncremented()`
- `newOrder_immediateFill_orderReceived()`
- `newOrder_rejectAll_rejectCountIncremented()`
- `newOrder_partialThenFull_twoFillsReceived()`
- `newOrder_noFill_orderAcked_fillCountZero()`
- `cancelRequest_pendingOrder_cancelConfirmed()`
- `marketData_published_bidAskCorrect()`
- `scheduleDisconnect_fixSessionDrops()`
- `assertOrderReceived_matchesSide_price_qty()`
- `assertOrderReceived_noMatchingOrder_assertionError()`
- `multipleInstruments_correctRoutingPerSymbol()`

### Out of Scope
No Aeron, no SBE, no cluster components. FIX only.

### Completion Checklist
- [ ] Files changed: CoinbaseExchangeSimulator.java, SimulatorConfig/OrderBook/SessionHandler/MarketDataPublisher/ScenarioController.java, TradingSystemTestHarness.java, T-015
- [ ] Tests added/updated: T-015 (11 test cases)
- [ ] All ACs verified: n/a (test infrastructure)
- [ ] Assumptions made: Artio acceptor mode available in artio version specified in BOM
- [ ] Blockers identified: none
- [ ] Follow-up items: Wire in E2E tests (ET-001 through ET-006, TASK-016/027)

---

## Task Card TASK-007: GatewayDisruptor + GatewaySlot

### Task Description
Implement the intra-gateway LMAX Disruptor ring buffer that serializes multiple producers
(artio-library thread, rest-poller thread) onto the single gateway-disruptor consumer thread
(AeronPublisher). This card creates the ring buffer infrastructure only; the consumer
(AeronPublisher) is TASK-012.

### Spec References
Spec §13.3 (GatewayDisruptor), §3.4.2 (AeronPublisher back-pressure policy), §14.3 (slot design). **AC-GW-004** for the authoritative ring-full policy — see AMB-008 below.

> **AMB-008 resolution (Spec V10.0 §14.4).** Spec V10.0 §14.4 resolves the earlier
> §14.4-vs-AC-GW-004 contradiction: the market-data `claimSlot()` path returns `null`
> on ring-full; the producer drops the tick and increments `DISRUPTOR_FULL_COUNTER`.
> The artio-library thread is NEVER blocked on ring-full. Fill-path producers
> (ExecutionEvent) still follow §13.3's indefinite-spin behaviour — dropping a fill
> is a position-reconciliation event.

### Required Existing Inputs
- `TASK-002`: generated SBE classes (needed for `MessageHeaderDecoder` in slot peek)
- `TASK-003`: `Ids.java` (counter constants)

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/GatewayDisruptor.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/GatewaySlot.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/GatewayDisruptorIntegrationTest.java (IT-001)
```

### Do Not Modify
Nothing else.

### Authoritative Spec Excerpts

**GatewaySlot (Spec §18.3 + §14.3):**
```java
@jdk.internal.vm.annotation.Contended
public final class GatewaySlot {
    public final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
    public int length = 0;
    public void reset() { length = 0; }   // called after each consume
}
// Requires JVM flag: -XX:-RestrictContended
```

**Ring buffer construction (Spec §13.3):**
```java
RingBuffer<GatewaySlot> ringBuffer = RingBuffer.createMultiProducer(
    GatewaySlot::new,
    4096,                       // power of 2; from config.disruptor().ringBufferSize()
    new BusySpinWaitStrategy()  // gateway-disruptor thread is CPU-pinned
);
```

**Publish pattern (no allocation on happy path):**
```java
// Returns the claimed GatewaySlot, or null if ring is full
public GatewaySlot claimSlot() {
    long hi = ringBuffer.tryNext();
    if (hi < 0) return null;   // ring full — caller increments DISRUPTOR_FULL_COUNTER
    return ringBuffer.get(hi); // returns the pre-allocated slot
}
public void publishSlot(GatewaySlot slot) {
    // publish using the sequence stored when slot was claimed
    ringBuffer.publish(slot.sequence);
}
```

**slot.reset() rule:** MUST be called after every consume — even on Aeron back-pressure failure — to prevent stale buffer data on slot reuse. See Spec §14.4 (last line of AeronPublisher.onEvent stub).

### Implementation Guidelines
- Ring buffer size from `config.disruptor().ringBufferSize()` (default 4096; must be power of 2).
- Slot size from `config.disruptor().slotSizeBytes()` (default 512).
- `DISRUPTOR_FULL_COUNTER` and `AERON_BACKPRESSURE_COUNTER` are Agrona `CountersManager` counters allocated in `GatewayDisruptor` constructor.
- `@Contended` on `GatewaySlot` prevents false sharing between producer writes and consumer reads.

### Acceptance Criteria
None directly — enables TASK-009/010/011/012.

### INPUT → OUTPUT Paths
- **Positive path:** Claim → encode into slot.buffer → publish → consumer sees message in order.
- **Edge path:** Exactly 4096 messages in flight → no drops.
- **Negative path:** Ring full (4097th claim while consumer stalled) → `claimSlot()` returns null.
- **Failure path:** `slot.reset()` not called → stale data on next claim (this is a correctness bug, verified by test).

### Implementation Steps
1. Create `GatewaySlot.java` with `@Contended`, pre-allocated 512-byte `UnsafeBuffer`, `length`, `sequence`, `reset()`.
2. Create `GatewayDisruptor.java` with `RingBuffer.createMultiProducer(GatewaySlot::new, size, BusySpinWaitStrategy)`.
3. Implement `claimSlot()` returning null on full ring.
4. Implement `publishSlot(slot)` using `ringBuffer.publish(slot.sequence)`.
5. Allocate `DISRUPTOR_FULL_COUNTER` and `AERON_BACKPRESSURE_COUNTER` in constructor.
6. Implement `start()` / `stop()` lifecycle methods (start/stop the `BatchEventProcessor`).

### Test Implementation Steps
**IT-001 GatewayDisruptorIntegrationTest:**
- `multipleProducers_messagesConsumedInOrder()`
- `singleProducer_highThroughput_noDrops()`
- `backpressure_alertFires_whenNearlyFull()`
- `slotReset_afterConsume_fieldsZeroed()`
- `ringFull_tryPublishReturnsFalse_counterIncremented()`
- `exactlyRingBufferSizeMessages_noDrops()`
- `producerAndConsumerSameThread_noDeadlock()`

### Out of Scope
No Aeron publishing (TASK-012). No SBE encoding (TASK-009/010/011).

### Completion Checklist
- [ ] Files changed: GatewayDisruptor.java, GatewaySlot.java, IT-001
- [ ] Tests added/updated: IT-001 (7 test cases)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: Disruptor 4.0 API used; `tryNext()` returns -1 on full ring
- [ ] Blockers identified: none
- [ ] Follow-up items: Wire consumer in TASK-012 (AeronPublisher)

---

## Task Card TASK-008: CoinbaseLogonStrategy

### Task Description
Implement the Artio `SessionCustomisationStrategy` that builds the Coinbase-specific FIX 4.2
Logon message with HMAC-SHA256 authentication. Required Coinbase tags: 95 (RawDataLength),
96 (RawData = signature), 98 (EncryptMethod=0), 108 (HeartBtInt), 554 (Password=passphrase),
and **8013 (CancelOrdersOnDisconnect)**. Credentials are resolved at gateway startup:
production config carries a Vault path, while standalone development may use the
`COINBASE_API_KEY`, `COINBASE_API_SECRET_BASE64`, and `COINBASE_API_PASSPHRASE`
environment variables until the Vault resolver is wired. Credentials are never stored in logs.

### Spec References
**Spec §9.8.1 (required tags), §9.8.2 (signature formula), §9.8.3 (Artio implementation),
§9.8.4 (Artio registration), §9.8.5 (credentials storage).**

### Required Existing Inputs
- `TASK-004`: `CredentialsConfig`, `GatewayConfig`
- `TASK-005`: `IdRegistry` (for Artio `Session.id()` → venueId mapping at logon)

### Expected Modified Files
```
platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml  (customised FIX 4.2 dictionary declaring field 8013 on Logon)
platform-gateway/build.gradle.kts                                   (wire Artio CodecGenerationTool to the customised dictionary)
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/CoinbaseLogonStrategy.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/CoinbaseLogonStrategyTest.java (T-014)
```

### Do Not Modify
All TASK-001 through TASK-007 files.

### Authoritative Spec Excerpts

**Required tags (Spec §9.8.1):**

| FIX Tag | Name | Value | Notes |
|---|---|---|---|
| 35 | MsgType | A | Standard logon |
| 95 | RawDataLength | length of signature | Byte length of signature string |
| 96 | RawData | `<signature>` | Base64 HMAC-SHA256 |
| 98 | EncryptMethod | 0 | None |
| 108 | HeartBtInt | 30 | Seconds |
| 554 | Password | `<api_passphrase>` | From Coinbase API key setup |
| **8013** | **CancelOrdersOnDisconnect** | **Y** | **Coinbase proprietary — NOT tag 9406** |

**Signature formula (Spec §9.8.2) — exact concatenation order, no delimiters:**
```
prehash = sendingTime    (tag 52, format: YYYYMMDD-HH:MM:SS.sss)
        + msgType        (tag 35, value: "A")
        + msgSeqNum      (tag 34, as string)
        + senderCompId   (tag 49)
        + targetCompId   (tag 56)
        + password       (tag 554)

signature = Base64( HMAC-SHA256( Base64Decode(apiSecret), UTF8Bytes(prehash) ) )

RawData        (tag 96) = signature string  (Base64-encoded, ~44 chars)
RawDataLength  (tag 95) = signature.length()
```

**CoinbaseLogonStrategy implementation (Spec §9.8.3) — verbatim:**
```java
package ig.rueishi.nitroj.exchange.gateway;

import uk.co.real_logic.artio.session.SessionCustomisationStrategy;
import uk.co.real_logic.artio.builder.LogonEncoder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class CoinbaseLogonStrategy implements SessionCustomisationStrategy {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final String apiPassphrase;
    private final byte[] apiSecretDecoded;   // Base64-decoded secret; stored as bytes

    // Logon is not on the hot path — it occurs only at startup and on reconnect.
    // String allocation in configureLogon() is acceptable. Mac instance pre-allocated.
    private final Mac mac;

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

        // Exact concatenation order per Spec §9.8.2 — no delimiters:
        String prehash = sendingTime + "A" + msgSeqNum + senderComp + targetComp + apiPassphrase;

        try {
            mac.init(new SecretKeySpec(apiSecretDecoded, HMAC_ALGO));
            byte[] hmacBytes  = mac.doFinal(prehash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String signature  = Base64.getEncoder().encodeToString(hmacBytes);

            logon.encryptMethod(0);
            logon.heartBtInt(30);
            logon.password(apiPassphrase);
            logon.rawData(signature);
            // rawDataLength (tag 95) is set automatically by Artio from rawData length

            // Tag 8013 CancelOrdersOnDisconnect="Y" — generated setter from the customised
            // Coinbase FIX 4.2 dictionary (see Spec §9.8.3). The dictionary declares field
            // 8013 with name "CancelOrdersOnDisconnect" on the Logon message, and Artio's
            // code generation produces this strongly-typed setter at build time. NOT tag 9406.
            logon.cancelOrdersOnDisconnect((byte)'Y');

        } catch (Exception e) {
            throw new RuntimeException("Logon signature failed", e);
        }
    }
}
```

**Registration with Artio (Spec §9.8.4):**
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
    .sessionCustomisationStrategy(logonStrategy)
    .epochNanoClock(new SystemEpochNanoClock());
```

### Implementation Guidelines
- `CoinbaseLogonStrategy` implements `SessionCustomisationStrategy` and its `configureLogon(LogonEncoder, long)` method writes all Coinbase-specific tags.
- **The prehash concatenation order is non-negotiable: `sendingTime + "A" + msgSeqNum + senderCompId + targetCompId + passphrase`** (Spec §9.8.2). Any deviation — e.g. using `System.currentTimeMillis()` instead of FIX tag 52, or substituting a nonce for the FIX header fields — produces a signature Coinbase will reject.
- `apiSecret` from config is already Base64-encoded — `Base64.getDecoder().decode(...)` is required before using it as the HMAC key (Spec §9.8.2: `HMAC-SHA256(Base64Decode(apiSecret), UTF8Bytes(prehash))`).
- Pre-allocate the `Mac` instance in the constructor; `mac.init(...)` + `mac.doFinal(...)` are called once per logon (rare event — allocation is fine).
- Credentials must not appear in logs. Use `"[REDACTED]"` in any log messages mentioning credentials.
- Tag 8013 (`CancelOrdersOnDisconnect="Y"`) is Coinbase-proprietary and required for correct behaviour on disconnect. **It is tag 8013 — NOT 9406.** It is produced by a generated setter on `LogonEncoder` from the customised FIX 4.2 dictionary at `platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml`. Raw-buffer append after `session.send()`, manual FIX checksum recomputation, and reflection into Artio internals are all explicitly out of scope.
- Register the Artio `Session.id()` → `venueId` mapping in `IdRegistryImpl` from within the session creation/acquisition/logon callback when the live session ID becomes known.

### Dictionary Extension — Committed Path (V9)

Tag 8013 requires a customised FIX 4.2 dictionary. This card OWNS that file as a production
artifact; it is not a spike output. Required deliverable:

- `platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml` — copy of the standard
  FIX 4.2 dictionary with `<field number="8013" name="CancelOrdersOnDisconnect" type="CHAR"/>`
  declared in `<fields>` and referenced from the `Logon` `<message>` block. Artio's
  `CodecGenerationTool` reads this dictionary at build time (see Spec §9.8.3 and Artio wiki
  "Codecs").
- Gradle wiring: the Artio code generation task's `fileNames(...)` or `fileStreams(...)`
  input points at the customised dictionary; the generated encoders land under
  `platform-gateway/src/generated/java`.
- Runtime verification in T-014: capture the raw Logon byte stream, grep for
  `\x01` + `8013=Y` + `\x01` (SOH-delimited tag).

### Acceptance Criteria
- **AC-FX-001**: Coinbase Logon signature validates on sandbox. Verified by T-014 and ET-001.

### INPUT → OUTPUT Paths
- **Positive path:** Valid credentials + correct prehash concatenation → valid HMAC in tag 96 → FIX logon accepted by simulator/sandbox.
- **Negative path (wrong secret):** Wrong API secret → invalid HMAC → simulator sends Logout (tag 58 = "Invalid signature").
- **Negative path (wrong prehash order):** Fields concatenated in wrong order → signature validates locally but Coinbase rejects → every reconnect fails; ET-001 times out.
- **Exception path:** `Mac.getInstance("HmacSHA256")` throws `NoSuchAlgorithmException` (impossible on Java 21 — propagate as `RuntimeException`).

### Implementation Steps
1. Create `platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml` as a copy of the standard FIX 4.2 dictionary, extended to declare field 8013 (`CancelOrdersOnDisconnect`, type CHAR) on the `Logon` message.
2. Update `platform-gateway/build.gradle.kts` to wire Artio's `CodecGenerationTool` at this customised dictionary (replacing the default FIX 4.2 dictionary for gateway-generated encoders).
3. Run `./gradlew :platform-gateway:generateFixCodecs` (or the equivalent Artio codec task) and verify the generated `LogonEncoder` exposes a `cancelOrdersOnDisconnect(byte)` setter.
4. Create `CoinbaseLogonStrategy` implementing `SessionCustomisationStrategy`.
5. In `configureLogon(LogonEncoder, long sessionId)`: read `sendingTime`, `msgSeqNum`, `senderCompId`, `targetCompId` from `logon.header()`; build prehash string in exact order per Spec §9.8.2.
6. Compute `HMAC-SHA256` using Base64-decoded secret as key and UTF-8 bytes of prehash as data.
7. Base64-encode the HMAC bytes; set on `logon.rawData(signature)` — Artio auto-populates tag 95 from the string length.
8. Set tags 98 (EncryptMethod=0), 108 (HeartBtInt=30), 554 (passphrase), and call `logon.cancelOrdersOnDisconnect((byte)'Y')` for tag 8013.
9. Register the strategy with Artio via `LibraryConfiguration.sessionCustomisationStrategy(...)`.

### Test Implementation Steps
**T-014 CoinbaseLogonStrategyTest:**
- `logonMessage_prehashFormat_sendingTimeMsgTypeSeqSenderTargetPass()` — verifies concatenation order per Spec §9.8.2
- `logonMessage_apiSecret_base64DecodedBeforeHmac()` — verifies the secret is decoded, not used raw
- `logonMessage_tag96_correctHmacSha256()`
- `logonMessage_tag95_rawDataLengthMatchesSignature()`
- `logonMessage_tag8013_cancelOnDisconnect_Y()`     — tag 8013 (NOT 9406)
- `logonMessage_tag108_heartbeatInterval_30()`
- `logonMessage_tag554_passphrase()`
- `logonMessage_tag98_encryptMethod_0()`
- `credentials_notInLogOutput()`
- `hmac_differentSendingTime_differentSignature()`
- `hmac_differentMsgSeqNum_differentSignature()`

### Out of Scope
No Artio session lifecycle (TASK-015/016). Logon message only.

### Completion Checklist
- [ ] Files changed: `coinbase-fix42-dictionary.xml` (customised FIX dictionary declaring field 8013), `platform-gateway/build.gradle.kts` (Artio codec-gen task wired to the customised dictionary), CoinbaseLogonStrategy.java, T-014
- [ ] Tests added/updated: T-014 (11 test cases)
- [ ] All ACs verified: AC-FX-001
- [ ] Assumptions made: HmacSHA256 always available on Java 21; Artio's generated `LogonEncoder.cancelOrdersOnDisconnect(byte)` setter behaves identically to the standard generated setters used for all other Logon fields.
- [ ] Blockers identified: none
- [ ] Follow-up items: Verify signature against Coinbase sandbox during integration testing (ET-001)


---

## Task Card TASK-009: MarketDataHandler

### Task Description
Implement the Artio `SessionHandler` callback that processes incoming FIX MsgType=W (Market Data
Snapshot) and MsgType=X (Market Data Incremental Refresh). Each FIX entry is SBE-encoded into
a `GatewaySlot` and published to the `GatewayDisruptor`. The `ingressTimestampNanos` is stamped
as the absolute first operation before any FIX tag is decoded.

### Spec References
Spec §3.4.1 (MarketDataHandler rules), §9.2 (FIX tag mapping), §4.1 (market data flow), §14.4 (ring-full policy — drop market-data tick; never block the artio-library thread).

### Required Existing Inputs
- `TASK-002`: `MarketDataEventEncoder`, `MessageHeaderEncoder`
- `TASK-005`: `IdRegistry`
- `TASK-007`: `GatewayDisruptor`, `GatewaySlot`

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/MarketDataHandler.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/MarketDataHandlerTest.java (T-016)
```

### Do Not Modify
`GatewayDisruptor.java`, `GatewaySlot.java`, `IdRegistryImpl.java`.

### Authoritative Spec Excerpts

**Must-complete-in < 1µs contract (Spec §3.4.1):**
- No allocation, no blocking, no exception throws on hot path.
- `ingressTimestampNanos = System.nanoTime()` is the **first** line.
- `venueId = idRegistry.venueId(sessionId)` — O(1) map lookup.
- `instrumentId = idRegistry.instrumentId(symbol)` — O(1) map lookup.

**SBE encode pattern (Spec §9, §14.3):**
```java
GatewaySlot slot = disruptor.claimSlot();
if (slot == null) {
    metricsCounters.increment(DISRUPTOR_FULL_COUNTER);
    return;   // artio-library thread NOT blocked; stale tick is acceptable
}
mdEventEncoder
    .wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
    .venueId(venueId).instrumentId(instrumentId)
    .entryType(entryType).updateAction(updateAction)
    .priceScaled(priceScaled).sizeScaled(sizeScaled)
    .priceLevel(priceLevelOrZero)
    .ingressTimestampNanos(ingressNanos)
    .exchangeTimestampNanos(parseTransactTime(entry.transactTime()))
    .fixSeqNum(header.msgSeqNum());
slot.length = mdEventEncoder.encodedLength() + HEADER_LENGTH;
disruptor.publishSlot(slot);
```

**MsgType=X:** Each group entry in the incremental refresh is published as a **separate**
GatewaySlot publication — not batched. 3 entries = 3 publishes.

**Unknown symbol handling:** `idRegistry.instrumentId(symbol) == 0` → log WARN, do NOT publish, return CONTINUE (do not throw).

**MsgType=8 handling:** If ExecutionReport arrives on wrong handler, return CONTINUE — do not publish.

### Acceptance Criteria
- **AC-GW-001**: `ingressTimestampNanos` stamped as first operation. Verified by T-016.
- **AC-GW-002**: MsgType=X: each entry published as separate MarketDataEvent SBE. Verified by T-016.
- **AC-GW-003**: Unknown symbol: discarded, WARN logged, no exception. Verified by T-016.
- **AC-GW-004**: Ring buffer full: market data tick discarded; artio-library thread not blocked. Verified by T-016.

### INPUT → OUTPUT Paths
- **Positive path:** MsgType=W bid entry → 1 GatewaySlot published with correct venueId, priceScaled, entryType=BID.
- **Edge path:** MsgType=X with 3 entries (bid, ask, trade) → 3 separate publishes.
- **Negative path:** Unknown symbol "XRP-USD" → WARN logged, DISRUPTOR_FULL_COUNTER unchanged.
- **Failure path:** Ring full → DISRUPTOR_FULL_COUNTER +1; handler returns without blocking.

### Implementation Steps
1. Create `MarketDataHandler` implementing Artio `SessionHandler`.
2. First line of `onMessage()`: `long ingressNanos = System.nanoTime()`.
3. Lookup `venueId` and `instrumentId` from `IdRegistry`.
4. On MsgType=W: iterate `NoMDEntries` group, publish one slot per entry.
5. On MsgType=X: same — one slot per entry.
6. On unknown symbol: `log.warn(...)`, return `Action.CONTINUE`.
7. On ring full: increment `DISRUPTOR_FULL_COUNTER`, return without blocking.

### Test Implementation Steps
**T-016 MarketDataHandlerTest:**
- `msgTypeW_singleEntry_bidEntry_correctSBEPublished()`
- `msgTypeW_singleEntry_askEntry_correctSBEPublished()`
- `msgTypeX_multipleEntries_allPublishedSeparately()`
- `ingressTimestampNanos_setBeforeDecoding()`
- `unknownSymbol_notPublishedToDisruptor_warnLogged()`
- `disruptorFull_messageDiscarded_counterIncremented()`
- `msgType8_ignored_notPublished()`
- `priceScaling_decimalPrice_scaledCorrectly()`
- `exchangeTimestamp_parsedFromTag60_inNanos()`
- `msgTypeX_singleEntry_publishedOnce()`
- `priceScaling_zeroPrice_publishedAsZero()`
- `msgTypeW_zeroEntries_nothingPublished()`

### Out of Scope
No SBE decoding (cluster-side). No L2 book updates (TASK-017).

### Completion Checklist
- [ ] Files changed: MarketDataHandler.java, T-016
- [ ] Tests added/updated: T-016 (12 test cases)
- [ ] All ACs verified: AC-GW-001, AC-GW-002, AC-GW-003, AC-GW-004
- [ ] Assumptions made: FIX price tags decoded as strings then scaled; `System.nanoTime()` is sub-microsecond
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-010: ExecutionHandler

### Task Description
Implement the Artio `SessionHandler` for FIX MsgType=8 (ExecutionReport). Each execution
report is SBE-encoded into an `ExecutionEvent` and published to the `GatewayDisruptor`.
Handles all ExecType variants including Coinbase quirks (ExecType=I for order status,
ExecType=F with partial/final fill logic, tag 103 conditional decode).

### Spec References
Spec §3.4.2, §9.3 (ExecutionReport handling), §9.4 (FIX tag mapping table).

### Required Existing Inputs
- `TASK-002`: `ExecutionEventEncoder`, `MessageHeaderEncoder`
- `TASK-005`: `IdRegistry`
- `TASK-007`: `GatewayDisruptor`, `GatewaySlot`

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ExecutionHandler.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/ExecutionHandlerTest.java (T-017)
```

### Do Not Modify
`GatewayDisruptor.java`, `GatewaySlot.java`, `MarketDataHandler.java`.

### Authoritative Spec Excerpts

**ExecType mapping (Spec §9.3):**
```
FIX ExecType '0' → SBE ExecType.NEW
FIX ExecType '1' → SBE ExecType.PARTIAL_FILL
FIX ExecType '4' → SBE ExecType.CANCELED        (isFinal=TRUE)
FIX ExecType '5' → SBE ExecType.REPLACED        (isFinal=TRUE)
FIX ExecType '8' → SBE ExecType.REJECTED        (decode tag 103 OrdRejReason)
FIX ExecType 'C' → SBE ExecType.EXPIRED         (isFinal=TRUE)
FIX ExecType 'F' → SBE ExecType.FILL
  → isFinal = (LeavesQty == 0) ? TRUE : FALSE
FIX ExecType 'I' → SBE ExecType.ORDER_STATUS    (does NOT trigger state transition)
```

**Tag 103 (OrdRejReason) — conditional decode:**
```java
byte rejectCode = (execDecoder.execType() == '8' && execDecoder.hasOrdRejReason())
                  ? (byte) execDecoder.ordRejReason() : 0;
```

**clOrdId from tag 11:** parsed as `long` using `Long.parseLong(tag11Value)` — Coinbase sends numeric clOrdId.

**Back-pressure on fills:** `ExecutionEvent` (templateId=2) must NEVER be dropped. Use indefinite retry loop per Spec §3.4.2. Blocking the disruptor thread on fill back-pressure is the correct behavior — a backed-up cluster is an ops emergency.

### Acceptance Criteria
- **AC-FX-005**: ExecType=I does not trigger state transition. Verified by T-017.
- **AC-FX-006**: ExecType=F + LeavesQty=0 sets isFinal=TRUE. Verified by T-017.
- **AC-FX-007**: ExecType=F + LeavesQty>0 sets isFinal=FALSE. Verified by T-017.
- **AC-GW-005**: Tag 103 decoded only when ExecType='8'. Verified by T-017.

### INPUT → OUTPUT Paths
- **Positive path:** ExecType='F', LeavesQty=0 → SBE `ExecutionEvent` with `isFinal=TRUE`, `execType=FILL`.
- **Edge path:** ExecType='F', LeavesQty>0 (partial) → `isFinal=FALSE`, `execType=FILL`.
- **Negative path:** ExecType='I' → SBE published with `execType=ORDER_STATUS`; cluster ignores state transitions.
- **Failure path:** Disruptor full on fill → spin indefinitely (never drop fills).

### Implementation Steps
1. Create `ExecutionHandler` implementing Artio `SessionHandler`.
2. First line: `long ingressNanos = System.nanoTime()`.
3. Implement `mapExecType(char fixExecType) → byte sbeExecType`.
4. Compute `isFinal`: ExecType=F and LeavesQty=0 → TRUE; LeavesQty>0 → FALSE; all others per table.
5. Decode tag 103 only when ExecType='8'.
6. For fills (templateId=2): indefinite retry on ring full. For others: try once, drop on full.
7. Populate all `ExecutionEventEncoder` fields from Spec §9.4 mapping table.
8. Set varString fields: `putVenueOrderId(tag37)`, `putExecId(tag17)`.

### Test Implementation Steps
**T-017 ExecutionHandlerTest:**
- `execTypeNew_correctSBEFields()`
- `execTypeFill_fullFill_isFinalTrue()`
- `execTypeFill_partialFill_isFinalFalse()`
- `execTypeRejected_rejectCodePopulated()`
- `execTypeRejected_noRejectCode_rejectCodeZero()`
- `execTypeCanceled_isFinalTrue()`
- `execTypeReplaced_isFinalTrue()`
- `execTypeOrderStatus_execTypeOrderStatusInSBE()`
- `clOrdId_parsedAsLong_fromTag11()`
- `ingressTimestampNanos_setBeforeDecoding()`
- `venueOrderId_varLengthField_populatedFromTag37()`
- `execId_varLengthField_populatedFromTag17()`
- `missingTag103_rejectCodeZero()`
- `execTypeFill_zeroLeavesQty_isFinalTrue()`
- `execTypeFill_largeFillQty_noOverflow()`
- `clOrdId_maxLongValue_parsedCorrectly()`

### Out of Scope
No order state transitions (TASK-020). No position updates (TASK-021).

### Completion Checklist
- [ ] Files changed: ExecutionHandler.java, T-017
- [ ] Tests added/updated: T-017 (16 test cases)
- [ ] All ACs verified: AC-FX-005, AC-FX-006, AC-FX-007, AC-GW-005
- [ ] Assumptions made: Coinbase always sends numeric clOrdId in tag 11
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-011: VenueStatusHandler

### Task Description
Implement the Artio `SessionAcquireHandler` that publishes `VenueStatusEvent` SBE messages
when the FIX session connects or disconnects. This triggers recovery in the cluster via
`RecoveryCoordinator.onVenueStatus()`.

### Spec References
Spec §3.4.2, §4.4 (recovery flow trigger), §3.4 (VenueStatusHandler description).

### Required Existing Inputs
- `TASK-002`: `VenueStatusEventEncoder`
- `TASK-005`: `IdRegistry`
- `TASK-007`: `GatewayDisruptor`

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/VenueStatusHandler.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/VenueStatusHandlerTest.java (T-023)
```

### Authoritative Spec Excerpts

**Artio SessionAcquireHandler pattern (Spec §3.4 TASK-011 section):**
```java
public final class VenueStatusHandler implements SessionAcquireHandler {
    @Override
    public SessionHandler onSessionAcquired(Session session, SessionInfo sessionInfo) {
        int venueId = idRegistry.venueId(sessionInfo.sessionId());
        publishVenueStatus(venueId, VenueStatus.CONNECTED);
        return new VenueSessionHandler(venueId, this);
    }
}

class VenueSessionHandler implements SessionHandler {
    @Override
    public Action onDisconnect(int libraryId, Session session) {
        publishVenueStatus(this.venueId, VenueStatus.DISCONNECTED);
        return CONTINUE;
    }
    // All other onMessage() calls: return CONTINUE (no-op)
}
```

**Artio heartbeat timeout → `onDisconnect()`** — handled by the `VenueSessionHandler` above.

**Unknown sessionId (from `venueId()` returning 0):** throw `ConfigValidationException("Unknown FIX session: " + sessionId)` — the system cannot operate without a valid venue mapping.

### Acceptance Criteria
None owned directly. Enables AC-RC-001 through AC-RC-009 (recovery is triggered by VenueStatusEvent).

### INPUT → OUTPUT Paths
- **Positive path:** `onSessionAcquired()` → 1 `VenueStatusEvent{CONNECTED}` published to Disruptor.
- **Negative path:** Unknown sessionId → `ConfigValidationException`.
- **Edge path:** Multiple logons on same session → only one CONNECTED event per `onSessionAcquired()`.
- **Failure path:** Disruptor full → drop CONNECTED event (recoverable; Artio will call again on retry).

### Implementation Steps
1. Create `VenueStatusHandler` implementing `SessionAcquireHandler`.
2. `onSessionAcquired()`: lookup venueId, publish CONNECTED, return inner `VenueSessionHandler`.
3. `VenueSessionHandler.onDisconnect()`: publish DISCONNECTED, return CONTINUE.
4. All other `onMessage()` in `VenueSessionHandler`: return CONTINUE.

### Test Implementation Steps
**T-023 VenueStatusHandlerTest:**
- `onSessionAcquired_publishesConnectedEvent()`
- `onDisconnect_publishesDisconnectedEvent()`
- `onSessionAcquired_venueIdResolvedFromSessionId()`
- `onSessionAcquired_unknownSessionId_throws()`
- `onDisconnect_correctVenueId_inEvent()`
- `multipleLogons_sameSession_onlyOneConnectedEvent()`
- `onDisconnect_afterDisconnect_idempotent()`

### Out of Scope
No recovery logic (TASK-022). FIX session events only.

### Completion Checklist
- [ ] Files changed: VenueStatusHandler.java, T-023
- [ ] Tests added/updated: T-023 (7 test cases)
- [ ] All ACs verified: n/a (enabler for recovery ACs)
- [ ] Assumptions made: Artio `SessionAcquireHandler` is the correct interface for v0.175 (spec §21.1 pinned version)
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-012: AeronPublisher (Disruptor Consumer)

### Task Description
Implement the single consumer of the `GatewayDisruptor`. On each `GatewaySlot`, it calls
`aeronPublication.offer()` with the pre-encoded SBE buffer. Fill messages (templateId=2)
use indefinite retry; all others spin up to 10µs then drop with counter increment.

### Spec References
Spec §3.4.2 (AeronPublisher policy), §14.3/14.4 (implementation detail).

### Required Existing Inputs
- `TASK-007`: `GatewayDisruptor`, `GatewaySlot`
- `TASK-002`: `MessageHeaderDecoder` (for templateId peek)

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/AeronPublisher.java
```

### Do Not Modify
`GatewayDisruptor.java`, `GatewaySlot.java`.

### Authoritative Spec Excerpts

**Priority-aware back-pressure (Spec §3.4.2):**
```java
// In AeronPublisher.onEvent(GatewaySlot slot, long sequence, boolean endOfBatch):
headerPeek.wrap(slot.buffer, 0);
boolean isFill = headerPeek.templateId() == ExecutionEventDecoder.TEMPLATE_ID;

if (isFill) {
    // NEVER drop fills — spin indefinitely
    long result;
    do { result = aeronPublication.offer(slot.buffer, 0, slot.length);
         Thread.onSpinWait();
    } while (result < 0);
} else {
    // Market data, venue status, balance: spin up to 10µs then drop
    long spinStart = System.nanoTime();
    long result;
    do {
        result = aeronPublication.offer(slot.buffer, 0, slot.length);
        if (result > 0) break;
        Thread.onSpinWait();
    } while (System.nanoTime() - spinStart < 10_000L);
    if (result < 0) metricsCounters.increment(AERON_BACKPRESSURE_COUNTER);
}
slot.reset();  // ALWAYS reset — even if offer failed
```

### Acceptance Criteria
None directly. Enables all cluster-side processing (TASK-017 through TASK-031).

### INPUT → OUTPUT Paths
- **Positive path:** Market data slot → `aeronPublication.offer()` succeeds in < 200ns → cluster receives.
- **Fill path:** Fill slot → indefinite spin until cluster accepts — blocks disruptor thread intentionally.
- **Back-pressure path:** Non-fill slot, Aeron slow → spin 10µs → drop → `AERON_BACKPRESSURE_COUNTER +1`.
- **Failure path:** Aeron closed → `offer()` returns `Publication.CLOSED (-4)` — treat as back-pressure, drop non-fills.

### Implementation Steps
1. Create `AeronPublisher` as Disruptor `EventHandler<GatewaySlot>`.
2. Pre-allocate `MessageHeaderDecoder headerPeek` (no allocation on hot path).
3. Implement `onEvent()` with the two-branch back-pressure policy above.
4. Always call `slot.reset()` at end of `onEvent()`.
5. Wire as single consumer in `GatewayDisruptor.start()`.

### Test Implementation Steps
IT-001 exists from TASK-007. Verify `AeronPublisher` integration:
- `fillMessage_aeronSlow_spinsIndefinitely_noDrops()`
- `nonFillMessage_aeronSlow_droppedAfter10us_counterIncremented()`
- `slotReset_calledAfterEveryEvent_evenOnFailure()`

### Out of Scope
No SBE encoding. No message routing.

### Completion Checklist
- [ ] Files changed: AeronPublisher.java, IT-001 (3 additional test cases)
- [ ] Tests added/updated: IT-001 (3 new cases added)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: `Thread.onSpinWait()` is more efficient than empty loop on Java 21
- [ ] Blockers identified: none
- [ ] Follow-up items: none


---

## Task Card TASK-013: OrderCommandHandler + ExecutionRouter

### Task Description
Implement the gateway-egress-thread component that decodes SBE commands from the Aeron
cluster egress and routes them to Artio as FIX messages. Critical requirement: a single
Aeron fragment may contain multiple concatenated SBE messages (arb dual-leg batch) — the
`onFragment()` loop MUST iterate the entire buffer until exhausted.

### Spec References
Spec §2.2.4 (cursor advancement rule), §3.4 (TASK-013 card), §9.5–9.7 (FIX message formats),
§8.13 (ReplaceOrderCommand).

### Required Existing Inputs
- `TASK-002`: all command decoders (`NewOrderCommandDecoder`, `CancelOrderCommandDecoder`, etc.)
- `TASK-005`: `IdRegistry`
- `TASK-008`: `CoinbaseLogonStrategy` (Artio session reference)
- `TASK-010`: `ExecutionHandler` (Artio session send pattern)

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/OrderCommandHandler.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ExecutionRouter.java         (interface)
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ExecutionRouterImpl.java
platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml                      (extend TASK-008 dictionary with outbound order messages)
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/OrderCommandHandlerTest.java (T-019)
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/ArbBatchParsingTest.java     (IT-007)
```

### Do Not Modify
`GatewayDisruptor.java`, `ExecutionHandler.java`, all cluster source files.

### Authoritative Spec Excerpts

**Fragment iteration loop — MUST iterate entire buffer (Spec §2.2.4 + §3.4 TASK-013):**
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
                offset += hdrLen + blockLen;  // fixed-length: safe to use blockLen
            }
            case CancelOrderCommandDecoder.TEMPLATE_ID -> {
                cancelDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                executionRouter.routeCancel(cancelDecoder);
                offset += hdrLen + cancelDecoder.encodedLength(); // varString: MUST use encodedLength()
            }
            case ReplaceOrderCommandDecoder.TEMPLATE_ID -> {
                replaceDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                executionRouter.routeReplace(replaceDecoder);
                offset += hdrLen + replaceDecoder.encodedLength();
            }
            case OrderStatusQueryCommandDecoder.TEMPLATE_ID -> {
                statusQueryDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                executionRouter.routeStatusQuery(statusQueryDecoder);
                offset += hdrLen + statusQueryDecoder.encodedLength();
            }
            case BalanceQueryRequestDecoder.TEMPLATE_ID -> {
                balanceQueryDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                restPoller.onBalanceQueryRequest(balanceQueryDecoder);
                offset += hdrLen + balanceQueryDecoder.encodedLength();
            }
            case RecoveryCompleteEventDecoder.TEMPLATE_ID -> {
                recoveryDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                venueStatusHandler.onRecoveryComplete(recoveryDecoder);
                offset += hdrLen + recoveryDecoder.encodedLength();
            }
            default -> {
                log.warn("Unknown egress templateId: {} at cursor={}; aborting fragment parse",
                          templateId, offset);
                return;  // cannot safely advance — stop fragment processing
            }
        }
    }
}
```

**CRITICAL cursor rule:** Use `blockLen` ONLY for `NewOrderCommand` (fixed-length, no `<data>` fields).
Use `decoder.encodedLength()` for ALL messages with varString `venueOrderId` (Cancel, Replace, OrderStatusQuery).
Using `blockLen` on varString messages corrupts the cursor and silently drops subsequent messages — including the second arb leg.

**FIX back-pressure (Spec §3.4 TASK-013, Artio 0.175):**
```java
// Populate the generated outbound FIX encoder once, then retry Session.trySend(Encoder).
for (int attempt = 0; attempt < 3; attempt++) {
    long result = session.trySend(encoder);
    if (result >= 0) { return; }
    LockSupport.parkNanos(1_000L);
}
metricsCounters.increment(ARTIO_BACK_PRESSURE_COUNTER);
// publish RejectEvent SBE to cluster so OrderManager transitions to REJECTED
```

**Artio 0.175 outbound codec requirement:**
`ExecutionRouterImpl` must use Artio generated outbound encoders and
`Session.trySend(Encoder)`. Do **not** use the older draft
`tryClaim()/claimBuffer()/commit()` path; those methods are not a public
`Session` API in Artio `0.175`. TASK-013 therefore also owns extending
`platform-gateway/src/main/resources/coinbase-fix42-dictionary.xml` beyond Logon
to include the outbound messages used here: `NewOrderSingle`,
`OrderCancelRequest`, `OrderCancelReplaceRequest`, and `OrderStatusRequest`.
After dictionary extension, rerun `:platform-gateway:generateFixCodecs` and verify
the generated builders expose `NewOrderSingleEncoder`,
`OrderCancelRequestEncoder`, `OrderCancelReplaceRequestEncoder`, and
`OrderStatusRequestEncoder`.

**FIX message format (Spec §9.5–9.6):**
```
NewOrderSingle (35=D): 11(clOrdId), 21=1, 38(qty), 40(ordType), 44(price — LIMIT only),
                        54(side), 55(symbol), 59(TIF), 60(transactTime), 1(account)
OrderCancelRequest (35=F): 11, 41(origClOrdId), 37(venueOrderId), 54, 55, 38, 60
OrderStatusRequest (35=H): 11, 37(if present)
```

**Replace for Coinbase = no MsgType=G.** The cluster drives cancel+new sequencing.
The gateway is a pure forwarder — it routes whatever commands the cluster publishes.
If it receives `ReplaceOrderCommand`, it sends MsgType=G (for non-Coinbase venues only).

### Acceptance Criteria
- **AC-FX-002**: NewOrderSingle contains all required tags from Spec §9.5. Verified by T-019.
- **AC-FX-003**: OrderCancelRequest contains tags 11, 41, 37, 54, 55, 38. Verified by T-019.
- **AC-FX-004**: Coinbase replace implemented as cancel+new (no MsgType=G for Coinbase). Verified by T-019.
- **AC-GW-006**: Artio back-pressure: retry 3× with 1µs sleep → reject event. Verified by T-019.
- **AC-GW-007**: Cancel sent before new order on replace. Verified by T-019 and ET-002.

### INPUT → OUTPUT Paths
- **Positive path:** Single-message fragment `NewOrderCommand` → 1 FIX NewOrderSingle with correct tags.
- **Arb batch path:** Fragment with 2 concatenated `NewOrderCommand` messages → exactly 2 `session.send()` calls with correct clOrdId, side, venue.
- **Mixed batch path:** `CancelOrderCommand` (varString) + `NewOrderCommand` (fixed) in one fragment → cursor advances correctly, both decoded.
- **Negative path:** Unknown templateId in fragment → `default` branch logs WARN, stops processing that fragment.
- **Back-pressure path:** `session.trySend(encoder)` fails 3× → `ARTIO_BACK_PRESSURE_COUNTER +1`, reject event published to cluster.

### Implementation Steps
1. Create `ExecutionRouter` interface: `routeNewOrder()`, `routeCancel()`, `routeReplace()`, `routeStatusQuery()`.
2. Extend `coinbase-fix42-dictionary.xml` so Artio generates outbound order encoders for `D`, `F`, `G`, and `H`.
3. Create `ExecutionRouterImpl` — wraps Artio session, populates generated FIX encoders per Spec §9.5–9.7, and sends via `Session.trySend(Encoder)`.
4. Create `OrderCommandHandler` implementing Artio `FragmentHandler` with the complete iteration loop above.
5. Pre-allocate one decoder instance per templateId — no new allocations in `onFragment()`.
6. Implement `Session.trySend(Encoder)` retry with `LockSupport.parkNanos(1_000L)` between attempts.
7. Implement reject-event publish to cluster on back-pressure exhaustion.

### Test Implementation Steps
**T-019 OrderCommandHandlerTest:**
- `newOrderCommand_producesCorrectFIXNewOrderSingle()`
- `newOrderCommand_allRequiredTagsPresent()`
- `cancelCommand_producesCorrectFIXCancelRequest()`
- `cancelCommand_allRequiredTagsPresent()`
- `replaceCommand_coinbase_notRouted_clusterOwnsSequencing()`
- `orderStatusQueryCommand_sends35H()`
- `balanceQueryRequest_enqueuedToRestPoller()`
- `recoveryCompleteEvent_loggedOnly_noFIXAction()`
- `artioBackPressure_retriesTrySendUpToThreeTimes()`
- `artioBackPressure_allTrySendRetriesFail_rejectEventPublished()`
- `newOrder_marketOrder_tag44Omitted()`
- `arbLegBatch_twoNewOrderCommandsOneFragment_bothFIXSent()`

**IT-007 ArbBatchParsingTest:**
- `singleFragment_twoNewOrderCommands_twoFIXSends_correctClOrdIds()`
- `singleFragment_cancelThenNew_cursorAdvancesCorrectly()`
- `singleFragment_unknownTemplateId_fragmentProcessingStops_noException()`

### Out of Scope
No cluster-side order management. No SBE encoding at gateway (only decoding).

### Completion Checklist
- [ ] Files changed: OrderCommandHandler.java, ExecutionRouter.java, ExecutionRouterImpl.java, coinbase-fix42-dictionary.xml, T-019, IT-007
- [ ] Tests added/updated: T-019 (12 cases), IT-007 (3 cases)
- [ ] All ACs verified: AC-FX-002, AC-FX-003, AC-FX-004, AC-GW-006, AC-GW-007
- [ ] Assumptions made: NewOrderCommand is truly fixed-length (no varString); CancelOrderCommand always has venueOrderId; Artio `Session.trySend(Encoder)` is the public outbound send API for version `0.175`
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-014: RestPoller

### Task Description
Implement the REST poller that runs on the `rest-poller-thread` and handles `BalanceQueryRequest`
commands from the cluster egress. On each request it calls Coinbase `GET /accounts`, parses
the JSON response, and publishes `BalanceQueryResponse` SBE messages (one per configured
currency) to the `GatewayDisruptor`.

### Spec References
Spec §3.4 (TASK-014 card), §22.4 (`[rest]` config), §16.3 (recovery phase 5).

### Required Existing Inputs
- `TASK-002`: `BalanceQueryResponseEncoder`, `BalanceQueryRequestDecoder`
- `TASK-004`: `RestConfig`, `CredentialsConfig`
- `TASK-005`: `IdRegistry`
- `TASK-007`: `GatewayDisruptor`

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/RestPoller.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/RestPollerTest.java           (T-022)
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/RestPollerIntegrationTest.java (IT-006)
```

### Authoritative Spec Excerpts

**JSON parsing (Spec §14.4):**
```java
JSONArray accounts = new JSONArray(responseBody);
for (int i = 0; i < accounts.length(); i++) {
    JSONObject acct = accounts.getJSONObject(i);
    String currency = acct.getString("currency");
    String balance  = acct.getString("available");  // use 'available' NOT 'balance'
    int instrumentId = idRegistry.instrumentId(currency + "-USD");
    if (instrumentId == 0) continue;  // not a configured instrument — skip silently
    long balanceScaled = ConfigManager.parseScaled(balance);
    // ... publish BalanceQueryResponse SBE
}
```

**Auth headers (Spec §14.4):**
```java
String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
String prehash   = timestamp + "GET" + "/accounts";
String signature = Base64.getEncoder().encodeToString(
    mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
// Headers: CB-ACCESS-KEY, CB-ACCESS-SIGN, CB-ACCESS-TIMESTAMP, CB-ACCESS-PASSPHRASE
```

**Error sentinel (Spec §3.4 TASK-014):** On HTTP error or timeout, publish `BalanceQueryResponse` with `balanceScaled = -1L` (sentinel). The cluster's `RecoveryCoordinator` treats `-1` as a fetch failure and activates the kill switch after the 5s timeout.

### Acceptance Criteria
None owned directly. Enables AC-RC-002, AC-RC-005, AC-RC-006 (balance reconciliation steps).

### INPUT → OUTPUT Paths
- **Positive path:** HTTP 200, BTC-USD in accounts → `BalanceQueryResponse{instrumentId=1, balanceScaled=X}` published.
- **Edge path:** Two configured currencies → two separate `BalanceQueryResponse` publishes.
- **Negative path:** Unknown currency (e.g. "LINK") → `idRegistry.instrumentId("LINK-USD")==0` → silently skipped.
- **Failure path:** HTTP 401 → `BalanceQueryResponse{balanceScaled=-1}` sentinel published.
- **Exception path:** Request timeout after `config.rest().timeoutMs()` → same sentinel.

### Implementation Steps
1. Create `RestPoller` with `HttpClient`, pre-allocated `Mac` for auth.
2. Implement `onBalanceQueryRequest(decoder)` — enqueues the requestId for the rest-poller-thread.
3. Rest-poller-thread calls `buildAuthenticatedRequest()`, sends, parses JSON.
4. Publish one `BalanceQueryResponse` per configured currency, or `-1` sentinel on failure.
5. Use `org.json:json:20240303` for JSON parsing.

### Test Implementation Steps
**T-022 RestPollerTest:**
- `balanceRequest_httpSuccess_responsePublished()`
- `balanceRequest_parsesAllCurrencies()`
- `balanceRequest_httpTimeout_errorSentinelPublished()`
- `balanceRequest_http401_errorSentinelPublished()`
- `authHeader_cbAccessSign_computedCorrectly()`
- `unknownCurrency_skipped_noResponsePublished()`
- `available_usedNotBalance_forReconciliation()`
- `balanceRequest_twoConfiguredCurrencies_twoResponsesPublished()`

**IT-006 RestPollerIntegrationTest:**
- `concurrentBalanceRequests_serialized_noRaceCondition()`
- `httpServer_unavailable_sentinelWithinTimeout()`

### Out of Scope
No FIX messaging. REST only.

### Completion Checklist
- [ ] Files changed: RestPoller.java, T-022, IT-006
- [ ] Tests added/updated: T-022 (8 cases), IT-006 (2 cases)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: `java.net.http.HttpClient` available (Java 21 standard library)
- [ ] Blockers identified: none
- [ ] Follow-up items: none


---

## Task Card TASK-015: ArtioLibraryLoop + EgressPollLoop

### Task Description
Implement the two CPU-pinned gateway poll loops. `ArtioLibraryLoop` drives Artio's duty cycle
(`fixLibrary.poll()`) on the `artio-library` thread. `EgressPollLoop` polls the Aeron cluster
egress subscription for commands and dispatches them to `OrderCommandHandler` on the `gateway-egress`
thread. Also implements `ThreadAffinityHelper` for CPU pinning.

### Spec References
Spec §25.1 (ArtioLibraryLoop), §25.2 (EgressPollLoop), §13.1 (thread inventory).

### Required Existing Inputs
- `TASK-001`: build files (net.openhft:affinity dependency)
- `TASK-013`: `OrderCommandHandler` (dispatched by EgressPollLoop)

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ArtioLibraryLoop.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/EgressPollLoop.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ThreadAffinityHelper.java
```

### Authoritative Spec Excerpts

**ArtioLibraryLoop (Spec §25.1):**
```java
public final class ArtioLibraryLoop implements Runnable {
    private static final int FRAGMENT_LIMIT = 10;
    private final FixLibrary   fixLibrary;
    private final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
    private volatile boolean   running      = true;

    @Override
    public void run() {
        ThreadAffinityHelper.pin(Thread.currentThread(), config.cpu().artioLibraryThread());
        while (running) {
            int workCount = fixLibrary.poll(FRAGMENT_LIMIT);
            idleStrategy.idle(workCount);
        }
        fixLibrary.close();
    }
    public void stop() { running = false; }
}
```

**EgressPollLoop (Spec §25.2):**
```java
public final class EgressPollLoop implements Runnable {
    private static final int FRAGMENT_LIMIT = 10;
    private final Subscription       egressSubscription;
    private final OrderCommandHandler commandHandler;
    private final IdleStrategy        idleStrategy = new BusySpinIdleStrategy();
    private volatile boolean          running      = true;
    private final FragmentHandler     handler;  // pre-allocated: commandHandler::onFragment

    @Override
    public void run() {
        ThreadAffinityHelper.pin(Thread.currentThread(), config.cpu().gatewayEgressThread());
        while (running) {
            int workCount = egressSubscription.poll(handler, FRAGMENT_LIMIT);
            idleStrategy.idle(workCount);
        }
    }
}
```

**ThreadAffinityHelper (Spec §15):**
```java
public final class ThreadAffinityHelper {
    public static void pin(Thread thread, int cpu) {
        if (cpu <= 0) return;  // 0 = no pinning (dev mode)
        try {
            AffinityLock.acquireLock(cpu);
        } catch (Exception e) {
            log.warn("CPU pinning failed for {} to CPU {}: {}", thread.getName(), cpu, e.getMessage());
            // Non-fatal: continue without pinning
        }
    }
}
```

### Acceptance Criteria
No ACs owned directly. Enables AC-SY-001 (startup within 10s).

### Implementation Steps
1. Create `ThreadAffinityHelper` — cpu ≤ 0 = no-op; otherwise `AffinityLock.acquireLock(cpu)`.
2. Create `ArtioLibraryLoop` per spec — `BusySpinIdleStrategy`, volatile `running` flag.
3. Create `EgressPollLoop` per spec — pre-allocated `FragmentHandler handler = commandHandler::onFragment`.
4. Both loops: thread name from config, pinning via `ThreadAffinityHelper`.

### Test Implementation Steps
No dedicated unit test. Verified by ET-006 `fullSystemSmoke_startsAndTrades_noExceptions()` (started in TASK-016/TASK-033).

### Completion Checklist
- [ ] Files changed: ArtioLibraryLoop.java, EgressPollLoop.java, ThreadAffinityHelper.java
- [ ] Tests added/updated: none (verified by ET-006 in TASK-016)
- [ ] All ACs verified: n/a
- [ ] Assumptions made: `AffinityLock.acquireLock(cpu)` from net.openhft:affinity available
- [ ] Blockers identified: none
- [ ] Follow-up items: Wire in GatewayMain (TASK-016)

---

## Task Card TASK-016: GatewayMain

### Task Description
Wire all gateway components into a runnable process. `GatewayMain.main()` executes the
9-step startup sequence from Spec §25.3: load config, connect Aeron, connect cluster client,
build all components, start Artio, run warmup harness, enable FIX session, start poll loops,
await shutdown. Warmup MUST complete before any FIX session is initiated (AC-SY-006).
Consumes `GatewayConfig.forTest()` and `CpuConfig.noPinning()` (both owned by TASK-004 per Spec §22 / §G.4) from the test harness; does not author them.
`GatewayMain` also resolves missing Coinbase credentials from the standalone development
environment variables `COINBASE_API_KEY`, `COINBASE_API_SECRET_BASE64`, and
`COINBASE_API_PASSPHRASE`; this is a runtime startup fallback, not TOML parsing.

### Spec References
Spec §25.3 (GatewayMain startup sequence), §G.4 (GatewayConfig.forTest()), §19 (deployment).

### Required Existing Inputs
- `TASK-004`: all config records (`ConfigManager`, `GatewayConfig.forTest()`, `FixConfig`, `CpuConfig.noPinning()`, `VenueConfig`, `InstrumentConfig`, `WarmupConfig.development()`, etc.)
- `TASK-005`: `IdRegistry` interface + `IdRegistryImpl` (including the V9.7/V10 `registerSession(venueId, session.id())` method)
- `TASK-006`: `CoinbaseExchangeSimulator`, `TradingSystemTestHarness`
- `TASK-007` through `TASK-015`: all gateway components (includes `TASK-013`'s `OrderCommandHandler`, which `GatewayMain.main()` constructs with the `ExecutionRouter`, `RestPoller`, and recovery-complete hook, then passes into the cluster-egress listener)
- No dependency on TASK-033. TASK-016 **owns and creates** the `WarmupHarness` interface (signature inlined below). TASK-033 produces the concrete `WarmupHarnessImpl` that implements this interface, and is therefore blocked on TASK-016, not the reverse.

> **Formalized ownership (replaces v1.1 stub guidance).** `WarmupHarness.java` is a
> production interface owned by TASK-016. TASK-033 lists it as a Required Existing Input
> and creates `WarmupHarnessImpl` that implements it. No stub, no placeholder, no forward
> reference — the interface is production code with a fully-specified signature (see
> "Authoritative Spec Excerpts" below).

### Expected Modified Files
```
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/GatewayMain.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/WarmupHarness.java          (interface — full signature below; impl in TASK-033)
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/test/TradingSystemTestHarness.java  (update)
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/ExecutionFeedbackE2ETest.java (ET-002)
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/GatewayMainTest.java          (credential-resolution unit coverage)
```

> **Note on `GatewayConfig.forTest()` and `CpuConfig.noPinning()`.** These factories are
> owned by TASK-004 per Spec §22 and §G.4 (see §5.4 ownership table). By the time TASK-016
> runs in week 5, both factories already exist from TASK-004 (week 1). TASK-016 calls them
> from `TradingSystemTestHarness.start()` but does not add them. The `forTest()` body is
> shown in "Authoritative Spec Excerpts" below as reference for what TASK-016 consumes,
> not as code TASK-016 writes.

### Do Not Modify
All component classes from TASK-007–015.

### Authoritative Spec Excerpts

**WarmupHarness interface — full signature owned by this card.**

The `WarmupHarness` interface is derived from Spec §26.2 (which defines a concrete
`WarmupHarness` class with a single public method). The plan splits the spec's concrete
class into an interface (TASK-016) plus an impl (TASK-033) to break the forward
dependency between `GatewayMain` and the warmup implementation's transitive deps (the
cluster-side `TradingClusteredService`, synthetic SBE encoders, and `WarmupClusterShim`).

Inline this exact interface into `WarmupHarness.java`:

```java
package ig.rueishi.nitroj.exchange.gateway;

import uk.co.real_logic.artio.library.FixLibrary;

/**
 * JVM warmup harness interface — run before any live FIX connectivity (AC-SY-006).
 *
 * Single method: {@link #runGatewayWarmup(FixLibrary)} — invoked by {@code GatewayMain}
 * immediately before {@code FixLibrary.initiate(...)} to ensure C2 compilation of the
 * hot paths has completed. The concrete {@code WarmupHarnessImpl} (TASK-033, located in
 * {@code platform-tooling}) drives the cluster's {@code TradingClusteredService.onSessionMessage()}
 * with synthetic market-data and execution events; the implementation details live there.
 *
 * Contract:
 * - {@code runGatewayWarmup} MUST complete before {@code GatewayMain} initiates the
 *   FIX logon. AC-SY-006 is verified by ET-006 checking log-line ordering.
 * - On completion, all warmup-synthesized state (order states, positions) has been
 *   cleared via {@code TradingClusteredService.resetWarmupState()}; no warmup residue
 *   persists into live trading.
 * - Exceptions from this method MUST abort startup — never swallowed. A failed warmup
 *   means the JVM's C2 compiler hasn't stabilised; trading with unwarmed code violates
 *   every latency AC.
 *
 * Implementations:
 * - {@code WarmupHarnessImpl} — production implementation (TASK-033, §26.2).
 * - Test doubles — unit tests may provide a no-op implementation; NOT a production stub.
 */
public interface WarmupHarness {

    /**
     * Run warmup iterations sufficient to trigger C2 compilation of all hot-path methods.
     * Blocks the calling thread until warmup completes (typically 100K iterations,
     * ~100-500ms on modern hardware).
     *
     * @param fixLibrary the Artio FixLibrary that will be used for live trading after
     *                    warmup. The implementation does NOT send any FIX traffic via
     *                    this library — it is passed only so the impl can query
     *                    library-level state (connection readiness, session acquisition
     *                    status) to coordinate warmup with library initialization.
     * @throws IllegalStateException if warmup does not complete successfully (e.g.
     *                                C2 verification fails when {@code requireC2Verified=true}).
     */
    void runGatewayWarmup(FixLibrary fixLibrary);
}
```

**Rationale for the `FixLibrary` parameter.** GatewayMain's startup sequence (Spec §25.3)
initializes the FixLibrary before warmup runs; the warmup may need to poll the library's
duty cycle to keep its event loop alive during warmup iterations. The parameter is passed
by the caller (GatewayMain) rather than captured via a field so the interface stays
thread-and-lifecycle-neutral.

**Note for TASK-033 implementer.** The production impl consumes a `TradingClusteredService`
reference and a `WarmupConfig` — these are injected via the impl's own constructor, not
through this interface. The impl's full body is in Spec §26.2.

**Startup sequence (Spec §25.3) — must be in this exact order:**
```java
// Step 1: Connect Aeron Media Driver
Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(config.aeronDir()));
// Step 2: Connect Aeron Cluster client
AeronCluster clusterClient = AeronCluster.connect(clusterCtx);
// Step 3: Build IdRegistry, then build remaining components.
//         See buildIdRegistry() helper below — V9.7 adds registerSession() after init().
config = GatewayMain.withResolvedCredentials(config, System.getenv());
List<VenueConfig> venues = ConfigManager.loadVenues(venuesConfigPath);
List<InstrumentConfig> instruments = ConfigManager.loadInstruments(instrumentsConfigPath);
IdRegistryImpl idRegistry = buildIdRegistry(venues, instruments);
GatewayDisruptor disruptor = new GatewayDisruptor(...);
RestPoller restPoller = new RestPoller(config.rest(), config.credentials(), idRegistry, disruptor);
ExecutionRouter executionRouter = new ExecutionRouterImpl(...);
VenueStatusHandler venueStatusHandler = new VenueStatusHandler(idRegistry, disruptor);
OrderCommandHandler cmdHandler = new OrderCommandHandler(
    executionRouter,
    restPoller,
    venueStatusHandler::onRecoveryComplete,
    System.getLogger(OrderCommandHandler.class.getName()));
// ... build remaining gateway components (disruptor, handlers) per Spec §25.3
// Step 4: Start Artio FixEngine + FixLibrary
FixEngine fixEngine = FixEngine.launch(engineConfig);
FixLibrary fixLibrary = FixLibrary.connect(libraryConfig);
// Step 5: Run warmup harness — BEFORE FIX logon
warmupHarness.runGatewayWarmup(fixLibrary);
// Step 6: Enable FIX connectivity AFTER warmup
fixLibrary.initiate(buildSessionConfig(config));
// Step 7: Start poll loops (artio-library + gateway-egress threads)
// Step 8: Start GatewayDisruptor consumer
disruptor.start();
// Step 9: Await SIGTERM
ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
barrier.await();
// Graceful shutdown: stop loops, close components in reverse order
```

**`buildIdRegistry(venues, instruments)` helper (V9.7 / Spec §17.8 "Gateway-side wiring"):**
```java
private static IdRegistryImpl buildIdRegistry(
    List<VenueConfig> venues,
    List<InstrumentConfig> instruments) {
    IdRegistryImpl idRegistry = new IdRegistryImpl();
    // init() populates name↔id maps only — does NOT populate venueBySessionId (V9.7/V10).
    // GatewayConfig remains process configuration; registry data is loaded separately
    // via ConfigManager.loadVenues(...) and ConfigManager.loadInstruments(...).
    idRegistry.init(venues, instruments);
    return idRegistry;
}

// Called from gateway-side Artio session creation/acquisition/logon handling.
private static void registerArtioSession(IdRegistryImpl idRegistry, GatewayConfig config, Session session) {
    idRegistry.registerSession(config.venueId(), session.id());
}
```

**GatewayConfig.forTest() (Spec §G.4):**
```java
public static GatewayConfig forTest() {
    return GatewayConfig.builder()
        .venueId(Ids.VENUE_COINBASE_SANDBOX)
        .aeronDir("/dev/shm/aeron-test-" + ProcessHandle.current().pid())
        .fix(FixConfig.builder()
            .senderCompId("TEST_SENDER").targetCompId("TEST_TARGET")
            .heartbeatIntervalS(5).reconnectIntervalMs(1000)
            .artioLogDir("./build/test-artio-" + ProcessHandle.current().pid())
            .artioReplayCapacity(256).build())
        .credentials(CredentialsConfig.hardcoded("test-key", "dGVzdA==", "test-pass"))
        .rest(RestConfig.builder().baseUrl("http://localhost:18080")
              .pollIntervalMs(500).timeoutMs(2000).build())
        .cpu(CpuConfig.noPinning())
        .disruptor(DisruptorConfig.builder().ringBufferSize(256).slotSizeBytes(512).build())
        .warmup(WarmupConfig.development())
        .build();
}
```

### Acceptance Criteria
- **AC-SY-001**: System starts and accepts FIX connection within 10 seconds. Verified by ET-006.
- **AC-SY-002**: Graceful shutdown: all threads stop; no port leaks. Verified by ET-006.
- **AC-SY-006**: Warmup harness completes before FIX session initiated. Verified by ET-006 log ordering.

### INPUT → OUTPUT Paths
- **Positive path:** All components start → FIX session to simulator established → market data flows → orders returned.
- **Failure path:** Aeron MediaDriver not running → `AeronException` with clear message before any FIX attempt.
- **Shutdown path:** SIGTERM received → graceful teardown within 5 seconds; no hanging threads.

### Implementation Steps
1. Implement `GatewayMain.main()` with the 9-step sequence above.
2. Implement the `buildIdRegistry(venues, instruments)` helper per the code block above — calls `IdRegistryImpl.init(venues, instruments)` only. Load `venues` with `ConfigManager.loadVenues(...)` and `instruments` with `ConfigManager.loadInstruments(...)`; do not add registry lists to `GatewayConfig`. Register the live Artio session from gateway-side session creation/acquisition/logon handling by calling `idRegistry.registerSession(config.venueId(), session.id())` (V9.7/V10 wiring per Spec §17.8). This gateway-side Artio session path is the only place in the gateway that calls `registerSession()`.
3. Use `GatewayConfig.forTest()` and `CpuConfig.noPinning()` as-is from TASK-004; no changes to those classes.
4. Add shutdown hooks: `Runtime.getRuntime().addShutdownHook()` to close Artio, Aeron, Disruptor.
5. Wire `TradingSystemTestHarness.start()` to use `GatewayConfig.forTest()` and single-node cluster. The test harness must also call `registerSession()` on its IdRegistry using the simulator/live Artio session ID — otherwise `venueId(long)` will throw when the simulator connects.

### Test Implementation Steps
**ET-002 ExecutionFeedbackE2ETest:**
- `newOrder_ack_orderStateNew()`
- `partialFill_orderPartiallyFilled_portfolioUpdated()`
- `venueReject_orderRejected_noPositionChange()`
- `cancelRace_fillArrivesBeforeCancelAck_positionUpdated()`

ET-006 also verified by this card (smoke test that gateway starts successfully).

### Out of Scope
No cluster startup (TASK-027). No strategy logic.

### Completion Checklist
- [ ] Files changed: GatewayMain.java (includes `buildIdRegistry()` helper and env-var credential fallback), WarmupHarness.java (interface), TradingSystemTestHarness.java, ET-002
- [ ] Verified: `GatewayMain.buildIdRegistry(venues, instruments)` calls `init(venues, instruments)` using registry lists loaded separately from `GatewayConfig`, and gateway-side Artio session handling calls `registerSession(venueId, session.id())` (V9.7/V10 wiring per Spec §17.8).
- [ ] Verified: `TradingSystemTestHarness.start()` also calls `registerSession()` using the simulator/live Artio session ID — otherwise simulator logon will make `venueId(long)` throw.
- [ ] Tests added/updated: ET-002 (4 E2E test cases), GatewayMainTest credential-resolution unit cases
- [ ] All ACs verified: AC-SY-001, AC-SY-002, AC-SY-006
- [ ] Assumptions made: none (WarmupHarness interface is owned by this card; no stubs). GatewayConfig.forTest() and CpuConfig.noPinning() are consumed from TASK-004, not added here. GatewayConfig remains process configuration and does not grow `venues()` / `instruments()` accessors; registry lists are loaded through ConfigManager. IdRegistry / IdRegistryImpl are consumed from TASK-005; `registerSession()` wiring happens in gateway-side Artio session creation/acquisition/logon handling after `buildIdRegistry()` has called `init()`.
- [ ] Blockers identified: none (TASK-033 consumes this card's interface, not the other way around)
- [ ] Follow-up items: TASK-033 provides `WarmupHarnessImpl`; wiring in `GatewayMain.main()` references the interface only


---

## Task Card TASK-017: InternalMarketView (L2 Book)

### Task Description
Implement the off-heap L2 order book using the Panama Foreign Memory API. `L2OrderBook` stores
20 price levels per side in a `MemorySegment` (off-heap, GC-invisible). `InternalMarketView`
holds one `L2OrderBook` per `(venueId, instrumentId)` pair. Strategies call `getBestBid()` /
`getBestAsk()` on every market data tick; the book is updated by `MessageRouter` before strategy
dispatch.

### Spec References
Spec §3.4.4 (InternalMarketView), §10 (L2 book layout, update algorithm, staleness), §18.3 (Panama API).

### Required Existing Inputs
- `TASK-002`: `MarketDataEventDecoder`
- `TASK-003`: `Ids.java` (INVALID_PRICE sentinel)

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/L2OrderBook.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/InternalMarketView.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/L2OrderBookTest.java (T-004)
```

### Authoritative Spec Excerpts

**Panama off-heap layout (Spec §10.2):**
```java
// 20 bid levels + 20 ask levels per book
// Each level: 2 longs (priceScaled, sizeScaled) = 16 bytes
// Total per book: 40 levels × 16 bytes = 640 bytes per book
// GC never sees the MemorySegment — no GC pressure regardless of book count
private static final Arena BOOK_ARENA = Arena.ofShared();

public L2OrderBook(int venueId, int instrumentId, Arena arena) {
    this.segment = arena.allocate(40 * 16L, 8L);  // 640 bytes, 8-byte aligned
    // ... initialize with INVALID_PRICE sentinels
}
```

**apply() signature (C-1 fix, Spec §3.4 TASK-017):**
```java
// Time passed as parameter — no cluster reference inside L2OrderBook
public void apply(MarketDataEventDecoder decoder, long clusterTimeMicros) {
    // Update the book level from the decoder fields
    this.lastUpdateClusterTime = clusterTimeMicros;
}

public boolean isStale(long stalenessThresholdMicros, long currentTimeMicros) {
    return (currentTimeMicros - lastUpdateClusterTime) > stalenessThresholdMicros;
}
```

**InternalMarketView key packing:**
```java
// Key: ((long)venueId << 32) | instrumentId  — primitive, no object allocation
LongObjectHashMap<L2OrderBook> books = new LongObjectHashMap<>();
private long packKey(int venueId, int instrumentId) { return ((long)venueId << 32) | instrumentId; }
```

**Arena lifecycle in tests:**
```java
@BeforeEach void createArena() { testArena = Arena.ofConfined(); }
@AfterEach  void closeArena()  { testArena.close(); }
// Pass testArena to L2OrderBook constructor in tests — prevents native memory leaks
```

### Acceptance Criteria
No ACs owned directly. Enables AC-MM-009 (staleness suppression) and AC-FO-005 (book updated before strategy).

### INPUT → OUTPUT Paths
- **Positive path:** `apply(decoder with BID price=65000, clusterTime=T)` → `getBestBid(v,i) == 65000_00000000L`.
- **Staleness path:** No `apply()` for > `stalenessThresholdMicros` → `isStale() == true` → strategy suppresses.
- **Edge path:** Insert better bid at level 0 → existing levels shift; worst level discarded at capacity=20.
- **Negative path:** `getBestBid()` on empty book → `Ids.INVALID_PRICE`.

### Implementation Steps
1. Create `L2OrderBook` — Panama `MemorySegment`, 20 levels per side, `apply()` + `isStale()` with time params.
2. Implement sorted insert: bids descending (best = highest), asks ascending (best = lowest).
3. Implement `UPDATE` (change size), `DELETE` (remove level, compact), `NEW` (insert at correct position).
4. At capacity (20 levels): discard worst level when a better one arrives.
5. Create `InternalMarketView` — `LongObjectHashMap<L2OrderBook>` keyed by packed `(venueId,instrumentId)`.
6. `InternalMarketView.apply(decoder, clusterTimeMicros)` → lookup or create book, call `book.apply()`.

### Test Implementation Steps
**T-004 L2OrderBookTest** (all tests use `@BeforeEach/@AfterEach` arena lifecycle):
- `insertBid_singleLevel_bestBidCorrect()`
- `insertAsk_singleLevel_bestAskCorrect()`
- `insertBid_multipleLevels_sortedDescending()`
- `insertAsk_multipleLevels_sortedAscending()`
- `updateExistingLevel_sizeChanged_priceUnchanged()`
- `deleteBestBid_secondLevelBecomesBest()`
- `deleteBestAsk_secondLevelBecomesBest()`
- `deleteMiddleLevel_remainingLevelsCompact()`
- `getBestBid_emptyBook_returnsInvalidSentinel()`
- `getBestAsk_emptyBook_returnsInvalidSentinel()`
- `insertAtMaxLevels_betterPrice_evictsWorstAndInserts()`
- `insertAtMaxLevels_worsePrice_discarded()`
- `updateLevel_sizeToZero_levelRemoved()`
- `staleness_noUpdateAfterThreshold_isStaleTrue()`
- `staleness_updateReceived_isStaleCleared()`
- `offHeap_noGCPressure_heapUnchanged()`

### Out of Scope
No strategy logic. No MessageRouter dispatch (TASK-025).

### Completion Checklist
- [ ] Files changed: L2OrderBook.java, InternalMarketView.java, T-004
- [ ] Tests added/updated: T-004 (16 test cases)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: Panama API stable in Java 21 (no incubator flag needed)
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-018: RiskEngine

### Task Description
Implement the 8-check pre-trade risk engine. All state is `long` primitives. The 8 checks run
in strict order on the single cluster thread — no locks, no ring buffers. Soft limits publish
a `RiskEvent` SBE to cluster egress without rejecting. Kill switch is activated automatically
on daily loss breach and manually via `AdminCommandHandler`.

### Spec References
Spec §12 (all subsections), §23.1 (position arrays), §23.2 (rate-limiting sliding window), §17.2 (RiskEngine interface).

### Required Existing Inputs
- `TASK-002`: `RiskEventEncoder`, `MessageHeaderEncoder`
- `TASK-003`: `Ids.java`, `ScaledMath`
- `TASK-004`: `RiskConfig`

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/RiskEngine.java        (interface)
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/RiskEngineImpl.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/RiskDecision.java      (record)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/test/CapturingPublication.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/RiskEngineTest.java    (T-005)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/RiskE2ETest.java       (ET-005)
```

### Authoritative Spec Excerpts

**RiskDecision record (Spec §12.3) — JIT scalar-replaces, zero heap allocation:**
```java
public record RiskDecision(boolean approved, byte rejectCode) {
    public static final RiskDecision APPROVED               = new RiskDecision(true,  (byte) 0);
    public static final RiskDecision REJECT_RECOVERY        = new RiskDecision(false, (byte) 1);
    public static final RiskDecision REJECT_KILL_SWITCH     = new RiskDecision(false, (byte) 2);
    public static final RiskDecision REJECT_ORDER_TOO_LARGE = new RiskDecision(false, (byte) 3);
    public static final RiskDecision REJECT_MAX_LONG        = new RiskDecision(false, (byte) 4);
    public static final RiskDecision REJECT_MAX_SHORT       = new RiskDecision(false, (byte) 5);
    public static final RiskDecision REJECT_MAX_NOTIONAL    = new RiskDecision(false, (byte) 6);
    public static final RiskDecision REJECT_RATE_LIMIT      = new RiskDecision(false, (byte) 7);
    public static final RiskDecision REJECT_DAILY_LOSS      = new RiskDecision(false, (byte) 8);
    public static final RiskDecision REJECT_VENUE_NOT_CONNECTED = new RiskDecision(false, (byte) 9);
}
```

**preTradeCheck() — 8 checks in strict order (Spec §12.2):**
```java
public RiskDecision preTradeCheck(int venueId, int instrumentId, byte side,
                                   long priceScaled, long qtyScaled, int strategyId) {
    if (recoveryLocks[venueId])                          return REJECT_RECOVERY;     // CHECK 1
    if (killSwitchActive)                                return REJECT_KILL_SWITCH;  // CHECK 2
    if (qtyScaled > maxOrderScaled[instrumentId])        return REJECT_ORDER_TOO_LARGE; // CHECK 3
    long proj = netPositionSnapshot[instrumentId]        // CHECK 4
              + (side == Side.BUY ? qtyScaled : -qtyScaled);
    if (proj > maxLongScaled[instrumentId])              return REJECT_MAX_LONG;
    if (proj < -maxShortScaled[instrumentId])            return REJECT_MAX_SHORT;
    long notional = ScaledMath.safeMulDiv(qtyScaled, priceScaled, Ids.SCALE); // CHECK 5
    if (notional > maxNotionalScaled[instrumentId])      return REJECT_MAX_NOTIONAL;
    pruneOrderWindow(cluster.time());                    // CHECK 6
    if (count >= maxOrdersPerSecond)                     return REJECT_RATE_LIMIT;
    long totalDailyLoss = dailyRealizedPnlScaled + dailyUnrealizedPnlScaled; // CHECK 7
    if (totalDailyLoss < -maxDailyLossScaled) {
        activateKillSwitch("daily_loss_limit"); return REJECT_DAILY_LOSS;
    }
    if (!venueConnected[venueId])                        return REJECT_VENUE_NOT_CONNECTED; // CHECK 8
    // Soft limit checks — approve but publish RiskEvent alert
    ... publishSoftAlert() if needed ...
    recordOrderInWindow(cluster.time());
    return APPROVED;
}
```

**Sliding window — corrected implementation (Spec §23.2):**
```java
private void pruneOrderWindow(long nowMicros) {
    while (count > 0) {
        int tail = (head - count + Ids.MAX_ORDERS_PER_WINDOW) % Ids.MAX_ORDERS_PER_WINDOW;
        if (nowMicros - orderTimestampsMicros[tail] > WINDOW_MICROS) count--;
        else break;
    }
}
private void recordOrderInWindow(long nowMicros) {
    orderTimestampsMicros[head] = nowMicros;
    head = (head + 1) % Ids.MAX_ORDERS_PER_WINDOW;
    count++;
}
```

**CapturingPublication (Spec §G.3) — used by risk tests to capture egress SBE:**
```java
public final class CapturingPublication implements Publication {
    private final List<byte[]> captured = new ArrayList<>();
    @Override public long offer(DirectBuffer buf, int offset, int length) {
        byte[] copy = new byte[length]; buf.getBytes(offset, copy); captured.add(copy);
        return captured.size();
    }
    public int countByTemplateId(int templateId) { ... }  // count captured by templateId
    public void clear() { captured.clear(); }
}
```

### Acceptance Criteria
- **AC-RK-001**: Pre-trade checks fire in exact order (CHECK 1–8). Verified by T-005.
- **AC-RK-002**: Kill switch rejects all orders. Verified by T-005, ET-005.
- **AC-RK-003**: Recovery lock rejects orders for affected venue only. Verified by T-005, IT-003.
- **AC-RK-004**: Max position uses net projected position. Verified by T-005.
- **AC-RK-005**: Sliding window expires old orders correctly. Verified by T-005.
- **AC-RK-006**: Daily loss limit breach activates kill switch. Verified by T-005, ET-005-1.
- **AC-RK-007**: Soft limits publish alert without rejecting. Verified by T-005.
- **AC-RK-008**: Daily reset clears daily counters but not kill switch. Verified by T-005, T-013.
- **AC-RK-009**: preTradeCheck() < 5µs (p99). Verified by T-005 (timed assertion).
- **AC-PF-P-002**: Risk check completes in < 5µs. Verified by T-005 performance case.
- **AC-PF-P-003**: Kill switch propagation < 10ms end-to-end. Partial — RiskEngine owns kill switch activation; timing verified by ET-001-4.

### INPUT → OUTPUT Paths
- **Positive path:** All 8 checks pass → `APPROVED`; order recorded in sliding window.
- **Check 1 path:** `recoveryLocks[venueId] == true` → `REJECT_RECOVERY` immediately; kill switch not checked.
- **Check 7 path:** `totalDailyLoss < -maxDailyLossScaled` → activates kill switch AND returns `REJECT_DAILY_LOSS`.
- **Soft limit path:** Position at 80% of limit → `APPROVED` + `RiskEvent.SOFT_LIMIT_BREACH` published to egress.
- **Performance path:** 8 checks in < 5µs measured by `System.nanoTime()` delta.

### Implementation Steps
1. Create `RiskDecision.java` (record with pre-allocated constants).
2. Create `RiskEngine` interface (Spec §17.2): `preTradeCheck()`, `activateKillSwitch()`, `deactivateKillSwitch()`, `isKillSwitchActive()`, `setRecoveryLock()`, `updatePositionSnapshot()`, `onFill()`, `resetDailyCounters()`, `setCluster()`, `writeSnapshot()`, `loadSnapshot()`, `resetAll()`.
3. Create `RiskEngineImpl` — all fields from Spec §12.1 (use those exact field names). Constructor loads limits from `RiskConfig` per Spec §18 TASK-018.
4. Implement `preTradeCheck()` exactly as Spec §12.2 — 8 checks in strict order.
5. Implement `activateKillSwitch()` per Spec §12.4 — publishes RiskEvent SBE, calls `orderManager.cancelAllOrders()`.
6. Implement `pruneOrderWindow()` + `recordOrderInWindow()` per Spec §23.2.
7. Create `CapturingPublication` under `platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/test/` (test helper — exposed as a cluster test fixture for downstream test classes).

### Test Implementation Steps
**T-005 RiskEngineTest** (all cases in spec):
```
check1_recoveryLock_takesOverKillSwitch()
check2_killSwitch_takesOverOrderSize()
check3_orderTooLarge_accepted_belowLimit() / rejected_atLimit()
check4_projectedLong_accepted/rejected
check4_projectedShort_rejected_belowMinusLimit()
check5_notional_rejected/accepted
check6_rateLimit_rejected_atLimit() / accepted / slidingWindow_oldOrdersExpire()
check7_dailyLoss_rejected_exceedsMax() / activatesKillSwitch()
check8_venueNotConnected_rejected() / venueConnected_approved()
activateKillSwitch_isActiveTrue() / deactivateKillSwitch_isActiveFalse()
killSwitch_allVenuesAffected() / recoveryLock_onlyAffectedVenue_otherVenueAllowed()
softLimit_position80Pct_approvedWithAlert() / dailyLoss50Pct_approvedWithAlert()
dailyReset_clearsAllDailyCounters() / doesNotClearKillSwitch() / doesNotClearRecoveryLock()
snapshot_writeAndLoad_killSwitchPreserved() / dailyPnlPreserved()
preTradeCheck_under5Micros() [timed assertion]
```

**ET-005 RiskE2ETest:**
- `dailyLossLimit_exceeded_killSwitchActivated()`
- `orderRateLimit_exceeded_ordersRejectedByRisk()`
- `killSwitch_deactivatedByAdmin_tradingResumes()`

**IT-003 RiskGateIntegrationTest** (uses MessageRouter to route):
- `recoveryLock_onlyAffectedVenue_otherVenueAllowed()` (requires MessageRouter from TASK-025 — integration test created in TASK-025)

### Out of Scope
No order management (TASK-020). No portfolio (TASK-021). No admin commands (TASK-024).

### Completion Checklist
- [ ] Files changed: RiskEngine.java, RiskEngineImpl.java, RiskDecision.java, CapturingPublication.java, T-005, ET-005
- [ ] Tests added/updated: T-005 (22 unit cases), ET-005 (3 E2E cases)
- [ ] All ACs verified: AC-RK-001 through AC-RK-009, AC-PF-P-002, AC-PF-P-003
- [ ] Assumptions made: `ScaledMath.safeMulDiv` handles notional overflow correctly (verified by T-024)
- [ ] Blockers identified: none
- [ ] Follow-up items: IT-003 created in TASK-025; ET-005-3 needs TASK-024 and TASK-032 to pass


---

## Task Card TASK-019: OrderStatePool

### Task Description
Implement the pre-allocated pool of 2048 `OrderState` objects. Pool is a LIFO stack array.
`claim()` pops from the top; `release()` pushes back. Overflow: heap-allocate and increment
counter. `OrderState.reset()` zeroes all primitive fields and nulls `venueOrderId` before
every `claim()` and `release()`.

### Spec References
Spec §7.6 (pool specification and implementation), §7.1 (OrderState fields).

### Required Existing Inputs
- `TASK-003`: `Ids.java` (MAX_ORDERS_PER_WINDOW is not the pool size, but ordering constants)

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderState.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderStatePool.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/order/OrderStatePoolTest.java (T-006)
```

### Authoritative Spec Excerpts

**OrderState fields (Spec §7.1) — all primitives except one String:**
```java
public final class OrderState {
    long   clOrdId, venueClOrdId;
    String venueOrderId;    // only String — written once on first ACK; never compared on hot path
    int    venueId, instrumentId, strategyId;
    byte   side, ordType, timeInForce;
    long   priceScaled, qtyScaled;
    byte   status;  // OrderStatus constants
    long   cumFillQtyScaled, leavesQtyScaled, avgFillPriceScaled;
    long   createdClusterTime, sentNanos, firstAckNanos, lastUpdateNanos, exchangeTimestampNanos;
    int    rejectCode;
    long   replacedByClOrdId;
}
```

**reset() — must zero every field:**
```java
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

**Pool implementation (Spec §7.6):**
```java
private static final int POOL_SIZE      = 2048;
private static final int WARN_THRESHOLD = POOL_SIZE - 100;
private final OrderState[] pool = new OrderState[POOL_SIZE];
private int top = -1;
private int allocated = 0;  // heap overflow count

public OrderStatePool() {
    for (int i = 0; i < POOL_SIZE; i++) pool[i] = new OrderState();
    top = POOL_SIZE - 1;
}
public OrderState claim() {
    if (top >= 0) { OrderState o = pool[top--]; o.reset(); return o; }
    allocated++;
    metricsCounters.increment(ORDER_POOL_EXHAUSTED_COUNTER);
    log.warn("OrderStatePool exhausted; allocating. Total overflow: {}", allocated);
    return new OrderState();
}
public void release(OrderState order) {
    if (top < POOL_SIZE - 1) { order.reset(); pool[++top] = order; }
    // overflow object: discard; GC collects
}
public int available() { return top + 1; }
```

### Acceptance Criteria
- **AC-OL-007**: OrderState pool: zero new allocations after warmup. Verified by T-006 and T-007.
- **AC-PF-P-006**: OrderStatePool: no heap allocation for orders after warmup. Verified by T-006.

### INPUT → OUTPUT Paths
- **Positive path:** `claim()` → returns reset object; `available()` decrements by 1.
- **Release path:** `release(order)` → `available()` increments; next `claim()` returns same object.
- **Overflow path:** `claim()` after 2048 claims → heap allocates; `ORDER_POOL_EXHAUSTED_COUNTER +1`.
- **Edge path:** `release()` of overflow object (pool full) → discarded; no panic.

### Implementation Steps
1. Create `OrderState.java` with all fields from Spec §7.1 and complete `reset()`.
2. Create `OrderStatePool.java` per Spec §7.6 — constructor pre-allocates 2048 instances.
3. `claim()`: pop from `top`; call `reset()`; return. If `top < 0`, heap allocate + warn.
4. `release()`: call `reset()`; push to `top` if pool not full.

### Test Implementation Steps
**T-006 OrderStatePoolTest:**
- `claim_returnsPreAllocatedObject()`
- `claim_objectIsReset_allFieldsZero()`
- `release_returnsObjectToPool()`
- `releaseAndClaim_sameObjectReturned()`
- `claimAll2048_noException()`
- `claim2049th_heapAllocates_counterIncremented()`
- `release_afterHeapOverflow_poolReceivesObject()`
- `orderStateReset_allPrimitiveFieldsZero()`
- `orderStateReset_venueOrderIdNull()`

### Out of Scope
No order lifecycle (TASK-020). Pool infrastructure only.

### Completion Checklist
- [ ] Files changed: OrderState.java, OrderStatePool.java, T-006
- [ ] Tests added/updated: T-006 (9 test cases)
- [ ] All ACs verified: AC-OL-007, AC-PF-P-006
- [ ] Assumptions made: POOL_SIZE=2048 is sufficient for maximum concurrent open orders
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-020: OrderManager

### Task Description
Implement the cluster-side order lifecycle manager. `OrderManagerImpl` maintains a
`LongObjectHashMap<OrderState>` keyed by `clOrdId`. On each `ExecutionEvent` it runs the
state machine from Spec §7.3. Duplicate `execId` detection uses a sliding window set of
10,000 entries. Returns `true` on fills so `MessageRouter` knows to call
`portfolioEngine.onFill()`.

### Spec References
Spec §7 (all subsections), §3.4.6 (OrderManager component responsibilities), §7.5 (idempotency). **Note:** the spec has no `§17.4` — OrderManager interface definition lives inside Section 7 and §3.4.6.

### Required Existing Inputs
- `TASK-002`: `ExecutionEventDecoder`, `CancelOrderCommandEncoder`
- `TASK-003`: `Ids.java`, `OrderStatus`
- `TASK-019`: `OrderState`, `OrderStatePool`

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderManager.java       (interface)
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderManagerImpl.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/order/OrderManagerTest.java   (T-007)
```

### Authoritative Spec Excerpts

**OrderManager interface (Spec §3.4.6 + Section 7):**
```java
public interface OrderManager {
    void createPendingOrder(long clOrdId, int venueId, int instrumentId, byte side,
                            byte ordType, byte timeInForce, long priceScaled, long qtyScaled,
                            int strategyId);
    boolean onExecution(ExecutionEventDecoder decoder);  // returns true if fill
    void cancelAllOrders();
    void writeSnapshot(ExclusivePublication pub);
    void loadSnapshot(Image image);
    void setCluster(Cluster cluster);
    void resetAll();  // for warmup reset — drains pool before clearing map
}
```

**State machine dispatch (Spec §7.3) — all 17 transition rows must be implemented.**
Key transitions:
```java
case PENDING_NEW + NEW      → order.status = NEW; set venueOrderId, firstAckNanos
case PENDING_NEW + REJECTED → order.status = REJECTED; set rejectCode; terminal → release pool
case NEW + FILL(partial)    → order.status = PARTIALLY_FILLED; applyFill(); return true
case NEW + FILL(final)      → order.status = FILLED; applyFill(); terminal → release pool; return true
case PENDING_CANCEL + CANCELED → order.status = CANCELED; terminal → release pool
case PENDING_CANCEL + FILL  → cancel race — fill wins; apply fill; return true
```

**Invalid transition handling (Spec §7.4):**
```java
if (OrderStatus.isTerminal(order.status)) {
    metricsCounters.increment(DUPLICATE_EXECUTION_REPORT_COUNTER); return false;
}
if (!isValidTransition(order.status, execType)) {
    metricsCounters.increment(INVALID_TRANSITION_COUNTER);
    // publish RiskEvent SBE ORDER_STATE_ERROR
    if (isFill(execType)) applyFill(order, decoder);  // fills ALWAYS applied
    return isFill(execType);
}
```

**Duplicate execId detection (Spec §7.5):**
```java
// Sliding window: ObjectHashSet<String> bounded to last 10,000 entries
// prune oldest when set size > 10,000
if (execIdsSeen.contains(execId)) {
    metricsCounters.increment(DUPLICATE_EXECUTION_REPORT_COUNTER); return false;
}
execIdsSeen.add(execId);
```

**resetAll() pool-drain ordering (Spec §7.6):**
```java
public void resetAll() {
    liveOrders.forEach((clOrdId, order) -> pool.release(order));  // drain pool FIRST
    liveOrders.clear();  // then clear map — pool slots not lost
}
```

### Acceptance Criteria
- **AC-OL-001**: All valid state transitions produce correct next state. Verified by T-007.
- **AC-OL-002**: Invalid transitions logged; do not change state. Verified by T-007.
- **AC-OL-003**: Fills on terminal orders applied (revenue never discarded). Verified by T-007.
- **AC-OL-004**: Duplicate execId discarded with metrics increment. Verified by T-007.
- **AC-OL-005**: clOrdId unique and monotonically increasing. Verified by T-007.
- **AC-OL-006**: cancelAllOrders() cancels all live non-terminal orders. Verified by T-007.
- **AC-OL-008**: Snapshot/restore: all live OrderState fields match. Verified by IT-005.

### INPUT → OUTPUT Paths
- **Positive path:** `NEW → FILL(final)` → `FILLED`, pool released, returns `true`.
- **Duplicate fill path:** ExecType=FILL with already-seen execId → counter +1, `false` returned.
- **Cancel race path:** `PENDING_CANCEL + FILL(final)` → `FILLED`, fill applied, pool released.
- **Invalid transition path:** ExecType=NEW on FILLED order → metrics, no state change.
- **Reset path:** `resetAll()` after 5 orders → all 5 pool slots returned; `pool.available() == 2048`.

### Implementation Steps
1. Create `OrderManager` interface.
2. Create `OrderManagerImpl` — `LongObjectHashMap<OrderState> liveOrders`; `OrderStatePool pool`.
3. Implement all 17 state transitions from Spec §7.3 with correct `applyFill()` helper.
4. Implement `cancelAllOrders()` — iterate `liveOrders`, publish `CancelOrderCommand` SBE for each non-terminal.
5. Implement `execId` deduplication sliding window.
6. Implement `writeSnapshot()` / `loadSnapshot()` per Aeron Archive SBE pattern.
7. Implement `resetAll()` with pool-drain ordering.

### Test Implementation Steps
**T-007 OrderManagerTest** — all transitions, edge cases, snapshot, pool drain:
- All 17 transition rows from Spec §7.3 verified
- `fillOnTerminalOrder_appliedNotRejected()` (AC-OL-003)
- `duplicateExecId_discardedCounterIncremented()` (AC-OL-004)
- `cancelAllOrders_publishesCancelForEachLiveOrder()` (AC-OL-006)
- `snapshot_writeAndLoad_allFieldsPreserved()` (AC-OL-008)
- `resetAll_drainPoolFirst_noPoolLeak()` (pool reset ordering)
- `clOrdId_10000Orders_allUnique()` (AC-OL-005)

### Out of Scope
No portfolio updates (TASK-021). No risk checks (TASK-018).

### Completion Checklist
- [ ] Files changed: OrderManager.java, OrderManagerImpl.java, T-007
- [ ] Tests added/updated: T-007 (25+ test cases covering all 17 transitions)
- [ ] All ACs verified: AC-OL-001 through AC-OL-006, AC-OL-008
- [ ] Assumptions made: `ObjectHashSet<String>` from Eclipse Collections; pruned to 10K entries
- [ ] Blockers identified: none
- [ ] Follow-up items: IT-005 (snapshot round-trip) in TASK-022 (moved from TASK-034 in v1.2)


---

## Task Card TASK-021: PortfolioEngine

### Task Description
Implement the cluster-side position and PnL tracker. `PortfolioEngineImpl` maintains one
`Position` per `(venueId, instrumentId)` pair, keyed by a single `long` (`(venueId << 32) | instrumentId`).
On every fill, updates netQty, avgEntryPrice (VWAP via `ScaledMath.vwap()`), and realizedPnL.
Notifies `RiskEngine.updatePositionSnapshot()` after every fill.

### Spec References
Spec §11 (scaled arithmetic), §14 (portfolio formulas), §3.4.7, §17.3 (PortfolioEngine interface).

### Required Existing Inputs
- `TASK-002`: `ExecutionEventDecoder`, `PositionEventEncoder`
- `TASK-003`: `Ids.java`, `ScaledMath` (vwap, safeMulDiv)
- `TASK-018`: `RiskEngine` (interface — calls `updatePositionSnapshot()`)

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/PortfolioEngine.java      (interface)
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/PortfolioEngineImpl.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/Position.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/PortfolioEngineTest.java  (T-008)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/FillCycleIntegrationTest.java (IT-004)
```

### Authoritative Spec Excerpts

**Position key packing (Spec §3.4.7):**
```java
long key = ((long)venueId << 32) | instrumentId;
LongObjectHashMap<Position> positions = new LongObjectHashMap<>();
```

**onFill() formula (Spec §14):**
```java
// On BUY fill:
if (pos.netQtyScaled >= 0) {
    // Adding to long position: VWAP average
    pos.avgEntryPriceScaled = ScaledMath.vwap(
        pos.avgEntryPriceScaled, pos.netQtyScaled, fillPrice, fillQty);
    pos.netQtyScaled += fillQty;
} else {
    // Reducing short position: realize PnL
    long closingQty = Math.min(fillQty, Math.abs(pos.netQtyScaled));
    pos.realizedPnlScaled += ScaledMath.safeMulDiv(
        pos.avgEntryPriceScaled - fillPrice, closingQty, Ids.SCALE);
    pos.netQtyScaled += fillQty;
    if (pos.netQtyScaled > 0) {
        // Position flipped to long: reset avgEntry to fillPrice for the residual
        pos.avgEntryPriceScaled = fillPrice;
    }
}
// After every fill:
riskEngine.updatePositionSnapshot(venueId, instrumentId, pos.netQtyScaled);
```

**unrealizedPnl calculation (Spec §14):**
```java
// Long position: (markPrice - avgEntry) * qty / SCALE
// Short position: (avgEntry - markPrice) * qty / SCALE
public long unrealizedPnl(int venueId, int instrumentId, long markPrice) {
    Position pos = getPosition(venueId, instrumentId);
    if (pos == null || pos.netQtyScaled == 0) return 0;
    long priceDiff = pos.netQtyScaled > 0
        ? markPrice - pos.avgEntryPriceScaled
        : pos.avgEntryPriceScaled - markPrice;
    return ScaledMath.safeMulDiv(priceDiff, Math.abs(pos.netQtyScaled), Ids.SCALE);
}
```

**netQty == 0 after full close:** `avgEntryPriceScaled` must be reset to 0.

### Acceptance Criteria
- **AC-PF-001**: VWAP average price correct for multiple buys. Verified by T-008.
- **AC-PF-002**: Realized PnL correct on long close. Verified by T-008.
- **AC-PF-003**: Realized PnL correct on short close. Verified by T-008.
- **AC-PF-004**: Position correctly flips long → short. Verified by T-008.
- **AC-PF-005**: netQty = 0 after full close; avgEntryPrice = 0. Verified by T-008.
- **AC-PF-006**: Unrealized PnL correct for long position. Verified by T-008.
- **AC-PF-007**: Unrealized PnL correct for short position. Verified by T-008.
- **AC-PF-008**: RiskEngine notified after every fill. Verified by T-008, IT-004.
- **AC-PF-009**: No arithmetic overflow for large prices and quantities. Verified by T-008, T-024.
- **AC-PF-010**: Snapshot/restore: positions and realized PnL match. Verified by IT-005.

### INPUT → OUTPUT Paths
- **Positive path:** BUY fill → netQty increases; avgEntry VWAP computed; riskEngine notified.
- **Long close path:** SELL fill on long position → realizedPnL increases; netQty decreases; avgEntry unchanged until netQty=0.
- **Position flip path:** SELL fill larger than long position → netQty goes negative; avgEntry resets to fillPrice.
- **Overflow path:** 1 BTC at $65K → `safeMulDiv` BigDecimal path → correct result (no ArithmeticException).
- **Snapshot path:** Write state → load → all fields identical.

### Implementation Steps
1. Create `Position` record: `netQtyScaled`, `avgEntryPriceScaled`, `realizedPnlScaled`.
2. Create `PortfolioEngine` interface: `onFill()`, `getNetQty()`, `getAvgEntryPrice()`, `unrealizedPnl()`, `adjustPosition()`, `writeSnapshot()`, `loadSnapshot()`, `resetAll()`, `setCluster()`, `archiveDailyPnl()`.
3. Implement `PortfolioEngineImpl.onFill()` per formula above.
4. After every fill: call `riskEngine.updatePositionSnapshot(venueId, instrumentId, netQty)`.
5. Implement `adjustPosition()` for recovery balance reconciliation (TASK-022 calls this).
6. Implement snapshot read/write.

### Test Implementation Steps
**T-008 PortfolioEngineTest** (test vectors from Spec §20.3):
- `buyFill_singleFill_netQtyCorrect()`
- `buyFill_twoFills_vwapAveragePriceCorrect()` [AC-PF-001]
- `sellFill_closeLong_realizedPnlCorrect()` [AC-PF-002]
- `buyFill_closeShort_realizedPnlCorrect()` [AC-PF-003]
- `sellFill_flipPositionToShort_netQtyNegative()` [AC-PF-004]
- `fullClose_netQtyZero_avgEntryZero()` [AC-PF-005]
- `unrealizedPnl_longPosition_markAboveEntry()` [AC-PF-006]
- `unrealizedPnl_shortPosition_markBelowEntry()` [AC-PF-007]
- `onFill_riskEngineNotified()` [AC-PF-008]
- `overflowProtection_largePriceAndQty_noSilentWrap()` [AC-PF-009]
- `snapshot_positionPreserved()` / `realizedPnlPreserved()` / `multiplePositions_allRestored()`

**IT-004 FillCycleIntegrationTest:**
- `fill_updatesPortfolio_riskSnapshotUpdated()` (OrderManager → PortfolioEngine → RiskEngine chain)
- `multipleFills_cumulativePosition_correct()`
- `fillOnTerminalOrder_portfolioUnchanged()`

### Out of Scope
No strategy position updates (TASK-030/031 call `getNetQty()`). No recovery (TASK-022).

### Completion Checklist
- [ ] Files changed: PortfolioEngine.java, PortfolioEngineImpl.java, Position.java, T-008, IT-004
- [ ] Tests added/updated: T-008 (13 cases), IT-004 (3 cases)
- [ ] All ACs verified: AC-PF-001 through AC-PF-010
- [ ] Assumptions made: ScaledMath.vwap handles BTC-at-$65K overflow via BigDecimal path
- [ ] Blockers identified: none
- [ ] Follow-up items: IT-005 (snapshot round-trip) in TASK-022 (moved from TASK-034 in v1.2)

---

## Task Card TASK-022: RecoveryCoordinator

### Task Description
Implement the cluster-side recovery state machine. `RecoveryCoordinatorImpl` manages per-venue
state through 6 ordered phases: IDLE → AWAITING_RECONNECT → QUERYING_ORDERS → AWAITING_BALANCE
→ COMPLETE. Hard timers (10s for order query, 5s for balance query) activate the kill switch
on expiry. Missing fills are synthesized; orphan orders are canceled; balance discrepancies
within tolerance are adjusted.

### Spec References
Spec §2.6 (recovery requirements), §4.4 (recovery flow), §16 (RecoveryCoordinator full logic), §17.5 (interface).

### Required Existing Inputs
- `TASK-002`: all command/event decoders and encoders
- `TASK-018`: `RiskEngine` (for `activateKillSwitch()`, `setRecoveryLock()`)
- `TASK-020`: `OrderManager` (for `onExecution()`, `forceTransitionToCanceled()`)
- `TASK-021`: `PortfolioEngine` (for `adjustPosition()`)

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/RecoveryCoordinator.java     (interface)
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/RecoveryCoordinatorImpl.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/RecoveryCoordinatorTest.java (T-011)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/RecoveryE2ETest.java         (ET-003)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/SnapshotRoundTripIntegrationTest.java (IT-005 — ownership moved from TASK-034 in v1.2)
```

### Authoritative Spec Excerpts

**Recovery phase sequence (Spec §2.6.2) — one authoritative interpretation:**
```
Phase 1: Archive Replay (Scenario A only) — cluster rebuilds state from archive
Phase 2: FIX Reconnect Initiated (Artio auto-reconnects)
Phase 3: FIX Logon Established → RecoveryCoordinator.onVenueStatus(CONNECTED) triggers Phase 4
Phase 4: Order reconciliation — 10s hard timer (correlationId = 2000 + venueId)
         OrderStatusQueryCommand published for each live order
         → On expiry: activateKillSwitch("recovery_timeout")
Phase 5: Balance reconciliation — 5s hard timer (correlationId = 3000 + venueId)
         BalanceQueryRequest published
         → On expiry: activateKillSwitch("recovery_timeout")
Phase 6: COMPLETE → setRecoveryLock(venueId, false) → RecoveryCompleteEvent published
```

**Timer correlation IDs:**
```
Order query timeout:   2000 + venueId
Balance query timeout: 3000 + venueId
```

**reconcileOrder() case table (Spec §16.4):**
```
Case A: venue=OPEN, internal=OPEN → match; no action
Case B: venue=CANCELED, internal=OPEN → forceTransitionToCanceled(clOrdId)
Case C: venue=FILLED, internal=OPEN → synthesize fill (isSynthetic=true); apply
Case D: venue=UNKNOWN, internal=OPEN → orphan cancel; publish CancelOrderCommand
Case E: venue=OPEN, internal=UNKNOWN → unexpected venue order; publish CancelOrderCommand
```

**Balance reconciliation tolerance:** discrepancy ≤ `0.0001` units (configurable) → adjust. Exceeds → kill switch.

### Acceptance Criteria
- **AC-RC-001**: No order submitted during recovery window. Verified by ET-003-1.
- **AC-RC-002**: RECONCILIATION_COMPLETE within 30 seconds. Verified by ET-003-1.
- **AC-RC-003**: Missing fill detected: synthetic FillEvent isSynthetic=true. Verified by ET-003-2, T-011.
- **AC-RC-004**: Orphan order triggers cancel. Verified by ET-003-3, T-011.
- **AC-RC-005**: Balance mismatch > tolerance → kill switch. Verified by ET-003-4, T-011.
- **AC-RC-006**: Balance mismatch within tolerance → adjust, trading resumes. Verified by T-011.
- **AC-RC-007**: RecoveryCompleteEvent published on success. Verified by ET-003-1, T-011.
- **AC-PF-P-004**: Recovery completes < 30 seconds. Verified by ET-003-1.

> **Note:** AC-RC-008 (`REPLAY_DONE before RECONNECT_INITIATED`) and AC-RC-009 (FIX
> session unchanged during cluster leader election) are owned by **TASK-026**
> (TradingClusteredService) — they depend on Aeron Cluster lifecycle ordering, not on
> RecoveryCoordinator behaviour. See TASK-026's Acceptance Criteria section. TASK-022's
> recovery flow runs downstream of the cluster lifecycle and is verified by AC-RC-001
> through AC-RC-007 above.

### INPUT → OUTPUT Paths
- **Happy path:** Disconnect → reconnect → all orders match → balance within tolerance → `RecoveryCompleteEvent` published; `recoveryLock` cleared.
- **Missing fill path:** Venue reports order FILLED; internal shows OPEN → synthetic `FillEvent(isSynthetic=true)` → portfolio updated.
- **Orphan path:** Venue has order; cluster doesn't → `CancelOrderCommand` published.
- **Timeout path (10s):** Order query not complete → `activateKillSwitch("recovery_timeout")`; no Phase 5.
- **Balance fail path:** Discrepancy > tolerance → `activateKillSwitch("reconciliation_failed")`; lock stays set.

### Implementation Steps
1. Create `RecoveryCoordinator` interface: `onVenueStatus()`, `onTimer()`, `isInRecovery()`, `reconcileOrder()`, `onBalanceResponse()`, `writeSnapshot()`, `loadSnapshot()`, `resetAll()`, `setCluster()`.
2. Create `RecoveryCoordinatorImpl` with per-venue state machine (array indexed by venueId).
3. Phase 4 start: publish `OrderStatusQueryCommand` for each live order from `OrderManager`; schedule 10s timer.
4. Phase 5 start: publish `BalanceQueryRequest`; schedule 5s timer.
5. `reconcileOrder()`: implement 5-case table from spec.
6. `onTimer()`: check correlationId for both order-query and balance-query timeouts.
7. Phase 6: call `riskEngine.setRecoveryLock(venueId, false)`; publish `RecoveryCompleteEvent`.

### Test Implementation Steps
**T-011 RecoveryCoordinatorTest** (all test cases from Spec §D.2):
- `onDisconnect_venueStateAwaitingReconnect()`
- `onReconnect_orderQuerySent_stateQueryingOrders()`
- `orderQueryResponse_matches_tradingResumes()`
- `orderQueryResponse_missingFill_syntheticFillApplied()`
- `orderQueryResponse_orphanAtVenue_cancelSent()`
- `orderQueryResponse_venueCanceled_orderForceCanceled()`
- `balanceReconciliation_withinTolerance_tradingResumes()`
- `balanceReconciliation_exceedsTolerance_killSwitchActivated()`
- `orderQueryTimeout_10s_killSwitchActivated()`
- `orderQueryTimeout_timerCorrelId_2000PlusVenueId()`
- `balanceQueryTimeout_5s_killSwitchActivated()`
- `recoveryCompleteEvent_publishedOnSuccess()`
- `snapshot_recoveryStatePreserved()`
- `multipleVenues_independentRecovery()`
- `disconnectDuringRecovery_resetAndRestart()`
- `idleVenue_connectedEvent_noQuerySent()`

**ET-003 RecoveryE2ETest:**
- `gatewayDisconnect_reconnects_reconciles_resumesTrading()`
- `reconnect_missingFill_detectedAndApplied()`
- `reconnect_orphanAtVenue_canceledBySystem()`
- `reconnect_balanceMismatch_killSwitchActivated()`
- `clusterLeaderFailover_gatewaySeamless_fixSessionContinues()`
- `tag8013_cancelOrdersOnDisconnect_reconciliationHandlesVenueCancels()`

**IT-005 SnapshotRoundTripIntegrationTest (owned here in v1.2; was TASK-034 in v1.1):**
- `snapshot_writeAndLoad_orderManagerState()` — write OrderManager snapshot; load into fresh instance; fields identical [AC-OL-008]
- `snapshot_writeAndLoad_portfolioState()` [AC-PF-010]
- `snapshot_writeAndLoad_riskState()`
- `snapshot_writeAndLoad_recoveryState()`
- `fullState_writeAndLoad_allComponentsConsistent()` — all 4 components in deterministic order
- `snapshot_corruptedBuffer_loadsDefaultState()`
- `snapshot_emptyPositions_loadProducesZeroNetQty()`
- `snapshot_roundTrip_killSwitchStatePreserved()`

### Out of Scope
No strategy activation post-recovery (notified via `RecoveryCompleteEvent`; strategies react in TASK-030/031).

### Completion Checklist
- [ ] Files changed: RecoveryCoordinator.java, RecoveryCoordinatorImpl.java, T-011, ET-003, IT-005
- [ ] Tests added/updated: T-011 (16 unit cases), ET-003 (6 E2E cases), IT-005 (8 integration cases)
- [ ] All ACs verified: AC-RC-001 through AC-RC-009, AC-PF-P-004, AC-OL-008, AC-PF-010
- [ ] Assumptions made: 30s SLA is the outer bound; 10s + 5s phase timers consume at most 15s
- [ ] Blockers identified: ET-003-5 (leader failover) requires 3-node cluster setup
- [ ] Follow-up items: IT-003 (risk gate + recovery integration) in TASK-025


---

## Task Card TASK-023: DailyResetTimer

### Task Description
Implement the cluster timer that fires at midnight UTC, resetting daily risk counters and
archiving daily PnL. Uses `cluster.scheduleTimer()` with correlation ID 1001 (reserved constant).
Fires as `onTimerEvent()` through the Raft log — identical on all nodes.

### Spec References
Spec §12.6 (DailyResetTimer full implementation).

### Required Existing Inputs
- `TASK-018`: `RiskEngine` (`resetDailyCounters()`)
- `TASK-021`: `PortfolioEngine` (`archiveDailyPnl()`)

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/DailyResetTimer.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/DailyResetTimerTest.java (T-013)
```

### Authoritative Spec Excerpts

**Full class (Spec §12.6):**
```java
public final class DailyResetTimer {
    private static final long DAILY_RESET_CORRELATION_ID = 1001L;
    private Cluster cluster;
    private final RiskEngine riskEngine;
    private final PortfolioEngine portfolioEngine;
    private final EpochClock epochClock;  // Agrona SystemEpochClock

    public void scheduleNextReset() {
        long nowMs          = epochClock.time();
        long nextMidnightMs = computeNextMidnightUtcMs(nowMs);
        cluster.scheduleTimer(DAILY_RESET_CORRELATION_ID, nextMidnightMs);
    }

    public void onTimer(long correlationId, long timestamp) {
        if (correlationId != DAILY_RESET_CORRELATION_ID) return;
        riskEngine.resetDailyCounters();
        portfolioEngine.archiveDailyPnl(cluster.egressPublication());
        scheduleNextReset();  // schedule next midnight
    }

    private long computeNextMidnightUtcMs(long nowMs) {
        long msInDay = 86_400_000L;
        return (nowMs / msInDay + 1) * msInDay;
    }
}
```

**Correlation ID rules (Spec §12.6):**
- `1001L` reserved for daily reset — never reuse.
- Arb leg timeouts: `4000L + (arbAttemptId & 0xFFFFL)`.
- Recovery timeouts: `2000L + venueId` and `3000L + venueId`.

### Acceptance Criteria
- **AC-RK-008**: Daily reset clears daily counters but not kill switch. Verified by T-005 + T-013.
- **AC-SY-007**: Daily counters reset at midnight via cluster timer. Verified by T-013.

### INPUT → OUTPUT Paths
- **Positive path:** `onTimer(1001L, ...)` → `riskEngine.resetDailyCounters()` called; next midnight timer scheduled.
- **Edge path:** `onTimer(9999L, ...)` → returns immediately; no side effects.
- **Next-midnight path:** `computeNextMidnightUtcMs(anyTime)` always returns next UTC midnight in milliseconds.

### Implementation Steps
1. Create `DailyResetTimer` per spec §12.6. Constructor takes `RiskEngine`, `PortfolioEngine`, `EpochClock`.
2. `scheduleNextReset()` calls `cluster.scheduleTimer(1001L, nextMidnightMs)`.
3. `onTimer()` guards on `correlationId != 1001L`.
4. Wire in `TradingClusteredService.onStart()` and `onTimerEvent()` (TASK-026).

### Test Implementation Steps
**T-013 DailyResetTimerTest:**
- `onTimer_correctCorrelId_resetsCountersAndReschedules()`
- `onTimer_wrongCorrelId_noAction()`
- `scheduleNextReset_schedulesAtNextMidnightUtc()`
- `computeNextMidnight_midday_returnsNextMidnight()`
- `computeNextMidnight_justBeforeMidnight_returnsNextMidnight()`
- `computeNextMidnight_atMidnight_returnsFollowingMidnight()`
- `dailyReset_doesNotClearKillSwitch()` [AC-RK-008 — mock killSwitch still active after reset]

### Out of Scope
No strategy logic. Timer scheduling only.

### Completion Checklist
- [ ] Files changed: DailyResetTimer.java, T-013
- [ ] Tests added/updated: T-013 (7 test cases)
- [ ] All ACs verified: AC-RK-008, AC-SY-007
- [ ] Assumptions made: `SystemEpochClock` provides millisecond wall-clock time
- [ ] Blockers identified: none
- [ ] Follow-up items: Wire in TradingClusteredService (TASK-026)

---

## Task Card TASK-024: AdminCommandHandler

### Task Description
Implement the cluster-side admin command processor. On each `AdminCommand` SBE: (1) validate
nonce freshness (< 24h) and uniqueness (replay protection via ring buffer + HashSet),
(2) validate HMAC-SHA256 signature, (3) validate operatorId whitelist, (4) execute the command,
(5) log audit trail. Also supports dual-key HMAC validation during key rotation.

### Spec References
Spec §24.4 (AdminCommandHandler), §24.5 (security: key rotation, whitelist, nonce bounds).

### Required Existing Inputs
- `TASK-002`: `AdminCommandDecoder`
- `TASK-003`: `Ids.java`
- `TASK-018`: `RiskEngine`
- No dependency on TASK-029. TASK-024 **owns and creates** the minimal `StrategyEngineControl` interface (see Expected Modified Files). TASK-029 produces the concrete `StrategyEngine` class which implements this interface, so TASK-029 is blocked on TASK-024 for this specific type — not the reverse.

> **Formalized ownership (replaces v1.1 stub guidance).** `StrategyEngineControl.java` is
> a minimal interface with two methods: `void pauseStrategy(int strategyId)` and
> `void resumeStrategy(int strategyId)`. TASK-024 creates this interface as production
> code; TASK-029's `StrategyEngine` class implements it alongside its other interfaces
> (`StrategyEngineLifecycle`, etc. as needed). No stubs, no forward references.

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/AdminCommandHandler.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyEngineControl.java  (minimal interface — owned here; impl on StrategyEngine in TASK-029)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/AdminCommandHandlerTest.java (T-012)
```

### Authoritative Spec Excerpts

**Nonce ring buffer (Spec §24.4):**
```java
private static final int NONCE_WINDOW_SIZE = 10_000;
private final long[]      nonceRing   = new long[NONCE_WINDOW_SIZE];
private final LongHashSet seenNonces  = new LongHashSet(NONCE_WINDOW_SIZE * 2);
private int               nonceHead   = 0;
private int               nonceCount  = 0;

private boolean addNonce(long nonce) {
    if (seenNonces.contains(nonce)) return false;  // replay
    if (nonceCount == NONCE_WINDOW_SIZE) {
        seenNonces.remove(nonceRing[nonceHead]); nonceCount--;
    }
    nonceRing[nonceHead] = nonce;
    nonceHead = (nonceHead + 1) % NONCE_WINDOW_SIZE;
    seenNonces.add(nonce); nonceCount++;
    return true;
}
```

**onAdminCommand() validation sequence (Spec §24.4):**
```java
// Step 1: Stale nonce check (> 24h)
long epochSeconds = nonce >>> 32;
if (nowSeconds - epochSeconds > 86_400L) { log.warn(...); return; }
// Step 2: Replay check
if (!addNonce(nonce)) { log.warn(...); return; }
// Step 3: HMAC validation (or dual-key during rotation)
if (expectedSig != decoder.hmacSignature()) { log.error(...); return; }
// Step 3b: OperatorId whitelist (v8 AMB-007 — added from §24.5)
if (!config.admin().allowedOperatorIds().contains(decoder.operatorId())) { log.error(...); return; }
// Step 4: Audit log (BEFORE execution — preserved in Aeron Archive even if execute throws)
log.info("ADMIN_CMD operator={} commandType={} ...", ...);
// Step 5: Execute
switch (decoder.commandType()) { ... }
```

**Command switch (Spec §24.4):**
```java
case DEACTIVATE_KILL_SWITCH → riskEngine.deactivateKillSwitch();
case ACTIVATE_KILL_SWITCH   → riskEngine.activateKillSwitch("operator:" + decoder.operatorId());
case PAUSE_STRATEGY         → strategyEngine.pauseStrategy(decoder.instrumentId());
case RESUME_STRATEGY        → strategyEngine.resumeStrategy(decoder.instrumentId());
case TRIGGER_SNAPSHOT       → cluster.takeSnapshot();
case RESET_DAILY_COUNTERS   → riskEngine.resetDailyCounters();
```

### Acceptance Criteria
- **AC-SY-004**: Admin kill switch deactivation via signed AdminCommand. Verified by T-012, ET-005-3.
- **AC-SY-005**: Admin command with wrong HMAC rejected. Verified by T-012.
- **AC-RK-010**: Kill switch deactivation requires operator admin command. Verified by T-012, ET-005-3.

### INPUT → OUTPUT Paths
- **Positive path:** Valid nonce, valid HMAC, known operatorId → command executed, audit logged.
- **Replay path:** Same nonce twice → second rejected, `log.warn`, no state change.
- **Stale path:** Nonce with epochSeconds > 24h ago → rejected.
- **Wrong HMAC path:** Any field tampered → HMAC mismatch → `log.error`, no state change.
- **Unknown operator path:** Valid HMAC, operatorId not in whitelist → rejected.

### Implementation Steps
1. Create `AdminCommandHandler` with nonce ring buffer and `LongHashSet`.
2. Implement `onAdminCommand()` with 5 validation + execution steps in exact order.
3. Implement `computeHmac()` matching `AdminClient.computeHmac()` byte-for-byte (same packed layout).
4. Implement dual-key HMAC validation for key rotation (`validateHmacDualKey()`).
5. Wire `strategyEngine` reference (use interface; concrete in TASK-029).

### Test Implementation Steps
**T-012 AdminCommandHandlerTest** (all test cases from Spec §D.2):
```
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
unknownOperatorId_commandRejected_afterHmacValidation()
```

### Out of Scope
No admin CLI (TASK-032). Cluster-side handler only.

### Completion Checklist
- [ ] Files changed: AdminCommandHandler.java, StrategyEngineControl.java (interface), T-012
- [ ] Tests added/updated: T-012 (15 test cases)
- [ ] All ACs verified: AC-SY-004, AC-SY-005, AC-RK-010
- [ ] Assumptions made: HMAC byte layout identical between AdminClient and AdminCommandHandler
- [ ] Blockers identified: none (StrategyEngineControl interface owned by this card; TASK-029 consumes)
- [ ] Follow-up items: ET-005-3 needs TASK-032 for full E2E admin kill-switch flow


---

## Task Card TASK-025: MessageRouter

### Task Description
Implement the `MessageRouter` that is the sole dispatch point inside
`TradingClusteredService.onSessionMessage()`. Routes incoming SBE messages via integer
`switch(templateId)` to the correct handler. Enforces strict fan-out ordering for
`ExecutionEvent`: OrderManager → PortfolioEngine → RiskEngine → StrategyEngine.
`InternalMarketView.apply()` is called before `StrategyEngine.onMarketData()`.

### Spec References
Spec §3.4.3 (MessageRouter full class definition, integer switch — authoritative per AMB-006).

### Required Existing Inputs
- `TASK-002`: all event decoders
- `TASK-017`: `InternalMarketView`
- `TASK-018`: `RiskEngine`
- `TASK-020`: `OrderManager`
- `TASK-021`: `PortfolioEngine`
- `TASK-022`: `RecoveryCoordinator`
- `TASK-024`: `AdminCommandHandler`

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/MessageRouter.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/MessageRouterTest.java    (T-018)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/RiskGateIntegrationTest.java (IT-003)
```

### Authoritative Spec Excerpts

**Full MessageRouter class (Spec §3.4.3) — integer switch is authoritative (AMB-006):**
```java
public final class MessageRouter {
    // Pre-allocated decoders — one per message type, no allocation on hot path
    private final MarketDataEventDecoder      mdDecoder      = new MarketDataEventDecoder();
    private final ExecutionEventDecoder       execDecoder    = new ExecutionEventDecoder();
    private final VenueStatusEventDecoder     venueDecoder   = new VenueStatusEventDecoder();
    private final BalanceQueryResponseDecoder balanceDecoder = new BalanceQueryResponseDecoder();
    private final AdminCommandDecoder         adminDecoder   = new AdminCommandDecoder();

    public void dispatch(DirectBuffer buffer, int offset, int blockLen, int version,
                          int hdrLen, int templateId) {
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
                recoveryCoordinator.onVenueStatus(venueDecoder);
            }
            case BalanceQueryResponseDecoder.TEMPLATE_ID -> {
                balanceDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                recoveryCoordinator.onBalanceResponse(balanceDecoder);
            }
            case AdminCommandDecoder.TEMPLATE_ID -> {
                adminDecoder.wrap(buffer, offset + hdrLen, blockLen, version);
                adminCommandHandler.onAdminCommand(adminDecoder);
            }
            default -> log.warn("Unknown templateId: {}", templateId);
        }
    }

    // Fan-out order is part of the spec contract (AC-FO-001 through AC-FO-005)
    private void onMarketData(MarketDataEventDecoder d) {
        marketView.apply(d, cluster.time());  // MUST be before strategy dispatch (AC-FO-005)
        strategyEngine.onMarketData(d);
    }

    private void onExecution(ExecutionEventDecoder d) {
        boolean isFill = orderManager.onExecution(d);    // AC-FO-001: OrderManager first
        if (isFill) {
            portfolioEngine.onFill(d);                    // AC-FO-002: Portfolio before Risk
            riskEngine.onFill(d);                         // AC-FO-003: Risk before Strategy
        }
        strategyEngine.onExecution(d, isFill);            // AC-FO-003: Strategy last
    }
}
```

### Acceptance Criteria
- **AC-FO-001**: OrderManager.onExecution() called before PortfolioEngine.onFill(). Verified by T-018.
- **AC-FO-002**: PortfolioEngine.onFill() called before RiskEngine.onFill(). Verified by T-018.
- **AC-FO-003**: RiskEngine.onFill() called before StrategyEngine.onExecution(). Verified by T-018.
- **AC-FO-004**: Non-fill ExecutionEvent: PortfolioEngine and RiskEngine not called. Verified by T-018.
- **AC-FO-005**: InternalMarketView.apply() called before StrategyEngine.onMarketData(). Verified by T-018, IT-002.

### INPUT → OUTPUT Paths
- **Market data path:** templateId=1 → `marketView.apply()` → `strategyEngine.onMarketData()`.
- **Fill path:** templateId=2, isFill=true → OrderManager → Portfolio → Risk → Strategy (strict order).
- **Non-fill execution path:** templateId=2, isFill=false → OrderManager only → Strategy (Portfolio + Risk NOT called).
- **Unknown templateId path:** `log.warn(...)`, no exception, no state change.

### Implementation Steps
1. Create `MessageRouter` with all constructor dependencies (all from TASK-017–024).
2. Implement `dispatch()` with integer switch — 5 cases + default warn.
3. Implement `onMarketData()` — `marketView.apply()` BEFORE `strategyEngine.onMarketData()`.
4. Implement `onExecution()` — `orderManager.onExecution()` returns `isFill`; conditional fan-out.
5. All decoders pre-allocated as fields — one instance per type.

### Test Implementation Steps
**T-018 MessageRouterTest** — verify fan-out order using mock in-order recorder:
- `onMarketData_bookUpdatedBeforeStrategyDispatched()` [AC-FO-005]
- `onExecution_fill_orderManagerBeforePortfolio()` [AC-FO-001]
- `onExecution_fill_portfolioBeforeRisk()` [AC-FO-002]
- `onExecution_fill_riskBeforeStrategy()` [AC-FO-003]
- `onExecution_nonFill_portfolioAndRiskNotCalled()` [AC-FO-004]
- `adminCommand_routedToAdminCommandHandler()`
- `venueStatus_routedToRecoveryCoordinator()`
- `unknownTemplateId_warnLogged_noException()`

**IT-003 RiskGateIntegrationTest:**
- `recoveryLock_onlyAffectedVenue_ordersForOtherVenueAllowed()` [AC-RK-003]
- `riskReject_doesNotUpdatePortfolio()`
- `fillWithKillSwitchActive_orderStillApplied()` (fills always applied per AC-OL-003)

### Out of Scope
No strategy logic (TASK-028–031). No cluster lifecycle (TASK-026).

### Completion Checklist
- [ ] Files changed: MessageRouter.java, T-018, IT-003
- [ ] Tests added/updated: T-018 (8 unit cases), IT-003 (3 integration cases)
- [ ] All ACs verified: AC-FO-001 through AC-FO-005
- [ ] Assumptions made: `StrategyEngine` (implementing `StrategyEngineControl` from TASK-024) is injected by `ClusterMain` (TASK-027); `MessageRouter` references it via interface only
- [ ] Blockers identified: none
- [ ] Follow-up items: none

---

## Task Card TASK-026: TradingClusteredService

### Task Description
Implement the `ClusteredService` lifecycle — the central Aeron Cluster component. Receives
all messages via `onSessionMessage()`, delegates to `MessageRouter`, handles timer events,
snapshot take/load, role changes, and the warmup shim interface. All business logic enters
through this single class.

### Spec References
Spec §3.6 (full TradingClusteredService class), §18.5 (warmup shim interface).

### Required Existing Inputs
- `TASK-018`: `RiskEngine` (field on `TradingClusteredService`; `setCluster()` propagation)
- `TASK-020`: `OrderManager` (field + `setCluster()` propagation)
- `TASK-021`: `PortfolioEngine` (field + `setCluster()` propagation)
- `TASK-022`: `RecoveryCoordinator` (for timer routing)
- `TASK-023`: `DailyResetTimer`
- `TASK-024`: `StrategyEngineControl` (interface — used for admin-driven pause/resume)
- `TASK-025`: `MessageRouter`
- `TASK-029`: `StrategyEngine` (field + `setCluster()` propagation in `onStart()`)
- No dependency on TASK-027 for interface surface. `TradingClusteredService` holds a
  `StrategyEngine` field; the concrete instance is injected by `ClusterMain` (TASK-027).
  For unit testing (T-021), a test double implementing the narrow `StrategyEngineControl`
  interface plus any other contracts needed is created inside the test class itself — this
  is a test-only double owned by T-021, not a production stub, and does not cross the
  ownership boundary.

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/TradingClusteredService.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/TradingClusteredServiceTest.java (T-021)
```

### Authoritative Spec Excerpts

**Full class (Spec §3.6):**
```java
public final class TradingClusteredService implements ClusteredService {
    private final StrategyEngine      strategyEngine;
    private final RiskEngine          riskEngine;
    private final OrderManager        orderManager;
    private final PortfolioEngine     portfolioEngine;
    private final RecoveryCoordinator recoveryCoordinator;
    private final DailyResetTimer     dailyResetTimer;
    private final MessageRouter       router;
    private Cluster cluster;
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        strategyEngine.setCluster(cluster); riskEngine.setCluster(cluster);
        orderManager.setCluster(cluster); portfolioEngine.setCluster(cluster);
        dailyResetTimer.setCluster(cluster); router.setCluster(cluster);
        if (snapshotImage != null) loadSnapshot(snapshotImage);
        dailyResetTimer.scheduleNextReset();
    }

    @Override
    public void onSessionMessage(ClientSession session, long timestamp,
                                  DirectBuffer buffer, int offset, int length) {
        headerDecoder.wrap(buffer, offset);
        router.dispatch(buffer, offset,
                         headerDecoder.blockLength(), headerDecoder.version(),
                         headerDecoder.encodedLength(), headerDecoder.templateId());
    }

    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
        dailyResetTimer.onTimer(correlationId, timestamp);       // correlationId 1001
        recoveryCoordinator.onTimer(correlationId, timestamp);   // correlationId 2000+v, 3000+v
        strategyEngine.onTimer(correlationId);                   // correlationId 4000+
    }

    @Override
    public void onTakeSnapshot(ExclusivePublication pub) {
        orderManager.writeSnapshot(pub);      // deterministic write order
        portfolioEngine.writeSnapshot(pub);
        riskEngine.writeSnapshot(pub);
        recoveryCoordinator.writeSnapshot(pub);
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        strategyEngine.setActive(newRole == Cluster.Role.LEADER);
    }
}
```

**Warmup shim methods (Spec §3.6 + §26.1):**
```java
public void installClusterShim(Cluster shimCluster) { this.cluster = shimCluster; /* + all components */ }
public void removeClusterShim() { this.cluster = null; /* + all components */ }
public void resetWarmupState() {
    orderManager.resetAll(); portfolioEngine.resetAll();
    riskEngine.resetAll(); recoveryCoordinator.resetAll(); strategyEngine.resetAll();
}
```

**installClusterShim guard:** If real cluster already set (non-null), throw `IllegalStateException("Cannot install shim while real cluster is set")`.

### Acceptance Criteria
- **AC-RC-008**: REPLAY_DONE before RECONNECT_INITIATED — achieved by Aeron Cluster opening ingress session only after `onStart()` returns with snapshot loaded. Verified by ET-003-5.
- **AC-RC-009**: FIX session unchanged during cluster leader election — gateway Aeron client reconnects automatically; no FIX reconnect needed. Verified by ET-003-5.
- **AC-SY-003**: 3-node cluster leader election < 500ms. Verified by ET-003-5 (infrastructure).

### INPUT → OUTPUT Paths
- **Normal message path:** `onSessionMessage()` → `headerDecoder` reads templateId → `router.dispatch()`.
- **Snapshot load path:** `onStart(cluster, snapshotImage)` → `loadSnapshot()` → all state restored deterministically.
- **Leader change path:** `onRoleChange(FOLLOWER)` → `strategyEngine.setActive(false)` → no new orders.
- **Warmup path:** `installClusterShim()` → `warmup()` → `resetWarmupState()` → `removeClusterShim()`.

### Implementation Steps
1. Create `TradingClusteredService` per Spec §3.6.
2. Implement all `ClusteredService` lifecycle methods.
3. Implement `installClusterShim()` with guard (throw if real cluster != null).
4. Implement `removeClusterShim()` (nulls cluster on all components).
5. Implement `resetWarmupState()` — calls `resetAll()` on all 5 components.

### Test Implementation Steps
**T-021 TradingClusteredServiceTest** (all test cases from Spec §3.4 TASK-033):
```
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
warmup_c2Verified_avgIterationBelowThreshold() [@Tag("SlowTest")]
```

### Out of Scope
No Aeron Cluster bootstrapping (TASK-027). No strategy logic (TASK-029–031).

### Completion Checklist
- [ ] Files changed: TradingClusteredService.java, T-021
- [ ] Tests added/updated: T-021 (16 test cases including 1 @SlowTest)
- [ ] All ACs verified: AC-RC-008, AC-RC-009 (via ET-003-5), AC-SY-003 (infrastructure)
- [ ] Assumptions made: Aeron Cluster guarantees no `onSessionMessage()` before `onStart()` returns
- [ ] Blockers identified: SlowTest requires -XX:-BackgroundCompilation to pass timing assertion
- [ ] Follow-up items: Wire warmup harness in TASK-033

---

## Task Card TASK-027: ClusterMain

### Task Description
Bootstrap the full Aeron Cluster node. `ClusterMain.main()` loads config, starts the embedded
or external MediaDriver, launches Aeron Archive, builds all cluster components via
`buildClusteredService()`, and starts the Aeron Cluster. This is the entry point for each of
the 3 cluster nodes.

### Spec References
Spec §19.5 (cluster fault tolerance), §19.6 (ClusterMain startup code).

### Required Existing Inputs
- `TASK-004`: `ClusterNodeConfig` (+ all config records the factory consumes via `ConfigManager.loadVenues(...)`, `ConfigManager.loadInstruments(...)`, `config.risk()`, `config.orderPool()`, `config.strategy()`)
- `TASK-005`: `IdRegistryImpl` (+ `init()` method contract — `registerSession()` is NOT called here; gateway owns that)
- `TASK-017`: `InternalMarketView`
- `TASK-018`: `RiskEngineImpl`
- `TASK-019`: `OrderStatePool`
- `TASK-020`: `OrderManagerImpl`
- `TASK-021`: `PortfolioEngineImpl` (+ `initPosition()` for pre-population)
- `TASK-022`: `RecoveryCoordinatorImpl`
- `TASK-023`: `DailyResetTimer`
- `TASK-024`: `AdminCommandHandler` (+ `setStrategyEngine()` setter for circular-reference wiring)
- `TASK-025`: `MessageRouter` (7-arg constructor per Spec §3.4.3)
- `TASK-026`: `TradingClusteredService`
- `TASK-028`: `StrategyContextImpl` (record)
- `TASK-029`: `StrategyEngine` (+ `register()` method)
- `TASK-030`: `MarketMakingStrategy` — conditionally instantiated when `config.strategy().marketMaking().enabled()`
- `TASK-031`: `ArbStrategy` — conditionally instantiated when `config.strategy().arb().enabled()`

> **Note:** TASK-027 is the integration point for every cluster-side component. Its
> `buildClusteredService()` factory is the single location that wires all 14 components into
> `TradingClusteredService`. If any upstream card is incomplete, TASK-027 cannot compile.

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ClusterMain.java
```

### Authoritative Spec Excerpts

**buildClusteredService() factory (derived from Spec §19.6 + §3.4.3 MessageRouter constructor + §3.6 TradingClusteredService constructor) — all arguments fully specified, no placeholders:**
```java
private static TradingClusteredService buildClusteredService(
    ClusterNodeConfig config,
    List<VenueConfig> venues,
    List<InstrumentConfig> instruments) {
    // Step 1: Registry + market view (no cluster-thread dependencies; constructor-only deps)
    IdRegistryImpl idRegistry = new IdRegistryImpl();
    idRegistry.init(venues, instruments);

    InternalMarketView marketView = new InternalMarketView();

    // Step 2: Core components (constructor signatures match their respective task cards)
    RiskEngineImpl riskEngine = new RiskEngineImpl(config.risk(), idRegistry);

    OrderStatePool pool = new OrderStatePool(config.orderPool().capacity());
    OrderManagerImpl orderManager = new OrderManagerImpl(pool, idRegistry);

    PortfolioEngineImpl portfolio = new PortfolioEngineImpl(riskEngine);

    RecoveryCoordinatorImpl recovery = new RecoveryCoordinatorImpl(
        orderManager, portfolio, riskEngine);

    // Step 3: AdminCommandHandler — note: strategyEngine reference is set AFTER StrategyEngine
    // is constructed to avoid a circular reference (see setStrategyEngine() call below).
    AdminCommandHandler adminHandler = new AdminCommandHandler(riskEngine);

    // Step 4: StrategyContext is wired once all collaborators exist.
    // `cluster` is null here — it is set by TradingClusteredService.onStart()'s
    // `strategyEngine.setCluster(cluster)` call; StrategyEngine propagates it into the ctx.
    StrategyContextImpl ctx = new StrategyContextImpl(
        marketView, riskEngine, orderManager, portfolio, recovery,
        /* cluster */ null,     // set by TradingClusteredService.onStart()
        idRegistry);

    // Step 5: StrategyEngine + strategy registration
    StrategyEngine strategyEngine = new StrategyEngine(ctx);

    if (config.strategy().marketMaking().enabled()) {
        strategyEngine.register(new MarketMakingStrategy(
            config.strategy().marketMaking(), idRegistry));
    }
    if (config.strategy().arb().enabled()) {
        strategyEngine.register(new ArbStrategy(
            config.strategy().arb(), idRegistry));
    }

    // Step 6: Wire the admin->strategy back-reference (previously null in Step 3)
    adminHandler.setStrategyEngine(strategyEngine);

    // Step 7: Pre-populate Position entries for every (venueId, instrumentId) pair to
    // eliminate first-fill heap allocation. See Spec §19.6 rationale.
    for (int venueId : idRegistry.allVenueIds()) {
        for (int instrumentId : idRegistry.allInstrumentIds()) {
            portfolio.initPosition(venueId, instrumentId);
        }
    }

    // Step 8: MessageRouter — 7-arg constructor per Spec §3.4.3
    MessageRouter router = new MessageRouter(
        strategyEngine, riskEngine, orderManager, portfolio,
        recovery, adminHandler, marketView);

    // Step 9: DailyResetTimer — EpochClock injected here, cluster reference injected
    // later in TradingClusteredService.onStart()
    DailyResetTimer timer = new DailyResetTimer(riskEngine, portfolio,
                                                  new SystemEpochClock());

    // Step 10: Assemble the ClusteredService — 7-arg constructor per Spec §3.6
    return new TradingClusteredService(
        strategyEngine, riskEngine, orderManager, portfolio,
        recovery, timer, router);
}
```

**Constructor reference (copy into `ClusterMain.java`):**

| Field | Constructor arity | Source |
|---|---|---|
| `IdRegistryImpl` | no-arg; populated via `init(venues, instruments)` | Spec §17.8 |
| `RiskEngineImpl` | `(RiskConfig, IdRegistry)` | TASK-018 |
| `OrderStatePool` | `(int capacity)` | TASK-019 |
| `OrderManagerImpl` | `(OrderStatePool, IdRegistry)` | TASK-020 |
| `PortfolioEngineImpl` | `(RiskEngine)` | TASK-021 |
| `RecoveryCoordinatorImpl` | `(OrderManager, PortfolioEngine, RiskEngine)` | TASK-022 |
| `AdminCommandHandler` | `(RiskEngine)` — strategyEngine via setter | TASK-024 |
| `StrategyContextImpl` | 7-arg record constructor; `cluster` arg is `null` at build time | TASK-028 |
| `StrategyEngine` | `(StrategyContext)` | TASK-029 |
| `MessageRouter` | 7-arg per Spec §3.4.3 | TASK-025 |
| `DailyResetTimer` | `(RiskEngine, PortfolioEngine, EpochClock)` | TASK-023 |
| `TradingClusteredService` | 7-arg per Spec §3.6 | TASK-026 |

**Deferred cluster-reference wiring.** `cluster` is `null` inside `buildClusteredService()`.
`TradingClusteredService.onStart(Cluster, Image)` (Spec §3.6) propagates the real Cluster
reference to every component via `setCluster(cluster)`. This is by design — it lets the
factory run at process startup before Aeron Cluster is fully initialized.

### Acceptance Criteria
- **AC-SY-003**: 3-node cluster leader election < 500ms. Verified by ET-003-5 and ET-006.

### INPUT → OUTPUT Paths
- **Positive path:** `ClusterMain config/cluster-node-0.toml` → cluster node starts, joins, elects leader.
- **Failure path:** Bad TOML → `ConfigValidationException` with clear message before any cluster code runs.

### Implementation Steps
1. Create `ClusterMain.main()` — load config, validate, build service, start cluster.
2. Implement `buildClusteredService()` factory wiring all components.
3. Configure `ClusteredMediaDriver` or connect to external MediaDriver.
4. Configure Aeron Archive for snapshot/replay.
5. Add shutdown hooks for graceful cluster termination.

### Test Implementation Steps
No dedicated unit test. Verified by ET-006 smoke test (full 3-node cluster) and ET-003-5 (failover).

### Completion Checklist
- [ ] Files changed: ClusterMain.java
- [ ] Tests added/updated: none (verified by ET-006, ET-003-5)
- [ ] All ACs verified: AC-SY-003 (infrastructure)
- [ ] Assumptions made: Aeron Cluster default election timeout (~1s) can be tuned below 500ms
- [ ] Blockers identified: none
- [ ] Follow-up items: none


---

## Task Card TASK-028: StrategyContext

### Task Description
Implement the `StrategyContext` interface and `StrategyContextImpl` record. This is the
dependency-injection contract between `StrategyEngine` and each concrete `Strategy`. Every
strategy receives a single `StrategyContext` in its `init()` call rather than individual
references to each collaborator. The `StrategyContextImpl` record is wired by `ClusterMain`.

### Spec References
Spec §17.9 (StrategyContext interface and StrategyContextImpl record — v8 AMB-001 fix applied).

### Required Existing Inputs
- `TASK-017`: `InternalMarketView`
- `TASK-018`: `RiskEngine`
- `TASK-020`: `OrderManager`
- `TASK-021`: `PortfolioEngine`
- `TASK-022`: `RecoveryCoordinator`
- `TASK-002`: `NewOrderCommandEncoder`, `CancelOrderCommandEncoder`, `MessageHeaderEncoder`
- `TASK-005`: `IdRegistry`

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContext.java      (interface)
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContextImpl.java  (record)
```

### Do Not Modify
All files from TASK-017 through TASK-027.

### Authoritative Spec Excerpts

**StrategyContext interface (Spec §17.9):**
```java
public interface StrategyContext {
    InternalMarketView        marketView();
    RiskEngine                riskEngine();
    OrderManager              orderManager();
    PortfolioEngine           portfolioEngine();
    RecoveryCoordinator       recoveryCoordinator();
    Cluster                   cluster();
    UnsafeBuffer              egressBuffer();
    MessageHeaderEncoder      headerEncoder();
    NewOrderCommandEncoder    newOrderEncoder();
    CancelOrderCommandEncoder cancelOrderEncoder();
    IdRegistry                idRegistry();
    CountersManager           counters();
}
```

**StrategyContextImpl record (Spec §17.9 — v8 AMB-001 fix: duplicate `counters` field removed):**
```java
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
// NOTE: AMB-001 (v8): The v7 spec had a duplicate `CountersManager counters` field.
// The duplicate has been removed. This record compiles cleanly with 12 components.
```

### Implementation Guidelines
- `StrategyContextImpl` is a Java record — all fields immutable, created once at startup in `ClusterMain.buildClusteredService()`.
- Each strategy pre-allocates its own `UnsafeBuffer egressBuffer` and SBE encoders — these are NOT shared between strategies to avoid encoding collisions.
- The `cluster` field is null at record construction; strategies call `ctx.cluster()` only after `init()` is called by `StrategyEngine`, which happens after `TradingClusteredService.onStart()` sets the real cluster via `setCluster()`.

### Acceptance Criteria
No ACs owned directly. Enables TASK-029, TASK-030, TASK-031.

### INPUT → OUTPUT Paths
- **Positive path:** `new StrategyContextImpl(marketView, riskEngine, ...)` → record fields accessible via accessors.
- **Null cluster path:** `ctx.cluster()` returns null before `onStart()` — strategies must guard.

### Implementation Steps
1. Create `StrategyContext.java` interface with 12 accessor methods.
2. Create `StrategyContextImpl.java` record implementing `StrategyContext` with 12 components.
3. Verify it compiles cleanly — confirm no duplicate field from v7.

### Test Implementation Steps
No dedicated test class. Integration-verified by TASK-029 `StrategyEngineTest` (T-020) and `IT-002`.

### Out of Scope
No strategy logic. Pure dependency container.

### Completion Checklist
- [ ] Files changed: StrategyContext.java, StrategyContextImpl.java
- [ ] Tests added/updated: none (verified by TASK-029/030/031 tests)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: each strategy holds its own SBE encoder instances; no sharing
- [ ] Blockers identified: none
- [ ] Follow-up items: Wire `cluster` field update via StrategyEngine.setCluster() in TASK-029

---

## Task Card TASK-029: Strategy Interface + StrategyEngine

### Task Description
Create the `Strategy` interface (plain, non-sealed since V9.9) — the sole plugin contract for trading strategies —
and the `StrategyEngine` that fans out all cluster lifecycle events to every registered
strategy. When the cluster becomes a follower, `setActive(false)` calls `onKillSwitch()` on
all strategies. Timer events route to all strategies regardless of active state.

### Spec References
Spec §17.1 (Strategy interface — plain, non-sealed per V9.9), §17.10 (StrategyEngine class), §2.3 (Strategy pluggability).

### Required Existing Inputs
- `TASK-028`: `StrategyContext`
- `TASK-002`: `MarketDataEventDecoder`, `ExecutionEventDecoder`
- `TASK-024`: `StrategyEngineControl` interface — `StrategyEngine` implements this interface as part of its public surface so `AdminCommandHandler` can drive pause/resume.

> **V9.9: `Strategy` is a plain (non-sealed) interface.** Earlier drafts declared
> `sealed interface Strategy permits MarketMakingStrategy, ArbStrategy`, which created
> a three-way compile-time cycle between TASK-029, TASK-030, and TASK-031. Spec V9.9
> drops the `sealed` + `permits` clause (see V9.9 changelog). TASK-029 now delivers
> a plain `Strategy` interface that compiles standalone; TASK-030 and TASK-031 each
> `implements Strategy` independently. No co-delivery constraint remains — TASK-029
> can be implemented first in Week 8, followed by TASK-030 and TASK-031 in parallel.

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/Strategy.java          (plain interface — V9.9 dropped `sealed`+`permits`)
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyEngine.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/StrategyEngineTest.java (T-020)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/L2BookToStrategyIntegrationTest.java (IT-002)
```

### Do Not Modify
`StrategyContext.java`, `StrategyContextImpl.java`, all cluster infrastructure files.

### Authoritative Spec Excerpts

**Strategy interface (Spec §17.1 — V9.9: no longer sealed, no `permits`):**
```java
public interface Strategy {

    void init(StrategyContext ctx);
    void onMarketData(MarketDataEventDecoder decoder);
    void onFill(ExecutionEventDecoder decoder);
    void onOrderRejected(long clOrdId, byte rejectCode, int venueId);
    void onKillSwitch();
    void onKillSwitchCleared();
    void onVenueRecovered(int venueId);
    void onPositionUpdate(int venueId, int instrumentId,
                          long netQtyScaled, long avgEntryScaled);
    int[] subscribedInstrumentIds();
    int[] activeVenueIds();
    void shutdown();
    default int strategyId() { return 0; }  // overridden by each strategy
    default void onTimer(long correlationId) {}  // no-op default for strategies that don't use timers
}
```

**StrategyEngine class (Spec §17.10):**
```java
public final class StrategyEngine {
    private final List<Strategy> strategies = new ArrayList<>(4);
    private Cluster cluster;
    private boolean active = false;  // true only on LEADER node

    public void register(Strategy strategy) { strategies.add(strategy); }

    public void setCluster(Cluster cluster) { this.cluster = cluster; }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) { for (Strategy s : strategies) s.onKillSwitch(); }
        else         { for (Strategy s : strategies) s.onKillSwitchCleared(); }
    }

    public void pauseStrategy(int strategyId) {
        strategies.stream()
            .filter(s -> s.strategyId() == strategyId)
            .forEach(Strategy::onKillSwitch);
    }

    public void resumeStrategy(int strategyId) {
        strategies.stream()
            .filter(s -> s.strategyId() == strategyId)
            .forEach(Strategy::onKillSwitchCleared);
    }

    public void onMarketData(MarketDataEventDecoder decoder) {
        if (!active) return;
        for (Strategy s : strategies) s.onMarketData(decoder);
    }

    public void onExecution(ExecutionEventDecoder decoder, boolean isFill) {
        if (!active) return;
        for (Strategy s : strategies) { if (isFill) s.onFill(decoder); }
    }

    // Timer events route to all strategies regardless of active — cluster determinism
    public void onTimer(long correlationId) {
        for (Strategy s : strategies) s.onTimer(correlationId);
    }

    public void onVenueRecovered(int venueId) {
        for (Strategy s : strategies) s.onVenueRecovered(venueId);
    }

    public void onPositionUpdate(int venueId, int instrumentId,
                                  long netQtyScaled, long avgEntryScaled) {
        for (Strategy s : strategies) s.onPositionUpdate(venueId, instrumentId,
                                                          netQtyScaled, avgEntryScaled);
    }

    public void resetAll() {
        for (Strategy s : strategies) s.shutdown();
        active = false; cluster = null;
    }
}
```

**Strategy registration (config-driven, Spec §2.3):**
```java
List<Strategy> strategies = new ArrayList<>();
if (config.isEnabled(STRATEGY_MARKET_MAKING))
    strategies.add(new MarketMakingStrategy(config.marketMaking()));
if (config.isEnabled(STRATEGY_ARB))
    strategies.add(new ArbStrategy(config.arb()));
strategyEngine.register(strategies);
```

**Critical: `onTimer()` routes to ALL strategies regardless of `active`.** Timers (e.g. arb leg
timeout) must fire on followers for state consistency in the Raft log. If a follower becomes
leader mid-timeout, the timer fires correctly regardless of prior active state.

### Acceptance Criteria
No ACs directly owned. Enables AC-MM-007/008 (kill switch propagation to strategies), AC-ARB-010
(leg timeout via cluster timer), AC-FO-003/004 (fan-out ordering verified in T-018).

### INPUT → OUTPUT Paths
- **setActive(false) path:** All registered strategies receive `onKillSwitch()`.
- **setActive(true) path:** All registered strategies receive `onKillSwitchCleared()`.
- **onMarketData inactive path:** `active == false` → no dispatch; strategies untouched.
- **onTimer inactive path:** `active == false` → timer still routes to all strategies.
- **pauseStrategy path:** Only the strategy with matching `strategyId()` receives `onKillSwitch()`.

### Implementation Steps
1. Create `Strategy.java` as a plain (non-sealed) interface. V9.9 drops the earlier `sealed` + `permits MarketMakingStrategy, ArbStrategy` clause so TASK-029 compiles independently of TASK-030/031.
2. Create `StrategyEngine.java` per Spec §17.10 — all 9 public methods.
3. `onTimer()` does NOT check `active` — routes unconditionally.
4. `pauseStrategy()` / `resumeStrategy()` use stream filter on `strategyId()`.
5. Wire `setCluster()` so strategies can access `cluster.time()` and `cluster.logPosition()`.

### Test Implementation Steps
**T-020 StrategyEngineTest** (note: T-009 owned by TASK-030; AMB-003 fix):
- `register_strategyAddedToList()`
- `setActive_false_callsOnKillSwitchOnAllStrategies()`
- `setActive_true_strategiesReceiveKillSwitchCleared()`
- `pauseStrategy_specificStrategy_otherStrategiesUnaffected()`
- `resumeStrategy_specificStrategy_resumes()`
- `onMarketData_inactive_noDispatch()`
- `onExecution_inactiveButFill_onFillNotCalled()`
- `pauseStrategy_unknownId_noEffect()`
- `setActive_sameValue_idempotent()`
- `register_afterSetActive_newStrategyActivatedCorrectly()`
- `onTimer_inactive_stillRouted_clusterDeterminism()`

**IT-002 L2BookToStrategyIntegrationTest:**
- `marketDataEvent_updatesBook_strategySeesNewPrices()`
- `bookStale_strategySuppress_noOrders()`
- `marketDataThenFill_positionUpdated_skewApplied()`
- `multipleInstruments_bookUpdates_onlyCorrectStrategyNotified()`

### Out of Scope
No strategy algorithms (TASK-030/031). Interface and fan-out engine only.

### Completion Checklist
- [ ] Files changed: Strategy.java, StrategyEngine.java, T-020, IT-002
- [ ] Tests added/updated: T-020 (11 unit cases), IT-002 (4 integration cases)
- [ ] All ACs verified: n/a (enabler)
- [ ] Assumptions made: `strategies.stream()` allocation in pauseStrategy/resumeStrategy is acceptable (rare admin path, not hot path)
- [ ] Blockers identified: none
- [ ] Follow-up items: Wire AdminCommandHandler.setStrategyEngine() in TASK-027 / TASK-024


---

## Task Card TASK-030: MarketMakingStrategy

### Task Description
Implement the full market making algorithm. On every `onMarketData()` call: check suppression,
fetch top-of-book, compute inventory skew, compute adjusted fair value, derive bid/ask prices
with tick rounding, compute bid/ask sizes with lot rounding and the configurable
`minQuoteSizeFractionBps` floor (v8 AMB-005 fix), check if refresh is needed, cancel live
quotes, submit new quotes. All arithmetic is `long`-only; zero allocations on hot path.

### Spec References
Spec §6.1 (full algorithm §6.1.1–6.1.8), §2.1 (business requirements), §20.1 (test vectors).

### Required Existing Inputs
- `TASK-029`: `Strategy` interface, `StrategyEngine`
- `TASK-028`: `StrategyContext`
- `TASK-017`: `InternalMarketView`
- `TASK-018`: `RiskEngine`
- `TASK-020`: `OrderManager`
- `TASK-021`: `PortfolioEngine` (consumed via `StrategyContext.portfolioEngine()`)
- `TASK-022`: `RecoveryCoordinator` (consumed via `StrategyContext.recoveryCoordinator()` — to suppress quoting during venue recovery)
- `TASK-002`: `NewOrderCommandEncoder`, `CancelOrderCommandEncoder`
- `TASK-003`: `Ids.java`, `ScaledMath`
- `TASK-004`: `MarketMakingConfig` (with `minQuoteSizeFractionBps` — v8 fix)

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingStrategy.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingStrategyTest.java (T-009)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingE2ETest.java      (ET-001)
```

### Do Not Modify
`StrategyEngine.java`, `Strategy.java`, all cluster infrastructure files.

### Authoritative Spec Excerpts

**State fields (Spec §6.1.2) — all primitives, zero allocation:**
```java
private long   lastQuotedMid         = 0;
private long   lastQuoteTimeCluster  = 0;
private long   liveBidClOrdId        = 0;   // 0 = no live bid
private long   liveAskClOrdId        = 0;   // 0 = no live ask
private long   lastKnownPosition     = 0;   // updated by onFill()
private long   suppressedUntilMicros = 0;
private int    consecutiveRejections = 0;
private long   lastRejectionTimeMicros = 0;
private long   lastTradePriceScaled  = 0;   // updated from EntryType.TRADE ticks only
private Cluster cluster;  // set in init(ctx)
private InternalMarketView  marketView;
private RiskEngine          riskEngine;
private RecoveryCoordinator recoveryCoordinator;
private PortfolioEngine     portfolioEngine;
```

**Full onMarketData() algorithm (Spec §6.1.3 + AMB-005 fix):**
```java
public void onMarketData(MarketDataEventDecoder decoder) {
    // TRADE ticks update lastTradePriceScaled; do NOT trigger quote refresh (AC-ST-002)
    if (decoder.entryType() == EntryType.TRADE) {
        lastTradePriceScaled = decoder.priceScaled(); return;
    }
    if (decoder.venueId() != config.venueId()) return;
    if (decoder.instrumentId() != config.instrumentId()) return;
    if (isSuppressed()) return;

    long bestBid = marketView.getBestBid(config.venueId(), config.instrumentId());
    long bestAsk = marketView.getBestAsk(config.venueId(), config.instrumentId());
    if (bestBid == INVALID_PRICE || bestAsk == INVALID_PRICE) return;

    long spread   = bestAsk - bestBid;
    long midPrice = (bestBid + bestAsk) / 2;

    // Wide spread: use lastTradePrice as fairValue fallback (AC-ST-001)
    long fairValue;
    if (spread * 10000 / midPrice > config.wideSpreadThresholdBps()) {
        if (lastTradePriceScaled > 0) fairValue = lastTradePriceScaled;
        else { suppress(WIDE_SPREAD, WIDE_SPREAD_COOLDOWN_MICROS); return; }  // AC-ST-003
    } else { fairValue = midPrice; }

    if (spread * 10000 / midPrice > config.maxTolerableSpreadBps()) {
        suppress(WIDE_SPREAD, WIDE_SPREAD_COOLDOWN_MICROS); return;
    }

    // Inventory skew — integer arithmetic only, no float (AC-MM-004, AC-MM-005)
    long maxLimit = lastKnownPosition >= 0 ? config.maxPositionLongScaled()
                                            : config.maxPositionShortScaled();
    long inventoryRatioBps = lastKnownPosition * 10000 / maxLimit;  // [-10000, +10000]
    long skewBps           = config.inventorySkewFactorBps() * inventoryRatioBps / 10000;
    long adjustedFairValue = fairValue - (skewBps * fairValue / 10000);

    // Price computation with tick rounding (AC-MM-011)
    long halfSpread = config.targetSpreadBps() * adjustedFairValue / 20000;
    long ourBid = ScaledMath.floorToTick(adjustedFairValue - halfSpread, config.tickSizeScaled());
    long ourAsk = ScaledMath.ceilToTick(adjustedFairValue + halfSpread, config.tickSizeScaled());
    if (ourBid >= ourAsk) ourAsk = ourBid + config.tickSizeScaled();  // min 1-tick spread

    // Size computation with lot rounding and configurable minFloor (AC-MM-006, AMB-005 fix)
    long minFloor = config.minQuoteSizeFractionBps();
    long bidSize, askSize;
    if (inventoryRatioBps > 0) {
        bidSize = config.baseQuoteSizeScaled() * Math.max(minFloor, 10000 - inventoryRatioBps) / 10000;
        askSize = config.baseQuoteSizeScaled() * (10000 + inventoryRatioBps) / 10000;
    } else {
        bidSize = config.baseQuoteSizeScaled() * (10000 - inventoryRatioBps) / 10000;
        askSize = config.baseQuoteSizeScaled() * Math.max(minFloor, 10000 + inventoryRatioBps) / 10000;
    }
    bidSize = Math.min(ScaledMath.floorToLot(bidSize, config.lotSizeScaled()), config.maxQuoteSizeScaled());
    askSize = Math.min(ScaledMath.floorToLot(askSize, config.lotSizeScaled()), config.maxQuoteSizeScaled());

    if (!requiresRefresh(ourBid, ourAsk, bidSize, askSize)) return;  // AC-MM-014

    cancelLiveQuotes();                                               // AC-MM-015: cancel before new
    if (bidSize > 0) submitQuote(Side.BUY,  ourBid, bidSize);        // AC-MM-006: no zero-size
    if (askSize > 0) submitQuote(Side.SELL, ourAsk, askSize);
    lastQuotedMid        = midPrice;
    lastQuoteTimeCluster = cluster.time();
}
```

> **Formula note (Spec V10.0 §6.1.3).** Spec V10.0 §6.1.3 computes
> `adjustedFairValue = fairValue - (skewBps * fairValue / 10000)`. Under normal
> spreads, `fairValue == midPrice` so the formula is equivalent to a skew-around-mid
> calculation. Under the wide-spread fallback, `fairValue == lastTradePriceScaled`
> (AC-ST-001); applying skew against `fairValue` rather than `midPrice` keeps the
> skew base consistent with the quote center and avoids the drift that the v8-era
> "skew against mid while quoting against last-trade" arithmetic produced. T-009
> test vectors align with this formula.

**isSuppressed() (Spec §6.1.4):**
```java
private boolean isSuppressed() {
    return riskEngine.isKillSwitchActive()
        || recoveryCoordinator.isInRecovery(config.venueId())
        || marketView.isStale(config.venueId(), config.instrumentId(),
                               config.marketDataStalenessThresholdMicros(), cluster.time())
        || (suppressedUntilMicros > 0 && cluster.time() < suppressedUntilMicros);
}
```

**submitQuote() — clOrdId from cluster.logPosition() (Spec §6.1.5, AC-MM-016):**
```java
private void submitQuote(byte side, long price, long size) {
    long clOrdId = cluster.logPosition();   // deterministic, unique, no allocation
    orderManager.createPendingOrder(clOrdId, config.venueId(), config.instrumentId(),
                                     side, OrdType.LIMIT, TimeInForce.GTD, price, size,
                                     Ids.STRATEGY_MARKET_MAKING);
    newOrderEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
        .clOrdId(clOrdId).venueId(config.venueId()).instrumentId(config.instrumentId())
        .side(side).ordType(OrdType.LIMIT).timeInForce(TimeInForce.GTD)
        .priceScaled(price).qtyScaled(size).strategyId(Ids.STRATEGY_MARKET_MAKING);
    cluster.egressPublication().offer(egressBuffer, 0,
        newOrderEncoder.encodedLength() + HEADER_LENGTH);
    if (side == Side.BUY) liveBidClOrdId = clOrdId;
    else                  liveAskClOrdId = clOrdId;
}
```

**cancelLiveQuotes() (Spec §6.1.6):** publishes `CancelOrderCommand` for each live side; zeros `liveBidClOrdId` / `liveAskClOrdId`.

**requiresRefresh() (Spec §6.1.7):**
```java
private boolean requiresRefresh(long newBid, long newAsk, long newBidSize, long newAskSize) {
    if (liveBidClOrdId == 0 || liveAskClOrdId == 0) return true;
    long priceDelta    = Math.abs(newBid - lastQuotedMid);
    long refreshThresh = lastQuotedMid * config.refreshThresholdBps() / 10000;
    if (priceDelta > refreshThresh) return true;
    long sizeDelta = Math.abs(newBidSize - config.baseQuoteSizeScaled());
    if (sizeDelta > config.baseQuoteSizeScaled() / 10) return true;
    return false;
}
```

**onOrderRejected() — consecutive rejection suppression (Spec §8.13 TASK-030, AC-ST-004/005/006):**
```java
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

### Acceptance Criteria
- **AC-MM-001** through **AC-MM-017**: all 17 market making ACs. Verified by T-009 and ET-001.
- **AC-ST-001**: lastTradePrice as fairValue fallback. Verified by T-009.
- **AC-ST-002**: lastTradePrice from TRADE ticks only. Verified by T-009.
- **AC-ST-003**: Wide spread + no lastTradePrice → suppress, no crash. Verified by T-009.
- **AC-ST-004**: Rejection counter incremented in `onOrderRejected()`. Verified by T-009.
- **AC-ST-005**: 3 rejections → 5-second suppression. Verified by T-009.
- **AC-ST-006**: Fill resets rejection counter. Verified by T-009.
- **AC-PF-P-001**: Zero allocations on hot path after warmup. Verified by T-009 async-profiler.
- **AC-PF-P-003**: Kill switch propagation < 10ms end-to-end. Measured by ET-001-4.

### INPUT → OUTPUT Paths
- **Positive path:** Valid market data, no suppression → two `NewOrderCommand` SBE published (BUY + SELL).
- **Kill switch path:** `riskEngine.isKillSwitchActive() == true` → `isSuppressed()` true → zero publishes.
- **Recovery lock path:** `recoveryCoordinator.isInRecovery(venueId) == true` → suppressed.
- **Stale market data path:** `marketView.isStale() == true` → suppressed.
- **Wide spread path:** Spread > `maxTolerableSpreadBps` → suppressed with cooldown.
- **Wide spread + no trade price path:** `lastTradePriceScaled == 0` → suppressed (no crash).
- **Refresh not needed path:** Prices and sizes unchanged → `requiresRefresh() == false` → no publish.
- **Max long position path:** `inventoryRatioBps == 10000` → `bidSize` rounds to 0 → only ASK submitted.
- **3 rejections path:** `consecutiveRejections >= 3` → `suppressedUntilMicros` set; cooldown active.
- **Zero allocation path:** `async-profiler --alloc` on hot methods shows 0 b/op after warmup.

### Implementation Steps
1. Create `MarketMakingStrategy` implementing `Strategy`.
2. Override `strategyId()` to return `Ids.STRATEGY_MARKET_MAKING`.
3. In `init(StrategyContext ctx)`: store all context references; pre-allocate encoders and `egressBuffer`.
4. Implement `onMarketData()` per the full algorithm above (Spec §6.1.3 + v8 AMB-005 fix).
5. Implement `isSuppressed()`, `submitQuote()`, `cancelLiveQuotes()`, `requiresRefresh()`, `suppress()`.
6. Implement `onFill()`: update `lastKnownPosition`; reset `consecutiveRejections = 0`.
7. Implement `onOrderRejected()`: increment counter; trigger 5s suppression at 3 consecutive.
8. Implement `onKillSwitch()`: call `cancelLiveQuotes()`; set suppressed indefinitely.
9. Implement `onKillSwitchCleared()`: clear `suppressedUntilMicros`.
10. Verify: zero `new` keywords on any code path reachable from `onMarketData()`.

### Test Implementation Steps
**T-009 MarketMakingStrategyTest** (full list from Spec §D.2, §10.9):
```
fairValue_symmetricSpread_midprice()
inventorySkew_longPosition_lowerBidAndAsk()         [AC-MM-004]
inventorySkew_shortPosition_higherBidAndAsk()        [AC-MM-005]
inventorySkew_zeroPosition_noAdjustment()
quoteSize_longInventory_smallerBid_largerAsk()
quoteSize_shortInventory_smallerAsk_largerBid()
tickRounding_bidRoundedDown()                        [AC-MM-011]
tickRounding_askRoundedUp()                          [AC-MM-011]
bidAskNonCrossing_skewedPricesFixed()                [AC-MM-003]
suppression_killSwitch_noQuotesSubmitted()           [AC-MM-008]
suppression_recoveryLock_noQuotesSubmitted()         [AC-RC-001]
suppression_staleMarketData_noQuotesSubmitted()      [AC-MM-009]
suppression_wideSpread_noQuotesSubmitted()           [AC-MM-010]
suppression_cleared_quotesResume()
suppression_cooldown_noQuotesDuringCooldown()        [AC-ST-005]
suppression_cooldownExpired_quotesResume()
zeroSize_bidSideAtMaxLong_bidNotSubmitted()          [AC-MM-006]
zeroSize_askSideAtMaxShort_askNotSubmitted()         [AC-MM-006]
refresh_priceUnchanged_noNewQuote()                  [AC-MM-014]
refresh_priceMovedBeyondThreshold_newQuote()         [AC-MM-002]
refresh_timeExceededMaxAge_newQuote()
refresh_cancelSentBeforeNewQuote()                   [AC-MM-015]
refresh_liveQuotesIdentical_noNewQuote()             [AC-MM-014]
wrongVenueId_ignored()                               [AC-MM-013]
wrongInstrumentId_ignored()                          [AC-MM-013]
clOrdId_usesClusterLogPosition()                     [AC-MM-016]
clOrdId_eachQuoteUnique()                            [AC-OL-005]
lastTradePrice_updatedFromTradeTick()                [AC-ST-002]
lastTradePrice_notUpdatedFromBidAsk()                [AC-ST-002]
wideSpread_withLastTradePrice_quotesAtTradePrice()   [AC-ST-001]
wideSpread_noLastTradePrice_suppressed()             [AC-ST-003]
onOrderRejected_counterIncremented()                 [AC-ST-004]
onOrderRejected_threeConsecutive_suppressionSet()    [AC-ST-005]
onFill_resetsRejectionCounter()                      [AC-ST-006]
minQuoteSizeFractionBps_configurable_floorRespected() [AMB-005 regression test]
```

**ET-001 MarketMakingE2ETest:**
```
mmStrategy_onMarketData_submitsTwoSidedQuotes()         [AC-MM-001, AC-MM-003, AC-MM-016]
mmStrategy_marketMoves_requotesWithinThreshold()        [AC-MM-002, AC-MM-015]
mmStrategy_fillReceived_inventorySkewsNextQuote()       [AC-MM-004]
mmStrategy_killSwitchActivated_quotesCancel()           [AC-MM-007, AC-MM-008, AC-PF-P-003]
mmStrategy_marketDataStale_quotesCancel()               [AC-MM-009]
mmStrategy_wideSpread_suppressesQuotes()                [AC-MM-010]
mmStrategy_positionAtMaxLong_askSideZeroSize_suppressed() [AC-MM-006]
```

### Out of Scope
No arb logic (TASK-031). No admin (TASK-024/032).

### Completion Checklist
- [ ] Files changed: MarketMakingStrategy.java, T-009, ET-001
- [ ] Tests added/updated: T-009 (35 unit cases), ET-001 (7 E2E cases)
- [ ] All ACs verified: AC-MM-001→017, AC-ST-001→006, AC-PF-P-001, AC-PF-P-003
- [ ] Assumptions made: `minQuoteSizeFractionBps` default 1000 in TOML (v8 AMB-005 fix verified by regression test)
- [ ] Blockers identified: none
- [ ] Follow-up items: none


---

## Task Card TASK-031: ArbStrategy

### Task Description
Implement the cross-venue arbitrage strategy. On every `onMarketData()` call: scan all venue
pairs for a profitable spread using the net profit formula (fees + slippage). On opportunity:
publish both legs in a **single** `egressPublication.offer()` call (one buffer, both
`NewOrderCommand` SBE messages concatenated). Track the active arb via `arbActive` +
`leg1/2ClOrdId`. On fill of both legs: compute imbalance, hedge if needed. Cluster timer
fires if either leg is still pending after `legTimeoutClusterMicros`.

### Spec References
Spec §6.2 (full ArbStrategy §6.2.1–6.2.5), §2.2 (business requirements), §20.1 (test vectors).

### Required Existing Inputs
- `TASK-029`: `Strategy` interface
- `TASK-028`: `StrategyContext`
- `TASK-017`: `InternalMarketView`
- `TASK-018`: `RiskEngine`
- `TASK-003`: `Ids.java`, `ScaledMath`
- `TASK-004`: `ArbStrategyConfig`
- `TASK-002`: `NewOrderCommandEncoder`, `CancelOrderCommandEncoder`

### Expected Modified Files
```
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategy.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategyTest.java (T-010)
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategyE2ETest.java (ET-004)
```

### Do Not Modify
`Strategy.java`, `StrategyEngine.java`, `MarketMakingStrategy.java`, all infrastructure files.

### Authoritative Spec Excerpts

**ArbAttempt state (Spec §6.2.2) — all primitives, no UUID, no String:**
```java
private long arbAttemptId       = 0;   // cluster.logPosition() at execution
private int  leg1VenueId        = 0;
private int  leg2VenueId        = 0;
private long leg1ClOrdId        = 0;
private long leg2ClOrdId        = 0;
private byte leg1Status         = LEG_PENDING;
private byte leg2Status         = LEG_PENDING;
private long leg1FillQtyScaled  = 0;
private long leg2FillQtyScaled  = 0;
private long arbCreatedCluster  = 0;
private boolean arbActive       = false;
private long activeArbTimeoutCorrelId = 0;
private static final byte LEG_PENDING = 0;
private static final byte LEG_DONE    = 1;
```

**executeArb() — single egressPublication.offer() (Spec §6.2.3, AC-ARB-003):**
```java
private void executeArb(int buyVenueId, int sellVenueId, long size,
                         long buyPrice, long sellPrice) {
    // Read logPosition() exactly once — all 3 IDs derived from single base (AC-ARB-006)
    long base    = cluster.logPosition();
    arbAttemptId = base;
    leg1ClOrdId  = base + 1;
    leg2ClOrdId  = base + 2;
    arbCreatedCluster = cluster.time();
    arbActive = true;

    // Encode both legs into a single egress buffer — minimises leg gap (AC-ARB-003)
    int offset = 0;
    offset += encodeLeg(egressBuffer, offset, buyVenueId,  Side.BUY,  buyPrice,  size, leg1ClOrdId);
    offset += encodeLeg(egressBuffer, offset, sellVenueId, Side.SELL, sellPrice, size, leg2ClOrdId);
    cluster.egressPublication().offer(egressBuffer, 0, offset);  // single offer

    // Schedule leg timeout (AC-ST-007)
    long correlId = ARB_TIMEOUT_BASE + (arbAttemptId & 0xFFFFL);
    cluster.scheduleTimer(correlId, cluster.time() / 1000 + config.legTimeoutClusterMicros() / 1000);
    activeArbTimeoutCorrelId = correlId;
}
private static final long ARB_TIMEOUT_BASE = 4000L;
```

**Net profit formula (Spec §6.2.3, AC-ARB-001):**
```java
long grossProfit = sellVenueBid - buyVenueAsk;
long buyFee      = ScaledMath.safeMulDiv(buyVenueAsk,  config.takerFeeScaled()[buyVenueId],  Ids.SCALE);
long sellFee     = ScaledMath.safeMulDiv(sellVenueBid, config.takerFeeScaled()[sellVenueId], Ids.SCALE);
long buySlip     = slippageScaled(buyVenueId,  size);
long sellSlip    = slippageScaled(sellVenueId, size);
long netProfit   = grossProfit - buyFee - sellFee - buySlip - sellSlip;
long netProfitBps = netProfit * 10000 / buyVenueAsk;
if (netProfitBps >= config.minNetProfitBps()) executeArb(...);
```

**Slippage model — linear formula (Spec §8.13, AC-ST-008):**
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

**onFill() + resetArbState() (Spec §6.2.4 — v8 AMB-004 fix: resetArbState() is a proper private method):**
```java
public void onFill(ExecutionEventDecoder decoder) {
    if (!arbActive) return;
    long clOrdId = decoder.clOrdId();
    if      (clOrdId == leg1ClOrdId) { leg1FillQtyScaled += decoder.fillQtyScaled(); if (decoder.isFinal() != BooleanType.FALSE) leg1Status = LEG_DONE; }
    else if (clOrdId == leg2ClOrdId) { leg2FillQtyScaled += decoder.fillQtyScaled(); if (decoder.isFinal() != BooleanType.FALSE) leg2Status = LEG_DONE; }

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

// AMB-004 fix (v8): resetArbState() is a separate private method, not nested inside onFill()
private void resetArbState() {
    arbAttemptId = 0; leg1VenueId = 0; leg2VenueId = 0;
    leg1ClOrdId = 0;  leg2ClOrdId = 0;
    leg1Status = LEG_PENDING; leg2Status = LEG_PENDING;
    leg1FillQtyScaled = 0; leg2FillQtyScaled = 0;
    arbCreatedCluster = 0; arbActive = false; activeArbTimeoutCorrelId = 0;
}
```

**Hedge failure → kill switch (Spec §2.2.5, AC-ARB-008):**
```java
// if hedge RiskDecision is not APPROVED:
riskEngine.activateKillSwitch("hedge_failure");
resetArbState();
// No retry — an unhedged position is a risk event requiring operator review
```

**Leg timeout via onTimer() (Spec §8.13 TASK-031, AC-ARB-010, AC-ST-007):**
```java
public void onTimer(long correlationId) {
    if (correlationId != activeArbTimeoutCorrelId || !arbActive) return;
    if (leg1Status == LEG_PENDING) submitCancel(leg1ClOrdId, leg1VenueId);
    if (leg2Status == LEG_PENDING) submitCancel(leg2ClOrdId, leg2VenueId);
    long imbalance = leg1FillQtyScaled - leg2FillQtyScaled;
    if (Math.abs(imbalance) > config.lotSizeScaled())
        submitHedge(imbalance > 0 ? Side.SELL : Side.BUY, Math.abs(imbalance),
                    imbalance > 0 ? leg2VenueId : leg1VenueId);
    resetArbState();
}
```

**No new arb while active (AC-ARB-009):** `onMarketData()` early-returns if `arbActive == true`.

**ArbStrategy state NOT in snapshot (Spec §6.2.5):** `TradingClusteredService.onTakeSnapshot()` does NOT snapshot `StrategyEngine` or its strategies. On restart, `RecoveryCoordinator` reconciles any in-flight arb legs as normal open orders. This is correct by design.

### Acceptance Criteria
- **AC-ARB-001** through **AC-ARB-011**: all 11 arbitrage ACs. Verified by T-010 and ET-004.
- **AC-ST-007**: Leg timeout fires via `cluster.scheduleTimer()`. Verified by T-010.
- **AC-ST-008**: Slippage linear formula correct value for test vector. Verified by T-010.

### INPUT → OUTPUT Paths
- **Profitable opportunity path:** `netProfitBps >= minNetProfitBps` → `executeArb()` → one `egressPublication.offer()` with both legs encoded.
- **Unprofitable path:** `netProfitBps < minNetProfitBps` → no offer; `arbActive` stays false.
- **Active arb path:** `arbActive == true` → new opportunity detected → early return; no new legs (AC-ARB-009).
- **Both legs filled path:** Both `LEG_DONE`, balanced → `resetArbState()`; no hedge.
- **Imbalanced fill path:** leg1 filled, leg2 not → `submitHedge()` → hedge order published.
- **Hedge failure path:** Hedge rejected by risk → `activateKillSwitch("hedge_failure")`.
- **Leg timeout path:** Timer fires after `legTimeoutClusterMicros` → cancel pending legs → hedge imbalance → `resetArbState()`.
- **Wrong timer ID path:** `correlationId != activeArbTimeoutCorrelId` → early return.

### Implementation Steps
1. Create `ArbStrategy` implementing `Strategy`.
2. Override `strategyId()` to return `Ids.STRATEGY_ARB`.
3. `init(ctx)`: store all context references; pre-allocate 1 `UnsafeBuffer egressBuffer` sized for 2 `NewOrderCommand` messages concatenated.
4. `onMarketData()`: guard `arbActive`; scan venue pairs for profitable spread.
5. `executeArb()`: read `cluster.logPosition()` once; derive 3 IDs; encode both legs; single `offer()`; schedule timeout timer.
6. `onFill()`: update leg states; on both `LEG_DONE`: compute imbalance, hedge if needed, `resetArbState()`.
7. `onTimer()`: guard on `activeArbTimeoutCorrelId`; cancel pending legs; hedge; reset.
8. `resetArbState()`: separate private method (v8 AMB-004 fix).
9. `submitHedge()`: call `riskEngine.preTradeCheck()`; if rejected → `activateKillSwitch("hedge_failure")`.
10. `slippageScaled()`: linear formula per spec.

### Test Implementation Steps
**T-010 ArbStrategyTest** (all test cases from Spec §D.2):
```
opportunityDetection_highFees_noTradeExecuted()          [AC-ARB-002]
opportunityDetection_sufficientSpread_tradeExecuted()    [AC-ARB-001]
netProfitFormula_includesFeesAndSlippage()               [AC-ARB-001]
arbAttemptId_usesClusterLogPosition()                    [AC-ARB-006]
base_clOrdIds_derived_from_single_logPosition()          [AC-ARB-006]
bothLegsInSingleEgressOffer()                            [AC-ARB-003]
leg1IsBuyOnCheaperVenue()                               [AC-ARB-004]
leg2IsSellOnExpensiveVenue()                            [AC-ARB-004]
bothLegs_ordType_IOC()                                  [AC-ARB-005]
fill_partialLeg1_arbActiveUntilBothFinal()
fill_bothLegsFinal_noImbalance_arbReset()
fill_imbalanced_hedgeSubmitted()                         [AC-ARB-007]
hedgeFailure_killSwitchActivated()                       [AC-ARB-008]
legTimeout_pendingLegCanceled_hedgeSubmitted()           [AC-ARB-010]
legTimeout_wrongCorrelId_ignored()
noNewArb_whileArbActive()                               [AC-ARB-009]
slippageModel_linearFormula()                            [AC-ST-008]
onMarketData_inactive_noArb()
timerRegistered_onEachArbExecution()                     [AC-ST-007]
cooldown_afterHedgeFailure_newArbSuppressed()            [AC-ARB-011]
```

**ET-004 ArbStrategyE2ETest:**
```
arbOpportunity_detected_bothLegsSubmitted()
arbLeg1FillLeg2Cancel_hedgeSubmitted()
arbHedgeFails_killSwitchActivated()
arbLegTimeout_pendingLegCanceled_imbalanceHedged()
```

### Out of Scope
No market making logic (TASK-030). No recovery awareness (handled by RecoveryCoordinator).

### Completion Checklist
- [ ] Files changed: ArbStrategy.java, T-010, ET-004
- [ ] Tests added/updated: T-010 (20 unit cases), ET-004 (4 E2E cases)
- [ ] All ACs verified: AC-ARB-001→011, AC-ST-007, AC-ST-008
- [ ] Assumptions made: `egressBuffer` sized for 2 × NewOrderCommand (fixed-length) — no resize needed
- [ ] Blockers identified: none
- [ ] Follow-up items: IT-007 ArbBatchParsingTest (gateway side) already owned by TASK-013


---

## Task Card TASK-032: AdminCli + AdminClient

### Task Description
Implement the operator command-line tool and its client library. `AdminCli` parses subcommands
(`kill-switch activate/deactivate`, `strategy pause/resume`, `snapshot trigger`,
`daily-counters reset`), builds a signed `AdminCommand` SBE message via `AdminClient`, and
publishes it to the cluster's Aeron admin channel. `AdminClient` handles HMAC-SHA256 signing
and nonce management using the authoritative strategy from v8 AMB-007: persistent file
storage as primary, wall-clock bootstrap on first run.

### Spec References
Spec §24.2 (AdminCli), §24.3 (AdminClient), §24.5 (key rotation, operatorId whitelist, nonce bounds).

### Required Existing Inputs
- `TASK-002`: `AdminCommandEncoder`, `MessageHeaderEncoder`
- `TASK-004`: `AdminConfig`
- `TASK-024`: `AdminCommandHandler` (validates the commands produced here — HMAC must match)

### Expected Modified Files
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/AdminCli.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/AdminClient.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/AdminConfig.java
```

### Do Not Modify
`AdminCommandHandler.java` and all cluster source files.

### Authoritative Spec Excerpts

**AdminCli.main() (Spec §24.2):**
```java
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
```

**AdminClient nonce strategy (Spec §8.12 + AMB-007 resolution):**
```java
// AMB-007 (v8): Authoritative nonce strategy — file persistence as primary;
// wall-clock microseconds as first-run bootstrap when no file exists.
private long loadOrBootstrapNonce(String nonceFile) {
    Path path = Path.of(nonceFile);
    if (Files.exists(path)) {
        return Long.parseLong(Files.readString(path).strip());
    }
    // First run: bootstrap from wall clock — large value, guaranteed non-repeating across restarts
    return System.currentTimeMillis() * 1000L;
}

private void saveNonce(long nonce) {
    Files.writeString(Path.of(config.nonceFile()), String.valueOf(nonce));
}

private void sendCommand(byte commandType, int venueId, int instrumentId) {
    long currentNonce = nonce++;
    saveNonce(nonce);  // persist BEFORE sending

    // Clock-step guard (AMB-007):
    if (System.currentTimeMillis() * 1000L < currentNonce) {
        throw new IllegalStateException("Nonce would decrease — clock stepped backward. " +
            "Investigate NTP before retrying.");
    }

    long signature = computeHmac(commandType, venueId, instrumentId,
                                   config.operatorId(), currentNonce);
    adminCommandEncoder
        .wrapAndApplyHeader(sendBuffer, 0, headerEncoder)
        .commandType(commandType).venueId(venueId).instrumentId(instrumentId)
        .operatorId(config.operatorId()).hmacSignature(signature).nonce(currentNonce);
    long result = adminPublication.offer(sendBuffer, 0,
                                          adminCommandEncoder.encodedLength() + HEADER_LENGTH);
    if (result < 0) throw new RuntimeException("Admin command not accepted: " + result);
}
```

**computeHmac() — byte layout MUST match AdminCommandHandler (Spec §24.3):**
```java
private long computeHmac(byte cmd, int venue, int instr, int opId, long nonce) {
    // Packed layout (17 bytes): cmd(1) + venue(4) + instr(4) + opId(4) + nonce(8)
    ByteBuffer buf = ByteBuffer.allocate(17);
    buf.put(cmd).putInt(venue).putInt(instr).putInt(opId).putLong(nonce);
    byte[] hmac = mac.doFinal(buf.array());
    return ByteBuffer.wrap(hmac).getLong();  // big-endian; first 8 of 32 bytes
}
// WARNING: AdminCommandHandler uses identical byte layout. Any change here
// must be mirrored in AdminCommandHandler.computeHmac() or all commands will fail.
```

### Acceptance Criteria
- **AC-SY-004**: Admin kill switch deactivation via signed AdminCommand. Verified by T-012 (handler side) and ET-005-3 (full E2E).
- **AC-SY-005**: Admin command with wrong HMAC rejected. Verified by T-012.
- **AC-RK-010**: Kill switch deactivation requires operator admin command. Verified by T-012 and ET-005-3.

### INPUT → OUTPUT Paths
- **Positive path:** `kill-switch deactivate` → valid HMAC signed command → cluster deactivates kill switch.
- **Wrong HMAC path:** Tampered `AdminCli` → different HMAC → `AdminCommandHandler` rejects.
- **Replay path:** Same nonce sent twice → second rejected by `AdminCommandHandler`.
- **Clock-step path:** Wall clock moved backward → `IllegalStateException` with clear message.
- **First-run path:** No nonce file → bootstrap from wall clock; file created.

### Implementation Steps
1. Create `AdminConfig.java` — loads `config/admin.toml`: `adminChannel`, `adminStreamId`, `aeronDir`, `operatorId`, `hmacKeyBase64Dev` (dev) or `hmacKeyVaultPath` (prod), `nonceFile`, `allowedOperatorIds`.
2. Create `AdminClient.java`:
   - Constructor: connect Aeron, create publication, init MAC, load/bootstrap nonce.
   - `sendCommand()` per spec — save nonce before send; clock-step guard.
   - `computeHmac()` — exact 17-byte layout matching `AdminCommandHandler`.
   - Public methods: `activateKillSwitch()`, `deactivateKillSwitch()`, `pauseStrategy()`, `resumeStrategy()`, `triggerSnapshot()`, `resetDailyCounters()`.
3. Create `AdminCli.main()` per spec §24.2.
4. Wire as standalone JAR via `platform-tooling/build.gradle.kts` `mainClass`.

### Test Implementation Steps
T-012 `AdminCommandHandlerTest` is owned by TASK-024 — **verify all 15 existing cases pass** with the `AdminClient` HMAC implementation from this card. The HMAC layout must produce identical bytes on both sides.

No new test file for this card. Integration verification:
- `./scripts/admin-deactivate-kill-switch.sh` → cluster deactivates kill switch (manual smoke test).
- ET-005-3 `killSwitch_deactivatedByAdmin_tradingResumes()` (full E2E — needs running cluster).

### Out of Scope
No cluster-side handling (TASK-024). CLI only.

### Completion Checklist
- [ ] Files changed: AdminCli.java, AdminClient.java, AdminConfig.java
- [ ] Tests added/updated: T-012 (verify existing 15 cases pass with this HMAC impl)
- [ ] All ACs verified: AC-SY-004, AC-SY-005, AC-RK-010
- [ ] Assumptions made: HMAC key loaded from TOML in dev; Vault in production; ByteBuffer big-endian for getLong()
- [ ] Blockers identified: ET-005-3 requires running cluster (TASK-027) + kill switch active state
- [ ] Follow-up items: Key rotation procedure (Spec §24.5) — operational runbook, not code

---

## Task Card TASK-033: WarmupHarness

### Task Description
Implement the JVM warmup harness that runs before any live FIX connectivity. The harness
calls `TradingClusteredService.onSessionMessage()` in a tight loop using synthetic SBE
messages (market data + execution events) via a `WarmupClusterShim` that simulates
`Cluster.time()` and `Cluster.logPosition()` without a real cluster. After warmup,
`resetWarmupState()` clears all component state. Section 27 JVM flags and the compile
command file make warmup deterministic.

### Spec References
Spec §26 (WarmupHarness + WarmupClusterShim), §27 (JIT optimization — ReadyNow, BackgroundCompilation,
compile commands), §27.6 (updated WarmupConfig — authoritative, v8 AMB-002 fix).

### Required Existing Inputs
- `TASK-004`: `WarmupConfig` (consumed by `WarmupHarnessImpl` constructor — v9.9: moved to TASK-004 per Spec §22), `GatewayConfig` (consumed by the test-harness call site)
- `TASK-016`: `WarmupHarness` interface (owned by TASK-016 per formalized ownership in v1.2)
- `TASK-026`: `TradingClusteredService` (with `installClusterShim()`, `resetWarmupState()`, `removeClusterShim()`)
- `TASK-003`: `SbeTestMessageBuilder` (for encoding synthetic messages)
- `TASK-002`: `MarketDataEventEncoder`, `ExecutionEventEncoder`

### Expected Modified Files
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/WarmupHarnessImpl.java    (implements the TASK-016 interface)
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/WarmupClusterShim.java
config/hotspot_compiler                   (already created by TASK-001 — verify content)
```

> **Note on `WarmupConfig.java`.** The `WarmupConfig` record is owned by TASK-004 and
> lives in `platform-common` per Spec §22. It is a plain immutable record with no tooling
> dependencies; placing it in `platform-common` preserves the module layering (tooling
> depends on common, never the reverse) and lets `GatewayConfig.forTest()` reference
> `WarmupConfig.development()` naturally. By the time TASK-033 runs in week 9, the class
> already exists from TASK-004 (week 1).

### Do Not Modify
`TradingClusteredService.java`, all cluster component files.

### Authoritative Spec Excerpts

**WarmupConfig — authoritative class (Spec §27.6, v8 AMB-002 fix):**
```java
// AMB-002 (v8): 3-parameter class with thresholdNanos. production()=100K iterations (not 200K).
public final class WarmupConfig {
    private final int     iterations;
    private final boolean requireC2Verified;
    private final long    thresholdNanos;

    public WarmupConfig(int iterations, boolean requireC2Verified, long thresholdNanos) { ... }

    public static WarmupConfig production()  { return new WarmupConfig(100_000, true, 500); }
    public static WarmupConfig development() { return new WarmupConfig(10_000, false, 0); }

    public int     iterations()        { return iterations; }
    public boolean requireC2Verified() { return requireC2Verified; }
    public long    thresholdNanos()    { return thresholdNanos; }
}
```

**WarmupClusterShim (Spec §26.1):**
```java
public final class WarmupClusterShim implements Cluster {
    private long simulatedTime     = System.currentTimeMillis() * 1000L;
    private long simulatedPosition = 1_000_000L;

    @Override public long time()          { return simulatedTime     += 10L; }   // +10µs per call
    @Override public long logPosition()   { return simulatedPosition++;      }    // unique longs
    @Override public Publication egressPublication() { return NOOP_PUBLICATION; }
    @Override public boolean scheduleTimer(long correlationId, long deadlineMs) { return true; }
    @Override public Cluster.Role role()  { return Cluster.Role.LEADER; }
    // All other Cluster methods: no-op for warmup
}
```

**WarmupHarnessImpl.runGatewayWarmup() sequence (Spec §26.2):**
```java
// Class is WarmupHarnessImpl (implements TASK-016's WarmupHarness interface).
// `service` (TradingClusteredService) and `config` (WarmupConfig) are injected via
// the impl's constructor; `fixLibrary` arrives as the interface-method argument.
@Override
public void runGatewayWarmup(FixLibrary fixLibrary) {
    WarmupClusterShim shim = new WarmupClusterShim();
    service.installClusterShim(shim);              // sets cluster field on all components

    UnsafeBuffer warmupBuffer = new UnsafeBuffer(new byte[512]);
    long start = System.nanoTime();

    for (int i = 0; i < config.iterations(); i++) {
        encodeSyntheticMarketData(warmupBuffer, ...);
        service.onSessionMessage(WARMUP_SESSION, shim.time(),
                                  warmupBuffer, 0, warmupBuffer.capacity());
        if (i % 10 == 0) {
            encodeSyntheticExecReport(warmupBuffer, ..., ExecType.NEW, i);
            service.onSessionMessage(WARMUP_SESSION, shim.time(),
                                      warmupBuffer, 0, warmupBuffer.capacity());
        }
        // Keep Artio's event loop alive during long warmup runs (non-blocking).
        fixLibrary.poll(1);
    }

    long elapsed = System.nanoTime() - start;
    if (config.requireC2Verified()) verifyC2Compilation(elapsed, config);

    service.resetWarmupState();   // MUST be before removeClusterShim() — clears business state
    service.removeClusterShim();  // real cluster set by onStart() later
    log.info("Warmup complete: {} iterations in {}ms", config.iterations(), elapsed / 1_000_000);
}

private void verifyC2Compilation(long elapsedNanos, WarmupConfig config) {
    long avgNanos = elapsedNanos / config.iterations();
    if (avgNanos > config.thresholdNanos()) {
        log.warn("Warmup avg {}ns > threshold {}ns. C2 may not be fully compiled. " +
                 "Do NOT go live until resolved.", avgNanos, config.thresholdNanos());
    } else {
        log.info("Warmup verified: avg {}ns/iter — C2 confirmed.", avgNanos);
    }
    // Logs WARN but does NOT throw — calling code decides whether to halt
}
```

**hotspot_compiler content (Spec §27.5 — verify file created by TASK-001):**
```
inline ig/rueishi/nitroj/exchange/cluster/MessageRouter dispatch
inline ig/rueishi/nitroj/exchange/common/ScaledMath safeMulDiv
inline ig/rueishi/nitroj/exchange/cluster/RiskEngineImpl preTradeCheck
dontinline ig/rueishi/nitroj/exchange/common/ScaledMath vwap
```

**Operational rule (Spec §27.7):** A cluster node that fails `requireC2Verified()` must not be
promoted to leader. It must log `FATAL: C2 not verified — node will not trade`, keep
`recoveryLock[all venues] = true`, and wait for operator intervention.

### Acceptance Criteria
- **AC-SY-006**: Warmup harness must complete before FIX session initiated. Verified by ET-006 (log line ordering check in smoke test).

### INPUT → OUTPUT Paths
- **Production path:** 100K iterations → avg < 500ns → "Warmup verified" log → `resetWarmupState()` → `removeClusterShim()`.
- **C2 not ready path:** avg > 500ns → `log.warn("Do NOT go live")` → continues (does not throw).
- **Development path:** 10K iterations, `requireC2Verified=false` → no timing check; fast startup for tests.
- **Reset path:** After warmup, `pool.available() == 2048` (all synthetic orders returned to pool).

### Implementation Steps
1. Create `WarmupClusterShim.java` — implements `Cluster`; monotone time/position; noop publication/timer.
2. Create `WarmupHarnessImpl.java` — implements the `WarmupHarness` interface owned by TASK-016; body is the `runGatewayWarmup(FixLibrary)` method per Spec §26.2 (loop with synthetic MD + exec events; `TradingClusteredService` and `WarmupConfig` injected via the constructor).
3. `verifyC2Compilation()` — logs WARN if avg > threshold; does NOT throw.
4. Ensure `resetWarmupState()` called BEFORE `removeClusterShim()`.
5. Verify `config/hotspot_compiler` has correct 4 directives (from TASK-001).

### Test Implementation Steps
**T-021 TradingClusteredServiceTest** — owned by TASK-026. For TASK-033, verify these cases that specifically test warmup:
- `installClusterShim_propagatedToAllComponents()` — shim reaches all components.
- `removeClusterShim_clusterNullInAllComponents()` — null after remove.
- `resetWarmupState_allComponentsReset()` — pool returns to full capacity.
- `warmup_c2Verified_avgIterationBelowThreshold()` — `@Tag("SlowTest")`, 100K iterations, avg < 500ns.
  ```java
  // From Spec §27.6. `harness.config()` is the public accessor on WarmupHarnessImpl (§26.2).
  @Test @Tag("SlowTest")
  void warmup_c2Verified_avgIterationBelowThreshold() {
      // `harness` is a WarmupHarnessImpl constructed with (service, WarmupConfig.production())
      // and `fixLibrary` is obtained from the test harness.
      long start = System.nanoTime();
      harness.runGatewayWarmup(fixLibrary);
      long avgNs = (System.nanoTime() - start) / harness.config().iterations();
      assertThat(avgNs).isLessThan(500L);
  }
  ```

### Out of Scope
No FIX connectivity (TASK-016). Warmup is cluster-process only.

### Completion Checklist
- [ ] Files changed: WarmupHarnessImpl.java, WarmupClusterShim.java
- [ ] Tests added/updated: T-021 (verify 4 warmup-specific cases pass)
- [ ] All ACs verified: none owned directly.
      **Downstream dependency:** this card produces `WarmupHarnessImpl`, which is the
      implementation that TASK-016's AC-SY-006 test (ET-006 log-line ordering check)
      depends on. AC-SY-006 cannot pass unless this card's implementation emits the
      `"Warmup complete"` log line and returns cleanly. TASK-033's own verification
      is the T-021 warmup cases (`warmup_c2Verified_avgIterationBelowThreshold()`,
      `resetWarmupState_allComponentsReset()`, etc.), which demonstrate that the
      implementation works but do not own AC-SY-006.
- [ ] Assumptions made: 100K iterations sufficient for C2 with -XX:-BackgroundCompilation on Session 1
- [ ] Blockers identified: SlowTest requires dedicated CI pipeline step with -XX:-BackgroundCompilation
- [ ] Follow-up items: ReadyNow profile setup (Spec §27.3) — operational, not code

---

## Task Card TASK-034: FIX Replay Tool

### Task Description
Implement a standalone command-line tool that reads Aeron Archive recordings and outputs
a human-readable audit log of every SBE message. `FIXReplayTool` decodes all 14 message
types and prints timestamps, templateId, key fields, and sequence numbers. `SBEReplayFile`
supports sequential read and seek-by-position. The tool does NOT modify the archive.

### Spec References
Spec §3.4 (TASK-034 card), §16.9 (audit log retention), §8 (all SBE message definitions).

### Required Existing Inputs
- `TASK-002`: all generated SBE decoders
- `TASK-001`: build scaffold (standalone JAR target in platform-tooling)

### Expected Modified Files
```
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/FIXReplayTool.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/SBEReplayFile.java
```

> **IT-005 ownership note (v1.2 change).** `IT-005 SnapshotRoundTripIntegrationTest` was
> incorrectly listed under TASK-034 in v1.1. Snapshot round-trip exercises OrderState /
> Position / RiskState snapshot production (TASK-020 / TASK-021 / TASK-018) and the
> recovery-driven load path (TASK-022), none of which TASK-034 produces. Ownership moves
> to **TASK-022 (RecoveryCoordinator)** in v1.2 — TASK-022 is the integration point for
> all three snapshot writers + the reload path. TASK-034 owns no integration test.

### Authoritative Spec Excerpts

**FIXReplayTool usage:**
```bash
java -cp platform-tooling.jar FIXReplayTool \
    --archive-dir ./archive/node-0 \
    --from-position 0 \
    --to-position 999999999 \
    --output audit.log
```

**Output format per message:**
```
[SEQ=12345] [T=2024-01-15T09:30:01.123456Z] [TEMPLATE=ExecutionEvent(2)]
  clOrdId=1000001  venueId=1  instrumentId=1  execType=FILL
  fillPrice=6500000000000  fillQty=1000000  isFinal=TRUE
```

**SBEReplayFile:** wraps `AeronArchive`; `hasNext()` / `next()` / `seekToPosition(long pos)`.

**Invalid/truncated record handling:** log WARN with position; continue to next record (do not abort).

### Acceptance Criteria
No ACs owned directly. Enables post-trade reconciliation and incident investigation.
Verification via the card's own unit tests on `SBEReplayFile` and a hand-built fixture
archive. IT-005 is owned by TASK-022 in v1.2.

### INPUT → OUTPUT Paths
- **Positive path:** Binary SBE archive → human-readable log with all 14 message types decoded.
- **Truncated record path:** Partial SBE message at end of archive → WARN logged; replay continues.
- **Unknown templateId path:** Unrecognised template → `"[UNKNOWN templateId=X]"` line; continues.

### Implementation Steps
1. Create `SBEReplayFile` — wraps AeronArchive; implements sequential read; supports seek-by-position.
2. Create `FIXReplayTool.main()` — parse CLI args; iterate `SBEReplayFile`; dispatch to per-template decoder; print formatted output.
3. Handle all 14 templateIds; `default` case prints unknown template info.
4. Invalid/truncated records: catch `Exception`, log WARN with position offset, continue.

### Test Implementation Steps

**FIXReplayToolTest** — the TASK-034 unit tests verify the replay tool in isolation:
- `sbeReplayFile_seekToPosition_nextReturnsExpectedRecord()`
- `sbeReplayFile_truncatedTrailingRecord_warnLoggedAndContinues()`
- `fixReplayTool_allTemplateIds_formattedCorrectly()`
- `fixReplayTool_unknownTemplateId_unknownLineEmitted()`
- `fixReplayTool_emptyArchive_noOutputNoException()`
- `fixReplayTool_rangeStartEnd_filtersRecordsCorrectly()`

> **Moved:** The `IT-005 SnapshotRoundTripIntegrationTest` test cases (snapshot write/load
> for OrderManager, Portfolio, Risk, recovery; full-state consistency; corrupted buffer;
> empty positions; kill-switch preservation) are now owned by **TASK-022** per v1.2
> ownership change. See TASK-022 for the full IT-005 case list.

### Out of Scope
No live trading path. Read-only replay tool.

### Completion Checklist
- [ ] Files changed: FIXReplayTool.java, SBEReplayFile.java, FIXReplayToolTest.java
- [ ] Tests added/updated: FIXReplayToolTest (6 unit cases per "Test Implementation Steps")
- [ ] All ACs verified: none owned directly
- [ ] Assumptions made: AeronArchive API available for programmatic replay in the Aeron version in the root build.gradle.kts
- [ ] Blockers identified: none
- [ ] Follow-up items: none


---

# SECTION 5 — OWNERSHIP & TRACEABILITY

---

## 5.1 Task Card Traceability Matrix

| Task ID | AC IDs Owned | Primary Spec Sections | Source Files (key) | Test Files |
|---|---|---|---|---|
| TASK-001 | AC-PF-P-005 | §19, §27.2, §27.5 | build.gradle.kts (all), docker-compose.yml, config/hotspot_compiler, scripts/* | none |
| TASK-002 | — | §8.1–8.13, §16.7, §21.4 | platform-common/src/main/resources/messages.xml | none |
| TASK-003 | — | §3.5, §7.2, §11 | Ids.java, OrderStatus.java, ScaledMath.java, SbeTestMessageBuilder.java | T-003, T-024 (T-024 is a supporting test for AC-PF-009; full ownership is TASK-021) |
| TASK-004 | — | §22, §27.6 (WarmupConfig), §G.4 (GatewayConfig.forTest() / CpuConfig.noPinning()) | ConfigManager.java, all *Config.java records (incl. VenueConfig, InstrumentConfig, WarmupConfig), all TOML files | T-002 |
| TASK-005 | — | §3.5, §17.7, §17.8, §22.2–22.3 | IdRegistry.java, IdRegistryImpl.java | T-001 |
| TASK-006 | — | §C, §D.4 | CoinbaseExchangeSimulator.java, Simulator*.java, TradingSystemTestHarness.java | T-015 |
| TASK-007 | — | §13.3, §14.3 | GatewayDisruptor.java, GatewaySlot.java | IT-001 |
| TASK-008 | AC-FX-001 | §9.8.1–§9.8.5 | CoinbaseLogonStrategy.java | T-014 |
| TASK-009 | AC-GW-001, AC-GW-002, AC-GW-003, AC-GW-004 | §3.4.1, §9.8, §4.1 | MarketDataHandler.java | T-016 |
| TASK-010 | AC-FX-005, AC-FX-006, AC-FX-007, AC-GW-005 | §3.4.2, §9.3–9.4 | ExecutionHandler.java | T-017 |
| TASK-011 | — | §3.4.2, §4.4 | VenueStatusHandler.java | T-023 |
| TASK-012 | — | §3.4.2, §14.3–14.4 | AeronPublisher.java | IT-001 (extended) |
| TASK-013 | AC-FX-002, AC-FX-003, AC-FX-004, AC-GW-006, AC-GW-007 | §2.2.4, §9.5–9.7 | OrderCommandHandler.java, ExecutionRouter.java, ExecutionRouterImpl.java | T-019, IT-007 |
| TASK-014 | — | §22.4, §16.3 | RestPoller.java | T-022, IT-006 |
| TASK-015 | — | §25.1–25.2, §13.1 | ArtioLibraryLoop.java, EgressPollLoop.java, ThreadAffinityHelper.java | — (ET-006 is owned by TASK-016) |
| TASK-016 | AC-SY-001, AC-SY-002, AC-SY-006 | §25.3, §19 | GatewayMain.java, WarmupHarness.java (interface) | ET-002, ET-006 |
| TASK-017 | — | §3.4.4, §10, §18.3 | L2OrderBook.java, InternalMarketView.java | T-004 |
| TASK-018 | AC-RK-001→009, AC-PF-P-002, AC-PF-P-003 | §12, §23, §17.2 | RiskEngine.java, RiskEngineImpl.java, RiskDecision.java, CapturingPublication.java | T-005, ET-005 |
| TASK-019 | AC-OL-007, AC-PF-P-006 | §7.1, §7.6 | OrderState.java, OrderStatePool.java | T-006 |
| TASK-020 | AC-OL-001→006, AC-OL-008 | §7, §3.4.6 | OrderManager.java, OrderManagerImpl.java | T-007 |
| TASK-021 | AC-PF-001→010 | §11, §14, §3.4.7, §17.3 | PortfolioEngine.java, PortfolioEngineImpl.java, Position.java | T-008, IT-004 |
| TASK-022 | AC-RC-001→007, AC-PF-P-004, AC-OL-008, AC-PF-010 | §2.6, §4.4, §16, §17.5 | RecoveryCoordinator.java, RecoveryCoordinatorImpl.java | T-011, ET-003, IT-005 |
| TASK-023 | AC-RK-008, AC-SY-007 | §12.6 | DailyResetTimer.java | T-013 |
| TASK-024 | AC-SY-004, AC-SY-005, AC-RK-010 | §24.4–24.5 | AdminCommandHandler.java | T-012 |
| TASK-025 | AC-FO-001→005 | §3.4.3 | MessageRouter.java | T-018, IT-003 |
| TASK-026 | AC-RC-008, AC-RC-009 | §3.6, §18.5 | TradingClusteredService.java | T-021 |
| TASK-027 | AC-SY-003 | §19.5–19.6 | ClusterMain.java | ET-006, ET-003-5 |
| TASK-028 | — | §17.9 | StrategyContext.java, StrategyContextImpl.java | — |
| TASK-029 | — | §17.1, §17.10, §2.3 | Strategy.java, StrategyEngine.java | T-020, IT-002 |
| TASK-030 | AC-MM-001→017, AC-ST-001→006, AC-PF-P-001, AC-PF-P-003 | §6.1, §2.1, §20.1 | MarketMakingStrategy.java | T-009, ET-001 |
| TASK-031 | AC-ARB-001→011, AC-ST-007, AC-ST-008 | §6.2, §2.2, §20.1 | ArbStrategy.java | T-010, ET-004 |
| TASK-032 | — | §24.2–24.3, §24.5 | AdminCli.java, AdminClient.java, AdminConfig.java | T-012 (verify) |
| TASK-033 | — | §26, §27, §27.6 | WarmupHarnessImpl.java, WarmupClusterShim.java | T-021 (warmup cases) |
| TASK-034 | — | §16.9, §8 | FIXReplayTool.java, SBEReplayFile.java | (card's own unit tests; IT-005 moved to TASK-022 in v1.2) |

---

## 5.2 AC Ownership Table

All 105 ACs. Every AC is owned by exactly one task card.

| AC ID | AC Text (verbatim from Spec E.2–E.13) | Owner |
|---|---|---|
| AC-MM-001 | Two-sided quotes submitted within 500ms of first valid market data after startup | TASK-030 |
| AC-MM-002 | Quotes requoted within 500ms when market moves > refreshThresholdBps | TASK-030 |
| AC-MM-003 | All quotes have bid price strictly less than ask price | TASK-030 |
| AC-MM-004 | Inventory skew: long position reduces bid and ask prices | TASK-030 |
| AC-MM-005 | Inventory skew: short position raises bid and ask prices | TASK-030 |
| AC-MM-006 | Zero-size quotes are never submitted | TASK-030 |
| AC-MM-007 | Kill switch cancels all live quotes within 10ms | TASK-030 |
| AC-MM-008 | Kill switch blocks all new quotes after activation | TASK-030 |
| AC-MM-009 | Market data stale suppression: quotes stop within 1 quote cycle | TASK-030 |
| AC-MM-010 | Wide spread suppression: no quotes when spread > maxTolerableSpreadBps | TASK-030 |
| AC-MM-011 | Prices rounded to tick size: bid rounded down, ask rounded up | TASK-030 |
| AC-MM-012 | Quote sizes rounded to lot size | TASK-030 |
| AC-MM-013 | Strategy only quotes on configured venueId and instrumentId | TASK-030 |
| AC-MM-014 | No redundant quotes: identical prices/sizes not resubmitted | TASK-030 |
| AC-MM-015 | Old quotes canceled before new quotes submitted on refresh | TASK-030 |
| AC-MM-016 | clOrdId is cluster.logPosition() — not random, not UUID | TASK-030 |
| AC-MM-017 | Zero allocations per onMarketData() call after warmup | TASK-030 |
| AC-ARB-001 | Opportunity formula includes taker fees and slippage before submitting | TASK-031 |
| AC-ARB-002 | Below minNetProfitBps threshold: no order submitted | TASK-031 |
| AC-ARB-003 | Both arb legs submitted in single Aeron offer call | TASK-031 |
| AC-ARB-004 | Leg 1 is always BUY on cheaper venue; Leg 2 is always SELL on expensive venue | TASK-031 |
| AC-ARB-005 | Both legs are IOC (TimeInForce=3) | TASK-031 |
| AC-ARB-006 | attemptId uses cluster.logPosition() — never UUID | TASK-031 |
| AC-ARB-007 | Leg imbalance > lotSize triggers hedge within 100ms | TASK-031 |
| AC-ARB-008 | Hedge failure triggers kill switch | TASK-031 |
| AC-ARB-009 | No new arb while one attempt is active | TASK-031 |
| AC-ARB-010 | Leg timeout: pending leg canceled and hedged within `legTimeoutClusterMicros` | TASK-031 |
| AC-ARB-011 | After hedge failure + kill switch, new arb suppressed for cooldownAfterFailureMicros | TASK-031 |
| AC-OL-001 | All valid state transitions from Section 7.3 produce correct next state | TASK-020 |
| AC-OL-002 | Invalid state transitions logged; do not change state | TASK-020 |
| AC-OL-003 | Fills on terminal orders are applied (revenue never discarded) | TASK-020 |
| AC-OL-004 | Duplicate execId (tag 17) discarded with metrics increment | TASK-020 |
| AC-OL-005 | clOrdId is unique and monotonically increasing system-wide | TASK-020 |
| AC-OL-006 | cancelAllOrders() cancels every live (non-terminal) order | TASK-020 |
| AC-OL-007 | OrderState pool: zero new allocations after warmup | TASK-019 |
| AC-OL-008 | Snapshot/restore: all live OrderState fields match after restart | TASK-020 |
| AC-PF-001 | VWAP average price correct for multiple buys (test vector) | TASK-021 |
| AC-PF-002 | Realized PnL correct on long close (test vector) | TASK-021 |
| AC-PF-003 | Realized PnL correct on short close (test vector) | TASK-021 |
| AC-PF-004 | Position correctly flips long → short (residual qty handled) | TASK-021 |
| AC-PF-005 | netQty = 0 after full close; avgEntryPrice reset to 0 | TASK-021 |
| AC-PF-006 | Unrealized PnL correct for long position, mark above entry | TASK-021 |
| AC-PF-007 | Unrealized PnL correct for short position, mark below entry | TASK-021 |
| AC-PF-008 | RiskEngine notified after every fill | TASK-021 |
| AC-PF-009 | No arithmetic overflow for large prices and quantities | TASK-021 |
| AC-PF-010 | Snapshot/restore: positions and realized PnL match exactly | TASK-021 |
| AC-RK-001 | Pre-trade checks fire in exact order (Section 12.2): CHECK 1–8 sequence | TASK-018 |
| AC-RK-002 | Kill switch rejects all orders for all venues | TASK-018 |
| AC-RK-003 | Recovery lock rejects orders for affected venue only | TASK-018 |
| AC-RK-004 | Max position check uses net projected position | TASK-018 |
| AC-RK-005 | Rate limit: sliding window expires old orders correctly | TASK-018 |
| AC-RK-006 | Daily loss limit breach activates kill switch automatically | TASK-018 |
| AC-RK-007 | Soft limits publish alert without rejecting | TASK-018 |
| AC-RK-008 | Daily reset clears daily counters but not kill switch | TASK-023 |
| AC-RK-009 | preTradeCheck() completes in < 5µs (p99) | TASK-018 |
| AC-RK-010 | Kill switch deactivation requires operator admin command | TASK-024 |
| AC-RC-001 | No order submitted during recovery window for affected venue | TASK-022 |
| AC-RC-002 | RECONCILIATION_COMPLETE within 30 seconds of LOGON_ESTABLISHED | TASK-022 |
| AC-RC-003 | Missing fill detected: synthetic FillEvent with isSynthetic=true | TASK-022 |
| AC-RC-004 | Orphan order at venue triggers cancel command | TASK-022 |
| AC-RC-005 | Balance mismatch > tolerance triggers kill switch | TASK-022 |
| AC-RC-006 | Balance mismatch within tolerance: position adjusted, trading resumes | TASK-022 |
| AC-RC-007 | RecoveryCompleteEvent published after successful reconciliation | TASK-022 |
| AC-RC-008 | REPLAY_DONE before RECONNECT_INITIATED (archive replay before FIX reconnect, Scenario A) | TASK-026 |
| AC-RC-009 | FIX session unchanged during cluster leader election | TASK-026 |
| AC-FX-001 | Coinbase Logon HMAC signature validates on sandbox | TASK-008 |
| AC-FX-002 | NewOrderSingle contains all required tags from Spec §9.5 | TASK-013 |
| AC-FX-003 | OrderCancelRequest contains tags 11, 41, 37, 54, 55, 38 | TASK-013 |
| AC-FX-004 | Coinbase replace (MsgType=G) implemented as cancel+new | TASK-013 |
| AC-FX-005 | ExecType=I (Order Status) does not trigger state transition | TASK-010 |
| AC-FX-006 | ExecType=F + LeavesQty=0 sets isFinal=TRUE | TASK-010 |
| AC-FX-007 | ExecType=F + LeavesQty>0 sets isFinal=FALSE | TASK-010 |
| AC-PF-P-001 | Zero allocations on hot path after 200K-iteration warmup | TASK-030 |
| AC-PF-P-002 | Risk check completes in < 5µs (p99) | TASK-018 |
| AC-PF-P-003 | Kill switch propagation < 10ms end-to-end | TASK-018 |
| AC-PF-P-004 | Recovery completes < 30 seconds | TASK-022 |
| AC-PF-P-005 | No GC pause > 1ms during 5-minute load test (ZGC) | TASK-001 |
| AC-PF-P-006 | OrderStatePool: no heap allocation for orders after warmup | TASK-019 |
| AC-SY-001 | System starts and accepts FIX connection within 10 seconds | TASK-016 |
| AC-SY-002 | Graceful shutdown: all threads stop; no port leaks | TASK-016 |
| AC-SY-003 | 3-node cluster: leader election completes within 500ms | TASK-027 |
| AC-SY-004 | Admin kill switch deactivation via signed AdminCommand | TASK-024 |
| AC-SY-005 | Admin command with wrong HMAC is rejected | TASK-024 |
| AC-SY-006 | Warmup harness must complete before FIX session initiated | TASK-016 |
| AC-SY-007 | Daily counters reset at midnight via cluster timer | TASK-023 |
| AC-GW-001 | MarketDataHandler stamps ingressTimestampNanos as first operation | TASK-009 |
| AC-GW-002 | FIX MsgType=X: each entry published as separate MarketDataEvent SBE | TASK-009 |
| AC-GW-003 | Unknown FIX symbol: event discarded, WARN logged, no exception | TASK-009 |
| AC-GW-004 | Ring buffer full: market data tick discarded; artio-library thread not blocked | TASK-009 |
| AC-GW-005 | ExecutionReport tag 103 decoded only when ExecType='8'; zero on all other types | TASK-010 |
| AC-GW-006 | Artio back-pressure: retry 3× with 1µs sleep → reject event to cluster | TASK-013 |
| AC-GW-007 | ReplaceOrderCommand: cancel sent first; new order only after cancel ACK | TASK-013 |
| AC-ST-001 | MarketMakingStrategy uses lastTradePrice as fairValue fallback when spread wide | TASK-030 |
| AC-ST-002 | lastTradePrice updated only from EntryType=TRADE market data events | TASK-030 |
| AC-ST-003 | No lastTradePrice + wide spread: strategy suppresses (does not crash) | TASK-030 |
| AC-ST-004 | Rejection counter incremented in onOrderRejected() | TASK-030 |
| AC-ST-005 | 3 consecutive rejections → 5-second suppression window | TASK-030 |
| AC-ST-006 | Fill resets rejection counter to 0 | TASK-030 |
| AC-ST-007 | ArbStrategy leg timeout fires via cluster.scheduleTimer() | TASK-031 |
| AC-ST-008 | ArbStrategy slippage model: linear formula produces correct value | TASK-031 |
| AC-FO-001 | On ExecutionEvent: OrderManager.onExecution() called before PortfolioEngine.onFill() | TASK-025 |
| AC-FO-002 | On ExecutionEvent: PortfolioEngine.onFill() called before RiskEngine.onFill() | TASK-025 |
| AC-FO-003 | On ExecutionEvent: RiskEngine.onFill() called before StrategyEngine.onExecution() | TASK-025 |
| AC-FO-004 | Non-fill ExecutionEvent: PortfolioEngine and RiskEngine not called | TASK-025 |
| AC-FO-005 | On MarketDataEvent: InternalMarketView.apply() called before StrategyEngine.onMarketData() | TASK-025 |

**Verification: 105 ACs listed. Every AC appears exactly once.**


---

## 5.3 Spec Section Ownership Table

| Spec Section | Owning Task Card | Notes |
|---|---|---|
| §1 System Overview | n/a | Architecture reference — no direct implementation |
| §2.1 Market Making | TASK-030 | Full algorithm in §6.1; §2.1 states business requirements |
| §2.2 Cross-Venue Arbitrage | TASK-031 | Full algorithm in §6.2 |
| §2.3 Strategy Pluggability | TASK-029 | Strategy interface and StrategyEngine |
| §2.4 Portfolio Requirements | TASK-021 | PortfolioEngine |
| §2.5 Risk Requirements | TASK-018 | RiskEngine |
| §2.6 Recovery Requirements | TASK-022 | RecoveryCoordinator |
| §3.4.1 MarketDataHandler | TASK-009 | |
| §3.4.2 AeronPublisher | TASK-012 | |
| §3.4.3 MessageRouter | TASK-025 | Integer switch authoritative per AMB-006 |
| §3.4.4 InternalMarketView | TASK-017 | |
| §3.4.5 RiskEngine | TASK-018 | |
| §3.4.6 OrderManager | TASK-020 | |
| §3.4.7 PortfolioEngine | TASK-021 | |
| §3.4.8 RecoveryCoordinator | TASK-022 | |
| §3.5 ID Registry Constants | TASK-003 | Ids.java constants |
| §3.6 TradingClusteredService | TASK-026 | |
| §4.1 Market Data Flow | TASK-009 + TASK-017 | Gateway encode + cluster book update |
| §4.2 Order Submission Flow | TASK-030 / TASK-031 | Strategy → Risk → OrderManager |
| §4.3 Execution Feedback Flow | TASK-010 + TASK-020 | Gateway decode + cluster state machine |
| §4.4 Recovery Flow | TASK-022 | RecoveryCoordinator drives all phases |
| §4.5 Admin Command Flow | TASK-024 + TASK-032 | Handler (cluster) + CLI (tooling) |
| §5 Domain Ownership | n/a | Architecture rule reference |
| §6.1 Market Making — Full Logic | TASK-030 | |
| §6.2 Arbitrage — Full Logic | TASK-031 | |
| §7 Order Model + State Machine | TASK-019 + TASK-020 | Pool + OrderManager |
| §8 SBE Event Schemas | TASK-002 | 14 wire message definitions + 3 snapshot messages (§16.7) |
| §9.8 Coinbase FIX Logon | TASK-008 | CoinbaseLogonStrategy (was §9.1–9.2 in v1.0; corrected) |
| §9.3–9.4 ExecutionReport mapping | TASK-010 | ExecutionHandler |
| §9.5–9.7 Order command FIX formats | TASK-013 | OrderCommandHandler |
| §10 L2 Book | TASK-017 | L2OrderBook + InternalMarketView |
| §11 Scaled Arithmetic | TASK-003 | ScaledMath |
| §12 Risk Engine | TASK-018 | RiskEngine + RiskDecision |
| §12.6 Daily Reset Timer | TASK-023 | DailyResetTimer |
| §13 Threading Model | TASK-015 | Poll loops + ThreadAffinityHelper |
| §13.3 GatewayDisruptor | TASK-007 | GatewayDisruptor + GatewaySlot |
| §14 AeronPublisher | TASK-012 | |
| §15 RestPoller | TASK-014 | |
| §16 RecoveryCoordinator (full) | TASK-022 | |
| §16.7 Snapshot SBE Messages | TASK-002 | `OrderStateSnapshot`, `PositionSnapshot`, `RiskStateSnapshot` (templateIds 50–52) |
| §17.1 Strategy interface | TASK-029 | |
| §17.2 RiskEngine interface | TASK-018 | |
| §17.3 PortfolioEngine interface | TASK-021 | |
| §17.5 RecoveryCoordinator interface | TASK-022 | |
| §17.6 ExecutionRouter interface (gateway) | TASK-013 | |
| §17.7 IdRegistry interface | TASK-005 | |
| §17.8 IdRegistryImpl | TASK-005 | |
| §17.9 StrategyContext | TASK-028 | AMB-001 fix applied |
| §17.10 StrategyEngine | TASK-029 | |
| §18.3 JIT / Java 21 constructs | TASK-001 / TASK-030 | Build flags; sealed class note (AMB-006) |
| §18.4 GC Strategy | TASK-001 | JVM flags in startup scripts |
| §18.5 JVM Warmup | TASK-033 | WarmupHarness; AMB-002 fix applied |
| §19.1 docker-compose | TASK-001 | |
| §19.5–19.6 ClusterMain | TASK-027 | |
| §20.1 Test vectors (arb/MM) | TASK-030 / TASK-031 | Used in T-009, T-010 |
| §20.3 Portfolio test vectors | TASK-021 | Used in T-008 |
| §22 TOML configuration | TASK-004 | All config files; night-config parser (V8 change) |
| §23.1–23.2 Risk position / rate limit | TASK-018 | Corrected field names + sliding window |
| §24.2–24.3 AdminCli / AdminClient | TASK-032 | AMB-007 nonce fix applied |
| §24.4–24.5 AdminCommandHandler | TASK-024 | |
| §25.1–25.3 Gateway poll loops | TASK-015 + TASK-016 | Loops + GatewayMain |
| §26 WarmupHarness | TASK-033 | |
| §27 JIT Optimization Guidelines | TASK-001 + TASK-033 | Config flags + WarmupConfig; AMB-002 fix |
| §C CoinbaseExchangeSimulator | TASK-006 | |
| §D.2 Unit test specs | Each owning task card | Test cases derived directly from spec |
| §D.4 TradingSystemTestHarness | TASK-006 + TASK-016 | Created in TASK-006; extended in TASK-016 |
| §E Acceptance Criteria | Section 5.2 of this plan | Full AC→task mapping |
| §F Test-to-AC mapping | Section 5.5 of this plan | |
| §G.3 CapturingPublication | TASK-018 | First cluster test to need it |
| §G.4 GatewayConfig.forTest() + CpuConfig.noPinning() | TASK-004 | Owns per Spec §22; consumed by TASK-016 in `TradingSystemTestHarness.start()` |
| §G.5 Unit test class mapping | Section 5.5 of this plan | |


---

## 5.4 Source File Ownership Table

| File Path | Owning Task | Created or Modified |
|---|---|---|
| `settings.gradle.kts` | TASK-001 | Created |
| `build.gradle.kts` (root) | TASK-001 | Created — declares pinned versions per Spec §21.1 and `e2eTest` source set per Spec §21.2 |
| `gradle/wrapper/gradle-wrapper.properties` | TASK-001 | Created |
| `gradle/wrapper/gradle-wrapper.jar` | TASK-001 | Created |
| `gradlew` / `gradlew.bat` | TASK-001 | Created |
| `platform-common/build.gradle.kts` | TASK-001 | Created; TASK-002 Modified (SBE `generateSBE` task per §21.4); TASK-003 Modified (java-test-fixtures plugin) |
| `platform-gateway/build.gradle.kts` | TASK-001 | Created |
| `platform-cluster/build.gradle.kts` | TASK-001 | Created |
| `platform-tooling/build.gradle.kts` | TASK-001 | Created |
| `config/hotspot_compiler` | TASK-001 | Created |
| `config/cluster-node-0.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `config/cluster-node-1.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `config/cluster-node-2.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `config/gateway-1.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `config/venues.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `config/instruments.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `config/admin.toml` | TASK-001 (skeleton) / TASK-004 (full) | Created/Modified |
| `docker-compose.yml` | TASK-001 | Created |
| `scripts/cluster-node-start.sh` | TASK-001 | Created |
| `scripts/gateway-start.sh` | TASK-001 | Created |
| `platform-common/src/main/resources/messages.xml` | TASK-002 | Created — 14 wire messages + 3 snapshot messages (§16.7) |
| `platform-common/.../common/Ids.java` | TASK-003 | Created |
| `platform-common/.../common/OrderStatus.java` | TASK-003 | Created |
| `platform-common/.../common/ScaledMath.java` | TASK-003 | Created |
| `platform-common/src/test/java/ig/rueishi/nitroj/exchange/test/SbeTestMessageBuilder.java` | TASK-003 | Created — exposed as test fixture consumed by all downstream test modules |
| `platform-common/.../common/ConfigManager.java` | TASK-004 | Created — uses night-config (V8 change) |
| `platform-common/.../common/GatewayConfig.java` | TASK-004 | Created (includes `forTest()` factory per Spec §G.4) |
| `platform-common/.../common/ClusterNodeConfig.java` | TASK-004 | Created |
| `platform-common/.../common/VenueConfig.java` | TASK-004 | Created per Spec §22.2 — fields: `id, name, fixHost, fixPort, sandbox`. Consumed by `IdRegistryImpl.init()` in TASK-005 for name↔id mapping only (FIX session identity is wired separately via `registerSession()`). |
| `platform-common/.../common/InstrumentConfig.java` | TASK-004 | Created per Spec §22.3 — fields: `id, symbol, baseAsset, quoteAsset`. Consumed by `IdRegistryImpl.init()` in TASK-005. |
| `platform-common/.../common/RiskConfig.java` | TASK-004 | Created |
| `platform-common/.../common/MarketMakingConfig.java` | TASK-004 | Created (incl. AMB-005 minQuoteSizeFractionBps) |
| `platform-common/.../common/ArbStrategyConfig.java` | TASK-004 | Created |
| `platform-common/.../common/FixConfig.java` | TASK-004 | Created |
| `platform-common/.../common/CpuConfig.java` | TASK-004 | Created (includes `noPinning()` factory per Spec §G.4) |
| `platform-common/.../common/DisruptorConfig.java` | TASK-004 | Created |
| `platform-common/.../common/RestConfig.java` | TASK-004 | Created |
| `platform-common/.../common/CredentialsConfig.java` | TASK-004 | Created |
| `platform-common/.../common/AdminConfig.java` | TASK-004 | Created |
| `platform-common/.../common/WarmupConfig.java` | TASK-004 | Created per Spec §22 and §27.6 — includes `production()` / `development()` factories (AMB-002 fix: 3-param class, 100K production) |
| `platform-common/.../common/ConfigValidationException.java` | TASK-004 | Created |
| `platform-common/.../registry/IdRegistry.java` | TASK-005 | Created (package `ig.rueishi.nitroj.exchange.registry` per Spec §17.7) |
| `platform-common/.../registry/IdRegistryImpl.java` | TASK-005 | Created (package `ig.rueishi.nitroj.exchange.registry` per Spec §17.8) |
| `platform-tooling/.../simulator/CoinbaseExchangeSimulator.java` | TASK-006 | Created |
| `platform-tooling/.../simulator/SimulatorConfig.java` | TASK-006 | Created |
| `platform-tooling/.../simulator/SimulatorOrderBook.java` | TASK-006 | Created |
| `platform-tooling/.../simulator/SimulatorSessionHandler.java` | TASK-006 | Created |
| `platform-tooling/.../simulator/MarketDataPublisher.java` | TASK-006 | Created |
| `platform-tooling/.../simulator/ScenarioController.java` | TASK-006 | Created |
| `platform-tooling/src/test/java/.../test/TradingSystemTestHarness.java` | TASK-006 | Created; TASK-016 Modified (test fixture, consumed by cluster + gateway test modules) |
| `platform-gateway/.../gateway/GatewayDisruptor.java` | TASK-007 | Created |
| `platform-gateway/.../gateway/GatewaySlot.java` | TASK-007 | Created |
| `platform-gateway/.../gateway/CoinbaseLogonStrategy.java` | TASK-008 | Created |
| `platform-gateway/.../gateway/MarketDataHandler.java` | TASK-009 | Created |
| `platform-gateway/.../gateway/ExecutionHandler.java` | TASK-010 | Created |
| `platform-gateway/.../gateway/VenueStatusHandler.java` | TASK-011 | Created |
| `platform-gateway/.../gateway/AeronPublisher.java` | TASK-012 | Created |
| `platform-gateway/.../gateway/OrderCommandHandler.java` | TASK-013 | Created |
| `platform-gateway/.../gateway/ExecutionRouter.java` | TASK-013 | Created |
| `platform-gateway/.../gateway/ExecutionRouterImpl.java` | TASK-013 | Created |
| `platform-gateway/.../gateway/RestPoller.java` | TASK-014 | Created |
| `platform-gateway/.../gateway/ArtioLibraryLoop.java` | TASK-015 | Created |
| `platform-gateway/.../gateway/EgressPollLoop.java` | TASK-015 | Created |
| `platform-gateway/.../gateway/ThreadAffinityHelper.java` | TASK-015 | Created |
| `platform-gateway/.../gateway/GatewayMain.java` | TASK-016 | Created |
| `platform-cluster/.../cluster/L2OrderBook.java` | TASK-017 | Created |
| `platform-cluster/.../cluster/InternalMarketView.java` | TASK-017 | Created |
| `platform-cluster/.../cluster/RiskEngine.java` | TASK-018 | Created |
| `platform-cluster/.../cluster/RiskEngineImpl.java` | TASK-018 | Created |
| `platform-cluster/.../cluster/RiskDecision.java` | TASK-018 | Created |
| `platform-cluster/src/test/java/.../test/CapturingPublication.java` | TASK-018 | Created (cluster test fixture) |
| `platform-cluster/.../order/OrderState.java` | TASK-019 | Created |
| `platform-cluster/.../order/OrderStatePool.java` | TASK-019 | Created |
| `platform-cluster/.../order/OrderManager.java` | TASK-020 | Created |
| `platform-cluster/.../order/OrderManagerImpl.java` | TASK-020 | Created |
| `platform-cluster/.../cluster/PortfolioEngine.java` | TASK-021 | Created |
| `platform-cluster/.../cluster/PortfolioEngineImpl.java` | TASK-021 | Created |
| `platform-cluster/.../cluster/Position.java` | TASK-021 | Created |
| `platform-cluster/.../cluster/RecoveryCoordinator.java` | TASK-022 | Created |
| `platform-cluster/.../cluster/RecoveryCoordinatorImpl.java` | TASK-022 | Created |
| `platform-cluster/.../cluster/DailyResetTimer.java` | TASK-023 | Created |
| `platform-cluster/.../cluster/AdminCommandHandler.java` | TASK-024 | Created |
| `platform-cluster/.../cluster/MessageRouter.java` | TASK-025 | Created |
| `platform-cluster/.../cluster/TradingClusteredService.java` | TASK-026 | Created |
| `platform-cluster/.../cluster/ClusterMain.java` | TASK-027 | Created |
| `platform-cluster/.../strategy/StrategyContext.java` | TASK-028 | Created |
| `platform-cluster/.../strategy/StrategyContextImpl.java` | TASK-028 | Created (AMB-001 fix: no duplicate field) |
| `platform-cluster/.../strategy/Strategy.java` | TASK-029 | Created |
| `platform-cluster/.../strategy/StrategyEngine.java` | TASK-029 | Created |
| `platform-cluster/.../strategy/MarketMakingStrategy.java` | TASK-030 | Created (AMB-005 fix: uses minQuoteSizeFractionBps) |
| `platform-cluster/.../strategy/ArbStrategy.java` | TASK-031 | Created (AMB-004 fix: resetArbState() extracted) |
| `platform-tooling/.../tooling/AdminCli.java` | TASK-032 | Created |
| `platform-tooling/.../tooling/AdminClient.java` | TASK-032 | Created (AMB-007 fix: file-persistence nonce) |
| `platform-tooling/.../tooling/AdminConfig.java` | TASK-032 | Created |
| `platform-tooling/.../tooling/WarmupHarnessImpl.java` | TASK-033 | Created (implements TASK-016's `WarmupHarness` interface) |
| `platform-tooling/.../tooling/WarmupClusterShim.java` | TASK-033 | Created |
| `platform-tooling/.../tooling/FIXReplayTool.java` | TASK-034 | Created |
| `platform-tooling/.../tooling/SBEReplayFile.java` | TASK-034 | Created |

> **Path abbreviation:** `platform-X/.../package/` expands to `platform-X/src/main/java/ig/rueishi/nitroj/exchange/package/`.
> Replace `ig/rueishi/nitroj/exchange` with your firm prefix as noted in §1.1.


---

## 5.5 Test File Ownership Table

> **V10.0 / v1.4.0 note.** Spec V10.0 establishes `§D.2` (unit — T-NNN), `§D.3`
> (integration — IT-NNN), and `§D.4` (end-to-end — ET-NNN) as the single authoritative
> sources for test method names and Given/When/Then bodies. Each test-class block in
> §D.2/§D.3/§D.4 carries an **Ownership** line naming the task card that CREATES the
> class file and any task cards that ADD methods to it (for shared test classes).
>
> This §5.5 table gives the **Owning Task** (the creator) and AC coverage. For the full
> method list of any test class, read the corresponding §D.2/§D.3/§D.4 block. Plan task
> card `### Test Implementation Steps` sections may list a subset of methods with
> one-line summaries as a convenience; when the plan and the spec diverge on method
> names or counts, **§D.2/§D.3/§D.4 in the spec wins**. This convention was established
> to eliminate the test-name drift that required V9.13 and V9.14 point releases.

| Test File Path | Owning Task | AC IDs Covered |
|---|---|---|
| `platform-common/.../T-001 IdRegistryImplTest` | TASK-005 | — (enabler) |
| `platform-common/.../T-002 ConfigManagerTest` | TASK-004 | — (enabler) |
| `platform-common/.../T-003 OrderStatusTest` | TASK-003 | — (enabler; T-024 co-located here as a supporting test for AC-PF-009) |
| `platform-cluster/.../T-004 L2OrderBookTest` | TASK-017 | AC-MM-009 (staleness) |
| `platform-cluster/.../T-005 RiskEngineTest` | TASK-018 | AC-RK-001→009, AC-PF-P-002 |
| `platform-cluster/.../T-006 OrderStatePoolTest` | TASK-019 | AC-OL-007, AC-PF-P-006 |
| `platform-cluster/.../T-007 OrderManagerTest` | TASK-020 | AC-OL-001→008 |
| `platform-cluster/.../T-008 PortfolioEngineTest` | TASK-021 | AC-PF-001→010 |
| `platform-cluster/.../T-009 MarketMakingStrategyTest` | TASK-030 | AC-MM-001→017, AC-ST-001→006, AC-PF-P-001 |
| `platform-cluster/.../T-010 ArbStrategyTest` | TASK-031 | AC-ARB-001→011, AC-ST-007, AC-ST-008 |
| `platform-cluster/.../T-011 RecoveryCoordinatorTest` | TASK-022 | AC-RC-001→007 |
| `platform-cluster/.../T-012 AdminCommandHandlerTest` | TASK-024 | AC-SY-004, AC-SY-005, AC-RK-010 |
| `platform-cluster/.../T-013 DailyResetTimerTest` | TASK-023 | AC-RK-008, AC-SY-007 |
| `platform-gateway/.../T-014 CoinbaseLogonStrategyTest` | TASK-008 | AC-FX-001 |
| `platform-tooling/.../T-015 CoinbaseExchangeSimulatorTest` | TASK-006 | — (test infrastructure) |
| `platform-gateway/.../T-016 MarketDataHandlerTest` | TASK-009 | AC-GW-001, AC-GW-002, AC-GW-003, AC-GW-004 |
| `platform-gateway/.../T-017 ExecutionHandlerTest` | TASK-010 | AC-FX-005, AC-FX-006, AC-FX-007, AC-GW-005 |
| `platform-cluster/.../T-018 MessageRouterTest` | TASK-025 | AC-FO-001→005 |
| `platform-gateway/.../T-019 OrderCommandHandlerTest` | TASK-013 | AC-FX-002, AC-FX-003, AC-FX-004, AC-GW-006, AC-GW-007 |
| `platform-cluster/.../T-020 StrategyEngineTest` | TASK-029 | — (enabler; AMB-003 fix: this is the correct owner) |
| `platform-cluster/.../T-021 TradingClusteredServiceTest` | TASK-026 | AC-RC-008, AC-RC-009 |
| `platform-gateway/.../T-022 RestPollerTest` | TASK-014 | — (enabler for AC-RC-002) |
| `platform-gateway/.../T-023 VenueStatusHandlerTest` | TASK-011 | — (enabler for recovery) |
| `platform-common/.../T-024 ScaledMathTest` | TASK-003 | AC-PF-009 |
| `platform-gateway/.../IT-001 GatewayDisruptorIntegrationTest` | TASK-007 (extended by TASK-012) | AC-GW-004 (back-pressure) |
| `platform-cluster/.../IT-002 L2BookToStrategyIntegrationTest` | TASK-029 | AC-FO-005 |
| `platform-cluster/.../IT-003 RiskGateIntegrationTest` | TASK-025 | AC-RK-003 |
| `platform-cluster/.../IT-004 FillCycleIntegrationTest` | TASK-021 | AC-PF-008 |
| `platform-cluster/.../IT-005 SnapshotRoundTripIntegrationTest` | TASK-022 | AC-OL-008, AC-PF-010 |
| `platform-gateway/.../IT-006 RestPollerIntegrationTest` | TASK-014 | — (enabler; AC-RC-002 fully owned by TASK-022 via ET-003 / IT-005 stack) |
| `platform-gateway/.../IT-007 ArbBatchParsingTest` | TASK-013 | AC-ARB-003 (gateway side) |
| `platform-tooling/src/test/java/.../tooling/FIXReplayToolTest` | TASK-034 | — (replay-tool unit tests; 6 cases) |
| `platform-cluster/.../ET-001 MarketMakingE2ETest` | TASK-030 | AC-MM-001→003, AC-MM-007→010, AC-MM-015, AC-PF-P-003 |
| `platform-gateway/.../ET-002 ExecutionFeedbackE2ETest` | TASK-016 | AC-OL-001, AC-FX-005→007 |
| `platform-cluster/.../ET-003 RecoveryE2ETest` | TASK-022 | AC-RC-001→007, AC-PF-P-004 |
| `platform-cluster/.../ET-004 ArbStrategyE2ETest` | TASK-031 | AC-ARB-003, AC-ARB-007, AC-ARB-008, AC-ARB-010 |
| `platform-cluster/.../ET-005 RiskE2ETest` | TASK-018 | AC-RK-002, AC-RK-006, AC-RK-010 |
| `platform-tooling/src/test/java/.../ET-006 FullSystemSmokeTest` | TASK-016 | AC-SY-001, AC-SY-002, AC-SY-003, AC-SY-006 |

---

## 5.6 Folder Structure Ownership Table

| Folder Path | Owning Task | Purpose |
|---|---|---|
| `platform-common/src/main/java/.../common/` | TASK-003–004 | Ids, OrderStatus, ScaledMath, all config records (ConfigManager, GatewayConfig, VenueConfig, InstrumentConfig, WarmupConfig, etc.) |
| `platform-common/src/main/java/.../registry/` | TASK-005 | `IdRegistry` interface + `IdRegistryImpl` (Spec §17.7–17.8; package `ig.rueishi.nitroj.exchange.registry`) |
| `platform-common/src/main/resources/` | TASK-002 | SBE schema XML (`messages.xml`) per Spec §21.3 |
| `platform-common/src/generated/java/` | TASK-002 | SBE-generated encoder/decoder classes (auto-generated per §21.4) |
| `platform-common/src/test/java/.../test/` | TASK-003 | Shared SBE test builder (`SbeTestMessageBuilder`) exposed as test fixture |
| `platform-gateway/src/main/java/.../gateway/` | TASK-007–016 | All gateway process components |
| `platform-cluster/src/main/java/.../cluster/` | TASK-017–027 | Cluster infrastructure components |
| `platform-cluster/src/main/java/.../order/` | TASK-019–020 | OrderState, OrderStatePool, OrderManager |
| `platform-cluster/src/main/java/.../strategy/` | TASK-028–031 | Strategy interface, StrategyEngine, concrete strategies |
| `platform-cluster/src/test/java/.../test/` | TASK-018 | `CapturingPublication` cluster test fixture |
| `platform-tooling/src/main/java/.../simulator/` | TASK-006 | CoinbaseExchangeSimulator and supporting classes |
| `platform-tooling/src/main/java/.../tooling/` | TASK-032–034 | AdminCli, WarmupHarnessImpl, FIXReplayTool |
| `platform-tooling/src/test/java/.../test/` | TASK-006 | `TradingSystemTestHarness` — exposed as test fixture for cluster + gateway test modules |
| `config/` | TASK-001 (structure) / TASK-004 (content) | All TOML config files and hotspot_compiler |
| `scripts/` | TASK-001 | Production JVM startup scripts |
| `archive/node-N/` | TASK-027 | Aeron Archive directories (created at runtime, not in repo) |
| `profiles/` | TASK-001 | ReadyNow JIT profile files (created at runtime, not in repo) |

---

## 5.7 Shared Support Class Ownership

| Class / File | Owning Task | Consumer Tasks |
|---|---|---|
| `Ids.java` | TASK-003 | ALL subsequent tasks |
| `OrderStatus.java` | TASK-003 | TASK-019, TASK-020, TASK-030, TASK-031 |
| `ScaledMath.java` | TASK-003 | TASK-018, TASK-021, TASK-030, TASK-031 |
| `SbeTestMessageBuilder.java` | TASK-003 | TASK-009→031 (all unit tests encoding SBE) |
| `ConfigManager.java` | TASK-004 | TASK-016, TASK-027, TASK-032, TASK-033 |
| `MarketMakingConfig.java` | TASK-004 | TASK-030 |
| `ArbStrategyConfig.java` | TASK-004 | TASK-031 |
| `GatewayConfig.java` | TASK-004 | TASK-016 (wires `forTest()` into `TradingSystemTestHarness`), all E2E tests |
| `IdRegistry.java` (interface) | TASK-005 | TASK-008, TASK-009, TASK-010, TASK-011, TASK-013, TASK-014 |
| `IdRegistryImpl.java` | TASK-005 | TASK-016, TASK-027 (ClusterMain wires it) |
| `TradingSystemTestHarness.java` | TASK-006 (Created) / TASK-016 (Extended) | TASK-016 (ET-002), TASK-022 (ET-003), TASK-027 (ET-006) |
| `GatewayDisruptor.java` | TASK-007 | TASK-009, TASK-010, TASK-011, TASK-012 |
| `GatewaySlot.java` | TASK-007 | TASK-009, TASK-010, TASK-011, TASK-012 |
| `CapturingPublication.java` | TASK-018 | TASK-018, TASK-021, TASK-022, TASK-024, TASK-030, TASK-031 |
| `OrderState.java` | TASK-019 | TASK-020 |
| `OrderStatePool.java` | TASK-019 | TASK-020, TASK-026 (resetWarmupState pool drain) |
| `OrderManager.java` (interface) | TASK-020 | TASK-021, TASK-022, TASK-025, TASK-028, TASK-030, TASK-031 |
| `RiskEngine.java` (interface) | TASK-018 | TASK-021, TASK-022, TASK-024, TASK-025, TASK-028, TASK-030, TASK-031 |
| `PortfolioEngine.java` (interface) | TASK-021 | TASK-022, TASK-023, TASK-025, TASK-028 |
| `RecoveryCoordinator.java` (interface) | TASK-022 | TASK-025, TASK-028, TASK-030, TASK-031 |
| `InternalMarketView.java` | TASK-017 | TASK-025, TASK-028, TASK-030, TASK-031 |
| `StrategyContext.java` (interface) | TASK-028 | TASK-029, TASK-030, TASK-031 |
| `Strategy.java` (plain interface) | TASK-029 | TASK-030, TASK-031 |
| `StrategyEngine.java` | TASK-029 | TASK-024, TASK-025, TASK-026, TASK-027 |
| `WarmupConfig.java` | TASK-004 | TASK-004 (creates), TASK-016 (consumed via `GatewayConfig.forTest().warmup(WarmupConfig.development())`), TASK-033 (constructor-injected into `WarmupHarnessImpl`). 3-param record per AMB-002 fix. Lives in `platform-common` per Spec §22. |
| `MessageRouter.java` | TASK-025 | TASK-026 |

---

## 5.8 Ownership Rule for New Files

> Any file, helper, folder, or interface introduced in this project that is not listed as a
> Required Existing Input in the creating task must be explicitly owned by exactly one task card.
>
> If a file is consumed by multiple cards, the card that **creates** it is the owner.
> All consuming cards must list it as a **Required Existing Input**.
>
> No file may have zero owners. No file may have two or more owners marked as "Created".
> A card marked "Modified" is a secondary modifier, not an owner.
>
> This rule is enforced by the tables in §5.4 (source files) and §5.5 (test files).
> Any file introduced during implementation that is not in these tables must be immediately
> assigned an owner and added to the appropriate table via a spec amendment.


---

# SECTION 6 — DEFINITION OF DONE

The implementation is **complete** when **all** of the following are simultaneously true:

## 6.1 Task Card Completeness
- [ ] Every task card's **Completion Checklist** is fully checked (all boxes ticked).
- [ ] No task card has an unchecked "Blockers identified" item (all blockers resolved or formally deferred with owner).

## 6.2 Acceptance Criteria Coverage
- [ ] Every AC in Section 1 (all 105 ACs) is marked **verified** against at least one passing test.
- [ ] No AC is in a "partially verified" state — every AC has a deterministic pass/fail test.
- [ ] All timing ACs (`@RequiresProductionEnvironment`) pass on colocation hardware and are documented with measured values.

## 6.3 Test Suite Health
- [ ] `./gradlew test` (standard suite, no `@SlowTest`) passes **green** with zero failures across all modules.
- [ ] `./gradlew ciTest` (including `@SlowTest`) passes green on the pre-release CI pipeline.
- [ ] Test coverage ≥ 90% line coverage on all primary classes (confirmed by JaCoCo report).
- [ ] No E2E test marked `@Disabled` without an approved deferral ticket.

## 6.4 File Ownership Integrity
- [ ] Every source file in `platform-*/src/main/java/` appears in §5.4 with exactly one owning task.
- [ ] Every test file in `platform-*/src/test/java/` appears in §5.5 with exactly one owning task.
- [ ] No source or test file exists in the codebase that is not in the §5.4/§5.5 tables.

## 6.5 Spec Alignment
- [ ] All AMB fixes (AMB-001 through AMB-009) baked into Spec V10.0 are honored in the corresponding implementation file. Every `// FIX AMB-NNN` annotation in source code matches the spec's resolution.
- [ ] No open `// UNRESOLVED-AMB:` comments remain in any source file.
- [ ] Any new ambiguity discovered during implementation is raised per §3.6 (Handling a Spec Ambiguity Discovered Mid-Execution) — never silently resolved.

## 6.6 Code Quality
- [ ] `./gradlew check` (Checkstyle + compilation) passes with zero errors and zero warnings on all modules.
- [ ] No hot-path class contains `new`, autoboxing, lambda capture, or `String` concatenation (verified by `@HotPath` Checkstyle rule or async-profiler `--alloc` report showing 0 b/op).
- [ ] No `@SuppressWarnings` annotation exists without an explanatory inline comment.

## 6.7 Operational Readiness
- [ ] `docker-compose up` starts all services on a clean machine without errors.
- [ ] `./scripts/cluster-node-start.sh config/cluster-node-0.toml` starts a cluster node without errors.
- [ ] `./scripts/gateway-start.sh config/gateway-1.toml` starts the gateway without errors.
- [ ] ET-006 `fullSystemSmoke_startsAndTrades_noExceptions()` passes against the 3-node cluster.
- [ ] `config/hotspot_compiler` contains the correct 4 directives from Spec §27.5 and is referenced by the production startup scripts.

---


# SECTION 7 — VERIFICATION SUMMARY

---

## 7.1 Completeness Status

- [x] Every AC is owned by exactly one task card. (Verified: 105 ACs, each appears once in §5.2.)
- [x] Every deliverable has an owning task card. (Verified: all source files in §5.4, test files in §5.5.)
- [x] No file ownership conflicts exist. (Verified: no file appears twice as "Created" in §5.4.)
- [x] All spec references in this plan target `NitroJEx_Master_Spec_V10.0.md`. No stale V7/V8 references remain.
- [x] Every card's required inputs and expected outputs reference only files owned by that card or explicitly listed as Required Existing Inputs.
- [x] All ambiguities surfaced during plan authoring (AMB-001 through AMB-009) are resolved in Spec V10.0. No candidate or unresolved ambiguities remain at the plan level.

---

## 7.2 Assumptions Made

```
Assumption ID: ASM-001
Context: TASK-001 (build scaffold)
Assumption: Gradle 8.x and JDK 21 are available in the CI environment. The SBE tool
  version pinned in the root `build.gradle.kts` (Spec §21.1: sbe-tool 1.37.1) is compatible
  with Gradle 8.
Risk if Wrong: Build fails at dependency resolution. Fix: pin exact Gradle wrapper version;
  verify SBE generation before starting TASK-002.
```

```
Assumption ID: ASM-002
Context: TASK-002 (SBE schema)
Assumption: NewOrderCommand is a fixed-length SBE message with no <data> varString fields.
  This is relied on in TASK-013 (OrderCommandHandler) to use blockLen rather than
  encodedLength() for cursor advancement.
Risk if Wrong: If NewOrderCommand ever gains a varString field, the cursor arithmetic in
  OrderCommandHandler.onFragment() will be wrong, silently corrupting subsequent messages
  in arb batch fragments. Review anytime the SBE schema is modified.
```

```
Assumption ID: ASM-003
Context: TASK-006 (CoinbaseExchangeSimulator)
Assumption: Artio v0.175 supports acceptor mode with the API used in CoinbaseExchangeSimulator.
  Artio's acceptor API may differ from the initiator API in ways not fully documented.
Risk if Wrong: Simulator startup fails. Fix: test Artio acceptor mode in a spike before
  committing to the simulator design.
```

```
Assumption ID: ASM-004
Context: TASK-017 (InternalMarketView)
Assumption: Java 21 Panama Foreign Memory API is stable (no incubator flag needed).
  `Arena.ofShared()` and `MemorySegment` are available without `--add-modules` flags.
Risk if Wrong: Compile error or runtime error. Fix: verify with `java --list-modules | grep foreign`.
```

```
Assumption ID: ASM-005
Context: TASK-018 (RiskEngine)
Assumption: ScaledMath.safeMulDiv correctly handles the notional overflow case
  (qtyScaled * priceScaled > Long.MAX_VALUE). This is relied on by preTradeCheck() CHECK 5.
  If ScaledMath has a bug, a large-notional order could pass the notional check silently.
Risk if Wrong: Silent risk limit bypass. Mitigated by T-024 test vector (1 BTC @ $65K)
  that explicitly exercises the BigDecimal path.
```

```
Assumption ID: ASM-006
Context: TASK-026 (TradingClusteredService) + TASK-033 (WarmupHarness)
Assumption: `TradingClusteredService.installClusterShim()` is safe to call before
  `onStart()` is called by Aeron Cluster. The warmup harness calls it before the real
  cluster is started.
Risk if Wrong: If Aeron Cluster calls any lifecycle method before the shim is installed,
  a NullPointerException on `cluster.time()` would occur. Mitigated by T-021
  `installClusterShim_realClusterAlreadySet_throws()` guard.
```

```
Assumption ID: ASM-007
Context: TASK-030 (MarketMakingStrategy) — AC-PF-P-001
Assumption: Zero-allocation on the hot path is achievable after JIT warmup. This is verified
  by async-profiler `--alloc` sampling in T-009 performance case. The assumption is that
  the JIT will scalar-replace RiskDecision records and that no implicit boxing occurs in
  the LongObjectHashMap operations on the cluster-service thread.
Risk if Wrong: Allocation on the hot path causes GC pressure and AC-PF-P-001 failure.
  Fix: use async-profiler in allocation profiling mode during integration testing; add
  @HotPath Checkstyle rule to flag allocations.
```

```
Assumption ID: ASM-008
Context: TASK-033 (WarmupHarnessImpl) — AC-SY-006
Assumption: The SlowTest `warmup_c2Verified_avgIterationBelowThreshold()` can achieve
  avg < 500ns when run with -XX:-BackgroundCompilation on the CI hardware. On slow CI
  runners this test may spuriously fail.
Risk if Wrong: Test is tagged @SlowTest and excluded from standard ./gradlew test.
  It runs only in the pre-release CI pipeline on dedicated hardware with -XX:-BackgroundCompilation.
  Spurious failures on underpowered hardware must be investigated before blocking release.
```

```
Assumption ID: ASM-009
Context: TASK-005 / TASK-017 / TASK-018 — Agrona 2.4.0 compatibility (V9 dependency refresh)
Assumption: The Agrona 2.x API used by the plan (`Object2IntHashMap`, `LongHashSet`,
  `UnsafeBuffer`, `Int2ObjectHashMap`, `DeadlineTimerWheel`) is source-compatible with
  the plan's usage in TASK-005, TASK-017, TASK-018, TASK-022, TASK-024. Spec §21.1 flags
  a 1.x → 2.x major version bump; breaking changes include removal of
  `org.agrona.concurrent.SigInt` (use `ShutdownSignalBarrier`), stricter validation in
  `SystemUtil.parseDuration` / `parseSize`, and behavioural fixes in map/set semantics.
Risk if Wrong: Compilation breaks in TASK-005/017/018. Mitigation: Spike-1 — a one-day
  verification pass that compiles a throw-away harness exercising every Agrona entry
  point the plan uses, run in parallel with TASK-001/002. Spike-1 is parallelizable with
  TASK-001 scaffolding and does not extend the critical path. If Spike-1 surfaces an
  incompatibility, the affected task card is revised before implementation begins.
```

```
Assumption ID: ASM-010
Context: TASK-008 (CoinbaseLogonStrategy) — tag 8013 dictionary-driven implementation
Assumption: Artio's CodecGenerationTool produces a strongly-typed setter on the generated
  `LogonEncoder` when field 8013 is declared on the Logon message in the customised FIX 4.2
  dictionary. Setter shape: `logon.cancelOrdersOnDisconnect(byte)`. This is the Artio wiki's
  documented pattern for extending message definitions via dictionary customisation.
Risk if Wrong: The generated accessor name or signature differs from the expected shape.
  Mitigation: Step 3 of TASK-008's Implementation Steps explicitly verifies the generated
  setter exists before writing the call site in `configureLogon(...)`. If the accessor has
  a different name (e.g. the Artio naming convention camel-cases differently), TASK-008
  is updated in-place with the actual generated name and the plan file is amended.
```

---

## 7.3 Phase Model

Implementation is scheduled in three phases. Phase 1 runs on commodity hardware (developer
laptops, standard CI). Phase 2 runs on the performance test environment (colocation or
equivalent bare-metal hardware). Phase 3 runs against Coinbase sandbox and then production.

### Phase 1 — Implementation and correctness (Weeks 1–10)

**Scope:** TASK-001 through TASK-034. All 99 non-timing ACs verified in CI.

**Environment:** Developer laptops and standard CI runners. Docker Compose for 3-node
cluster testing. CoinbaseExchangeSimulator (TASK-006) in place of real Coinbase.

**Exit criteria:**
- `./gradlew check` passes green with zero errors and zero warnings on all modules.
- `./gradlew test` passes green (all unit + integration tests; excludes `@E2E`, `@SlowTest`, `@RequiresProductionEnvironment`).
- `./gradlew e2eTest` passes green (all E2E tests on loopback with CoinbaseExchangeSimulator).
- `./gradlew ciTest` passes green on pre-release CI (includes `@SlowTest`; still excludes production-environment tests).
- All §6 Definition of Done checkpoints satisfied except the timing ACs (AC-PF-P-001 through AC-PF-P-005, AC-SY-003).
- Zero-allocation verification passes in CI via async-profiler `--alloc` on hot-path classes.

**Gate to Phase 2:** All above criteria satisfied. Sign-off recorded.

### Phase 2 — Performance validation (Weeks 11–12, may extend)

**Scope:** Verify the 6 timing-sensitive ACs on real hardware; 1–2 tuning iterations
expected for JIT flags, ReadyNow profile training, CPU pinning adjustments, and GC tuning.

**Environment:** Colocation or equivalent bare-metal hardware per Spec §19.3 — isolcpus,
Solarflare NICs, Azul Platform Prime (C4) or OpenJDK ZGC, huge pages configured.

**ACs verified in this phase:**
- AC-PF-P-001 Zero allocations on hot path after 200K-iteration warmup.
- AC-PF-P-002 Risk check completes in < 5µs (p99).
- AC-PF-P-003 Kill switch propagation < 10ms end-to-end.
- AC-PF-P-005 No GC pause > 1ms during 5-minute load test (ZGC).
- AC-SY-003 3-node cluster leader election completes within 500ms.
- Performance harness from Spec §20.4 executes clean (all histogram percentile targets met).

**Exit criteria:**
- All 6 timing ACs pass on hardware under sustained 10K msg/sec load for 5 minutes.
- HDR Histogram output captured and archived for every run.
- Azul ReadyNow profile trained over three consecutive production-equivalent runs per Spec §27.3.
- JVM flag set committed as the production configuration.

**Gate to Phase 3:** All above criteria satisfied. Sign-off recorded.

### Phase 3 — Production acceptance (post-Phase 2)

**Scope:** Connect to real Coinbase sandbox, run in market-data-only shadow mode, then
enable order submission under operator supervision.

**Stages:**
1. Coinbase sandbox logon + market-data subscription only (no orders). Run for 1–2 weeks.
2. Sandbox order submission at minimum size under operator supervision. Verify full
   round-trip (order → fill → portfolio → risk → snapshot). Run for 1 week.
3. Production deployment. Gradual rollout per operator runbook.

**Exit criteria:**
- All ET-* E2E tests pass against real Coinbase sandbox (not just CoinbaseExchangeSimulator).
- 24 hours of continuous shadow-mode operation with zero unexpected disconnects, zero
  allocation spikes, zero long GC pauses.
- Operator runbook, kill-switch drills, and recovery drills exercised end-to-end.

---

## 7.4 Spikes

Small, time-boxed verification efforts that de-risk specific implementation choices.
Spikes are parallelizable with early task cards; they do not extend the critical path.

```
Spike ID: Spike-1
Purpose: Verify Agrona 2.4.0 API compatibility with the plan's usage
Duration: 1 day
Parallel with: TASK-001, TASK-002
Blocks: TASK-005, TASK-017, TASK-018 (must complete before these cards begin coding)
Scope:
  - Compile a throw-away harness that instantiates every Agrona entry point the plan
    uses: Object2IntHashMap, LongHashSet, Int2ObjectHashMap, UnsafeBuffer,
    DeadlineTimerWheel, any counter or collection type referenced in TASK-005, TASK-017,
    TASK-018, TASK-022, TASK-024.
  - Confirm no removed API is called (specifically: SigInt — should be ShutdownSignalBarrier).
  - Confirm map/set semantics match the plan's assumptions (no silent behavioural change
    affecting correctness).
Acceptance:
  - Harness compiles cleanly against agrona:2.4.0.
  - Harness runs a smoke test exercising every entry point; no runtime exception.
  - Any incompatibility found is documented and the affected task card is revised
    in-place before that card is started.
Deliverable:
  - `spike-1-agrona-compat/` throw-away module in the repo (deletable after verification).
```

---
