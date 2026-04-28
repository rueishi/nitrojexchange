# NitroJEx Master Specification V11.0

## Status

Active Development - Supersedes V10.0

## Based On

`NitroJEx_Master_Spec_V10.0.md` (frozen production baseline)

## Implementation Plan

`nitrojex_implementation_plan_v2.0.0.md`

## Key Enhancements

- Multi-version FIX protocol plugin architecture for FIX 4.2, FIX 4.4, and FIXT.1.1 / FIX 5.0SP2.
- Venue plugin architecture that separates venue-specific behavior from FIX protocol mechanics.
- Configurable L2 or L3 market-data model per venue.
- Shared standard FIX L2 and L3 normalizer base classes with venue-specific `enrich(...)` hooks.
- L3 venue order book support sufficient to derive per-venue L2 books.
- Optional consolidated L2 market view across venues.
- External executable-liquidity view that can subtract NitroJEx's own visible working orders from venue/public liquidity.
- Modern cross-venue arbitrage controls: self-trade prevention, self-cross checks, queue-position awareness where possible, and L3 own-order reconciliation.
- Coinbase FIX L3 connectivity path with Coinbase Simulator end-to-end coverage.
- Explicit steady-state hot-path zero-allocation target with JMH/allocation verification.
- Clean V11 execution track with new task-card numbering starting at TASK-101.

---

# 1. Versioning and Baseline Rules

## 1.1 Frozen Baseline

The following files are immutable V10 baseline artifacts:

```text
NitroJEx_Master_Spec_V10.0.md
nitrojex_implementation_plan_v1.4.0.md
```

They must not be edited for V11 work. V10 remains the production/audit reference and historical truth for the delivered system.

## 1.2 V11 Scope

V11 is a major system evolution, not a patch. It reuses valid V10 architecture where appropriate and introduces new extension points for multi-version FIX, venue plugins, and configurable market-data depth.

## 1.3 Task Numbering

V11 implementation tasks must not reuse V10 task IDs. V11 task cards start at:

```text
TASK-101
```

---

# 2. Architecture Overview

V11 separates external venue connectivity into two independent plugin axes:

```text
FIX Protocol Plugin
  Owns FIX session version, Artio dictionary, generated codecs, BeginString,
  FIXT application version, and protocol-level session configuration.

Venue Plugin
  Owns venue-specific authentication, proprietary tags, order-entry policy,
  execution-report policy, market-data enrichment, and venue capabilities.
```

The FIX protocol plugin must not contain Coinbase or venue-specific business rules.

The venue plugin must not own Artio dictionary generation or FIX protocol version mechanics.

Market data books and own order state are deliberately separate:

```text
VenueL2Book / VenueL3Book / ConsolidatedL2Book
  Represent venue-published market liquidity.

OrderManager / OrderState
  Represent NitroJEx's own live orders, fills, cancels, rejects, and replaces.

OwnOrderOverlay / ExternalLiquidityView
  Bridges both worlds when strategies need executable liquidity excluding
  NitroJEx's own visible resting orders.
```

The market book is not the source of truth for NitroJEx's own orders. Execution reports remain authoritative for own order state.

V11 also separates runtime paths by allocation policy:

```text
Steady-state hot path
  FIX market-data receive, FIX execution-report receive, SBE encode/decode,
  gateway disruptor publish, cluster book update, risk check, order state
  transition, strategy decision, and order command encode.

Cold/control path
  Startup, config parsing, admin tools, simulator, tests, REST polling,
  recovery/bootstrap, diagnostics, and operator-facing logging.
```

The hot path must evolve toward deterministic, bounded, zero-allocation behavior. The cold/control path may allocate when it is not part of normal trading-event processing.

---

# 3. Supported FIX Protocol Plugins

V11 defines the following protocol plugins:

```text
ArtioFix42Plugin
ArtioFix44Plugin
ArtioFixt11Fix50Sp2Plugin
```

## 3.1 FIX Protocol Plugin Contract

```java
interface FixProtocolPlugin {
    FixPluginId id();

    String beginString();

    String defaultApplVerId(); // null for FIX 4.2 and FIX 4.4

    Class<? extends FixDictionary> dictionaryClass();

    SessionConfiguration.Builder configureSession(
        SessionConfiguration.Builder builder,
        FixConfig fix,
        VenueConfig venue
    );

    SessionCustomisationStrategy sessionCustomisationStrategy(
        VenuePlugin venuePlugin,
        CredentialsConfig credentials
    );
}
```

## 3.2 FIX 4.2

```text
Plugin: ArtioFix42Plugin
BeginString: FIX.4.2
DefaultApplVerID: none
Dictionary: FIX 4.2 XML
Generated package: ig.rueishi.nitroj.exchange.fix.fix42
```

FIX 4.2 remains available for venues that explicitly select `FIX_42`, but V11 must not generate codecs into the global `uk.co.real_logic.artio` package.

## 3.3 FIX 4.4

```text
Plugin: ArtioFix44Plugin
BeginString: FIX.4.4
DefaultApplVerID: none
Dictionary: FIX 4.4 XML
Generated package: ig.rueishi.nitroj.exchange.fix.fix44
```

## 3.4 FIXT.1.1 / FIX 5.0SP2

```text
Plugin: ArtioFixt11Fix50Sp2Plugin
BeginString: FIXT.1.1
DefaultApplVerID: FIX.5.0SP2
Transport dictionary: FIXT.1.1
Application dictionary: FIX 5.0SP2
Generated package: ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2
```

Artio code generation for FIXT must support split dictionaries, conceptually:

```text
FIXT11.xml;FIX50SP2.xml
```

---

# 4. Artio Code Generation

V10 generated one dictionary into the default Artio package. V11 requires one codegen task per protocol family:

```text
generateFix42Codecs
generateFix44Codecs
generateFixt11Fix50Sp2Codecs
```

Each task must set a unique `fix.codecs.parent_package` and output directory. All generated source directories are added to the gateway module source set.

Generated codec imports must be hidden behind protocol/venue adapters. Gateway core code must not directly import version-specific generated encoder classes.

---

# 5. Venue Plugin Architecture

Venue plugins are concrete implementations for real venues only.

Examples:

```text
CoinbaseVenuePlugin
BinanceVenuePlugin
KrakenVenuePlugin
CmeVenuePlugin
```

There is no `FutureVenuePlugin`.

There is no runtime `GenericFixVenuePlugin`. Shared generic behavior may exist only as abstract/helper classes.

## 5.1 Venue Plugin Contract

```java
interface VenuePlugin {
    String id();

    VenueCapabilities capabilities(VenueConfig venue);

    VenueLogonCustomizer logonCustomizer(CredentialsConfig credentials);

    VenueOrderEntryAdapter orderEntryAdapter(
        Session session,
        IdRegistry idRegistry,
        FixProtocolPlugin fixPlugin,
        GatewayCallbacks callbacks
    );

    ExecutionReportNormalizer executionReportNormalizer();

    MarketDataNormalizer marketDataNormalizer(
        VenueConfig venue,
        IdRegistry idRegistry,
        GatewayDisruptor disruptor
    );

    VenueOrderEntryAdapter orderEntryAdapter(...);

    BalanceQuerySink balanceQuerySink(...);

    CredentialsConfig resolveCredentials(
        CredentialsConfig credentials,
        Map<String, String> environment
    );
}
```

## 5.2 Coinbase Plugin Composition

Coinbase is implemented by composing shared standard components with Coinbase-specific policies:

```text
CoinbaseVenuePlugin
  -> CoinbaseLogonStrategy / VenueLogonCustomizer
  -> StandardOrderEntryAdapter + CoinbaseOrderEntryPolicy
  -> StandardExecutionReportNormalizer + CoinbaseExecutionReportPolicy
  -> AbstractFixL2MarketDataNormalizer with optional Coinbase enrich(...)
  -> AbstractFixL3MarketDataNormalizer with optional Coinbase enrich(...)
  -> CoinbaseRestPoller
  -> Coinbase credential fallback from Coinbase-owned environment variable names
```

Coinbase-specific production gateway classes must live under:

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/coinbase
```

Shared venue abstractions must live under:

```text
platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue
```

The default `VenuePluginRegistry` may register currently supported concrete venue plugins, including Coinbase. Shared config parsing must not hard-code a Coinbase-only venue plugin whitelist; unsupported venue plugins fail at the gateway registry/composition boundary.

---

# 6. Configuration Model

Venue registry entries define protocol and market-data choices:

```toml
[[venue]]
id = 1
name = "COINBASE"
fixHost = "fix.exchange.coinbase.com"
fixPort = 4198
sandbox = false

fixPlugin = "FIXT11_FIX50SP2"
venuePlugin = "COINBASE"
marketDataModel = "L3"

orderEntryEnabled = true
marketDataEnabled = true
nativeReplaceSupported = false
```

Required enums:

```java
enum FixPluginId {
    FIX_42,
    FIX_44,
    FIXT11_FIX50SP2
}

enum MarketDataModel {
    L2,
    L3
}
```

Required capabilities:

```java
record VenueCapabilities(
    boolean orderEntryEnabled,
    boolean marketDataEnabled,
    boolean nativeReplaceSupported
) {}
```

Gateway process config remains responsible for CompIDs, heartbeat, reconnect interval, Artio log directory, replay capacity, and credentials.

Credential fallback is venue-specific. Gateway startup asks the selected `VenuePlugin` to resolve credentials. Coinbase owns `COINBASE_API_KEY`, `COINBASE_API_SECRET_BASE64`, and `COINBASE_API_PASSPHRASE`; shared gateway startup must not know those names.

---

# 7. Market Data Normalization

V11 standardizes shared FIX parsing and reserves venue plugins for policy and enrichment.

## 7.1 Shared Rule

Common FIX L2/L3 tags are parsed once in shared base normalizers. Venue plugins override small hooks only when venue behavior differs.

## 7.2 L2 Normalization

L2 means price-level market data.

Common FIX fields:

```text
35=W or 35=X
34=MsgSeqNum
55=Symbol
268=NoMDEntries
269=MDEntryType
270=MDEntryPx
271=MDEntrySize
279=MDUpdateAction
1023=MDPriceLevel
60=TransactTime
```

Required base class:

```java
abstract class AbstractFixL2MarketDataNormalizer implements MarketDataNormalizer {
    public final Action onFixMessage(...) {
        // shared FIX scan
        // parse standard L2 tags
        // build normalized L2 context
        // call enrich(context)
        // publish MarketDataEvent
    }

    protected void enrich(L2MarketDataContext context) {
        // default no-op
    }

    protected EntryType mapEntryType(char mdEntryType) { ... }

    protected UpdateAction mapUpdateAction(char mdUpdateAction) { ... }

    protected boolean isMarketDataMessage(char msgType) { ... }
}
```

`MarketDataEvent.sizeScaled` represents absolute price-level size after update unless explicitly documented otherwise.

## 7.3 L3 Normalization

L3 means order-level market data.

Common FIX fields:

```text
35=W or 35=X
34=MsgSeqNum
55=Symbol
268=NoMDEntries
269=MDEntryType
270=MDEntryPx
271=MDEntrySize
278=MDEntryID
280=MDEntryRefID
279=MDUpdateAction
60=TransactTime
```

Required base class:

```java
abstract class AbstractFixL3MarketDataNormalizer implements MarketDataNormalizer {
    public final Action onFixMessage(...) {
        // shared FIX scan
        // parse standard L3 tags
        // build normalized L3 context
        // call enrich(context)
        // publish MarketByOrderEvent
        // optionally derive L2 update
    }

    protected void enrich(L3MarketDataContext context) {
        // default no-op
    }

    protected String orderIdentity(L3MarketDataContext context) {
        // default uses MDEntryID / tag 278
    }

    protected boolean sizeIsAbsolute() {
        return true;
    }

    protected boolean deleteRequiresPreviousState() {
        return true;
    }
}
```

Required new normalized event:

```text
MarketByOrderEvent
  venueId
  instrumentId
  side
  updateAction
  priceScaled
  sizeScaled
  venueOrderId or mdEntryId
  ingressTimestampNanos
  exchangeTimestampNanos
  fixSeqNum
```

---

# 8. Per-Venue L2, L3 State, and Derived L2

For venues configured as L3, NitroJEx maintains enough order-level state to correctly derive L2.

Recommended naming:

```text
VenueL2Book
  Price-level book for one venue/instrument.
  Current implementation may still be named L2OrderBook, but the role is
  venue-level L2, not L3.

VenueL3Book
  Order-level book for one venue/instrument, only when the venue feed is L3.

ConsolidatedL2Book
  Cross-venue aggregate L2 book for one instrument.
```

`L2OrderBook` must not be renamed to `VenueL3OrderBook`; it stores price levels, not individual orders.

Minimum required L3 state per venue/instrument:

```text
orderId -> side
orderId -> price
orderId -> size
side + price -> total size
```

Historical deleted orders are removed. Queue ordering is optional and not required for the first V11 implementation.

L3 flow:

```text
FIX L3 message
  -> AbstractFixL3MarketDataNormalizer
  -> MarketByOrderEvent
  -> VenueL3Book
  -> derived MarketDataEvent
  -> VenueL2Book
  -> ConsolidatedL2Book
```

---

# 9. Consolidated L2 View

Per-venue L2 remains the required foundation. Consolidated L2 is an additional market view used for cross-venue fair value, risk valuation, hedging, adverse-selection detection, and arbitrage.

Consolidation rule:

```text
venueBooks[venueId][instrumentId][side][price] = size
consolidated[instrumentId][side][price] = sum(size across venues)
```

A delete from one venue only removes that venue's contribution.

Strategies must be able to read both per-venue and consolidated views:

```java
interface MarketView {
    long bestBid(int venueId, int instrumentId);
    long bestAsk(int venueId, int instrumentId);

    long consolidatedBestBid(int instrumentId);
    long consolidatedBestAsk(int instrumentId);

    L2OrderBook venueBook(int venueId, int instrumentId);
    ConsolidatedL2Book consolidatedBook(int instrumentId);
}
```

Market making does not require consolidated L2, but may use it as a reference price. Arb and hedging strategies are expected to benefit from it.

Consolidated L2 is a gross public-market view. It must not be used as executable liquidity for arbitrage unless own visible liquidity has been excluded or the strategy explicitly accepts the risk of self-liquidity double-counting.

---

# 10. Arbitrage and Own-Liquidity Control

NitroJEx currently has a multi-venue arbitrage strategy implementation: `ArbStrategy` scans configured venue IDs, compares buy-venue best ask against sell-venue best bid, applies taker fees and slippage, and submits deterministic two-leg IOC orders through the cluster command path.

V11 upgrades this into a professional real-life implementation by adding explicit own-liquidity and self-trade controls.

## 10.1 Arbitrage Market Views

Arbitrage must distinguish:

```text
Gross venue L2
  All venue-published visible liquidity.

Own working orders
  NitroJEx's authoritative live order state from ExecutionReport / OrderManager.

External executable L2
  Gross venue L2 minus NitroJEx's own visible resting liquidity, where identifiable
  or conservatively estimable.
```

Arbitrage must use external executable liquidity for opportunity sizing and profitability checks.

## 10.2 L2-Only Venue Approximation

For L2-only venues, exact self-identification is usually unavailable. NitroJEx must conservatively estimate external liquidity:

```text
externalSizeAtPrice =
    max(0, grossVenueL2SizeAtPrice - ownWorkingSizeAtSameVenueSidePrice)
```

This prevents strategies from treating their own resting quote as external liquidity.

## 10.3 L3 Own-Order Reconciliation

For L3 venues, if public/order-level market data exposes an order ID that can be matched to `OrderManager.venueOrderId`, NitroJEx should mark that L3 order as own visible liquidity.

Required matching inputs:

```text
clOrdId
venueOrderId
venueId
instrumentId
side
price
remainingQty
```

If reliable venue-order-ID matching is unavailable, the system falls back to the conservative L2 approximation.

## 10.4 Pre-Trade Self-Cross Checks

Before sending an arbitrage leg, NitroJEx must check whether the order would cross NitroJEx's own resting orders on the same venue/instrument.

Rules:

```text
New BUY would self-cross if own SELL price <= buy price.
New SELL would self-cross if own BUY price >= sell price.
```

On self-cross risk, the strategy must choose one explicit action:

```text
skip trade
reduce size
cancel/replace own resting order first
route to another venue
send only when venue self-trade prevention is configured
```

The default for arbitrage is to skip or reduce the trade unless a task explicitly implements a cancel-then-trade sequence.

## 10.5 Venue Self-Trade Prevention

Venue-native self-trade prevention is required where the venue supports it. Venue-specific STP tags and policies belong in `OrderEntryPolicy.enrichNewOrder(...)`, not in generic strategy code.

Typical policy choices:

```text
cancel newest
cancel oldest
cancel both
decrement and cancel
reject taking order
```

STP is an emergency brake. It does not replace strategy-level external-liquidity and self-cross checks.

## 10.6 Queue Position Awareness

Queue position is optional for the first V11 own-liquidity implementation, but the design must not block it.

For L3 venues, queue position may be estimated from order-level order and own-order matching.

For L2 venues, queue position is approximate:

```text
sizeAheadEstimate =
    levelSizeWhenOrderAccepted - ownOrderRemainingQty
```

adjusted by later trades/cancels when the data allows.

## 10.7 Post-Only Quote Management

Post-only behavior applies mainly to passive quoting and market-making legs. Arbitrage legs are often IOC/taker. However, when an arbitrage strategy uses a passive leg, it must be able to request post-only behavior through venue order-entry policy when supported.

## 10.8 Required Strategy Safeguards

Modern V11 arbitrage must include:

```text
external liquidity instead of raw gross L2
self-cross pre-check before sending each leg
venue STP where supported
own-order overlay for L2 approximation
L3 own-order reconciliation where order IDs are matchable
leg timeout and imbalance hedge behavior
fees, slippage, inventory/risk checks, and cooldown after failure
tests for self-liquidity false opportunities
```

---

# 11. Order Entry Refactor

Gateway order routing must stop directly importing generated FIX 4.2 encoder classes.

Required adapter:

```java
interface VenueOrderEntryAdapter {
    void sendNewOrder(NewOrderCommandDecoder command);
    void sendCancel(CancelOrderCommandDecoder command);
    void sendReplace(ReplaceOrderCommandDecoder command);
    void sendStatusQuery(OrderStatusQueryCommandDecoder command);
}
```

`ExecutionRouterImpl` becomes a thin router over `VenueOrderEntryAdapter`.

Venue-specific order-entry differences are represented through policy hooks:

```java
interface OrderEntryPolicy {
    boolean nativeReplaceSupported();
    boolean accountRequired();
    String account(FixConfig fix, VenueConfig venue);
    void enrichNewOrder(...);
    void enrichCancel(...);
    void enrichReplace(...);
}
```

---

# 12. Execution Report Normalization

Execution reports use a shared normalizer plus venue policy:

```java
final class StandardExecutionReportNormalizer implements ExecutionReportNormalizer {
    StandardExecutionReportNormalizer(ExecutionReportPolicy policy) { ... }
}

interface ExecutionReportPolicy {
    ExecType mapExecType(char fixExecType);
    int mapRejectCode(int fixRejectCode);
    boolean isFinal(ExecType execType, long leavesQtyScaled);
    long parseClientOrderId(CharSequence clOrdId);
    void enrich(ExecutionReportContext context);
}
```

Common FIX behavior is implemented once. Venue-specific reject codes, proprietary fields, fee/liquidity tags, or nonstandard client-order-id behavior belong in the venue policy.

---

# 13. Logon Customization

Logon customization is venue-specific. Protocol mechanics are FIX-plugin-owned.

```java
interface VenueLogonCustomizer {
    void customizeLogon(AbstractLogonEncoder logon, long sessionId);
}
```

Coinbase owns HMAC signing, passphrase handling, proprietary cancel-on-disconnect behavior, and Coinbase-specific logon fields.

FIX protocol plugins own dictionary class, BeginString, FIXT application version, and protocol-level session configuration.

---

# 14. Gateway Runtime Composition

Target runtime:

```java
record VenueRuntime(
    VenueConfig venue,
    FixProtocolPlugin fixPlugin,
    VenuePlugin venuePlugin,
    Session session,
    ExecutionRouter executionRouter,
    MarketDataNormalizer marketDataNormalizer,
    ExecutionReportNormalizer executionReportNormalizer
) {}
```

The current one-gateway-one-venue deployment model remains valid. The design must not block a later multi-venue gateway process using:

```java
Map<Integer, VenueRuntime> runtimes;
```

---

# 15. Testing Strategy

V11 must preserve the V10 testing rigor and extend it for the new plugin and L3 market-data surfaces. Every V11 feature must include positive, negative, edge, exception, and failure-case coverage at the appropriate test tier.

The V11 test suite is the mandatory quality gate before any QA/UAT cycle using real Coinbase connectivity. Real Coinbase QA/UAT must not begin until local unit tests, integration tests, and Coinbase Simulator end-to-end tests prove all practical code paths for the changed classes and methods.

## 15.1 Test Tiers

V11 uses three required test tiers:

```text
Unit tests
  Validate isolated config parsing, plugin registry behavior, normalizer parsing,
  order-entry policy decisions, L3 book mutation, and consolidated L2 aggregation.

Integration tests
  Validate module-level wiring across real SBE codecs, gateway normalizers,
  cluster market views, generated FIX codecs, and plugin composition.

End-to-end tests
  Validate full gateway/cluster behavior using CoinbaseExchangeSimulator,
  including Coinbase FIX L2 market-data flow, Coinbase FIX L3 market-data flow,
  and live-wire FIX order-entry/execution feedback where required by the task
  plan.
```

## 15.2 Class and Method Coverage Standard

Every V11 task that creates or modifies production code must include task-owned tests for the practical behavior surface of each changed class and non-trivial method.

Required coverage:

```text
Constructors / factories
  Valid construction, invalid null/blank inputs, unsupported enum/config values,
  and immutable/default-field behavior.

Public methods
  Success path, invalid input path, boundary input path, exception/failure path,
  and important side effects.

Protocol parsers / normalizers
  All supported tags, missing required tags, malformed numeric values, unknown
  enum values, unsupported message types, duplicate or repeated update fields,
  and venue enrich hook behavior.

Stateful components
  Empty state, first insert, update existing, delete existing, delete missing,
  duplicate insert, zero-size update, stale state, reset/recovery behavior, and
  invariants after failure.

Concurrency / back-pressure components
  Ring full, send rejected, retry exhausted, drop/spin behavior, and metric or
  callback side effects.
```

Private helper methods do not require direct tests when their behavior is fully covered through public APIs. If a private helper owns complex protocol/state logic that cannot be confidently covered through public APIs, the design should be reconsidered or a package-private test-facing seam should be justified in the task.

## 15.3 Required Case Categories

Every V11 task that owns behavior must cover these categories unless the task card explicitly documents why a category is not applicable:

```text
Positive cases
  Valid config, valid FIX messages, valid plugin selection, expected L2/L3 updates.

Negative cases
  Unsupported plugin IDs, unsupported market-data models, unknown symbols,
  missing required tags, unsupported venue capabilities.

Edge cases
  Empty books, duplicate prices across venues, zero-size updates, deletes for
  missing orders, repeated updates at the same price, max visible depth, and
  snapshot followed by incrementals.

Exception cases
  Config validation failures, malformed FIX fields, invalid numeric values,
  missing dictionary mappings, and plugin registry misses.

Failure cases
  Disruptor full/drop behavior, Artio send back-pressure, simulator disconnect,
  sequence gaps, stale market data, and L3 state recovery after bad/missing data.
```

## 15.4 Integration Coverage Standard

Integration tests must prove that independently tested classes work together across module boundaries.

Required V11 integration surfaces:

```text
Config -> plugin registry
  Venue config selects the correct FIX protocol plugin, venue plugin, and
  market-data model.

FIX codegen -> gateway runtime
  Generated dictionaries coexist, compile, and are selected by session config.

Gateway normalizer -> SBE
  L2/L3 FIX messages become the correct normalized SBE events.

SBE -> cluster market view
  MarketDataEvent and MarketByOrderEvent update per-venue L2, VenueL3Book,
  derived L2, and consolidated L2 correctly.

Gateway runtime composition
  VenueRuntime wires protocol plugin, venue plugin, order-entry adapter,
  execution normalizer, and market-data normalizer consistently.
```

## 15.5 Coinbase FIX L2/L3 End-to-End Coverage

V11 explicitly targets Coinbase FIX connectivity through the local Coinbase Simulator before any real Coinbase QA/UAT. The simulator must support enough FIX L2, FIX L3, order-entry, execution-report, disconnect, reconnect, and malformed-message behavior to validate gateway and cluster logic without requiring live Coinbase access.

The V11 automated suite has two Coinbase simulator E2E levels:

```text
Harness-level simulator E2E
  Uses deterministic simulator events and local harness bridges to validate
  normalizers, SBE messages, books, derived L2, consolidated L2, and failure
  handling.

Live-wire simulator E2E
  Uses a local Coinbase simulator FIX endpoint/session fixture and proves the
  gateway-to-cluster-to-egress-to-gateway order/execution loop. These tests must
  exercise real FIX session/connectivity semantics, not only direct Java method
  calls or direct SBE event injection.
```

The simulator must be able to emit:

```text
L2 snapshot
L2 incremental price-level update
L2 malformed message for negative/exception testing
L3 snapshot
L3 add order
L3 change order size
L3 delete order
L3 sequence gap or out-of-order update
L3 disconnect/reconnect event
Malformed L3 message for negative/exception testing
NewOrderSingle ack/fill/reject execution reports
OrderCancelRequest cancel/reject execution reports
```

Required L2 live-wire E2E flow:

```text
CoinbaseExchangeSimulator local FIX endpoint
  -> Gateway FIX session using selected FIXT.1.1/FIX 5.0SP2 plugin
  -> CoinbaseVenuePlugin
  -> AbstractFixL2MarketDataNormalizer + Coinbase enrich(...)
  -> MarketDataEvent
  -> GatewayDisruptor
  -> Aeron Cluster ingress
  -> MessageRouter
  -> Venue L2 book / InternalMarketView
  -> optional ConsolidatedL2Book
  -> StrategyEngine / MarketView
  -> cluster egress order command
  -> gateway OrderCommandHandler / ExecutionRouter
  -> Coinbase FIX order-entry adapter
  -> CoinbaseExchangeSimulator receives order
  -> simulator ExecutionReport
  -> gateway ExecutionHandler
  -> cluster OrderManager / PortfolioEngine / RiskEngine / StrategyEngine
```

Required L3 live-wire E2E flow:

```text
CoinbaseExchangeSimulator local FIX endpoint
  -> Coinbase FIX L3 message stream
  -> Gateway FIX session using selected FIXT.1.1/FIX 5.0SP2 plugin
  -> CoinbaseVenuePlugin
  -> AbstractFixL3MarketDataNormalizer + Coinbase enrich(...)
  -> MarketByOrderEvent
  -> GatewayDisruptor
  -> Aeron Cluster ingress
  -> MessageRouter
  -> VenueL3Book
  -> derived MarketDataEvent
  -> VenueL2Book
  -> optional ConsolidatedL2Book
  -> StrategyEngine / MarketView
  -> cluster egress order command
  -> gateway OrderCommandHandler / ExecutionRouter
  -> Coinbase FIX order-entry adapter
  -> CoinbaseExchangeSimulator receives order
  -> simulator ExecutionReport
  -> gateway ExecutionHandler
  -> cluster OrderManager / PortfolioEngine / RiskEngine / StrategyEngine
```

End-to-end tests must prove for Coinbase L2:

```text
Coinbase L2 snapshot builds correct venue L2 state
Coinbase L2 add/change/delete or equivalent price-level update updates venue L2
Coinbase L2 updates can update optional consolidated L2
Coinbase L2 unknown symbol is rejected without corrupting book state
Coinbase L2 malformed message fails safely
Coinbase L2 disconnect marks venue state appropriately
Coinbase L2 reconnect/resubscribe recovers or marks stale state deterministically
At least one order can travel cluster egress -> gateway -> simulator over FIX
Simulator execution report can travel simulator -> gateway -> cluster state
```

End-to-end tests must prove for Coinbase L3:

```text
Coinbase L3 snapshot builds correct active L3 state
Coinbase L3 add/change/delete derives correct per-venue L2 levels
Coinbase L3 unknown symbol is rejected without corrupting book state
Coinbase L3 malformed message fails safely
Coinbase L3 disconnect marks venue state appropriately
Coinbase L3 sequence gap is detected and triggers recovery/staleness behavior
At least one order can travel cluster egress -> gateway -> simulator over FIX
Simulator execution report can travel simulator -> gateway -> cluster state
```

## 15.6 Pre-QA/UAT Readiness Gate

Before QA/UAT against real Coinbase connectivity, the following must be true:

```text
All V11 task-owned unit tests pass.
All V11 integration tests pass.
All Coinbase Simulator E2E tests pass.
All Coinbase Simulator live-wire FIX L2 E2E tests pass.
All Coinbase Simulator live-wire FIX L3 E2E tests pass.
All new/modified classes have explicit positive, negative, edge, exception,
and failure coverage or documented non-applicability.
No V11 task has unresolved test TODOs.
No real Coinbase connectivity is required for the automated acceptance suite.
Known simulator gaps are documented and approved before UAT.
```

QA/UAT with real Coinbase is for venue certification, credentials, network/TLS/session behavior, and production-readiness validation. It is not a substitute for local automated coverage.

## 15.7 Test Ownership Rule

Task cards must explicitly name the test classes they create or modify. If a task changes runtime behavior but does not add a test, the task must document why the behavior is already covered by another named test class.

---

# 16. Low-Latency Determinism and Allocation Policy

NitroJEx targets a deterministic trading core and zero-allocation steady-state hot paths. This does not mean the entire repository is literally allocation-free. Startup, config parsing, simulator tooling, REST parsing, admin operations, exceptional diagnostics, and tests may allocate.

The professional claim for V11 is:

```text
NitroJEx is designed for deterministic-core, low-latency trading with a
bounded-allocation architecture and an explicit path to zero-allocation
steady-state hot paths.
```

The project must not claim full zero-GC / zero-allocation production readiness until hot-path JMH and allocation profiling prove `0 B/op` for the declared steady-state paths.

## 16.1 Hot-Path Allocation Boundary

The following paths are considered hot and must be designed for zero allocation after startup/warmup:

```text
FIX L2/L3 market-data parsing
FIX execution-report parsing
SBE message encode/decode
Gateway disruptor handoff
Aeron publication handoff
VenueL2Book mutation
VenueL3Book mutation
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

The following paths are cold/control paths and may allocate:

```text
TOML config loading
plugin registry construction
startup validation
admin CLI
replay tooling setup
CoinbaseExchangeSimulator internals
REST polling and JSON parsing, provided parsed results are converted before
entering hot strategy/order/book paths
operator logging
exception construction on unrecoverable startup/config failures
tests and benchmark harness code
```

## 16.2 Collections and State Storage

Hot-path state must move away from general-purpose Java collections where practical.

Preferred hot-path structures:

```text
arrays indexed by venueId / instrumentId / side
bounded preallocated object pools
primitive maps where unavoidable
fixed-size ring buffers
reusable mutable context objects
byte buffers for venue-provided textual IDs
scaled long values for price, size, and notional
```

General-purpose `Map`, `List`, `String`, and per-event record/object creation are acceptable in config, tests, tooling, simulator, or startup code. They are not acceptable as a long-term design for normal steady-state market-data, order, risk, or strategy event processing.

## 16.3 FIX Parsing and Symbol Lookup

FIX normalizers must evolve from string-based parsing toward direct parsing from `DirectBuffer` / byte ranges.

Required direction:

```text
Parse decimals directly into scaled long values from bytes.
Map FIX enum tags from raw byte/char values.
Resolve symbols through byte-based registry lookup.
Avoid converting tag values to String on the normal market-data path.
Keep CharSequence/String compatibility APIs only as transitional or cold-path adapters.
```

## 16.4 Venue Order ID Representation

Venue order IDs may arrive as textual FIX fields, but hot-path storage must not depend on allocating Java `String` objects per event.

Target representation:

```text
venueId
instrumentId
venueOrderIdHash
fixed byte storage for original venue order id
length
collision check using stored bytes
```

The fast key may use a hash/fingerprint, but correctness must handle hash collisions by comparing stored bytes. Exact own-order reconciliation must remain venue- and instrument-scoped.

## 16.5 REST JSON Boundary

Coinbase REST polling is not part of the trading hot path. JSON parsing may allocate inside the REST poller as a cold/side path, but JSON objects must not enter strategy, book, risk, or order-manager hot paths.

If a REST-derived value is needed by hot code, it must be converted into compact internal state or SBE/admin events before crossing into the deterministic core.

## 16.6 Logging and Exceptions

Expected hot-path failures must use counters, status codes, and safe drops instead of constructing exceptions or formatting logs.

Examples:

```text
unknown symbol -> increment counter and drop
malformed market-data tick -> increment counter and drop
disruptor full -> increment counter and apply configured drop/back-pressure rule
unsupported config -> throw during startup, not at first hot-path use
```

Operator logging should be rate-limited or handled by cold monitoring code that reads counters and state snapshots.

## 16.7 Benchmark and Allocation Proof

V11 must add benchmark coverage before any production claim of zero allocation.

Required benchmark surfaces:

```text
FIX L2 parsing
FIX L3 parsing
VenueL3Book add/change/delete
L3-to-L2 derivation
ConsolidatedL2Book update
OwnOrderOverlay update/query
ExternalLiquidityView query
RiskEngine decision
MarketMakingStrategy tick
ArbStrategy tick
order command encoding
```

Required evidence:

```text
JMH benchmark results include allocation data.
Hot-path target is 0 B/op after warmup.
No GC occurs during the steady-state benchmark measurement window.
Latency histograms are produced for selected gateway and cluster paths.
Any non-zero allocation is documented with owner, reason, and remediation task.
```

## 16.8 Determinism Definition

NitroJEx determinism means:

```text
Given the same ordered internal SBE event stream and the same initial snapshot,
the cluster core must produce the same order state, risk state, book state,
strategy decisions, and outbound command sequence.
```

Live networking, wall-clock timing, OS scheduling, exchange behavior, REST timing, and operator logging are not deterministic. They must be normalized into ordered internal events before entering deterministic core logic.

# 17. Non-Goals

- Do not modify V10 spec or v1.4.0 plan.
- Do not reuse TASK-001 through TASK-034.
- Do not create placeholder future venue plugins.
- Do not make every venue implement full custom normalization.
- Do not replace per-venue L2 books with consolidated L2.
- Do not require consolidated L2 for all market-making strategies.
- Do not claim full zero-GC / zero-allocation production readiness without benchmark evidence.
