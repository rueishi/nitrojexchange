/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum ParentUpdateReason
{
    CHILD_ACCEPTED((byte)1),

    CHILD_PARTIAL_FILL((byte)2),

    CHILD_FILL((byte)3),

    CHILD_REJECT((byte)4),

    CHILD_CANCEL((byte)5),

    TIMER((byte)6),

    HEDGE_SUBMITTED((byte)7),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    ParentUpdateReason(final byte value)
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
    public static ParentUpdateReason get(final byte value)
    {
        switch (value)
        {
            case 1: return CHILD_ACCEPTED;
            case 2: return CHILD_PARTIAL_FILL;
            case 3: return CHILD_FILL;
            case 4: return CHILD_REJECT;
            case 5: return CHILD_CANCEL;
            case 6: return TIMER;
            case 7: return HEDGE_SUBMITTED;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
