package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FieldGridTest {
    private static final double EPSILON = 1e-12;

    @Test
    void samplesExtremaAndInterpolates() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final FieldGrid grid = FieldGrid.sample((x, y) -> x + y, viewport,
                3, 3, 1, CancellationToken.NONE);

        assertEquals(-2, grid.getMinValue(), EPSILON);
        assertEquals(2, grid.getMaxValue(), EPSILON);
        assertEquals(-1, grid.getMinPoint().x(), EPSILON);
        assertEquals(-1, grid.getMinPoint().y(), EPSILON);
        assertEquals(0, grid.interpolateAtPixel(1, 1), EPSILON);
    }

    @Test
    void coarseGridStillIncludesLastPixel() {
        final FieldGrid grid = FieldGrid.sample((x, y) -> x,
                new Viewport(0, 10, 0, 10), 10, 8, 4, CancellationToken.NONE);

        assertEquals(9, grid.getPixelX(grid.getColumns() - 1));
        assertEquals(7, grid.getPixelY(grid.getRows() - 1));
    }
}
