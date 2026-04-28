package ig.rueishi.nitroj.exchange.gateway.venue.coinbase;

import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.gateway.venue.OrderEntryPolicy;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;

import java.util.Objects;
import java.util.Set;

/**
 * Coinbase order-entry policy for the shared FIX adapter.
 *
 * <p>Responsibility: captures Coinbase-specific order-entry capability decisions
 * that are independent of the generated FIX encoder implementation. Role in
 * system: {@link StandardOrderEntryAdapter} consults this policy before routing
 * replace commands and before sending enriched messages. Relationships: owned by
 * {@link CoinbaseVenuePlugin}; currently preserves the existing behavior where
 * native FIX cancel/replace messages are suppressed because cluster sequencing
 * emits cancel plus new order. Lifecycle: stateless singleton-style object.
 * Design intent: make Coinbase's replace rule explicit and testable instead of
 * hiding it as a boolean constructor parameter on gateway core.
 */
public final class CoinbaseOrderEntryPolicy implements OrderEntryPolicy {
    private static final String DEFAULT_STP_POLICY = "dc";
    private static final Set<String> SUPPORTED_STP_POLICIES = Set.of("", "dc", "co", "cn", "cb");
    private final VenueCapabilities capabilities;
    private final String selfTradePreventionPolicy;

    public CoinbaseOrderEntryPolicy() {
        this(new VenueCapabilities(true, true, false), DEFAULT_STP_POLICY);
    }

    public CoinbaseOrderEntryPolicy(final VenueCapabilities capabilities) {
        this(capabilities, DEFAULT_STP_POLICY);
    }

    public CoinbaseOrderEntryPolicy(final VenueCapabilities capabilities, final String selfTradePreventionPolicy) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        if (selfTradePreventionPolicy == null) {
            throw new NullPointerException("selfTradePreventionPolicy");
        }
        if (!SUPPORTED_STP_POLICIES.contains(selfTradePreventionPolicy)) {
            throw new IllegalArgumentException("Unsupported Coinbase self-trade-prevention policy: "
                + selfTradePreventionPolicy);
        }
        this.selfTradePreventionPolicy = selfTradePreventionPolicy;
    }

    @Override
    public boolean nativeReplaceSupported() {
        return capabilities.nativeReplaceSupported();
    }

    @Override
    public String selfTradePreventionPolicy() {
        return selfTradePreventionPolicy;
    }

    /**
     * Applies Coinbase Exchange order-level self-trade prevention.
     *
     * <p>Coinbase REST/docs often describe STP with lowercase values such as
     * {@code dc}, {@code co}, {@code cn}, and {@code cb}; the FIX 5.0
     * NewOrderSingle wire field is tag 7928 {@code SelfTradeType} and uses
     * uppercase one-character values. Encoding it here keeps the venue-specific
     * wire mapping out of shared strategy and FIX adapter code.</p>
     */
    @Override
    public void enrichNewOrder(final NewOrderCommandDecoder command, final NewOrderSingleEncoder encoder) {
        if (!selfTradePreventionPolicy.isEmpty()) {
            encoder.selfTradeType(toFixSelfTradeType(selfTradePreventionPolicy));
        }
    }

    static char toFixSelfTradeType(final String policy) {
        return switch (policy) {
            case "dc" -> 'D';
            case "co" -> 'O';
            case "cn" -> 'N';
            case "cb" -> 'B';
            case "" -> '\0';
            default -> throw new IllegalArgumentException("Unsupported Coinbase self-trade-prevention policy: " + policy);
        };
    }
}
