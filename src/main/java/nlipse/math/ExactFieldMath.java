package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

/** Rare-path high-precision arithmetic for overflow and severe cancellation. */
final class ExactFieldMath {
    // The full finite double range spans about 632 decimal orders of magnitude.
    // This precision can retain a Double.MIN_VALUE residual beside Double.MAX_VALUE terms.
    private static final MathContext CONTEXT = new MathContext(768, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private ExactFieldMath() {
    }

    static double scaledAbsoluteDifference(final double first, final double second,
            final double scale) {
        return exact(first).subtract(exact(second)).abs()
                .multiply(exact(scale), CONTEXT).doubleValue();
    }

    static BigDecimal distance(final FocusSet foci, final int index,
            final double x, final double y) {
        final BigDecimal dx = exact(x).subtract(exact(foci.x(index)));
        final BigDecimal dy = exact(y).subtract(exact(foci.y(index)));
        return dx.multiply(dx, CONTEXT).add(dy.multiply(dy, CONTEXT), CONTEXT).sqrt(CONTEXT);
    }

    static BigDecimal signedDistance(final FocusSet foci, final int index,
            final double x, final double y) {
        return distance(foci, index, x, y).multiply(exact(foci.weight(index)), CONTEXT);
    }

    static BigDecimal magnitudeDistance(final FocusSet foci, final int index,
            final double x, final double y) {
        return distance(foci, index, x, y)
                .multiply(exact(Math.abs(foci.weight(index))), CONTEXT);
    }

    static double signedDistanceSum(final FocusSet foci, final double x, final double y) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.weight(index) != 0) {
                sum = sum.add(signedDistance(foci, index, x, y), CONTEXT);
            }
        }
        return sum.doubleValue();
    }

    static double arithmeticMagnitudeMean(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                sum = sum.add(magnitudeDistance(foci, index, x, y), CONTEXT);
            }
        }
        return sum.divide(BigDecimal.valueOf(foci.activeCount()), CONTEXT).doubleValue();
    }

    static double quadraticMagnitudeMean(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                final BigDecimal value = magnitudeDistance(foci, index, x, y);
                sumSquares = sumSquares.add(value.multiply(value, CONTEXT), CONTEXT);
            }
        }
        return sumSquares.divide(BigDecimal.valueOf(foci.activeCount()), CONTEXT)
                .sqrt(CONTEXT).doubleValue();
    }

    static double hyperbola(final FocusSet foci, final double x, final double y) {
        final int size = foci.size();
        if (size < 2) {
            return 0;
        }
        final BigDecimal[] values = new BigDecimal[size];
        for (int index = 0; index < size; index++) {
            values[index] = signedDistance(foci, index, x, y);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < size; index++) {
            for (int previous = 0; previous < index; previous++) {
                sum = sum.add(values[index].subtract(values[previous]).abs(), CONTEXT);
            }
        }
        final BigDecimal divisor = BigDecimal.valueOf((long) size * (size - 1));
        return sum.multiply(TWO, CONTEXT).divide(divisor, CONTEXT).doubleValue();
    }

    static double range(final FocusSet foci, final double x, final double y) {
        if (foci.activeCount() < 2) {
            return 0;
        }
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (int index = 0; index < foci.size(); index++) {
            if (!foci.isActive(index)) {
                continue;
            }
            final BigDecimal value = magnitudeDistance(foci, index, x, y);
            minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
            maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
        }
        return maximum.subtract(minimum, CONTEXT).doubleValue();
    }

    static double median(final FocusSet foci, final double x, final double y) {
        final int count = foci.activeCount();
        if (count == 0) {
            return 0;
        }
        final BigDecimal[] values = new BigDecimal[count];
        int target = 0;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                values[target++] = magnitudeDistance(foci, index, x, y);
            }
        }
        Arrays.sort(values);
        final int upper = count / 2;
        if ((count & 1) == 1) {
            return values[upper].doubleValue();
        }
        return values[upper - 1].add(values[upper], CONTEXT)
                .divide(TWO, CONTEXT).doubleValue();
    }

    static double potential(final FocusSet foci, final double x, final double y) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean positiveInfinity = false;
        boolean negativeInfinity = false;
        for (int index = 0; index < foci.size(); index++) {
            final double weight = foci.weight(index);
            if (weight == 0) {
                continue;
            }
            final BigDecimal distance = distance(foci, index, x, y);
            if (distance.signum() == 0) {
                positiveInfinity |= weight > 0;
                negativeInfinity |= weight < 0;
            } else {
                sum = sum.add(exact(weight).divide(distance, CONTEXT), CONTEXT);
            }
        }
        if (positiveInfinity && negativeInfinity) {
            return Double.NaN;
        }
        if (positiveInfinity) {
            return Double.POSITIVE_INFINITY;
        }
        if (negativeInfinity) {
            return Double.NEGATIVE_INFINITY;
        }
        return sum.doubleValue();
    }

    static double weightedDoubleSum(final double[] values, final double[] weights) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < values.length; index++) {
            if (weights[index] != 0 && values[index] != 0) {
                sum = sum.add(exact(weights[index]).multiply(exact(values[index]), CONTEXT), CONTEXT);
            }
        }
        return sum.doubleValue();
    }

    static double weightedLogSum(final double[] logarithms, final double[] weights) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < logarithms.length; index++) {
            if (weights[index] != 0 && logarithms[index] != 0) {
                sum = sum.add(exact(weights[index])
                        .multiply(BigDecimal.valueOf(logarithms[index]), CONTEXT), CONTEXT);
            }
        }
        return sum.doubleValue();
    }

    static double multiply(final double first, final double second) {
        return exact(first).multiply(exact(second), CONTEXT).doubleValue();
    }

    private static BigDecimal exact(final double value) {
        return new BigDecimal(value);
    }
}
