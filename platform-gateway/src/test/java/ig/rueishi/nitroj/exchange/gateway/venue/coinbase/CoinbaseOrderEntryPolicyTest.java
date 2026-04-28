package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.gateway.venue.OrderEntryPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for Coinbase order-entry policy decisions.
 *
 * <p>Responsibility: verifies Coinbase-owned order-entry capabilities that are
 * consumed by the shared FIX adapter. Role in system: the policy keeps
 * self-trade-prevention and native replace decisions in the venue plugin layer,
 * not in generic strategy or protocol code. Relationships: tests exercise the
 * public {@link OrderEntryPolicy} contract without constructing Artio sessions.
 * Lifecycle: stateless policy instances are created per assertion. Design intent:
 * make venue STP behavior explicit before arbitrage relies on it as a final
 * exchange-side guardrail.</p>
 */
final class CoinbaseOrderEntryPolicyTest {
    @Test
    void selfTradePrevention_defaultPolicy_isCancelDecrease() {
        final CoinbaseOrderEntryPolicy policy = new CoinbaseOrderEntryPolicy();

        assertThat(policy.selfTradePreventionPolicy()).isEqualTo("dc");
        final NewOrderSingleEncoder encoder = new NewOrderSingleEncoder();
        policy.enrichNewOrder(null, encoder);
        assertThat(encoder.hasSelfTradeType()).isTrue();
        assertThat(encoder.selfTradeType()).isEqualTo('D');
    }

    @Test
    void selfTradePrevention_emptyPolicy_supportedAsNoOp() {
        final CoinbaseOrderEntryPolicy policy = new CoinbaseOrderEntryPolicy(
            new VenueCapabilities(true, true, false), "");
        final NewOrderSingleEncoder encoder = new NewOrderSingleEncoder();
        policy.enrichNewOrder(null, encoder);

        assertThat(policy.selfTradePreventionPolicy()).isEmpty();
        assertThat(encoder.hasSelfTradeType()).isFalse();
    }

    @Test
    void selfTradePrevention_supportedPolicies_mapToFixSelfTradeType() {
        assertThat(CoinbaseOrderEntryPolicy.toFixSelfTradeType("dc")).isEqualTo('D');
        assertThat(CoinbaseOrderEntryPolicy.toFixSelfTradeType("co")).isEqualTo('O');
        assertThat(CoinbaseOrderEntryPolicy.toFixSelfTradeType("cn")).isEqualTo('N');
        assertThat(CoinbaseOrderEntryPolicy.toFixSelfTradeType("cb")).isEqualTo('B');
    }

    @Test
    void selfTradePrevention_unsupportedPolicy_failsClearly() {
        assertThatThrownBy(() -> new CoinbaseOrderEntryPolicy(
            new VenueCapabilities(true, true, false), "bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported Coinbase self-trade-prevention policy");
    }

    @Test
    void nativeReplaceSupported_preservesVenueCapability() {
        final CoinbaseOrderEntryPolicy disabled = new CoinbaseOrderEntryPolicy(
            new VenueCapabilities(true, true, false));
        final CoinbaseOrderEntryPolicy enabled = new CoinbaseOrderEntryPolicy(
            new VenueCapabilities(true, true, true));

        assertThat(disabled.nativeReplaceSupported()).isFalse();
        assertThat(enabled.nativeReplaceSupported()).isTrue();
    }

    @Test
    void defaultOrderEntryPolicy_hasNoStpPolicy() {
        final OrderEntryPolicy policy = () -> false;

        assertThat(policy.selfTradePreventionPolicy()).isEmpty();
    }
}
