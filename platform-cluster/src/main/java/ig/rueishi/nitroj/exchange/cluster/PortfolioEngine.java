package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;

/**
 * Cluster-side portfolio and PnL component.
 *
 * <p>The message router calls {@link #onFill(ExecutionEventDecoder)} after
 * OrderManager accepts a fill and before RiskEngine receives its fill callback.
 * Implementations maintain positions keyed by venue/instrument, expose position
 * reads for strategies and recovery, and notify RiskEngine after every fill so
 * pre-trade position checks use the freshest cluster-local inventory.</p>
 */
public interface PortfolioEngine {
    void initPosition(int venueId, int instrumentId);

    void onFill(ExecutionEventDecoder decoder);

    void refreshUnrealizedPnl(int venueId, int instrumentId, long markPriceScaled);

    long getNetQtyScaled(int venueId, int instrumentId);

    long getAvgEntryPriceScaled(int venueId, int instrumentId);

    long unrealizedPnl(int venueId, int instrumentId, long markPriceScaled);

    void adjustPosition(int venueId, int instrumentId, double balanceUnscaled);

    long getTotalRealizedPnlScaled();

    long getTotalUnrealizedPnlScaled();

    void writeSnapshot(ExclusivePublication snapshotPublication);

    void loadSnapshot(Image snapshotImage);

    void archiveDailyPnl(Publication egressPublication);

    void setCluster(Cluster cluster);

    void resetAll();
}
