package ig.rueishi.nitroj.exchange.test;

import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.ExpandableArrayBuffer;

/**
 * Shared test-only helpers for encoding small SBE fixtures without repeating boilerplate.
 */
public final class SbeTestMessageBuilder {
    private SbeTestMessageBuilder() {
    }

    public static ExpandableArrayBuffer encodeMarketData(
        final int venueId,
        final int instrumentId,
        final EntryType entryType,
        final UpdateAction updateAction,
        final long priceScaled,
        final long sizeScaled,
        final int priceLevel,
        final long ingressTimestampNanos,
        final long exchangeTimestampNanos,
        final int fixSeqNum
    ) {
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(128);
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        final MarketDataEventEncoder encoder = new MarketDataEventEncoder();

        encoder.wrapAndApplyHeader(buffer, 0, header)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .entryType(entryType)
            .updateAction(updateAction)
            .priceScaled(priceScaled)
            .sizeScaled(sizeScaled)
            .priceLevel(priceLevel)
            .ingressTimestampNanos(ingressTimestampNanos)
            .exchangeTimestampNanos(exchangeTimestampNanos)
            .fixSeqNum(fixSeqNum);

        return buffer;
    }

    public static ExpandableArrayBuffer encodeExecution(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final ExecType execType,
        final Side side,
        final long fillPriceScaled,
        final long fillQtyScaled,
        final long cumQtyScaled,
        final long leavesQtyScaled,
        final int rejectCode,
        final long ingressTimestampNanos,
        final long exchangeTimestampNanos,
        final int fixSeqNum,
        final boolean isFinal,
        final byte[] venueOrderId,
        final byte[] execId
    ) {
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(256);
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        final ExecutionEventEncoder encoder = new ExecutionEventEncoder();

        encoder.wrapAndApplyHeader(buffer, 0, header)
            .clOrdId(clOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .execType(execType)
            .side(side)
            .fillPriceScaled(fillPriceScaled)
            .fillQtyScaled(fillQtyScaled)
            .cumQtyScaled(cumQtyScaled)
            .leavesQtyScaled(leavesQtyScaled)
            .rejectCode(rejectCode)
            .ingressTimestampNanos(ingressTimestampNanos)
            .exchangeTimestampNanos(exchangeTimestampNanos)
            .fixSeqNum(fixSeqNum)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execId, 0, execId.length);

        return buffer;
    }
}
