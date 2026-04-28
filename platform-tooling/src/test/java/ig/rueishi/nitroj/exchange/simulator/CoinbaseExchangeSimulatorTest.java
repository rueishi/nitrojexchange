package ig.rueishi.nitroj.exchange.simulator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-006 {@link CoinbaseExchangeSimulator}.
 *
 * <p>Responsibility: verifies simulator startup, market-data publication,
 * order/cancel capture, fill-mode behavior, disconnect scheduling, and assertion
 * helpers. Role in system: later E2E tests depend on this simulator as their
 * exchange-side FIX test double. Relationships: tests exercise the public
 * simulator API and its TASK-006 support classes without starting gateway,
 * cluster, Aeron, or SBE components. Lifecycle: each test creates a simulator on
 * an ephemeral port and closes it during teardown. Design intent: keep the
 * simulator behavior deterministic enough for repeatable automated testing.
 */
final class CoinbaseExchangeSimulatorTest {
    /**
     * Verifies simulator startup accepts the configured port and records logon.
     *
     * @throws Exception if the simulator fails to start on an ephemeral port
     */
    @Test
    void logon_accepted_logonCountIncremented() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.IMMEDIATE)) {
            simulator.start();

            assertThat(simulator.getLogonCount()).isEqualTo(1);
            assertThat(simulator.isConnected()).isTrue();
        }
    }

    /**
     * Verifies immediate-fill mode records the order and emits ack/fill reports.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void newOrder_immediateFill_orderReceived() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.IMMEDIATE)) {
            simulator.start();

            simulator.submitNewOrder("BUY", 65_000.00, 0.01);

            simulator.assertOrderReceived("BUY", 65_000.00, 0.01);
            assertThat(simulator.getFillCount()).isEqualTo(1);
            assertThat(simulator.executionReports()).extracting(CoinbaseExchangeSimulator.ExecutionReport::execType)
                .containsExactly("0", "F");
        }
    }

    /**
     * Verifies reject-all mode records a reject and does not fill the order.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void newOrder_rejectAll_rejectCountIncremented() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.REJECT_ALL)) {
            simulator.start();

            simulator.submitNewOrder("SELL", 65_001.00, 0.02);

            assertThat(simulator.getRejectCount()).isEqualTo(1);
            assertThat(simulator.getFillCount()).isZero();
            assertThat(simulator.executionReports().get(1).rejectReason()).isZero();
        }
    }

    /**
     * Verifies partial-fill mode emits two fills after the initial ack.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void newOrder_partialThenFull_twoFillsReceived() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.PARTIAL_THEN_FULL)) {
            simulator.start();

            simulator.submitNewOrder("BUY", 65_000.00, 0.10);
            waitUntil(() -> simulator.getFillCount() == 2);

            assertThat(simulator.executionReports()).extracting(CoinbaseExchangeSimulator.ExecutionReport::execType)
                .containsExactly("0", "F", "F");
            assertThat(simulator.executionReports().get(1).lastQty()).isEqualTo(0.05);
            assertThat(simulator.executionReports().get(2).lastQty()).isEqualTo(0.05);
        }
    }

    /**
     * Verifies no-fill mode acknowledges the order but leaves it pending.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void newOrder_noFill_orderAcked_fillCountZero() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            simulator.submitNewOrder("BUY", 65_000.00, 0.01);

            assertThat(simulator.getFillCount()).isZero();
            assertThat(simulator.pendingOrderCount()).isEqualTo(1);
            assertThat(simulator.executionReports()).extracting(CoinbaseExchangeSimulator.ExecutionReport::execType)
                .containsExactly("0");
        }
    }

    /**
     * Verifies cancel requests remove live orders and emit cancel confirmation.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void cancelRequest_pendingOrder_cancelConfirmed() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();
            String clOrdId = simulator.submitNewOrder("BUY", 65_000.00, 0.01);

            simulator.submitCancel("CXL-1", clOrdId);

            simulator.assertCancelReceived(clOrdId);
            assertThat(simulator.pendingOrderCount()).isZero();
            assertThat(simulator.executionReports().get(1).execType()).isEqualTo("4");
        }
    }

    /**
     * Verifies market-data ticks reflect configured bid/ask values.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void marketData_published_bidAskCorrect() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.IMMEDIATE)) {
            simulator.start();

            waitUntil(() -> simulator.getMarketDataCount() > 0);

            assertThat(simulator.lastMarketDataTick().symbol()).isEqualTo("BTC-USD");
            assertThat(simulator.lastMarketDataTick().bid()).isEqualTo(65_000.00);
            assertThat(simulator.lastMarketDataTick().ask()).isEqualTo(65_001.00);
        }
    }

    /**
     * Verifies scheduled disconnect marks the simulator disconnected.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void scheduleDisconnect_fixSessionDrops() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            simulator.scheduleDisconnect(10);
            waitUntil(() -> !simulator.isConnected());

            assertThat(simulator.isConnected()).isFalse();
        }
    }

    /**
     * Verifies assertion helper matches expected side, price, and quantity.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void assertOrderReceived_matchesSide_price_qty() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            simulator.submitNewOrder("SELL", 65_001.00, 0.02);

            simulator.assertOrderReceived("SELL", 65_001.00, 0.02);
        }
    }

    /**
     * Verifies assertion helper fails when no matching order arrives.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void assertOrderReceived_noMatchingOrder_assertionError() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            assertThatThrownBy(() -> simulator.assertOrderReceived("BUY", 1.0, 1.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected order not received");
        }
    }

    /**
     * Verifies simulator config can carry multiple instruments and route orders by symbol.
     *
     * @throws Exception if the simulator fails to start
     */
    @Test
    void multipleInstruments_correctRoutingPerSymbol() throws Exception {
        SimulatorConfig config = SimulatorConfig.builder()
            .port(freePort())
            .marketDataIntervalMs(10)
            .instrument("BTC-USD", 65_000.00, 65_001.00)
            .instrument("ETH-USD", 3_000.00, 3_001.00)
            .fillMode(CoinbaseExchangeSimulator.FillMode.NO_FILL)
            .build();
        try (CoinbaseExchangeSimulator simulator = CoinbaseExchangeSimulator.builder()
            .config(config)
            .fillMode(CoinbaseExchangeSimulator.FillMode.NO_FILL)
            .build()) {
            simulator.start();

            simulator.submitNewOrder("ETH-1", "BUY", "ETH-USD", 3_000.00, 1.5);

            List<CoinbaseExchangeSimulator.ReceivedOrder> orders = simulator.receivedOrders();
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).symbol()).isEqualTo("ETH-USD");
        }
    }

    @Test
    void l3Snapshot_validPrimaryInstrument_emitsBidAndAskSnapshotMessages() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            CoinbaseExchangeSimulator.L3OrderEvent first = simulator.emitL3Snapshot();

            assertThat(first.action()).isEqualTo("SNAPSHOT");
            assertThat(simulator.l3Events()).hasSize(2);
            assertThat(simulator.l3Events()).extracting(CoinbaseExchangeSimulator.L3OrderEvent::side)
                .containsExactly("BUY", "SELL");
            assertThat(simulator.l3FixMessages()).allSatisfy(message -> assertThat(message)
                .contains("35=X")
                .contains("278=SNAP-"));
        }
    }

    @Test
    void l3AddChangeDelete_validOrder_emitsIncrementalActionsWithMonotonicSeqNums() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            simulator.emitL3AddOrder("OID-1", "BUY", "BTC-USD", 65_000.00, 0.5);
            simulator.emitL3ChangeOrder("OID-1", "BUY", "BTC-USD", 65_000.00, 0.25);
            simulator.emitL3DeleteOrder("OID-1", "BUY", "BTC-USD", 65_000.00);

            assertThat(simulator.l3Events()).extracting(CoinbaseExchangeSimulator.L3OrderEvent::action)
                .containsExactly("ADD", "CHANGE", "DELETE");
            assertThat(simulator.l3Events()).extracting(CoinbaseExchangeSimulator.L3OrderEvent::seqNum)
                .containsExactly(1, 2, 3);
            assertThat(simulator.l3Events().get(2).size()).isZero();
        }
    }

    @Test
    void marketDataSubscribe_l2AndL3_supportedButUnknownModelThrowsClearError() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            simulator.subscribeMarketData("L2");
            simulator.subscribeMarketData("L3");

            assertThatThrownBy(() -> simulator.subscribeMarketData("FULL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported simulator market-data model");
        }
    }

    @Test
    void l3Emit_unknownSymbol_throwsWithoutRecordingEvent() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            assertThatThrownBy(() -> simulator.emitL3AddOrder("OID-1", "BUY", "ETH-USD", 3_000.00, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown L3 symbol");
            assertThat(simulator.l3Events()).isEmpty();
        }
    }

    @Test
    void l3Emit_edgeCases_zeroSizeDuplicateAndEmptySnapshotSupported() throws Exception {
        SimulatorConfig config = SimulatorConfig.builder()
            .port(freePort())
            .instrument("BTC-USD", 65_000.00, 65_001.00)
            .fillMode(CoinbaseExchangeSimulator.FillMode.NO_FILL)
            .build();
        try (CoinbaseExchangeSimulator simulator = CoinbaseExchangeSimulator.builder().config(config).build()) {
            simulator.start();

            simulator.emitL3ChangeOrder("OID-1", "BUY", "BTC-USD", 65_000.00, 0.0);
            simulator.emitL3AddOrder("OID-1", "BUY", "BTC-USD", 65_000.00, 0.1);
            simulator.emitL3AddOrder("OID-1", "BUY", "BTC-USD", 65_000.00, 0.2);

            assertThat(simulator.l3Events()).hasSize(3);
            assertThat(simulator.l3Events().get(0).size()).isZero();
        }
    }

    @Test
    void l3Emit_invalidFields_throwValidationErrors() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            assertThatThrownBy(() -> simulator.emitL3AddOrder("", "BUY", "BTC-USD", 65_000.00, 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
            assertThatThrownBy(() -> simulator.emitL3AddOrder("OID-1", "HOLD", "BTC-USD", 65_000.00, 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported L3 side");
            assertThatThrownBy(() -> simulator.emitL3AddOrder("OID-1", "BUY", "BTC-USD", Double.NaN, 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
            assertThatThrownBy(() -> simulator.emitL3AddOrder("OID-1", "BUY", "BTC-USD", 65_000.00, -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        }
    }

    @Test
    void l3FailureScenarios_sequenceGapMalformedDisconnectReconnectRecorded() throws Exception {
        try (CoinbaseExchangeSimulator simulator = newSimulator(CoinbaseExchangeSimulator.FillMode.NO_FILL)) {
            simulator.start();

            simulator.emitL3SequenceGap(2);
            CoinbaseExchangeSimulator.L3OrderEvent event =
                simulator.emitL3AddOrder("OID-1", "SELL", "BTC-USD", 65_001.00, 0.4);
            String malformed = simulator.emitMalformedL3Message("bad-price");
            simulator.scheduleDisconnect(0);
            waitUntil(() -> !simulator.isConnected());
            simulator.reconnect();

            assertThat(event.seqNum()).isEqualTo(3);
            assertThat(simulator.getL3SequenceGapCount()).isEqualTo(1);
            assertThat(simulator.getMalformedL3Count()).isEqualTo(1);
            assertThat(malformed).contains("58=bad-price");
            assertThat(simulator.isConnected()).isTrue();
            assertThat(simulator.getLogonCount()).isEqualTo(2);
        }
    }

    private static CoinbaseExchangeSimulator newSimulator(final CoinbaseExchangeSimulator.FillMode mode) throws IOException {
        return CoinbaseExchangeSimulator.builder()
            .port(freePort())
            .instrument("BTC-USD", 65_000.00, 65_001.00)
            .fillMode(mode)
            .build();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitUntil(final Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Condition was not met before timeout");
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }
}
