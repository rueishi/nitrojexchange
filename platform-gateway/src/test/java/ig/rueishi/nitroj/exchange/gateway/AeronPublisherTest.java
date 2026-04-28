package ig.rueishi.nitroj.exchange.gateway;

import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import io.aeron.Publication;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for TASK-012 Aeron publication policy.
 *
 * <p>Responsibility: verify {@link AeronPublisher}'s priority-aware handling of
 * pre-encoded gateway slots. Role in system: these tests protect the final
 * gateway handoff before cluster ingress, where fill events must be lossless but
 * stale non-fill events may be dropped under Aeron back-pressure. Relationships:
 * generated SBE encoders create realistic message headers, and a deterministic
 * offer function simulates Aeron return codes without starting a media driver.</p>
 */
final class AeronPublisherTest {
    private static final byte[] VENUE_ORDER_ID = {'v'};
    private static final byte[] EXEC_ID = {'e'};

    /** Verifies fill events spin through back-pressure until accepted and do not increment drops. */
    @Test
    void fillMessage_aeronSlow_spinsIndefinitely_noDrops() throws Exception {
        final GatewaySlot slot = executionSlot();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger drops = new AtomicInteger();
        final AeronPublisher publisher = new AeronPublisher((buffer, offset, length) ->
            attempts.incrementAndGet() < 3 ? Publication.BACK_PRESSURED : 42L, drops::incrementAndGet);

        publisher.onEvent(slot, 1L, true);

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(drops.get()).isZero();
    }

    /** Verifies non-fill events are dropped after the 10 microsecond spin budget. */
    @Test
    void nonFillMessage_aeronSlow_droppedAfter10us_counterIncremented() throws Exception {
        final GatewaySlot slot = marketDataSlot();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger drops = new AtomicInteger();
        final AeronPublisher publisher = new AeronPublisher((buffer, offset, length) -> {
            attempts.incrementAndGet();
            return Publication.BACK_PRESSURED;
        }, drops::incrementAndGet);

        publisher.onEvent(slot, 1L, true);

        assertThat(attempts.get()).isPositive();
        assertThat(drops.get()).isEqualTo(1);
    }

    /** Verifies accepted non-fill events return without incrementing the drop counter. */
    @Test
    void nonFillMessage_aeronAccepts_counterNotIncremented() throws Exception {
        final GatewaySlot slot = marketDataSlot();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger drops = new AtomicInteger();
        final AeronPublisher publisher = new AeronPublisher((buffer, offset, length) -> {
            attempts.incrementAndGet();
            return 42L;
        }, drops::incrementAndGet);

        publisher.onEvent(slot, 1L, true);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(drops.get()).isZero();
    }

    /** Verifies slots are reset even when Aeron never accepts a non-fill message. */
    @Test
    void slotReset_calledAfterEveryEvent_evenOnFailure() throws Exception {
        final GatewaySlot slot = marketDataSlot();
        slot.sequence = 99L;
        final AeronPublisher publisher = new AeronPublisher(
            (buffer, offset, length) -> Publication.CLOSED,
            () -> { });

        publisher.onEvent(slot, 99L, true);

        assertThat(slot.length).isZero();
        assertThat(slot.sequence).isEqualTo(-1L);
    }

    private static GatewaySlot marketDataSlot() {
        final GatewaySlot slot = new GatewaySlot();
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        final MarketDataEventEncoder encoder = new MarketDataEventEncoder();
        encoder.wrapAndApplyHeader(slot.buffer, 0, header)
            .venueId(1)
            .instrumentId(1)
            .entryType(EntryType.BID)
            .updateAction(UpdateAction.NEW)
            .priceScaled(1)
            .sizeScaled(1)
            .priceLevel(1)
            .ingressTimestampNanos(1)
            .exchangeTimestampNanos(1)
            .fixSeqNum(1);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
        return slot;
    }

    private static GatewaySlot executionSlot() {
        final GatewaySlot slot = new GatewaySlot();
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        final ExecutionEventEncoder encoder = new ExecutionEventEncoder();
        encoder.wrapAndApplyHeader(slot.buffer, 0, header)
            .clOrdId(1)
            .venueId(1)
            .instrumentId(1)
            .execType(ExecType.FILL)
            .side(Side.BUY)
            .fillPriceScaled(1)
            .fillQtyScaled(1)
            .cumQtyScaled(1)
            .leavesQtyScaled(0)
            .rejectCode(0)
            .ingressTimestampNanos(1)
            .exchangeTimestampNanos(1)
            .fixSeqNum(1)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(VENUE_ORDER_ID, 0, VENUE_ORDER_ID.length)
            .putExecId(EXEC_ID, 0, EXEC_ID.length);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
        return slot;
    }
}
