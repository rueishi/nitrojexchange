package ig.rueishi.nitroj.exchange.cluster;

/**
 * Pre-allocated risk decisions returned from the hot-path pre-trade check.
 */
public record RiskDecision(boolean approved, byte rejectCode) {
    public static final RiskDecision APPROVED = new RiskDecision(true, (byte) 0);
    public static final RiskDecision REJECT_RECOVERY = new RiskDecision(false, (byte) 1);
    public static final RiskDecision REJECT_KILL_SWITCH = new RiskDecision(false, (byte) 2);
    public static final RiskDecision REJECT_ORDER_TOO_LARGE = new RiskDecision(false, (byte) 3);
    public static final RiskDecision REJECT_MAX_LONG = new RiskDecision(false, (byte) 4);
    public static final RiskDecision REJECT_MAX_SHORT = new RiskDecision(false, (byte) 5);
    public static final RiskDecision REJECT_MAX_NOTIONAL = new RiskDecision(false, (byte) 6);
    public static final RiskDecision REJECT_RATE_LIMIT = new RiskDecision(false, (byte) 7);
    public static final RiskDecision REJECT_DAILY_LOSS = new RiskDecision(false, (byte) 8);
    public static final RiskDecision REJECT_VENUE_NOT_CONNECTED = new RiskDecision(false, (byte) 9);
}
