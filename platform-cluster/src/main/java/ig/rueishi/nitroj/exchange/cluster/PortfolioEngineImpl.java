package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.PositionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.PositionSnapshotDecoder;
import ig.rueishi.nitroj.exchange.messages.PositionSnapshotEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Single-threaded implementation of the cluster portfolio engine.
 *
 * <p>Positions are stored in an Agrona {@link Long2ObjectHashMap} keyed by
 * {@code ((long) venueId << 32) | instrumentId}. ClusterMain pre-populates known
 * pairs with {@link #initPosition(int, int)} so ordinary fills mutate existing
 * {@link Position} objects rather than allocating on the hot path. Fills update
 * side-aware average entry and realized PnL using {@link ScaledMath}, then push
 * the post-fill net quantity into RiskEngine.</p>
 */
public final class PortfolioEngineImpl implements PortfolioEngine {
    private final Long2ObjectHashMap<Position> positions = new Long2ObjectHashMap<>();
    private final RiskEngine riskEngine;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final PositionSnapshotEncoder snapshotEncoder = new PositionSnapshotEncoder();
    private final PositionSnapshotDecoder snapshotDecoder = new PositionSnapshotDecoder();
    private final PositionEventEncoder positionEventEncoder = new PositionEventEncoder();
    private final UnsafeBuffer snapshotBuffer =
        new UnsafeBuffer(new byte[MessageHeaderEncoder.ENCODED_LENGTH + PositionSnapshotEncoder.BLOCK_LENGTH]);
    private final UnsafeBuffer eventBuffer =
        new UnsafeBuffer(new byte[MessageHeaderEncoder.ENCODED_LENGTH + PositionEventEncoder.BLOCK_LENGTH]);
    private final UnsafeBuffer countBuffer = new UnsafeBuffer(new byte[Integer.BYTES]);
    private Cluster cluster;

    public PortfolioEngineImpl(final RiskEngine riskEngine) {
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
    }

    /**
     * Pre-allocates a position for a configured venue/instrument pair.
     *
     * <p>This is called during cluster startup. If the position already exists
     * from snapshot load or another config path, the existing object is reused.</p>
     */
    @Override
    public void initPosition(final int venueId, final int instrumentId) {
        final long key = posKey(venueId, instrumentId);
        if (!positions.containsKey(key)) {
            positions.put(key, new Position(venueId, instrumentId));
        }
    }

    /**
     * Applies one fill to portfolio state and notifies RiskEngine.
     *
     * <p>BUY fills either add to/flip long inventory or cover a short. SELL fills
     * mirror that behavior for short inventory. Realized PnL is recognized only
     * on the closing quantity. If a fill crosses through zero, the residual open
     * position receives the fill price as its new average entry.</p>
     */
    @Override
    public void onFill(final ExecutionEventDecoder decoder) {
        final Position position = position(decoder.venueId(), decoder.instrumentId());
        if (decoder.side() == Side.BUY) {
            updateOnBuy(position, decoder.fillPriceScaled(), decoder.fillQtyScaled());
        } else {
            updateOnSell(position, decoder.fillPriceScaled(), decoder.fillQtyScaled());
        }
        riskEngine.updatePositionSnapshot(position.venueId, position.instrumentId, position.netQtyScaled);
    }

    @Override
    public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) {
        position(venueId, instrumentId).unrealizedPnlScaled = unrealizedPnl(venueId, instrumentId, markPriceScaled);
    }

    @Override
    public long getNetQtyScaled(final int venueId, final int instrumentId) {
        final Position position = positions.get(posKey(venueId, instrumentId));
        return position == null ? 0L : position.netQtyScaled;
    }

    @Override
    public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) {
        final Position position = positions.get(posKey(venueId, instrumentId));
        return position == null ? 0L : position.avgEntryPriceScaled;
    }

    @Override
    public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) {
        final Position position = positions.get(posKey(venueId, instrumentId));
        if (position == null || position.netQtyScaled == 0L) {
            return 0L;
        }
        final long priceDiff = position.netQtyScaled > 0L
            ? markPriceScaled - position.avgEntryPriceScaled
            : position.avgEntryPriceScaled - markPriceScaled;
        return signedMulDiv(priceDiff, Math.abs(position.netQtyScaled));
    }

    /**
     * Reconciles internal inventory to an externally observed venue balance.
     *
     * <p>RecoveryCoordinator uses this after REST balance reconciliation. The
     * method updates quantity only; it leaves average entry and realized PnL
     * untouched because a balance adjustment is not a trade and has no execution
     * price.</p>
     */
    @Override
    public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) {
        final Position position = position(venueId, instrumentId);
        position.netQtyScaled = Math.round(balanceUnscaled * Ids.SCALE);
        if (position.netQtyScaled == 0L) {
            position.avgEntryPriceScaled = 0L;
        }
        riskEngine.updatePositionSnapshot(venueId, instrumentId, position.netQtyScaled);
    }

    @Override
    public long getTotalRealizedPnlScaled() {
        long total = 0L;
        for (Position position : positions.values()) {
            total += position.realizedPnlScaled;
        }
        return total;
    }

    @Override
    public long getTotalUnrealizedPnlScaled() {
        long total = 0L;
        for (Position position : positions.values()) {
            total += position.unrealizedPnlScaled;
        }
        return total;
    }

    @Override
    public void writeSnapshot(final ExclusivePublication snapshotPublication) {
        if (snapshotPublication == null) {
            return;
        }
        countBuffer.putInt(0, positions.size(), ByteOrder.LITTLE_ENDIAN);
        offer(snapshotPublication, countBuffer, Integer.BYTES);
        for (Position position : positions.values()) {
            final int length = encodeSnapshot(position, snapshotBuffer, 0);
            offer(snapshotPublication, snapshotBuffer, length);
        }
    }

    @Override
    public void loadSnapshot(final Image snapshotImage) {
        if (snapshotImage == null) {
            return;
        }
        final List<byte[]> fragments = new ArrayList<>();
        while (fragments.isEmpty()) {
            final int polled = snapshotImage.poll((buffer, offset, length, header) -> {
                final byte[] copy = new byte[length];
                buffer.getBytes(offset, copy);
                fragments.add(copy);
            }, Integer.MAX_VALUE);
            if (polled == 0) {
                Thread.onSpinWait();
            }
        }
        loadSnapshotFragments(fragments);
    }

    /**
     * Emits one PositionEvent per position for daily reporting.
     *
     * <p>DailyResetTimer calls this before clearing daily counters so external
     * reporting can archive the final realized/unrealized PnL snapshot.</p>
     */
    @Override
    public void archiveDailyPnl(final Publication egressPublication) {
        if (egressPublication == null) {
            return;
        }
        for (Position position : positions.values()) {
            positionEventEncoder
                .wrapAndApplyHeader(eventBuffer, 0, headerEncoder)
                .venueId(position.venueId)
                .instrumentId(position.instrumentId)
                .netQtyScaled(position.netQtyScaled)
                .avgEntryPriceScaled(position.avgEntryPriceScaled)
                .realizedPnlScaled(position.realizedPnlScaled)
                .unrealizedPnlScaled(position.unrealizedPnlScaled)
                .snapshotClusterTime(nowMicros())
                .triggeringClOrdId(0L);
            offer(egressPublication, eventBuffer, MessageHeaderEncoder.ENCODED_LENGTH + positionEventEncoder.encodedLength());
        }
    }

    @Override
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void resetAll() {
        positions.clear();
    }

    public int positionCount() {
        return positions.size();
    }

    public long realizedPnl(final int venueId, final int instrumentId) {
        final Position position = positions.get(posKey(venueId, instrumentId));
        return position == null ? 0L : position.realizedPnlScaled;
    }

    List<byte[]> snapshotFragments() {
        final List<byte[]> fragments = new ArrayList<>();
        countBuffer.putInt(0, positions.size(), ByteOrder.LITTLE_ENDIAN);
        fragments.add(copy(countBuffer, 0, Integer.BYTES));
        for (Position position : positions.values()) {
            final int length = encodeSnapshot(position, snapshotBuffer, 0);
            fragments.add(copy(snapshotBuffer, 0, length));
        }
        return fragments;
    }

    void loadSnapshotFragments(final List<byte[]> fragments) {
        if (fragments.isEmpty()) {
            return;
        }
        final int count = new UnsafeBuffer(fragments.get(0)).getInt(0, ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            loadSnapshotRecord(new UnsafeBuffer(fragments.get(i + 1)), 0);
        }
    }

    int encodeSnapshot(final Position position, final UnsafeBuffer buffer, final int offset) {
        snapshotEncoder
            .wrapAndApplyHeader(buffer, offset, headerEncoder)
            .venueId(position.venueId)
            .instrumentId(position.instrumentId)
            .netQtyScaled(position.netQtyScaled)
            .avgEntryPriceScaled(position.avgEntryPriceScaled)
            .realizedPnlScaled(position.realizedPnlScaled);
        return MessageHeaderEncoder.ENCODED_LENGTH + snapshotEncoder.encodedLength();
    }

    private void loadSnapshotRecord(final DirectBuffer buffer, final int offset) {
        snapshotDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        final Position position = position(snapshotDecoder.venueId(), snapshotDecoder.instrumentId());
        position.netQtyScaled = snapshotDecoder.netQtyScaled();
        position.avgEntryPriceScaled = snapshotDecoder.avgEntryPriceScaled();
        position.realizedPnlScaled = snapshotDecoder.realizedPnlScaled();
    }

    private void updateOnBuy(final Position position, final long fillPrice, final long fillQty) {
        if (position.netQtyScaled >= 0L) {
            position.avgEntryPriceScaled = ScaledMath.vwap(position.avgEntryPriceScaled, position.netQtyScaled, fillPrice, fillQty);
            position.netQtyScaled += fillQty;
            return;
        }

        final long closingQty = Math.min(fillQty, Math.abs(position.netQtyScaled));
        position.realizedPnlScaled += signedMulDiv(position.avgEntryPriceScaled - fillPrice, closingQty);
        position.netQtyScaled += fillQty;
        if (position.netQtyScaled > 0L) {
            position.avgEntryPriceScaled = fillPrice;
        } else if (position.netQtyScaled == 0L) {
            position.avgEntryPriceScaled = 0L;
        }
    }

    private void updateOnSell(final Position position, final long fillPrice, final long fillQty) {
        if (position.netQtyScaled <= 0L) {
            position.avgEntryPriceScaled = ScaledMath.vwap(position.avgEntryPriceScaled, Math.abs(position.netQtyScaled), fillPrice, fillQty);
            position.netQtyScaled -= fillQty;
            return;
        }

        final long closingQty = Math.min(fillQty, position.netQtyScaled);
        position.realizedPnlScaled += signedMulDiv(fillPrice - position.avgEntryPriceScaled, closingQty);
        position.netQtyScaled -= fillQty;
        if (position.netQtyScaled < 0L) {
            position.avgEntryPriceScaled = fillPrice;
        } else if (position.netQtyScaled == 0L) {
            position.avgEntryPriceScaled = 0L;
        }
    }

    private Position position(final int venueId, final int instrumentId) {
        final long key = posKey(venueId, instrumentId);
        Position position = positions.get(key);
        if (position == null) {
            position = new Position(venueId, instrumentId);
            positions.put(key, position);
        }
        return position;
    }

    static long posKey(final int venueId, final int instrumentId) {
        return ((long) venueId << 32) | (instrumentId & 0xffff_ffffL);
    }

    private static long signedMulDiv(final long priceDiff, final long qtyScaled) {
        final long magnitude = ScaledMath.safeMulDiv(Math.abs(priceDiff), qtyScaled, Ids.SCALE);
        return priceDiff >= 0L ? magnitude : -magnitude;
    }

    private long nowMicros() {
        return cluster == null ? 0L : cluster.time();
    }

    private static void offer(final ExclusivePublication publication, final DirectBuffer buffer, final int length) {
        while (publication.offer(buffer, 0, length) < 0) {
            Thread.onSpinWait();
        }
    }

    private static void offer(final Publication publication, final DirectBuffer buffer, final int length) {
        while (publication.offer(buffer, 0, length) < 0) {
            Thread.onSpinWait();
        }
    }

    private static byte[] copy(final DirectBuffer buffer, final int offset, final int length) {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return bytes;
    }
}
