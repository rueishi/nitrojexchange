package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable instrument identity loaded from {@code instruments.toml}.
 *
 * <p>Responsibility: carries the stable instrument ID registry fields owned by
 * TASK-004. Role in system: TASK-005's ID registry uses these records for
 * symbol-to-ID and ID-to-symbol mappings. Relationships: trading-specific limits
 * and strategy settings are intentionally held in {@link RiskConfig} and
 * {@link MarketMakingConfig}; this record is only the instrument catalog.
 * Lifecycle: created at startup and then treated as read-only shared metadata.
 * Design intent: keep the registry surface minimal and aligned with the task
 * card fields consumed by downstream components.
 *
 * @param id immutable integer instrument ID
 * @param symbol venue/FIX symbol, for example {@code BTC-USD}
 * @param baseAsset base asset code
 * @param quoteAsset quote asset code
 */
public record InstrumentConfig(int id, String symbol, String baseAsset, String quoteAsset) {
}
