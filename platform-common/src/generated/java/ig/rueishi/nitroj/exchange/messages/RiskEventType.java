/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum RiskEventType
{
    KILL_SWITCH_ACTIVATED((byte)1),

    KILL_SWITCH_DEACTIVATED((byte)2),

    SOFT_LIMIT_BREACH((byte)3),

    HARD_LIMIT_REJECT((byte)4),

    RECOVERY_LOCK_SET((byte)5),

    RECOVERY_LOCK_CLEARED((byte)6),

    ORDER_STATE_ERROR((byte)7),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    RiskEventType(final byte value)
    {
        this.value = value;
    }

    /**
     * The raw encoded value in the Java type representation.
     *
     * @return the raw value encoded.
     */
    public byte value()
    {
        return value;
    }

    /**
     * Lookup the enum value representing the value.
     *
     * @param value encoded to be looked up.
     * @return the enum value representing the value.
     */
    public static RiskEventType get(final byte value)
    {
        switch (value)
        {
            case 1: return KILL_SWITCH_ACTIVATED;
            case 2: return KILL_SWITCH_DEACTIVATED;
            case 3: return SOFT_LIMIT_BREACH;
            case 4: return HARD_LIMIT_REJECT;
            case 5: return RECOVERY_LOCK_SET;
            case 6: return RECOVERY_LOCK_CLEARED;
            case 7: return ORDER_STATE_ERROR;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
