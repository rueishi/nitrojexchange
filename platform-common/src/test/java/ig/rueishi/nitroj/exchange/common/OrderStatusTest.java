package ig.rueishi.nitroj.exchange.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {
    @Test
    void isTerminal_allStatuses_matchExpectedTerminality() {
        assertThat(OrderStatus.isTerminal(OrderStatus.PENDING_NEW)).isFalse();
        assertThat(OrderStatus.isTerminal(OrderStatus.NEW)).isFalse();
        assertThat(OrderStatus.isTerminal(OrderStatus.PARTIALLY_FILLED)).isFalse();
        assertThat(OrderStatus.isTerminal(OrderStatus.FILLED)).isTrue();
        assertThat(OrderStatus.isTerminal(OrderStatus.PENDING_CANCEL)).isFalse();
        assertThat(OrderStatus.isTerminal(OrderStatus.CANCELED)).isTrue();
        assertThat(OrderStatus.isTerminal(OrderStatus.PENDING_REPLACE)).isFalse();
        assertThat(OrderStatus.isTerminal(OrderStatus.REPLACED)).isTrue();
        assertThat(OrderStatus.isTerminal(OrderStatus.REJECTED)).isTrue();
        assertThat(OrderStatus.isTerminal(OrderStatus.EXPIRED)).isTrue();
    }

    @Test
    void terminalMask_bitArithmetic_matchesUniqueTerminalStatuses() {
        final Set<Byte> statuses = Set.of(
            OrderStatus.PENDING_NEW,
            OrderStatus.NEW,
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.FILLED,
            OrderStatus.PENDING_CANCEL,
            OrderStatus.CANCELED,
            OrderStatus.PENDING_REPLACE,
            OrderStatus.REPLACED,
            OrderStatus.REJECTED,
            OrderStatus.EXPIRED
        );

        assertThat(statuses).hasSize(10);
        assertThat(statuses).allMatch(status -> status >= 0 && status <= 9);

        final long expectedTerminalMask =
            (1L << OrderStatus.FILLED)
                | (1L << OrderStatus.CANCELED)
                | (1L << OrderStatus.REPLACED)
                | (1L << OrderStatus.REJECTED)
                | (1L << OrderStatus.EXPIRED);

        final long actualTerminalMask = statuses.stream()
            .filter(OrderStatus::isTerminal)
            .mapToLong(status -> 1L << status)
            .reduce(0L, (left, right) -> left | right);

        assertThat(actualTerminalMask).isEqualTo(expectedTerminalMask);
    }
}
