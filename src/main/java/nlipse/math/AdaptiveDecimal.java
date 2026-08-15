package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Adaptive rare-path decimal evaluation with an explicit binary64 rounding guard. */
final class AdaptiveDecimal {
    // Every evaluation needs at least two rounds before the guard can accept a
    // result, so the first precision is charged twice. Starting near quad
    // precision keeps the common near-cancellation case cheap; the doubling
    // loop still escalates to full binary64 dynamic range when a residual
    // hides far below the largest intermediate.
    static final int INITIAL_PRECISION = 34;
    static final int MAXIMUM_PRECISION = 4096;
    private static final int GUARD_DIGITS = 24;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal EIGHT = BigDecimal.valueOf(8);

    private AdaptiveDecimal() {
    }

    @FunctionalInterface
    interface Computation {
        BigDecimal compute(MathContext context);
    }

    static double toDouble(final Computation computation) {
        return toDouble(Integer.MIN_VALUE, computation);
    }

    /**
     * Evaluates until the result is provably inside one binary64 rounding cell.
     *
     * @param cancellationScaleExponent decimal exponent of the largest intermediate that may
     *        cancel out, or {@link Integer#MIN_VALUE} when ordinary relative error is enough
     */
    static double toDouble(final int cancellationScaleExponent,
            final Computation computation) {
        if (computation == null) {
            throw new IllegalArgumentException("Adaptive computation is required");
        }
        BigDecimal previous = null;
        double previousRounded = Double.NaN;
        BigDecimal current = null;
        int precision = INITIAL_PRECISION;
        while (true) {
            final MathContext context = new MathContext(precision, RoundingMode.HALF_EVEN);
            current = computation.compute(context);
            if (current == null) {
                throw new IllegalStateException("Adaptive computation returned null");
            }
            final double rounded = current.doubleValue();
            if (previous != null
                    && Double.doubleToLongBits(rounded)
                            == Double.doubleToLongBits(previousRounded)) {
                final BigDecimal observedChange = current.subtract(previous).abs();
                final BigDecimal error = observedChange.multiply(FOUR)
                        .max(decimalResolution(current, precision, cancellationScaleExponent)
                                .multiply(EIGHT));
                if (insideRoundingCell(current, rounded, error)) {
                    return rounded;
                }
            }
            if (precision >= MAXIMUM_PRECISION) {
                return rounded;
            }
            previous = current;
            previousRounded = rounded;
            precision = Math.min(MAXIMUM_PRECISION, precision * 2);
        }
    }

    static MathContext guard(final MathContext context) {
        return new MathContext(Math.min(MAXIMUM_PRECISION,
                context.getPrecision() + GUARD_DIGITS), context.getRoundingMode());
    }

    static BigDecimal exact(final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Only finite doubles have an exact decimal form");
        }
        return new BigDecimal(value);
    }

    private static BigDecimal decimalResolution(final BigDecimal value, final int precision,
            final int cancellationScaleExponent) {
        final int valueExponent = value.signum() == 0
                ? Integer.MIN_VALUE : value.precision() - value.scale() - 1;
        final int adjustedExponent = Math.max(valueExponent, cancellationScaleExponent);
        if (adjustedExponent == Integer.MIN_VALUE) {
            return BigDecimal.ONE.scaleByPowerOfTen(-precision);
        }
        return BigDecimal.ONE.scaleByPowerOfTen(adjustedExponent - precision + 2).abs();
    }

    private static boolean insideRoundingCell(final BigDecimal value, final double rounded,
            final BigDecimal error) {
        if (Double.isNaN(rounded)) {
            return false;
        }
        if (rounded == Double.POSITIVE_INFINITY) {
            final BigDecimal boundary = positiveOverflowBoundary();
            return value.subtract(boundary).compareTo(error) > 0;
        }
        if (rounded == Double.NEGATIVE_INFINITY) {
            final BigDecimal boundary = positiveOverflowBoundary().negate();
            return boundary.subtract(value).compareTo(error) > 0;
        }

        final BigDecimal centre = exact(rounded);
        final BigDecimal lowerBoundary;
        if (rounded == -Double.MAX_VALUE) {
            final BigDecimal spacing = exact(Math.nextUp(rounded)).subtract(centre);
            lowerBoundary = centre.subtract(spacing.divide(TWO));
        } else {
            lowerBoundary = midpoint(exact(Math.nextDown(rounded)), centre);
        }
        final BigDecimal upperBoundary;
        if (rounded == Double.MAX_VALUE) {
            final BigDecimal spacing = centre.subtract(exact(Math.nextDown(rounded)));
            upperBoundary = centre.add(spacing.divide(TWO));
        } else {
            upperBoundary = midpoint(centre, exact(Math.nextUp(rounded)));
        }
        return value.subtract(lowerBoundary).compareTo(error) > 0
                && upperBoundary.subtract(value).compareTo(error) > 0;
    }

    private static BigDecimal positiveOverflowBoundary() {
        final BigDecimal maximum = exact(Double.MAX_VALUE);
        final BigDecimal spacing = maximum.subtract(exact(Math.nextDown(Double.MAX_VALUE)));
        return maximum.add(spacing.divide(TWO));
    }

    private static BigDecimal midpoint(final BigDecimal first, final BigDecimal second) {
        return first.add(second).divide(TWO);
    }

}
