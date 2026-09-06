package nlipse.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.CancellationException;

/**
 * Adaptive rare-path decimal evaluation: the precision doubles until the
 * computed {@link Ball} enclosure lies strictly inside one binary64 rounding
 * cell, and that cell's double is the result.
 */
final class AdaptiveDecimal {
    // Starting near quad precision keeps the common near-cancellation case
    // cheap; the doubling loop still escalates to full binary64 dynamic range
    // when a residual hides far below the largest intermediate.
    static final int INITIAL_PRECISION = 34;
    static final int MAXIMUM_PRECISION = 4096;
    private static final int GUARD_DIGITS = 24;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private AdaptiveDecimal() {
    }

    @FunctionalInterface
    interface Enclosure {
        Ball compute(MathContext context);
    }

    /**
     * Evaluates at doubling precision until the enclosure is provably inside one
     * binary64 rounding cell. The carried radius is a bound, not an estimate, so
     * the first precision that resolves the value is accepted; nothing about the
     * computation's cancellation needs to be known in advance. At the maximum
     * precision an enclosure still crossing a cell boundary is taken to be that
     * boundary, which a tie rounds to even.
     */
    static double toDouble(final Enclosure computation) {
        if (computation == null) {
            throw new IllegalArgumentException("Adaptive computation is required");
        }
        int precision = INITIAL_PRECISION;
        while (true) {
            checkCancelled();
            final MathContext context = new MathContext(precision, RoundingMode.HALF_EVEN);
            final Ball enclosure = computation.compute(context);
            if (enclosure == null) {
                throw new IllegalStateException("Adaptive computation returned null");
            }
            final double rounded = enclosure.midpoint().doubleValue();
            if (enclosure.isBounded()
                    && insideRoundingCell(enclosure.midpoint(), rounded, enclosure.radius())) {
                return rounded;
            }
            if (precision >= MAXIMUM_PRECISION) {
                return enclosure.isBounded()
                        ? nearestBoundaryOrRounded(enclosure.midpoint(), rounded, enclosure.radius())
                        : rounded;
            }
            precision = Math.min(MAXIMUM_PRECISION, precision * 2);
        }
    }

    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Field evaluation cancelled");
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

    private static boolean insideRoundingCell(final BigDecimal value, final double rounded,
            final BigDecimal error) {
        if (Double.isNaN(rounded)) {
            return false;
        }
        if (rounded == Double.POSITIVE_INFINITY) {
            return value.subtract(positiveOverflowBoundary()).compareTo(error) > 0;
        }
        if (rounded == Double.NEGATIVE_INFINITY) {
            return positiveOverflowBoundary().negate().subtract(value).compareTo(error) > 0;
        }
        return value.subtract(lowerBoundary(rounded)).compareTo(error) > 0
                && upperBoundary(rounded).subtract(value).compareTo(error) > 0;
    }

    /**
     * The fallback once precision is exhausted: an enclosure this narrow that
     * still reaches a cell boundary is, for every practical purpose, the
     * boundary itself (an exactly representable tie such as 2^-1075), so the
     * boundary's own correctly rounded double is returned.
     */
    private static double nearestBoundaryOrRounded(final BigDecimal value, final double rounded,
            final BigDecimal error) {
        if (Double.isNaN(rounded)) {
            return rounded;
        }
        final BigDecimal lower;
        final BigDecimal upper;
        if (rounded == Double.POSITIVE_INFINITY) {
            lower = positiveOverflowBoundary();
            upper = null;
        } else if (rounded == Double.NEGATIVE_INFINITY) {
            lower = null;
            upper = positiveOverflowBoundary().negate();
        } else {
            lower = lowerBoundary(rounded);
            upper = upperBoundary(rounded);
        }
        if (lower != null && value.subtract(lower).abs().compareTo(error) <= 0) {
            return lower.doubleValue();
        }
        if (upper != null && upper.subtract(value).abs().compareTo(error) <= 0) {
            return upper.doubleValue();
        }
        return rounded;
    }

    private static BigDecimal lowerBoundary(final double rounded) {
        final BigDecimal centre = exact(rounded);
        if (rounded == -Double.MAX_VALUE) {
            final BigDecimal spacing = exact(Math.nextUp(rounded)).subtract(centre);
            return centre.subtract(spacing.divide(TWO));
        }
        return midpoint(exact(Math.nextDown(rounded)), centre);
    }

    private static BigDecimal upperBoundary(final double rounded) {
        final BigDecimal centre = exact(rounded);
        if (rounded == Double.MAX_VALUE) {
            final BigDecimal spacing = centre.subtract(exact(Math.nextDown(rounded)));
            return centre.add(spacing.divide(TWO));
        }
        return midpoint(centre, exact(Math.nextUp(rounded)));
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
