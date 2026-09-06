package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Rare-path adaptive-precision arithmetic for overflow and severe cancellation.
 *
 * <p>Every evaluator computes a {@link Ball} enclosure, so {@link AdaptiveDecimal}
 * accepts a result on the carried error bound alone: whatever cancelled inside
 * a sum is reflected in its radius, and no per-family scale of the largest
 * intermediate has to be estimated up front. The digit reservations that
 * remain (extra working digits ahead of a large weight or a tiny power) only
 * shorten the precision ladder; they never decide correctness.
 */
final class ExactFieldMath {
    private static final double LN_TEN = Math.log(10);
    /** Cap on the digits reserved ahead of time; the adaptive loop supplies the rest. */
    private static final int RESERVED_DIGITS_CAP = 400;
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
        return AdaptiveDecimal.toDouble(context -> magnitudeDistance(foci.exactData(), index,
                point(x, y), AdaptiveDecimal.guard(context)));
    }

    static double signedDistanceSum(final FocusSet foci, final double x, final double y) {
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            Ball sum = Ball.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(distance(exact, index, point, work)
                            .multiply(exact.weight(index), work), work);
                }
            }
            return sum;
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
            Ball sum = Ball.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(magnitudeDistance(exact, index, point, work), work);
                }
            }
            return sum.divide(BigDecimal.valueOf(foci.activeCount()), work);
        });
    }

    static double quadraticMagnitudeMean(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            return sumOfSquares(foci, point(x, y), work)
                    .divide(BigDecimal.valueOf(foci.activeCount()), work).sqrt(work);
        });
    }

    static double quadraticMagnitudeNorm(final FocusSet foci,
            final double x, final double y) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            return sumOfSquares(foci, point(x, y), work).sqrt(work);
        });
    }

    private static Ball sumOfSquares(final FocusSet foci, final DecimalPoint point,
            final MathContext work) {
        final ExactFocusData exact = foci.exactData();
        Ball sumSquares = Ball.ZERO;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                final Ball value = magnitudeDistance(exact, index, point, work);
                sumSquares = sumSquares.add(value.multiply(value, work), work);
            }
        }
        return sumSquares;
    }

    static double hyperbola(final FocusSet foci, final double x, final double y) {
        final int size = foci.size();
        if (size < 2) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball[] values = new Ball[size];
            for (int index = 0; index < size; index++) {
                values[index] = distance(exact, index, point, work)
                        .multiply(exact.weight(index), work);
            }
            Ball sum = Ball.ZERO;
            for (int index = 0; index < size; index++) {
                for (int previous = 0; previous < index; previous++) {
                    sum = sum.add(values[index].subtract(values[previous], work).abs(), work);
                }
            }
            final BigDecimal divisor = BigDecimal.valueOf((long) size * (size - 1));
            return sum.multiply(TWO, work).divide(divisor, work);
        });
    }

    static double range(final FocusSet foci, final double x, final double y) {
        final int count = foci.activeCount();
        if (count < 2) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball[] values = magnitudeDistances(foci, foci.exactData(), point(x, y), work);
            return Ball.orderStatistic(values, count - 1)
                    .subtract(Ball.orderStatistic(values, 0), work);
        });
    }

    static double envelope(final FocusSet foci, final double x, final double y,
            final boolean nearest) {
        final int count = foci.activeCount();
        if (count == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball[] values = magnitudeDistances(foci, foci.exactData(), point(x, y), work);
            return Ball.orderStatistic(values, nearest ? 0 : count - 1);
        });
    }

    static double median(final FocusSet foci, final double x, final double y) {
        final int count = foci.activeCount();
        if (count == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball[] values = magnitudeDistances(foci, foci.exactData(), point(x, y), work);
            final int upper = count / 2;
            if ((count & 1) == 1) {
                return Ball.orderStatistic(values, upper);
            }
            return Ball.orderStatistic(values, upper - 1)
                    .add(Ball.orderStatistic(values, upper), work).divide(TWO, work);
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
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            Ball sum = Ball.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(Ball.exact(exact.weight(index))
                            .divide(distance(exact, index, point, work), work), work);
                }
            }
            return sum;
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
        // A large weight multiplies the logarithm's absolute error into the
        // exponent, where exp amplifies it. Reserving the corresponding digits
        // for the logarithms lets the first round resolve such sums; the
        // enclosure would otherwise reach the same precision by escalation.
        final int count = Math.max(1, foci.activeCount());
        double largestTermLn = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                largestTermLn = Math.max(largestTermLn,
                        Math.log(Math.abs(foci.weight(index))) + Math.log(
                                Math.max(1, Math.abs(foci.logDistance(index, x, y)))));
            }
        }
        final int reserved = reservedDigits(largestTermLn + Math.log(2.0 * count * count));
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(amplifiedContext(context, reserved));
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            Ball sum = Ball.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    sum = sum.add(distance(exact, index, point, work).log(work)
                            .multiply(exact.weight(index), work), work);
                }
            }
            // Round the final exponential, not the logarithm, to binary64
            // (notably at overflow and underflow).
            return sum.exp(context);
        });
    }

    /** Digits to reserve ahead of an intermediate of the given ln-magnitude, capped. */
    private static int reservedDigits(final double largestLnMagnitude) {
        if (!(largestLnMagnitude > 0)) {
            return 0;
        }
        return (int) Math.min(RESERVED_DIGITS_CAP, Math.ceil(largestLnMagnitude / LN_TEN) + 4);
    }

    /** Reserves digits lost through multiplication by a large weight or division by a tiny p. */
    private static MathContext amplifiedContext(final MathContext context, final int extraDigits) {
        return new MathContext(Math.min(AdaptiveDecimal.MAXIMUM_PRECISION,
                context.getPrecision() + extraDigits), context.getRoundingMode());
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
        return AdaptiveDecimal.toDouble(context -> powerMeanEnclosure(foci, x, y, power, context));
    }

    static double smoothEnvelope(final FocusSet foci, final double x, final double y,
            final double temperature, final boolean nearest) {
        final int count = foci.activeCount();
        if (count == 0) {
            return 0;
        }
        return AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            final BigDecimal tau = AdaptiveDecimal.exact(temperature);
            final Ball[] values = magnitudeDistances(foci, exact, point, work);
            // The identity anchor +- tau log(mean exp(+-(v - anchor)/tau)) holds
            // for any anchor; the extreme one keeps every exponent non-positive.
            Ball anchor = values[0];
            for (int index = 1; index < values.length; index++) {
                final int comparison = values[index].midpoint().compareTo(anchor.midpoint());
                if (nearest ? comparison < 0 : comparison > 0) {
                    anchor = values[index];
                }
            }
            Ball sum = Ball.ZERO;
            for (final Ball value : values) {
                final Ball exponent = (nearest ? anchor.subtract(value, work)
                        : value.subtract(anchor, work)).divide(tau, work);
                sum = sum.add(exponent.exp(work), work);
            }
            final Ball mean = sum.divide(BigDecimal.valueOf(values.length), work);
            final Ball correction = mean.log(work).multiply(tau, work);
            return nearest ? anchor.subtract(correction, work) : anchor.add(correction, work);
        });
    }

    static double gaussian(final FocusSet foci, final double x, final double y,
            final double sigma) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        final double value = AdaptiveDecimal.toDouble(context -> {
            final DecimalPoint point = point(x, y);
            final ExactFocusData exact = foci.exactData();
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball sigmaDecimal = Ball.exact(sigma);
            final Ball denominator = sigmaDecimal.multiply(sigmaDecimal, work).multiply(TWO, work);
            Ball sum = Ball.ZERO;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final Ball distance = distance(exact, index, point, work);
                final Ball exponent = distance.multiply(distance, work)
                        .divide(denominator, work).negate();
                sum = sum.add(exponent.exp(work).multiply(exact.weight(index), work), work);
            }
            return sum;
        });
        // Exponentials truncated to decimal zero cannot carry a sign, so a
        // wholly underflowed sum arrives as +0.0 whatever its true sign. When
        // one sign's largest term provably dominates the other sign's total,
        // the correctly rounded zero is that sign's.
        if (value == 0) {
            final GaussianTerms terms = gaussianTerms(foci, x, y, sigma);
            if (Math.abs(terms.positiveLog() - terms.negativeLog())
                    > Math.log(foci.activeCount()) + 1) {
                return terms.positiveLog() > terms.negativeLog() ? 0.0 : -0.0;
            }
        }
        return value;
    }

    /** ln-domain bounds of the largest Gaussian term of each sign, which decide a wholly underflowed sum's zero. */
    private static GaussianTerms gaussianTerms(final FocusSet foci, final double x,
            final double y, final double sigma) {
        double positive = Double.NEGATIVE_INFINITY;
        double negative = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < foci.size(); index++) {
            if (!foci.isActive(index)) {
                continue;
            }
            final double weight = foci.weight(index);
            final double ratio = foci.distanceRatio(index, x, y, sigma);
            final double logTerm = Math.log(Math.abs(weight)) - 0.5 * ratio * ratio;
            if (Double.isNaN(logTerm)) {
                continue;
            }
            if (weight > 0) {
                positive = Math.max(positive, logTerm);
            } else {
                negative = Math.max(negative, logTerm);
            }
        }
        return new GaussianTerms(positive, negative);
    }

    private record GaussianTerms(double positiveLog, double negativeLog) {
    }

    private static Ball powerMeanEnclosure(final FocusSet foci, final double x,
            final double y, final double power, final MathContext context) {
        final BigDecimal powerDecimal = AdaptiveDecimal.exact(power);
        // exp(p * delta) may round to 1 at successive adaptive precisions.
        // The later division by p would amplify the lost correction. Reserve
        // ceil(log10(1/|p|)) digits BEFORE evaluating it, including subnormal p;
        // do not approximate a nonzero p by the geometric-mean limit.
        final int extraDigits = power == 0 ? 0
                : Math.max(0, powerDecimal.scale() - powerDecimal.precision() + 1);
        final MathContext work = AdaptiveDecimal.guard(amplifiedContext(context, extraDigits));
        final DecimalPoint point = point(x, y);
        final ExactFocusData exact = foci.exactData();
        final Ball[] values = magnitudeDistances(foci, exact, point, work);
        for (final Ball value : values) {
            if (value.isExactlyZero() && power <= 0) {
                return Ball.ZERO;
            }
        }
        if (power == 0) {
            Ball logSum = Ball.ZERO;
            for (final Ball value : values) {
                logSum = logSum.add(value.log(work), work);
            }
            return logSum.divide(BigDecimal.valueOf(values.length), work).exp(context);
        }

        final Ball[] logarithms = new Ball[values.length];
        Ball anchor = null;
        for (int index = 0; index < values.length; index++) {
            if (values[index].isExactlyZero()) {
                continue;
            }
            logarithms[index] = values[index].log(work);
            if (anchor == null) {
                anchor = logarithms[index];
            } else {
                final int comparison = logarithms[index].midpoint().compareTo(anchor.midpoint());
                if (power > 0 ? comparison > 0 : comparison < 0) {
                    anchor = logarithms[index];
                }
            }
        }
        if (anchor == null) {
            return Ball.ZERO;
        }
        Ball exponentialSum = Ball.ZERO;
        for (final Ball logarithm : logarithms) {
            if (logarithm == null) {
                continue;
            }
            final Ball exponent = logarithm.subtract(anchor, work).multiply(powerDecimal, work);
            exponentialSum = exponentialSum.add(exponent.exp(work), work);
        }
        final Ball mean = exponentialSum.divide(BigDecimal.valueOf(values.length), work);
        final Ball resultLogarithm = anchor.add(mean.log(work).divide(powerDecimal, work), work);
        return resultLogarithm.exp(context);
    }

    private static Ball[] magnitudeDistances(final FocusSet foci,
            final ExactFocusData exact, final DecimalPoint point, final MathContext work) {
        final Ball[] values = new Ball[foci.activeCount()];
        int target = 0;
        for (int index = 0; index < foci.size(); index++) {
            if (foci.isActive(index)) {
                values[target++] = magnitudeDistance(exact, index, point, work);
            }
        }
        return values;
    }

    private static Ball distance(final ExactFocusData exact, final int index,
            final DecimalPoint point, final MathContext work) {
        final Ball dx = Ball.exact(point.x().subtract(exact.x(index)));
        final Ball dy = Ball.exact(point.y().subtract(exact.y(index)));
        return dx.multiply(dx, work).add(dy.multiply(dy, work), work).sqrt(work);
    }

    private static Ball magnitudeDistance(final ExactFocusData exact, final int index,
            final DecimalPoint point, final MathContext work) {
        return distance(exact, index, point, work).multiply(exact.absoluteWeight(index), work);
    }

    private static DecimalPoint point(final double x, final double y) {
        return new DecimalPoint(AdaptiveDecimal.exact(x), AdaptiveDecimal.exact(y));
    }

    private record DecimalPoint(BigDecimal x, BigDecimal y) {
    }
}
