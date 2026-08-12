package nlipse.render;

import java.util.Random;
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
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        assertThrows(IllegalArgumentException.class, () -> viewport.worldX(0, 1));
    }

    @Test
    void fullFiniteRangeMapsInteriorCoordinatesWithoutOverflow() {
        final double maximum = Double.MAX_VALUE;
        final Viewport viewport = new Viewport(-maximum, maximum, -maximum, maximum);

        assertEquals(-maximum, viewport.worldX(0, 4), 0);
        assertEquals(maximum, viewport.worldX(3, 4), 0);
        assertTrue(Double.isFinite(viewport.worldX(1, 4)));
        assertTrue(Double.isFinite(viewport.worldX(2, 4)));
        assertEquals(1.5, viewport.pixelX(0, 4), 1e-15);
        assertEquals(1.5, viewport.pixelY(0, 4), 1e-15);

        final Viewport leftCentred = viewport.zoomAtPixel(0, 1.5, 4, 4, 0.5);
        assertEquals(-maximum, leftCentred.xMin(), 0);
        assertEquals(0, leftCentred.xMax(), 0);
    }

    @Test
    void twoPixelFullRangeMapsFractionalCoordinatesWithoutInfinity() {
        final double maximum = Double.MAX_VALUE;
        final Viewport viewport = new Viewport(-maximum, maximum, -maximum, maximum);

        assertEquals(0, viewport.worldX(0.5, 2), 0);
        assertEquals(0, viewport.worldY(0.5, 2), 0);
        assertTrue(Double.isFinite(viewport.worldX(0.25, 2)));
        assertTrue(Double.isFinite(viewport.worldY(0.25, 2)));
    }

    @Test
    void nearFullRangeCanPanTowardTheRepresentableSide() {
        final double maximum = Double.MAX_VALUE;
        final Viewport viewport = new Viewport(-maximum, maximum / 2, -1, 1);
        final Viewport panned = viewport.panPixels(-1, 0, 100, 3);

        assertTrue(panned.xMin() > viewport.xMin());
        assertTrue(panned.xMax() > viewport.xMax());
        assertTrue(Double.isFinite(panned.xMin()));
        assertTrue(Double.isFinite(panned.xMax()));
        assertEquals(viewport, viewport.panPixels(1, 0, 100, 3));
    }

    @Test
    void strictlyOutsideValuesRemainOutsideAfterPixelRounding() {
        final Viewport viewport = new Viewport(
                -5.390771529961747E-226, 6.432962249325585E-231, -1, 1);
        final int width = 893;

        assertTrue(viewport.pixelX(Math.nextDown(viewport.xMin()), width) < 0);
        assertTrue(viewport.pixelX(Math.nextUp(viewport.xMax()), width) > width - 1.0);
    }

    @Test
    void exponentBiasedRangesPreserveEndpointsAndOrdering() {
        final java.util.Random random = new java.util.Random(0xF0112026L);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            double first;
            double second;
            do {
                first = Double.longBitsToDouble(random.nextLong());
                second = Double.longBitsToDouble(random.nextLong());
            } while (!Double.isFinite(first) || !Double.isFinite(second)
                    || first == second);
            final double minimum = Math.min(first, second);
            final double maximum = Math.max(first, second);
            final int width = 2 + random.nextInt(1023);
            final Viewport viewport = new Viewport(minimum, maximum, -1, 1);

            assertEquals(minimum, viewport.worldX(0, width), 0);
            assertEquals(maximum, viewport.worldX(width - 1.0, width), 0);
            assertEquals(0, viewport.pixelX(minimum, width), 0);
            assertEquals(width - 1.0, viewport.pixelX(maximum, width), 0);

            double previous = minimum;
            for (int sample = 1; sample < 9; sample++) {
                final double pixel = (width - 1.0) * sample / 9;
                final double world = viewport.worldX(pixel, width);
                assertTrue(Double.isFinite(world));
                assertTrue(world >= previous && world >= minimum && world <= maximum);
                previous = world;
            }

            final double before = Math.nextDown(minimum);
            if (Double.isFinite(before)) {
                assertTrue(viewport.pixelX(before, width) < 0);
            }
            final double after = Math.nextUp(maximum);
            if (Double.isFinite(after)) {
                assertTrue(viewport.pixelX(after, width) > width - 1.0);
            }
        }
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
        final Viewport fullRange = normal.zoomAtPixel(50, 50, 101, 101, Double.MAX_VALUE);
        assertEquals(-Double.MAX_VALUE, fullRange.xMin(), 0);
        assertEquals(Double.MAX_VALUE, fullRange.xMax(), 0);
        assertEquals(-Double.MAX_VALUE, fullRange.yMin(), 0);
        assertEquals(Double.MAX_VALUE, fullRange.yMax(), 0);
        assertEquals(fullRange, fullRange.zoomAtPixel(50, 50, 101, 101, 2));
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

    @Test
    void minimumSubnormalViewportMapsEndpointsAndOutsideGeometry() {
        final double minimum = Double.MIN_VALUE;
        final Viewport viewport = new Viewport(0, minimum, 0, minimum);
        final SamplingLattice lattice = viewport.samplingLattice(3, 3);

        assertEquals(0, lattice.stepX(), 0);
        assertEquals(-0.0, lattice.stepY(), 0);
        assertEquals(0, viewport.worldX(0, 3), 0);
        assertEquals(minimum, viewport.worldX(2, 3), 0);
        assertEquals(minimum, viewport.worldY(0, 3), 0);
        assertEquals(0, viewport.worldY(2, 3), 0);
        assertEquals(0, viewport.pixelX(0, 3), 0);
        assertEquals(2, viewport.pixelX(minimum, 3), 0);
        assertEquals(0, viewport.pixelY(minimum, 3), 0);
        assertEquals(2, viewport.pixelY(0, 3), 0);

        assertTrue(viewport.pixelX(-minimum, 3) < 0);
        assertTrue(viewport.pixelX(Math.nextUp(minimum), 3) > 2);
        assertTrue(viewport.pixelY(Math.nextUp(minimum), 3) < 0);
        assertTrue(viewport.pixelY(-minimum, 3) > 2);
    }

    @Test
    void adjacentDoubleViewportsKeepEndpointsAndOutsidePointsOutside() {
        final Random random = new Random(0x1170_5eedL);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            final double xMin = finiteExponentBiased(random);
            final double xMax = Math.nextUp(xMin);
            final double yMin = finiteExponentBiased(random);
            final double yMax = Math.nextUp(yMin);
            if (!Double.isFinite(xMax) || !Double.isFinite(yMax)) {
                iteration--;
                continue;
            }
            final int width = 3 + random.nextInt(1_000);
            final int height = 3 + random.nextInt(1_000);
            final Viewport viewport = new Viewport(xMin, xMax, yMin, yMax);

            assertEquals(0, viewport.pixelX(xMin, width), 0);
            assertEquals(width - 1.0, viewport.pixelX(xMax, width), 0);
            assertEquals(0, viewport.pixelY(yMax, height), 0);
            assertEquals(height - 1.0, viewport.pixelY(yMin, height), 0);

            final double beforeX = Math.nextDown(xMin);
            final double afterX = Math.nextUp(xMax);
            final double beforeY = Math.nextDown(yMin);
            final double afterY = Math.nextUp(yMax);
            if (Double.isFinite(beforeX)) {
                assertTrue(viewport.pixelX(beforeX, width) < 0);
            }
            if (Double.isFinite(afterX)) {
                assertTrue(viewport.pixelX(afterX, width) > width - 1.0);
            }
            if (Double.isFinite(afterY)) {
                assertTrue(viewport.pixelY(afterY, height) < 0);
            }
            if (Double.isFinite(beforeY)) {
                assertTrue(viewport.pixelY(beforeY, height) > height - 1.0);
            }
        }
    }

    private static double finiteExponentBiased(final Random random) {
        while (true) {
            final double significand = random.nextBoolean()
                    ? random.nextDouble() : -random.nextDouble();
            final int exponent = random.nextInt(2_098) - 1_074;
            final double value = Math.scalb(significand, exponent);
            if (Double.isFinite(value)) {
                return value == 0 ? 0 : value;
            }
        }
    }

}
