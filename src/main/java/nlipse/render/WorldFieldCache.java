package nlipse.render;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;
import nlipse.math.DistanceField;

/**
 * Memory-bounded cache of scalar samples on world-space lattices.
 *
 * <p>A pure integer-pixel pan preserves the lattice spacing and merely changes the global
 * sample-index window. Overlapping tiles are therefore reused exactly and only newly exposed
 * sample points are evaluated.</p>
 */
final class WorldFieldCache {
    private static final int TILE_SIZE = 32;
    private static final int TILES_PER_TASK = 2;

    private final long budgetBytes;
    private final Map<LatticeKey, Lattice> lattices = new HashMap<>();
    private final Map<Long, Lattice> latticesById = new HashMap<>();
    private final Map<Long, Integer> tileCountsByLattice = new HashMap<>();
    private final Map<TileKey, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
    private final AtomicLong tileHits = new AtomicLong();
    private final AtomicLong tileMisses = new AtomicLong();
    private final AtomicLong reusedSamples = new AtomicLong();
    private final AtomicLong sampledValues = new AtomicLong();
    private long nextLatticeId;
    private long cachedBytes;

    WorldFieldCache(final long budgetBytes) {
        this.budgetBytes = Math.max(Tile.ESTIMATED_BYTES, budgetBytes);
    }

    FieldGrid sample(final FieldIdentity identity, final DistanceField field,
            final Viewport viewport, final int pixelWidth, final int pixelHeight,
            final CancellationToken token) {
        if (identity == null || field == null || viewport == null || token == null) {
            throw new IllegalArgumentException(
                    "Field identity, field, viewport and cancellation token are required");
        }
        final int pixelCount = RenderDimensions.checkedPixelCount(
                pixelWidth, pixelHeight, 2);
        token.throwIfCancelled();

        final LatticeSelection selection = selectLattice(identity, viewport,
                pixelWidth, pixelHeight);
        final long firstGlobalX = selection.offsetX();
        final long firstGlobalY = selection.offsetY();
        final long lastGlobalX = Math.addExact(firstGlobalX, pixelWidth - 1L);
        final long lastGlobalY = Math.addExact(firstGlobalY, pixelHeight - 1L);

        final long firstTileX = Math.floorDiv(firstGlobalX, TILE_SIZE);
        final long lastTileX = Math.floorDiv(lastGlobalX, TILE_SIZE);
        final long firstTileY = Math.floorDiv(firstGlobalY, TILE_SIZE);
        final long lastTileY = Math.floorDiv(lastGlobalY, TILE_SIZE);
        try {
            final List<TileRequest> requests = new ArrayList<>();
            final Map<TileKey, Tile> selectedTiles = new HashMap<>();
            synchronized (tiles) {
                for (long tileY = firstTileY; tileY <= lastTileY; tileY++) {
                    final int minimumLocalY = tileY == firstTileY
                            ? Math.floorMod(firstGlobalY, TILE_SIZE) : 0;
                    final int maximumLocalY = tileY == lastTileY
                            ? Math.floorMod(lastGlobalY, TILE_SIZE) : TILE_SIZE - 1;
                    for (long tileX = firstTileX; tileX <= lastTileX; tileX++) {
                        final int minimumLocalX = tileX == firstTileX
                                ? Math.floorMod(firstGlobalX, TILE_SIZE) : 0;
                        final int maximumLocalX = tileX == lastTileX
                                ? Math.floorMod(lastGlobalX, TILE_SIZE) : TILE_SIZE - 1;
                        final TileKey key = new TileKey(selection.lattice().id(), tileX, tileY);
                        Tile tile = tiles.get(key);
                        if (tile == null) {
                            tile = new Tile();
                            tiles.put(key, tile);
                            cachedBytes += Tile.ESTIMATED_BYTES;
                            tileCountsByLattice.merge(selection.lattice().id(),
                                    1, Integer::sum);
                            tileMisses.incrementAndGet();
                        } else {
                            tileHits.incrementAndGet();
                        }
                        requests.add(new TileRequest(key, tile,
                                minimumLocalX, maximumLocalX, minimumLocalY, maximumLocalY));
                        selectedTiles.put(key, tile);
                    }
                }
            }

            if (SamplingPool.parallelism() > 1 && requests.size() > TILES_PER_TASK) {
                SamplingPool.invoke(new SampleTilesTask(requests, 0, requests.size(),
                        selection.lattice(), field, token, reusedSamples, sampledValues));
            } else {
                sampleTiles(requests, 0, requests.size(), selection.lattice(), field, token,
                        reusedSamples, sampledValues);
            }
            token.throwIfCancelled();

            final double[] values = new double[pixelCount];
            for (int row = 0; row < pixelHeight; row++) {
                if ((row & 31) == 0) {
                    token.throwIfCancelled();
                }
                final long globalY = firstGlobalY + row;
                final long tileY = Math.floorDiv(globalY, TILE_SIZE);
                final int localY = Math.floorMod(globalY, TILE_SIZE);
                final int targetOffset = row * pixelWidth;
                int column = 0;
                while (column < pixelWidth) {
                    final long globalX = firstGlobalX + column;
                    final long tileX = Math.floorDiv(globalX, TILE_SIZE);
                    final int localX = Math.floorMod(globalX, TILE_SIZE);
                    final int length = Math.min(pixelWidth - column, TILE_SIZE - localX);
                    final Tile tile = selectedTiles.get(
                            new TileKey(selection.lattice().id(), tileX, tileY));
                    if (tile == null) {
                        throw new IllegalStateException("A required world-space tile is missing");
                    }
                    tile.copyRow(localX, localY, values, targetOffset + column, length);
                    column += length;
                }
            }
            return FieldGrid.fromFullResolutionValues(
                    viewport, pixelWidth, pixelHeight, values, token);
        } finally {
            evictOversizedCache();
        }
    }

    private LatticeSelection selectLattice(final FieldIdentity identity,
            final Viewport viewport, final int pixelWidth, final int pixelHeight) {
        final SamplingLattice requested = viewport.samplingLattice(pixelWidth, pixelHeight);
        final LatticeKey key = LatticeKey.from(identity, requested);
        synchronized (tiles) {
            Lattice lattice = lattices.get(key);
            if (lattice == null) {
                lattice = new Lattice(++nextLatticeId, key, requested);
                lattices.put(key, lattice);
                latticesById.put(lattice.id(), lattice);
            }
            return new LatticeSelection(lattice, requested.offsetX(), requested.offsetY());
        }
    }

    private static void sampleTiles(final List<TileRequest> requests, final int from,
            final int to, final Lattice lattice, final DistanceField field,
            final CancellationToken token, final AtomicLong reusedSamples,
            final AtomicLong sampledValues) {
        long reused = 0;
        long sampled = 0;
        try {
            for (int index = from; index < to; index++) {
                token.throwIfCancelled();
                final TileRequest request = requests.get(index);
                final long tileOriginX = Math.multiplyExact(request.key().tileX(), TILE_SIZE);
                final long tileOriginY = Math.multiplyExact(request.key().tileY(), TILE_SIZE);
                synchronized (request.tile()) {
                    for (int localY = request.minimumLocalY();
                            localY <= request.maximumLocalY(); localY++) {
                        final int firstIndex = localY * TILE_SIZE + request.minimumLocalX();
                        final int afterLastIndex = localY * TILE_SIZE
                                + request.maximumLocalX() + 1;
                        if (request.tile().present.nextClearBit(firstIndex) >= afterLastIndex) {
                            reused += afterLastIndex - firstIndex;
                            continue;
                        }
                        final long globalY = tileOriginY + localY;
                        final double worldY = lattice.worldY(globalY);
                        for (int localX = request.minimumLocalX();
                                localX <= request.maximumLocalX(); localX++) {
                            final int valueIndex = localY * TILE_SIZE + localX;
                            if (request.tile().present.get(valueIndex)) {
                                reused++;
                                continue;
                            }
                            if ((localX & 15) == 0) {
                                token.throwIfCancelled();
                            }
                            final long globalX = tileOriginX + localX;
                            final double worldX = lattice.worldX(globalX);
                            request.tile().values[valueIndex] = field.value(worldX, worldY);
                            request.tile().present.set(valueIndex);
                            sampled++;
                        }
                    }
                }
            }
        } finally {
            reusedSamples.addAndGet(reused);
            sampledValues.addAndGet(sampled);
        }
    }

    /** Removes all cached samples for one mathematical field identity. */
    void invalidate(final FieldIdentity identity) {
        if (identity == null) {
            return;
        }
        synchronized (tiles) {
            final java.util.HashSet<Long> latticeIds = new java.util.HashSet<>();
            for (final Lattice lattice : latticesById.values()) {
                if (lattice.key().identity().equals(identity)) {
                    latticeIds.add(lattice.id());
                }
            }
            if (latticeIds.isEmpty()) {
                return;
            }
            final Iterator<Map.Entry<TileKey, Tile>> iterator = tiles.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<TileKey, Tile> entry = iterator.next();
                if (latticeIds.contains(entry.getKey().latticeId())) {
                    iterator.remove();
                    cachedBytes -= Tile.ESTIMATED_BYTES;
                }
            }
            for (final long latticeId : latticeIds) {
                final Lattice lattice = latticesById.remove(latticeId);
                tileCountsByLattice.remove(latticeId);
                if (lattice != null) {
                    lattices.remove(lattice.key(), lattice);
                }
            }
        }
    }

    private void evictOversizedCache() {
        synchronized (tiles) {
            final Iterator<Map.Entry<TileKey, Tile>> iterator = tiles.entrySet().iterator();
            while (cachedBytes > budgetBytes && iterator.hasNext()) {
                final Map.Entry<TileKey, Tile> entry = iterator.next();
                iterator.remove();
                cachedBytes -= Tile.ESTIMATED_BYTES;
                releaseLatticeTile(entry.getKey().latticeId());
            }
        }
    }

    private void releaseLatticeTile(final long latticeId) {
        final Integer count = tileCountsByLattice.get(latticeId);
        if (count == null) {
            return;
        }
        if (count > 1) {
            tileCountsByLattice.put(latticeId, count - 1);
            return;
        }
        tileCountsByLattice.remove(latticeId);
        final Lattice lattice = latticesById.remove(latticeId);
        if (lattice == null) {
            return;
        }
        lattices.remove(lattice.key(), lattice);
    }

    long tileHits() {
        return tileHits.get();
    }

    long tileMisses() {
        return tileMisses.get();
    }

    long reusedSamples() {
        return reusedSamples.get();
    }

    long sampledValues() {
        return sampledValues.get();
    }

    long cachedBytes() {
        synchronized (tiles) {
            return cachedBytes;
        }
    }

    private record Lattice(long id, LatticeKey key, SamplingLattice coordinates) {
        double worldX(final long globalX) {
            return coordinates.worldXAtIndex(globalX);
        }

        double worldY(final long globalY) {
            return coordinates.worldYAtIndex(globalY);
        }
    }

    private record LatticeKey(FieldIdentity identity, long originXBits, long rootXMaxBits,
            long originYBits, long rootYMinBits, long stepXBits, long stepYBits,
            int pixelWidth, int pixelHeight) {
        static LatticeKey from(final FieldIdentity identity, final SamplingLattice lattice) {
            return new LatticeKey(identity, lattice.originXBits(), lattice.rootXMaxBits(),
                    lattice.originYBits(), lattice.rootYMinBits(), lattice.stepXBits(),
                    lattice.stepYBits(), lattice.pixelWidth(), lattice.pixelHeight());
        }
    }

    private record LatticeSelection(Lattice lattice, long offsetX, long offsetY) {
    }

    private record TileKey(long latticeId, long tileX, long tileY) {
    }

    private record TileRequest(TileKey key, Tile tile,
            int minimumLocalX, int maximumLocalX,
            int minimumLocalY, int maximumLocalY) {
    }

    private static final class Tile {
        private static final long ESTIMATED_BYTES = 192L
                + (long) TILE_SIZE * TILE_SIZE * Double.BYTES
                + ((long) TILE_SIZE * TILE_SIZE + 7) / 8;

        private final double[] values = new double[TILE_SIZE * TILE_SIZE];
        private final BitSet present = new BitSet(values.length);

        synchronized void copyRow(final int sourceX, final int sourceY,
                final double[] target,
                final int targetOffset, final int length) {
            final int sourceOffset = sourceY * TILE_SIZE + sourceX;
            final int missing = present.nextClearBit(sourceOffset);
            if (missing < sourceOffset + length) {
                throw new IllegalStateException("A world-space tile contains an unsampled value");
            }
            System.arraycopy(values, sourceOffset, target, targetOffset, length);
        }
    }

    private static final class SampleTilesTask extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final transient List<TileRequest> requests;
        private final int from;
        private final int to;
        private final transient Lattice lattice;
        private final transient DistanceField field;
        private final transient CancellationToken token;
        private final AtomicLong reusedSamples;
        private final AtomicLong sampledValues;

        SampleTilesTask(final List<TileRequest> requests, final int from, final int to,
                final Lattice lattice, final DistanceField field, final CancellationToken token,
                final AtomicLong reusedSamples, final AtomicLong sampledValues) {
            this.requests = requests;
            this.from = from;
            this.to = to;
            this.lattice = lattice;
            this.field = field;
            this.token = token;
            this.reusedSamples = reusedSamples;
            this.sampledValues = sampledValues;
        }

        @Override
        protected void compute() {
            token.throwIfCancelled();
            if (to - from <= TILES_PER_TASK) {
                sampleTiles(requests, from, to, lattice, field, token,
                        reusedSamples, sampledValues);
                return;
            }
            final int middle = (from + to) >>> 1;
            invokeAll(
                    new SampleTilesTask(requests, from, middle, lattice, field, token,
                            reusedSamples, sampledValues),
                    new SampleTilesTask(requests, middle, to, lattice, field, token,
                            reusedSamples, sampledValues));
        }
    }
}
