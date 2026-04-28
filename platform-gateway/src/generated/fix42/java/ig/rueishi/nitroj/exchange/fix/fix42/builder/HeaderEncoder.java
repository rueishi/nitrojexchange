/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix42.builder;

import uk.co.real_logic.artio.dictionary.Generated;
import org.agrona.MutableDirectBuffer;
import org.agrona.AsciiSequenceView;
import static uk.co.real_logic.artio.dictionary.generation.CodecUtil.*;
import static uk.co.real_logic.artio.dictionary.SessionConstants.*;
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.fields.ReadOnlyDecimalFloat;
import uk.co.real_logic.artio.fields.DecimalFloat;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;
import uk.co.real_logic.artio.util.AsciiBuffer;
import uk.co.real_logic.artio.fields.LocalMktDateEncoder;
import uk.co.real_logic.artio.fields.UtcTimestampEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import uk.co.real_logic.artio.dictionary.CharArraySet;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.IntHashSet.IntIterator;
import uk.co.real_logic.artio.EncodingException;
import uk.co.real_logic.artio.dictionary.CharArrayWrapper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.AsciiSequenceView;
import uk.co.real_logic.artio.builder.FieldBagEncoder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static uk.co.real_logic.artio.builder.Validation.CODEC_VALIDATION_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownField.CODEC_REJECT_UNKNOWN_FIELD_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownEnumValue.CODEC_REJECT_UNKNOWN_ENUM_VALUE_ENABLED;
import ig.rueishi.nitroj.exchange.fix.fix42.*;

@Generated("uk.co.real_logic.artio")
public class HeaderEncoder implements uk.co.real_logic.artio.builder.SessionHeaderEncoder
{
    public HeaderEncoder()
    {
        beginStringAsCopy(DEFAULT_BEGIN_STRING, 0, DEFAULT_BEGIN_STRING.length);
    }


    private static final byte[] DEFAULT_BEGIN_STRING="FIX.4.2".getBytes(StandardCharsets.US_ASCII);

    private static final int beginStringHeaderLength = 2;
    private static final byte[] beginStringHeader = new byte[] {56, (byte) '='};

    private static final int bodyLengthHeaderLength = 2;
    private static final byte[] bodyLengthHeader = new byte[] {57, (byte) '='};

    private static final int msgTypeHeaderLength = 3;
    private static final byte[] msgTypeHeader = new byte[] {51, 53, (byte) '='};

    private static final int senderCompIDHeaderLength = 3;
    private static final byte[] senderCompIDHeader = new byte[] {52, 57, (byte) '='};

    private static final int targetCompIDHeaderLength = 3;
    private static final byte[] targetCompIDHeader = new byte[] {53, 54, (byte) '='};

    private static final int msgSeqNumHeaderLength = 3;
    private static final byte[] msgSeqNumHeader = new byte[] {51, 52, (byte) '='};

    private static final int senderSubIDHeaderLength = 3;
    private static final byte[] senderSubIDHeader = new byte[] {53, 48, (byte) '='};

    private static final int senderLocationIDHeaderLength = 4;
    private static final byte[] senderLocationIDHeader = new byte[] {49, 52, 50, (byte) '='};

    private static final int targetSubIDHeaderLength = 3;
    private static final byte[] targetSubIDHeader = new byte[] {53, 55, (byte) '='};

    private static final int targetLocationIDHeaderLength = 4;
    private static final byte[] targetLocationIDHeader = new byte[] {49, 52, 51, (byte) '='};

    private static final int possDupFlagHeaderLength = 3;
    private static final byte[] possDupFlagHeader = new byte[] {52, 51, (byte) '='};

    private static final int possResendHeaderLength = 3;
    private static final byte[] possResendHeader = new byte[] {57, 55, (byte) '='};

    private static final int sendingTimeHeaderLength = 3;
    private static final byte[] sendingTimeHeader = new byte[] {53, 50, (byte) '='};

    private static final int origSendingTimeHeaderLength = 4;
    private static final byte[] origSendingTimeHeader = new byte[] {49, 50, 50, (byte) '='};

    private static final int lastMsgSeqNumProcessedHeaderLength = 4;
    private static final byte[] lastMsgSeqNumProcessedHeader = new byte[] {51, 54, 57, (byte) '='};

    private final MutableDirectBuffer beginString = new UnsafeBuffer();
    private byte[] beginStringInternalBuffer = beginString.byteArray();
    private int beginStringOffset = 0;
    private int beginStringLength = 0;

    /* BeginString = 8 */
    public HeaderEncoder beginString(final DirectBuffer value, final int offset, final int length)
    {
        beginString.wrap(value);
        beginStringOffset = offset;
        beginStringLength = length;
        return this;
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final DirectBuffer value, final int length)
    {
        return beginString(value, 0, length);
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final DirectBuffer value)
    {
        return beginString(value, 0, value.capacity());
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final byte[] value, final int offset, final int length)
    {
        beginString.wrap(value);
        beginStringOffset = offset;
        beginStringLength = length;
        return this;
    }

    /* BeginString = 8 */
    public HeaderEncoder beginStringAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(beginString, value, offset, length))
        {
            beginStringInternalBuffer = beginString.byteArray();
        }
        beginStringOffset = 0;
        beginStringLength = length;
        return this;
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final byte[] value, final int length)
    {
        return beginString(value, 0, length);
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final byte[] value)
    {
        return beginString(value, 0, value.length);
    }

    /* BeginString = 8 */
    public boolean hasBeginString()
    {
        return beginStringLength > 0;
    }

    /* BeginString = 8 */
    public MutableDirectBuffer beginString()
    {
        return beginString;
    }

    /* BeginString = 8 */
    public String beginStringAsString()
    {
        return beginString.getStringWithoutLengthAscii(beginStringOffset, beginStringLength);
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final CharSequence value)
    {
        if (toBytes(value, beginString))
        {
            beginStringInternalBuffer = beginString.byteArray();
        }
        beginStringOffset = 0;
        beginStringLength = value.length();
        return this;
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            beginString.wrap(buffer);
            beginStringOffset = value.offset();
            beginStringLength = value.length();
        }
        return this;
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final char[] value)
    {
        return beginString(value, 0, value.length);
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final char[] value, final int length)
    {
        return beginString(value, 0, length);
    }

    /* BeginString = 8 */
    public HeaderEncoder beginString(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, beginString, offset, length))
        {
            beginStringInternalBuffer = beginString.byteArray();
        }
        beginStringOffset = 0;
        beginStringLength = length;
        return this;
    }

    private final MutableDirectBuffer msgType = new UnsafeBuffer();
    private byte[] msgTypeInternalBuffer = msgType.byteArray();
    private int msgTypeOffset = 0;
    private int msgTypeLength = 0;

    /* MsgType = 35 */
    public HeaderEncoder msgType(final DirectBuffer value, final int offset, final int length)
    {
        msgType.wrap(value);
        msgTypeOffset = offset;
        msgTypeLength = length;
        return this;
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final DirectBuffer value, final int length)
    {
        return msgType(value, 0, length);
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final DirectBuffer value)
    {
        return msgType(value, 0, value.capacity());
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final byte[] value, final int offset, final int length)
    {
        msgType.wrap(value);
        msgTypeOffset = offset;
        msgTypeLength = length;
        return this;
    }

    /* MsgType = 35 */
    public HeaderEncoder msgTypeAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(msgType, value, offset, length))
        {
            msgTypeInternalBuffer = msgType.byteArray();
        }
        msgTypeOffset = 0;
        msgTypeLength = length;
        return this;
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final byte[] value, final int length)
    {
        return msgType(value, 0, length);
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final byte[] value)
    {
        return msgType(value, 0, value.length);
    }

    /* MsgType = 35 */
    public boolean hasMsgType()
    {
        return msgTypeLength > 0;
    }

    /* MsgType = 35 */
    public MutableDirectBuffer msgType()
    {
        return msgType;
    }

    /* MsgType = 35 */
    public String msgTypeAsString()
    {
        return msgType.getStringWithoutLengthAscii(msgTypeOffset, msgTypeLength);
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final CharSequence value)
    {
        if (toBytes(value, msgType))
        {
            msgTypeInternalBuffer = msgType.byteArray();
        }
        msgTypeOffset = 0;
        msgTypeLength = value.length();
        return this;
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            msgType.wrap(buffer);
            msgTypeOffset = value.offset();
            msgTypeLength = value.length();
        }
        return this;
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final char[] value)
    {
        return msgType(value, 0, value.length);
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final char[] value, final int length)
    {
        return msgType(value, 0, length);
    }

    /* MsgType = 35 */
    public HeaderEncoder msgType(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, msgType, offset, length))
        {
            msgTypeInternalBuffer = msgType.byteArray();
        }
        msgTypeOffset = 0;
        msgTypeLength = length;
        return this;
    }

    private final MutableDirectBuffer senderCompID = new UnsafeBuffer();
    private byte[] senderCompIDInternalBuffer = senderCompID.byteArray();
    private int senderCompIDOffset = 0;
    private int senderCompIDLength = 0;

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final DirectBuffer value, final int offset, final int length)
    {
        senderCompID.wrap(value);
        senderCompIDOffset = offset;
        senderCompIDLength = length;
        return this;
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final DirectBuffer value, final int length)
    {
        return senderCompID(value, 0, length);
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final DirectBuffer value)
    {
        return senderCompID(value, 0, value.capacity());
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final byte[] value, final int offset, final int length)
    {
        senderCompID.wrap(value);
        senderCompIDOffset = offset;
        senderCompIDLength = length;
        return this;
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(senderCompID, value, offset, length))
        {
            senderCompIDInternalBuffer = senderCompID.byteArray();
        }
        senderCompIDOffset = 0;
        senderCompIDLength = length;
        return this;
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final byte[] value, final int length)
    {
        return senderCompID(value, 0, length);
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final byte[] value)
    {
        return senderCompID(value, 0, value.length);
    }

    /* SenderCompID = 49 */
    public boolean hasSenderCompID()
    {
        return senderCompIDLength > 0;
    }

    /* SenderCompID = 49 */
    public MutableDirectBuffer senderCompID()
    {
        return senderCompID;
    }

    /* SenderCompID = 49 */
    public String senderCompIDAsString()
    {
        return senderCompID.getStringWithoutLengthAscii(senderCompIDOffset, senderCompIDLength);
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final CharSequence value)
    {
        if (toBytes(value, senderCompID))
        {
            senderCompIDInternalBuffer = senderCompID.byteArray();
        }
        senderCompIDOffset = 0;
        senderCompIDLength = value.length();
        return this;
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            senderCompID.wrap(buffer);
            senderCompIDOffset = value.offset();
            senderCompIDLength = value.length();
        }
        return this;
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final char[] value)
    {
        return senderCompID(value, 0, value.length);
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final char[] value, final int length)
    {
        return senderCompID(value, 0, length);
    }

    /* SenderCompID = 49 */
    public HeaderEncoder senderCompID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, senderCompID, offset, length))
        {
            senderCompIDInternalBuffer = senderCompID.byteArray();
        }
        senderCompIDOffset = 0;
        senderCompIDLength = length;
        return this;
    }

    private final MutableDirectBuffer targetCompID = new UnsafeBuffer();
    private byte[] targetCompIDInternalBuffer = targetCompID.byteArray();
    private int targetCompIDOffset = 0;
    private int targetCompIDLength = 0;

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final DirectBuffer value, final int offset, final int length)
    {
        targetCompID.wrap(value);
        targetCompIDOffset = offset;
        targetCompIDLength = length;
        return this;
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final DirectBuffer value, final int length)
    {
        return targetCompID(value, 0, length);
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final DirectBuffer value)
    {
        return targetCompID(value, 0, value.capacity());
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final byte[] value, final int offset, final int length)
    {
        targetCompID.wrap(value);
        targetCompIDOffset = offset;
        targetCompIDLength = length;
        return this;
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(targetCompID, value, offset, length))
        {
            targetCompIDInternalBuffer = targetCompID.byteArray();
        }
        targetCompIDOffset = 0;
        targetCompIDLength = length;
        return this;
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final byte[] value, final int length)
    {
        return targetCompID(value, 0, length);
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final byte[] value)
    {
        return targetCompID(value, 0, value.length);
    }

    /* TargetCompID = 56 */
    public boolean hasTargetCompID()
    {
        return targetCompIDLength > 0;
    }

    /* TargetCompID = 56 */
    public MutableDirectBuffer targetCompID()
    {
        return targetCompID;
    }

    /* TargetCompID = 56 */
    public String targetCompIDAsString()
    {
        return targetCompID.getStringWithoutLengthAscii(targetCompIDOffset, targetCompIDLength);
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final CharSequence value)
    {
        if (toBytes(value, targetCompID))
        {
            targetCompIDInternalBuffer = targetCompID.byteArray();
        }
        targetCompIDOffset = 0;
        targetCompIDLength = value.length();
        return this;
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            targetCompID.wrap(buffer);
            targetCompIDOffset = value.offset();
            targetCompIDLength = value.length();
        }
        return this;
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final char[] value)
    {
        return targetCompID(value, 0, value.length);
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final char[] value, final int length)
    {
        return targetCompID(value, 0, length);
    }

    /* TargetCompID = 56 */
    public HeaderEncoder targetCompID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, targetCompID, offset, length))
        {
            targetCompIDInternalBuffer = targetCompID.byteArray();
        }
        targetCompIDOffset = 0;
        targetCompIDLength = length;
        return this;
    }

    private int msgSeqNum;

    private boolean hasMsgSeqNum;

    public boolean hasMsgSeqNum()
    {
        return hasMsgSeqNum;
    }

    /* MsgSeqNum = 34 */
    public HeaderEncoder msgSeqNum(int value)
    {
        msgSeqNum = value;
        hasMsgSeqNum = true;
        return this;
    }

    /* MsgSeqNum = 34 */
    public int msgSeqNum()
    {
        return msgSeqNum;
    }

    private final MutableDirectBuffer senderSubID = new UnsafeBuffer();
    private byte[] senderSubIDInternalBuffer = senderSubID.byteArray();
    private int senderSubIDOffset = 0;
    private int senderSubIDLength = 0;

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final DirectBuffer value, final int offset, final int length)
    {
        senderSubID.wrap(value);
        senderSubIDOffset = offset;
        senderSubIDLength = length;
        return this;
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final DirectBuffer value, final int length)
    {
        return senderSubID(value, 0, length);
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final DirectBuffer value)
    {
        return senderSubID(value, 0, value.capacity());
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final byte[] value, final int offset, final int length)
    {
        senderSubID.wrap(value);
        senderSubIDOffset = offset;
        senderSubIDLength = length;
        return this;
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(senderSubID, value, offset, length))
        {
            senderSubIDInternalBuffer = senderSubID.byteArray();
        }
        senderSubIDOffset = 0;
        senderSubIDLength = length;
        return this;
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final byte[] value, final int length)
    {
        return senderSubID(value, 0, length);
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final byte[] value)
    {
        return senderSubID(value, 0, value.length);
    }

    /* SenderSubID = 50 */
    public boolean hasSenderSubID()
    {
        return senderSubIDLength > 0;
    }

    /* SenderSubID = 50 */
    public MutableDirectBuffer senderSubID()
    {
        return senderSubID;
    }

    /* SenderSubID = 50 */
    public String senderSubIDAsString()
    {
        return senderSubID.getStringWithoutLengthAscii(senderSubIDOffset, senderSubIDLength);
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final CharSequence value)
    {
        if (toBytes(value, senderSubID))
        {
            senderSubIDInternalBuffer = senderSubID.byteArray();
        }
        senderSubIDOffset = 0;
        senderSubIDLength = value.length();
        return this;
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            senderSubID.wrap(buffer);
            senderSubIDOffset = value.offset();
            senderSubIDLength = value.length();
        }
        return this;
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final char[] value)
    {
        return senderSubID(value, 0, value.length);
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final char[] value, final int length)
    {
        return senderSubID(value, 0, length);
    }

    /* SenderSubID = 50 */
    public HeaderEncoder senderSubID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, senderSubID, offset, length))
        {
            senderSubIDInternalBuffer = senderSubID.byteArray();
        }
        senderSubIDOffset = 0;
        senderSubIDLength = length;
        return this;
    }

    private final MutableDirectBuffer senderLocationID = new UnsafeBuffer();
    private byte[] senderLocationIDInternalBuffer = senderLocationID.byteArray();
    private int senderLocationIDOffset = 0;
    private int senderLocationIDLength = 0;

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final DirectBuffer value, final int offset, final int length)
    {
        senderLocationID.wrap(value);
        senderLocationIDOffset = offset;
        senderLocationIDLength = length;
        return this;
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final DirectBuffer value, final int length)
    {
        return senderLocationID(value, 0, length);
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final DirectBuffer value)
    {
        return senderLocationID(value, 0, value.capacity());
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final byte[] value, final int offset, final int length)
    {
        senderLocationID.wrap(value);
        senderLocationIDOffset = offset;
        senderLocationIDLength = length;
        return this;
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(senderLocationID, value, offset, length))
        {
            senderLocationIDInternalBuffer = senderLocationID.byteArray();
        }
        senderLocationIDOffset = 0;
        senderLocationIDLength = length;
        return this;
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final byte[] value, final int length)
    {
        return senderLocationID(value, 0, length);
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final byte[] value)
    {
        return senderLocationID(value, 0, value.length);
    }

    /* SenderLocationID = 142 */
    public boolean hasSenderLocationID()
    {
        return senderLocationIDLength > 0;
    }

    /* SenderLocationID = 142 */
    public MutableDirectBuffer senderLocationID()
    {
        return senderLocationID;
    }

    /* SenderLocationID = 142 */
    public String senderLocationIDAsString()
    {
        return senderLocationID.getStringWithoutLengthAscii(senderLocationIDOffset, senderLocationIDLength);
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final CharSequence value)
    {
        if (toBytes(value, senderLocationID))
        {
            senderLocationIDInternalBuffer = senderLocationID.byteArray();
        }
        senderLocationIDOffset = 0;
        senderLocationIDLength = value.length();
        return this;
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            senderLocationID.wrap(buffer);
            senderLocationIDOffset = value.offset();
            senderLocationIDLength = value.length();
        }
        return this;
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final char[] value)
    {
        return senderLocationID(value, 0, value.length);
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final char[] value, final int length)
    {
        return senderLocationID(value, 0, length);
    }

    /* SenderLocationID = 142 */
    public HeaderEncoder senderLocationID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, senderLocationID, offset, length))
        {
            senderLocationIDInternalBuffer = senderLocationID.byteArray();
        }
        senderLocationIDOffset = 0;
        senderLocationIDLength = length;
        return this;
    }

    private final MutableDirectBuffer targetSubID = new UnsafeBuffer();
    private byte[] targetSubIDInternalBuffer = targetSubID.byteArray();
    private int targetSubIDOffset = 0;
    private int targetSubIDLength = 0;

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final DirectBuffer value, final int offset, final int length)
    {
        targetSubID.wrap(value);
        targetSubIDOffset = offset;
        targetSubIDLength = length;
        return this;
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final DirectBuffer value, final int length)
    {
        return targetSubID(value, 0, length);
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final DirectBuffer value)
    {
        return targetSubID(value, 0, value.capacity());
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final byte[] value, final int offset, final int length)
    {
        targetSubID.wrap(value);
        targetSubIDOffset = offset;
        targetSubIDLength = length;
        return this;
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(targetSubID, value, offset, length))
        {
            targetSubIDInternalBuffer = targetSubID.byteArray();
        }
        targetSubIDOffset = 0;
        targetSubIDLength = length;
        return this;
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final byte[] value, final int length)
    {
        return targetSubID(value, 0, length);
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final byte[] value)
    {
        return targetSubID(value, 0, value.length);
    }

    /* TargetSubID = 57 */
    public boolean hasTargetSubID()
    {
        return targetSubIDLength > 0;
    }

    /* TargetSubID = 57 */
    public MutableDirectBuffer targetSubID()
    {
        return targetSubID;
    }

    /* TargetSubID = 57 */
    public String targetSubIDAsString()
    {
        return targetSubID.getStringWithoutLengthAscii(targetSubIDOffset, targetSubIDLength);
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final CharSequence value)
    {
        if (toBytes(value, targetSubID))
        {
            targetSubIDInternalBuffer = targetSubID.byteArray();
        }
        targetSubIDOffset = 0;
        targetSubIDLength = value.length();
        return this;
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            targetSubID.wrap(buffer);
            targetSubIDOffset = value.offset();
            targetSubIDLength = value.length();
        }
        return this;
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final char[] value)
    {
        return targetSubID(value, 0, value.length);
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final char[] value, final int length)
    {
        return targetSubID(value, 0, length);
    }

    /* TargetSubID = 57 */
    public HeaderEncoder targetSubID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, targetSubID, offset, length))
        {
            targetSubIDInternalBuffer = targetSubID.byteArray();
        }
        targetSubIDOffset = 0;
        targetSubIDLength = length;
        return this;
    }

    private final MutableDirectBuffer targetLocationID = new UnsafeBuffer();
    private byte[] targetLocationIDInternalBuffer = targetLocationID.byteArray();
    private int targetLocationIDOffset = 0;
    private int targetLocationIDLength = 0;

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final DirectBuffer value, final int offset, final int length)
    {
        targetLocationID.wrap(value);
        targetLocationIDOffset = offset;
        targetLocationIDLength = length;
        return this;
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final DirectBuffer value, final int length)
    {
        return targetLocationID(value, 0, length);
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final DirectBuffer value)
    {
        return targetLocationID(value, 0, value.capacity());
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final byte[] value, final int offset, final int length)
    {
        targetLocationID.wrap(value);
        targetLocationIDOffset = offset;
        targetLocationIDLength = length;
        return this;
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(targetLocationID, value, offset, length))
        {
            targetLocationIDInternalBuffer = targetLocationID.byteArray();
        }
        targetLocationIDOffset = 0;
        targetLocationIDLength = length;
        return this;
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final byte[] value, final int length)
    {
        return targetLocationID(value, 0, length);
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final byte[] value)
    {
        return targetLocationID(value, 0, value.length);
    }

    /* TargetLocationID = 143 */
    public boolean hasTargetLocationID()
    {
        return targetLocationIDLength > 0;
    }

    /* TargetLocationID = 143 */
    public MutableDirectBuffer targetLocationID()
    {
        return targetLocationID;
    }

    /* TargetLocationID = 143 */
    public String targetLocationIDAsString()
    {
        return targetLocationID.getStringWithoutLengthAscii(targetLocationIDOffset, targetLocationIDLength);
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final CharSequence value)
    {
        if (toBytes(value, targetLocationID))
        {
            targetLocationIDInternalBuffer = targetLocationID.byteArray();
        }
        targetLocationIDOffset = 0;
        targetLocationIDLength = value.length();
        return this;
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            targetLocationID.wrap(buffer);
            targetLocationIDOffset = value.offset();
            targetLocationIDLength = value.length();
        }
        return this;
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final char[] value)
    {
        return targetLocationID(value, 0, value.length);
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final char[] value, final int length)
    {
        return targetLocationID(value, 0, length);
    }

    /* TargetLocationID = 143 */
    public HeaderEncoder targetLocationID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, targetLocationID, offset, length))
        {
            targetLocationIDInternalBuffer = targetLocationID.byteArray();
        }
        targetLocationIDOffset = 0;
        targetLocationIDLength = length;
        return this;
    }

    private boolean possDupFlag;

    private boolean hasPossDupFlag;

    public boolean hasPossDupFlag()
    {
        return hasPossDupFlag;
    }

    /* PossDupFlag = 43 */
    public HeaderEncoder possDupFlag(boolean value)
    {
        possDupFlag = value;
        hasPossDupFlag = true;
        return this;
    }

    /* PossDupFlag = 43 */
    public boolean possDupFlag()
    {
        return possDupFlag;
    }

    private boolean possResend;

    private boolean hasPossResend;

    public boolean hasPossResend()
    {
        return hasPossResend;
    }

    /* PossResend = 97 */
    public HeaderEncoder possResend(boolean value)
    {
        possResend = value;
        hasPossResend = true;
        return this;
    }

    /* PossResend = 97 */
    public boolean possResend()
    {
        return possResend;
    }

    private final MutableDirectBuffer sendingTime = new UnsafeBuffer();
    private byte[] sendingTimeInternalBuffer = sendingTime.byteArray();
    private int sendingTimeOffset = 0;
    private int sendingTimeLength = 0;

    /* SendingTime = 52 */
    public HeaderEncoder sendingTime(final DirectBuffer value, final int offset, final int length)
    {
        sendingTime.wrap(value);
        sendingTimeOffset = offset;
        sendingTimeLength = length;
        return this;
    }

    /* SendingTime = 52 */
    public HeaderEncoder sendingTime(final DirectBuffer value, final int length)
    {
        return sendingTime(value, 0, length);
    }

    /* SendingTime = 52 */
    public HeaderEncoder sendingTime(final DirectBuffer value)
    {
        return sendingTime(value, 0, value.capacity());
    }

    /* SendingTime = 52 */
    public HeaderEncoder sendingTime(final byte[] value, final int offset, final int length)
    {
        sendingTime.wrap(value);
        sendingTimeOffset = offset;
        sendingTimeLength = length;
        return this;
    }

    /* SendingTime = 52 */
    public HeaderEncoder sendingTimeAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(sendingTime, value, offset, length))
        {
            sendingTimeInternalBuffer = sendingTime.byteArray();
        }
        sendingTimeOffset = 0;
        sendingTimeLength = length;
        return this;
    }

    /* SendingTime = 52 */
    public HeaderEncoder sendingTime(final byte[] value, final int length)
    {
        return sendingTime(value, 0, length);
    }

    /* SendingTime = 52 */
    public HeaderEncoder sendingTime(final byte[] value)
    {
        return sendingTime(value, 0, value.length);
    }

    /* SendingTime = 52 */
    public boolean hasSendingTime()
    {
        return sendingTimeLength > 0;
    }

    /* SendingTime = 52 */
    public MutableDirectBuffer sendingTime()
    {
        return sendingTime;
    }

    /* SendingTime = 52 */
    public String sendingTimeAsString()
    {
        return sendingTime.getStringWithoutLengthAscii(sendingTimeOffset, sendingTimeLength);
    }

    private final MutableDirectBuffer origSendingTime = new UnsafeBuffer();
    private byte[] origSendingTimeInternalBuffer = origSendingTime.byteArray();
    private int origSendingTimeOffset = 0;
    private int origSendingTimeLength = 0;

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTime(final DirectBuffer value, final int offset, final int length)
    {
        origSendingTime.wrap(value);
        origSendingTimeOffset = offset;
        origSendingTimeLength = length;
        return this;
    }

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTime(final DirectBuffer value, final int length)
    {
        return origSendingTime(value, 0, length);
    }

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTime(final DirectBuffer value)
    {
        return origSendingTime(value, 0, value.capacity());
    }

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTime(final byte[] value, final int offset, final int length)
    {
        origSendingTime.wrap(value);
        origSendingTimeOffset = offset;
        origSendingTimeLength = length;
        return this;
    }

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTimeAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(origSendingTime, value, offset, length))
        {
            origSendingTimeInternalBuffer = origSendingTime.byteArray();
        }
        origSendingTimeOffset = 0;
        origSendingTimeLength = length;
        return this;
    }

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTime(final byte[] value, final int length)
    {
        return origSendingTime(value, 0, length);
    }

    /* OrigSendingTime = 122 */
    public HeaderEncoder origSendingTime(final byte[] value)
    {
        return origSendingTime(value, 0, value.length);
    }

    /* OrigSendingTime = 122 */
    public boolean hasOrigSendingTime()
    {
        return origSendingTimeLength > 0;
    }

    /* OrigSendingTime = 122 */
    public MutableDirectBuffer origSendingTime()
    {
        return origSendingTime;
    }

    /* OrigSendingTime = 122 */
    public String origSendingTimeAsString()
    {
        return origSendingTime.getStringWithoutLengthAscii(origSendingTimeOffset, origSendingTimeLength);
    }

    private int lastMsgSeqNumProcessed;

    private boolean hasLastMsgSeqNumProcessed;

    public boolean hasLastMsgSeqNumProcessed()
    {
        return hasLastMsgSeqNumProcessed;
    }

    /* LastMsgSeqNumProcessed = 369 */
    public HeaderEncoder lastMsgSeqNumProcessed(int value)
    {
        lastMsgSeqNumProcessed = value;
        hasLastMsgSeqNumProcessed = true;
        return this;
    }

    /* LastMsgSeqNumProcessed = 369 */
    public int lastMsgSeqNumProcessed()
    {
        return lastMsgSeqNumProcessed;
    }

    int finishHeader(final MutableAsciiBuffer buffer, final int bodyStart, final int bodyLength)
    {
        int position = bodyStart - 1;

        buffer.putSeparator(position);
        position = buffer.putNaturalIntAsciiFromEnd(bodyLength, position);
        position -= bodyLengthHeaderLength;
        buffer.putBytes(position, bodyLengthHeader, 0, bodyLengthHeaderLength);

        if (beginStringLength > 0) {
        position--;
        buffer.putSeparator(position);
        position -= beginStringLength;
        buffer.putBytes(position, beginString, beginStringOffset, beginStringLength);
        position -= beginStringHeaderLength;
        buffer.putBytes(position, beginStringHeader, 0, beginStringHeaderLength);
        } else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: BeginString");
        }

        return position;
    }

    // 35=...| + other header fields
    public long startMessage(final MutableAsciiBuffer buffer, final int offset)
    {
        final int start = offset + beginStringLength + 16;
        int position = start;

        if (msgTypeLength > 0)
        {
            buffer.putBytes(position, msgTypeHeader, 0, msgTypeHeaderLength);
            position += msgTypeHeaderLength;
            buffer.putBytes(position, msgType, msgTypeOffset, msgTypeLength);
            position += msgTypeLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: MsgType");
        }

        if (senderCompIDLength > 0)
        {
            buffer.putBytes(position, senderCompIDHeader, 0, senderCompIDHeaderLength);
            position += senderCompIDHeaderLength;
            buffer.putBytes(position, senderCompID, senderCompIDOffset, senderCompIDLength);
            position += senderCompIDLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: SenderCompID");
        }

        if (targetCompIDLength > 0)
        {
            buffer.putBytes(position, targetCompIDHeader, 0, targetCompIDHeaderLength);
            position += targetCompIDHeaderLength;
            buffer.putBytes(position, targetCompID, targetCompIDOffset, targetCompIDLength);
            position += targetCompIDLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: TargetCompID");
        }

        if (hasMsgSeqNum)
        {
            buffer.putBytes(position, msgSeqNumHeader, 0, msgSeqNumHeaderLength);
            position += msgSeqNumHeaderLength;
            position += buffer.putIntAscii(position, msgSeqNum);
            buffer.putSeparator(position);
            position++;
        }

        if (senderSubIDLength > 0)
        {
            buffer.putBytes(position, senderSubIDHeader, 0, senderSubIDHeaderLength);
            position += senderSubIDHeaderLength;
            buffer.putBytes(position, senderSubID, senderSubIDOffset, senderSubIDLength);
            position += senderSubIDLength;
            buffer.putSeparator(position);
            position++;
        }

        if (senderLocationIDLength > 0)
        {
            buffer.putBytes(position, senderLocationIDHeader, 0, senderLocationIDHeaderLength);
            position += senderLocationIDHeaderLength;
            buffer.putBytes(position, senderLocationID, senderLocationIDOffset, senderLocationIDLength);
            position += senderLocationIDLength;
            buffer.putSeparator(position);
            position++;
        }

        if (targetSubIDLength > 0)
        {
            buffer.putBytes(position, targetSubIDHeader, 0, targetSubIDHeaderLength);
            position += targetSubIDHeaderLength;
            buffer.putBytes(position, targetSubID, targetSubIDOffset, targetSubIDLength);
            position += targetSubIDLength;
            buffer.putSeparator(position);
            position++;
        }

        if (targetLocationIDLength > 0)
        {
            buffer.putBytes(position, targetLocationIDHeader, 0, targetLocationIDHeaderLength);
            position += targetLocationIDHeaderLength;
            buffer.putBytes(position, targetLocationID, targetLocationIDOffset, targetLocationIDLength);
            position += targetLocationIDLength;
            buffer.putSeparator(position);
            position++;
        }

        if (hasPossDupFlag)
        {
            buffer.putBytes(position, possDupFlagHeader, 0, possDupFlagHeaderLength);
            position += possDupFlagHeaderLength;
            position += buffer.putBooleanAscii(position, possDupFlag);
            buffer.putSeparator(position);
            position++;
        }

        if (hasPossResend)
        {
            buffer.putBytes(position, possResendHeader, 0, possResendHeaderLength);
            position += possResendHeaderLength;
            position += buffer.putBooleanAscii(position, possResend);
            buffer.putSeparator(position);
            position++;
        }

        if (sendingTimeLength > 0)
        {
            buffer.putBytes(position, sendingTimeHeader, 0, sendingTimeHeaderLength);
            position += sendingTimeHeaderLength;
            buffer.putBytes(position, sendingTime, sendingTimeOffset, sendingTimeLength);
            position += sendingTimeLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: SendingTime");
        }

        if (origSendingTimeLength > 0)
        {
            buffer.putBytes(position, origSendingTimeHeader, 0, origSendingTimeHeaderLength);
            position += origSendingTimeHeaderLength;
            buffer.putBytes(position, origSendingTime, origSendingTimeOffset, origSendingTimeLength);
            position += origSendingTimeLength;
            buffer.putSeparator(position);
            position++;
        }

        if (hasLastMsgSeqNumProcessed)
        {
            buffer.putBytes(position, lastMsgSeqNumProcessedHeader, 0, lastMsgSeqNumProcessedHeaderLength);
            position += lastMsgSeqNumProcessedHeaderLength;
            position += buffer.putIntAscii(position, lastMsgSeqNumProcessed);
            buffer.putSeparator(position);
            position++;
        }

        return Encoder.result(position - start, start);
    }

    public void reset()
    {
        this.resetSenderCompID();
        this.resetTargetCompID();
        this.resetMsgSeqNum();
        this.resetSenderSubID();
        this.resetSenderLocationID();
        this.resetTargetSubID();
        this.resetTargetLocationID();
        this.resetPossDupFlag();
        this.resetPossResend();
        this.resetSendingTime();
        this.resetOrigSendingTime();
        this.resetLastMsgSeqNumProcessed();
        beginStringAsCopy(DEFAULT_BEGIN_STRING, 0, DEFAULT_BEGIN_STRING.length);
    }

    public void resetSenderCompID()
    {
        senderCompIDLength = 0;
        senderCompID.wrap(senderCompIDInternalBuffer);
    }

    public void resetTargetCompID()
    {
        targetCompIDLength = 0;
        targetCompID.wrap(targetCompIDInternalBuffer);
    }

    public void resetMsgSeqNum()
    {
        hasMsgSeqNum = false;
    }

    public void resetSenderSubID()
    {
        senderSubIDLength = 0;
        senderSubID.wrap(senderSubIDInternalBuffer);
    }

    public void resetSenderLocationID()
    {
        senderLocationIDLength = 0;
        senderLocationID.wrap(senderLocationIDInternalBuffer);
    }

    public void resetTargetSubID()
    {
        targetSubIDLength = 0;
        targetSubID.wrap(targetSubIDInternalBuffer);
    }

    public void resetTargetLocationID()
    {
        targetLocationIDLength = 0;
        targetLocationID.wrap(targetLocationIDInternalBuffer);
    }

    public void resetPossDupFlag()
    {
        hasPossDupFlag = false;
    }

    public void resetPossResend()
    {
        hasPossResend = false;
    }

    public void resetSendingTime()
    {
        sendingTimeLength = 0;
        sendingTime.wrap(sendingTimeInternalBuffer);
    }

    public void resetOrigSendingTime()
    {
        origSendingTimeLength = 0;
        origSendingTime.wrap(origSendingTimeInternalBuffer);
    }

    public void resetLastMsgSeqNumProcessed()
    {
        hasLastMsgSeqNumProcessed = false;
    }

    public String toString()
    {
        return appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        return appendTo(builder, 1);
    }

    public StringBuilder appendTo(final StringBuilder builder, final int level)
    {
        builder.append("{\n");        indent(builder, level);
        builder.append("\"MessageName\": \"Header\",\n");
        if (hasBeginString())
        {
            indent(builder, level);
            builder.append("\"BeginString\": \"");
            appendBuffer(builder, beginString, beginStringOffset, beginStringLength);
            builder.append("\",\n");
        }


        if (hasMsgType())
        {
            indent(builder, level);
            builder.append("\"MsgType\": \"");
            appendBuffer(builder, msgType, msgTypeOffset, msgTypeLength);
            builder.append("\",\n");
        }

        if (hasSenderCompID())
        {
            indent(builder, level);
            builder.append("\"SenderCompID\": \"");
            appendBuffer(builder, senderCompID, senderCompIDOffset, senderCompIDLength);
            builder.append("\",\n");
        }

        if (hasTargetCompID())
        {
            indent(builder, level);
            builder.append("\"TargetCompID\": \"");
            appendBuffer(builder, targetCompID, targetCompIDOffset, targetCompIDLength);
            builder.append("\",\n");
        }

        if (hasMsgSeqNum())
        {
            indent(builder, level);
            builder.append("\"MsgSeqNum\": \"");
            builder.append(msgSeqNum);
            builder.append("\",\n");
        }

        if (hasSenderSubID())
        {
            indent(builder, level);
            builder.append("\"SenderSubID\": \"");
            appendBuffer(builder, senderSubID, senderSubIDOffset, senderSubIDLength);
            builder.append("\",\n");
        }

        if (hasSenderLocationID())
        {
            indent(builder, level);
            builder.append("\"SenderLocationID\": \"");
            appendBuffer(builder, senderLocationID, senderLocationIDOffset, senderLocationIDLength);
            builder.append("\",\n");
        }

        if (hasTargetSubID())
        {
            indent(builder, level);
            builder.append("\"TargetSubID\": \"");
            appendBuffer(builder, targetSubID, targetSubIDOffset, targetSubIDLength);
            builder.append("\",\n");
        }

        if (hasTargetLocationID())
        {
            indent(builder, level);
            builder.append("\"TargetLocationID\": \"");
            appendBuffer(builder, targetLocationID, targetLocationIDOffset, targetLocationIDLength);
            builder.append("\",\n");
        }

        if (hasPossDupFlag())
        {
            indent(builder, level);
            builder.append("\"PossDupFlag\": \"");
            builder.append(possDupFlag);
            builder.append("\",\n");
        }

        if (hasPossResend())
        {
            indent(builder, level);
            builder.append("\"PossResend\": \"");
            builder.append(possResend);
            builder.append("\",\n");
        }

        if (hasSendingTime())
        {
            indent(builder, level);
            builder.append("\"SendingTime\": \"");
            appendBuffer(builder, sendingTime, sendingTimeOffset, sendingTimeLength);
            builder.append("\",\n");
        }

        if (hasOrigSendingTime())
        {
            indent(builder, level);
            builder.append("\"OrigSendingTime\": \"");
            appendBuffer(builder, origSendingTime, origSendingTimeOffset, origSendingTimeLength);
            builder.append("\",\n");
        }

        if (hasLastMsgSeqNumProcessed())
        {
            indent(builder, level);
            builder.append("\"LastMsgSeqNumProcessed\": \"");
            builder.append(lastMsgSeqNumProcessed);
            builder.append("\",\n");
        }
        indent(builder, level - 1);
        builder.append("}");
        return builder;
    }

    public HeaderEncoder copyTo(final Encoder encoder)
    {
        return copyTo((HeaderEncoder)encoder);
    }

    public HeaderEncoder copyTo(final HeaderEncoder encoder)
    {
        encoder.reset();
        if (hasBeginString())
        {
            encoder.beginStringAsCopy(beginString.byteArray(), 0, beginStringLength);
        }


        if (hasMsgType())
        {
            encoder.msgTypeAsCopy(msgType.byteArray(), 0, msgTypeLength);
        }

        if (hasSenderCompID())
        {
            encoder.senderCompIDAsCopy(senderCompID.byteArray(), 0, senderCompIDLength);
        }

        if (hasTargetCompID())
        {
            encoder.targetCompIDAsCopy(targetCompID.byteArray(), 0, targetCompIDLength);
        }

        if (hasMsgSeqNum())
        {
            encoder.msgSeqNum(this.msgSeqNum());
        }

        if (hasSenderSubID())
        {
            encoder.senderSubIDAsCopy(senderSubID.byteArray(), 0, senderSubIDLength);
        }

        if (hasSenderLocationID())
        {
            encoder.senderLocationIDAsCopy(senderLocationID.byteArray(), 0, senderLocationIDLength);
        }

        if (hasTargetSubID())
        {
            encoder.targetSubIDAsCopy(targetSubID.byteArray(), 0, targetSubIDLength);
        }

        if (hasTargetLocationID())
        {
            encoder.targetLocationIDAsCopy(targetLocationID.byteArray(), 0, targetLocationIDLength);
        }

        if (hasPossDupFlag())
        {
            encoder.possDupFlag(this.possDupFlag());
        }

        if (hasPossResend())
        {
            encoder.possResend(this.possResend());
        }

        if (hasSendingTime())
        {
            encoder.sendingTimeAsCopy(sendingTime.byteArray(), 0, sendingTimeLength);
        }

        if (hasOrigSendingTime())
        {
            encoder.origSendingTimeAsCopy(origSendingTime.byteArray(), 0, origSendingTimeLength);
        }

        if (hasLastMsgSeqNumProcessed())
        {
            encoder.lastMsgSeqNumProcessed(this.lastMsgSeqNumProcessed());
        }
        return encoder;
    }

}
