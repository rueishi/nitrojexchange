# NitroJEx

NitroJEx is a Java 21 low-latency trading platform prototype for venue connectivity, market-data normalization, order/risk state, and strategy execution.

The active development line is **V11.0**. V10.0 is preserved as the frozen baseline, while V11.0 adds multi-version FIX support, venue plugins, Coinbase FIX L3 support, L3-to-L2 derivation, consolidated L2 views, own-liquidity-aware arbitrage controls, and Coinbase simulator coverage.

This repository is not a financial recommendation system. It is infrastructure code. Real venue connectivity must go through QA/UAT, credential review, exchange certification/onboarding, and production risk controls before live use.

## Current Status

```text
Active spec:        NitroJEx_Master_Spec_V11.0.md
Active plan:        nitrojex_implementation_plan_v2.0.0.md
Migration doc:      NitroJEx_V10_to_V11_Migration.md
Frozen V10 spec:    NitroJEx_Master_Spec_V10.0.md
Frozen V10 plan:    nitrojex_implementation_plan_v1.4.0.md
```

The codebase currently builds and passes automated checks locally with:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
```

The project should still be treated as **pre-QA / pre-UAT** for real Coinbase connectivity.

NitroJEx targets **zero-allocation steady-state hot paths**, not literal zero allocation across the whole repository. Startup/config parsing, admin tooling, simulator code, diagnostics, REST polling, and tests may allocate. The trading hot path is the part that must be benchmarked toward `0 B/op`.

## TODO: V12 Low-Latency Hardening

V12 is planned as the low-latency, deterministic, zero-allocation hardening line. V11 establishes the architecture and Coinbase FIX L3 path, but it should not be marketed as fully proven zero-GC production trading infrastructure yet.

V12 goals:

- Prove steady-state hot-path allocation behavior with JMH and GC profilers, including published `B/op`, `alloc.rate`, and percentile latency results.
- Replace remaining hot-path `HashMap`, `LinkedHashMap`, `List`, `String`, records, and avoidable object creation with bounded primitive/off-heap/flyweight structures where justified.
- Remove or isolate FIX parsing and normalization paths that allocate `String`/`CharSequence` values for symbols, order IDs, venue order IDs, and market-data fields.
- Make venue L3 books, consolidated L2 books, own-order overlays, and strategy liquidity views allocation-free after warmup under configured capacity limits.
- Keep Coinbase REST JSON parsing explicitly outside the trading hot path, or replace it with a cold-path parser/boundary that cannot leak allocation-heavy objects into deterministic state.
- Add benchmark gates or CI reports that prevent claiming `0 B/op` until allocation evidence exists.
- Complete real Coinbase certification/QA/UAT before production connectivity claims.
- Harden security and operations: secrets handling, credential rotation, audit trails, monitoring, alerting, failover, disaster recovery, and deployment evidence.
- Strengthen financial correctness: reconciliation, kill switch, risk limits, rejected-order handling, disconnect recovery, self-trade prevention validation, and audit-grade evidence before live trading.

Until V12 evidence exists, the correct claim is:

```text
NitroJEx is designed toward low-latency deterministic hot paths and has a roadmap to verified zero allocation / zero GC behavior. V11 is not yet benchmark-proven zero-GC.
```

## Major Capabilities

- Multi-module Gradle build targeting Java 21.
- SBE schema generation for internal messages.
- Artio FIX codec generation.
- FIX protocol plugins for:
  - FIX 4.2
  - FIX 4.4
  - FIXT.1.1 / FIX 5.0SP2
- Venue plugin architecture separating venue behavior from FIX mechanics.
- Coinbase venue plugin with Coinbase-specific logon, order-entry policy, REST polling, and L2/L3 normalizers.
- Configurable venue market-data model: `L2` or `L3`.
- Shared abstract FIX L2 and L3 market-data normalizers with venue enrichment hooks.
- Venue L3 book that derives per-venue L2.
- Consolidated L2 book across venues.
- Own order overlay and external liquidity view so strategies can reason about gross liquidity versus executable external liquidity.
- Market-making and cross-venue arbitrage strategy scaffolding/tests.
- Coinbase exchange simulator and deterministic Coinbase FIX L3 E2E-style tests.
- Planned Coinbase Simulator live-wire E2E tests for both Coinbase FIX L2 and L3 before real Coinbase QA/UAT.
- Startup scripts for one gateway process per venue.

## Repository Layout

```text
.
├── platform-common      Shared config, IDs, math, SBE schema, generated SBE codecs
├── platform-gateway     Artio gateway, FIX plugins, venue plugins, handlers, normalizers
├── platform-cluster     Cluster service, books, risk, order state, strategies
├── platform-tooling     Simulator, admin/replay/warmup tooling, test harness
├── config               Local/dev TOML config
├── scripts              Runtime startup wrappers
├── .prompt              Codex task prompts and compile notes
├── NitroJEx_Master_Spec_V10.0.md
├── NitroJEx_Master_Spec_V11.0.md
├── NitroJEx_V10_to_V11_Migration.md
├── nitrojex_implementation_plan_v1.4.0.md
└── nitrojex_implementation_plan_v2.0.0.md
```

## Modules

### `platform-common`

Owns shared types and generated wire codecs:

- `ConfigManager`
- `VenueConfig`, `GatewayConfig`, `ClusterNodeConfig`
- `FixPluginId`, `MarketDataModel`, `VenueCapabilities`
- fixed-point math helpers
- ID registry
- SBE message schema at `platform-common/src/main/resources/messages.xml`
- generated SBE Java source under `platform-common/src/generated/java`

SBE generation:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-common:generateSBE
```

### `platform-gateway`

Owns venue connectivity and gateway ingress/egress:

- `GatewayMain`
- Artio session loop and handlers
- Aeron publisher
- gateway disruptor
- FIX protocol plugin registry
- venue plugin registry
- shared L2/L3 FIX normalizers
- Coinbase venue plugin

Important packages:

```text
ig.rueishi.nitroj.exchange.gateway.fix
ig.rueishi.nitroj.exchange.gateway.marketdata
ig.rueishi.nitroj.exchange.gateway.venue
ig.rueishi.nitroj.exchange.gateway.venue.coinbase
```

Generated FIX codecs live in separate packages/directories so FIX versions do not collide:

```text
platform-gateway/src/generated/fix42/java
platform-gateway/src/generated/fix44/java
platform-gateway/src/generated/fixt11-fix50sp2/java
```

FIX codec generation:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:generateFix42Codecs
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:generateFix44Codecs
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:generateFixt11Fix50Sp2Codecs
```

### `platform-cluster`

Owns deterministic trading state and strategy logic:

- `TradingClusteredService`
- `L2OrderBook`
- `VenueL3Book`
- `ConsolidatedL2Book`
- `OwnOrderOverlay`
- `ExternalLiquidityView`
- `OrderManager`
- `RiskEngine`
- `PortfolioEngine`
- `MarketMakingStrategy`
- `ArbStrategy`

Important design rule: market books and NitroJEx own order state are separate. Execution reports are authoritative for own order state. Market data is the public venue view. `OwnOrderOverlay` and `ExternalLiquidityView` bridge both views when strategies need executable external liquidity.

### `platform-tooling`

Owns local tooling:

- `CoinbaseExchangeSimulator`
- simulator market-data publishing
- scenario controls
- admin CLI/client
- FIX replay tool
- warmup harness implementation
- shared trading system test harness

## Requirements

- Linux or WSL is recommended.
- Java 21 JDK.
- Gradle Wrapper from this repo.

Install Java 21 on Ubuntu/WSL if needed:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

## Build

From the repo root:

```bash
cd ~/nitrojexchange
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build
```

Compile only:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileJava
```

Run all standard checks:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
```

Build runnable shadow jars:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew shadowJar
```

## Tests

Run all standard tests:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test
```

Run all checks:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
```

Run module tests:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-common:test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-cluster:test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-tooling:test
```

Run a specific test:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:test --tests ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseL3MarketDataNormalizerTest
```

Run explicit E2E source-set tests:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
```

Run hot-path allocation benchmarks:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
```

The benchmark task runs JMH with `-prof gc`. For the declared trading hot path, `0 B/op` after warmup is the target. Any non-zero allocation must be treated as evidence, not embarrassment: document the benchmark, owner, reason, and remediation task before claiming zero-allocation readiness.

The default `test` task excludes tests tagged:

```text
E2E
SlowTest
RequiresProductionEnvironment
```

The `ciTest` task excludes:

```text
E2E
RequiresProductionEnvironment
```

## Configuration

Runtime config is TOML-based.

Important files:

```text
config/venues.toml
config/instruments.toml
config/gateway-1.toml
config/cluster-node-0.toml
config/cluster-node-1.toml
config/cluster-node-2.toml
config/admin.toml
```

### Venue Registry

`config/venues.toml` defines immutable venue IDs and venue capabilities.

Example Coinbase entry:

```toml
[[venue]]
id        = 1
name      = "COINBASE"
fixHost   = "fix.exchange.coinbase.com"
fixPort   = 4198
sandbox   = false
fixPlugin = "FIXT11_FIX50SP2"
venuePlugin = "COINBASE"
marketDataModel = "L3"
orderEntryEnabled = true
marketDataEnabled = true
nativeReplaceSupported = false
```

Operational rule: once assigned, venue IDs must not be reused. Persisted state, logs, and replay data refer to the numeric IDs.

### Gateway Config

`config/gateway-1.toml` binds a gateway process to one venue ID:

```toml
[process]
venueId = 1
nodeRole = "gateway"
```

The gateway loads:

```text
gateway config
venues.toml
instruments.toml
```

Production secrets are not stored in config. The config stores a Vault path:

```toml
[credentials]
vaultPath = "secret/trading/coinbase/venue-1"
```

Before live connectivity, credential resolution must provide the API key, secret, and passphrase expected by the selected venue plugin.

## Running Locally

Build first:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew shadowJar
```

Start a cluster node:

```bash
scripts/cluster-node-start.sh config/cluster-node-0.toml
```

Start the dedicated Coinbase gateway:

```bash
scripts/gateway-coinbase-start.sh
```

The generic gateway launcher requires the venue name explicitly:

```bash
scripts/gateway-start.sh COINBASE config/gateway-1.toml config/venues.toml config/instruments.toml
```

NitroJEx is designed for one gateway instance per venue. Future venues should add their own wrapper script, for example:

```text
scripts/gateway-binance-start.sh
scripts/gateway-kraken-start.sh
```

Those wrappers should delegate to `scripts/gateway-start.sh` with the venue name and correct config files.

## Coinbase FIX L2/L3 Readiness

V11 includes a Coinbase FIX L3 implementation path and shared L2/L3 market-data support:

- Coinbase venue plugin.
- Coinbase logon customization.
- Coinbase order-entry policy.
- Coinbase L2 and L3 normalizers.
- Coinbase simulator.
- Coinbase FIX L3 simulator tests.
- Concrete Coinbase L3 FIX normalizer tests.

Current automated Coinbase simulator coverage is harness-level. It validates simulator-generated L3 events, gateway normalization, SBE events, L3 book updates, derived L2, and consolidated L2 behavior without live Coinbase access.

The required pre-QA/UAT gap is now explicit in the V11 plan: add automated Coinbase Simulator live-wire tests for both L2 and L3. Those tests must start a local Coinbase simulator FIX endpoint/session fixture and prove:

```text
Coinbase simulator FIX session
  -> gateway FIX session
  -> gateway disruptor
  -> Aeron Cluster ingress
  -> cluster market state/books/strategy observation
  -> cluster egress order command
  -> gateway OrderCommandHandler / ExecutionRouter
  -> Coinbase FIX order entry into simulator
  -> simulator ExecutionReport
  -> gateway ExecutionHandler
  -> cluster OrderManager / PortfolioEngine / RiskEngine / StrategyEngine
```

Required live-wire E2E classes:

```text
platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/CoinbaseFixL2LiveWireE2ETest.java
platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/CoinbaseFixL3LiveWireE2ETest.java
```

Before real Coinbase QA/UAT:

- Confirm current Coinbase FIX endpoint, TLS, FIX version, SenderCompID, TargetCompID, and dictionary requirements from Coinbase onboarding/docs.
- Confirm account/API key has FIX permissions enabled.
- Wire real credential resolution from Vault or the approved secret source.
- Run local unit/integration/simulator tests.
- Run automated Coinbase Simulator live-wire L2 and L3 E2E tests.
- Run Coinbase sandbox/UAT connectivity.
- Verify logon, heartbeat, resend/replay, sequence reset policy, market-data recovery, order entry, cancel, reject, disconnect, and reconnect flows.
- Only then consider production shadow/cutover.

## REST JSON Boundary

Coinbase REST polling is a cold/side path. `org.json` parsing is confined to Coinbase REST-owned gateway code and must not appear in cluster books, risk, order management, strategy APIs, or normal FIX market-data handling. REST-derived data must cross into the rest of the system as primitive internal state, DTOs, or SBE/admin events.

## Market Data Model

Each venue declares one market-data model:

```toml
marketDataModel = "L2"
```

or:

```toml
marketDataModel = "L3"
```

L2 venues publish price-level updates directly into per-venue L2 books.

L3 venues publish order-level updates into `VenueL3Book`. The system derives per-venue L2 from the active L3 state and can then update consolidated L2.

The current book model separates:

```text
VenueL3Book
  Order-level venue book.

L2OrderBook
  Per-venue price-level book.

ConsolidatedL2Book
  Cross-venue aggregated price-level book.

OwnOrderOverlay
  NitroJEx working-order visibility.

ExternalLiquidityView
  Gross market liquidity minus NitroJEx own visible liquidity where identifiable.
```

## Strategy Notes

### Market Making

`MarketMakingStrategy` uses venue market data, configured spreads, inventory skew, quote sizing, and staleness checks to produce quoting behavior.

For market making, L3 can be valuable because it can support queue-position awareness and exact own-order reconciliation when the venue exposes reliable order IDs. L2 is still useful for spread, top-of-book, and venue-level liquidity decisions.

### Arbitrage

`ArbStrategy` is treated as a multi-venue strategy. It scans configured venues, compares executable edge, and submits two venue legs when the opportunity exceeds the configured threshold.

Important V11 arbitrage controls:

- Use external executable liquidity, not raw gross L2 size.
- Avoid reacting to NitroJEx's own visible quote as if it were external liquidity.
- Use L2 conservative own-liquidity subtraction when only level data exists.
- Use L3 exact own-order matching when reliable venue order IDs exist.
- Perform self-cross checks before leg submission.
- Respect cooldowns and leg timeout controls.
- Use venue-native STP hooks where supported.

Coinbase STP is implemented as venue-specific order-entry enrichment in `CoinbaseOrderEntryPolicy`.

## FIX and Venue Plugin Design

V11 has two independent plugin axes.

FIX protocol plugins own protocol mechanics:

```text
BeginString
DefaultApplVerID
dictionary class
Artio session config
generated codec package
```

Venue plugins own venue behavior:

```text
authentication/logon customization
proprietary tags
order-entry policy
execution-report policy
market-data enrichment
venue capabilities
credential fallback
```

Shared abstractions live under:

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue
```

Coinbase-specific production classes live under:

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase
```

## Generated Code

Generated code is intentionally visible to the IDE and build:

```text
platform-common/src/generated/java
platform-gateway/src/generated/fix42/java
platform-gateway/src/generated/fix44/java
platform-gateway/src/generated/fixt11-fix50sp2/java
```

Regenerate SBE and FIX code with:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-common:generateSBE
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:generateFix42Codecs
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:generateFix44Codecs
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:generateFixt11Fix50Sp2Codecs
```

Normal compile/check tasks already depend on the required generation tasks.

## Development Rules

- Do not modify V10 spec/plan files.
- Use V11 spec and plan for active work.
- Use task IDs `TASK-101` and above for V11.
- Treat FIX parsing, SBE encode/decode, gateway handoff, book mutation, order state, risk, strategy tick, and order encoding as steady-state hot paths.
- Keep allocation-heavy work in cold/control paths: startup, config, admin, tooling, simulator, REST polling, diagnostics, and tests.
- Keep venue-specific behavior out of FIX protocol plugins.
- Keep FIX protocol mechanics out of venue plugins.
- Keep Coinbase classes under `gateway/venue/coinbase`.
- Keep shared venue abstractions under `gateway/venue`.
- Keep market books separate from own order state.
- Add tests for positive, negative, edge, exception, and failure paths where applicable.
- Real Coinbase QA/UAT is the final connectivity validation, not a substitute for automated tests.

## Common Commands

```bash
# Full build
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build

# Full check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check

# Compile gateway only
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:compileJava

# Test cluster only
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-cluster:test

# Test gateway only
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-gateway:test

# Build runnable jars
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew shadowJar
```

## Troubleshooting

### Gradle cannot find Java 21

Set `JAVA_HOME` explicitly:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew check
```

### Generated classes are missing

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean compileJava
```

### Gateway script says venue mismatch

The explicit venue passed to `gateway-start.sh` must match `process.venueId` in the gateway config and the corresponding name in `venues.toml`.

Example:

```bash
scripts/gateway-start.sh COINBASE config/gateway-1.toml config/venues.toml config/instruments.toml
```

### Coinbase live connection fails before logon

Check:

- gateway jar exists
- venue name matches config
- FIX host/port are current for your Coinbase onboarding
- TLS requirements are satisfied
- SenderCompID and TargetCompID are correct
- credential resolver provides API key, secret, and passphrase
- selected FIX protocol plugin matches the venue config

## Release Readiness

Before production:

- `./gradlew check` passes.
- Coinbase simulator tests pass.
- Coinbase sandbox/UAT tests pass.
- FIX session recovery and resend behavior are validated.
- Market-data sequence gap/reconnect behavior is validated.
- Order-entry reject/cancel/fill paths are validated.
- Risk limits are reviewed.
- Secrets are loaded from approved infrastructure.
- Metrics and logs are reviewed.
- Runbooks are written.
- Shadow mode is completed before cutover.
