package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Deterministic exponent-biased differential checks against independent definitions. */
class DistanceFieldsDifferentialTest {
    private static final MathContext REFERENCE = new MathContext(700, RoundingMode.HALF_EVEN);
    private static final long ALGEBRAIC_SEED = 0x4e4c495053454d41L;
    private static final long POTENTIAL_SEED = 0x504f54454e544941L;
    private static final long TRANSCENDENTAL_SEED = 0x52414449414c4655L;

    @Test
    void algebraicFamiliesMatchHighPrecisionReferences() {
        final Random random = new Random(ALGEBRAIC_SEED);
        for (int sample = 0; sample < 72; sample++) {
            final List<Focus> foci = randomFoci(random, 2 + random.nextInt(7), -420, 420);
            final double x = randomFinite(random, -420, 420, false);
            final double y = randomFinite(random, -420, 420, false);
            final ReferenceValues reference = referenceValues(foci, x, y);
            final String label = "sample " + sample;

            assertClose(reference.signedSum(),
                    DistanceFields.create(CurveType.LIPSE, foci).value(x, y), label + " ellipse");
            assertClose(reference.hyperbola(),
                    DistanceFields.create(CurveType.HYPERB, foci).value(x, y), label + " hyperbola");
            assertClose(reference.range(),
                    DistanceFields.create(CurveType.RANGE, foci).value(x, y), label + " range");
            assertClose(reference.quadratic(),
                    DistanceFields.create(CurveType.QUADRATIC, foci).value(x, y),
                    label + " quadratic");
            assertClose(reference.median(),
                    DistanceFields.create(CurveType.MEDIAN, foci).value(x, y), label + " median");
            assertClose(reference.arithmeticMean(),
                    DistanceFields.create(CurveType.POWER_MEAN, foci, 1).value(x, y),
                    label + " power p=1");
            assertClose(reference.rootMeanSquare(),
                    DistanceFields.create(CurveType.POWER_MEAN, foci, 2).value(x, y),
                    label + " power p=2");
        }
    }

    @Test
    void inversePotentialMatchesHighPrecisionReferenceIncludingCancellation() {
        final Random random = new Random(POTENTIAL_SEED);
        for (int sample = 0; sample < 120; sample++) {
            final int count = 2 + random.nextInt(5);
            final List<Focus> foci = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                final double distance = positiveFinite(random, -40, 160);
                double weight = randomFinite(random, -200, 200, false);
                if ((index & 1) != 0) {
                    weight = -Math.abs(weight);
                } else {
                    weight = Math.abs(weight);
                }
                foci.add(new Focus(distance, 0, weight));
            }
            final BigDecimal expectedDecimal = potentialReference(foci, 0, 0);
            final double expected = expectedDecimal.doubleValue();
            final double actual = DistanceFields.create(CurveType.POTENTIAL, foci).value(0, 0);
            assertClose(expected, actual, "potential sample " + sample);
        }
    }

    @Test
    void inversePotentialRetainsUsefulAccuracyUnderDeliberateCancellation() {
        final Random random = new Random(0x43414e43454c4c45L);
        int completed = 0;
        while (completed < 256) {
            final double firstDistance = positiveFinite(random, -500, 500);
            final double secondDistance = positiveFinite(random, -500, 500);
            final double firstWeight = randomFinite(random, -500, 500, false);
            final double firstTerm = firstWeight / firstDistance;
            if (!Double.isFinite(firstTerm) || firstTerm == 0) {
                continue;
            }
            final int cancelledBits = 8 + random.nextInt(56);
            final double perturbation = Math.copySign(Math.scalb(1.0, -cancelledBits),
                    random.nextBoolean() ? 1 : -1);
            final double secondWeight = -firstTerm * (1 + perturbation) * secondDistance;
            if (!Double.isFinite(secondWeight) || secondWeight == 0) {
                continue;
            }

            final List<Focus> foci = List.of(
                    new Focus(firstDistance, 0, firstWeight),
                    new Focus(secondDistance, 0, secondWeight));
            final double expected = potentialReference(foci, 0, 0).doubleValue();
            final double actual = DistanceFields.create(CurveType.POTENTIAL, foci).value(0, 0);
            if (!Double.isFinite(expected) || expected == 0) {
                continue;
            }
            final double tolerance = Math.max(Math.abs(expected) * 1e-8,
                    1024 * Math.ulp(expected));
            assertEquals(expected, actual, tolerance,
                    "cancelled bits " + cancelledBits + " sample " + completed);
            completed++;
        }
    }

    @Test
    void moderateTranscendentalFamiliesMatchDirectDefinitions() {
        final Random random = new Random(TRANSCENDENTAL_SEED);
        final double[] powers = {-3.5, -1.5, -0.25, 0.25, 0.75, 1.5, 3.5};
        for (int sample = 0; sample < 120; sample++) {
            final int count = 2 + random.nextInt(6);
            final List<Focus> foci = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                double weight = -2.5 + 5 * random.nextDouble();
                if (Math.abs(weight) < 0.15) {
                    weight = Math.copySign(0.15, weight == 0 ? 1 : weight);
                }
                foci.add(new Focus(-3 + 6 * random.nextDouble(),
                        -3 + 6 * random.nextDouble(), weight));
            }
            final double x = -2 + 4 * random.nextDouble();
            final double y = -2 + 4 * random.nextDouble();
            final double[] distances = foci.stream()
                    .mapToDouble(focus -> Math.hypot(x - focus.x(), y - focus.y()))
                    .toArray();
            final double[] magnitudes = new double[count];
            for (int index = 0; index < count; index++) {
                magnitudes[index] = Math.abs(foci.get(index).weight()) * distances[index];
            }

            double cassini = 1;
            double gaussian = 0;
            final double sigma = 0.25 + 2.5 * random.nextDouble();
            for (int index = 0; index < count; index++) {
                cassini *= Math.pow(distances[index], foci.get(index).weight());
                final double ratio = distances[index] / sigma;
                gaussian += foci.get(index).weight() * Math.exp(-0.5 * ratio * ratio);
            }
            assertRelative(cassini,
                    DistanceFields.create(CurveType.CASSIN, foci).value(x, y),
                    "cassini sample " + sample);
            assertRelative(gaussian,
                    DistanceFields.create(CurveType.GAUSSIAN, foci, sigma).value(x, y),
                    "gaussian sample " + sample);

            final double power = powers[random.nextInt(powers.length)];
            double powered = 0;
            for (final double magnitude : magnitudes) {
                powered += Math.pow(magnitude, power);
            }
            final double expectedPower = Math.pow(powered / count, 1 / power);
            assertRelative(expectedPower,
                    DistanceFields.create(CurveType.POWER_MEAN, foci, power).value(x, y),
                    "power sample " + sample);

            final double temperature = 0.2 + 2.8 * random.nextDouble();
            double nearestSum = 0;
            double farthestSum = 0;
            for (final double magnitude : magnitudes) {
                nearestSum += Math.exp(-magnitude / temperature);
                farthestSum += Math.exp(magnitude / temperature);
            }
            final double smoothNearest = -temperature * Math.log(nearestSum / count);
            final double smoothFarthest = temperature * Math.log(farthestSum / count);
            assertRelative(smoothNearest,
                    DistanceFields.create(CurveType.SMOOTH_NEAREST, foci, temperature)
                            .value(x, y),
                    "smooth nearest sample " + sample);
            assertRelative(smoothFarthest,
                    DistanceFields.create(CurveType.SMOOTH_FARTHEST, foci, temperature)
                            .value(x, y),
                    "smooth farthest sample " + sample);
        }
    }

    @Test
    void powerMeansAndSmoothEnvelopesRespectTheirOrdering() {
        final Random random = new Random(0x4f52444552494e47L);
        final double[] powers = {Double.NEGATIVE_INFINITY, -4, -1, 0, 1, 2, 4,
                Double.POSITIVE_INFINITY};
        for (int sample = 0; sample < 100; sample++) {
            final List<Focus> foci = randomFoci(random, 2 + random.nextInt(8), -8, 8);
            final double x = randomFinite(random, -8, 8, false);
            final double y = randomFinite(random, -8, 8, false);
            double previous = Double.NEGATIVE_INFINITY;
            for (final double power : powers) {
                final double value = DistanceFields.create(CurveType.POWER_MEAN, foci, power)
                        .value(x, y);
                assertTrue(value >= previous || nearlyEqual(value, previous),
                        "power means decreased at p=" + power + " in sample " + sample);
                previous = value;
            }

            final double nearest = DistanceFields.create(CurveType.NEAREST, foci).value(x, y);
            final double arithmetic = DistanceFields.create(CurveType.POWER_MEAN, foci, 1)
                    .value(x, y);
            final double farthest = DistanceFields.create(CurveType.FARTHEST, foci).value(x, y);
            final double temperature = positiveFinite(random, -6, 4);
            final double smoothNearest = DistanceFields
                    .create(CurveType.SMOOTH_NEAREST, foci, temperature).value(x, y);
            final double smoothFarthest = DistanceFields
                    .create(CurveType.SMOOTH_FARTHEST, foci, temperature).value(x, y);
            assertTrue(nearest <= smoothNearest || nearlyEqual(nearest, smoothNearest));
            assertTrue(smoothNearest <= arithmetic || nearlyEqual(smoothNearest, arithmetic));
            assertTrue(arithmetic <= smoothFarthest || nearlyEqual(arithmetic, smoothFarthest));
            assertTrue(smoothFarthest <= farthest || nearlyEqual(smoothFarthest, farthest));
        }
    }

    private static ReferenceValues referenceValues(final List<Focus> foci,
            final double x, final double y) {
        final BigDecimal[] signed = new BigDecimal[foci.size()];
        final List<BigDecimal> magnitudes = new ArrayList<>();
        for (int index = 0; index < foci.size(); index++) {
            final Focus focus = foci.get(index);
            final BigDecimal distance = distance(x, y, focus);
            signed[index] = distance.multiply(decimal(focus.weight()), REFERENCE);
            if (focus.weight() != 0) {
                magnitudes.add(distance.multiply(decimal(Math.abs(focus.weight())), REFERENCE));
            }
        }
        BigDecimal signedSum = BigDecimal.ZERO;
        for (final BigDecimal value : signed) {
            signedSum = signedSum.add(value, REFERENCE);
        }

        BigDecimal differenceSum = BigDecimal.ZERO;
        for (int first = 0; first < signed.length; first++) {
            for (int second = 0; second < first; second++) {
                differenceSum = differenceSum.add(signed[first].subtract(signed[second], REFERENCE)
                        .abs(), REFERENCE);
            }
        }
        final BigDecimal hyperbola = signed.length < 2 ? BigDecimal.ZERO
                : differenceSum.multiply(BigDecimal.TWO, REFERENCE)
                        .divide(BigDecimal.valueOf((long) signed.length * (signed.length - 1)),
                                REFERENCE);

        magnitudes.sort(Comparator.naturalOrder());
        final BigDecimal range = magnitudes.size() < 2 ? BigDecimal.ZERO
                : magnitudes.getLast().subtract(magnitudes.getFirst(), REFERENCE);
        BigDecimal squares = BigDecimal.ZERO;
        BigDecimal magnitudeSum = BigDecimal.ZERO;
        for (final BigDecimal value : magnitudes) {
            squares = squares.add(value.multiply(value, REFERENCE), REFERENCE);
            magnitudeSum = magnitudeSum.add(value, REFERENCE);
        }
        final BigDecimal quadratic = squares.sqrt(REFERENCE);
        final BigDecimal median;
        if (magnitudes.isEmpty()) {
            median = BigDecimal.ZERO;
        } else if ((magnitudes.size() & 1) == 1) {
            median = magnitudes.get(magnitudes.size() / 2);
        } else {
            final int upper = magnitudes.size() / 2;
            median = magnitudes.get(upper - 1).add(magnitudes.get(upper), REFERENCE)
                    .divide(BigDecimal.TWO, REFERENCE);
        }
        final BigDecimal count = BigDecimal.valueOf(Math.max(1, magnitudes.size()));
        final BigDecimal arithmetic = magnitudes.isEmpty() ? BigDecimal.ZERO
                : magnitudeSum.divide(count, REFERENCE);
        final BigDecimal rms = magnitudes.isEmpty() ? BigDecimal.ZERO
                : squares.divide(count, REFERENCE).sqrt(REFERENCE);
        return new ReferenceValues(signedSum.doubleValue(), hyperbola.doubleValue(),
                range.doubleValue(), quadratic.doubleValue(), median.doubleValue(),
                arithmetic.doubleValue(), rms.doubleValue());
    }

    private static BigDecimal potentialReference(final List<Focus> foci,
            final double x, final double y) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Focus focus : foci) {
            if (focus.weight() != 0) {
                sum = sum.add(decimal(focus.weight()).divide(distance(x, y, focus), REFERENCE),
                        REFERENCE);
            }
        }
        return sum;
    }

    private static BigDecimal distance(final double x, final double y, final Focus focus) {
        final BigDecimal dx = decimal(x).subtract(decimal(focus.x()), REFERENCE);
        final BigDecimal dy = decimal(y).subtract(decimal(focus.y()), REFERENCE);
        return dx.multiply(dx, REFERENCE).add(dy.multiply(dy, REFERENCE), REFERENCE)
                .sqrt(REFERENCE);
    }

    private static BigDecimal decimal(final double value) {
        return new BigDecimal(value);
    }

    private static List<Focus> randomFoci(final Random random, final int count,
            final int minimumExponent, final int maximumExponent) {
        final List<Focus> foci = new ArrayList<>(count);
        boolean active = false;
        for (int index = 0; index < count; index++) {
            double weight = random.nextInt(9) == 0 ? 0
                    : randomFinite(random, minimumExponent, maximumExponent, false);
            if (weight != 0) {
                active = true;
            }
            foci.add(new Focus(randomFinite(random, minimumExponent, maximumExponent, false),
                    randomFinite(random, minimumExponent, maximumExponent, false), weight));
        }
        if (!active) {
            final Focus first = foci.getFirst();
            foci.set(0, new Focus(first.x(), first.y(), 1));
        }
        return List.copyOf(foci);
    }

    private static double positiveFinite(final Random random,
            final int minimumExponent, final int maximumExponent) {
        return Math.abs(randomFinite(random, minimumExponent, maximumExponent, false));
    }

    private static double randomFinite(final Random random,
            final int minimumExponent, final int maximumExponent, final boolean allowZero) {
        if (allowZero && random.nextInt(16) == 0) {
            return 0;
        }
        final int exponent = minimumExponent
                + random.nextInt(maximumExponent - minimumExponent + 1);
        final double mantissa = 0.5 + 0.5 * random.nextDouble();
        return Math.copySign(Math.scalb(mantissa, exponent), random.nextBoolean() ? 1 : -1);
    }

    private static void assertClose(final double expected, final double actual,
            final String label) {
        if (Double.isNaN(expected)) {
            assertTrue(Double.isNaN(actual), label + ": expected NaN but was " + actual);
            return;
        }
        if (Double.isInfinite(expected)) {
            assertEquals(expected, actual, label);
            return;
        }
        final double tolerance = Math.max(512 * Math.ulp(expected), 512 * Double.MIN_VALUE);
        assertEquals(expected, actual, tolerance, label);
    }

    private static void assertRelative(final double expected, final double actual,
            final String label) {
        if (!Double.isFinite(expected)) {
            assertEquals(expected, actual, label);
            return;
        }
        final double tolerance = Math.max(2e-12 * Math.max(1, Math.abs(expected)),
                64 * Math.ulp(expected));
        assertEquals(expected, actual, tolerance, label);
    }

    private static boolean nearlyEqual(final double first, final double second) {
        final double tolerance = Math.max(64 * Math.max(Math.ulp(first), Math.ulp(second)),
                1e-13 * Math.max(1, Math.max(Math.abs(first), Math.abs(second))));
        return Math.abs(first - second) <= tolerance;
    }

    private record ReferenceValues(double signedSum, double hyperbola, double range,
            double quadratic, double median, double arithmeticMean, double rootMeanSquare) {
    }
}
