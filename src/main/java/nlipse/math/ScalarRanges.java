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
        final double range = maximum - minimum;
        if (Double.isFinite(range)) {
            return (value - minimum) / range;
        }
        final double scale = Math.max(Math.abs(minimum), Math.abs(maximum));
        final double scaledMinimum = minimum / scale;
        return (value / scale - scaledMinimum) / (maximum / scale - scaledMinimum);
    }
}
