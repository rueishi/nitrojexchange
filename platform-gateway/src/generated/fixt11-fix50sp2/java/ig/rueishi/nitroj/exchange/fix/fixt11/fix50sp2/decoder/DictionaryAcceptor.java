/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.decoder;

import uk.co.real_logic.artio.dictionary.Generated;

@Generated("uk.co.real_logic.artio")
public interface DictionaryAcceptor
{
    void onLogon(final LogonDecoder decoder);

    void onNewOrderSingle(final NewOrderSingleDecoder decoder);

    void onOrderCancelRequest(final OrderCancelRequestDecoder decoder);

    void onOrderCancelReplaceRequest(final OrderCancelReplaceRequestDecoder decoder);

    void onOrderStatusRequest(final OrderStatusRequestDecoder decoder);


}
