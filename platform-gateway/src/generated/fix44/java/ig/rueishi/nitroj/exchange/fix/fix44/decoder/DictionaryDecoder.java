/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix44.decoder;

import uk.co.real_logic.artio.util.AsciiBuffer;
import uk.co.real_logic.artio.dictionary.Generated;

@Generated("uk.co.real_logic.artio")
public final class DictionaryDecoder
{

    private final DictionaryAcceptor acceptor;

    private final LogonDecoder logon = new LogonDecoder();
    private final NewOrderSingleDecoder newOrderSingle = new NewOrderSingleDecoder();
    private final OrderCancelRequestDecoder orderCancelRequest = new OrderCancelRequestDecoder();
    private final OrderCancelReplaceRequestDecoder orderCancelReplaceRequest = new OrderCancelReplaceRequestDecoder();
    private final OrderStatusRequestDecoder orderStatusRequest = new OrderStatusRequestDecoder();

    public DictionaryDecoder(final DictionaryAcceptor acceptor)
    {
        this.acceptor = acceptor;
    }

    public void onMessage(
        final AsciiBuffer buffer,
        final int offset,
        final int length,
        final long messageType)
    {
        if (messageType == LogonDecoder.MESSAGE_TYPE)
        {
            logon.decode(buffer, offset, length);
            acceptor.onLogon(logon);
            logon.reset();
        }

        else if (messageType == NewOrderSingleDecoder.MESSAGE_TYPE)
        {
            newOrderSingle.decode(buffer, offset, length);
            acceptor.onNewOrderSingle(newOrderSingle);
            newOrderSingle.reset();
        }

        else if (messageType == OrderCancelRequestDecoder.MESSAGE_TYPE)
        {
            orderCancelRequest.decode(buffer, offset, length);
            acceptor.onOrderCancelRequest(orderCancelRequest);
            orderCancelRequest.reset();
        }

        else if (messageType == OrderCancelReplaceRequestDecoder.MESSAGE_TYPE)
        {
            orderCancelReplaceRequest.decode(buffer, offset, length);
            acceptor.onOrderCancelReplaceRequest(orderCancelReplaceRequest);
            orderCancelReplaceRequest.reset();
        }

        else if (messageType == OrderStatusRequestDecoder.MESSAGE_TYPE)
        {
            orderStatusRequest.decode(buffer, offset, length);
            acceptor.onOrderStatusRequest(orderStatusRequest);
            orderStatusRequest.reset();
        }

    }

}
