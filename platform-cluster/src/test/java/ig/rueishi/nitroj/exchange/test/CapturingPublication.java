package ig.rueishi.nitroj.exchange.test;

import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CapturingPublication {
    private final List<byte[]> captured = new ArrayList<>();

    public long offer(final DirectBuffer buffer, final int offset, final int length) {
        final byte[] copy = new byte[length];
        buffer.getBytes(offset, copy);
        captured.add(copy);
        return length;
    }

    public int countByTemplateId(final int templateId) {
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        int count = 0;
        for (byte[] message : captured) {
            headerDecoder.wrap(new UnsafeBuffer(message), 0);
            if (headerDecoder.templateId() == templateId) {
                count++;
            }
        }
        return count;
    }

    public UnsafeBuffer message(final int index) {
        return new UnsafeBuffer(captured.get(index));
    }

    public int size() {
        return captured.size();
    }

    public void clear() {
        captured.clear();
    }
}
