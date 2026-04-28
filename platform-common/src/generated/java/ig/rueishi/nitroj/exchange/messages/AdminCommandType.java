/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum AdminCommandType
{
    DEACTIVATE_KILL_SWITCH((byte)1),

    ACTIVATE_KILL_SWITCH((byte)2),

    PAUSE_STRATEGY((byte)3),

    RESUME_STRATEGY((byte)4),

    RESET_DAILY_COUNTERS((byte)5),

    TRIGGER_SNAPSHOT((byte)6),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    AdminCommandType(final byte value)
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
    public static AdminCommandType get(final byte value)
    {
        switch (value)
        {
            case 1: return DEACTIVATE_KILL_SWITCH;
            case 2: return ACTIVATE_KILL_SWITCH;
            case 3: return PAUSE_STRATEGY;
            case 4: return RESUME_STRATEGY;
            case 5: return RESET_DAILY_COUNTERS;
            case 6: return TRIGGER_SNAPSHOT;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
