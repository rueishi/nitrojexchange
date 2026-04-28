package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;

/**
 * Coordinates per-venue recovery after FIX disconnect/reconnect events.
 *
 * <p>The message router forwards venue status, order-status execution reports,
 * balance responses, and timer events to this component. Implementations set
 * risk recovery locks, query gateway state, reconcile order/balance differences,
 * and publish recovery-complete events once trading may resume.</p>
 */
public interface RecoveryCoordinator {
    void onVenueStatus(VenueStatusEventDecoder decoder);

    void onBalanceResponse(BalanceQueryResponseDecoder decoder);

    void onTimer(long correlationId, long timestamp);

    boolean isInRecovery(int venueId);

    void reconcileOrder(ExecutionEventDecoder decoder);

    void writeSnapshot(ExclusivePublication snapshotPublication);

    void loadSnapshot(Image snapshotImage);

    void resetAll();

    void setCluster(Cluster cluster);
}
