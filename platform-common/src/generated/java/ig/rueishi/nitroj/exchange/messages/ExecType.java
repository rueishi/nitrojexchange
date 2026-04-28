/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum ExecType
{
    NEW((byte)0),

    PARTIAL_FILL((byte)1),

    CANCELED((byte)4),

    REPLACED((byte)5),

    REJECTED((byte)8),

    ORDER_STATUS((byte)9),

    EXPIRED((byte)12),

    FILL((byte)15),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    ExecType(final byte value)
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
    public static ExecType get(final byte value)
    {
        switch (value)
        {
            case 0: return NEW;
            case 1: return PARTIAL_FILL;
            case 4: return CANCELED;
            case 5: return REPLACED;
            case 8: return REJECTED;
            case 9: return ORDER_STATUS;
            case 12: return EXPIRED;
            case 15: return FILL;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
