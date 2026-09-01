package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import org.junit.jupiter.api.Test;

/**
 * The adaptive evaluators promise a result provably inside one binary64
 * rounding cell. That promise is checked here as strict bit equality with
 * 700-digit references on deliberately ill-conditioned inputs, which is what
 * exercises the per-call cancellation scales, the truncation contract of
 * DecimalMath.exp and the far-field signed-zero shortcut.
 */
class ExactFieldMathDifferentialTest {
    private static final MathContext REFERENCE = new MathContext(700, RoundingMode.HALF_EVEN);

    @Test
    void algebraicEvaluatorsRoundExactly() {
        final Random random = new Random(0x4558414354414c47L);
        for (int sample = 0; sample < 60; sample++) {
            final List<Focus> foci = randomFoci(random, 2 + random.nextInt(5), -250, 250);
            final double x = randomFinite(random, -250, 250);
            final double y = randomFinite(random, -250, 250);
            final FocusSet set = FocusSet.from(foci);
            final String label = "sample " + sample;

            assertBits(signedSum(foci, x, y), ExactFieldMath.signedDistanceSum(set, x, y),
                    label + " signed sum");
            assertBits(hyperbola(foci, x, y), ExactFieldMath.hyperbola(set, x, y),
                    label + " hyperbola");
            if (set.activeCount() >= 2) {
                assertBits(range(foci, x, y), ExactFieldMath.range(set, x, y),
                        label + " range");
            }
            assertBits(potential(foci, x, y), ExactFieldMath.potential(set, x, y),
                    label + " potential");
        }
    }

    @Test
    void deliberateCancellationRoundsExactly() {
        final Random random = new Random(0x43414e43454c4c58L);
        for (int sample = 0; sample < 60; sample++) {
            final double firstDistance = positiveFinite(random, -200, 200);
            final double secondDistance = positiveFinite(random, -200, 200);
            final double firstWeight = randomFinite(random, -200, 200);
            final int cancelledBits = 8 + random.nextInt(50);
            final double perturbation = Math.scalb(1.0, -cancelledBits)
                    * (random.nextBoolean() ? 1 : -1);
            final double sumWeight = -firstWeight * firstDistance * (1 + perturbation)
                    / secondDistance;
            final double potentialWeight = -firstWeight / firstDistance * (1 + perturbation)
                    * secondDistance;
            if (!Double.isFinite(sumWeight) || sumWeight == 0
                    || !Double.isFinite(potentialWeight) || potentialWeight == 0) {
                continue;
            }
            final String label = "cancelled bits " + cancelledBits + " sample " + sample;

            final List<Focus> summed = List.of(new Focus(firstDistance, 0, firstWeight),
                    new Focus(secondDistance, 0, sumWeight));
            assertBits(signedSum(summed, 0, 0),
                    ExactFieldMath.signedDistanceSum(FocusSet.from(summed), 0, 0),
                    label + " signed sum");

            final List<Focus> inverse = List.of(new Focus(firstDistance, 0, firstWeight),
                    new Focus(secondDistance, 0, potentialWeight));
            assertBits(potential(inverse, 0, 0),
                    ExactFieldMath.potential(FocusSet.from(inverse), 0, 0),
                    label + " potential");

            final double sigma = positiveFinite(random, -4, 4);
            final List<Focus> kernels = List.of(new Focus(firstDistance, 0, firstWeight),
                    new Focus(firstDistance, 0, -firstWeight * (1 + perturbation)));
            assertBits(gaussian(kernels, 0, 0, sigma),
                    ExactFieldMath.gaussian(FocusSet.from(kernels), 0, 0, sigma),
                    label + " gaussian");
        }
    }

    @Test
    void transcendentalEvaluatorsRoundExactly() {
        final Random random = new Random(0x5452414e53434e44L);
        final double[] powers = {-3.5, -1.5, -0.25, 0, 0.25, 0.75, 1.5, 3.5};
        for (int sample = 0; sample < 40; sample++) {
            final List<Focus> foci = randomFoci(random, 2 + random.nextInt(4), -30, 30);
            final double x = randomFinite(random, -30, 30);
            final double y = randomFinite(random, -30, 30);
            final FocusSet set = FocusSet.from(foci);
            final String label = "sample " + sample;

            final double sigma = positiveFinite(random, -8, 8);
            assertRoundsTo(gaussian(foci, x, y, sigma), ExactFieldMath.gaussian(set, x, y, sigma),
                    zeroSign(foci, x, y, sigma), label + " gaussian");
            assertBits(FieldMath.expFromLog(cassiniLog(foci, x, y)),
                    ExactFieldMath.cassini(set, x, y), label + " cassini");

            final double power = powers[random.nextInt(powers.length)];
            assertBits(powerMean(foci, x, y, power),
                    ExactFieldMath.powerMean(set, x, y, power), label + " power " + power);

            final double temperature = positiveFinite(random, -8, 8);
            assertBits(smoothEnvelope(foci, x, y, temperature, true),
                    ExactFieldMath.smoothEnvelope(set, x, y, temperature, true),
                    label + " smooth nearest");
            assertBits(smoothEnvelope(foci, x, y, temperature, false),
                    ExactFieldMath.smoothEnvelope(set, x, y, temperature, false),
                    label + " smooth farthest");
        }
    }

    @Test
    void farFieldGaussianKeepsTheReferenceSignedZero() {
        final Random random = new Random(0x46415246494c4420L);
        for (int sample = 0; sample < 40; sample++) {
            final List<Focus> foci = new ArrayList<>();
            final int count = 1 + random.nextInt(4);
            for (int index = 0; index < count; index++) {
                foci.add(new Focus(-2 + 4 * random.nextDouble(), -2 + 4 * random.nextDouble(),
                        randomFinite(random, -20, 20)));
            }
            final double angle = 2 * Math.PI * random.nextDouble();
            final double radius = 45 + 25 * random.nextDouble();
            final double x = radius * Math.cos(angle);
            final double y = radius * Math.sin(angle);
            final double expected = gaussian(foci, x, y, 1);
            final double sign = zeroSign(foci, x, y, 1);
            final String label = "sample " + sample;

            assertRoundsTo(expected, ExactFieldMath.gaussian(FocusSet.from(foci), x, y, 1),
                    sign, label + " exact evaluator");
            assertRoundsTo(expected,
                    DistanceFields.create(CurveType.GAUSSIAN, foci, 1).value(x, y),
                    sign, label + " field shortcut");
        }
    }

    /**
     * The sign a wholly underflowed Gaussian sum's zero must carry: that of
     * the sign whose largest term provably dominates the other sign's total,
     * or 0 when neither does and the contract promises only the value. This
     * cannot come from the reference, whose own exponentials truncate to an
     * unsigned decimal zero far below the binary64 range.
     */
    private static double zeroSign(final List<Focus> foci, final double x, final double y,
            final double sigma) {
        double positive = Double.NEGATIVE_INFINITY;
        double negative = Double.NEGATIVE_INFINITY;
        for (final Focus focus : foci) {
            final double ratio = Math.hypot(x - focus.x(), y - focus.y()) / sigma;
            final double logTerm = Math.log(Math.abs(focus.weight())) - 0.5 * ratio * ratio;
            if (focus.weight() > 0) {
                positive = Math.max(positive, logTerm);
            } else {
                negative = Math.max(negative, logTerm);
            }
        }
        if (!(Math.abs(positive - negative) > Math.log(foci.size()) + 1)) {
            return 0;
        }
        return positive > negative ? 1 : -1;
    }

    private static void assertRoundsTo(final double expected, final double actual,
            final double zeroSign, final String label) {
        if (expected != 0) {
            assertBits(expected, actual, label);
        } else if (zeroSign == 0) {
            assertEquals(0.0, actual, 0, label);
        } else {
            assertBits(Math.copySign(0.0, zeroSign), actual, label);
        }
    }

    private static double signedSum(final List<Focus> foci, final double x, final double y) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Focus focus : foci) {
            sum = sum.add(distance(x, y, focus).multiply(decimal(focus.weight()), REFERENCE),
                    REFERENCE);
        }
        return sum.doubleValue();
    }

    private static double hyperbola(final List<Focus> foci, final double x, final double y) {
        final int size = foci.size();
        final BigDecimal[] values = new BigDecimal[size];
        for (int index = 0; index < size; index++) {
            values[index] = distance(x, y, foci.get(index))
                    .multiply(decimal(foci.get(index).weight()), REFERENCE);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int first = 0; first < size; first++) {
            for (int second = 0; second < first; second++) {
                sum = sum.add(values[first].subtract(values[second], REFERENCE).abs(), REFERENCE);
            }
        }
        return sum.multiply(BigDecimal.TWO, REFERENCE)
                .divide(BigDecimal.valueOf((long) size * (size - 1)), REFERENCE).doubleValue();
    }

    private static double range(final List<Focus> foci, final double x, final double y) {
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (final BigDecimal value : magnitudes(foci, x, y)) {
            minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
            maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
        }
        return maximum.subtract(minimum, REFERENCE).doubleValue();
    }

    private static double potential(final List<Focus> foci, final double x, final double y) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Focus focus : foci) {
            if (focus.weight() != 0) {
                sum = sum.add(decimal(focus.weight()).divide(distance(x, y, focus), REFERENCE),
                        REFERENCE);
            }
        }
        return sum.doubleValue();
    }

    private static double gaussian(final List<Focus> foci, final double x, final double y,
            final double sigma) {
        final BigDecimal denominator = decimal(sigma).multiply(decimal(sigma), REFERENCE)
                .multiply(BigDecimal.TWO, REFERENCE);
        BigDecimal sum = BigDecimal.ZERO;
        for (final Focus focus : foci) {
            if (focus.weight() == 0) {
                continue;
            }
            final BigDecimal distance = distance(x, y, focus);
            final BigDecimal exponent = distance.multiply(distance, REFERENCE)
                    .divide(denominator, REFERENCE).negate();
            sum = sum.add(decimal(focus.weight())
                    .multiply(DecimalMath.exp(exponent, REFERENCE), REFERENCE), REFERENCE);
        }
        return sum.doubleValue();
    }

    private static double cassiniLog(final List<Focus> foci, final double x, final double y) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Focus focus : foci) {
            if (focus.weight() != 0) {
                sum = sum.add(decimal(focus.weight())
                        .multiply(DecimalMath.log(distance(x, y, focus), REFERENCE), REFERENCE),
                        REFERENCE);
            }
        }
        return sum.doubleValue();
    }

    private static double powerMean(final List<Focus> foci, final double x, final double y,
            final double power) {
        final List<BigDecimal> logs = new ArrayList<>();
        for (final BigDecimal value : magnitudes(foci, x, y)) {
            logs.add(DecimalMath.log(value, REFERENCE));
        }
        final BigDecimal count = BigDecimal.valueOf(logs.size());
        if (power == 0) {
            BigDecimal sum = BigDecimal.ZERO;
            for (final BigDecimal logarithm : logs) {
                sum = sum.add(logarithm, REFERENCE);
            }
            return DecimalMath.exp(sum.divide(count, REFERENCE), REFERENCE).doubleValue();
        }
        final BigDecimal exponent = decimal(power);
        BigDecimal anchor = logs.getFirst();
        for (final BigDecimal logarithm : logs) {
            if (power > 0 ? logarithm.compareTo(anchor) > 0 : logarithm.compareTo(anchor) < 0) {
                anchor = logarithm;
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final BigDecimal logarithm : logs) {
            sum = sum.add(DecimalMath.exp(
                    exponent.multiply(logarithm.subtract(anchor, REFERENCE), REFERENCE),
                    REFERENCE), REFERENCE);
        }
        final BigDecimal result = anchor.add(DecimalMath.log(sum.divide(count, REFERENCE),
                REFERENCE).divide(exponent, REFERENCE), REFERENCE);
        return DecimalMath.exp(result, REFERENCE).doubleValue();
    }

    private static double smoothEnvelope(final List<Focus> foci, final double x,
            final double y, final double temperature, final boolean nearest) {
        final List<BigDecimal> values = magnitudes(foci, x, y);
        final BigDecimal tau = decimal(temperature);
        BigDecimal anchor = values.getFirst();
        for (final BigDecimal value : values) {
            if (nearest ? value.compareTo(anchor) < 0 : value.compareTo(anchor) > 0) {
                anchor = value;
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final BigDecimal value : values) {
            final BigDecimal argument = nearest
                    ? anchor.subtract(value, REFERENCE).divide(tau, REFERENCE)
                    : value.subtract(anchor, REFERENCE).divide(tau, REFERENCE);
            sum = sum.add(DecimalMath.exp(argument, REFERENCE), REFERENCE);
        }
        final BigDecimal correction = tau.multiply(DecimalMath.log(
                sum.divide(BigDecimal.valueOf(values.size()), REFERENCE), REFERENCE), REFERENCE);
        return (nearest ? anchor.subtract(correction, REFERENCE)
                : anchor.add(correction, REFERENCE)).doubleValue();
    }

    private static List<BigDecimal> magnitudes(final List<Focus> foci, final double x,
            final double y) {
        final List<BigDecimal> values = new ArrayList<>();
        for (final Focus focus : foci) {
            if (focus.weight() != 0) {
                values.add(distance(x, y, focus)
                        .multiply(decimal(Math.abs(focus.weight())), REFERENCE));
            }
        }
        return values;
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
        for (int index = 0; index < count; index++) {
            foci.add(new Focus(randomFinite(random, minimumExponent, maximumExponent),
                    randomFinite(random, minimumExponent, maximumExponent),
                    randomFinite(random, minimumExponent, maximumExponent)));
        }
        return List.copyOf(foci);
    }

    private static double positiveFinite(final Random random, final int minimumExponent,
            final int maximumExponent) {
        return Math.abs(randomFinite(random, minimumExponent, maximumExponent));
    }

    private static double randomFinite(final Random random, final int minimumExponent,
            final int maximumExponent) {
        final int exponent = minimumExponent
                + random.nextInt(maximumExponent - minimumExponent + 1);
        final double mantissa = 0.5 + 0.5 * random.nextDouble();
        return Math.copySign(Math.scalb(mantissa, exponent), random.nextBoolean() ? 1 : -1);
    }

    private static void assertBits(final double expected, final double actual,
            final String label) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual),
                () -> label + ": expected " + expected + " but was " + actual);
    }
}
