package ig.rueishi.nitroj.exchange.gateway;

import com.lmax.disruptor.EventHandler;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import io.aeron.Publication;
import org.agrona.DirectBuffer;

import java.util.Objects;

/**
 * Single-consumer bridge from the gateway Disruptor to Aeron cluster ingress.
 *
 * <p>Responsibility: consume pre-encoded {@link GatewaySlot} payloads and offer
 * them to an Aeron {@link Publication}. Role in system: this class serializes
 * all gateway producer traffic onto one Aeron publication, preserving Aeron's
 * single-writer expectation while keeping FIX/REST producer threads independent.
 * Relationships: {@link GatewayDisruptor} owns slot lifecycle and counters,
 * SBE {@link MessageHeaderDecoder} identifies fill traffic, and Aeron performs
 * the actual IPC/network publication.</p>
 *
 * <p>Lifecycle: a gateway startup task wires one instance as the Disruptor event
 * handler. The same {@link MessageHeaderDecoder} is reused for every slot, and
 * slots are reset in a {@code finally} block so failures never leave stale
 * lengths or sequence numbers in reusable ring entries. Design intent: fills are
 * never dropped; all other traffic is lossy under sustained Aeron back-pressure
 * after a 10 microsecond spin budget.</p>
 */
public final class AeronPublisher implements EventHandler<GatewaySlot> {
    static final long NON_FILL_SPIN_BUDGET_NANOS = 10_000L;

    private final OfferFunction offerFunction;
    private final Runnable backPressureCounter;
    private final MessageHeaderDecoder headerPeek = new MessageHeaderDecoder();

    /**
     * Creates a publisher backed by a real Aeron publication.
     *
     * @param publication Aeron publication connected to cluster ingress
     * @param backPressureCounter callback that increments the gateway Aeron
     *                            back-pressure counter for dropped non-fill events
     */
    public AeronPublisher(final Publication publication, final Runnable backPressureCounter) {
        this((buffer, offset, length) -> publication.offer(buffer, offset, length), backPressureCounter);
    }

    /**
     * Creates a publisher with an injectable offer function for deterministic tests.
     *
     * @param offerFunction publication offer operation
     * @param backPressureCounter counter increment callback for non-fill drops
     */
    AeronPublisher(final OfferFunction offerFunction, final Runnable backPressureCounter) {
        this.offerFunction = Objects.requireNonNull(offerFunction, "offerFunction");
        this.backPressureCounter = Objects.requireNonNull(backPressureCounter, "backPressureCounter");
    }

    /**
     * Offers one gateway slot to Aeron using the task-card back-pressure policy.
     *
     * <p>Template id is peeked from the SBE header already encoded at offset zero.
     * {@link ExecutionEventDecoder#TEMPLATE_ID} is treated as lossless fill traffic
     * and spins indefinitely until Aeron accepts the message. All other templates
     * spin for at most 10 microseconds; if Aeron is still back-pressured, the event
     * is dropped and the supplied counter callback is invoked.</p>
     *
     * @param slot pre-encoded gateway slot
     * @param sequence Disruptor sequence number, not needed by the publication
     * @param endOfBatch true when this is the last event in the current Disruptor batch
     */
    @Override
    public void onEvent(final GatewaySlot slot, final long sequence, final boolean endOfBatch) {
        try {
            headerPeek.wrap(slot.buffer, 0);
            if (headerPeek.templateId() == ExecutionEventDecoder.TEMPLATE_ID) {
                offerFill(slot);
            } else {
                offerLossy(slot);
            }
        } finally {
            slot.reset();
        }
    }

    private void offerFill(final GatewaySlot slot) {
        long result;
        do {
            result = offerFunction.offer(slot.buffer, 0, slot.length);
            if (result < 0) {
                Thread.onSpinWait();
            }
        } while (result < 0);
    }

    private void offerLossy(final GatewaySlot slot) {
        final long spinStart = System.nanoTime();
        long result;
        do {
            result = offerFunction.offer(slot.buffer, 0, slot.length);
            if (result > 0) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() - spinStart < NON_FILL_SPIN_BUDGET_NANOS);

        if (result < 0) {
            backPressureCounter.run();
        }
    }

    @FunctionalInterface
    interface OfferFunction {
        long offer(DirectBuffer buffer, int offset, int length);
    }
}
