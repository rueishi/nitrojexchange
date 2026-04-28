package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.RecoveryCompleteEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Aeron fragment parser for gateway egress order commands.
 *
 * <p>Responsibility: decode one Aeron fragment from cluster egress and dispatch
 * every SBE command contained in that fragment. Role in system: this is the
 * gateway-egress-thread bridge between Aeron/SBE transport and execution-side
 * services such as {@link ExecutionRouter} and the REST balance poller.
 * Relationships: generated SBE decoders are preallocated here, order commands
 * are forwarded to {@link ExecutionRouter}, balance requests go to the REST
 * poller hook, and recovery-complete events go to a venue-status hook. Lifecycle:
 * gateway wiring constructs one handler and passes it to the Aeron subscription
 * poll loop; Aeron calls {@link #onFragment(DirectBuffer, int, int, Header)} for
 * each egress fragment. Design intent: parse concatenated SBE messages in-place
 * without allocation or incomplete cursor advancement, because arb batches may
 * place multiple order commands into the same fragment.</p>
 */
public final class OrderCommandHandler implements FragmentHandler {
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final NewOrderCommandDecoder newOrderDecoder = new NewOrderCommandDecoder();
    private final CancelOrderCommandDecoder cancelDecoder = new CancelOrderCommandDecoder();
    private final ReplaceOrderCommandDecoder replaceDecoder = new ReplaceOrderCommandDecoder();
    private final OrderStatusQueryCommandDecoder statusQueryDecoder = new OrderStatusQueryCommandDecoder();
    private final BalanceQueryRequestDecoder balanceQueryDecoder = new BalanceQueryRequestDecoder();
    private final RecoveryCompleteEventDecoder recoveryDecoder = new RecoveryCompleteEventDecoder();
    private final ExecutionRouter executionRouter;
    private final BalanceQuerySink restPoller;
    private final RecoveryCompleteSink venueStatusHandler;
    private final Logger logger;

    /**
     * Creates a handler with no-op hooks for non-order messages.
     *
     * @param executionRouter router that turns decoded order commands into FIX actions
     */
    public OrderCommandHandler(final ExecutionRouter executionRouter) {
        this(executionRouter, balance -> { }, recovery -> { }, System.getLogger(OrderCommandHandler.class.getName()));
    }

    OrderCommandHandler(
        final ExecutionRouter executionRouter,
        final BalanceQuerySink restPoller,
        final RecoveryCompleteSink venueStatusHandler,
        final Logger logger) {
        this.executionRouter = Objects.requireNonNull(executionRouter, "executionRouter");
        this.restPoller = Objects.requireNonNull(restPoller, "restPoller");
        this.venueStatusHandler = Objects.requireNonNull(venueStatusHandler, "venueStatusHandler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Decodes and routes all SBE messages in a single Aeron fragment.
     *
     * <p>The cursor rule is correctness-sensitive: fixed-length
     * {@code NewOrderCommand} advances by header plus block length, while every
     * command with variable data advances by {@code decoder.encodedLength()}.
     * Unknown template IDs stop parsing because the handler cannot know whether
     * the unknown message has variable-length fields.</p>
     *
     * @param buffer Aeron fragment buffer
     * @param offset first byte of the fragment payload
     * @param length number of bytes in the fragment payload
     * @param header Aeron metadata header, unused by this parser
     */
    @Override
    public void onFragment(final DirectBuffer buffer, int offset, final int length, final Header header) {
        final int endOffset = offset + length;
        while (offset < endOffset) {
            headerDecoder.wrap(buffer, offset);
            final int templateId = headerDecoder.templateId();
            final int blockLength = headerDecoder.blockLength();
            final int version = headerDecoder.version();
            final int headerLength = headerDecoder.encodedLength();
            final int bodyOffset = offset + headerLength;

            switch (templateId) {
                case NewOrderCommandDecoder.TEMPLATE_ID -> {
                    newOrderDecoder.wrap(buffer, bodyOffset, blockLength, version);
                    executionRouter.routeNewOrder(newOrderDecoder);
                    offset += headerLength + blockLength;
                }
                case CancelOrderCommandDecoder.TEMPLATE_ID -> {
                    cancelDecoder.wrap(buffer, bodyOffset, blockLength, version);
                    executionRouter.routeCancel(cancelDecoder);
                    cancelDecoder.sbeSkip();
                    offset += headerLength + cancelDecoder.encodedLength();
                }
                case ReplaceOrderCommandDecoder.TEMPLATE_ID -> {
                    replaceDecoder.wrap(buffer, bodyOffset, blockLength, version);
                    executionRouter.routeReplace(replaceDecoder);
                    replaceDecoder.sbeSkip();
                    offset += headerLength + replaceDecoder.encodedLength();
                }
                case OrderStatusQueryCommandDecoder.TEMPLATE_ID -> {
                    statusQueryDecoder.wrap(buffer, bodyOffset, blockLength, version);
                    executionRouter.routeStatusQuery(statusQueryDecoder);
                    statusQueryDecoder.sbeSkip();
                    offset += headerLength + statusQueryDecoder.encodedLength();
                }
                case BalanceQueryRequestDecoder.TEMPLATE_ID -> {
                    balanceQueryDecoder.wrap(buffer, bodyOffset, blockLength, version);
                    restPoller.onBalanceQueryRequest(balanceQueryDecoder);
                    offset += headerLength + balanceQueryDecoder.encodedLength();
                }
                case RecoveryCompleteEventDecoder.TEMPLATE_ID -> {
                    recoveryDecoder.wrap(buffer, bodyOffset, blockLength, version);
                    venueStatusHandler.onRecoveryComplete(recoveryDecoder);
                    offset += headerLength + recoveryDecoder.encodedLength();
                }
                default -> {
                    logger.log(Level.WARNING, "Unknown egress templateId: {0} at cursor={1}; aborting fragment parse",
                        templateId, offset);
                    return;
                }
            }
        }
    }

    /**
     * Hook used by gateway wiring to enqueue balance requests for the REST poller.
     */
    @FunctionalInterface
    public interface BalanceQuerySink {
        void onBalanceQueryRequest(BalanceQueryRequestDecoder command);
    }

    /**
     * Hook used by gateway wiring to observe recovery-complete events without sending FIX.
     */
    @FunctionalInterface
    interface RecoveryCompleteSink {
        void onRecoveryComplete(RecoveryCompleteEventDecoder event);
    }
}
