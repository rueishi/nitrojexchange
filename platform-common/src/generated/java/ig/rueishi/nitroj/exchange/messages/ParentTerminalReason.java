/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum ParentTerminalReason
{
    COMPLETED((byte)1),

    CANCELED_BY_PARENT((byte)2),

    EXPIRED((byte)3),

    RISK_REJECTED((byte)4),

    CHILD_REJECTED((byte)5),

    HEDGE_FAILED((byte)6),

    KILL_SWITCH((byte)7),

    EXECUTION_ABORTED((byte)8),

    CAPACITY_REJECTED((byte)9),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    ParentTerminalReason(final byte value)
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
    public static ParentTerminalReason get(final byte value)
    {
        switch (value)
        {
            case 1: return COMPLETED;
            case 2: return CANCELED_BY_PARENT;
            case 3: return EXPIRED;
            case 4: return RISK_REJECTED;
            case 5: return CHILD_REJECTED;
            case 6: return HEDGE_FAILED;
            case 7: return KILL_SWITCH;
            case 8: return EXECUTION_ABORTED;
            case 9: return CAPACITY_REJECTED;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
