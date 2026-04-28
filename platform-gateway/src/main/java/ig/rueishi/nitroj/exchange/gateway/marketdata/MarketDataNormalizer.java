package ig.rueishi.nitroj.exchange.gateway.marketdata;

import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;

/**
 * Common gateway interface for venue market-data normalization.
 *
 * <p>Responsibility: accepts raw FIX bytes from Artio and emits normalized
 * gateway events through an implementation-owned sink. Role in system: V11
 * runtime composition selects an L2 or L3 normalizer based on venue config while
 * gateway session handlers remain protocol-neutral. Relationships: implemented
 * by shared standard FIX normalizer bases and optionally enriched by venue
 * plugins. Lifecycle: one normalizer is constructed for a venue runtime and
 * invoked on the Artio library thread for every inbound market-data message.
 * Design intent: decouple FIX parsing and venue enrichment from the concrete
 * Artio session callback class.
 */
public interface MarketDataNormalizer {

    /**
     * Normalizes one inbound FIX message.
     *
     * @param sessionId Artio session ID used to resolve the venue
     * @param buffer inbound FIX message bytes
     * @param offset start of the FIX message
     * @param length number of message bytes
     * @param ingressNanos timestamp captured before parsing
     * @return Artio continuation action
     */
    Action onFixMessage(long sessionId, DirectBuffer buffer, int offset, int length, long ingressNanos);
}
