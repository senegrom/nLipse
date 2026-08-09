package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FieldGridTest {
    private static final double EPSILON = 1e-12;

    @Test
    void samplesExtrema() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final FieldGrid grid = FieldGrid.sample((x, y) -> x + y, viewport,
                3, 3, 1, CancellationToken.NONE);
        final FieldExtrema extrema = grid.getExtrema().orElseThrow();

        assertEquals(-2, extrema.minimum(), EPSILON);
        assertEquals(2, extrema.maximum(), EPSILON);
        assertEquals(-1, extrema.minimumPoint().x(), EPSILON);
        assertEquals(-1, extrema.minimumPoint().y(), EPSILON);
    }

    @Test
    void constantFieldPreservesItsActualExtrema() {
        final FieldGrid grid = FieldGrid.sample((x, y) -> 7,
                new Viewport(-1, 1, -1, 1), 4, 4, 1, CancellationToken.NONE);
        final FieldExtrema extrema = grid.getExtrema().orElseThrow();

        assertEquals(7, extrema.minimum(), EPSILON);
        assertEquals(7, extrema.maximum(), EPSILON);
    }

    @Test
    void fieldWithoutFiniteSamplesHasNoExtrema() {
        final FieldGrid grid = FieldGrid.sample((x, y) -> x < 0
                        ? Double.NaN : Double.POSITIVE_INFINITY,
                new Viewport(-1, 1, -1, 1), 4, 4, 1, CancellationToken.NONE);

        assertTrue(grid.getExtrema().isEmpty());
    }

    @Test
    void largeGridSamplesEveryPointAndReportsItsFootprint() {
        final AtomicInteger calls = new AtomicInteger();
        final Set<String> threadNames = ConcurrentHashMap.newKeySet();
        final FieldGrid grid = FieldGrid.sample((x, y) -> {
            calls.incrementAndGet();
            threadNames.add(Thread.currentThread().getName());
            return x - y;
        }, new Viewport(-2, 3, -4, 5), 512, 300, 1, CancellationToken.NONE);
        final FieldExtrema extrema = grid.getExtrema().orElseThrow();

        assertEquals(512 * 300, calls.get());
        assertEquals(-7, extrema.minimum(), EPSILON);
        assertEquals(7, extrema.maximum(), EPSILON);
        assertEquals(512L * 300 * Double.BYTES + (512L + 300) * Integer.BYTES + 128,
                grid.estimatedBytes());
        assertFalse(threadNames.stream().anyMatch(name -> name.contains("commonPool")));
    }

    @Test
    void coarseGridStillIncludesLastPixel() {
        final FieldGrid grid = FieldGrid.sample((x, y) -> x,
                new Viewport(0, 10, 0, 10), 10, 8, 4, CancellationToken.NONE);

        assertEquals(9, grid.getPixelX(grid.getColumns() - 1));
        assertEquals(7, grid.getPixelY(grid.getRows() - 1));
    }

    @Test
    void coarseningReusesExistingSamplesWithoutCallingTheField() {
        final AtomicInteger calls = new AtomicInteger();
        final Viewport viewport = new Viewport(0, 9, 0, 7);
        final FieldGrid full = FieldGrid.sample((x, y) -> {
            calls.incrementAndGet();
            return x + 10 * y;
        }, viewport, 10, 8, 1, CancellationToken.NONE);
        final int callsAfterFullSample = calls.get();

        final FieldGrid coarse = full.coarsen(4, viewport);

        assertEquals(callsAfterFullSample, calls.get());
        assertEquals(4, coarse.getStep());
        assertEquals(9, coarse.getPixelX(coarse.getColumns() - 1));
        assertEquals(7, coarse.getPixelY(coarse.getRows() - 1));
        assertEquals(full.getValue(0, 0), coarse.getValue(0, 0), EPSILON);
        assertEquals(full.getValue(9, 7),
                coarse.getValue(coarse.getColumns() - 1, coarse.getRows() - 1), EPSILON);
        assertEquals(full.getValue(2, 2), coarse.finiteValueAtPixel(2, 2), EPSILON);
    }
}
