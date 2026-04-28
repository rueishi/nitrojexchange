/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix44.decoder;

import uk.co.real_logic.artio.util.MessageTypeEncoding;
import uk.co.real_logic.artio.decoder.SessionHeaderDecoder;
import org.agrona.AsciiNumberFormatException;
import uk.co.real_logic.artio.dictionary.Generated;
import org.agrona.MutableDirectBuffer;
import org.agrona.AsciiSequenceView;
import static uk.co.real_logic.artio.dictionary.generation.CodecUtil.*;
import static uk.co.real_logic.artio.dictionary.SessionConstants.*;
import uk.co.real_logic.artio.builder.Decoder;
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
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.builder.CommonDecoderImpl;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static uk.co.real_logic.artio.builder.Validation.CODEC_VALIDATION_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownField.CODEC_REJECT_UNKNOWN_FIELD_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownEnumValue.CODEC_REJECT_UNKNOWN_ENUM_VALUE_ENABLED;
import ig.rueishi.nitroj.exchange.fix.fix44.*;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.HeaderEncoder;

@Generated("uk.co.real_logic.artio")
public class HeaderDecoder extends CommonDecoderImpl implements SessionHeaderDecoder
{
    public final IntHashSet REQUIRED_FIELDS = new IntHashSet(14);

    {
        if (CODEC_VALIDATION_ENABLED)
        {
            REQUIRED_FIELDS.add(Constants.BEGIN_STRING);
            REQUIRED_FIELDS.add(Constants.BODY_LENGTH);
            REQUIRED_FIELDS.add(Constants.MSG_TYPE);
            REQUIRED_FIELDS.add(Constants.SENDER_COMP_ID);
            REQUIRED_FIELDS.add(Constants.TARGET_COMP_ID);
            REQUIRED_FIELDS.add(Constants.MSG_SEQ_NUM);
            REQUIRED_FIELDS.add(Constants.SENDING_TIME);
        }
    }

    private final IntHashSet alreadyVisitedFields = new IntHashSet(30);

    private final IntHashSet unknownFields = new IntHashSet(10);

    private final IntHashSet missingRequiredFields = new IntHashSet(14);

    public boolean validate()
    {
        if (rejectReason != Decoder.NO_ERROR)
        {
            return false;
        }
        final IntIterator missingFieldsIterator = missingRequiredFields.iterator();
        if (missingFieldsIterator.hasNext())
        {
            invalidTagId = missingFieldsIterator.nextValue();
            rejectReason = 1;
            return false;
        }
        if (hasPossDupFlag)
        {
        if (CODEC_VALIDATION_ENABLED && possDupFlagLength > 1)
        {
            invalidTagId = 43;
            rejectReason = 5;
            return false;
        }
        }

        if (hasPossResend)
        {
        if (CODEC_VALIDATION_ENABLED && possResendLength > 1)
        {
            invalidTagId = 97;
            rejectReason = 5;
            return false;
        }
        }
        return true;
    }

    public HeaderDecoder()
    {
        this(new TrailerDecoder());
    }

    private final TrailerDecoder trailer;
    public HeaderDecoder(final TrailerDecoder trailer)
    {
        this.trailer = trailer;
    }

    public long messageType()
    {
        return MessageTypeEncoding.packMessageType(msgType(), msgTypeLength());
    }

    private char[] beginString = new char[1];

    /* BeginString = 8 */
    public char[] beginString()
    {
        return beginString;
    }


    private int beginStringOffset;

    private int beginStringLength;

    /* BeginString = 8 */
    public int beginStringLength()
    {
        return beginStringLength;
    }

    /* BeginString = 8 */
    public String beginStringAsString()
    {
        return new String(beginString, 0, beginStringLength);
    }

    /* BeginString = 8 */
    public AsciiSequenceView beginString(final AsciiSequenceView view)
    {
        return view.wrap(buffer, beginStringOffset, beginStringLength);
    }


    private final CharArrayWrapper beginStringWrapper = new CharArrayWrapper();
    private int bodyLength = MISSING_INT;

    /* BodyLength = 9 */
    public int bodyLength()
    {
        return bodyLength;
    }



    private char[] msgType = new char[1];

    /* MsgType = 35 */
    public char[] msgType()
    {
        return msgType;
    }


    private int msgTypeOffset;

    private int msgTypeLength;

    /* MsgType = 35 */
    public int msgTypeLength()
    {
        return msgTypeLength;
    }

    /* MsgType = 35 */
    public String msgTypeAsString()
    {
        return new String(msgType, 0, msgTypeLength);
    }

    /* MsgType = 35 */
    public AsciiSequenceView msgType(final AsciiSequenceView view)
    {
        return view.wrap(buffer, msgTypeOffset, msgTypeLength);
    }


    private final CharArrayWrapper msgTypeWrapper = new CharArrayWrapper();
    private char[] senderCompID = new char[1];

    /* SenderCompID = 49 */
    public char[] senderCompID()
    {
        return senderCompID;
    }


    private int senderCompIDOffset;

    private int senderCompIDLength;

    /* SenderCompID = 49 */
    public int senderCompIDLength()
    {
        return senderCompIDLength;
    }

    /* SenderCompID = 49 */
    public String senderCompIDAsString()
    {
        return new String(senderCompID, 0, senderCompIDLength);
    }

    /* SenderCompID = 49 */
    public AsciiSequenceView senderCompID(final AsciiSequenceView view)
    {
        return view.wrap(buffer, senderCompIDOffset, senderCompIDLength);
    }


    private final CharArrayWrapper senderCompIDWrapper = new CharArrayWrapper();
    private char[] targetCompID = new char[1];

    /* TargetCompID = 56 */
    public char[] targetCompID()
    {
        return targetCompID;
    }


    private int targetCompIDOffset;

    private int targetCompIDLength;

    /* TargetCompID = 56 */
    public int targetCompIDLength()
    {
        return targetCompIDLength;
    }

    /* TargetCompID = 56 */
    public String targetCompIDAsString()
    {
        return new String(targetCompID, 0, targetCompIDLength);
    }

    /* TargetCompID = 56 */
    public AsciiSequenceView targetCompID(final AsciiSequenceView view)
    {
        return view.wrap(buffer, targetCompIDOffset, targetCompIDLength);
    }


    private final CharArrayWrapper targetCompIDWrapper = new CharArrayWrapper();
    private int msgSeqNum = MISSING_INT;

    /* MsgSeqNum = 34 */
    public int msgSeqNum()
    {
        return msgSeqNum;
    }



    private char[] senderSubID = new char[1];

    private boolean hasSenderSubID;

    /* SenderSubID = 50 */
    public char[] senderSubID()
    {
        if (!hasSenderSubID)
        {
            throw new IllegalArgumentException("No value for optional field: SenderSubID");
        }

        return senderSubID;
    }

    public boolean hasSenderSubID()
    {
        return hasSenderSubID;
    }


    private int senderSubIDOffset;

    private int senderSubIDLength;

    /* SenderSubID = 50 */
    public int senderSubIDLength()
    {
        if (!hasSenderSubID)
        {
            throw new IllegalArgumentException("No value for optional field: SenderSubID");
        }

        return senderSubIDLength;
    }

    /* SenderSubID = 50 */
    public String senderSubIDAsString()
    {
        return hasSenderSubID ? new String(senderSubID, 0, senderSubIDLength) : null;
    }

    /* SenderSubID = 50 */
    public AsciiSequenceView senderSubID(final AsciiSequenceView view)
    {
        if (!hasSenderSubID)
        {
            throw new IllegalArgumentException("No value for optional field: SenderSubID");
        }

        return view.wrap(buffer, senderSubIDOffset, senderSubIDLength);
    }


    private final CharArrayWrapper senderSubIDWrapper = new CharArrayWrapper();
    private char[] senderLocationID = new char[1];

    private boolean hasSenderLocationID;

    /* SenderLocationID = 142 */
    public char[] senderLocationID()
    {
        if (!hasSenderLocationID)
        {
            throw new IllegalArgumentException("No value for optional field: SenderLocationID");
        }

        return senderLocationID;
    }

    public boolean hasSenderLocationID()
    {
        return hasSenderLocationID;
    }


    private int senderLocationIDOffset;

    private int senderLocationIDLength;

    /* SenderLocationID = 142 */
    public int senderLocationIDLength()
    {
        if (!hasSenderLocationID)
        {
            throw new IllegalArgumentException("No value for optional field: SenderLocationID");
        }

        return senderLocationIDLength;
    }

    /* SenderLocationID = 142 */
    public String senderLocationIDAsString()
    {
        return hasSenderLocationID ? new String(senderLocationID, 0, senderLocationIDLength) : null;
    }

    /* SenderLocationID = 142 */
    public AsciiSequenceView senderLocationID(final AsciiSequenceView view)
    {
        if (!hasSenderLocationID)
        {
            throw new IllegalArgumentException("No value for optional field: SenderLocationID");
        }

        return view.wrap(buffer, senderLocationIDOffset, senderLocationIDLength);
    }


    private final CharArrayWrapper senderLocationIDWrapper = new CharArrayWrapper();
    private char[] targetSubID = new char[1];

    private boolean hasTargetSubID;

    /* TargetSubID = 57 */
    public char[] targetSubID()
    {
        if (!hasTargetSubID)
        {
            throw new IllegalArgumentException("No value for optional field: TargetSubID");
        }

        return targetSubID;
    }

    public boolean hasTargetSubID()
    {
        return hasTargetSubID;
    }


    private int targetSubIDOffset;

    private int targetSubIDLength;

    /* TargetSubID = 57 */
    public int targetSubIDLength()
    {
        if (!hasTargetSubID)
        {
            throw new IllegalArgumentException("No value for optional field: TargetSubID");
        }

        return targetSubIDLength;
    }

    /* TargetSubID = 57 */
    public String targetSubIDAsString()
    {
        return hasTargetSubID ? new String(targetSubID, 0, targetSubIDLength) : null;
    }

    /* TargetSubID = 57 */
    public AsciiSequenceView targetSubID(final AsciiSequenceView view)
    {
        if (!hasTargetSubID)
        {
            throw new IllegalArgumentException("No value for optional field: TargetSubID");
        }

        return view.wrap(buffer, targetSubIDOffset, targetSubIDLength);
    }


    private final CharArrayWrapper targetSubIDWrapper = new CharArrayWrapper();
    private char[] targetLocationID = new char[1];

    private boolean hasTargetLocationID;

    /* TargetLocationID = 143 */
    public char[] targetLocationID()
    {
        if (!hasTargetLocationID)
        {
            throw new IllegalArgumentException("No value for optional field: TargetLocationID");
        }

        return targetLocationID;
    }

    public boolean hasTargetLocationID()
    {
        return hasTargetLocationID;
    }


    private int targetLocationIDOffset;

    private int targetLocationIDLength;

    /* TargetLocationID = 143 */
    public int targetLocationIDLength()
    {
        if (!hasTargetLocationID)
        {
            throw new IllegalArgumentException("No value for optional field: TargetLocationID");
        }

        return targetLocationIDLength;
    }

    /* TargetLocationID = 143 */
    public String targetLocationIDAsString()
    {
        return hasTargetLocationID ? new String(targetLocationID, 0, targetLocationIDLength) : null;
    }

    /* TargetLocationID = 143 */
    public AsciiSequenceView targetLocationID(final AsciiSequenceView view)
    {
        if (!hasTargetLocationID)
        {
            throw new IllegalArgumentException("No value for optional field: TargetLocationID");
        }

        return view.wrap(buffer, targetLocationIDOffset, targetLocationIDLength);
    }


    private final CharArrayWrapper targetLocationIDWrapper = new CharArrayWrapper();
    private boolean possDupFlag;

    private int possDupFlagLength = 0;
    public int possDupFlagLength()    {
       return possDupFlagLength;
    }
    private boolean hasPossDupFlag;

    /* PossDupFlag = 43 */
    public boolean possDupFlag()
    {
        if (!hasPossDupFlag)
        {
            throw new IllegalArgumentException("No value for optional field: PossDupFlag");
        }

        return possDupFlag;
    }

    public boolean hasPossDupFlag()
    {
        return hasPossDupFlag;
    }



    private boolean possResend;

    private int possResendLength = 0;
    public int possResendLength()    {
       return possResendLength;
    }
    private boolean hasPossResend;

    /* PossResend = 97 */
    public boolean possResend()
    {
        if (!hasPossResend)
        {
            throw new IllegalArgumentException("No value for optional field: PossResend");
        }

        return possResend;
    }

    public boolean hasPossResend()
    {
        return hasPossResend;
    }



    private byte[] sendingTime = new byte[24];

    /* SendingTime = 52 */
    public byte[] sendingTime()
    {
        return sendingTime;
    }


    private int sendingTimeOffset;

    private int sendingTimeLength;

    /* SendingTime = 52 */
    public int sendingTimeLength()
    {
        return sendingTimeLength;
    }

    /* SendingTime = 52 */
    public String sendingTimeAsString()
    {
        return new String(sendingTime, 0, sendingTimeLength);
    }

    /* SendingTime = 52 */
    public AsciiSequenceView sendingTime(final AsciiSequenceView view)
    {
        return view.wrap(buffer, sendingTimeOffset, sendingTimeLength);
    }


    private byte[] origSendingTime = new byte[24];

    private boolean hasOrigSendingTime;

    /* OrigSendingTime = 122 */
    public byte[] origSendingTime()
    {
        if (!hasOrigSendingTime)
        {
            throw new IllegalArgumentException("No value for optional field: OrigSendingTime");
        }

        return origSendingTime;
    }

    public boolean hasOrigSendingTime()
    {
        return hasOrigSendingTime;
    }


    private int origSendingTimeOffset;

    private int origSendingTimeLength;

    /* OrigSendingTime = 122 */
    public int origSendingTimeLength()
    {
        if (!hasOrigSendingTime)
        {
            throw new IllegalArgumentException("No value for optional field: OrigSendingTime");
        }

        return origSendingTimeLength;
    }

    /* OrigSendingTime = 122 */
    public String origSendingTimeAsString()
    {
        return hasOrigSendingTime ? new String(origSendingTime, 0, origSendingTimeLength) : null;
    }

    /* OrigSendingTime = 122 */
    public AsciiSequenceView origSendingTime(final AsciiSequenceView view)
    {
        if (!hasOrigSendingTime)
        {
            throw new IllegalArgumentException("No value for optional field: OrigSendingTime");
        }

        return view.wrap(buffer, origSendingTimeOffset, origSendingTimeLength);
    }


    private int lastMsgSeqNumProcessed = MISSING_INT;

    private boolean hasLastMsgSeqNumProcessed;

    /* LastMsgSeqNumProcessed = 369 */
    public int lastMsgSeqNumProcessed()
    {
        if (!hasLastMsgSeqNumProcessed)
        {
            throw new IllegalArgumentException("No value for optional field: LastMsgSeqNumProcessed");
        }

        return lastMsgSeqNumProcessed;
    }

    public boolean hasLastMsgSeqNumProcessed()
    {
        return hasLastMsgSeqNumProcessed;
    }



    public int decode(final AsciiBuffer buffer, final int offset, final int length)
    {
        // Decode Header
        int seenFieldCount = 0;
        if (CODEC_VALIDATION_ENABLED)
        {
            missingRequiredFields.copy(REQUIRED_FIELDS);
            alreadyVisitedFields.clear();
        }
        this.buffer = buffer;
        final int end = offset + length;
        int position = offset;
        int positionIter = position;
        int tag;

        while (position < end)
        {
            final int equalsPosition = buffer.scan(position, end, '=');
            if (equalsPosition == AsciiBuffer.UNKNOWN_INDEX)
            {
               return position;
            }
            tag = buffer.getInt(position, equalsPosition);
            final int valueOffset = equalsPosition + 1;
            int endOfField = buffer.scan(valueOffset, end, START_OF_HEADER);
            if (endOfField == AsciiBuffer.UNKNOWN_INDEX)
            {
                rejectReason = 5;
                break;
            }
            final int valueLength = endOfField - valueOffset;
            if (CODEC_VALIDATION_ENABLED)
            {
                if (tag <= 0)
                {
                    invalidTagId = tag;
                    rejectReason = 0;
                }
                else if (valueLength == 0)
                {
                    invalidTagId = tag;
                    rejectReason = 4;
                }
                else if (seenFieldCount == 0 && tag != 8)
                {
                    invalidTagId = tag;
                    rejectReason = 14;
                }
                else if (seenFieldCount == 1 && tag != 9)
                {
                    invalidTagId = tag;
                    rejectReason = 14;
                }
                else if (seenFieldCount == 2 && tag != 35)
                {
                    invalidTagId = tag;
                    rejectReason = 14;
                }
                if (!alreadyVisitedFields.add(tag))
                {
                    invalidTagId = tag;
                    rejectReason = 13;
                }
                missingRequiredFields.remove(tag);
                seenFieldCount++;
            }

            switch (tag)
            {
            case Constants.BEGIN_STRING:
                beginString = buffer.getChars(beginString, valueOffset, valueLength);
                beginStringOffset = valueOffset;
                beginStringLength = valueLength;
                break;

            case Constants.BODY_LENGTH:
                bodyLength = getInt(buffer, valueOffset, endOfField, 9, CODEC_VALIDATION_ENABLED);
                break;

            case Constants.MSG_TYPE:
                msgType = buffer.getChars(msgType, valueOffset, valueLength);
                msgTypeOffset = valueOffset;
                msgTypeLength = valueLength;
                break;

            case Constants.SENDER_COMP_ID:
                senderCompID = buffer.getChars(senderCompID, valueOffset, valueLength);
                senderCompIDOffset = valueOffset;
                senderCompIDLength = valueLength;
                break;

            case Constants.TARGET_COMP_ID:
                targetCompID = buffer.getChars(targetCompID, valueOffset, valueLength);
                targetCompIDOffset = valueOffset;
                targetCompIDLength = valueLength;
                break;

            case Constants.MSG_SEQ_NUM:
                msgSeqNum = getInt(buffer, valueOffset, endOfField, 34, CODEC_VALIDATION_ENABLED);
                break;

            case Constants.SENDER_SUB_ID:
                hasSenderSubID = true;
                senderSubID = buffer.getChars(senderSubID, valueOffset, valueLength);
                senderSubIDOffset = valueOffset;
                senderSubIDLength = valueLength;
                break;

            case Constants.SENDER_LOCATION_ID:
                hasSenderLocationID = true;
                senderLocationID = buffer.getChars(senderLocationID, valueOffset, valueLength);
                senderLocationIDOffset = valueOffset;
                senderLocationIDLength = valueLength;
                break;

            case Constants.TARGET_SUB_ID:
                hasTargetSubID = true;
                targetSubID = buffer.getChars(targetSubID, valueOffset, valueLength);
                targetSubIDOffset = valueOffset;
                targetSubIDLength = valueLength;
                break;

            case Constants.TARGET_LOCATION_ID:
                hasTargetLocationID = true;
                targetLocationID = buffer.getChars(targetLocationID, valueOffset, valueLength);
                targetLocationIDOffset = valueOffset;
                targetLocationIDLength = valueLength;
                break;

            case Constants.POSS_DUP_FLAG:
                hasPossDupFlag = true;
                possDupFlag = buffer.getBoolean(valueOffset);
                possDupFlagLength = valueLength;
                break;

            case Constants.POSS_RESEND:
                hasPossResend = true;
                possResend = buffer.getBoolean(valueOffset);
                possResendLength = valueLength;
                break;

            case Constants.SENDING_TIME:
                sendingTime = buffer.getBytes(sendingTime, valueOffset, valueLength);
                sendingTimeOffset = valueOffset;
                sendingTimeLength = valueLength;
                break;

            case Constants.ORIG_SENDING_TIME:
                hasOrigSendingTime = true;
                origSendingTime = buffer.getBytes(origSendingTime, valueOffset, valueLength);
                origSendingTimeOffset = valueOffset;
                origSendingTimeLength = valueLength;
                break;

            case Constants.LAST_MSG_SEQ_NUM_PROCESSED:
                hasLastMsgSeqNumProcessed = true;
                lastMsgSeqNumProcessed = getInt(buffer, valueOffset, endOfField, 369, CODEC_VALIDATION_ENABLED);
                break;

            default:
                if (!CODEC_REJECT_UNKNOWN_FIELD_ENABLED)
                {
                    alreadyVisitedFields.remove(tag);
                }
                else
                {
                    if (!true)
                    {
                        unknownFields.add(tag);
                    }
                }
                if (CODEC_REJECT_UNKNOWN_FIELD_ENABLED || true)
                {
                    return position - offset;
                }

            }

            if (position < (endOfField + 1))
            {
                position = endOfField + 1;
            }
        }
        return position - offset;
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
        buffer = null;
        if (CODEC_VALIDATION_ENABLED)
        {
            invalidTagId = Decoder.NO_ERROR;
            rejectReason = Decoder.NO_ERROR;
            missingRequiredFields.clear();
            unknownFields.clear();
            alreadyVisitedFields.clear();
        }
    }

    public void resetSenderCompID()
    {
        senderCompIDOffset = 0;
        senderCompIDLength = 0;
    }

    public void resetTargetCompID()
    {
        targetCompIDOffset = 0;
        targetCompIDLength = 0;
    }

    public void resetMsgSeqNum()
    {
        msgSeqNum = MISSING_INT;
    }

    public void resetSenderSubID()
    {
        hasSenderSubID = false;
    }

    public void resetSenderLocationID()
    {
        hasSenderLocationID = false;
    }

    public void resetTargetSubID()
    {
        hasTargetSubID = false;
    }

    public void resetTargetLocationID()
    {
        hasTargetLocationID = false;
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
        sendingTimeOffset = 0;
        sendingTimeLength = 0;
    }

    public void resetOrigSendingTime()
    {
        hasOrigSendingTime = false;
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
        indent(builder, level);
        builder.append("\"BeginString\": \"");
        builder.append(this.beginString(), 0, beginStringLength());
        builder.append("\",\n");


        indent(builder, level);
        builder.append("\"MsgType\": \"");
        builder.append(this.msgType(), 0, msgTypeLength());
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"SenderCompID\": \"");
        builder.append(this.senderCompID(), 0, senderCompIDLength());
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"TargetCompID\": \"");
        builder.append(this.targetCompID(), 0, targetCompIDLength());
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"MsgSeqNum\": \"");
        builder.append(msgSeqNum);
        builder.append("\",\n");

        if (hasSenderSubID())
        {
            indent(builder, level);
            builder.append("\"SenderSubID\": \"");
            builder.append(this.senderSubID(), 0, senderSubIDLength());
            builder.append("\",\n");
        }

        if (hasSenderLocationID())
        {
            indent(builder, level);
            builder.append("\"SenderLocationID\": \"");
            builder.append(this.senderLocationID(), 0, senderLocationIDLength());
            builder.append("\",\n");
        }

        if (hasTargetSubID())
        {
            indent(builder, level);
            builder.append("\"TargetSubID\": \"");
            builder.append(this.targetSubID(), 0, targetSubIDLength());
            builder.append("\",\n");
        }

        if (hasTargetLocationID())
        {
            indent(builder, level);
            builder.append("\"TargetLocationID\": \"");
            builder.append(this.targetLocationID(), 0, targetLocationIDLength());
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

        indent(builder, level);
        builder.append("\"SendingTime\": \"");
        appendData(builder, sendingTime, sendingTimeLength);
        builder.append("\",\n");

        if (hasOrigSendingTime())
        {
            indent(builder, level);
            builder.append("\"OrigSendingTime\": \"");
            appendData(builder, origSendingTime, origSendingTimeLength);
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

    public HeaderEncoder toEncoder(final Encoder encoder)
    {
        return toEncoder((HeaderEncoder)encoder);
    }

    public HeaderEncoder toEncoder(final HeaderEncoder encoder)
    {
        encoder.reset();
        encoder.beginString(this.beginString(), 0, beginStringLength());

        encoder.msgType(this.msgType(), 0, msgTypeLength());
        encoder.senderCompID(this.senderCompID(), 0, senderCompIDLength());
        encoder.targetCompID(this.targetCompID(), 0, targetCompIDLength());
        encoder.msgSeqNum(this.msgSeqNum());
        if (hasSenderSubID())
        {
            encoder.senderSubID(this.senderSubID(), 0, senderSubIDLength());
        }

        if (hasSenderLocationID())
        {
            encoder.senderLocationID(this.senderLocationID(), 0, senderLocationIDLength());
        }

        if (hasTargetSubID())
        {
            encoder.targetSubID(this.targetSubID(), 0, targetSubIDLength());
        }

        if (hasTargetLocationID())
        {
            encoder.targetLocationID(this.targetLocationID(), 0, targetLocationIDLength());
        }

        if (hasPossDupFlag())
        {
            encoder.possDupFlag(this.possDupFlag());
        }

        if (hasPossResend())
        {
            encoder.possResend(this.possResend());
        }

        encoder.sendingTimeAsCopy(this.sendingTime(), 0, sendingTimeLength());
        if (hasOrigSendingTime())
        {
            encoder.origSendingTimeAsCopy(this.origSendingTime(), 0, origSendingTimeLength());
        }

        if (hasLastMsgSeqNumProcessed())
        {
            encoder.lastMsgSeqNumProcessed(this.lastMsgSeqNumProcessed());
        }
        return encoder;
    }

}
