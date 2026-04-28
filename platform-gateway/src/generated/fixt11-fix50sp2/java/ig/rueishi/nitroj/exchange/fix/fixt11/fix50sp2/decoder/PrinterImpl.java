/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.decoder;

import uk.co.real_logic.artio.builder.Printer;
import uk.co.real_logic.artio.util.AsciiBuffer;
import uk.co.real_logic.artio.dictionary.Generated;

@Generated("uk.co.real_logic.artio")
public class PrinterImpl implements Printer
{

    private final LogonDecoder logon = new LogonDecoder();
    private final NewOrderSingleDecoder newOrderSingle = new NewOrderSingleDecoder();
    private final OrderCancelRequestDecoder orderCancelRequest = new OrderCancelRequestDecoder();
    private final OrderCancelReplaceRequestDecoder orderCancelReplaceRequest = new OrderCancelReplaceRequestDecoder();
    private final OrderStatusRequestDecoder orderStatusRequest = new OrderStatusRequestDecoder();

    public String toString(
        final AsciiBuffer input,
        final int offset,
        final int length,
        final long messageType)
    {
            if (messageType == 65L)
            {
                logon.decode(input, offset, length);
                return logon.toString();
            }

            if (messageType == 68L)
            {
                newOrderSingle.decode(input, offset, length);
                return newOrderSingle.toString();
            }

            if (messageType == 70L)
            {
                orderCancelRequest.decode(input, offset, length);
                return orderCancelRequest.toString();
            }

            if (messageType == 71L)
            {
                orderCancelReplaceRequest.decode(input, offset, length);
                return orderCancelReplaceRequest.toString();
            }

            if (messageType == 72L)
            {
                orderStatusRequest.decode(input, offset, length);
                return orderStatusRequest.toString();
            }

            else
            {
                throw new IllegalArgumentException("Unknown Message Type: " + messageType);
            }
    }

}
