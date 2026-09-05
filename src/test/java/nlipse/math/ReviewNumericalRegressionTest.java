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
    @Test
    void cassiniExactFallbackDoesNotAcceptAFalselyRoundedUnitDistance() {
        for (final double sign : new double[]{1, -1}) {
            final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                    List.of(new Focus(0, 0, sign * 0x1.0p1023)));
            // log(f) = 2^1022 * log1p(2^-1060), indistinguishable from 2^-38.
            final double expected = Math.exp(sign * 0x1.0p-38);
            assertEquals(expected, field.value(1, 0x1.0p-530), 4 * Math.ulp(expected));
        }
    }

    @Test
    void aggregateFamiliesAreInvariantUnderExactPowerOfTwoRescaling() {
        final List<Focus> tiny = List.of(new Focus(0, 0, 0x1.0p1022), new Focus(0, 0, 0x1.0p1023));
        final List<Focus> normal = List.of(new Focus(0, 0, 0x1.0p-52), new Focus(0, 0, 0x1.0p-51));
        for (final CurveType type : CurveType.values()) {
            if (type == CurveType.CASSIN || type == CurveType.GAUSSIAN || type == CurveType.POTENTIAL) {
                continue;
            }
            final double[] parameters = type == CurveType.POWER_MEAN
                    ? new double[]{-3.5, -1, 0, 0.5, 1, 2, 3.5}
                    : new double[]{type == CurveType.SMOOTH_NEAREST || type == CurveType.SMOOTH_FARTHEST
                            ? 1e-15 : type.defaultParameter()};
            for (final double parameter : parameters) {
                final double expected = DistanceFields.create(type, normal, parameter).value(1, 1);
                final double actual = DistanceFields.create(type, tiny, parameter)
                        .value(Double.MIN_VALUE, Double.MIN_VALUE);
                assertEquals(expected, actual, 32 * Math.ulp(expected), type + " parameter " + parameter);
            }
        }
    }

    @Test
    void structuralSubnormalFailuresBypassTheInteractiveAllowance() {
        final double m = Double.MIN_VALUE;
        final ExactBudget budget = ExactBudget.limited(0);
        final DistanceField potential = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, m)), 1, budget);
        assertEquals(Math.sqrt(0.5), potential.value(m, m), 4 * Math.ulp(Math.sqrt(0.5)));
    }

    @Test
    void cassiniAmplificationIsMarkedWhenTheInteractiveAllowanceIsExhausted() {
        final ExactBudget budget = ExactBudget.limited(0);
        DistanceFields.create(CurveType.CASSIN, List.of(new Focus(0, 0, 0x1.0p54)), 1, budget)
                .value(1, 0x1.0p-27);
        org.junit.jupiter.api.Assertions.assertTrue(budget.exhausted());
    }
}
