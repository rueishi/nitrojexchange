package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.EntryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link OwnOrderOverlay}.
 *
 * <p>Responsibility: verifies that own working order quantities are tracked by
 * venue/instrument/side/price and by optional venue order ID. Role in system:
 * this overlay feeds {@link ExternalLiquidityView} while keeping gross market
 * books free of private order ownership. Relationships: tests use direct overlay
 * APIs so order-manager state-machine coverage remains independent. Lifecycle:
 * each test creates a fresh overlay. Design intent: prove conservative L2
 * subtraction, exact L3 matching, terminal cleanup, and invalid input handling
 * before strategies rely on the view.</p>
 */
final class OwnOrderOverlayTest {
    @Test
    void upsert_oneOwnOrder_tracksLevelAndVenueOrderId() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();

        overlay.upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 10L, true);

        assertThat(overlay.ownSizeAt(1, 1, EntryType.BID, 100L)).isEqualTo(10L);
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 1, 1, EntryType.BID, 100L)).isEqualTo(10L);
    }

    @Test
    void upsert_multipleOrdersSameLevel_aggregatesAndUpdates() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();

        overlay.upsert(1L, "VO-1", 1, 1, EntryType.ASK, 101L, 10L, true);
        overlay.upsert(2L, "VO-2", 1, 1, EntryType.ASK, 101L, 5L, true);
        overlay.upsert(1L, "VO-1", 1, 1, EntryType.ASK, 101L, 7L, true);

        assertThat(overlay.ownSizeAt(1, 1, EntryType.ASK, 101L)).isEqualTo(12L);
        assertThat(overlay.orderCount()).isEqualTo(2);
    }

    @Test
    void remove_terminalOrZeroQty_removesOwnLiquidity() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 10L, true);

        overlay.upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 0L, true);

        assertThat(overlay.ownSizeAt(1, 1, EntryType.BID, 100L)).isZero();
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 1, 1, EntryType.BID, 100L)).isZero();
    }

    @Test
    void exactMatch_mismatchedIdentity_returnsZero() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 10L, true);

        assertThat(overlay.exactOwnSizeByVenueOrderId("UNKNOWN", 1, 1, EntryType.BID, 100L)).isZero();
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 2, 1, EntryType.BID, 100L)).isZero();
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 1, 2, EntryType.BID, 100L)).isZero();
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 1, 1, EntryType.ASK, 100L)).isZero();
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 1, 1, EntryType.BID, 101L)).isZero();
    }

    @Test
    void exactMatch_sameVenueOrderIdOnDifferentVenues_doesNotOverwrite() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        overlay.upsert(1L, "REUSED", 1, 1, EntryType.BID, 100L, 10L, true);
        overlay.upsert(2L, "REUSED", 2, 1, EntryType.ASK, 101L, 5L, true);

        assertThat(overlay.exactOwnSizeByVenueOrderId("REUSED", 1, 1, EntryType.BID, 100L)).isEqualTo(10L);
        assertThat(overlay.exactOwnSizeByVenueOrderId("REUSED", 2, 1, EntryType.ASK, 101L)).isEqualTo(5L);
        assertThat(overlay.ownSizeAt(1, 1, EntryType.BID, 100L)).isEqualTo(10L);
        assertThat(overlay.ownSizeAt(2, 1, EntryType.ASK, 101L)).isEqualTo(5L);
    }

    @Test
    void upsert_invalidIdentityOrValues_throws() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();

        assertThatThrownBy(() -> overlay.upsert(0L, "VO", 1, 1, EntryType.BID, 100L, 1L, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> overlay.upsert(1L, "VO", 1, 1, EntryType.TRADE, 100L, 1L, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> overlay.upsert(1L, "VO", 1, 1, EntryType.BID, 0L, 1L, true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remove_unknownOrder_isIgnored() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();

        overlay.remove(99L);

        assertThat(overlay.orderCount()).isZero();
    }

    @Test
    void capacityExceeded_ignoresNewOrderAndPreservesExistingState() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay(1);

        overlay.upsert(1L, "VO-1", 1, 1, EntryType.BID, 100L, 10L, true);
        overlay.upsert(2L, "VO-2", 1, 1, EntryType.BID, 101L, 10L, true);

        assertThat(overlay.orderCount()).isEqualTo(1);
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-1", 1, 1, EntryType.BID, 100L)).isEqualTo(10L);
        assertThat(overlay.exactOwnSizeByVenueOrderId("VO-2", 1, 1, EntryType.BID, 101L)).isZero();
    }

    @Test
    void invalidCapacity_throwsClearly() {
        assertThatThrownBy(() -> new OwnOrderOverlay(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxOwnOrders");
    }
}
