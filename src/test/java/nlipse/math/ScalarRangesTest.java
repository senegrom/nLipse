package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScalarRangesTest {
    @Test
    void interpolatesAcrossASpanThatWouldOverflow() {
        final double minimum = -Double.MAX_VALUE;
        final double maximum = Double.MAX_VALUE;

        assertEquals(minimum, ScalarRanges.interpolate(minimum, maximum, 0));
        assertEquals(0, ScalarRanges.interpolate(minimum, maximum, 0.5));
        assertEquals(maximum, ScalarRanges.interpolate(minimum, maximum, 1));
        assertTrue(Double.isFinite(ScalarRanges.interpolate(minimum, maximum, 0.05)));
    }

    @Test
    void affineInterpolationHandlesAnUnrepresentableSpan() {
        final double maximum = Double.MAX_VALUE;

        assertEquals(-maximum, ScalarRanges.affine(-maximum, maximum, 0), 0);
        assertEquals(0, ScalarRanges.affine(-maximum, maximum, 0.5), 0);
        assertEquals(maximum, ScalarRanges.affine(-maximum, maximum, 1), 0);
        assertEquals(Double.NEGATIVE_INFINITY,
                ScalarRanges.affine(-maximum, maximum, -0.5));
        assertEquals(Double.POSITIVE_INFINITY,
                ScalarRanges.affine(-maximum, maximum, 1.5));
    }

    @Test
    void unboundedFractionPreservesInteriorAndOffRangePositions() {
        final double maximum = Double.MAX_VALUE;

        assertEquals(0.5, ScalarRanges.unboundedFraction(0, -maximum, maximum));
        assertEquals(0.75, ScalarRanges.unboundedFraction(maximum / 2, -maximum, maximum));
        assertTrue(ScalarRanges.unboundedFraction(-2, -1, 1) < 0);
        assertTrue(ScalarRanges.unboundedFraction(2, -1, 1) > 1);
        assertEquals(Double.NEGATIVE_INFINITY,
                ScalarRanges.unboundedFraction(Double.NEGATIVE_INFINITY, -1, 1));
        assertEquals(Double.POSITIVE_INFINITY,
                ScalarRanges.unboundedFraction(Double.POSITIVE_INFINITY, -1, 1));
    }

    @Test
    void normalizesAcrossASpanThatWouldOverflow() {
        final double minimum = -Double.MAX_VALUE;
        final double maximum = Double.MAX_VALUE;

        assertEquals(0, ScalarRanges.fraction(minimum, minimum, maximum));
        assertEquals(0.5, ScalarRanges.fraction(0, minimum, maximum));
        assertEquals(1, ScalarRanges.fraction(maximum, minimum, maximum));
    }
}
