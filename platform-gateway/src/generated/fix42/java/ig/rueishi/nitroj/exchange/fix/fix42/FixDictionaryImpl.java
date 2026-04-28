/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix42;

import uk.co.real_logic.artio.dictionary.FixDictionary;
import uk.co.real_logic.artio.builder.AbstractLogonEncoder;
import ig.rueishi.nitroj.exchange.fix.fix42.builder.LogonEncoder;
import uk.co.real_logic.artio.builder.AbstractResendRequestEncoder;
import uk.co.real_logic.artio.builder.AbstractLogoutEncoder;
import uk.co.real_logic.artio.builder.AbstractHeartbeatEncoder;
import uk.co.real_logic.artio.builder.AbstractRejectEncoder;
import uk.co.real_logic.artio.builder.AbstractTestRequestEncoder;
import uk.co.real_logic.artio.builder.AbstractSequenceResetEncoder;
import uk.co.real_logic.artio.builder.AbstractBusinessMessageRejectEncoder;
import uk.co.real_logic.artio.builder.SessionHeaderEncoder;
import ig.rueishi.nitroj.exchange.fix.fix42.builder.HeaderEncoder;
import uk.co.real_logic.artio.decoder.AbstractLogonDecoder;
import ig.rueishi.nitroj.exchange.fix.fix42.decoder.LogonDecoder;
import uk.co.real_logic.artio.decoder.AbstractLogoutDecoder;
import uk.co.real_logic.artio.decoder.AbstractRejectDecoder;
import uk.co.real_logic.artio.decoder.AbstractTestRequestDecoder;
import uk.co.real_logic.artio.decoder.AbstractSequenceResetDecoder;
import uk.co.real_logic.artio.decoder.AbstractHeartbeatDecoder;
import uk.co.real_logic.artio.decoder.AbstractResendRequestDecoder;
import uk.co.real_logic.artio.decoder.AbstractUserRequestDecoder;
import uk.co.real_logic.artio.decoder.SessionHeaderDecoder;
import ig.rueishi.nitroj.exchange.fix.fix42.decoder.HeaderDecoder;
import uk.co.real_logic.artio.dictionary.Generated;

@Generated("uk.co.real_logic.artio")
public class FixDictionaryImpl implements FixDictionary
{
    public String beginString()
    {
        return "FIX.4.2";
    }

    public SessionHeaderDecoder makeHeaderDecoder()
    {
        return new HeaderDecoder();
    }

    public SessionHeaderEncoder makeHeaderEncoder()
    {
        return new HeaderEncoder();
    }

    public AbstractLogonEncoder makeLogonEncoder()
    {
        return new LogonEncoder();
    }

    public AbstractResendRequestEncoder makeResendRequestEncoder()
    {
        return null;
    }

    public AbstractLogoutEncoder makeLogoutEncoder()
    {
        return null;
    }

    public AbstractHeartbeatEncoder makeHeartbeatEncoder()
    {
        return null;
    }

    public AbstractRejectEncoder makeRejectEncoder()
    {
        return null;
    }

    public AbstractTestRequestEncoder makeTestRequestEncoder()
    {
        return null;
    }

    public AbstractSequenceResetEncoder makeSequenceResetEncoder()
    {
        return null;
    }

    public AbstractBusinessMessageRejectEncoder makeBusinessMessageRejectEncoder()
    {
        return null;
    }

    public AbstractLogonDecoder makeLogonDecoder()
    {
        return new LogonDecoder();
    }

    public AbstractLogoutDecoder makeLogoutDecoder()
    {
        return null;
    }

    public AbstractRejectDecoder makeRejectDecoder()
    {
        return null;
    }

    public AbstractTestRequestDecoder makeTestRequestDecoder()
    {
        return null;
    }

    public AbstractSequenceResetDecoder makeSequenceResetDecoder()
    {
        return null;
    }

    public AbstractHeartbeatDecoder makeHeartbeatDecoder()
    {
        return null;
    }

    public AbstractResendRequestDecoder makeResendRequestDecoder()
    {
        return null;
    }

    public AbstractUserRequestDecoder makeUserRequestDecoder()
    {
        return null;
    }

}
