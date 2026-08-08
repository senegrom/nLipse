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
    void normalizesAcrossASpanThatWouldOverflow() {
        final double minimum = -Double.MAX_VALUE;
        final double maximum = Double.MAX_VALUE;

        assertEquals(0, ScalarRanges.fraction(minimum, minimum, maximum));
        assertEquals(0.5, ScalarRanges.fraction(0, minimum, maximum));
        assertEquals(1, ScalarRanges.fraction(maximum, minimum, maximum));
    }
}
