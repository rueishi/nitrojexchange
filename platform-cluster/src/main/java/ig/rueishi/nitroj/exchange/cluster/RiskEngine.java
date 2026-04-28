package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;

public interface RiskEngine {
    RiskDecision preTradeCheck(
        int venueId,
        int instrumentId,
        byte side,
        long priceScaled,
        long qtyScaled,
        int strategyId
    );

    void updatePositionSnapshot(int venueId, int instrumentId, long netQtyScaled);

    void updateDailyPnl(long realizedPnlDeltaScaled);

    void setRecoveryLock(int venueId, boolean locked);

    long getDailyPnlScaled();

    void activateKillSwitch(String reason);

    void deactivateKillSwitch();

    boolean isKillSwitchActive();

    void writeSnapshot(ExclusivePublication snapshotPublication);

    void loadSnapshot(Image snapshotImage);

    void resetDailyCounters();

    void setCluster(Cluster cluster);

    void onFill(ExecutionEventDecoder decoder);

    void resetAll();
}
