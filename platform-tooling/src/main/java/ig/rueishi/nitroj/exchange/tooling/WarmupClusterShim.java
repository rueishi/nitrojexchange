package ig.rueishi.nitroj.exchange.tooling;

import io.aeron.Aeron;
import io.aeron.DirectBufferVector;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredServiceContainer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;

/**
 * Minimal Cluster facade used during JVM warmup.
 *
 * <p>The shim supplies monotonic cluster time and unique log positions without
 * starting Aeron Cluster. TradingClusteredService can therefore wire all normal
 * collaborators and execute hot-path message processing before the real cluster
 * starts. Publication-related methods are no-ops that report success, because
 * warmup must exercise encoding paths but must not emit live orders.</p>
 */
public final class WarmupClusterShim implements Cluster {
    private long simulatedTime = System.currentTimeMillis() * 1_000L;
    private long simulatedPosition = 1_000_000L;

    @Override public int memberId() { return 0; }
    @Override public Role role() { return Role.LEADER; }
    @Override public long logPosition() { return simulatedPosition++; }
    @Override public Aeron aeron() { return null; }
    @Override public ClusteredServiceContainer.Context context() { return null; }
    @Override public ClientSession getClientSession(final long clusterSessionId) { return null; }
    @Override public Collection<ClientSession> clientSessions() { return List.of(); }
    @Override public void forEachClientSession(final Consumer<? super ClientSession> action) { }
    @Override public boolean closeClientSession(final long clusterSessionId) { return true; }
    @Override public long time() { return simulatedTime += 10L; }
    @Override public TimeUnit timeUnit() { return TimeUnit.MICROSECONDS; }
    @Override public boolean scheduleTimer(final long correlationId, final long deadline) { return true; }
    @Override public boolean cancelTimer(final long correlationId) { return true; }
    @Override public long offer(final DirectBuffer buffer, final int offset, final int length) { return length; }
    @Override public long offer(final DirectBufferVector[] vectors) { return vectors == null ? 0L : vectors.length; }
    @Override public long tryClaim(final int length, final BufferClaim bufferClaim) { return length; }
    @Override public IdleStrategy idleStrategy() { return null; }
}
