package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ViewportTest {
    private static final double EPSILON = 1e-12;

    @Test
    void mapsBoundsToFirstAndLastPixels() {
        final Viewport viewport = new Viewport(-3, 3, -2, 2);

        assertEquals(0, viewport.pixelX(-3, 101), EPSILON);
        assertEquals(100, viewport.pixelX(3, 101), EPSILON);
        assertEquals(0, viewport.pixelY(2, 81), EPSILON);
        assertEquals(80, viewport.pixelY(-2, 81), EPSILON);
        assertEquals(-3, viewport.worldX(0, 101), EPSILON);
        assertEquals(3, viewport.worldX(100, 101), EPSILON);
        assertEquals(2, viewport.worldY(0, 81), EPSILON);
        assertEquals(-2, viewport.worldY(80, 81), EPSILON);
    }

    @Test
    void transformsRoundTrip() {
        final Viewport viewport = new Viewport(-10, 7, -4, 13);

        assertEquals(2.25, viewport.worldX(viewport.pixelX(2.25, 257), 257), EPSILON);
        assertEquals(-1.75, viewport.worldY(viewport.pixelY(-1.75, 193), 193), EPSILON);
    }

    @Test
    void panAndZoomAreCursorCentred() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        final Viewport panned = viewport.panPixels(10, -20, 101, 101);
        assertEquals(-1.2, panned.getXMin(), EPSILON);
        assertEquals(-1.4, panned.getYMin(), EPSILON);

        final Viewport zoomed = viewport.zoomAtPixel(50, 50, 101, 101, 0.5);
        assertEquals(-0.5, zoomed.getXMin(), EPSILON);
        assertEquals(0.5, zoomed.getXMax(), EPSILON);
    }

    @Test
    void invalidBoundsAndResolutionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Viewport(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Viewport(Double.NaN, 1, -1, 1));
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        assertThrows(IllegalArgumentException.class, () -> viewport.worldX(0, 1));
    }
}
