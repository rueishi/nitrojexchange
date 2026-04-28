package ig.rueishi.nitroj.exchange.order;

import org.agrona.DirectBuffer;

@FunctionalInterface
interface OrderMessageSink {
    OrderMessageSink NO_OP = (buffer, offset, length) -> length;

    long offer(DirectBuffer buffer, int offset, int length);
}
