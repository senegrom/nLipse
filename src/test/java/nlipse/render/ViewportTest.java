package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(-1.2, panned.xMin(), EPSILON);
        assertEquals(-1.4, panned.yMin(), EPSILON);

        final Viewport zoomed = viewport.zoomAtPixel(50, 50, 101, 101, 0.5);
        assertEquals(-0.5, zoomed.xMin(), EPSILON);
        assertEquals(0.5, zoomed.xMax(), EPSILON);
    }

    @Test
    void invalidBoundsAndResolutionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Viewport(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Viewport(Double.NaN, 1, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Viewport(-Double.MAX_VALUE, Double.MAX_VALUE, -1, 1));
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        assertThrows(IllegalArgumentException.class, () -> viewport.worldX(0, 1));
    }

    @Test
    void transformationsStopAtRepresentableBounds() {
        Viewport viewport = new Viewport(-1, 1, -1, 1);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            viewport = viewport.zoomAtPixel(50, 50, 101, 101, 0.85);
        }

        assertTrue(viewport.xMin() < viewport.xMax());
        assertTrue(viewport.yMin() < viewport.yMax());
        assertEquals(viewport, viewport.panPixels(Double.MAX_VALUE, 0, 101, 101));

        final Viewport normal = new Viewport(-1, 1, -1, 1);
        assertEquals(normal, normal.zoomAtPixel(50, 50, 101, 101, Double.MAX_VALUE));
    }
    @Test
    void integerPansCarryExactLatticeLineageAndRoundTripBitwise() {
        final int width = 257;
        final int height = 193;
        final Viewport initial = new Viewport(-3.7, 5.2, -2.4, 4.9);
        final SamplingLattice root = initial.samplingLattice(width, height);
        final Viewport panned = initial.panPixels(37, -19, width, height);
        final SamplingLattice shifted = panned.samplingLattice(width, height);

        assertEquals(root.originXBits(), shifted.originXBits());
        assertEquals(root.originYBits(), shifted.originYBits());
        assertEquals(root.stepXBits(), shifted.stepXBits());
        assertEquals(root.stepYBits(), shifted.stepYBits());
        assertEquals(-37, shifted.offsetX());
        assertEquals(19, shifted.offsetY());

        final Viewport returned = panned.panPixels(-37, 19, width, height);
        assertEquals(initial, returned);
        for (int pixel : new int[] {0, 1, 128, 255, 256}) {
            assertEquals(Double.doubleToLongBits(initial.worldX(pixel, width)),
                    Double.doubleToLongBits(returned.worldX(pixel, width)));
        }
        for (int pixel : new int[] {0, 1, 96, 191, 192}) {
            assertEquals(Double.doubleToLongBits(initial.worldY(pixel, height)),
                    Double.doubleToLongBits(returned.worldY(pixel, height)));
        }
    }

    @Test
    void fractionalPanAndResolutionChangeStartNewLattices() {
        final Viewport initial = new Viewport(-2, 2, -1.5, 1.5);
        final SamplingLattice root = initial.samplingLattice(129, 97);
        final Viewport integerPan = initial.panPixels(4, 3, 129, 97);
        final Viewport fractionalPan = integerPan.panPixels(0.5, 0, 129, 97);
        final SamplingLattice fractional = fractionalPan.samplingLattice(129, 97);
        final SamplingLattice resized = integerPan.samplingLattice(257, 193);

        assertTrue(root.originXBits() != fractional.originXBits());
        assertEquals(0, fractional.offsetX());
        assertEquals(0, fractional.offsetY());
        assertTrue(root.stepXBits() != resized.stepXBits());
        assertEquals(0, resized.offsetX());
        assertEquals(0, resized.offsetY());
    }

}
