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
    /** MAX_VALUE plus half its spacing: everything beyond rounds to infinity. */
    private static final BigDecimal OVERFLOW_BOUNDARY = exact(Double.MAX_VALUE).add(
            exact(Double.MAX_VALUE).subtract(exact(Math.nextDown(Double.MAX_VALUE))).divide(TWO));

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
     * boundary, which a tie rounds to even. A result that rounds to zero is
     * decided by value: its sign is the midpoint's.
     */
    static double toDouble(final Enclosure computation) {
        return evaluate(computation, false);
    }

    /**
     * As {@link #toDouble}, for a sum that may be negative: a result that rounds
     * to zero also gets the sign of the value it rounds, -0.0 for a negative one.
     * An enclosure that reaches both zero and negative values keeps climbing; at
     * the maximum precision it is taken to be zero itself, which is +0.0.
     */
    static double toSignedDouble(final Enclosure computation) {
        return evaluate(computation, true);
    }

    private static double evaluate(final Enclosure computation, final boolean signedZero) {
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
            final boolean last = precision >= MAXIMUM_PRECISION;
            if (decides(enclosure)) {
                if (!signedZero || rounded != 0) {
                    return rounded;
                }
                final int sign = zeroSign(enclosure);
                if (sign != 0 || last) {
                    return sign < 0 ? -0.0 : 0.0;
                }
            } else if (last) {
                return enclosure.isBounded()
                        ? nearestBoundaryOrRounded(enclosure.midpoint(), rounded, enclosure.radius())
                        : rounded;
            }
            precision = Math.min(MAXIMUM_PRECISION, precision * 2);
        }
    }

    /**
     * Whether the enclosure already decides its double, as {@link #toDouble}
     * would accept it at this rung. A shortcut enclosure whose error does not
     * shrink with precision uses this to step aside when it cannot decide.
     */
    static boolean decides(final Ball enclosure) {
        return enclosure.isBounded() && cell(enclosure.midpoint().doubleValue())
                .contains(enclosure.midpoint(), enclosure.radius());
    }

    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Field evaluation cancelled");
        }
    }

    /**
     * The working precision behind a rung: always {@link #GUARD_DIGITS} above it.
     * {@link #MAXIMUM_PRECISION} caps the ladder's rungs, never the guard: at the
     * last rung a capped guard left {@code DecimalMath} without guard digits,
     * and {@link Ball}'s log/exp enclosures (which assume them) missed the true
     * value by up to thousands of ulps.
     */
    static MathContext guard(final MathContext context) {
        return new MathContext(context.getPrecision() + GUARD_DIGITS, context.getRoundingMode());
    }

    static BigDecimal exact(final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Only finite doubles have an exact decimal form");
        }
        return new BigDecimal(value);
    }

    /**
     * The sign of the zero that an enclosure inside the zero cell rounds to: +1
     * when no enclosed value is negative (an exact zero is +0.0 too), -1 when
     * every enclosed value is, 0 while it still reaches both.
     */
    private static int zeroSign(final Ball enclosure) {
        if (enclosure.midpoint().subtract(enclosure.radius()).signum() >= 0) {
            return 1;
        }
        return enclosure.midpoint().add(enclosure.radius()).signum() < 0 ? -1 : 0;
    }

    /**
     * The fallback once precision is exhausted: an enclosure this narrow that
     * still reaches a cell boundary is, for every practical purpose, the
     * boundary itself (an exactly representable tie such as 2^-1075), so the
     * boundary's own correctly rounded double is returned.
     */
    private static double nearestBoundaryOrRounded(final BigDecimal value, final double rounded,
            final BigDecimal error) {
        final Cell cell = cell(rounded);
        if (cell.lower() != null && value.subtract(cell.lower()).abs().compareTo(error) <= 0) {
            return cell.lower().doubleValue();
        }
        if (cell.upper() != null && cell.upper().subtract(value).abs().compareTo(error) <= 0) {
            return cell.upper().doubleValue();
        }
        return rounded;
    }

    /** The values that round to one double: between two boundaries, a null one unbounded. */
    private record Cell(BigDecimal lower, BigDecimal upper) {
        /** Whether every value within {@code error} of {@code value} lies strictly inside. */
        boolean contains(final BigDecimal value, final BigDecimal error) {
            return (lower == null || value.subtract(lower).compareTo(error) > 0)
                    && (upper == null || upper.subtract(value).compareTo(error) > 0);
        }
    }

    private static Cell cell(final double rounded) {
        if (rounded == Double.POSITIVE_INFINITY) {
            return new Cell(OVERFLOW_BOUNDARY, null);
        }
        if (rounded == Double.NEGATIVE_INFINITY) {
            return new Cell(null, OVERFLOW_BOUNDARY.negate());
        }
        final BigDecimal centre = exact(rounded);
        return new Cell(
                rounded == -Double.MAX_VALUE ? OVERFLOW_BOUNDARY.negate()
                        : exact(Math.nextDown(rounded)).add(centre).divide(TWO),
                rounded == Double.MAX_VALUE ? OVERFLOW_BOUNDARY
                        : centre.add(exact(Math.nextUp(rounded))).divide(TWO));
    }
}
