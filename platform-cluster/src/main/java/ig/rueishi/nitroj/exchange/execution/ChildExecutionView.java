package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;

import java.util.Objects;

/**
 * Reusable primitive view over a child execution event plus its parent ID.
 *
 * <p>`OrderState.parentOrderId` is the authoritative hot-path attribution
 * source. The execution engine supplies that primitive when wrapping the child
 * execution decoder, avoiding a heap lookup wrapper per execution report.</p>
 */
public final class ChildExecutionView {
    private ExecutionEventDecoder decoder;
    private long parentOrderId;

    public ChildExecutionView wrap(final ExecutionEventDecoder decoder, final long parentOrderId) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.parentOrderId = parentOrderId;
        return this;
    }

    public ExecutionEventDecoder decoder() {
        return decoder;
    }

    public long parentOrderId() {
        return parentOrderId;
    }

    public long childClOrdId() {
        return decoder.clOrdId();
    }

    public int venueId() {
        return decoder.venueId();
    }

    public int instrumentId() {
        return decoder.instrumentId();
    }

    public ExecType execType() {
        return decoder.execType();
    }

    public Side side() {
        return decoder.side();
    }

    public long fillPriceScaled() {
        return decoder.fillPriceScaled();
    }

    public long fillQtyScaled() {
        return decoder.fillQtyScaled();
    }

    public long cumQtyScaled() {
        return decoder.cumQtyScaled();
    }

    public long leavesQtyScaled() {
        return decoder.leavesQtyScaled();
    }

    public int rejectCode() {
        return decoder.rejectCode();
    }

    public boolean finalExecution() {
        return decoder.isFinal().value() == 1;
    }
}
