package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import nlipse.math.DistanceField;

class MarchingSquaresTest {
    private static final double EPSILON = 1e-12;

    @Test
    void tracesStraightVerticalContour() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final DistanceField field = (x, y) -> x;
        final FieldGrid grid = FieldGrid.sample(field, viewport, 3, 3, 1, CancellationToken.NONE);
        final List<double[]> segments = new ArrayList<>();

        final int count = MarchingSquares.trace(grid, field, viewport, 0,
                CancellationToken.NONE,
                (x1, y1, x2, y2) -> segments.add(new double[]{x1, y1, x2, y2}));

        assertEquals(2, count);
        for (final double[] segment : segments) {
            assertEquals(1, segment[0], EPSILON);
            assertEquals(1, segment[2], EPSILON);
        }
    }

    @Test
    void ambiguousSaddleUsesCentreSampleAndProducesTwoSegments() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final DistanceField field = (x, y) -> x * y + 0.1;
        final FieldGrid grid = FieldGrid.sample(field, viewport, 2, 2, 1, CancellationToken.NONE);

        final int count = MarchingSquares.trace(grid, field, viewport, 0,
                CancellationToken.NONE, (x1, y1, x2, y2) -> { });

        assertEquals(2, count);
    }

    @Test
    void coarsePreviewAdaptivelyFindsHiddenCentralLoop() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final DistanceField field = (x, y) -> 0.3 - x * x - y * y;
        final FieldGrid grid = FieldGrid.sample(field, viewport, 5, 5, 4, CancellationToken.NONE);

        final int count = MarchingSquares.trace(grid, field, viewport, 0,
                CancellationToken.NONE, (x1, y1, x2, y2) -> { });

        assertTrue(count > 0);
    }
}
