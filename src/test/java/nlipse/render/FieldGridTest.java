package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        final FieldGrid grid = FieldGrid.sample((x, y) -> {
            calls.incrementAndGet();
            return x - y;
        }, new Viewport(-2, 3, -4, 5), 512, 300, 1, CancellationToken.NONE);
        final FieldExtrema extrema = grid.getExtrema().orElseThrow();

        assertEquals(512 * 300, calls.get());
        assertEquals(-7, extrema.minimum(), EPSILON);
        assertEquals(7, extrema.maximum(), EPSILON);
        assertEquals(512L * 300 * Double.BYTES + (512L + 300) * Integer.BYTES + 128,
                grid.estimatedBytes());
    }

    @Test
    void coarseGridStillIncludesLastPixel() {
        final FieldGrid grid = FieldGrid.sample((x, y) -> x,
                new Viewport(0, 10, 0, 10), 10, 8, 4, CancellationToken.NONE);

        assertEquals(9, grid.getPixelX(grid.getColumns() - 1));
        assertEquals(7, grid.getPixelY(grid.getRows() - 1));
    }
}
