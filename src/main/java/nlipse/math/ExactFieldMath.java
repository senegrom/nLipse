package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;

/** Rare-path adaptive-precision arithmetic for overflow and severe cancellation. */
final class ExactFieldMath {
    // Decimal exponents of the largest intermediates that can cancel for finite binary64 input.
    // They let AdaptiveDecimal distinguish stable-looking zeroes from unresolved cancellation.
    private static final int WEIGHTED_DISTANCE_SCALE_EXPONENT = 620;
    private static final int POTENTIAL_SCALE_EXPONENT = 640;
    private static final int WEIGHTED_LOG_SCALE_EXPONENT = 320;
    private static final int POWER_SCALE_EXPONENT = 960;
    private static final int SMOOTH_SCALE_EXPONENT = 960;
    private static final int GAUSSIAN_SCALE_EXPONENT = 320;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private ExactFieldMath() {
    }

    static double scaledAbsoluteDifference(final double first, final double second,
            final double scale) {
        return AdaptiveDecimal.exact(first).subtract(AdaptiveDecimal.exact(second)).abs()
                .multiply(AdaptiveDecimal.exact(scale)).doubleValue();
    }

    static double magnitudeDistance(final FocusSet foci, final int index,
            final double x, final double y) {
        return AdaptiveDecimal.toDouble(context ->
                magnitudeDistance(foci.exactData(), index, point(x, y), context)
                        .round(context));
    }

    static double signedDistanceSum(final FocusSet foci, final double x, final double y) {
        return AdaptiveDecimal.toDouble(WEIGHTED_DISTANCE_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(distance(exact, index, point, context)
                            .multiply(exact.weight(index), work), work);
                }
            }
            return sum.round(context);
        });
    }

    static double arithmeticMagnitudeMean(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(magnitudeDistance(exact, index, point, context), work);
                }
            }
            return sum.divide(BigDecimal.valueOf(foci.activeCount()), context);
        });
    }

    static double quadraticMagnitudeMean(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            BigDecimal sumSquares = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    final BigDecimal value = magnitudeDistance(exact, index, point, context);
                    sumSquares = sumSquares.add(value.multiply(value, work), work);
                }
            }
            return sumSquares.divide(BigDecimal.valueOf(foci.activeCount()), work)
                    .sqrt(work).round(context);
        });
    }

    static double quadraticMagnitudeNorm(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            BigDecimal sumSquares = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    final BigDecimal value = magnitudeDistance(exact, index, point, context);
                    sumSquares = sumSquares.add(value.multiply(value, work), work);
                }
            }
            return sumSquares.sqrt(work).round(context);
        });
    }

    static double hyperbola(final FocusSet foci, final double x, final double y) {
        final int size = foci.size();
        if (size < 2) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(WEIGHTED_DISTANCE_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            final BigDecimal[] values = new BigDecimal[size];
            for (int index = 0; index < size; index++) {
                values[index] = distance(exact, index, point, context)
                        .multiply(exact.weight(index), work);
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < size; index++) {
                for (int previous = 0; previous < index; previous++) {
                    sum = sum.add(values[index].subtract(values[previous]).abs(), work);
                }
            }
            final BigDecimal divisor = BigDecimal.valueOf((long) size * (size - 1));
            return sum.multiply(TWO, work).divide(divisor, context);
        });
    }

    static double range(final FocusSet foci, final double x, final double y) {
        if (foci.activeCount() < 2) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(WEIGHTED_DISTANCE_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            BigDecimal minimum = null;
            BigDecimal maximum = null;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final BigDecimal value = magnitudeDistance(exact, index, point, context);
                minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
                maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
            }
            return maximum.subtract(minimum).round(context);
        });
    }

    static double envelope(final FocusSet foci, final double x, final double y,
            final boolean nearest) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            BigDecimal result = null;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final BigDecimal value = magnitudeDistance(exact, index, point, context);
                if (result == null || nearest && value.compareTo(result) < 0
                        || !nearest && value.compareTo(result) > 0) {
                    result = value;
                }
            }
            return result.round(context);
        });
    }

    static double median(final FocusSet foci, final double x, final double y) {
        final int count = foci.activeCount();
        if (count == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final BigDecimal[] values = new BigDecimal[count];
            int target = 0;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    values[target++] = magnitudeDistance(exact, index, point, context);
                }
            }
            Arrays.sort(values);
            final int upper = count / 2;
            if ((count & 1) == 1) {
                return values[upper].round(context);
            }
            return values[upper - 1].add(values[upper])
                    .divide(TWO, context);
        });
    }

    static double potential(final FocusSet foci, final double x, final double y) {
        boolean positiveInfinity = false;
        boolean negativeInfinity = false;
        for (int index = 0; index < foci.size(); index++) {
            if (!foci.isActive(index) || x != foci.x(index) || y != foci.y(index)) {
                continue;
            }
            positiveInfinity |= foci.weight(index) > 0;
            negativeInfinity |= foci.weight(index) < 0;
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
        return AdaptiveDecimal.toDouble(POTENTIAL_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(exact.weight(index).divide(
                            distance(exact, index, point, context), work), work);
                }
            }
            return sum.round(context);
        });
    }

    static double cassini(final FocusSet foci, final double x, final double y) {
        boolean zeroFactor = false;
        boolean infiniteFactor = false;
        for (int index = 0; index < foci.size(); index++) {
            if (!foci.isActive(index) || x != foci.x(index) || y != foci.y(index)) {
                continue;
            }
            zeroFactor |= foci.weight(index) > 0;
            infiniteFactor |= foci.weight(index) < 0;
        }
        if (zeroFactor && infiniteFactor) {
            return Double.NaN;
        }
        if (zeroFactor) {
            return 0;
        }
        if (infiniteFactor) {
            return Double.POSITIVE_INFINITY;
        }
        final double logarithm = AdaptiveDecimal.toDouble(WEIGHTED_LOG_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    final BigDecimal logDistance = DecimalMath.log(
                            distance(exact, index, point, context), work);
                    sum = sum.add(exact.weight(index).multiply(logDistance, work), work);
                }
            }
            return sum.round(context);
        });
        return FieldMath.expFromLog(logarithm);
    }

    static double powerMean(final FocusSet foci, final double x, final double y,
            final double power) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        if (power == Double.POSITIVE_INFINITY) {
            return envelope(foci, x, y, false);
        }
        if (power == Double.NEGATIVE_INFINITY) {
            return envelope(foci, x, y, true);
        }
        if (power == 1) {
            return arithmeticMagnitudeMean(foci, x, y);
        }
        if (power == 2) {
            return quadraticMagnitudeMean(foci, x, y);
        }
        return AdaptiveDecimal.toDouble(POWER_SCALE_EXPONENT, context -> powerMeanDecimal(foci, x, y, power, context));
    }

    static double smoothEnvelope(final FocusSet foci, final double x, final double y,
            final double temperature, final boolean nearest) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(SMOOTH_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            final BigDecimal tau = AdaptiveDecimal.exact(temperature);
            final BigDecimal[] values = magnitudeDistances(foci, exact, point, context);
            BigDecimal anchor = values[0];
            for (int index = 1; index < values.length; index++) {
                if (nearest && values[index].compareTo(anchor) < 0
                        || !nearest && values[index].compareTo(anchor) > 0) {
                    anchor = values[index];
                }
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (final BigDecimal value : values) {
                final BigDecimal exponent = nearest
                        ? anchor.subtract(value).divide(tau, work)
                        : value.subtract(anchor).divide(tau, work);
                sum = sum.add(DecimalMath.exp(exponent, work), work);
            }
            final BigDecimal mean = sum.divide(BigDecimal.valueOf(values.length), work);
            final BigDecimal correction = tau.multiply(DecimalMath.log(mean, work), work);
            return (nearest ? anchor.subtract(correction, work)
                    : anchor.add(correction, work)).round(context);
        });
    }

    static double gaussian(final FocusSet foci, final double x, final double y,
            final double sigma) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(GAUSSIAN_SCALE_EXPONENT, context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            final BigDecimal sigmaDecimal = AdaptiveDecimal.exact(sigma);
            final BigDecimal denominator = sigmaDecimal.multiply(sigmaDecimal, work)
                    .multiply(TWO, work);
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final BigDecimal distance = distance(exact, index, point, context);
                final BigDecimal exponent = distance.multiply(distance, work)
                        .divide(denominator, work).negate();
                final BigDecimal kernel = DecimalMath.exp(exponent, work);
                sum = sum.add(exact.weight(index).multiply(kernel, work), work);
            }
            return sum.round(context);
        });
    }

    private static BigDecimal powerMeanDecimal(final FocusSet foci, final double x,
            final double y, final double power, final MathContext context) {
        final DecimalPoint point = point(x, y);
        final ExactFocusData exact = foci.exactData();
        final MathContext work = AdaptiveDecimal.guard(context);
        final BigDecimal[] values = magnitudeDistances(foci, exact, point, context);
        for (final BigDecimal value : values) {
            if (value.signum() == 0) {
                if (power <= 0) {
                    return BigDecimal.ZERO;
                }
            }
        }
        if (power == 0) {
            BigDecimal logSum = BigDecimal.ZERO;
            for (final BigDecimal value : values) {
                logSum = logSum.add(DecimalMath.log(value, work), work);
            }
            return DecimalMath.exp(logSum.divide(BigDecimal.valueOf(values.length), work), context);
        }

        final BigDecimal powerDecimal = AdaptiveDecimal.exact(power);
        final BigDecimal[] logarithms = new BigDecimal[values.length];
        BigDecimal anchor = null;
        for (int index = 0; index < values.length; index++) {
            if (values[index].signum() == 0) {
                continue;
            }
            logarithms[index] = DecimalMath.log(values[index], work);
            if (anchor == null || power > 0 && logarithms[index].compareTo(anchor) > 0
                    || power < 0 && logarithms[index].compareTo(anchor) < 0) {
                anchor = logarithms[index];
            }
        }
        BigDecimal exponentialSum = BigDecimal.ZERO;
        for (final BigDecimal logarithm : logarithms) {
            if (logarithm == null) {
                continue;
            }
            final BigDecimal exponent = powerDecimal.multiply(
                    logarithm.subtract(anchor), work);
            exponentialSum = exponentialSum.add(DecimalMath.exp(exponent, work), work);
        }
        final BigDecimal mean = exponentialSum.divide(BigDecimal.valueOf(values.length), work);
        final BigDecimal resultLogarithm = anchor.add(
                DecimalMath.log(mean, work).divide(powerDecimal, work), work);
        return DecimalMath.exp(resultLogarithm, context);
    }

    private static BigDecimal[] magnitudeDistances(final FocusSet foci,
            final ExactFocusData exact, final DecimalPoint point, final MathContext context) {
        final BigDecimal[] values = new BigDecimal[foci.activeCount()];
        int target = 0;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                values[target++] = magnitudeDistance(exact, index, point, context);
            }
        }
        return values;
    }

    private static BigDecimal distance(final ExactFocusData exact, final int index,
            final DecimalPoint point, final MathContext context) {
        final MathContext work = AdaptiveDecimal.guard(context);
        final BigDecimal dx = point.x().subtract(exact.x(index));
        final BigDecimal dy = point.y().subtract(exact.y(index));
        return dx.multiply(dx, work).add(dy.multiply(dy, work), work)
                .sqrt(work).round(context);
    }

    private static BigDecimal magnitudeDistance(final ExactFocusData exact, final int index,
            final DecimalPoint point, final MathContext context) {
        return distance(exact, index, point, context)
                .multiply(exact.absoluteWeight(index), AdaptiveDecimal.guard(context))
                .round(context);
    }

    private static DecimalPoint point(final double x, final double y) {
        return new DecimalPoint(AdaptiveDecimal.exact(x), AdaptiveDecimal.exact(y));
    }

    private record DecimalPoint(BigDecimal x, BigDecimal y) {
    }
}
