/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum TimeInForce
{
    DAY((byte)0),

    GTC((byte)1),

    IOC((byte)3),

    FOK((byte)4),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    TimeInForce(final byte value)
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
    public static TimeInForce get(final byte value)
    {
        switch (value)
        {
            case 0: return DAY;
            case 1: return GTC;
            case 3: return IOC;
            case 4: return FOK;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
