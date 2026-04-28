# NitroJEx — V11 Execution-Ready Implementation Plan

## Version

v2.0.0

## Based On

`nitrojex_implementation_plan_v1.4.0.md` (frozen V10 execution baseline)

## Scope

Implements `NitroJEx_Master_Spec_V11.0.md`.

## Source Spec

`NitroJEx_Master_Spec_V11.0.md`

## Rules

- Do not modify `NitroJEx_Master_Spec_V10.0.md`.
- Do not modify `nitrojex_implementation_plan_v1.4.0.md`.
- Do not reuse TASK-001 through TASK-034.
- V11 task cards start at TASK-101.
- Keep generated FIX codecs version-isolated by package.
- Keep venue-specific behavior out of FIX protocol plugins.
- Keep FIX protocol mechanics out of venue plugins.
- Keep Coinbase-specific gateway classes under `gateway/venue/coinbase`.
- Keep config parsing venue-plugin-name agnostic; unsupported venue plugins fail in gateway plugin registry/composition.
- Keep market books separate from NitroJEx own order state.
- Arbitrage must use external executable liquidity, not raw gross L2 size, when own visible liquidity may be present.

---

# Section 1 — V11 Acceptance Criteria

## FIX Protocol Plugins

AC-V11-FIX-001 The gateway can select a FIX protocol plugin from venue config.

AC-V11-FIX-002 FIX 4.2 codecs are generated into a package that does not collide with other FIX versions.

AC-V11-FIX-003 FIX 4.4 codecs are generated into a package that does not collide with other FIX versions.

AC-V11-FIX-004 FIXT.1.1/FIX 5.0SP2 codecs are generated from split transport/application dictionaries.

AC-V11-FIX-005 Artio session configuration uses the selected plugin's dictionary class.

## Venue Plugins

AC-V11-VENUE-001 The gateway can select a venue plugin from venue config.

AC-V11-VENUE-002 Coinbase behavior is represented by `CoinbaseVenuePlugin`.

AC-V11-VENUE-003 Venue logon customization is provided by the venue plugin.

AC-V11-VENUE-004 Venue order-entry behavior is hidden behind `VenueOrderEntryAdapter`.

AC-V11-VENUE-005 Unsupported venue plugin IDs fail gateway startup/composition with a clear registry error.

AC-V11-VENUE-006 Coinbase-specific gateway production classes live under `gateway/venue/coinbase`; shared venue abstractions live under `gateway/venue`.

AC-V11-VENUE-007 Venue-specific credential fallback is owned by the selected venue plugin, not by shared `GatewayMain`.

## L2/L3 Market Data

AC-V11-MD-001 Each venue config declares `marketDataModel = L2` or `L3`.

AC-V11-MD-002 L2 venues use shared `AbstractFixL2MarketDataNormalizer`.

AC-V11-MD-003 L3 venues use shared `AbstractFixL3MarketDataNormalizer`.

AC-V11-MD-004 L2 and L3 normalizers expose venue-specific `enrich(...)` hooks.

AC-V11-MD-005 L3 venues maintain active order-level state sufficient to derive L2.

AC-V11-MD-006 Derived L2 updates from L3 are applied to per-venue L2 books.

AC-V11-MD-007 Consolidated L2 sums per-venue level sizes and preserves venue contribution boundaries.

AC-V11-MD-008 Market view exposes gross per-venue L2, optional per-venue L3, consolidated L2, and external executable liquidity without mixing own order state directly into market books.

## Arbitrage and Own-Liquidity Controls

AC-V11-ARB-001 The existing `ArbStrategy` is recognized as a multi-venue strategy because it scans configured venue IDs and submits two venue legs when net edge exceeds configured threshold.

AC-V11-ARB-002 Arbitrage opportunity detection uses external executable liquidity: gross venue L2 minus own visible working orders where identifiable or conservatively estimable.

AC-V11-ARB-003 Before submitting an arbitrage leg, the strategy performs a same-venue/same-instrument self-cross check against own working orders.

AC-V11-ARB-004 L2-only venues use conservative own-liquidity subtraction by venue, instrument, side, and price.

AC-V11-ARB-005 L3 venues reconcile own visible orders by matching market-data order IDs to known venue order IDs when reliable IDs are available.

AC-V11-ARB-006 Venue-native self-trade-prevention policy is supported through venue order-entry policy hooks where the venue supports it.

AC-V11-ARB-007 Arbitrage tests cover false opportunities caused by own visible liquidity, self-cross rejection/reduction, L2 approximation, L3 exact matching, leg timeout, imbalance hedge, and cooldown after failure.

## Coinbase FIX L2/L3 and Test Coverage

AC-V11-TEST-001 V11 task-owned behavior has unit tests covering positive, negative, edge, exception, and failure cases where applicable.

AC-V11-TEST-002 V11 integration tests validate plugin composition across config, gateway, generated FIX codecs, SBE messages, and cluster market views.

AC-V11-TEST-003 CoinbaseExchangeSimulator emits Coinbase FIX L3 snapshot/add/change/delete flows.

AC-V11-TEST-004 CoinbaseExchangeSimulator emits Coinbase FIX L3 failure flows including malformed messages, sequence gaps, and disconnect/reconnect.

AC-V11-TEST-005 End-to-end tests prove Coinbase FIX L3 updates produce correct `MarketByOrderEvent`, `VenueL3Book`, derived per-venue L2, and optional consolidated L2 behavior.

AC-V11-TEST-006 End-to-end tests prove Coinbase FIX L3 failure cases fail safely without corrupting L3 or L2 book state.

AC-V11-TEST-007 Every new or modified V11 production class has task-owned tests covering constructors/factories, public methods, state transitions, parser branches, exception paths, and failure side effects where applicable.

AC-V11-TEST-008 Private helper behavior is covered through public APIs, or a justified package-private test seam is documented in the task output.

AC-V11-TEST-009 QA/UAT with real Coinbase connectivity is blocked until all V11 unit, integration, and Coinbase Simulator E2E tests pass.

AC-V11-TEST-010 Real Coinbase QA/UAT is not used as a replacement for automated local coverage; it is only the final venue-connectivity validation stage.

AC-V11-TEST-011 CoinbaseExchangeSimulator provides an automated local FIX acceptor path that exercises real FIX connectivity semantics, not only direct method calls or harness-level event injection.

AC-V11-TEST-012 Automated Coinbase Simulator live-wire E2E tests prove Coinbase FIX L2 market data through gateway, disruptor, Aeron, cluster market view, venue L2 book, optional consolidated L2, strategy observation, cluster egress, and gateway order-entry handling.

AC-V11-TEST-013 Automated Coinbase Simulator live-wire E2E tests prove Coinbase FIX L3 market data through gateway, disruptor, Aeron, cluster `VenueL3Book`, derived venue L2, optional consolidated L2, strategy observation, cluster egress, and gateway order-entry handling.

AC-V11-TEST-014 The pre-QA/UAT gate includes explicit Coinbase Simulator live-wire L2 and L3 E2E tests; real Coinbase QA/UAT remains blocked until those tests pass or any environment-only limitation is documented and approved.

## Low-Latency Determinism and Zero-Allocation Hot Paths

AC-V11-PERF-001 The spec defines hot-path versus cold/control-path allocation boundaries.

AC-V11-PERF-002 Hot-path market-data, order, risk, strategy, and book code avoids general-purpose per-event `Map`, `List`, `String`, record, or object allocation where practical.

AC-V11-PERF-003 FIX L2/L3 normalizers provide a path to parse from byte buffers/direct buffers without converting normal market-data tag values to `String`.

AC-V11-PERF-004 Venue order IDs have a bounded hot-path representation that avoids allocating Java `String` objects per event and remains collision-correct.

AC-V11-PERF-005 Coinbase REST JSON parsing is isolated to a cold/side path and cannot leak JSON objects into book, risk, strategy, or order-manager hot paths.

AC-V11-PERF-006 Expected hot-path failures use counters/status/drop behavior instead of exception construction and formatted logging.

AC-V11-PERF-007 JMH/allocation benchmark coverage exists for gateway normalizers, book mutation, own-liquidity views, risk decisions, strategy ticks, and order encoding.

AC-V11-PERF-008 Benchmark output reports allocation rate and GC behavior; any non-zero hot-path allocation is documented with remediation ownership.

AC-V11-PERF-009 Deterministic-core replay tests prove the same ordered internal SBE stream and initial state produce the same book, order, risk, strategy, and outbound command results.

AC-V11-PERF-010 The project documentation does not claim full zero-GC / zero-allocation production readiness until benchmark evidence supports it.

---

# Section 2 — Task Cards

## TASK-101 — Create V11 Config Model

### Objective

Add config fields and enums needed to select FIX protocol plugin, venue plugin, and market-data model per venue.

### Files to Update

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/VenueConfig.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/ConfigManager.java
config/venues.toml
```

### Files to Create

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/FixPluginId.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/MarketDataModel.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/VenueCapabilities.java
```

### Acceptance Criteria

- AC-V11-FIX-001
- AC-V11-MD-001
- AC-V11-TEST-001
- AC-V11-TEST-007
- Unsupported enum values fail with clear config errors.
- Unit tests cover valid L2 config, valid L3 config, unsupported FIX plugin enum, unknown venue plugin string accepted for gateway registry resolution, unsupported market-data model, and missing required plugin fields.

---

## TASK-102 — Add FIX Protocol Plugin Interfaces and Registry

### Objective

Introduce the FIX protocol plugin abstraction and registry.

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/fix/FixProtocolPlugin.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/fix/FixProtocolPluginRegistry.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/fix/ArtioFix42Plugin.java
```

### Acceptance Criteria

- AC-V11-FIX-001
- AC-V11-TEST-001
- AC-V11-TEST-007
- FIX 4.2 behavior can be selected through the registry.
- No venue-specific behavior appears in the FIX plugin interface.
- Unit tests cover successful FIX 4.2 lookup, unsupported plugin lookup, null/blank plugin IDs, and dictionary-class exposure.

---

## TASK-103 — Version-Isolate Artio FIX Code Generation

### Objective

Replace single global FIX codegen with version-isolated generation.

### Files to Update

```text
platform-gateway/build.gradle.kts
```

### Acceptance Criteria

- AC-V11-FIX-002
- AC-V11-FIX-003
- AC-V11-FIX-004
- AC-V11-TEST-002
- AC-V11-TEST-007
- Generated packages do not collide.
- Integration tests or compile checks prove all generated source sets can coexist.
- Failure coverage proves missing dictionary inputs fail the codegen task clearly.

---

## TASK-104 — Add Venue Plugin Interfaces and Coinbase Plugin Shell

### Objective

Introduce the venue plugin abstraction and wrap current Coinbase behavior behind `CoinbaseVenuePlugin`.

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/VenuePlugin.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/VenuePluginRegistry.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseVenuePlugin.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/VenueLogonCustomizer.java
```

### Acceptance Criteria

- AC-V11-VENUE-001
- AC-V11-VENUE-002
- AC-V11-VENUE-003
- AC-V11-VENUE-005
- AC-V11-VENUE-006
- AC-V11-VENUE-007
- AC-V11-TEST-001
- AC-V11-TEST-007
- Unit tests cover Coinbase plugin lookup, unsupported venue lookup, null/blank venue plugin IDs, venue-owned credential fallback, and logon customizer exposure.

---

## TASK-105 — Refactor Order Entry Behind VenueOrderEntryAdapter

### Objective

Remove direct gateway-core dependency on generated FIX 4.2 encoder classes.

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/VenueOrderEntryAdapter.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/OrderEntryPolicy.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/StandardOrderEntryAdapter.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseOrderEntryPolicy.java
```

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/ExecutionRouterImpl.java
```

### Acceptance Criteria

- AC-V11-VENUE-004
- AC-V11-TEST-001
- AC-V11-TEST-007
- Coinbase native replace suppression remains supported.
- Current outbound new/cancel/status behavior remains covered by tests.
- Unit tests cover successful new/cancel/status sends, native replace suppression, back-pressure failure, unknown instrument, and invalid order-entry policy behavior.
- Unit tests cover venue STP enrichment hooks when a venue policy provides them.

---

## TASK-106 — Extract Shared L2 FIX Market Data Normalizer

### Objective

Move current L2 parsing behavior into `AbstractFixL2MarketDataNormalizer` with venue `enrich(...)` hook.

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/MarketDataNormalizer.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/L2MarketDataContext.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL2MarketDataNormalizer.java
```

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/MarketDataHandler.java
```

### Acceptance Criteria

- AC-V11-MD-002
- AC-V11-MD-004
- AC-V11-TEST-001
- AC-V11-TEST-007
- AC-V11-TEST-008
- Existing L2 market-data tests continue to pass.
- Unit tests cover valid snapshot, valid incremental, unknown symbol, malformed numeric field, missing symbol, zero-size delete behavior, ring-full drop, and `enrich(...)` hook invocation.

---

## TASK-107 — Add L3 MarketByOrder Schema and Normalizer Base

### Objective

Add normalized L3 order-level event and shared L3 normalizer base.

### Files to Update

```text
platform-common/src/main/resources/messages.xml
```

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/L3MarketDataContext.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL3MarketDataNormalizer.java
```

### Acceptance Criteria

- AC-V11-MD-003
- AC-V11-MD-004
- AC-V11-TEST-001
- AC-V11-TEST-007
- AC-V11-TEST-008
- L3 normalizer supports MDEntryID/tag 278 by default.
- Unit tests cover valid L3 snapshot/add/change/delete parsing, missing MDEntryID, malformed price/size, unknown symbol, unsupported entry type, and `enrich(...)` hook invocation.

---

## TASK-108 — Add VenueL3Book and Derived L2 Updates

### Objective

Maintain minimum active L3 state and derive per-venue L2 updates.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/VenueL3Book.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/VenueL3BookTest.java
```

### Acceptance Criteria

- AC-V11-MD-005
- AC-V11-MD-006
- AC-V11-TEST-001
- AC-V11-TEST-007
- Deleted orders are removed from L3 state.
- Unit tests cover add, change, delete, duplicate add, delete missing order, change missing order, price move, zero-size change, and derived L2 level correctness.

---

## TASK-109 — Add ConsolidatedL2Book

### Objective

Add optional cross-venue L2 aggregation while preserving per-venue books.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ConsolidatedL2Book.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/ConsolidatedL2BookTest.java
```

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/InternalMarketView.java
```

### Acceptance Criteria

- AC-V11-MD-007
- AC-V11-TEST-001
- AC-V11-TEST-007
- Delete from one venue only removes that venue's contribution.
- Existing per-venue L2 book behavior remains unchanged.
- Unit tests cover same price across venues, one-venue delete, zero-size update, best bid/ask recalculation, empty consolidated book, and stale venue contribution removal.

---

## TASK-110 — Gateway Runtime Plugin Composition

### Objective

Wire selected FIX protocol plugin, venue plugin, market-data normalizer, execution normalizer, and order-entry adapter into gateway startup.

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/GatewayMain.java
```

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/VenueRuntime.java
```

### Acceptance Criteria

- The gateway builds a `VenueRuntime` for the configured venue.
- Artio session config uses the selected FIX protocol plugin dictionary.
- Market-data normalizer uses the configured `MarketDataModel`.
- AC-V11-TEST-002
- AC-V11-TEST-007
- Integration tests cover L2 venue runtime, L3 venue runtime, unsupported plugin failure, missing market-data normalizer failure, and Coinbase FIX L3 runtime composition.

---

## TASK-111 — Extend CoinbaseExchangeSimulator for FIX L3

### Objective

Extend the Coinbase simulator so V11 can test Coinbase FIX L3 connectivity without live Coinbase access.

### Files to Update

```text
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseExchangeSimulator.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/SimulatorSessionHandler.java
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseExchangeSimulatorTest.java
```

### Acceptance Criteria

- AC-V11-TEST-003
- AC-V11-TEST-004
- AC-V11-TEST-007
- Simulator can emit L3 snapshot, add, change, and delete order messages.
- Simulator can emit malformed L3 message, sequence gap, and disconnect/reconnect events.
- Positive tests cover valid L3 snapshot, valid add, valid size change, valid price move, valid delete, and mixed snapshot-plus-incremental streams.
- Negative tests cover unknown symbols, unsupported market-data subscription type, unsupported entry type, invalid venue/instrument setup, and unknown order IDs.
- Edge tests cover empty snapshot, zero-size change, delete missing order, duplicate add for the same order ID, repeated change at same price, max configured depth/order count, and reconnect after an empty book.
- Exception tests cover malformed FIX field generation, invalid numeric values, null/blank order IDs, invalid scenario arguments, and simulator startup/configuration validation errors.
- Failure tests cover simulated sequence gap, out-of-order update, disconnect/reconnect, forced session close, scheduler/publisher stop while active, and safe shutdown with pending L3 events.

---

## TASK-112 — Add Coinbase FIX L3 End-to-End Tests

### Objective

Add E2E coverage proving Coinbase FIX L3 messages travel through gateway normalization, cluster L3 state, derived L2 state, and market views.

### Files to Create

```text
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseFixL3E2ETest.java
```

### Files to Update

```text
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/test/TradingSystemTestHarness.java
```

### Acceptance Criteria

- AC-V11-TEST-005
- AC-V11-TEST-006
- AC-V11-TEST-009
- AC-V11-TEST-010
- E2E test covers Coinbase L3 snapshot to `VenueL3Book`.
- E2E test covers Coinbase L3 add/change/delete to derived per-venue L2.
- E2E test covers unknown symbol without corrupting book state.
- E2E test covers malformed message safe failure.
- E2E test covers sequence gap recovery/staleness behavior.
- E2E test covers simulator disconnect/reconnect venue-status behavior.
- Positive E2E tests cover snapshot, add, change, delete, derived L2 best bid/ask, and consolidated L2 update after L3 input.
- Negative E2E tests cover unknown symbol, unsupported entry type, unknown order delete, and unexpected venue/instrument IDs.
- Edge E2E tests cover empty snapshot, zero-size update, duplicate order ID, repeated update at the same price, and snapshot followed by incrementals.
- Exception E2E tests cover malformed FIX price/size/order ID fields and prove the gateway/cluster remains alive.
- Failure E2E tests cover sequence gap, out-of-order L3 update, simulator disconnect, reconnect resubscription/recovery behavior, and book-state non-corruption after failure.
- E2E validation must run through the Coinbase Simulator and must not require live Coinbase connectivity.

---

## TASK-113 — Rename and Clarify Venue L2 Book Boundary

### Objective

Clarify that the current price-level book is a venue-level L2 book, not an L3 book, and keep L3 order-level state separate.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/L2OrderBook.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/InternalMarketView.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/L2OrderBookTest.java
```

### Acceptance Criteria

- AC-V11-MD-005
- AC-V11-MD-006
- AC-V11-MD-008
- AC-V11-TEST-001
- AC-V11-TEST-007
- The price-level book is documented or renamed as `VenueL2Book`.
- `VenueL3Book` remains the only L3/order-level book.
- L2 venue feeds update the venue L2 book directly.
- L3 venue feeds update `VenueL3Book`, derive venue L2, then update consolidated L2.
- Tests prove L2-only venues do not allocate or require L3 state.
- Positive tests cover direct L2 add/change/delete and L3-derived L2 add/change/delete.
- Negative tests cover non-book entry types, wrong venue/instrument events, and invalid level access.
- Edge tests cover empty book, full depth, level insertion at top/middle/bottom, levels beyond max depth, zero-size update, repeated same-price update, and delete missing level.
- Exception tests cover invalid constructor inputs if the implementation adds validation, closed/invalid memory arena behavior where applicable, and malformed decoded event assumptions through public APIs.
- Failure tests cover stale book detection, derived-L2 failure not mutating unrelated levels, and preservation of consolidated contributions after one venue update fails or is ignored.
- Tests explicitly prove the price-level book contains no order-ID keyed state.

---

## TASK-114 — Add OwnOrderOverlay and ExternalLiquidityView

### Objective

Add a strategy-facing external-liquidity view that subtracts NitroJEx's own visible working orders from gross venue liquidity without mixing own order state into market books.

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlay.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityView.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlayTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityViewTest.java
```

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/InternalMarketView.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderManagerImpl.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderState.java
```

### Acceptance Criteria

- AC-V11-MD-008
- AC-V11-ARB-002
- AC-V11-ARB-004
- AC-V11-ARB-005
- AC-V11-TEST-001
- AC-V11-TEST-007
- External liquidity returns gross L2 when no own working orders exist.
- L2-only external size subtracts own working size by venue, instrument, side, and price using `max(0, gross - own)`.
- L3 external size subtracts exact own visible orders when venue order IDs match.
- Missing or unreliable L3 IDs fall back to conservative L2 approximation.
- Terminal orders are removed from the own-order overlay.
- Positive tests cover no-own-order gross passthrough, one own order subtraction, multiple own orders at the same venue/side/price, multiple venues at the same price, partial fill remaining quantity, cancel removal, replace price/size update, and exact L3 own-order match.
- Negative tests cover unknown venue order ID, mismatched venue ID, mismatched instrument ID, mismatched side, mismatched price, non-working own order, and external-liquidity requests for unknown books.
- Edge tests cover empty book, gross size smaller than own size returning zero, zero remaining quantity, duplicate own order update, same order ID reused after terminal state, multiple own orders across both sides, and missing L3 ID fallback to L2 approximation.
- Exception tests cover null/blank venue order IDs, invalid side/status values, invalid price/size values if validation is added, and attempts to mutate overlay with incomplete order identity.
- Failure tests cover duplicate execution reports, out-of-order terminal then working updates, rejected order cleanup, cancel/replace race ordering as represented by execution reports, and overlay state remaining consistent after ignored invalid updates.
- Tests must verify market books remain gross/public and own-order state is stored only in the overlay/order manager path.

---

## TASK-115 — Add Arbitrage Self-Cross and STP Controls

### Objective

Upgrade `ArbStrategy` to avoid trading with NitroJEx's own visible orders and to use venue STP where supported.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategy.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContext.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContextImpl.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/OrderEntryPolicy.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseOrderEntryPolicy.java
```

### Files to Update or Create Tests

```text
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategyTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategyE2ETest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseOrderEntryPolicyTest.java
```

### Acceptance Criteria

- AC-V11-ARB-001
- AC-V11-ARB-002
- AC-V11-ARB-003
- AC-V11-ARB-006
- AC-V11-ARB-007
- ArbStrategy reads external executable liquidity instead of raw gross venue L2 for opportunity detection and sizing.
- A BUY leg is blocked, reduced, or rerouted when own SELL liquidity on the same venue/instrument is priced at or below the BUY price.
- A SELL leg is blocked, reduced, or rerouted when own BUY liquidity on the same venue/instrument is priced at or above the SELL price.
- Coinbase order-entry policy exposes venue-supported self-trade-prevention enrichment if available.
- Positive tests cover valid cross-venue arbitrage with no own liquidity, valid arbitrage after partial own-liquidity subtraction, correct two-leg IOC command encoding, risk-approved submission, and existing leg timeout/imbalance hedge behavior.
- Negative tests cover false arbitrage caused entirely by own liquidity, same-venue self-cross BUY, same-venue self-cross SELL, risk rejection, unsupported STP policy, and disabled/paused strategy behavior.
- Edge tests cover partial own level still leaving executable external size, full own level reducing size to zero, one venue stale or missing quote, equal bid/ask no edge, minimum-profit boundary exactly at threshold, max position cap, and cooldown boundary.
- Exception tests cover missing `ExternalLiquidityView`/context dependency, invalid venue ID array indexing, invalid strategy config values if validation exists, and malformed command encoding dependencies.
- Failure tests cover Artio/cluster offer failure if surfaced by test harness, leg timeout cancel, one-leg fill imbalance hedge, hedge risk rejection activating kill switch, duplicate fills, out-of-order final execution, and cooldown after failed attempt.
- Gateway policy tests cover STP enrichment positive case, no-op default policy, invalid policy value, and preservation of existing Coinbase native replace behavior.

---

## TASK-116 — Add L3 Own-Order Reconciliation and Queue-Position Hooks

### Objective

Reconcile own orders against L3 market data when venue order IDs are matchable and expose optional queue-position estimates for future strategy use.

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/VenueL3Book.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlay.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderManagerImpl.java
```

### Files to Create

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/QueuePositionEstimate.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/L3OwnOrderReconciliationTest.java
```

### Acceptance Criteria

- AC-V11-ARB-005
- AC-V11-ARB-007
- AC-V11-TEST-001
- AC-V11-TEST-007
- L3 venue order IDs can be marked as own when they match `OrderState.venueOrderId`.
- Exact own visible size is excluded from external L3-derived liquidity.
- Queue-position estimate is optional and may return unknown when feed data is insufficient.
- Positive tests cover own order appears in L3, exact own size exclusion, own order size change, own order price move, own order delete, partial fill reconciliation, and known queue-position estimate when order ordering is available.
- Negative tests cover venue ID mismatch, instrument ID mismatch, side mismatch, price mismatch, unknown venue order ID, stale venue order ID, and L3 order not belonging to NitroJEx.
- Edge tests cover order ID reuse after terminal state, duplicate L3 add, delete before execution report, execution report before L3 visibility, zero-size L3 change, multiple own orders at one level, and mixed own/external orders at one level.
- Exception tests cover null/blank venue order ID, invalid queue-position inputs, incomplete `OrderState` identity, and unsupported feed ordering returning unknown instead of throwing.
- Failure tests cover out-of-order L3 updates, missing delete, sequence gap recovery interaction, reconciliation after reconnect, and ensuring failed reconciliation cannot corrupt gross L3/L2 books.
- Tests must prove queue-position data is advisory only and order-state correctness still comes from `OrderManager` / execution reports.

---

## TASK-117 — Define Hot-Path Allocation Policy and Documentation

### Objective

Codify which NitroJEx paths must be steady-state zero-allocation, which paths are allowed to allocate, and how future code reviews validate the boundary.

### Blockers

```text
TASK-101 through TASK-116 should already be complete for the V11 baseline.
```

### Files to Update

```text
NitroJEx_Master_Spec_V11.0.md
nitrojex_implementation_plan_v2.0.0.md
README.md
```

### Files to Create

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/AllocationPolicy.java
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/AllocationPolicyTest.java
```

### Acceptance Criteria

- AC-V11-PERF-001
- AC-V11-PERF-010
- AC-V11-TEST-001
- AC-V11-TEST-007
- Hot paths are explicitly listed: FIX L2/L3 parsing, execution-report parsing, SBE encode/decode, gateway disruptor handoff, Aeron publication handoff, L2/L3 book mutation, consolidated L2 mutation, own-order overlay update/query, external-liquidity query, order state transition, risk decision, strategy tick, and order command encoding.
- Cold/control paths are explicitly listed: config parsing, startup validation, plugin construction, admin CLI, simulator, replay tooling, REST polling, diagnostics, and tests.
- Code-level policy exposes an enum or equivalent stable constants for `HOT_PATH` and `COLD_PATH`.
- README states that NitroJEx targets zero-allocation steady-state hot paths, not literal zero allocation across the entire repository.
- Positive tests cover policy constants and documented path classification.
- Negative tests cover unknown/blank path names if lookup helpers are provided.
- Edge tests cover aliases or mixed-case path names if lookup helpers normalize input.
- Exception tests cover null input if public lookup APIs are provided.
- Failure tests cover documentation drift by asserting required hot/cold categories are represented in the policy class.

---

## TASK-118 — Add JMH Allocation Benchmark Module

### Objective

Add benchmark infrastructure that can prove or disprove steady-state hot-path allocation claims.

### Blockers

```text
TASK-117
```

### Files to Update

```text
settings.gradle.kts
build.gradle.kts
README.md
```

### Files to Create

```text
platform-benchmarks/build.gradle.kts
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/FixL2NormalizerBenchmark.java
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/FixL3NormalizerBenchmark.java
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/BookMutationBenchmark.java
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/OwnLiquidityBenchmark.java
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/StrategyTickBenchmark.java
platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/benchmark/OrderEncodingBenchmark.java
```

### Acceptance Criteria

- AC-V11-PERF-007
- AC-V11-PERF-008
- AC-V11-TEST-001
- AC-V11-TEST-007
- A Gradle benchmark task runs JMH with GC allocation profiling enabled or documented instructions for `-prof gc`.
- Benchmarks include warmup and measurement settings appropriate for low-latency hot-path code.
- Benchmark fixtures preallocate reusable state before measurement.
- Benchmark output reports allocation rate for each hot-path benchmark.
- Positive benchmarks cover valid FIX L2 parse, valid FIX L3 parse, L3 add/change/delete, consolidated L2 update, own-liquidity lookup, risk/strategy tick, and order command encoding.
- Negative benchmarks cover ignored/unsupported messages without measuring exception construction as normal behavior.
- Edge benchmarks cover empty book, full book/depth boundary, repeated same-price update, and zero-size delete/change.
- Exception-path benchmarks are excluded from zero-allocation targets and documented as cold/failure paths.
- Failure handling benchmarks cover disruptor/book rejection-style status paths where applicable without throwing in the measurement loop.
- README documents how to run benchmarks and how to interpret non-zero `B/op`.

---

## TASK-119 — Add Byte-Based Symbol Registry Lookup

### Objective

Allow FIX normalizers to resolve instruments directly from byte ranges so market-data parsing can avoid normal-path `String` allocation.

### Blockers

```text
TASK-117
TASK-118
```

### Files to Update

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/registry/IdRegistry.java
platform-common/src/main/java/ig/rueishi/nitroj/exchange/registry/IdRegistryImpl.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL2MarketDataNormalizer.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL3MarketDataNormalizer.java
```

### Files to Update or Create Tests

```text
platform-common/src/test/java/ig/rueishi/nitroj/exchange/registry/IdRegistryImplTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL2MarketDataNormalizerTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL3MarketDataNormalizerTest.java
```

### Acceptance Criteria

- AC-V11-PERF-002
- AC-V11-PERF-003
- AC-V11-TEST-001
- AC-V11-TEST-007
- `IdRegistry` exposes a byte/direct-buffer symbol lookup path that returns instrument ID without requiring a new `String`.
- Existing `CharSequence` lookup remains available for config, tests, and compatibility.
- FIX L2/L3 normalizers use the byte-based lookup where practical.
- Positive tests cover known symbols from byte arrays/direct buffers and existing `CharSequence` lookup.
- Negative tests cover unknown symbols, wrong venue/instrument assumptions, unsupported encodings, and invalid byte ranges.
- Edge tests cover empty symbol, max configured symbol length, symbols sharing prefixes, offset/non-zero buffer slices, and repeated lookup of the same symbol.
- Exception tests cover null buffers and invalid offset/length values.
- Failure tests cover malformed FIX symbol fields being dropped with counters/logging behavior unchanged and no book mutation.
- Tests must prove existing config and registry behavior remains backward compatible.

---

## TASK-120 — Replace Hot-Path Venue Order ID Strings with Bounded Identity

### Objective

Introduce a bounded venue-order-ID representation for L3 books and own-order reconciliation that avoids allocating Java `String` values per event while remaining collision-correct.

### Blockers

```text
TASK-117
TASK-118
TASK-119
```

### Files to Create

```text
platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/VenueOrderId.java
platform-common/src/test/java/ig/rueishi/nitroj/exchange/common/VenueOrderIdTest.java
```

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/VenueL3Book.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlay.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityView.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseL3MarketDataNormalizer.java
```

### Files to Update Tests

```text
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/VenueL3BookTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlayTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityViewTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseL3MarketDataNormalizerTest.java
```

### Acceptance Criteria

- AC-V11-PERF-004
- AC-V11-ARB-005
- AC-V11-TEST-001
- AC-V11-TEST-007
- Venue order identity includes venue ID, instrument ID, hash/fingerprint, byte length, and fixed byte storage or equivalent bounded representation.
- Hash collisions are handled by byte comparison, not by assuming hash uniqueness.
- Existing public test/helper APIs may accept strings, but production hot-path storage must use bounded identity internally.
- Positive tests cover exact ID match, same ID on different venues, same ID on different instruments, and exact own-order reconciliation.
- Negative tests cover different IDs with same prefix, venue mismatch, instrument mismatch, side/price mismatch, unknown ID, and stale ID after terminal order.
- Edge tests cover maximum supported ID length, empty/blank ID rejection, duplicate add, ID reuse after delete/terminal state, and deliberate hash-collision fixture if supported.
- Exception tests cover null ID input, overlong ID input, invalid byte ranges, and incomplete identity construction.
- Failure tests cover malformed L3 ID field, out-of-order delete/change with missing ID, and failed reconciliation not corrupting gross L3/L2 books.
- Tests must prove exact reconciliation remains correct after replacing `String`-keyed maps.

---

## TASK-121 — Replace Hot-Path Collections with Bounded Preallocated Structures

### Objective

Remove general-purpose Java collections from identified steady-state book, own-order, and strategy state paths where practical, replacing them with bounded arrays, primitive maps, or preallocated pools.

### Blockers

```text
TASK-117
TASK-118
TASK-119
TASK-120
```

### Files to Update

```text
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/VenueL3Book.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ConsolidatedL2Book.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlay.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/ExternalLiquidityView.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/order/OrderManagerImpl.java
platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyEngine.java
```

### Files to Update Tests

```text
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/VenueL3BookTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/ConsolidatedL2BookTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/OwnOrderOverlayTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/order/OrderManagerTest.java
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/StrategyEngineTest.java
```

### Acceptance Criteria

- AC-V11-PERF-002
- AC-V11-PERF-007
- AC-V11-PERF-008
- AC-V11-TEST-001
- AC-V11-TEST-007
- Hot-path state has explicit capacity limits and deterministic failure behavior when capacity is exceeded.
- Normal market-data/order/strategy processing does not allocate collection nodes or per-event records.
- General-purpose collections remain allowed in tests, config, startup composition, and tooling.
- Positive tests cover normal add/change/delete, order state transitions, strategy registration/dispatch, and own-liquidity queries with the new bounded structures.
- Negative tests cover capacity exceeded, duplicate keys, missing keys, unsupported side/status values, and invalid venue/instrument indexes.
- Edge tests cover empty structures, full capacity, remove then reuse slot, repeated update of same key, max venue/instrument dimensions, and boundary prices/sizes.
- Exception tests cover invalid constructor capacities, null dependencies, and invalid array bounds if exposed by public APIs.
- Failure tests cover partial update rollback or safe ignore behavior when capacity is exceeded and prove unrelated state remains unchanged.
- JMH benchmarks are updated or added to reflect the bounded structures.

---

## TASK-122 — Refactor FIX L2/L3 Normalizers for DirectBuffer Parsing

### Objective

Move shared FIX L2/L3 normalization toward direct byte parsing for symbol, price, size, enum, sequence, and timestamp fields.

### Blockers

```text
TASK-117
TASK-118
TASK-119
```

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL2MarketDataNormalizer.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL3MarketDataNormalizer.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/L2MarketDataContext.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/L3MarketDataContext.java
```

### Files to Create

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/marketdata/FixFieldParser.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/marketdata/FixFieldParserTest.java
```

### Files to Update Tests

```text
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL2MarketDataNormalizerTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/marketdata/AbstractFixL3MarketDataNormalizerTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/MarketDataHandlerTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseL3MarketDataNormalizerTest.java
```

### Acceptance Criteria

- AC-V11-PERF-003
- AC-V11-PERF-006
- AC-V11-TEST-001
- AC-V11-TEST-007
- Price and size parse directly from byte ranges into scaled long values.
- Side, update action, and message type parse from raw bytes/chars.
- Sequence numbers parse from bytes without normal-path string conversion.
- Timestamp parsing avoids allocation where practical or is explicitly documented if retained as transitional allocation.
- Positive tests cover valid L2 snapshot, valid L2 incremental, valid L3 snapshot, valid L3 incremental, decimal precision, and timestamp parsing.
- Negative tests cover unknown symbol, unsupported entry type, unsupported message type, missing required tag, unsupported update action, and invalid side.
- Edge tests cover zero price/size, max decimal precision, whole-number prices, short messages, duplicate tags, repeated groups, empty snapshot, and non-zero buffer offset.
- Exception tests cover malformed numeric values, overlong numeric fields, invalid timestamp fields, null buffers, and invalid offset/length.
- Failure tests cover malformed messages being dropped through counters/logging without throwing in expected hot-path handling and without mutating downstream state.
- JMH parser benchmarks are updated to use the direct parser.

---

## TASK-123 — Isolate Coinbase REST JSON from Hot Trading Paths

### Objective

Make Coinbase REST polling explicitly cold/side-path and ensure JSON objects cannot leak into deterministic book, risk, order, or strategy paths.

### Blockers

```text
TASK-117
```

### Files to Update

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseRestPoller.java
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/VenueRuntime.java
NitroJEx_Master_Spec_V11.0.md
README.md
```

### Files to Create or Update Tests

```text
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseRestPollerTest.java
platform-gateway/src/test/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase/CoinbaseRestPollerIntegrationTest.java
```

### Acceptance Criteria

- AC-V11-PERF-005
- AC-V11-PERF-006
- AC-V11-TEST-001
- AC-V11-TEST-007
- REST poller parses JSON only inside Coinbase REST-owned code.
- REST-derived data crossing into gateway/cluster code uses compact internal DTOs, SBE/admin events, or pre-existing primitive state; no `org.json` types appear in hot-path APIs.
- REST polling runs independently of normal FIX market-data/order handling.
- Positive tests cover account parse, balance update, timeout-safe poll cycle, and conversion to internal state/event.
- Negative tests cover missing fields, unknown currency, non-200 response, disabled REST config, and invalid venue config.
- Edge tests cover empty account list, zero balance, very large balance, repeated identical poll result, slow poll interval, and shutdown during idle.
- Exception tests cover malformed JSON, network exception, invalid numeric field, null response body, and interrupted polling.
- Failure tests cover REST outage while FIX path remains usable, repeated REST failures with counter/log behavior, and safe shutdown during active request.
- Static or package-level checks prove `org.json` usage remains confined to REST-owned gateway code.

---

## TASK-124 — Add Deterministic Replay and Allocation Gate Documentation

### Objective

Add replay-style tests and documentation proving deterministic-core behavior and defining how allocation benchmark results gate production claims.

### Blockers

```text
TASK-117
TASK-118
```

### Files to Update

```text
README.md
NitroJEx_Master_Spec_V11.0.md
nitrojex_implementation_plan_v2.0.0.md
```

### Files to Create

```text
platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/DeterministicReplayTest.java
platform-benchmarks/README.md
```

### Acceptance Criteria

- AC-V11-PERF-008
- AC-V11-PERF-009
- AC-V11-PERF-010
- AC-V11-TEST-001
- AC-V11-TEST-007
- Deterministic replay test applies the same ordered internal event stream twice from the same initial state and proves identical book, order, risk, strategy, and outbound command results.
- Replay input includes market-data events, market-by-order events, execution events, order commands, risk decisions where applicable, and strategy ticks.
- Benchmark README documents required commands, expected profiler output, `0 B/op` target, and how to record remediation for non-zero allocation.
- Project README clearly distinguishes current low-latency design from proven zero-allocation production readiness.
- Positive tests cover identical replay producing identical output.
- Negative tests cover changed event order producing intentionally different output or a documented deterministic rejection.
- Edge tests cover empty replay, single-event replay, duplicate event replay, reset/snapshot replay, and max configured venue/instrument IDs.
- Exception tests cover malformed replay fixtures, missing snapshot state, invalid event template IDs, and unsupported message versions.
- Failure tests cover replay with rejected/risk-failed commands, stale market data, dropped malformed event, and recovery after sequence-gap marker if represented internally.
- Test output or assertions must not rely on wall-clock timestamps unless timestamps are part of the input stream.

---

## TASK-125 — Add Coinbase Simulator Real FIX Connectivity for L2 and L3

### Objective

Extend the Coinbase simulator from a deterministic in-process test double into an automated local FIX acceptor test fixture that can validate real FIX session connectivity for Coinbase-style L2 and L3 market data without live Coinbase access.

### Blockers

```text
TASK-111
TASK-112
```

### Files to Update

```text
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseExchangeSimulator.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/SimulatorConfig.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/SimulatorSessionHandler.java
platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/MarketDataPublisher.java
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseExchangeSimulatorTest.java
```

### Files to Create

```text
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/simulator/CoinbaseFixConnectivitySimulatorTest.java
```

### Acceptance Criteria

- AC-V11-TEST-011
- AC-V11-TEST-012
- AC-V11-TEST-013
- AC-V11-TEST-014
- AC-V11-TEST-001
- AC-V11-TEST-007
- Simulator starts a local FIX acceptor endpoint or equivalent Artio-compatible FIX session fixture on an ephemeral/configured port.
- Simulator accepts Coinbase gateway logon using the configured FIXT.1.1/FIX 5.0SP2 dictionary/session behavior where supported by the local fixture.
- Simulator supports explicit L2 and L3 market-data subscription modes for automated tests.
- L2 mode emits Coinbase-style price-level snapshot and incremental updates.
- L3 mode emits Coinbase-style order-level snapshot, add, change, delete, sequence gap, malformed message, and disconnect/reconnect flows.
- Simulator receives gateway order-entry messages and emits execution reports for ack, fill, partial fill, cancel, reject, and disconnect scenarios.
- Positive tests cover logon, heartbeat/session alive, L2 subscription, L3 subscription, order ack/fill, cancel ack, and clean logout/shutdown.
- Negative tests cover bad credentials, unsupported market-data model, unknown symbol, invalid message type, invalid CompID, and duplicate session attempt.
- Edge tests cover reconnect with existing book, empty L2 snapshot, empty L3 snapshot, repeated subscription, high sequence number, and simultaneous market-data plus order-entry traffic.
- Exception tests cover malformed inbound FIX, invalid numeric fields, missing required tags, port bind failure, simulator startup failure, and shutdown during active session.
- Failure tests cover sequence gap, disconnect/reconnect, simulator-side reject, delayed fill timeout, forced session close, and safe cleanup with pending market-data/order events.
- Tests must prove the simulator path uses real FIX wire/session behavior and not only direct Java method calls.

---

## TASK-126 — Add Coinbase L2 and L3 Live-Wire End-to-End Tests

### Objective

Add automated Coinbase Simulator live-wire E2E tests proving the full local trading wire for both Coinbase L2 and Coinbase L3: simulator FIX session, gateway runtime, gateway disruptor, Aeron Cluster ingress, cluster market state, strategy observation, cluster egress, gateway order command handling, FIX order-entry back to simulator, and execution feedback.

### Blockers

```text
TASK-125
```

### Files to Update

```text
platform-tooling/src/test/java/ig/rueishi/nitroj/exchange/test/TradingSystemTestHarness.java
README.md
NitroJEx_Master_Spec_V11.0.md
nitrojex_implementation_plan_v2.0.0.md
```

### Files to Create

```text
platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/CoinbaseFixL2LiveWireE2ETest.java
platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/CoinbaseFixL3LiveWireE2ETest.java
```

### Acceptance Criteria

- AC-V11-TEST-011
- AC-V11-TEST-012
- AC-V11-TEST-013
- AC-V11-TEST-014
- AC-V11-TEST-001
- AC-V11-TEST-007
- L2 live-wire E2E starts the Coinbase simulator FIX endpoint, gateway runtime, and cluster service/test fixture automatically.
- L3 live-wire E2E starts the Coinbase simulator FIX endpoint, gateway runtime, and cluster service/test fixture automatically.
- L2 live-wire E2E proves Coinbase price-level snapshot/incrementals update the gateway normalizer, SBE market-data event, cluster `InternalMarketView`, venue L2 book, optional consolidated L2, and strategy observation path.
- L3 live-wire E2E proves Coinbase order-level snapshot/add/change/delete updates the gateway normalizer, SBE market-by-order event, cluster `VenueL3Book`, derived venue L2, optional consolidated L2, and strategy observation path.
- L2 and L3 live-wire E2E prove at least one strategy-generated or test-injected order command travels from cluster egress to gateway `OrderCommandHandler`, through `ExecutionRouter`, through Coinbase order-entry policy/STP enrichment, and into the simulator as a FIX order.
- L2 and L3 live-wire E2E prove simulator execution reports travel back through gateway `ExecutionHandler`, gateway disruptor, Aeron ingress, cluster `MessageRouter`, `OrderManager`, `PortfolioEngine`, `RiskEngine`, and strategy fill callback.
- Positive tests cover logon, market-data subscription, book update, outbound new order, simulator ack/fill, execution feedback, and clean shutdown.
- Negative tests cover unknown symbol, unsupported subscription type, rejected order, cancel unknown order, and unsupported FIX message.
- Edge tests cover empty book, repeated subscription, reconnect after book state exists, partial fill followed by full fill, zero-size market-data update, and L3 order ID reuse.
- Exception tests cover malformed L2 FIX message, malformed L3 FIX message, invalid numeric field, missing required tag, and gateway/simulator startup validation failures.
- Failure tests cover simulator disconnect, reconnect/resubscribe, sequence gap/stale book behavior, Aeron/disruptor back-pressure where practical in the fixture, delayed fill timeout, and safe process/test-fixture shutdown.
- E2E tests must be automated Gradle tests and must not require live Coinbase network connectivity.
- The pre-QA/UAT verification command must include both live-wire L2 and live-wire L3 E2E test classes.

---

# Section 3 — Verification

Initial V11 verification target:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:compileJava :platform-cluster:test
```

Full V11 verification target:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
```

Pre-QA/UAT automated gate:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-tooling:test --tests ig.rueishi.nitroj.exchange.simulator.CoinbaseFixL3E2ETest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest --tests ig.rueishi.nitroj.exchange.e2e.CoinbaseFixL2LiveWireE2ETest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest --tests ig.rueishi.nitroj.exchange.e2e.CoinbaseFixL3LiveWireE2ETest
```

Real Coinbase QA/UAT may not begin until the automated gate passes or any environment-only failures are documented with owner approval.

Coinbase FIX L3 E2E verification target:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-tooling:test --tests ig.rueishi.nitroj.exchange.simulator.CoinbaseFixL3E2ETest
```

Coinbase Simulator live-wire L2/L3 E2E verification targets:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest --tests ig.rueishi.nitroj.exchange.e2e.CoinbaseFixL2LiveWireE2ETest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest --tests ig.rueishi.nitroj.exchange.e2e.CoinbaseFixL3LiveWireE2ETest
```
