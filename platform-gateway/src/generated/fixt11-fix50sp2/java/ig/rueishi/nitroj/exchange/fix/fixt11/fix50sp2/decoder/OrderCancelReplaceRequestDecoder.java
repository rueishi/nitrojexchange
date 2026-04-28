/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.decoder;

import org.agrona.AsciiNumberFormatException;
import uk.co.real_logic.artio.dictionary.Generated;
import org.agrona.MutableDirectBuffer;
import org.agrona.AsciiSequenceView;
import static uk.co.real_logic.artio.dictionary.generation.CodecUtil.*;
import static uk.co.real_logic.artio.dictionary.SessionConstants.*;
import uk.co.real_logic.artio.builder.Decoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.decoder.HeaderDecoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.decoder.TrailerDecoder;
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
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.*;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderCancelReplaceRequestEncoder;

@Generated("uk.co.real_logic.artio")
public class OrderCancelReplaceRequestDecoder extends CommonDecoderImpl implements MessageDecoder
{
    public final IntHashSet REQUIRED_FIELDS = new IntHashSet(16);

    {
        if (CODEC_VALIDATION_ENABLED)
        {
            REQUIRED_FIELDS.add(Constants.ORIG_CL_ORD_ID);
            REQUIRED_FIELDS.add(Constants.CL_ORD_ID);
            REQUIRED_FIELDS.add(Constants.HANDL_INST);
            REQUIRED_FIELDS.add(Constants.SYMBOL);
            REQUIRED_FIELDS.add(Constants.SIDE);
            REQUIRED_FIELDS.add(Constants.TRANSACT_TIME);
            REQUIRED_FIELDS.add(Constants.ORDER_QTY);
            REQUIRED_FIELDS.add(Constants.ORD_TYPE);
        }
    }

    private final IntHashSet alreadyVisitedFields = new IntHashSet(22);

    private final IntHashSet unknownFields = new IntHashSet(10);

    private final IntHashSet missingRequiredFields = new IntHashSet(16);

    public boolean validate()
    {
        if (rejectReason != Decoder.NO_ERROR)
        {
            return false;
        }
        final IntIterator missingFieldsIterator = missingRequiredFields.iterator();
        final IntIterator unknownFieldsIterator = unknownFields.iterator();
        if (CODEC_REJECT_UNKNOWN_FIELD_ENABLED && unknownFieldsIterator.hasNext())
        {
            invalidTagId = unknownFieldsIterator.nextValue();
            rejectReason = Constants.ALL_FIELDS.contains(invalidTagId) ? 2 : 0;
            return false;
        }
        if (!header.validate())
        {
            invalidTagId = header.invalidTagId();
            rejectReason = header.rejectReason();
            return false;
        }
        else if (!trailer.validate())
        {
            invalidTagId = trailer.invalidTagId();
            rejectReason = trailer.rejectReason();
            return false;
        }
        if (missingFieldsIterator.hasNext())
        {
            invalidTagId = missingFieldsIterator.nextValue();
            rejectReason = 1;
            return false;
        }
        if (CODEC_VALIDATION_ENABLED && handlInstLength > 1)
        {
            invalidTagId = 21;
            rejectReason = 5;
            return false;
        }

        if (CODEC_VALIDATION_ENABLED && sideLength > 1)
        {
            invalidTagId = 54;
            rejectReason = 5;
            return false;
        }

        if (CODEC_VALIDATION_ENABLED && ordTypeLength > 1)
        {
            invalidTagId = 40;
            rejectReason = 5;
            return false;
        }

        if (hasTimeInForce)
        {
        if (CODEC_VALIDATION_ENABLED && timeInForceLength > 1)
        {
            invalidTagId = 59;
            rejectReason = 5;
            return false;
        }
        }
        return true;
    }

    public static final long MESSAGE_TYPE = 71L;

    public static final String MESSAGE_TYPE_AS_STRING = "G";

    public static final char[] MESSAGE_TYPE_CHARS = MESSAGE_TYPE_AS_STRING.toCharArray();

    public static final byte[] MESSAGE_TYPE_BYTES = MESSAGE_TYPE_AS_STRING.getBytes(US_ASCII);

    public final IntHashSet messageFields = new IntHashSet(54);

    {
        messageFields.add(Constants.BEGIN_STRING);
        messageFields.add(Constants.BODY_LENGTH);
        messageFields.add(Constants.MSG_TYPE);
        messageFields.add(Constants.SENDER_COMP_ID);
        messageFields.add(Constants.TARGET_COMP_ID);
        messageFields.add(Constants.MSG_SEQ_NUM);
        messageFields.add(Constants.SENDER_SUB_ID);
        messageFields.add(Constants.SENDER_LOCATION_ID);
        messageFields.add(Constants.TARGET_SUB_ID);
        messageFields.add(Constants.TARGET_LOCATION_ID);
        messageFields.add(Constants.POSS_DUP_FLAG);
        messageFields.add(Constants.POSS_RESEND);
        messageFields.add(Constants.SENDING_TIME);
        messageFields.add(Constants.ORIG_SENDING_TIME);
        messageFields.add(Constants.LAST_MSG_SEQ_NUM_PROCESSED);
        messageFields.add(Constants.ORDER_ID);
        messageFields.add(Constants.ORIG_CL_ORD_ID);
        messageFields.add(Constants.CL_ORD_ID);
        messageFields.add(Constants.HANDL_INST);
        messageFields.add(Constants.SYMBOL);
        messageFields.add(Constants.SIDE);
        messageFields.add(Constants.TRANSACT_TIME);
        messageFields.add(Constants.ORDER_QTY);
        messageFields.add(Constants.ORD_TYPE);
        messageFields.add(Constants.PRICE);
        messageFields.add(Constants.TIME_IN_FORCE);
        messageFields.add(Constants.CHECK_SUM);
    }

    private final TrailerDecoder trailer = new TrailerDecoder();

    public TrailerDecoder trailer()
    {
        return trailer;
    }

    private final HeaderDecoder header = new HeaderDecoder(trailer);

    public HeaderDecoder header()
    {
        return header;
    }

    private char[] orderID = new char[1];

    private boolean hasOrderID;

    /* OrderID = 37 */
    public char[] orderID()
    {
        if (!hasOrderID)
        {
            throw new IllegalArgumentException("No value for optional field: OrderID");
        }

        return orderID;
    }

    public boolean hasOrderID()
    {
        return hasOrderID;
    }


    private int orderIDOffset;

    private int orderIDLength;

    /* OrderID = 37 */
    public int orderIDLength()
    {
        if (!hasOrderID)
        {
            throw new IllegalArgumentException("No value for optional field: OrderID");
        }

        return orderIDLength;
    }

    /* OrderID = 37 */
    public String orderIDAsString()
    {
        return hasOrderID ? new String(orderID, 0, orderIDLength) : null;
    }

    /* OrderID = 37 */
    public AsciiSequenceView orderID(final AsciiSequenceView view)
    {
        if (!hasOrderID)
        {
            throw new IllegalArgumentException("No value for optional field: OrderID");
        }

        return view.wrap(buffer, orderIDOffset, orderIDLength);
    }


    private final CharArrayWrapper orderIDWrapper = new CharArrayWrapper();
    private char[] origClOrdID = new char[1];

    /* OrigClOrdID = 41 */
    public char[] origClOrdID()
    {
        return origClOrdID;
    }


    private int origClOrdIDOffset;

    private int origClOrdIDLength;

    /* OrigClOrdID = 41 */
    public int origClOrdIDLength()
    {
        return origClOrdIDLength;
    }

    /* OrigClOrdID = 41 */
    public String origClOrdIDAsString()
    {
        return new String(origClOrdID, 0, origClOrdIDLength);
    }

    /* OrigClOrdID = 41 */
    public AsciiSequenceView origClOrdID(final AsciiSequenceView view)
    {
        return view.wrap(buffer, origClOrdIDOffset, origClOrdIDLength);
    }


    private final CharArrayWrapper origClOrdIDWrapper = new CharArrayWrapper();
    private char[] clOrdID = new char[1];

    /* ClOrdID = 11 */
    public char[] clOrdID()
    {
        return clOrdID;
    }


    private int clOrdIDOffset;

    private int clOrdIDLength;

    /* ClOrdID = 11 */
    public int clOrdIDLength()
    {
        return clOrdIDLength;
    }

    /* ClOrdID = 11 */
    public String clOrdIDAsString()
    {
        return new String(clOrdID, 0, clOrdIDLength);
    }

    /* ClOrdID = 11 */
    public AsciiSequenceView clOrdID(final AsciiSequenceView view)
    {
        return view.wrap(buffer, clOrdIDOffset, clOrdIDLength);
    }


    private final CharArrayWrapper clOrdIDWrapper = new CharArrayWrapper();
    private char handlInst = MISSING_CHAR;

    private int handlInstLength = 0;
    public int handlInstLength()    {
       return handlInstLength;
    }
    /* HandlInst = 21 */
    public char handlInst()
    {
        return handlInst;
    }



    private char[] symbol = new char[1];

    /* Symbol = 55 */
    public char[] symbol()
    {
        return symbol;
    }


    private int symbolOffset;

    private int symbolLength;

    /* Symbol = 55 */
    public int symbolLength()
    {
        return symbolLength;
    }

    /* Symbol = 55 */
    public String symbolAsString()
    {
        return new String(symbol, 0, symbolLength);
    }

    /* Symbol = 55 */
    public AsciiSequenceView symbol(final AsciiSequenceView view)
    {
        return view.wrap(buffer, symbolOffset, symbolLength);
    }


    private final CharArrayWrapper symbolWrapper = new CharArrayWrapper();
    private char side = MISSING_CHAR;

    private int sideLength = 0;
    public int sideLength()    {
       return sideLength;
    }
    /* Side = 54 */
    public char side()
    {
        return side;
    }



    private byte[] transactTime = new byte[24];

    /* TransactTime = 60 */
    public byte[] transactTime()
    {
        return transactTime;
    }


    private int transactTimeOffset;

    private int transactTimeLength;

    /* TransactTime = 60 */
    public int transactTimeLength()
    {
        return transactTimeLength;
    }

    /* TransactTime = 60 */
    public String transactTimeAsString()
    {
        return new String(transactTime, 0, transactTimeLength);
    }

    /* TransactTime = 60 */
    public AsciiSequenceView transactTime(final AsciiSequenceView view)
    {
        return view.wrap(buffer, transactTimeOffset, transactTimeLength);
    }


    private DecimalFloat orderQty = DecimalFloat.newNaNValue();

    /* OrderQty = 38 */
    public DecimalFloat orderQty()
    {
        return orderQty;
    }



    private char ordType = MISSING_CHAR;

    private int ordTypeLength = 0;
    public int ordTypeLength()    {
       return ordTypeLength;
    }
    /* OrdType = 40 */
    public char ordType()
    {
        return ordType;
    }



    private DecimalFloat price = DecimalFloat.newNaNValue();

    private boolean hasPrice;

    /* Price = 44 */
    public DecimalFloat price()
    {
        if (!hasPrice)
        {
            throw new IllegalArgumentException("No value for optional field: Price");
        }

        return price;
    }

    public boolean hasPrice()
    {
        return hasPrice;
    }



    private char timeInForce = MISSING_CHAR;

    private int timeInForceLength = 0;
    public int timeInForceLength()    {
       return timeInForceLength;
    }
    private boolean hasTimeInForce;

    /* TimeInForce = 59 */
    public char timeInForce()
    {
        if (!hasTimeInForce)
        {
            throw new IllegalArgumentException("No value for optional field: TimeInForce");
        }

        return timeInForce;
    }

    public boolean hasTimeInForce()
    {
        return hasTimeInForce;
    }



    public int decode(final AsciiBuffer buffer, final int offset, final int length)
    {
        // Decode OrderCancelReplaceRequest
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
        position += header.decode(buffer, position, length);
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
            case Constants.ORDER_ID:
                hasOrderID = true;
                orderID = buffer.getChars(orderID, valueOffset, valueLength);
                orderIDOffset = valueOffset;
                orderIDLength = valueLength;
                break;

            case Constants.ORIG_CL_ORD_ID:
                origClOrdID = buffer.getChars(origClOrdID, valueOffset, valueLength);
                origClOrdIDOffset = valueOffset;
                origClOrdIDLength = valueLength;
                break;

            case Constants.CL_ORD_ID:
                clOrdID = buffer.getChars(clOrdID, valueOffset, valueLength);
                clOrdIDOffset = valueOffset;
                clOrdIDLength = valueLength;
                break;

            case Constants.HANDL_INST:
                handlInst = buffer.getChar(valueOffset);
                handlInstLength = valueLength;
                break;

            case Constants.SYMBOL:
                symbol = buffer.getChars(symbol, valueOffset, valueLength);
                symbolOffset = valueOffset;
                symbolLength = valueLength;
                break;

            case Constants.SIDE:
                side = buffer.getChar(valueOffset);
                sideLength = valueLength;
                break;

            case Constants.TRANSACT_TIME:
                transactTime = buffer.getBytes(transactTime, valueOffset, valueLength);
                transactTimeOffset = valueOffset;
                transactTimeLength = valueLength;
                break;

            case Constants.ORDER_QTY:
                orderQty = getFloat(buffer, orderQty, valueOffset, valueLength, 38, CODEC_VALIDATION_ENABLED, decimalFloatOverflowHandler);
                break;

            case Constants.ORD_TYPE:
                ordType = buffer.getChar(valueOffset);
                ordTypeLength = valueLength;
                break;

            case Constants.PRICE:
                hasPrice = true;
                price = getFloat(buffer, price, valueOffset, valueLength, 44, CODEC_VALIDATION_ENABLED, decimalFloatOverflowHandler);
                break;

            case Constants.TIME_IN_FORCE:
                hasTimeInForce = true;
                timeInForce = buffer.getChar(valueOffset);
                timeInForceLength = valueLength;
                break;

            default:
                if (!CODEC_REJECT_UNKNOWN_FIELD_ENABLED)
                {
                    alreadyVisitedFields.remove(tag);
                }
                else
                {
                    if (!(trailer.REQUIRED_FIELDS.contains(tag)))
                    {
                        unknownFields.add(tag);
                    }
                }
                if (CODEC_REJECT_UNKNOWN_FIELD_ENABLED || (trailer.REQUIRED_FIELDS.contains(tag)))
                {
                    position += trailer.decode(buffer, position, end - position);
                    return position - offset;
                }

            }

            if (position < (endOfField + 1))
            {
                position = endOfField + 1;
            }
        }
        position += trailer.decode(buffer, position, end - position);
        return position - offset;
    }

    public void reset()
    {
        header.reset();
        trailer.reset();
        resetMessage();
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

    public void resetMessage()
    {
        this.resetOrderID();
        this.resetOrigClOrdID();
        this.resetClOrdID();
        this.resetHandlInst();
        this.resetSymbol();
        this.resetSide();
        this.resetTransactTime();
        this.resetOrderQty();
        this.resetOrdType();
        this.resetPrice();
        this.resetTimeInForce();
    }

    public void resetOrderID()
    {
        hasOrderID = false;
    }

    public void resetOrigClOrdID()
    {
        origClOrdIDOffset = 0;
        origClOrdIDLength = 0;
    }

    public void resetClOrdID()
    {
        clOrdIDOffset = 0;
        clOrdIDLength = 0;
    }

    public void resetHandlInst()
    {
        handlInst = MISSING_CHAR;
    }

    public void resetSymbol()
    {
        symbolOffset = 0;
        symbolLength = 0;
    }

    public void resetSide()
    {
        side = MISSING_CHAR;
    }

    public void resetTransactTime()
    {
        transactTimeOffset = 0;
        transactTimeLength = 0;
    }

    public void resetOrderQty()
    {
        orderQty.reset();
    }

    public void resetOrdType()
    {
        ordType = MISSING_CHAR;
    }

    public void resetPrice()
    {
        hasPrice = false;
    }

    public void resetTimeInForce()
    {
        hasTimeInForce = false;
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
        builder.append("\"MessageName\": \"OrderCancelReplaceRequest\",\n");
        builder.append("  \"header\": ");
        header.appendTo(builder, level + 1);
        builder.append("\n");
        if (hasOrderID())
        {
            indent(builder, level);
            builder.append("\"OrderID\": \"");
            builder.append(this.orderID(), 0, orderIDLength());
            builder.append("\",\n");
        }

        indent(builder, level);
        builder.append("\"OrigClOrdID\": \"");
        builder.append(this.origClOrdID(), 0, origClOrdIDLength());
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"ClOrdID\": \"");
        builder.append(this.clOrdID(), 0, clOrdIDLength());
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"HandlInst\": \"");
        builder.append(handlInst);
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"Symbol\": \"");
        builder.append(this.symbol(), 0, symbolLength());
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"Side\": \"");
        builder.append(side);
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"TransactTime\": \"");
        appendData(builder, transactTime, transactTimeLength);
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"OrderQty\": \"");
        orderQty.appendTo(builder);
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"OrdType\": \"");
        builder.append(ordType);
        builder.append("\",\n");

        if (hasPrice())
        {
            indent(builder, level);
            builder.append("\"Price\": \"");
            price.appendTo(builder);
            builder.append("\",\n");
        }

        if (hasTimeInForce())
        {
            indent(builder, level);
            builder.append("\"TimeInForce\": \"");
            builder.append(timeInForce);
            builder.append("\",\n");
        }
        indent(builder, level - 1);
        builder.append("}");
        return builder;
    }

    public OrderCancelReplaceRequestEncoder toEncoder(final Encoder encoder)
    {
        return toEncoder((OrderCancelReplaceRequestEncoder)encoder);
    }

    public OrderCancelReplaceRequestEncoder toEncoder(final OrderCancelReplaceRequestEncoder encoder)
    {
        encoder.reset();
        if (hasOrderID())
        {
            encoder.orderID(this.orderID(), 0, orderIDLength());
        }

        encoder.origClOrdID(this.origClOrdID(), 0, origClOrdIDLength());
        encoder.clOrdID(this.clOrdID(), 0, clOrdIDLength());
        encoder.handlInst(this.handlInst());
        encoder.symbol(this.symbol(), 0, symbolLength());
        encoder.side(this.side());
        encoder.transactTimeAsCopy(this.transactTime(), 0, transactTimeLength());
        encoder.orderQty(this.orderQty());
        encoder.ordType(this.ordType());
        if (hasPrice())
        {
            encoder.price(this.price());
        }

        if (hasTimeInForce())
        {
            encoder.timeInForce(this.timeInForce());
        }
        return encoder;
    }

}
