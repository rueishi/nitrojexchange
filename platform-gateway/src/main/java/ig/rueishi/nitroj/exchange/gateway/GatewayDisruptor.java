package ig.rueishi.nitroj.exchange.gateway;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import ig.rueishi.nitroj.exchange.common.DisruptorConfig;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-producer, single-consumer ring buffer for gateway ingress handoff.
 *
 * <p>Responsibility: serializes events produced by gateway threads onto the
 * gateway-disruptor consumer thread. Role in system: Artio-library and REST
 * producer paths claim preallocated {@link GatewaySlot} instances, encode payloads,
 * and publish them for later Aeron publishing. Relationships: downstream
 * {@code AeronPublisher} is TASK-012 and is represented here by an injected
 * {@link EventHandler}; counters are allocated with Agrona so later monitoring can
 * expose ring-full and Aeron-back-pressure events. Lifecycle: created during
 * gateway startup, started before producers publish, stopped during shutdown.
 * Design intent: preserve the AMB-008 policy that ring-full market-data producers
 * can see a null claim and drop, while the ring itself remains allocation-free on
 * the successful claim/publish path.
 */
public final class GatewayDisruptor implements AutoCloseable {
    public static final String DISRUPTOR_FULL_COUNTER_LABEL = "DISRUPTOR_FULL_COUNTER";
    public static final String AERON_BACKPRESSURE_COUNTER_LABEL = "AERON_BACKPRESSURE_COUNTER";

    private final Disruptor<GatewaySlot> disruptor;
    private final RingBuffer<GatewaySlot> ringBuffer;
    private final AtomicCounter disruptorFullCounter;
    private final AtomicCounter aeronBackPressureCounter;
    private final AtomicInteger threadCounter = new AtomicInteger();

    /**
     * Creates the gateway ring buffer from TASK-004 disruptor config.
     *
     * @param config ring size and slot-size config
     * @param countersManager Agrona counter allocator
     * @param consumer downstream consumer invoked by the gateway-disruptor thread
     */
    public GatewayDisruptor(
        final DisruptorConfig config,
        final CountersManager countersManager,
        final EventHandler<GatewaySlot> consumer
    ) {
        this(config.ringBufferSize(), config.slotSizeBytes(), countersManager, consumer);
    }

    /**
     * Creates the gateway ring buffer with explicit sizing for tests and startup wiring.
     *
     * @param ringBufferSize power-of-two Disruptor ring size
     * @param slotSizeBytes bytes allocated for each slot payload buffer
     * @param countersManager Agrona counter allocator
     * @param consumer downstream consumer invoked for each published slot
     */
    public GatewayDisruptor(
        final int ringBufferSize,
        final int slotSizeBytes,
        final CountersManager countersManager,
        final EventHandler<GatewaySlot> consumer
    ) {
        validatePowerOfTwo(ringBufferSize);
        Objects.requireNonNull(countersManager, "countersManager");
        Objects.requireNonNull(consumer, "consumer");

        disruptorFullCounter = countersManager.newCounter(DISRUPTOR_FULL_COUNTER_LABEL);
        aeronBackPressureCounter = countersManager.newCounter(AERON_BACKPRESSURE_COUNTER_LABEL);

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "gateway-disruptor-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        disruptor = new Disruptor<>(
            () -> new GatewaySlot(slotSizeBytes),
            ringBufferSize,
            threadFactory,
            ProducerType.MULTI,
            new BusySpinWaitStrategy());
        disruptor.handleEventsWith(resettingConsumer(consumer));
        ringBuffer = disruptor.getRingBuffer();
    }

    /**
     * Claims a preallocated slot for producer encoding.
     *
     * <p>When the ring is full, this method increments
     * {@link #DISRUPTOR_FULL_COUNTER_LABEL} and returns {@code null}. Producers
     * that cannot drop messages, such as future fill-path producers, can spin and
     * retry; market-data producers can drop the tick per AMB-008.
     *
     * @return claimed slot, or {@code null} when the ring is full
     */
    public GatewaySlot claimSlot() {
        try {
            long sequence = ringBuffer.tryNext();
            GatewaySlot slot = ringBuffer.get(sequence);
            slot.sequence = sequence;
            return slot;
        } catch (InsufficientCapacityException ex) {
            disruptorFullCounter.increment();
            return null;
        }
    }

    /**
     * Publishes a previously claimed slot.
     *
     * @param slot slot returned by {@link #claimSlot()}
     * @throws IllegalArgumentException if the slot was not claimed
     */
    public void publishSlot(final GatewaySlot slot) {
        if (slot == null || slot.sequence < 0) {
            throw new IllegalArgumentException("slot must be claimed before publish");
        }
        ringBuffer.publish(slot.sequence);
    }

    /**
     * Starts the Disruptor consumer thread.
     */
    public void start() {
        if (!disruptor.hasStarted()) {
            disruptor.start();
        }
    }

    /**
     * Stops the Disruptor consumer thread.
     */
    public void stop() {
        if (disruptor.hasStarted()) {
            disruptor.halt();
        }
    }

    @Override
    public void close() {
        stop();
        disruptorFullCounter.close();
        aeronBackPressureCounter.close();
    }

    public RingBuffer<GatewaySlot> ringBuffer() {
        return ringBuffer;
    }

    public long remainingCapacity() {
        return ringBuffer.remainingCapacity();
    }

    public long disruptorFullCount() {
        return disruptorFullCounter.get();
    }

    public long aeronBackPressureCount() {
        return aeronBackPressureCounter.get();
    }

    public void incrementAeronBackPressureCounter() {
        aeronBackPressureCounter.increment();
    }

    private static EventHandler<GatewaySlot> resettingConsumer(final EventHandler<GatewaySlot> delegate) {
        return (slot, sequence, endOfBatch) -> {
            try {
                delegate.onEvent(slot, sequence, endOfBatch);
            } finally {
                slot.reset();
            }
        };
    }

    private static void validatePowerOfTwo(final int value) {
        if (value <= 0 || (value & (value - 1)) != 0) {
            throw new IllegalArgumentException("ringBufferSize must be a positive power of two: " + value);
        }
    }
}
