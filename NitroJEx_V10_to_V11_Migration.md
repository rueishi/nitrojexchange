# NitroJEx V10 to V11 Migration

## 1. Overview

This document describes the differences between `NitroJEx_Master_Spec_V10.0.md` and `NitroJEx_Master_Spec_V11.0.md`, including breaking changes, new modules, compatibility expectations, and rollout strategy.

V10 is the frozen production baseline. V11 is a major system evolution focused on multi-version FIX connectivity, venue plugins, configurable L2/L3 market data, L3-to-L2 derivation, optional consolidated L2 views, and professional cross-venue arbitrage safeguards.

## 2. Breaking Changes

- Gateway FIX codec generation changes from one global Artio package to version-isolated generated packages.
- Gateway core must stop importing generated FIX 4.2 encoder classes directly.
- Venue config gains required plugin and market-data-model fields.
- Market-data routing becomes plugin-selected rather than hard-wired to the current L2 parser.
- L3 support introduces a new `MarketByOrderEvent` schema.
- `InternalMarketView` expands from per-venue L2 only to per-venue L2 plus optional consolidated L2 and L3-derived updates.
- Arbitrage opportunity sizing must move from raw gross L2 to external executable liquidity that excludes NitroJEx's own visible working orders where possible.
- Unsupported venue plugin strings are parsed by common config but fail at gateway plugin registry/composition instead of a Coinbase-only common whitelist.

## 3. New Capabilities

- FIX 4.2, FIX 4.4, and FIXT.1.1/FIX 5.0SP2 protocol plugin support.
- Venue plugin architecture.
- Coinbase venue behavior wrapped in `CoinbaseVenuePlugin`.
- Shared abstract L2 FIX normalizer with venue `enrich(...)` hook.
- Shared abstract L3 FIX normalizer with venue `enrich(...)` hook.
- Configurable market-data model per venue: `L2` or `L3`.
- Minimum active L3 book state to derive L2.
- Optional consolidated L2 book across venues.
- External executable-liquidity view for arbitrage and hedging.
- Own-order overlay that keeps `OrderManager` state separate from market books while allowing self-liquidity subtraction.
- L2-only conservative own-liquidity subtraction.
- L3 own-order reconciliation by venue order ID where the venue feed exposes reliable IDs.
- Strategy-level self-cross checks and venue-native self-trade-prevention hooks.
- Coinbase FIX L3 simulator and end-to-end test coverage.
- Pre-QA/UAT automated quality gate requiring unit, integration, and Coinbase Simulator E2E coverage before real Coinbase connectivity testing.

## 4. Module Changes

| Module | Change |
|--------|--------|
| `platform-common` | Adds config enums, venue capabilities, and `MarketByOrderEvent` SBE schema. |
| `platform-gateway` | Adds FIX protocol plugin registry, venue plugin registry, order-entry adapter, L2/L3 normalizer bases, runtime plugin composition, venue-owned credential fallback, and venue STP order-entry hooks. |
| `platform-cluster` | Adds `VenueL3Book`, derived L2 update path, `ConsolidatedL2Book`, own-order overlay, external-liquidity view, and arbitrage self-cross controls. |
| `platform-tooling` | Extends CoinbaseExchangeSimulator and TradingSystemTestHarness for Coinbase FIX L3 end-to-end coverage. Later replay tooling may learn `MarketByOrderEvent`. |
| `config` | `venues.toml` gains `fixPlugin`, `venuePlugin`, `marketDataModel`, and capability flags. |

## 5. Compatibility

| Compatibility Question | Answer |
|------------------------|--------|
| Are V10 spec and plan modified? | No. They remain frozen. |
| Can current Coinbase FIX 4.2 behavior be preserved? | Yes, through `ArtioFix42Plugin` plus `CoinbaseVenuePlugin`. |
| Are V10 gateway binaries directly compatible with V11 config? | No. V11 venue config adds required fields. |
| Are existing `MarketDataEvent` L2 consumers preserved? | Yes. L2 remains the common price-level event. |
| Does V11 add a new data format? | Yes. `MarketByOrderEvent` for L3. |
| Does market making require consolidated L2? | No. Consolidated L2 is optional and used as a higher-level reference view. |
| Does current code already include multi-venue arbitrage? | Yes. `ArbStrategy` scans configured venue IDs, compares buy ask/sell bid, applies fees/slippage, and submits two IOC legs. |
| Is current arbitrage complete for real-life self-trade safety? | Not yet. V11 adds external liquidity, own-order overlay, self-cross checks, L3 reconciliation, and venue STP hooks. |

## 6. Rollout Strategy

1. Freeze and archive V10 artifacts:
   - `NitroJEx_Master_Spec_V10.0.md`
   - `nitrojex_implementation_plan_v1.4.0.md`
2. Implement V11 plugin interfaces without changing behavior.
3. Wrap current Coinbase FIX 4.2 path as `ArtioFix42Plugin` + `CoinbaseVenuePlugin`.
4. Run V11 in compatibility mode using existing Coinbase FIX 4.2 behavior.
5. Add L2 normalizer extraction and verify existing market-data tests.
6. Add L3 schema and L3 book behind config.
7. Extend CoinbaseExchangeSimulator for Coinbase FIX L3 snapshot/incremental/failure flows.
8. Add Coinbase FIX L3 end-to-end tests.
9. Add consolidated L2 behind strategy/market-view accessors.
10. Add own-order overlay and external executable-liquidity view.
11. Upgrade arbitrage to use external liquidity, same-venue self-cross checks, L3 own-order reconciliation, and venue STP hooks.
12. Add FIX 4.4 and FIXT.1.1/FIX 5.0SP2 codegen packages.
13. Run the V11 automated quality gate: all unit tests, integration tests, and Coinbase Simulator E2E tests.
14. Begin real Coinbase QA/UAT only after automated coverage passes.
15. Shadow test V11 against V10 where possible.
16. Gradually cut over venue by venue.

## 7. Rollback Strategy

Rollback is file/config based:

- Keep V10 artifacts and binaries available.
- Keep V11 config separate from V10 config.
- If V11 plugin selection or market-data model causes runtime failure, revert the deployment to the V10 gateway/cluster pair and V10 config.

## 8. Traceability

V10 task IDs remain:

```text
TASK-001 through TASK-034
```

V11 task IDs begin at:

```text
TASK-101
```

No V11 task may reuse a V10 task ID.
