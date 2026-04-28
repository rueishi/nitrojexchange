/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix42.decoder;

import uk.co.real_logic.artio.dictionary.Generated;

@Generated("uk.co.real_logic.artio")
public class DefaultDictionaryAcceptor implements DictionaryAcceptor
{
    @Override
    public void onLogon(final LogonDecoder decoder) {}

    @Override
    public void onNewOrderSingle(final NewOrderSingleDecoder decoder) {}

    @Override
    public void onOrderCancelRequest(final OrderCancelRequestDecoder decoder) {}

    @Override
    public void onOrderCancelReplaceRequest(final OrderCancelReplaceRequestDecoder decoder) {}

    @Override
    public void onOrderStatusRequest(final OrderStatusRequestDecoder decoder) {}


}
