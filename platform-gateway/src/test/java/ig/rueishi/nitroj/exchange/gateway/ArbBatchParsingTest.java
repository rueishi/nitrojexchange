package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration-style fragment parsing coverage for TASK-013 arb batches.
 *
 * <p>Responsibility: verify that {@link OrderCommandHandler} walks an entire
 * Aeron fragment containing multiple concatenated SBE messages. Role in system:
 * this protects the gateway side of the arb dual-leg path, where the cluster can
 * publish both legs into one fragment. Relationships: the tests use real
 * generated SBE encoders and the production handler, with a recording
 * {@link ExecutionRouter} standing in for Artio routing. Lifecycle: each test
 * builds one synthetic fragment and invokes the handler exactly as Aeron would.
 * Design intent: catch cursor bugs, especially the fixed-length versus
 * variable-length advancement distinction that can silently drop later commands.</p>
 */
final class ArbBatchParsingTest {
    /** Verifies two new-order messages in one fragment produce two router calls. */
    @Test
    void singleFragment_twoNewOrderCommands_twoFIXSends_correctClOrdIds() {
        final RecordingRouter router = new RecordingRouter();
        final OrderCommandHandler handler = new OrderCommandHandler(router);
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        int cursor = OrderCommandHandlerTest.putNewOrder(buffer, 0, 3001L, Side.BUY, OrdType.LIMIT);
        cursor = OrderCommandHandlerTest.putNewOrder(buffer, cursor, 3002L, Side.SELL, OrdType.LIMIT);

        handler.onFragment(buffer, 0, cursor, null);

        assertThat(router.actions).containsExactly("D:3001", "D:3002");
    }

    /** Verifies a variable-length cancel followed by a fixed new order advances the cursor correctly. */
    @Test
    void singleFragment_cancelThenNew_cursorAdvancesCorrectly() {
        final RecordingRouter router = new RecordingRouter();
        final OrderCommandHandler handler = new OrderCommandHandler(router);
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final OrderCommandHandlerTest.EncodedMessage cancel =
            OrderCommandHandlerTest.cancelMessage(4001L, 3001L, "venue-order-abc");
        buffer.putBytes(0, cancel.buffer, 0, cancel.length);
        final int length = OrderCommandHandlerTest.putNewOrder(buffer, cancel.length, 4002L, Side.BUY, OrdType.LIMIT);

        handler.onFragment(buffer, 0, length, null);

        assertThat(router.actions).containsExactly("F:4001:venue-order-abc", "D:4002");
    }

    /** Verifies unknown template IDs stop parsing the fragment without throwing. */
    @Test
    void singleFragment_unknownTemplateId_fragmentProcessingStops_noException() {
        final RecordingRouter router = new RecordingRouter();
        final OrderCommandHandler handler = new OrderCommandHandler(router);
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MessageHeaderEncoder()
            .wrap(buffer, 0)
            .blockLength(0)
            .templateId(999)
            .schemaId(1)
            .version(1);

        assertThatCode(() -> handler.onFragment(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH, null))
            .doesNotThrowAnyException();
        assertThat(router.actions).isEmpty();
    }

    private static final class RecordingRouter implements ExecutionRouter {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void routeNewOrder(final NewOrderCommandDecoder command) {
            actions.add("D:" + command.clOrdId());
        }

        @Override
        public void routeCancel(final CancelOrderCommandDecoder command) {
            final byte[] venueOrderId = new byte[command.venueOrderIdLength()];
            command.getVenueOrderId(venueOrderId, 0, venueOrderId.length);
            actions.add("F:" + command.cancelClOrdId() + ":" + new String(venueOrderId));
        }

        @Override
        public void routeReplace(final ReplaceOrderCommandDecoder command) {
            actions.add("G:" + command.newClOrdId());
        }

        @Override
        public void routeStatusQuery(final OrderStatusQueryCommandDecoder command) {
            actions.add("H:" + command.clOrdId());
        }
    }
}
