package nlipse.math;

/** Shared floating-point helpers for field implementations. */
final class FieldMath {
    static final double LOG_MAX_VALUE = Math.log(Double.MAX_VALUE);
    static final double CENTRED_LIMIT = 0.5;
    private static final ThreadLocal<double[]> SCRATCH =
            ThreadLocal.withInitial(() -> new double[0]);

    private FieldMath() {
    }

    /**
     * Returns a reusable thread-local array. Only indices below
     * {@code minimumLength} belong to the caller; all contents are undefined.
     */
    static double[] scratch(final int minimumLength) {
        double[] values = SCRATCH.get();
        if (values.length < minimumLength) {
            values = new double[minimumLength];
            SCRATCH.set(values);
        }
        return values;
    }

    static double expFromLog(final double logarithm) {
        if (Double.isNaN(logarithm)) {
            return Double.NaN;
        }
        if (logarithm > LOG_MAX_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        // Math.exp performs the correct gradual-underflow rounding, including
        // the boundary where the result is Double.MIN_VALUE.
        return Math.exp(logarithm);
    }

    static double scaledProduct(final double first, final double second) {
        final double product = first * second;
        if (Double.isFinite(product) || first == 0 || second == 0
                || !Double.isFinite(first) || !Double.isFinite(second)) {
            return product;
        }
        final double logarithm = Math.log(Math.abs(first)) + Math.log(Math.abs(second));
        return Math.copySign(expFromLog(logarithm), first * Math.copySign(1.0, second));
    }

    static double multiplyFromLog(final double signedFactor, final double logarithm) {
        if (signedFactor == 0) {
            return 0;
        }
        if (Double.isNaN(logarithm)) {
            return Double.NaN;
        }
        final double magnitude = expFromLog(Math.log(Math.abs(signedFactor)) + logarithm);
        return Math.copySign(magnitude, signedFactor);
    }

    /**
     * Returns whether a mixed-sign sum is too close to its condition scale to
     * trust the floating-point result without the exceptional evaluator.
     */
    static boolean cancellationUncertain(final double result,
            final double magnitudeScale, final int termCount,
            final double relativeLimit) {
        if (!Double.isFinite(result) || !Double.isFinite(magnitudeScale)) {
            return true;
        }
        if (magnitudeScale <= 0) {
            return false;
        }
        final double ulpBound = Math.ulp(magnitudeScale) * Math.max(8, termCount * 4);
        final double relativeBound = relativeLimit <= 0
                ? 0 : magnitudeScale * relativeLimit;
        return Math.abs(result) <= Math.max(ulpBound, relativeBound);
    }

    static final class CompensatedSum {
        private double sum;
        private double compensation;

        void add(final double value) {
            final double next = sum + value;
            compensation += Math.abs(sum) >= Math.abs(value)
                    ? (sum - next) + value
                    : (value - next) + sum;
            sum = next;
        }

        double value() {
            return sum + compensation;
        }
    }
}
