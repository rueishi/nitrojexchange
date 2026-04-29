/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum PriceMode
{
    LIMIT((byte)1),

    REFERENCE((byte)2),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    PriceMode(final byte value)
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
    public static PriceMode get(final byte value)
    {
        switch (value)
        {
            case 1: return LIMIT;
            case 2: return REFERENCE;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
