package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /** Largest ratio z/τ at which the smooth envelope is taken from its mean and variance. */
    private static final BigDecimal TINY_ENVELOPE_RATIO = new BigDecimal(0x1p-60);
    private static final BigDecimal THREE_HALVES = new BigDecimal("1.5");
    /** An upper bound of a ratio, to six digits. */
    private static final MathContext RATIO_BOUND = new MathContext(6, RoundingMode.UP);

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
        final List<DistanceGroup> groups = distanceGroups(foci, point(x, y));
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            Ball sum = Ball.ZERO;
            for (final DistanceGroup group : groups) {
                sum = sum.add(group.distance(work).multiply(group.weight(), work), work);
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
        // Foci with exactly equal weighted distances w·d add exactly nothing to one
        // another, so each group of equal values enters once, weighted by how many
        // pairs it stands for, and all-equal values are an exact zero at once.
        final DecimalPoint point = point(x, y);
        final ExactFocusData exact = foci.exactData();
        final Map<ValueKey, ValueGroup> byValue = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            final BigDecimal squared = squaredDistance(exact, index, point);
            final BigDecimal weight = exact.weight(index);
            final ValueKey key = new ValueKey(weight.signum() * squared.signum(),
                    weight.multiply(weight).multiply(squared).stripTrailingZeros());
            byValue.merge(key, new ValueGroup(squared, weight, 1),
                    (first, second) -> new ValueGroup(first.squaredDistance(), first.weight(),
                            first.count() + 1));
        }
        final List<ValueGroup> groups = new ArrayList<>(byValue.values());
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball[] values = new Ball[groups.size()];
            for (int group = 0; group < values.length; group++) {
                values[group] = Ball.exact(groups.get(group).squaredDistance()).sqrt(work)
                        .multiply(groups.get(group).weight(), work);
            }
            Ball sum = Ball.ZERO;
            for (int group = 0; group < values.length; group++) {
                for (int previous = 0; previous < group; previous++) {
                    final long pairs = (long) groups.get(group).count() * groups.get(previous).count();
                    sum = sum.add(values[group].subtract(values[previous], work).abs()
                            .multiply(BigDecimal.valueOf(pairs), work), work);
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
        // Exactly equal magnitude distances (equal w²d²) are an exact zero, which
        // two separately enclosed order statistics would leave as 0 ± 2r.
        if (magnitudeDistancesAllEqual(foci, point(x, y))) {
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
        final List<DistanceGroup> groups = distanceGroups(foci, point(x, y));
        return AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            Ball sum = Ball.ZERO;
            for (final DistanceGroup group : groups) {
                sum = sum.add(Ball.exact(group.weight()).divide(group.distance(work), work), work);
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

    /**
     * Reserves digits lost through multiplication by a large weight or division
     * by a tiny p. Like the guard, the reservation comes on top of the rung:
     * capping it at the last rung would drop exactly the digits it reserves.
     */
    private static MathContext amplifiedContext(final MathContext context, final int extraDigits) {
        return new MathContext(context.getPrecision() + extraDigits, context.getRoundingMode());
    }

    /**
     * The power mean for a finite power. The fields route p = ±∞, 1 and 2 to
     * their own evaluators, so only the harmonic (-1), geometric (0) and
     * general powers reach this one.
     */
    static double powerMean(final FocusSet foci, final double x, final double y,
            final double power) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        if (!Double.isFinite(power)) {
            throw new IllegalArgumentException("The exact power mean needs a finite power");
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
            final Ball farBelowTemperature = tinyRatioEnvelope(values, tau, nearest, work);
            if (farBelowTemperature != null) {
                return farBelowTemperature;
            }
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

    /**
     * The smooth envelope far below the temperature, from the mean and variance
     * of the magnitude distances z alone. Near 1 the logarithm of the general
     * route loses digits so fast that there it climbed to 544-digit rungs, at
     * 9-22 ms a point.
     *
     * <p>With u = z/τ, every |u - ū| is at most ρ ≥ max z/τ, and for ρ ≤ 1
     * the log-moment ln E[exp(±(u - ū))] lies within [1 - ρ, 1 + 2ρ] times
     * Var(u)/2 (from e^v ≥ 1 + v + v²/2 + v³/6 and e^v - 1 - v ≤ (v²/2)e^|v|).
     * So the envelope is mean(z) ± (1 + ε)·Var(z)/(2τ) with -ρ ≤ ε ≤ 2ρ: an
     * enclosure a relative ρ² wide at ρ ≤ 2^-60, and strictly off the mean
     * whenever the distances differ, which rounds a tie of the mean the way
     * the envelope leans.
     *
     * @return the enclosure, or {@code null} when a ratio may exceed 2^-60
     */
    private static Ball tinyRatioEnvelope(final Ball[] values, final BigDecimal tau,
            final boolean nearest, final MathContext work) {
        BigDecimal largest = BigDecimal.ZERO;
        for (final Ball value : values) {
            if (!value.isBounded()) {
                return null;
            }
            largest = largest.max(value.midpoint().abs().add(value.radius()));
        }
        final BigDecimal ratio = largest.divide(tau, RATIO_BOUND);
        if (ratio.compareTo(TINY_ENVELOPE_RATIO) > 0) {
            return null;
        }
        final BigDecimal count = BigDecimal.valueOf(values.length);
        Ball sum = Ball.ZERO;
        for (final Ball value : values) {
            sum = sum.add(value, work);
        }
        final Ball mean = sum.divide(count, work);
        Ball squares = Ball.ZERO;
        for (final Ball value : values) {
            final Ball deviation = value.subtract(mean, work);
            squares = squares.add(deviation.multiply(deviation, work), work);
        }
        final Ball halfVarianceOverTau = squares.divide(count.multiply(TWO), work).divide(tau, work);
        // 1 + ε for ε in [-ρ, 2ρ]: midpoint 1 + ρ/2, radius 3ρ/2
        final Ball factor = Ball.of(BigDecimal.ONE.add(ratio.divide(TWO)), ratio.multiply(THREE_HALVES));
        final Ball correction = halfVarianceOverTau.multiply(factor, work);
        return nearest ? mean.subtract(correction, work) : mean.add(correction, work);
    }

    static double gaussian(final FocusSet foci, final double x, final double y,
            final double sigma) {
        if (foci.activeCount() == 0) {
            return 0;
        }
        final List<DistanceGroup> groups = distanceGroups(foci, point(x, y));
        final double value = AdaptiveDecimal.toDouble(context -> {
            final MathContext work = AdaptiveDecimal.guard(context);
            final Ball sigmaDecimal = Ball.exact(sigma);
            final Ball denominator = sigmaDecimal.multiply(sigmaDecimal, work).multiply(TWO, work);
            Ball sum = Ball.ZERO;
            for (final DistanceGroup group : groups) {
                final Ball exponent = Ball.exact(group.squaredDistance())
                        .divide(denominator, work).negate();
                sum = sum.add(exponent.exp(work).multiply(group.weight(), work), work);
            }
            return sum;
        });
        // Exponentials truncated to decimal zero cannot carry a sign, so a
        // wholly underflowed sum arrives as +0.0 whatever its true sign. When
        // one sign's largest term provably dominates the other sign's total,
        // the correctly rounded zero is that sign's.
        if (value == 0) {
            final int sign = underflowedGaussianSign(groups, sigma);
            if (sign != 0) {
                return sign > 0 ? 0.0 : -0.0;
            }
        }
        return value;
    }

    /**
     * The sign of a Gaussian sum too small to represent: +1 or -1 when only one
     * sign occurs, or when one sign's largest term exceeds the other sign's
     * total ({@code ln n + 1} ln-units of margin); 0 when neither is provable.
     * With {@code t = ln|w| - d²/(2σ²)} per term it compares {@code 2σ²·t =
     * 2σ²·ln|w| - d²} in exact decimal arithmetic: the primitive
     * {@code -½(d/σ)²} errs by about {@code (d/σ)²·2^-52}, which beyond
     * d/σ ≈ 1e8 exceeds the margin itself, and overflows past d/σ ≈ 1.3e154.
     * The terms are the merged distance groups, so a cancelled pair cannot
     * decide the sign of what remains.
     */
    private static int underflowedGaussianSign(final List<DistanceGroup> groups,
            final double sigma) {
        final BigDecimal twoSigmaSquared = AdaptiveDecimal.exact(sigma).pow(2).multiply(TWO);
        BigDecimal positive = null;
        BigDecimal negative = null;
        for (final DistanceGroup group : groups) {
            final BigDecimal key = twoSigmaSquared
                    .multiply(AdaptiveDecimal.exact(logMagnitude(group.weight())))
                    .subtract(group.squaredDistance());
            if (group.weight().signum() > 0) {
                positive = positive == null ? key : positive.max(key);
            } else {
                negative = negative == null ? key : negative.max(key);
            }
        }
        if (positive == null || negative == null) {
            return positive != null ? 1 : negative != null ? -1 : 0;
        }
        // ln|w| is a rounded double (within 2e-13 for any finite weight), hence the slack.
        final BigDecimal margin = twoSigmaSquared.multiply(
                AdaptiveDecimal.exact(Math.log(groups.size()) + 1 + 1e-12));
        final BigDecimal difference = positive.subtract(negative);
        if (difference.compareTo(margin) > 0) {
            return 1;
        }
        return difference.negate().compareTo(margin) > 0 ? -1 : 0;
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

    /** The squared distance of a focus from the point, exactly. */
    private static BigDecimal squaredDistance(final ExactFocusData exact, final int index,
            final DecimalPoint point) {
        final BigDecimal dx = point.x().subtract(exact.x(index));
        final BigDecimal dy = point.y().subtract(exact.y(index));
        return dx.multiply(dx).add(dy.multiply(dy));
    }

    /**
     * The active foci of a weight-linear sum, grouped by their exact squared
     * distance from the point with each group's weights added exactly; groups
     * whose weights cancel are left out. Each distance then enters the sum once,
     * so an exact cancellation (coincident foci of opposite weight, or a point
     * on the mirror line of an antisymmetric pair) is exactly absent. Enclosed
     * term by term it left 0 ± 2r, which the adaptive loop resolves only once
     * the radius is below 2^-1075, at 544 or 1088 digits: milliseconds a point.
     */
    private static List<DistanceGroup> distanceGroups(final FocusSet foci,
            final DecimalPoint point) {
        final ExactFocusData exact = foci.exactData();
        final Map<BigDecimal, DistanceGroup> byDistance = new LinkedHashMap<>();
        for (int index = 0; index < foci.size(); index++) {
            if (!foci.isActive(index)) {
                continue;
            }
            final BigDecimal squared = squaredDistance(exact, index, point);
            // Keyed without trailing zeros, so that equal values are equal keys
            byDistance.merge(squared.stripTrailingZeros(),
                    new DistanceGroup(squared, exact.weight(index)),
                    (first, second) -> new DistanceGroup(first.squaredDistance(),
                            first.weight().add(second.weight())));
        }
        final List<DistanceGroup> groups = new ArrayList<>(byDistance.size());
        for (final DistanceGroup group : byDistance.values()) {
            if (group.weight().signum() != 0) {
                groups.add(group);
            }
        }
        return groups;
    }

    /** Whether every active focus has exactly the same magnitude distance |w|·d. */
    private static boolean magnitudeDistancesAllEqual(final FocusSet foci,
            final DecimalPoint point) {
        final ExactFocusData exact = foci.exactData();
        BigDecimal first = null;
        for (int index = 0; index < foci.size(); index++) {
            if (!foci.isActive(index)) {
                continue;
            }
            final BigDecimal weight = exact.weight(index);
            final BigDecimal square = weight.multiply(weight)
                    .multiply(squaredDistance(exact, index, point));
            if (first == null) {
                first = square;
            } else if (square.compareTo(first) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * ln|w| of a nonzero exact weight as a double, within about 2e-13 even when a
     * merged weight lies beyond the double range. A weight that is one double
     * gets exactly {@code Math.log(Math.abs(w))}.
     */
    private static double logMagnitude(final BigDecimal weight) {
        final double magnitude = Math.abs(weight.doubleValue());
        if (Double.isFinite(magnitude)) {
            return Math.log(magnitude);
        }
        final int exponent = weight.precision() - weight.scale() - 1;
        return Math.log(weight.abs().movePointLeft(exponent).doubleValue()) + exponent * LN_TEN;
    }

    private record DecimalPoint(BigDecimal x, BigDecimal y) {
    }

    /** Foci at one exact squared distance, with their weights summed exactly. */
    private record DistanceGroup(BigDecimal squaredDistance, BigDecimal weight) {
        Ball distance(final MathContext work) {
            return Ball.exact(squaredDistance).sqrt(work);
        }
    }

    /** A weighted distance w·d, exactly: its sign and its square w²d² without trailing zeros. */
    private record ValueKey(int sign, BigDecimal square) {
    }

    /** Foci with one exact weighted distance; the first stands for all of them. */
    private record ValueGroup(BigDecimal squaredDistance, BigDecimal weight, int count) {
    }
}
