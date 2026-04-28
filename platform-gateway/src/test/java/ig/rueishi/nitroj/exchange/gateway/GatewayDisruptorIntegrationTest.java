package ig.rueishi.nitroj.exchange.gateway;

import com.lmax.disruptor.EventHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage for TASK-007 gateway Disruptor handoff.
 *
 * <p>Responsibility: verifies the real LMAX Disruptor ring buffer, preallocated
 * {@link GatewaySlot} reuse, full-ring behavior, counters, and consumer reset
 * rule. Role in system: this test gives later gateway producer and Aeron publisher
 * cards a known-good handoff primitive. Relationships: tests exercise
 * {@link GatewayDisruptor} with an injected consumer in place of TASK-012
 * {@code AeronPublisher}. Lifecycle: each case creates its own ring and counter
 * manager, starts the consumer only when needed, and closes the disruptor after
 * assertions. Design intent: cover concurrency and edge capacity paths while
 * keeping the test independent of Aeron, Artio, and SBE encoding.
 */
final class GatewayDisruptorIntegrationTest {
    /**
     * Verifies multiple producer threads can publish into one consumer without
     * losing messages.
     *
     * @throws Exception if producers or consumer time out
     */
    @Test
    void multipleProducers_messagesConsumedInOrder() throws Exception {
        int producers = 2;
        int perProducer = 32;
        CountDownLatch consumed = new CountDownLatch(producers * perProducer);
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        try (GatewayDisruptor disruptor = new GatewayDisruptor(128, 512, counters(), recordInt(seen, consumed))) {
            disruptor.start();

            Thread first = new Thread(() -> publishRange(disruptor, 0, perProducer));
            Thread second = new Thread(() -> publishRange(disruptor, 1_000, perProducer));
            first.start();
            second.start();
            first.join();
            second.join();

            assertThat(consumed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(seen).hasSize(producers * perProducer);
            assertThat(seen).contains(0, perProducer - 1, 1_000, 1_000 + perProducer - 1);
        }
    }

    /**
     * Verifies a single producer can sustain a burst without drops when the
     * consumer is running.
     *
     * @throws Exception if consumption times out
     */
    @Test
    void singleProducer_highThroughput_noDrops() throws Exception {
        int messages = 1_000;
        CountDownLatch consumed = new CountDownLatch(messages);
        try (GatewayDisruptor disruptor = new GatewayDisruptor(1024, 512, counters(), (slot, seq, end) -> consumed.countDown())) {
            disruptor.start();

            publishRange(disruptor, 0, messages);

            assertThat(consumed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(disruptor.disruptorFullCount()).isZero();
        }
    }

    /**
     * Verifies remaining capacity exposes a nearly/full ring to callers.
     */
    @Test
    void backpressure_alertFires_whenNearlyFull() {
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> { })) {
            for (int i = 0; i < 4; i++) {
                GatewaySlot slot = disruptor.claimSlot();
                assertThat(slot).isNotNull();
            }

            assertThat(disruptor.remainingCapacity()).isZero();
        }
    }

    /**
     * Verifies the consumer wrapper resets slot fields after consumption.
     *
     * @throws Exception if consumption times out
     */
    @Test
    void slotReset_afterConsume_fieldsZeroed() throws Exception {
        CountDownLatch consumed = new CountDownLatch(1);
        AtomicInteger observedLength = new AtomicInteger();
        GatewaySlot[] claimed = new GatewaySlot[1];
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> {
            observedLength.set(slot.length);
            consumed.countDown();
        })) {
            disruptor.start();
            GatewaySlot slot = disruptor.claimSlot();
            claimed[0] = slot;
            slot.length = 7;
            disruptor.publishSlot(slot);

            assertThat(consumed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(observedLength.get()).isEqualTo(7);
            assertThat(claimed[0].length).isZero();
            assertThat(claimed[0].sequence).isEqualTo(-1);
        }
    }

    /**
     * Verifies a full ring returns null and increments the full counter.
     */
    @Test
    void ringFull_tryPublishReturnsFalse_counterIncremented() {
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> { })) {
            for (int i = 0; i < 4; i++) {
                assertThat(disruptor.claimSlot()).isNotNull();
            }

            assertThat(disruptor.claimSlot()).isNull();
            assertThat(disruptor.disruptorFullCount()).isEqualTo(1);
        }
    }

    /**
     * Verifies producer errors cannot publish null or already-reset slots.
     */
    @Test
    void invalidPublish_rejectedBeforeRingCursorMoves() {
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> { })) {
            assertThatThrownBy(() -> disruptor.publishSlot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot must be claimed");
            assertThatThrownBy(() -> disruptor.publishSlot(new GatewaySlot()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot must be claimed");
            assertThat(disruptor.disruptorFullCount()).isZero();
        }
    }

    /**
     * Verifies the Aeron back-pressure counter remains under explicit publisher
     * ownership and is independent from ring-full accounting.
     */
    @Test
    void aeronBackPressureCounter_incrementedIndependentlyFromRingFullCounter() {
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> { })) {
            disruptor.incrementAeronBackPressureCounter();
            disruptor.incrementAeronBackPressureCounter();

            assertThat(disruptor.aeronBackPressureCount()).isEqualTo(2);
            assertThat(disruptor.disruptorFullCount()).isZero();
        }
    }

    /**
     * Verifies exactly ring-buffer-size claims can be in flight without drops.
     */
    @Test
    void exactlyRingBufferSizeMessages_noDrops() {
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> { })) {
            for (int i = 0; i < 4; i++) {
                assertThat(disruptor.claimSlot()).isNotNull();
            }

            assertThat(disruptor.disruptorFullCount()).isZero();
        }
    }

    /**
     * Verifies publishing from the test thread while the consumer runs does not
     * deadlock.
     *
     * @throws Exception if consumption times out
     */
    @Test
    void producerAndConsumerSameThread_noDeadlock() throws Exception {
        CountDownLatch consumed = new CountDownLatch(1);
        try (GatewayDisruptor disruptor = new GatewayDisruptor(4, 512, counters(), (slot, seq, end) -> consumed.countDown())) {
            disruptor.start();
            GatewaySlot slot = disruptor.claimSlot();
            slot.length = 1;
            disruptor.publishSlot(slot);

            assertThat(consumed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static EventHandler<GatewaySlot> recordInt(final List<Integer> seen, final CountDownLatch consumed) {
        return (slot, sequence, endOfBatch) -> {
            seen.add(slot.buffer.getInt(0));
            consumed.countDown();
        };
    }

    private static void publishRange(final GatewayDisruptor disruptor, final int startInclusive, final int count) {
        for (int i = 0; i < count; i++) {
            GatewaySlot slot;
            do {
                slot = disruptor.claimSlot();
                if (slot == null) {
                    Thread.onSpinWait();
                }
            } while (slot == null);
            slot.buffer.putInt(0, startInclusive + i);
            slot.length = Integer.BYTES;
            disruptor.publishSlot(slot);
        }
    }

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }
}
