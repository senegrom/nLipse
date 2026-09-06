package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * A midpoint-radius enclosure of a real number: the true value lies within
 * {@code radius} of {@code midpoint}. Every operation propagates its operands'
 * radii and adds the rounding error of its own midpoint arithmetic, so a
 * computation carries its total error bound with it. The adaptive loop can
 * then accept a result exactly when the enclosure fits inside one binary64
 * rounding cell, without any per-family estimate of what may have cancelled.
 *
 * <p>Midpoints follow the caller's {@link MathContext}; radii are kept at a
 * few digits and always rounded away from zero. The transcendental operations
 * rely on the error contract of {@link DecimalMath}: with its 24 guard digits,
 * {@code exp} is within one unit of the requested precision (relative) and
 * {@code log} within half a unit plus an absolute {@code 10^-(precision+13)}
 * from the constants and series. An enclosure that cannot be bounded (division
 * by a ball containing zero, a logarithm of one reaching zero, an exponent
 * ball wider than one) is {@link #UNBOUNDED} and is never accepted.
 */
final class Ball {
    /** Radius arithmetic: six digits, always rounded away from zero. */
    private static final MathContext RADIUS = new MathContext(6, RoundingMode.UP);
    private static final MathContext RADIUS_NEAREST = new MathContext(6, RoundingMode.HALF_UP);
    /** Covers the nearest rounding of a six-digit radius operation that cannot round up itself. */
    private static final BigDecimal MARGIN = new BigDecimal("1.01");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    /** {@code DecimalMath.exp} returns twice this once binary64 overflow is certain. */
    private static final BigDecimal OVERFLOW = new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.TWO);
    /** Extra decimal digits below {@code exp}'s zero truncation that its true value provably stays under. */
    private static final int EXP_TRUNCATION_DIGITS = 671;
    /** Absolute error of {@code DecimalMath.log} beyond its final rounding, as digits below the precision. */
    private static final int LOG_INTERNAL_DIGITS = 13;

    static final Ball ZERO = new Ball(BigDecimal.ZERO, BigDecimal.ZERO, false);
    /** An enclosure of unknown extent; every operation on it stays unbounded. */
    static final Ball UNBOUNDED = new Ball(BigDecimal.ZERO, null, false);

    private final BigDecimal midpoint;
    /** Absolute error bound, or {@code null} when unbounded. */
    private final BigDecimal radius;
    /** An exponential beyond the binary64 range: valid as a final value, unusable as an operand. */
    private final boolean overflow;

    private Ball(final BigDecimal midpoint, final BigDecimal radius, final boolean overflow) {
        this.midpoint = midpoint;
        this.radius = radius;
        this.overflow = overflow;
    }

    static Ball exact(final BigDecimal value) {
        return new Ball(value, BigDecimal.ZERO, false);
    }

    /** An enclosure of the given non-negative radius around {@code midpoint}. */
    static Ball of(final BigDecimal midpoint, final BigDecimal radius) {
        if (radius.signum() < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        return new Ball(midpoint, radius, false);
    }

    static Ball exact(final double value) {
        return exact(AdaptiveDecimal.exact(value));
    }

    BigDecimal midpoint() {
        return midpoint;
    }

    /** The absolute error bound; only meaningful when {@link #isBounded()}. */
    BigDecimal radius() {
        return radius;
    }

    boolean isBounded() {
        return radius != null;
    }

    /** Whether this is a final exponential beyond the binary64 range. */
    boolean isOverflow() {
        return overflow;
    }

    /** Whether the enclosure is the single point zero. */
    boolean isExactlyZero() {
        return isBounded() && midpoint.signum() == 0 && radius.signum() == 0;
    }

    /** Whether every enclosed value is strictly positive. */
    boolean isPositive() {
        return isBounded() && !overflow && midpoint.signum() > 0 && midpoint.compareTo(radius) > 0;
    }

    private boolean usable() {
        return isBounded() && !overflow;
    }

    private BigDecimal lower() {
        return midpoint.subtract(radius);
    }

    private BigDecimal upper() {
        return midpoint.add(radius);
    }

    Ball negate() {
        return usable() ? new Ball(midpoint.negate(), radius, false) : UNBOUNDED;
    }

    /** {@code |x|} lies within the same radius of {@code |midpoint|}. */
    Ball abs() {
        return usable() ? new Ball(midpoint.abs(), radius, false) : UNBOUNDED;
    }

    Ball add(final Ball other, final MathContext context) {
        if (!usable() || !other.usable()) {
            return UNBOUNDED;
        }
        final BigDecimal sum = midpoint.add(other.midpoint, context);
        return new Ball(sum, radius.add(other.radius, RADIUS).add(halfUlp(sum, context), RADIUS), false);
    }

    Ball subtract(final Ball other, final MathContext context) {
        return add(other.negate(), context);
    }

    Ball multiply(final Ball other, final MathContext context) {
        if (!usable() || !other.usable()) {
            return UNBOUNDED;
        }
        final BigDecimal product = midpoint.multiply(other.midpoint, context);
        // (m1 + d1)(m2 + d2) - m1 m2 = m1 d2 + m2 d1 + d1 d2
        final BigDecimal propagated = midpoint.abs().multiply(other.radius, RADIUS)
                .add(other.midpoint.abs().multiply(radius, RADIUS), RADIUS)
                .add(radius.multiply(other.radius, RADIUS), RADIUS);
        return new Ball(product, propagated.add(halfUlp(product, context), RADIUS), false);
    }

    /** Multiplication by an exactly known constant. */
    Ball multiply(final BigDecimal factor, final MathContext context) {
        if (!usable()) {
            return UNBOUNDED;
        }
        final BigDecimal product = midpoint.multiply(factor, context);
        return new Ball(product,
                factor.abs().multiply(radius, RADIUS).add(halfUlp(product, context), RADIUS), false);
    }

    Ball divide(final Ball other, final MathContext context) {
        if (!usable() || !other.usable()) {
            return UNBOUNDED;
        }
        final BigDecimal divisorMagnitude = other.midpoint.abs();
        final BigDecimal clearance = divisorMagnitude.subtract(other.radius);
        if (clearance.signum() <= 0) {
            return UNBOUNDED; // the divisor may be zero
        }
        final BigDecimal quotient = midpoint.divide(other.midpoint, context);
        // |(m1 + d1)/(m2 + d2) - m1/m2| <= (|m2| r1 + |m1| r2) / (|m2| (|m2| - r2))
        final BigDecimal numerator = divisorMagnitude.multiply(radius, RADIUS)
                .add(midpoint.abs().multiply(other.radius, RADIUS), RADIUS);
        final BigDecimal propagated = numerator.divide(
                divisorMagnitude.multiply(clearance, RADIUS_NEAREST), RADIUS).multiply(MARGIN, RADIUS);
        return new Ball(quotient, propagated.add(halfUlp(quotient, context), RADIUS), false);
    }

    /** Division by an exactly known non-zero constant. */
    Ball divide(final BigDecimal divisor, final MathContext context) {
        if (!usable()) {
            return UNBOUNDED;
        }
        final BigDecimal quotient = midpoint.divide(divisor, context);
        return new Ball(quotient,
                radius.divide(divisor.abs(), RADIUS).add(halfUlp(quotient, context), RADIUS), false);
    }

    /** The square root of a non-negative quantity; an enclosure reaching below zero is clipped at zero. */
    Ball sqrt(final MathContext context) {
        if (!usable()) {
            return UNBOUNDED;
        }
        if (midpoint.signum() <= 0) {
            // The true value lies in [0, radius], so its root lies in [0, sqrt(radius)].
            final BigDecimal bound = radius.add(midpoint.abs(), RADIUS);
            return new Ball(BigDecimal.ZERO, bound.sqrt(RADIUS_NEAREST).multiply(MARGIN, RADIUS), false);
        }
        final BigDecimal root = midpoint.sqrt(context);
        if (radius.signum() == 0) {
            return new Ball(root, ulp(root, context), false);
        }
        // |sqrt(x) - sqrt(m)| <= |x - m| / sqrt(m) and also <= sqrt(|x - m|).
        final BigDecimal viaSlope = radius.divide(root, RADIUS).multiply(MARGIN, RADIUS);
        final BigDecimal viaRoot = radius.sqrt(RADIUS_NEAREST).multiply(MARGIN, RADIUS);
        return new Ball(root, viaSlope.min(viaRoot).add(ulp(root, context), RADIUS), false);
    }

    /**
     * The exponential. An exponent enclosure wider than one is unbounded; a
     * result truncated to zero by {@code DecimalMath.exp} keeps the bound its
     * truncation guarantees; a result beyond the binary64 range is an
     * {@link #isOverflow() overflow} enclosure, valid only as a final value.
     */
    Ball exp(final MathContext context) {
        if (!usable() || radius.compareTo(BigDecimal.ONE) > 0) {
            return UNBOUNDED;
        }
        final BigDecimal value = DecimalMath.exp(midpoint, context);
        if (value.compareTo(OVERFLOW) >= 0) {
            return new Ball(value, BigDecimal.ZERO, true);
        }
        if (value.signum() == 0) {
            return new Ball(BigDecimal.ZERO,
                    BigDecimal.ONE.scaleByPowerOfTen(-context.getPrecision() - EXP_TRUNCATION_DIGITS), false);
        }
        // e^r - 1 <= r (1 + r) for r <= 1 bounds the growth over the exponent's radius.
        final BigDecimal growth = radius.multiply(BigDecimal.ONE.add(radius, RADIUS), RADIUS);
        return new Ball(value, value.abs().multiply(growth, RADIUS).add(ulp(value, context), RADIUS), false);
    }

    /** The natural logarithm; an enclosure reaching zero or below is unbounded. */
    Ball log(final MathContext context) {
        if (!isPositive()) {
            return UNBOUNDED;
        }
        final BigDecimal value = DecimalMath.log(midpoint, context);
        // |log x - log m| <= |x - m| / min(x, m) <= r / (m - r)
        final BigDecimal propagated = radius.divide(midpoint.subtract(radius), RADIUS).multiply(MARGIN, RADIUS);
        final BigDecimal internal = BigDecimal.ONE.scaleByPowerOfTen(-context.getPrecision() - LOG_INTERNAL_DIGITS);
        return new Ball(value,
                propagated.add(halfUlp(value, context), RADIUS).add(internal, RADIUS), false);
    }

    /**
     * The enclosure of the {@code rank}-th smallest (0-based) of the true values:
     * order statistics are monotone in every input, so it lies between the
     * {@code rank}-th smallest lower and upper bounds. The midpoint is the
     * {@code rank}-th smallest midpoint.
     */
    static Ball orderStatistic(final Ball[] values, final int rank) {
        final BigDecimal[] lowers = new BigDecimal[values.length];
        final BigDecimal[] uppers = new BigDecimal[values.length];
        final BigDecimal[] midpoints = new BigDecimal[values.length];
        for (int index = 0; index < values.length; index++) {
            if (!values[index].usable()) {
                return UNBOUNDED;
            }
            lowers[index] = values[index].lower();
            uppers[index] = values[index].upper();
            midpoints[index] = values[index].midpoint;
        }
        Arrays.sort(lowers);
        Arrays.sort(uppers);
        Arrays.sort(midpoints);
        final BigDecimal midpoint = midpoints[rank];
        final BigDecimal radius = midpoint.subtract(lowers[rank]).max(uppers[rank].subtract(midpoint))
                .round(RADIUS).max(BigDecimal.ZERO);
        return new Ball(midpoint, radius, false);
    }

    /** Half a unit in the last place of {@code rounded} at {@code context}'s precision: the error of one rounding. */
    private static BigDecimal halfUlp(final BigDecimal rounded, final MathContext context) {
        return ulp(rounded, context).multiply(HALF);
    }

    private static BigDecimal ulp(final BigDecimal rounded, final MathContext context) {
        if (rounded.signum() == 0) {
            return BigDecimal.ZERO; // a rounded zero was an exact zero
        }
        final int exponent = rounded.precision() - rounded.scale() - 1;
        return BigDecimal.ONE.scaleByPowerOfTen(exponent - context.getPrecision() + 1);
    }

    @Override
    public String toString() {
        return isBounded() ? midpoint + " +- " + radius + (overflow ? " (overflow)" : "") : "unbounded";
    }
}
