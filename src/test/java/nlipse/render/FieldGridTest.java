package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FieldGridTest {
    private static final double EPSILON = 1e-12;

    @Test
    void samplesExtrema() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final FieldGrid grid = FieldGrid.sample((x, y) -> x + y, viewport,
                3, 3, 1, CancellationToken.NONE);

        assertEquals(-2, grid.getMinValue(), EPSILON);
        assertEquals(2, grid.getMaxValue(), EPSILON);
        assertEquals(-1, grid.getMinPoint().x(), EPSILON);
        assertEquals(-1, grid.getMinPoint().y(), EPSILON);
    }

    @Test
    void constantFieldPreservesItsActualExtrema() {
        final FieldGrid grid = FieldGrid.sample((x, y) -> 7,
                new Viewport(-1, 1, -1, 1), 4, 4, 1, CancellationToken.NONE);

        assertEquals(7, grid.getMinValue(), EPSILON);
        assertEquals(7, grid.getMaxValue(), EPSILON);
    }

    @Test
    void largeGridSamplesEveryPointAndReportsItsFootprint() {
        final AtomicInteger calls = new AtomicInteger();
        final FieldGrid grid = FieldGrid.sample((x, y) -> {
            calls.incrementAndGet();
            return x - y;
        }, new Viewport(-2, 3, -4, 5), 512, 300, 1, CancellationToken.NONE);

        assertEquals(512 * 300, calls.get());
        assertEquals(-7, grid.getMinValue(), EPSILON);
        assertEquals(7, grid.getMaxValue(), EPSILON);
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
