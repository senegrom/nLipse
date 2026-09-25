package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
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

    /**
     * Far from three close foci every smooth-envelope ratio rounds to one double,
     * which routed every pixel to the exact evaluator whatever the allowance: a
     * 200x200 far-zoom frame took 16 s. The collapse is a budgeted fallback now,
     * and the estimate lies within the spread of the distances.
     */
    @Test
    void collapsedSmoothEnvelopeRatiosRespectTheInteractiveAllowance() {
        final List<Focus> foci = List.of(new Focus(0, 0, 1), new Focus(1, 0, 1), new Focus(0, 1, 1));
        for (final CurveType type : List.of(CurveType.SMOOTH_NEAREST, CurveType.SMOOTH_FARTHEST)) {
            final ExactBudget budget = ExactBudget.limited(0);
            final DistanceField field = DistanceFields.create(type, foci, 0.5, budget);
            final long started = System.nanoTime();
            for (int row = 0; row < 100; row++) {
                for (int column = 0; column < 100; column++) {
                    final double x = 1e17 + column * 1e3;
                    final double y = 1e17 + row * 1e3;
                    final double value = field.value(x, y);
                    final double nearest = Math.hypot(x - 1, y - 1);
                    final double farthest = Math.hypot(x, y);
                    org.junit.jupiter.api.Assertions.assertTrue(
                            value >= Math.nextDown(Math.min(nearest, farthest))
                                    && value <= Math.nextUp(Math.max(nearest, farthest)),
                            type + " at (" + x + ", " + y + "): " + value);
                }
            }
            final long millis = (System.nanoTime() - started) / 1_000_000;
            // About 4 s through the unbudgeted exact route; milliseconds without it.
            org.junit.jupiter.api.Assertions.assertTrue(millis < 1_500, type + " took " + millis + " ms");
            org.junit.jupiter.api.Assertions.assertTrue(budget.exhausted(), type + " should report the collapse");
        }
    }

    /**
     * Past d/σ ≈ 1.3e154 the square in the Gaussian exponent overflows. Such a
     * term still has a sign: an all-negative sum rounds to -0.0, not +0.0.
     */
    @Test
    void gaussianZeroKeepsItsSignWhenTheExponentOverflows() {
        final DistanceField unit = DistanceFields.create(CurveType.GAUSSIAN, List.of(new Focus(0, 0, -1)), 1);
        assertEquals(-0.0, unit.value(1e154, 0));
        assertEquals(-0.0, unit.value(2e154, 0));
        final DistanceField narrow = DistanceFields.create(CurveType.GAUSSIAN,
                List.of(new Focus(0, 0, -1)), 1e-160);
        assertEquals(-0.0, narrow.value(1, 0));
        final FocusSet set = FocusSet.from(List.of(new Focus(0, 0, -1)));
        assertEquals(-0.0, ExactFieldMath.gaussian(set, 2e154, 0, 1));
    }

    /**
     * The zero's sign follows the dominant term, which the primitive exponent
     * -½(d/σ)² misjudges once its rounding error (about (d/σ)²·2^-52) exceeds
     * the one-unit margin: here the exact ln(positive/negative) is +7.44, while
     * the primitive exponents made the negative term look dominant.
     */
    @Test
    void gaussianZeroSignFollowsTheExactDominantTerm() {
        final List<Focus> foci = List.of(new Focus(2.9206e-8, 0, 1),
                new Focus(-2.9206e-8, 0, -Math.exp(20)));
        assertEquals(0.0, DistanceFields.create(CurveType.GAUSSIAN, foci, 1).value(4.69762048e8, 0));
        assertEquals(0.0, ExactFieldMath.gaussian(FocusSet.from(foci), 4.69762048e8, 0, 1));
    }

    /**
     * Coincident opposite foci cancel exactly. Out here (e^-1800) even the exact
     * evaluator's exponentials truncate to zero, so only the dominance rule can
     * sign the result; weighed one by one, the pair's equal and opposite terms
     * tied for dominance and left the zero unsigned (+0.0), though the only
     * term that remains is negative.
     */
    @Test
    void gaussianZeroSignIgnoresACancelledPair() {
        final List<Focus> foci = List.of(new Focus(1, 0, 1), new Focus(1, 0, -1),
                new Focus(0, 0, -1));
        assertEquals(-0.0, DistanceFields.create(CurveType.GAUSSIAN, foci, 1).value(61, 0));
        assertEquals(-0.0, ExactFieldMath.gaussian(FocusSet.from(foci), 61, 0, 1));
    }

    /**
     * When every ratio |w|d/τ underflows or turns subnormal, the envelope is the
     * arithmetic mean of the magnitude distances. With the allowance spent,
     * 0.12.15 returned the hard envelope there, or ratios quantized to
     * multiples of the smallest subnormal.
     */
    @Test
    void smoothEnvelopeOfUnderflowedRatiosIsTheMeanWithoutAllowance() {
        record Case(double temperature, List<Focus> foci, double x, double y, double mean) {
        }
        final List<Case> cases = List.of(
                new Case(1e17, List.of(new Focus(0, 0, 1), new Focus(2e-307, 0, 1)), 0, 0, 1e-307),
                new Case(1e17, List.of(new Focus(0, 0, 1), new Focus(1e-306, 0, 1),
                        new Focus(0, 3e-306, 1)), 2e-307, 1e-307, 1.3122403144431869e-306),
                new Case(1e308, List.of(new Focus(0, 0, 1), new Focus(1e-16, 0, 1),
                        new Focus(3e-16, 0, 1)), 0, 0, 1.3333333333333334e-16),
                new Case(1e300, List.of(new Focus(0, 0, 1), new Focus(1e-24, 0, 2)),
                        5e-25, 0, 7.5e-25),
                new Case(1e17, List.of(new Focus(0, 0, 1), new Focus(9e-307, 0, 1)),
                        3e-307, 0, 4.5e-307));
        for (final Case example : cases) {
            for (final CurveType type : List.of(CurveType.SMOOTH_NEAREST, CurveType.SMOOTH_FARTHEST)) {
                final String label = type + " at τ=" + example.temperature() + " " + example.foci();
                final double value = DistanceFields.create(type, example.foci(),
                        example.temperature(), ExactBudget.limited(0)).value(example.x(), example.y());
                assertEquals(example.mean(), value, 4 * Math.ulp(example.mean()), label);
                // With the allowance such ratios go exact, which also rounds a tie of the mean.
                assertEquals(ExactFieldMath.smoothEnvelope(FocusSet.from(example.foci()), example.x(),
                                example.y(), example.temperature(), type == CurveType.SMOOTH_NEAREST),
                        DistanceFields.create(type, example.foci(), example.temperature())
                                .value(example.x(), example.y()), label);
            }
        }
    }

    /**
     * Above τ ≈ 1.4e306 every ratio |w|/τ counts as rounding sensitive, so every
     * point goes exact, and there the exact envelope's logarithm near 1 cost
     * 9-22 ms a point. So far below the temperature, mean and variance bound
     * the envelope instead.
     */
    @Test
    void smoothEnvelopeAtAHugeTemperatureNeedsNoLogarithm() {
        final List<Focus> foci = List.of(new Focus(0, 0, 1), new Focus(1, 0, 1), new Focus(0, 1, 1));
        final DistanceField mean = DistanceFields.create(CurveType.POWER_MEAN, foci, 1);
        final double[] expected = new double[200];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = mean.value(0.3 + index * 1e-3, 0.4);
        }
        for (final CurveType type : List.of(CurveType.SMOOTH_NEAREST, CurveType.SMOOTH_FARTHEST)) {
            final DistanceField field = DistanceFields.create(type, foci, 1e307);
            field.value(0.3, 0.4);
            final double[] values = new double[expected.length];
            final long started = System.nanoTime();
            for (int index = 0; index < values.length; index++) {
                values[index] = field.value(0.3 + index * 1e-3, 0.4);
            }
            final long millis = (System.nanoTime() - started) / 1_000_000;
            for (int index = 0; index < values.length; index++) {
                assertEquals(expected[index], values[index], 4 * Math.ulp(expected[index]),
                        type + " at point " + index);
            }
            // Two to four seconds through the logarithm; milliseconds without it.
            assertTrue(millis < 1_000, type + " took " + millis + " ms");
        }
    }

    /**
     * The mean of 1 and nextUp(1) is exactly the tie between them, and the
     * envelope leans off it: above for the farthest, below for the nearest.
     * The mean-and-variance route must keep that lean at any huge temperature.
     */
    @Test
    void exactEnvelopeFarBelowTheTemperatureRoundsATieOfTheMeanTheWayItLeans() {
        final FocusSet foci = FocusSet.from(List.of(new Focus(Math.nextUp(1.0), 0, 1), new Focus(1, 0, 1)));
        for (final double temperature : new double[]{1e20, 1e200, Double.MAX_VALUE}) {
            assertEquals(Math.nextUp(1.0), ExactFieldMath.smoothEnvelope(foci, 0, 0, temperature, false));
            assertEquals(1.0, ExactFieldMath.smoothEnvelope(foci, 0, 0, temperature, true));
        }
    }

    /**
     * exp(ln MAX) is about 100 ulps below MAX, and the primitive only clamped a
     * logarithm above ln MAX: a product at the top of the range came back short,
     * or finite where it overflows. Weights at or above 1022, the earlier
     * tests', go exact anyway.
     */
    @Test
    void cassiniRoundsCorrectlyAtTheEdgesOfTheRange() {
        final double root = Math.sqrt(Double.MAX_VALUE);
        final double[][] overflowSide = {
                {Double.MAX_VALUE, 1}, {Math.nextDown(Double.MAX_VALUE), 1}, {1.7976931348623e308, 1},
                {root, 2}, {Math.nextUp(root), 2}, {Math.cbrt(Double.MAX_VALUE), 3}};
        for (final double[] example : overflowSide) {
            assertCassiniPower(example[0], (int) example[1]);
        }
        // Around 2^-1075, where the product rounds to 0 or to MIN_VALUE
        for (final int weight : new int[]{2, 3}) {
            for (int step = -40; step <= 40; step += 8) {
                assertCassiniPower(Math.exp((-1075 * Math.log(2) + step * 0x1.0p-45) / weight), weight);
            }
        }
    }

    private static void assertCassiniPower(final double distance, final int weight) {
        // A single focus at the origin: the field is exactly distance^weight.
        final double expected = new BigDecimal(distance).pow(weight).doubleValue();
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, weight)));
        assertEquals(expected, field.value(distance, 0), "distance " + distance + ", weight " + weight);
    }
}
