package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import nlipse.math.DistanceField;
import nlipse.model.CurveType;
import nlipse.model.Focus;

class WorldFieldCacheTest {
    private static final double EPSILON = 1e-12;
    private static final FieldIdentity IDENTITY = new FieldIdentity(CurveType.LIPSE,
            CurveType.LIPSE.defaultParameter(), List.of(new Focus(0, 0, 1)));

    @Test
    void integerPixelPanSamplesOnlyNewlyExposedStrips() {
        final int width = 257;
        final int height = 193;
        final int panX = 11;
        final int panY = -7;
        final Viewport initial = new Viewport(-3.7, 5.2, -2.4, 4.9);
        final Viewport panned = initial.panPixels(panX, panY, width, height);
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x + 3 * y;
        };
        final WorldFieldCache cache = new WorldFieldCache(64L * 1024 * 1024);

        final FieldGrid first = cache.sample(IDENTITY, field, initial, width, height,
                CancellationToken.NONE);
        final int afterFirst = evaluations.get();
        final FieldGrid second = cache.sample(IDENTITY, field, panned, width, height,
                CancellationToken.NONE);

        final int overlap = (width - Math.abs(panX)) * (height - Math.abs(panY));
        final int newlyExposed = width * height - overlap;
        assertEquals(width * height, afterFirst);
        assertEquals(newlyExposed, evaluations.get() - afterFirst);
        assertEquals(overlap, cache.reusedSamples());
        assertTrue(cache.tileHits() > 0);

        // New global (31, 13) is the old global (20, 20) after this pan.
        assertEquals(first.getValue(20, 20), second.getValue(31, 13), EPSILON);
    }

    @Test
    void cancelledSamplingStillRestoresTheMemoryBudget() {
        final long oneTileBudget = 9_000;
        final AtomicInteger evaluations = new AtomicInteger();
        final WorldFieldCache cache = new WorldFieldCache(oneTileBudget);
        final CancellationToken token = () -> evaluations.get() > 200;

        assertThrows(RenderCancelledException.class, () -> cache.sample(IDENTITY, (x, y) -> {
            evaluations.incrementAndGet();
            return x + y;
        }, new Viewport(-2, 2, -2, 2), 257, 257, token));

        assertTrue(cache.cachedBytes() <= oneTileBudget);
    }

    @Test
    void fractionalPixelShiftDoesNotReuseAWorldLattice() {
        final int width = 129;
        final int height = 97;
        final Viewport initial = new Viewport(-2, 2, -1.5, 1.5);
        final double halfPixel = initial.width() / (width - 1.0) * 0.5;
        final Viewport shifted = new Viewport(initial.xMin() + halfPixel,
                initial.xMax() + halfPixel, initial.yMin(), initial.yMax());
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x + y;
        };
        final WorldFieldCache cache = new WorldFieldCache(32L * 1024 * 1024);

        cache.sample(IDENTITY, field, initial, width, height, CancellationToken.NONE);
        final int afterFirst = evaluations.get();
        cache.sample(IDENTITY, field, shifted, width, height, CancellationToken.NONE);

        assertEquals(width * height, evaluations.get() - afterFirst);
        assertEquals(0, cache.reusedSamples());
    }

    @Test
    void zoomCreatesANewLatticeInsteadOfReusingMisalignedSamples() {
        final int width = 129;
        final int height = 97;
        final Viewport initial = new Viewport(-2, 2, -1.5, 1.5);
        final Viewport zoomed = initial.zoomAtPixel(64, 48, width, height, 0.9);
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x - y;
        };
        final WorldFieldCache cache = new WorldFieldCache(32L * 1024 * 1024);

        cache.sample(IDENTITY, field, initial, width, height, CancellationToken.NONE);
        final int afterFirst = evaluations.get();
        cache.sample(IDENTITY, field, zoomed, width, height, CancellationToken.NONE);

        assertEquals(width * height, evaluations.get() - afterFirst);
        assertEquals(0, cache.reusedSamples());
    }

    @Test
    void nearlyAlignedButBitwiseDifferentLatticeIsNotReused() {
        final int width = 129;
        final int height = 97;
        final Viewport initial = new Viewport(-2, 2, -1.5, 1.5);
        final double shiftedMinimum = Math.nextUp(initial.xMin());
        final double shift = shiftedMinimum - initial.xMin();
        final Viewport shifted = new Viewport(shiftedMinimum,
                initial.xMax() + shift, initial.yMin(), initial.yMax());
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x + y;
        };
        final WorldFieldCache cache = new WorldFieldCache(32L * 1024 * 1024);

        cache.sample(IDENTITY, field, initial, width, height, CancellationToken.NONE);
        final int afterFirst = evaluations.get();
        cache.sample(IDENTITY, field, shifted, width, height, CancellationToken.NONE);

        assertEquals(width * height, evaluations.get() - afterFirst);
        assertEquals(0, cache.reusedSamples());
    }

    @Test
    void repeatedIntegerPansReuseByExactIndexRatherThanCoordinateTolerance() {
        final int width = 129;
        final int height = 97;
        final Viewport initial = new Viewport(-2.3, 3.1, -1.7, 2.9);
        final WorldFieldCache cache = new WorldFieldCache(64L * 1024 * 1024);
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x - 2 * y;
        };

        cache.sample(IDENTITY, field, initial, width, height, CancellationToken.NONE);
        Viewport current = initial;
        current = current.panPixels(9, -4, width, height);
        cache.sample(IDENTITY, field, current, width, height, CancellationToken.NONE);
        current = current.panPixels(-3, 11, width, height);
        cache.sample(IDENTITY, field, current, width, height, CancellationToken.NONE);
        current = current.panPixels(-6, -7, width, height);
        final int beforeReturn = evaluations.get();
        cache.sample(IDENTITY, field, current, width, height, CancellationToken.NONE);

        assertEquals(initial, current);
        assertEquals(0, evaluations.get() - beforeReturn);
    }

    @Test
    void equivalentBoundsWithoutLineageDoNotClaimAnExistingLattice() {
        final int width = 129;
        final int height = 97;
        final Viewport initial = new Viewport(-2, 2, -1.5, 1.5);
        final Viewport panned = initial.panPixels(5, -3, width, height);
        final Viewport reconstructed = new Viewport(panned.xMin(), panned.xMax(),
                panned.yMin(), panned.yMax());
        final WorldFieldCache cache = new WorldFieldCache(64L * 1024 * 1024);
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x + y;
        };

        cache.sample(IDENTITY, field, initial, width, height, CancellationToken.NONE);
        cache.sample(IDENTITY, field, panned, width, height, CancellationToken.NONE);
        final int beforeReconstructed = evaluations.get();
        cache.sample(IDENTITY, field, reconstructed, width, height, CancellationToken.NONE);

        assertEquals(width * height, evaluations.get() - beforeReconstructed);
    }

    @Test
    void invalidatingAFieldIdentityForcesFreshSampling() {
        final int width = 65;
        final int height = 49;
        final WorldFieldCache cache = new WorldFieldCache(16L * 1024 * 1024);
        final AtomicInteger evaluations = new AtomicInteger();
        final DistanceField field = (x, y) -> {
            evaluations.incrementAndGet();
            return x - y;
        };
        final Viewport viewport = new Viewport(-2, 2, -1, 1);

        cache.sample(IDENTITY, field, viewport, width, height, CancellationToken.NONE);
        final int afterFirst = evaluations.get();
        cache.invalidate(IDENTITY);
        cache.sample(IDENTITY, field, viewport, width, height, CancellationToken.NONE);

        assertEquals(width * height, afterFirst);
        assertEquals(width * height, evaluations.get() - afterFirst);
    }

}
