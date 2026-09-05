package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import org.junit.jupiter.api.Test;

/** Regressions for precision lost before weighting or normalizing a distance. */
class ReviewNumericalRegressionTest {
    @Test
    void cassiniRetainsNearUnitDistanceBeforeApplyingLargeExponent() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 0x1.0p54)));
        // Independent 100-digit reference: exp(2^53 * log1p(2^-54)).
        final double expected = 1.6487212707001281239680468388075676509776636615963458496;
        assertEquals(expected, field.value(1, 0x1.0p-27), 4 * Math.ulp(expected));
    }

    @Test
    void weightedNormDoesNotRoundSubnormalDistanceBeforeScaling() {
        final double m = Double.MIN_VALUE;
        final double expected = Math.scalb(Math.sqrt(2), -51);
        for (final CurveType type : List.of(
                CurveType.NEAREST, CurveType.FARTHEST, CurveType.QUADRATIC)) {
            final DistanceField field = DistanceFields.create(type,
                    List.of(new Focus(0, 0, 0x1.0p1023)));
            assertEquals(expected, field.value(m, m), 4 * Math.ulp(expected), type.name());
        }
    }

    @Test
    void potentialNormalizesSubnormalDistanceWithoutLosingGeometry() {
        final double m = Double.MIN_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, m)));
        final double expected = 0.707106781186547524400844362104849039284835937688474;
        assertEquals(expected, field.value(m, m), 4 * Math.ulp(expected));
    }

    @Test
    void gaussianNormalizesSubnormalCoordinatesBeforeTakingNorm() {
        final double m = Double.MIN_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN,
                List.of(new Focus(0, 0, 1)), m);
        final double expected = Math.exp(-1);
        assertEquals(expected, field.value(m, m), 4 * Math.ulp(expected));
    }
}
