/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix44.builder;

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
import ig.rueishi.nitroj.exchange.fix.fix44.*;

@Generated("uk.co.real_logic.artio")
public class TrailerEncoder
{
    private static final int checkSumHeaderLength = 3;
    private static final byte[] checkSumHeader = new byte[] {49, 48, (byte) '='};

    private final MutableDirectBuffer checkSum = new UnsafeBuffer();
    private byte[] checkSumInternalBuffer = checkSum.byteArray();
    private int checkSumOffset = 0;
    private int checkSumLength = 0;

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final DirectBuffer value, final int offset, final int length)
    {
        checkSum.wrap(value);
        checkSumOffset = offset;
        checkSumLength = length;
        return this;
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final DirectBuffer value, final int length)
    {
        return checkSum(value, 0, length);
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final DirectBuffer value)
    {
        return checkSum(value, 0, value.capacity());
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final byte[] value, final int offset, final int length)
    {
        checkSum.wrap(value);
        checkSumOffset = offset;
        checkSumLength = length;
        return this;
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSumAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(checkSum, value, offset, length))
        {
            checkSumInternalBuffer = checkSum.byteArray();
        }
        checkSumOffset = 0;
        checkSumLength = length;
        return this;
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final byte[] value, final int length)
    {
        return checkSum(value, 0, length);
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final byte[] value)
    {
        return checkSum(value, 0, value.length);
    }

    /* CheckSum = 10 */
    public boolean hasCheckSum()
    {
        return checkSumLength > 0;
    }

    /* CheckSum = 10 */
    public MutableDirectBuffer checkSum()
    {
        return checkSum;
    }

    /* CheckSum = 10 */
    public String checkSumAsString()
    {
        return checkSum.getStringWithoutLengthAscii(checkSumOffset, checkSumLength);
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final CharSequence value)
    {
        if (toBytes(value, checkSum))
        {
            checkSumInternalBuffer = checkSum.byteArray();
        }
        checkSumOffset = 0;
        checkSumLength = value.length();
        return this;
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            checkSum.wrap(buffer);
            checkSumOffset = value.offset();
            checkSumLength = value.length();
        }
        return this;
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final char[] value)
    {
        return checkSum(value, 0, value.length);
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final char[] value, final int length)
    {
        return checkSum(value, 0, length);
    }

    /* CheckSum = 10 */
    public TrailerEncoder checkSum(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, checkSum, offset, length))
        {
            checkSumInternalBuffer = checkSum.byteArray();
        }
        checkSumOffset = 0;
        checkSumLength = length;
        return this;
    }

    long finishMessage(final MutableAsciiBuffer buffer, final int messageStart, final int offset)
    {
        int position = offset;

        final int checkSum = buffer.computeChecksum(messageStart, position);
        buffer.putBytes(position, checkSumHeader, 0, checkSumHeaderLength);
        position += checkSumHeaderLength;
        buffer.putNaturalPaddedIntAscii(position, 3, checkSum);
        position += 3;
        buffer.putSeparator(position);
        position++;

        return Encoder.result(position - messageStart, messageStart);
    }
    int startTrailer(final MutableAsciiBuffer buffer, final int offset)
    {
        final int start = offset;
        int position = start;

        return position - start;
    }

    public void reset()
    {
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
        builder.append("\"MessageName\": \"Trailer\",\n");
        if (hasCheckSum())
        {
            indent(builder, level);
            builder.append("\"CheckSum\": \"");
            appendBuffer(builder, checkSum, checkSumOffset, checkSumLength);
            builder.append("\",\n");
        }
        indent(builder, level - 1);
        builder.append("}");
        return builder;
    }

    public TrailerEncoder copyTo(final Encoder encoder)
    {
        return copyTo((TrailerEncoder)encoder);
    }

    public TrailerEncoder copyTo(final TrailerEncoder encoder)
    {
        encoder.reset();
        if (hasCheckSum())
        {
            encoder.checkSumAsCopy(checkSum.byteArray(), 0, checkSumLength);
        }
        return encoder;
    }

}
