package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;

class DistanceFieldsTest {
    private static final double EPSILON = 1e-12;

    @Test
    void ellipseUsesWeightedSum() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(0, 0, 2), new Focus(3, 4, 0.5)));
        assertEquals(2.5, field.value(0, 0), EPSILON);
    }

    @Test
    void ellipseRecoversFiniteCancellationAfterIntermediateOverflow() {
        final double coordinate = Double.MAX_VALUE * 0.75;
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(coordinate, 0, 1), new Focus(coordinate, 0, 1),
                        new Focus(coordinate, 0, -1), new Focus(coordinate, 0, -1)));

        assertEquals(0, field.value(0, 0), 0);
    }

    @Test
    void ellipseDistinguishesTrueOverflowFromFiniteCancellation() {
        final Focus farPositive = new Focus(Double.MAX_VALUE, 0, 2);
        final Focus farNegative = new Focus(Double.MAX_VALUE, 0, -2);

        assertEquals(Double.POSITIVE_INFINITY,
                DistanceFields.create(CurveType.LIPSE, List.of(farPositive)).value(0, 0));
        assertEquals(Double.NEGATIVE_INFINITY,
                DistanceFields.create(CurveType.LIPSE, List.of(farNegative)).value(0, 0));
        assertEquals(0, DistanceFields.create(CurveType.LIPSE,
                List.of(farPositive, farNegative)).value(0, 0), 0);
        assertTrue(Double.isNaN(DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(0, 0, 1), new Focus(0, 0, -1)))
                .value(Double.POSITIVE_INFINITY, 0)));
    }

    @Test
    void ellipseRecoversFiniteResidualWhenBothProductsOverflow() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE, List.of(
                new Focus(0, 0, 2),
                new Focus(Double.MIN_VALUE, 0, -2)));

        assertEquals(2 * Double.MIN_VALUE, field.value(Double.MAX_VALUE, 0), 0);
    }

    @Test
    void exactFallbackDoesNotLoseATinyTermAddedBeforeHugeCancellation() {
        final double maximum = Double.MAX_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.LIPSE, List.of(
                new Focus(1, 0, Double.MIN_VALUE),
                new Focus(maximum, 0, maximum / 2),
                new Focus(maximum, 0, maximum / 2),
                new Focus(maximum, 0, -maximum)));

        assertEquals(Double.MIN_VALUE, field.value(0, 0), 0);
    }

    @Test
    void ellipseCanSubtractAFiniteTermFromAnOverflowingTerm() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE, List.of(
                new Focus(0, 0, 2),
                new Focus(0, 0, -1)));

        assertEquals(Double.MAX_VALUE, field.value(Double.MAX_VALUE, 0), 0);
    }

    @Test
    void cassiniUsesWeightedProduct() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 1), new Focus(3, 4, 1)));
        assertEquals(12, field.value(0, 4), EPSILON);
        assertEquals(0, field.value(0, 0), EPSILON);
    }

    @Test
    void cassiniAccumulatesInTheLogDomainBeforeExponentiating() {
        final double coordinate = 1e200;
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(coordinate, 0, 2), new Focus(-coordinate, 0, -2)));

        assertEquals(1, field.value(0, 0), 1e-12);
    }

    @Test
    void cassiniRecoversFiniteCancellationWithExtremeWeights() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN, List.of(
                new Focus(0, 0, Double.MAX_VALUE),
                new Focus(0, 0, -Double.MAX_VALUE),
                new Focus(0, 0, 1)));

        assertEquals(Math.E, field.value(Math.E, 0), 1e-14);
    }


    @Test
    void cassiniRetainsTermsThatUnderflowOnlyAfterWeightNormalization() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN, List.of(
                new Focus(1, 0, Double.MAX_VALUE),
                new Focus(Math.nextUp(1.0), 0, 1)));

        assertEquals(Math.nextUp(1.0), field.value(0, 0), 0);
    }

    @Test
    void cassiniPreservesZeroAndSingularFactorSemantics() {
        final DistanceField zeroWeight = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 0)));
        final DistanceField undefined = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 1), new Focus(0, 0, -1)));

        assertEquals(1, zeroWeight.value(0, 0), 0);
        assertTrue(Double.isNaN(undefined.value(0, 0)));
    }

    @Test
    void hyperbolaUsesMeanPairwiseDifference() {
        final DistanceField field = DistanceFields.create(CurveType.HYPERB,
                List.of(new Focus(0, 0, 1), new Focus(3, 0, 2), new Focus(0, 4, 0.5)));
        assertEquals(4, field.value(0, 0), EPSILON);
    }

    @Test
    void oneFocusHyperbolaIsZero() {
        final DistanceField field = DistanceFields.create(CurveType.HYPERB,
                List.of(new Focus(1, 2, 3)));
        assertEquals(0, field.value(100, -50), EPSILON);
    }

    @Test
    void largeHyperbolaMatchesBruteForceReference() {
        final List<Focus> foci = IntStream.range(0, 64)
                .mapToObj(index -> new Focus(index * 0.17 - 4, index % 7 - 3,
                        index % 5 - 2.25))
                .toList();
        final DistanceField field = DistanceFields.create(CurveType.HYPERB, foci);
        final double x = 0.75;
        final double y = -1.25;
        final double[] distances = foci.stream()
                .mapToDouble(focus -> Math.hypot(x - focus.x(), y - focus.y()) * focus.weight())
                .toArray();
        double sum = 0;
        for (int i = 0; i < distances.length; i++) {
            for (int j = 0; j < i; j++) {
                sum += Math.abs(distances[i] - distances[j]);
            }
        }
        final double expected = 2 * sum / ((double) distances.length * (distances.length - 1));

        assertEquals(expected, field.value(x, y), 1e-10);
    }

    @Test
    void hyperbolaAvoidsOverflowWhenTheFinalMeanIsFinite() {
        final double coordinate = Double.MAX_VALUE * 0.75;
        final DistanceField field = DistanceFields.create(CurveType.HYPERB,
                List.of(new Focus(coordinate, 0, 1), new Focus(coordinate, 0, 1),
                        new Focus(coordinate, 0, -1), new Focus(coordinate, 0, -1)));

        final double value = field.value(0, 0);
        assertTrue(Double.isFinite(value));
        assertEquals(4.0 / 3.0, value / coordinate, 1e-12);
    }


    @Test
    void hyperbolaIgnoresUnusedCapacityInTheSharedScratchBuffer() {
        final DistanceField larger = DistanceFields.create(CurveType.HYPERB, List.of(
                new Focus(1, 0, 1), new Focus(2, 0, 1),
                new Focus(3, 0, 1), new Focus(4, 0, 1)));
        larger.value(0, 0);
        final DistanceField smaller = DistanceFields.create(CurveType.HYPERB, List.of(
                new Focus(1e95, 0, 1e-315),
                new Focus(0, 0, 1e13)));

        assertEquals(1e13, smaller.value(1, 0), 1e-15);
    }

    @Test
    void hypotAvoidsPrematureOverflow() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(1e308, 0, 1)));
        final double value = field.value(0, 0);
        assertTrue(Double.isFinite(value));
        assertEquals(1e308, value, 1e292);
    }

    @Test
    void signedAndZeroWeightsRemainSupported() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(0, 0, -1), new Focus(2, 0, 0)));
        assertEquals(-1, field.value(1, 0), EPSILON);
    }
    @Test
    void magnitudeFamiliesUseAbsoluteWeightsAndIgnoreZeroWeights() {
        final List<Focus> foci = List.of(
                new Focus(0, 0, -2),
                new Focus(3, 4, 0.5),
                new Focus(100, 100, 0));
        final double x = 0;
        final double y = 4;

        assertEquals(1.5, DistanceFields.create(CurveType.NEAREST, foci).value(x, y), EPSILON);
        assertEquals(8, DistanceFields.create(CurveType.FARTHEST, foci).value(x, y), EPSILON);
        assertEquals(Math.hypot(8, 1.5),
                DistanceFields.create(CurveType.QUADRATIC, foci).value(x, y), EPSILON);
        assertEquals(6.5, DistanceFields.create(CurveType.RANGE, foci).value(x, y), EPSILON);
    }

    @Test
    void magnitudeFamiliesAreZeroWhenNoFocusIsActive() {
        final List<Focus> foci = List.of(new Focus(0, 0, 0), new Focus(1, 1, -0.0));

        for (final CurveType type : List.of(
                CurveType.NEAREST, CurveType.FARTHEST,
                CurveType.QUADRATIC, CurveType.RANGE,
                CurveType.POWER_MEAN, CurveType.MEDIAN,
                CurveType.SMOOTH_NEAREST, CurveType.SMOOTH_FARTHEST,
                CurveType.GAUSSIAN)) {
            assertEquals(0, DistanceFields.create(type, foci).value(4, -3), 0);
        }
    }

    @Test
    void quadraticFamilyAvoidsPrematureOverflow() {
        final DistanceField field = DistanceFields.create(CurveType.QUADRATIC,
                List.of(new Focus(1e308, 0, 1), new Focus(0, 1e308, 1)));

        final double value = field.value(0, 0);
        assertTrue(Double.isFinite(value));
        assertEquals(Math.sqrt(2), value / 1e308, 1e-15);
    }

    @Test
    void potentialUsesSignedInverseDistances() {
        final DistanceField field = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, 2), new Focus(3, 4, -5)));

        assertEquals(-7.0 / 6.0, field.value(0, 4), EPSILON);
    }

    @Test
    void potentialRecoversExtremeWeightCancellation() {
        final DistanceField field = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, Double.MAX_VALUE),
                        new Focus(0, 0, -Double.MAX_VALUE)));

        assertEquals(0, field.value(2, 0), 0);
    }

    @Test
    void potentialDetectsFiniteButIllConditionedCancellation() {
        final double firstWeight = -3.841044956269343e35;
        final double secondWeight = 5.8564212468211325e35;
        final double firstDistance = 8.460432894611084e18;
        final double secondDistance = 1.2899577986045925e19;
        final DistanceField field = DistanceFields.create(CurveType.POTENTIAL, List.of(
                new Focus(firstDistance, 0, firstWeight),
                new Focus(secondDistance, 0, secondWeight)));
        final MathContext context = new MathContext(80);
        final double expected = new BigDecimal(firstWeight)
                .divide(new BigDecimal(firstDistance), context)
                .add(new BigDecimal(secondWeight)
                        .divide(new BigDecimal(secondDistance), context), context)
                .doubleValue();

        assertEquals(expected, field.value(0, 0), Math.ulp(expected));
    }


    @Test
    void potentialIgnoresUnusedThreadLocalScratchEntries() {
        final List<Focus> manyFoci = java.util.stream.IntStream.range(0, 32)
                .mapToObj(index -> new Focus(index + 1, index % 3, index + 0.5))
                .toList();
        DistanceFields.create(CurveType.HYPERB, manyFoci).value(0, 0);

        final DistanceField potential = DistanceFields.create(CurveType.POTENTIAL, List.of(
                new Focus(1, 0, 2),
                new Focus(3, 0, -1)));
        assertEquals(5.0 / 3.0, potential.value(0, 0), Math.ulp(5.0 / 3.0));
    }

    @Test
    void potentialRetainsAWeightThatUnderflowsDuringNormalization() {
        final DistanceField field = DistanceFields.create(CurveType.POTENTIAL, List.of(
                new Focus(100, 0, Double.MAX_VALUE),
                new Focus(1, 0, Double.MIN_VALUE),
                new Focus(100, 0, -Double.MAX_VALUE)));

        assertEquals(Double.MIN_VALUE, field.value(0, 0), 0);
    }

    @Test
    void potentialPreservesSignedSingularities() {
        final DistanceField positive = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, 1)));
        final DistanceField negative = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, -1)));
        final DistanceField undefined = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, 1), new Focus(0, 0, -1)));
        final DistanceField disabled = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(0, 0, 0)));

        assertEquals(Double.POSITIVE_INFINITY, positive.value(0, 0));
        assertEquals(Double.NEGATIVE_INFINITY, negative.value(0, 0));
        assertTrue(Double.isNaN(undefined.value(0, 0)));
        assertEquals(0, disabled.value(0, 0), 0);
    }


    @Test
    void powerMeanUsesEfficientSpecialCasesAndArbitraryP() {
        final List<Focus> foci = List.of(
                new Focus(3, 4, 2),
                new Focus(0, 4, -0.5),
                new Focus(100, 100, 0));
        final double x = 0;
        final double y = 0;

        assertEquals(6, DistanceFields.create(CurveType.POWER_MEAN, foci, 1)
                .value(x, y), EPSILON);
        assertEquals(Math.sqrt(52), DistanceFields.create(CurveType.POWER_MEAN, foci, 2)
                .value(x, y), EPSILON);
        assertEquals(Math.sqrt(20), DistanceFields.create(CurveType.POWER_MEAN, foci, 0)
                .value(x, y), EPSILON);
        assertEquals(10.0 / 3.0, DistanceFields.create(CurveType.POWER_MEAN, foci, -1)
                .value(x, y), EPSILON);
        assertEquals(Math.pow((Math.sqrt(10) + Math.sqrt(2)) / 2, 2),
                DistanceFields.create(CurveType.POWER_MEAN, foci, 0.5).value(x, y), EPSILON);
        assertEquals(Math.cbrt(504), DistanceFields.create(CurveType.POWER_MEAN, foci, 3)
                .value(x, y), EPSILON);
        assertEquals(10, DistanceFields.create(CurveType.POWER_MEAN, foci,
                Double.POSITIVE_INFINITY).value(x, y), 0);
        assertEquals(2, DistanceFields.create(CurveType.POWER_MEAN, foci,
                Double.NEGATIVE_INFINITY).value(x, y), 0);

        final double quadratic = DistanceFields.create(CurveType.QUADRATIC, foci).value(x, y);
        assertEquals(quadratic / Math.sqrt(2),
                DistanceFields.create(CurveType.POWER_MEAN, foci, 2).value(x, y), EPSILON);
    }

    @Test
    void powerMeanIsStableNearZeroAndAtExtendedValues() {
        final List<Focus> finite = List.of(new Focus(2, 0, 1), new Focus(8, 0, 1));
        final double geometric = 4;
        assertEquals(geometric,
                DistanceFields.create(CurveType.POWER_MEAN, finite, 1e-10).value(0, 0),
                1e-8);

        final List<Focus> withZero = List.of(new Focus(0, 0, 1), new Focus(2, 0, 1));
        assertEquals(0, DistanceFields.create(CurveType.POWER_MEAN, withZero, -2)
                .value(0, 0), 0);
        assertEquals(0, DistanceFields.create(CurveType.POWER_MEAN, withZero, 0)
                .value(0, 0), 0);
        assertEquals(Math.sqrt(2), DistanceFields.create(CurveType.POWER_MEAN, withZero, 2)
                .value(0, 0), EPSILON);

        final List<Focus> withInfinity = List.of(
                new Focus(2, 0, 1), new Focus(Double.MAX_VALUE, 0, 2));
        assertEquals(4, DistanceFields.create(CurveType.POWER_MEAN, withInfinity, -1)
                .value(0, 0), 1e-12);
        assertEquals(Double.MAX_VALUE,
                DistanceFields.create(CurveType.POWER_MEAN, withInfinity, 1)
                        .value(0, 0));
    }

    @Test
    void medianUsesActiveWeightedDistancesForOddEvenAndLargeSets() {
        final List<Focus> odd = List.of(
                new Focus(1, 0, 1), new Focus(9, 0, -1),
                new Focus(3, 0, 1), new Focus(100, 0, 0));
        assertEquals(3, DistanceFields.create(CurveType.MEDIAN, odd).value(0, 0), 0);

        final List<Focus> even = List.of(
                new Focus(15, 0, 1), new Focus(1, 0, 1),
                new Focus(9, 0, 1), new Focus(3, 0, 1));
        assertEquals(6, DistanceFields.create(CurveType.MEDIAN, even).value(0, 0), 0);

        final List<Focus> many = IntStream.rangeClosed(1, 65)
                .mapToObj(index -> new Focus(index, 0, index % 2 == 0 ? -1 : 1))
                .toList();
        assertEquals(33, DistanceFields.create(CurveType.MEDIAN, many).value(0, 0), 0);
    }

    @Test
    void medianSelectionMatchesSortingAcrossVariedOrdersAndDuplicates() {
        final java.util.Random random = new java.util.Random(0x4e4c69707365L);
        for (int trial = 0; trial < 250; trial++) {
            final int count = 17 + random.nextInt(96);
            final java.util.ArrayList<Focus> foci = new java.util.ArrayList<>(count);
            final double[] reference = new double[count];
            for (int index = 0; index < count; index++) {
                final double value = random.nextInt(11) == 0
                        ? 4 : Math.scalb(0.5 + random.nextDouble(), random.nextInt(30) - 15);
                reference[index] = value;
                foci.add(new Focus(value, 0, index % 2 == 0 ? 1 : -1));
            }
            java.util.Arrays.sort(reference);
            final int lower = (count - 1) / 2;
            final int upper = count / 2;
            final double expected = ScalarRanges.interpolate(reference[lower], reference[upper], 0.5);

            assertEquals(expected, DistanceFields.create(CurveType.MEDIAN, foci).value(0, 0), 0,
                    "trial " + trial);
        }
    }

    @Test
    void medianMidpointDoesNotOverflow() {
        final List<Focus> foci = List.of(
                new Focus(Double.MAX_VALUE, 0, 1),
                new Focus(Double.MAX_VALUE, 0, -1));
        assertEquals(Double.MAX_VALUE,
                DistanceFields.create(CurveType.MEDIAN, foci).value(0, 0), 0);
    }

    @Test
    void smoothEnvelopesAreNormalizedStableAndApproachExactEnvelopes() {
        final List<Focus> foci = List.of(new Focus(2, 0, 1), new Focus(10, 0, 1));
        final double temperature = 0.5;
        final double expectedNearest = 2 - temperature
                * Math.log((1 + Math.exp((2 - 10) / temperature)) / 2);
        final double expectedFarthest = 10 + temperature
                * Math.log((1 + Math.exp((2 - 10) / temperature)) / 2);

        assertEquals(expectedNearest,
                DistanceFields.create(CurveType.SMOOTH_NEAREST, foci, temperature)
                        .value(0, 0), EPSILON);
        assertEquals(expectedFarthest,
                DistanceFields.create(CurveType.SMOOTH_FARTHEST, foci, temperature)
                        .value(0, 0), EPSILON);
        assertEquals(2, DistanceFields.create(CurveType.SMOOTH_NEAREST, foci, 1e-9)
                .value(0, 0), 1e-8);
        assertEquals(10, DistanceFields.create(CurveType.SMOOTH_FARTHEST, foci, 1e-9)
                .value(0, 0), 1e-8);

        final List<Focus> equal = List.of(new Focus(4, 0, 1), new Focus(-4, 0, -1));
        assertEquals(4, DistanceFields.create(CurveType.SMOOTH_NEAREST, equal, 1e200)
                .value(0, 0), 0);
        assertEquals(4, DistanceFields.create(CurveType.SMOOTH_FARTHEST, equal, 1e200)
                .value(0, 0), 0);
    }


    @Test
    void arithmeticPowerMeanCanBeFiniteWhenEveryTermOverflows() {
        final double coordinate = Double.MAX_VALUE * 0.75;
        final DistanceField field = DistanceFields.create(CurveType.POWER_MEAN, List.of(
                new Focus(coordinate, 0, 2),
                new Focus(coordinate, 0, 2)), 1);

        assertEquals(coordinate * 2, field.value(0, 0), 0);
    }

    @Test
    void powerMeansAreMonotoneInPForPositiveFiniteInputs() {
        final List<Focus> foci = List.of(
                new Focus(0.25, 0, 1), new Focus(2, 0, -1),
                new Focus(7, 0, 0.5), new Focus(100, 100, 0));
        final double[] powers = {
                Double.NEGATIVE_INFINITY, -8, -2, -0.25, 0, 0.25, 1, 2, 8,
                Double.POSITIVE_INFINITY};
        double previous = Double.NEGATIVE_INFINITY;
        for (final double power : powers) {
            final double current = DistanceFields.create(CurveType.POWER_MEAN, foci, power)
                    .value(0, 0);
            assertTrue(current >= previous - 1e-12, "p=" + power);
            previous = current;
        }
    }

    @Test
    void normalizedSmoothEnvelopesStayBetweenTheExactEnvelopeAndArithmeticMean() {
        final List<Focus> foci = List.of(
                new Focus(1, 0, 1), new Focus(4, 0, -1), new Focus(10, 0, 1));
        final double arithmetic = 5;
        for (final double temperature : new double[]{1e-6, 0.1, 1, 10, 1e6}) {
            final double nearest = DistanceFields.create(
                    CurveType.SMOOTH_NEAREST, foci, temperature).value(0, 0);
            final double farthest = DistanceFields.create(
                    CurveType.SMOOTH_FARTHEST, foci, temperature).value(0, 0);
            assertTrue(nearest >= 1 && nearest <= arithmetic, "soft minimum τ=" + temperature);
            assertTrue(farthest <= 10 && farthest >= arithmetic,
                    "soft maximum τ=" + temperature);
        }
    }

    @Test
    void gaussianFieldUsesSignedAmplitudesAndStableWidthScaling() {
        final List<Focus> foci = List.of(
                new Focus(0, 0, 2),
                new Focus(1, 0, -1),
                new Focus(100, 100, 0));
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN, foci, 1);

        assertEquals(2 - Math.exp(-0.5), field.value(0, 0), EPSILON);
        assertEquals(2 * Math.exp(-0.5) - 1, field.value(1, 0), EPSILON);
        assertEquals(0, DistanceFields.create(CurveType.GAUSSIAN,
                List.of(new Focus(0, 0, 0)), 1).value(0, 0), 0);
    }




    @Test
    void gaussianRetainsAWeightThatUnderflowsDuringNormalization() {
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN, List.of(
                new Focus(100, 0, Double.MAX_VALUE),
                new Focus(0, 0, Double.MIN_VALUE)), 1);

        assertEquals(Double.MIN_VALUE, field.value(0, 0), 0);
    }

    @Test
    void gaussianRecoversARepresentableTermLostByWeightNormalization() {
        final double expected = Math.exp(-0.5 * 38 * 38);
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN, List.of(
                new Focus(100, 0, Double.MAX_VALUE),
                new Focus(38, 0, 1)), 1);

        assertEquals(expected, field.value(0, 0), 0);
    }

    @Test
    void gaussianPreservesRepresentableSubnormalTails() {
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN,
                List.of(new Focus(0, 0, 1)), 1);
        final double expected = Math.exp(-0.5 * 38 * 38);

        assertTrue(expected > 0);
        assertEquals(expected, field.value(38, 0), 0);
    }

    @Test
    void exactRadialFallbacksIgnoreUnusedSharedScratchCapacity() {
        final List<Focus> many = IntStream.range(0, 64)
                .mapToObj(index -> new Focus(index + 1, index % 3, index + 0.5))
                .toList();
        DistanceFields.create(CurveType.POWER_MEAN, many, 0.5).value(0, 0);

        final DistanceField cassini = DistanceFields.create(CurveType.CASSIN, List.of(
                new Focus(1, 0, Double.MAX_VALUE),
                new Focus(Math.nextUp(1.0), 0, 1)));
        assertEquals(Math.nextUp(1.0), cassini.value(0, 0), 0);

        final DistanceField gaussian = DistanceFields.create(CurveType.GAUSSIAN, List.of(
                new Focus(0, 0, Double.MAX_VALUE),
                new Focus(0, 0, -Double.MAX_VALUE),
                new Focus(0, 0, Double.MIN_VALUE)), 1);
        assertEquals(Double.MIN_VALUE, gaussian.value(0, 0), 0);
    }

    @Test
    void gaussianAmplitudeCanRescueAnUnderflowedKernel() {
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN,
                List.of(new Focus(40, 0, Double.MAX_VALUE)), 1);
        final double expected = Math.exp(Math.log(Double.MAX_VALUE) - 800);

        assertTrue(expected > 0);
        assertEquals(expected, field.value(0, 0), Math.ulp(expected));
    }

    @Test
    void magnitudeFamiliesPropagateUndefinedCoordinatesInsteadOfTreatingThemAsDisabled() {
        final List<Focus> foci = List.of(new Focus(0, 0, 1));
        for (final CurveType type : List.of(CurveType.NEAREST, CurveType.FARTHEST,
                CurveType.QUADRATIC, CurveType.RANGE, CurveType.POWER_MEAN,
                CurveType.MEDIAN, CurveType.SMOOTH_NEAREST, CurveType.SMOOTH_FARTHEST)) {
            assertTrue(Double.isNaN(DistanceFields.create(type, foci).value(Double.NaN, 0)),
                    type.name());
        }
    }


    @Test
    void finiteCoordinateOverflowCanBeRescaledBackIntoRange() {
        final double maximum = Double.MAX_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(-maximum, 0, 0.5)));

        assertEquals(maximum, field.value(maximum, 0), 0);
    }

    @Test
    void exactFallbackPreservesMinimumSubnormalDistanceResiduals() {
        final double maximum = Double.MAX_VALUE;
        final double minimum = Double.MIN_VALUE;
        final List<Focus> unsigned = List.of(
                new Focus(0, 0, 1), new Focus(minimum, 0, 1));
        final List<Focus> signed = List.of(
                new Focus(0, 0, 1), new Focus(minimum, 0, -1));

        assertEquals(minimum,
                DistanceFields.create(CurveType.LIPSE, signed).value(maximum, 0), 0);
        assertEquals(minimum,
                DistanceFields.create(CurveType.RANGE, unsigned).value(maximum, 0), 0);
        assertEquals(minimum,
                DistanceFields.create(CurveType.HYPERB, unsigned).value(maximum, 0), 0);
    }

    @Test
    void cassiniUsesExtendedRangeDistancesInFiniteRatios() {
        final double maximum = Double.MAX_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.CASSIN, List.of(
                new Focus(-maximum, 0, 1), new Focus(0, 0, -1)));

        assertEquals(2, field.value(maximum, 0), 2e-12);
    }

    @Test
    void potentialUsesExtendedRangeDistanceRatios() {
        final double maximum = Double.MAX_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.POTENTIAL,
                List.of(new Focus(-maximum, 0, maximum)));

        assertEquals(0.5, field.value(maximum, 0), 0);
    }

    @Test
    void gaussianUsesDistanceToWidthRatioBeforeOverflow() {
        final double maximum = Double.MAX_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN,
                List.of(new Focus(-maximum, 0, 1)), maximum);

        assertEquals(Math.exp(-2), field.value(maximum, 0), 2e-15);
    }

    @Test
    void smoothEnvelopesConvergeToTheArithmeticMeanAtHugeTemperature() {
        final List<Focus> foci = List.of(new Focus(2, 0, 1), new Focus(10, 0, 1));
        final double expected = 6;

        assertEquals(expected,
                DistanceFields.create(CurveType.SMOOTH_NEAREST, foci, 1e200).value(0, 0),
                1e-12);
        assertEquals(expected,
                DistanceFields.create(CurveType.SMOOTH_FARTHEST, foci, 1e200).value(0, 0),
                1e-12);
    }

    @Test
    void smoothEnvelopesUseFiniteExtremaWhenAllDistanceRatiosOverflow() {
        final List<Focus> foci = List.of(
                new Focus(10, 0, 1),
                new Focus(2, 0, 1));

        assertEquals(2, DistanceFields.create(
                CurveType.SMOOTH_NEAREST, foci, Double.MIN_VALUE).value(0, 0), 0);
        assertEquals(10, DistanceFields.create(
                CurveType.SMOOTH_FARTHEST, foci, Double.MIN_VALUE).value(0, 0), 0);
    }

    @Test
    void smoothEnvelopesDoNotUseTheFirstValueWhenHugeTemperatureRatiosCollapse() {
        final List<Focus> foci = List.of(
                new Focus(Math.nextUp(1.0), 0, 1),
                new Focus(1, 0, 1));

        assertEquals(1, DistanceFields.create(
                CurveType.SMOOTH_NEAREST, foci, Double.MAX_VALUE).value(0, 0), 0);
        // The arithmetic mean is exactly the tie between 1 and nextUp(1). At every finite
        // temperature smooth-max remains strictly above that mean, so ties-to-even does not
        // apply and the correctly rounded value is the upper neighbour.
        assertEquals(Math.nextUp(1.0), DistanceFields.create(
                CurveType.SMOOTH_FARTHEST, foci, Double.MAX_VALUE).value(0, 0), 0);
    }

    @Test
    void adaptiveFallbackRaisesPrecisionUntilATinyResidualIsRoundable() {
        AdaptiveDecimal.resetStatistics();
        final double maximum = Double.MAX_VALUE;
        final DistanceField field = DistanceFields.create(CurveType.LIPSE, List.of(
                new Focus(1, 0, Double.MIN_VALUE),
                new Focus(maximum, 0, maximum / 2),
                new Focus(maximum, 0, maximum / 2),
                new Focus(maximum, 0, -maximum)));

        assertEquals(Double.MIN_VALUE, field.value(0, 0), 0);
        final AdaptiveDecimal.Statistics statistics = AdaptiveDecimal.statistics();
        assertEquals(1, statistics.evaluations());
        assertTrue(statistics.rounds() > 2);
        assertTrue(statistics.peakPrecision() > 640);
        assertTrue(statistics.peakPrecision() <= AdaptiveDecimal.MAXIMUM_PRECISION);
    }

    @Test
    void exactFocusCoordinatesAreConvertedOnlyOncePerField() {
        final FocusSet foci = FocusSet.from(List.of(
                new Focus(Double.MAX_VALUE, Double.MIN_VALUE, -2),
                new Focus(-1, 3, 0)));

        assertTrue(foci.exactData() == foci.exactData());
        assertEquals(new java.math.BigDecimal(Double.MAX_VALUE), foci.exactData().x(0));
        assertEquals(new java.math.BigDecimal(Double.MIN_VALUE), foci.exactData().y(0));
    }

    @Test
    void signedEllipseUsesExactFallbackBeforeFiniteCancellationLosesUsefulBits() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE, List.of(
                new Focus(1.1, 0, 1e16),
                new Focus(2.3, 0, -4_782_608_695_647_391.0)));

        assertEquals(0x1.57d3807e081ccp13, field.value(0, 0), 0);
    }

    @Test
    void rangeAndHyperbolaPreserveNearEqualWeightedDistances() {
        final List<Focus> foci = List.of(
                new Focus(1.1, 0, 1e16),
                new Focus(2.3, 0, 4_782_608_695_647_391.0));
        final double expected = 0x1.57d3807e081ccp13;

        assertEquals(expected, DistanceFields.create(CurveType.RANGE, foci).value(0, 0), 0);
        assertEquals(expected, DistanceFields.create(CurveType.HYPERB, foci).value(0, 0), 0);
    }

    @Test
    void cassiniRetainsAccuracyWhenLargeWeightedLogarithmsAlmostCancel() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN, List.of(
                new Focus(1.05, 0, 1e16),
                new Focus(0.7, 0, 1_367_916_782_453_125.0)));

        assertEquals(0x1.261bdfd2f2522p1, field.value(0, 0), 0);
    }

    @Test
    void gaussianRetainsAccuracyWhenLargeSignedKernelsAlmostCancel() {
        final DistanceField field = DistanceFields.create(CurveType.GAUSSIAN, List.of(
                new Focus(2.3, 0, 1e16),
                new Focus(2.300023, 0, -1.0002116234460838e16)), 0.5);

        assertEquals(0x1.044b0bd4a3c1ap-2, field.value(0, 0), 0);
    }

    @Test
    void smoothEnvelopesRecoverFiniteRatiosAfterWeightedDistanceOverflow() {
        final double maximum = Double.MAX_VALUE;
        final double ratio = 1.1;
        final List<Focus> foci = List.of(
                new Focus(maximum, 0, ratio),
                new Focus(0, 0, 1));
        final double expectedNearest = -maximum
                * Math.log((Math.exp(-ratio) + 1) / 2);
        final double expectedFarthest = maximum
                * Math.log((Math.exp(ratio) + 1) / 2);

        assertEquals(expectedNearest,
                DistanceFields.create(CurveType.SMOOTH_NEAREST, foci, maximum).value(0, 0),
                8 * Math.ulp(expectedNearest));
        assertEquals(expectedFarthest,
                DistanceFields.create(CurveType.SMOOTH_FARTHEST, foci, maximum).value(0, 0),
                8 * Math.ulp(expectedFarthest));
    }

}
