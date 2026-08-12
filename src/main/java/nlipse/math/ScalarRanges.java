package nlipse.math;

/** Overflow-safe interpolation and normalization for finite ordered scalar ranges. */
public final class ScalarRanges {
    private ScalarRanges() {
    }

    public static double interpolate(final double minimum, final double maximum,
            final double fraction) {
        if (fraction <= 0) {
            return minimum;
        }
        if (fraction >= 1) {
            return maximum;
        }
        final double range = maximum - minimum;
        if (Double.isFinite(range)) {
            return minimum + range * fraction;
        }
        return minimum * (1 - fraction) + maximum * fraction;
    }

    public static double fraction(final double value, final double minimum,
            final double maximum) {
        if (value <= minimum) {
            return 0;
        }
        if (value >= maximum) {
            return 1;
        }
        return unboundedFraction(value, minimum, maximum);
    }

    /**
     * Returns an unclamped position in an ordered finite range without requiring the
     * span or numerator to be representable. Values outside the range produce values
     * below zero or above one, which coordinate transforms need for off-screen points.
     */
    public static double unboundedFraction(final double value, final double minimum,
            final double maximum) {
        if (Double.isNaN(value)) {
            return Double.NaN;
        }
        if (value == minimum) {
            return 0;
        }
        if (value == maximum) {
            return 1;
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return Double.NEGATIVE_INFINITY;
        }
        if (value == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        final double range = maximum - minimum;
        if (Double.isFinite(range)) {
            return (value - minimum) / range;
        }
        final double scale = Math.max(Math.max(Math.abs(minimum), Math.abs(maximum)),
                Math.abs(value));
        final double scaledMinimum = minimum / scale;
        final double scaledRange = maximum / scale - scaledMinimum;
        final double result = (value / scale - scaledMinimum) / scaledRange;
        if (!Double.isNaN(result)) {
            return result;
        }
        if (value < minimum) {
            return Double.NEGATIVE_INFINITY;
        }
        if (value > maximum) {
            return Double.POSITIVE_INFINITY;
        }
        return 0.5;
    }
}
