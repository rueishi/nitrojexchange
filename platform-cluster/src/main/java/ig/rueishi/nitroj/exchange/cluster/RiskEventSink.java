package ig.rueishi.nitroj.exchange.cluster;

import org.agrona.DirectBuffer;

@FunctionalInterface
interface RiskEventSink {
    RiskEventSink NO_OP = (buffer, offset, length) -> length;

    long offer(DirectBuffer buffer, int offset, int length);
}
