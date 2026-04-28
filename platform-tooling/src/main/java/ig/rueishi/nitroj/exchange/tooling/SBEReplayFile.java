package ig.rueishi.nitroj.exchange.tooling;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Sequential offline reader for archived SBE records.
 *
 * <p>The replay tool is intentionally read-only. For unit-testable offline
 * operation it consumes files containing repeated {@code position, length,
 * payload} tuples, where payload is a complete SBE message beginning with the
 * standard message header. The class supports seeking by cluster/archive
 * position and skips truncated trailing records with a warning.</p>
 */
public final class SBEReplayFile implements Closeable {
    private static final Logger LOGGER = System.getLogger(SBEReplayFile.class.getName());
    private static final int MAX_RECORD_BYTES = 1 << 20;

    private final List<Path> files;
    private int fileIndex = -1;
    private DataInputStream input;
    private Record next;

    public SBEReplayFile(final Path archivePath) {
        try {
            if (Files.isDirectory(archivePath)) {
                try (var stream = Files.list(archivePath)) {
                    files = stream.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList();
                }
            } else {
                files = List.of(archivePath);
            }
            openNextFile();
            advance();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to open replay path: " + archivePath, ex);
        }
    }

    public boolean hasNext() {
        return next != null;
    }

    public Record next() {
        final Record current = next;
        advance();
        return current;
    }

    public void seekToPosition(final long position) {
        closeCurrent();
        fileIndex = -1;
        try {
            openNextFile();
            advance();
            while (next != null && next.position() < position) {
                advance();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to seek replay file", ex);
        }
    }

    private void advance() {
        next = null;
        while (input != null) {
            try {
                final long position = input.readLong();
                final int length = input.readInt();
                if (length < 0 || length > MAX_RECORD_BYTES) {
                    LOGGER.log(Level.WARNING, "Invalid SBE replay record length {0} at position {1}", length, position);
                    continue;
                }
                final byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) {
                    LOGGER.log(Level.WARNING, "Truncated SBE replay record at position {0}", position);
                    openNextFile();
                    continue;
                }
                next = new Record(position, new UnsafeBuffer(bytes), length);
                return;
            } catch (EOFException ex) {
                try {
                    openNextFile();
                } catch (IOException ioEx) {
                    throw new IllegalStateException("Unable to advance replay file", ioEx);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Unable to read SBE replay record: {0}", ex.toString());
                try {
                    openNextFile();
                } catch (IOException ioEx) {
                    throw new IllegalStateException("Unable to advance replay file", ioEx);
                }
            }
        }
    }

    private void openNextFile() throws IOException {
        closeCurrent();
        fileIndex++;
        if (fileIndex >= files.size()) {
            input = null;
            return;
        }
        final InputStream stream = Files.newInputStream(files.get(fileIndex));
        input = new DataInputStream(stream);
    }

    private void closeCurrent() {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
            input = null;
        }
    }

    @Override
    public void close() {
        closeCurrent();
    }

    public record Record(long position, UnsafeBuffer buffer, int length) {
    }
}
