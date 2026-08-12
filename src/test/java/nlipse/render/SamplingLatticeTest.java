package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SamplingLatticeTest {
    @Test
    void fullFiniteRangeUsesOverflowSafeStepsAndFusedCoordinates() {
        final double maximum = Double.MAX_VALUE;
        final SamplingLattice wide = SamplingLattice.fromViewport(
                -maximum, maximum, -maximum, maximum, 4, 4);

        assertTrue(Double.isFinite(wide.stepX()));
        assertTrue(Double.isFinite(wide.stepY()));
        assertTrue(Double.isFinite(wide.worldX(1)));
        assertTrue(Double.isFinite(wide.worldX(2)));
        assertEquals(-maximum, wide.worldX(0), 0);
        assertEquals(maximum, wide.worldX(3), 0);
        assertEquals(maximum, wide.worldY(0), 0);
        assertEquals(-maximum, wide.worldY(3), 0);

        final SamplingLattice endpointsOnly = SamplingLattice.fromViewport(
                -maximum, maximum, -maximum, maximum, 2, 2);
        assertEquals(Double.POSITIVE_INFINITY, endpointsOnly.stepX());
        assertEquals(Double.NEGATIVE_INFINITY, endpointsOnly.stepY());
        assertEquals(-maximum, endpointsOnly.worldX(0), 0);
        assertEquals(0, endpointsOnly.worldX(0.5), 0);
        assertEquals(maximum, endpointsOnly.worldX(1), 0);
        assertEquals(maximum, endpointsOnly.worldY(0), 0);
        assertEquals(0, endpointsOnly.worldY(0.5), 0);
        assertEquals(-maximum, endpointsOnly.worldY(1), 0);
    }

    @Test
    void zeroStepSignsAreCanonicalizedForLatticeIdentity() {
        final SamplingLattice lattice = SamplingLattice.fromViewport(
                0, Double.MIN_VALUE, 0, Double.MIN_VALUE, 3, 3);

        assertEquals(Double.doubleToLongBits(0.0), lattice.stepXBits());
        assertEquals(Double.doubleToLongBits(-0.0), lattice.stepYBits());
    }
}
