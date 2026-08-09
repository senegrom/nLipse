package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import nlipse.math.DistanceField;

class ContourGeometryTest {
    @Test
    void stitchesCellSegmentsIntoOneContinuousPolyline() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final DistanceField field = (x, y) -> x;
        final FieldGrid grid = FieldGrid.sample(field, viewport, 5, 5, 1,
                CancellationToken.NONE);

        final ContourGeometry geometry = ContourGeometry.trace(grid, field, viewport,
                new double[]{0.25}, CancellationToken.NONE);

        assertEquals(1, geometry.polylines(0).size());
        final ContourGeometry.Polyline line = geometry.polylines(0).getFirst();
        assertEquals(5, line.pointCount());
        assertFalse(line.closed());
    }

    @Test
    void closesASingleLoopRatherThanKeepingIndependentSegments() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final DistanceField field = (x, y) -> x * x + y * y;
        final FieldGrid grid = FieldGrid.sample(field, viewport, 65, 65, 1,
                CancellationToken.NONE);

        final ContourGeometry geometry = ContourGeometry.trace(grid, field, viewport,
                new double[]{0.5}, CancellationToken.NONE);

        assertEquals(1, geometry.polylines(0).size());
        final ContourGeometry.Polyline loop = geometry.polylines(0).getFirst();
        assertTrue(loop.closed());
        assertTrue(loop.pointCount() > 32);
    }
}
