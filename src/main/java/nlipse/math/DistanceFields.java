package nlipse.math;

import java.util.Arrays;
import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Factory for immutable weighted multi-focus fields. */
public final class DistanceFields {
    private DistanceFields() {
    }

    public static DistanceField create(final CurveType type, final List<Focus> foci) {
        if (type == null) {
            throw new IllegalArgumentException("Curve type is required");
        }
        if (foci == null || foci.isEmpty()) {
            throw new IllegalArgumentException("At least one focus is required");
        }
        final double[] xs = new double[foci.size()];
        final double[] ys = new double[foci.size()];
        final double[] weights = new double[foci.size()];
        for (int i = 0; i < foci.size(); i++) {
            final Focus focus = foci.get(i);
            if (focus == null) {
                throw new IllegalArgumentException("Focus " + (i + 1) + " is null");
            }
            xs[i] = focus.x();
            ys[i] = focus.y();
            weights[i] = focus.weight();
        }

        return switch (type) {
            case LIPSE -> new SumField(xs, ys, weights);
            case CASSIN -> new CassiniField(xs, ys, weights);
            case HYPERB -> new HyperbolaField(xs, ys, weights);
            case NEAREST -> new EnvelopeField(xs, ys, weights, true);
            case FARTHEST -> new EnvelopeField(xs, ys, weights, false);
            case QUADRATIC -> new QuadraticField(xs, ys, weights);
            case RANGE -> new RangeField(xs, ys, weights);
            case POTENTIAL -> new PotentialField(xs, ys, weights);
        };
    }

    private abstract static class FocusField implements DistanceField {
        protected final double[] xs;
        protected final double[] ys;
        protected final double[] weights;

        FocusField(final double[] xs, final double[] ys, final double[] weights) {
            this.xs = xs;
            this.ys = ys;
            this.weights = weights;
        }

        protected final double distance(final int index, final double x, final double y) {
            return Math.hypot(x - xs[index], y - ys[index]);
        }

        /** Non-negative weighted distance for magnitude-only families; NaN means disabled. */
        protected final double activeMagnitudeDistance(final int index,
                final double x, final double y) {
            final double magnitude = Math.abs(weights[index]);
            return magnitude == 0 ? Double.NaN : distance(index, x, y) * magnitude;
        }
    }

    private static final class SumField extends FocusField {
        SumField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
        }

        @Override
        public double value(final double x, final double y) {
            double sum = 0;
            double compensation = 0;
            double largestMagnitude = 0;
            boolean allTermsFinite = true;
            for (int i = 0; i < xs.length; i++) {
                final double term = distance(i, x, y) * weights[i];
                allTermsFinite &= Double.isFinite(term);
                largestMagnitude = Math.max(largestMagnitude, Math.abs(term));
                final double next = sum + term;
                if (Math.abs(sum) >= Math.abs(term)) {
                    compensation += (sum - next) + term;
                } else {
                    compensation += (term - next) + sum;
                }
                sum = next;
            }
            final double result = sum + compensation;
            if (Double.isFinite(result) || !allTermsFinite || largestMagnitude == 0) {
                return result;
            }

            // Recover finite cancellations without allowing an intermediate sum to overflow.
            sum = 0;
            compensation = 0;
            for (int i = 0; i < xs.length; i++) {
                final double term = distance(i, x, y) * weights[i] / largestMagnitude;
                final double next = sum + term;
                if (Math.abs(sum) >= Math.abs(term)) {
                    compensation += (sum - next) + term;
                } else {
                    compensation += (term - next) + sum;
                }
                sum = next;
            }
            return largestMagnitude * (sum + compensation);
        }
    }

    private static final class CassiniField extends FocusField {
        private static final double LOG_MAX_VALUE = Math.log(Double.MAX_VALUE);
        private static final double LOG_MIN_VALUE = Math.log(Double.MIN_VALUE);

        private final double weightScale;

        CassiniField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
            double maximum = 0;
            for (final double weight : weights) {
                maximum = Math.max(maximum, Math.abs(weight));
            }
            weightScale = maximum;
        }

        @Override
        public double value(final double x, final double y) {
            if (weightScale == 0) {
                return 1;
            }
            double normalizedLogarithm = 0;
            double compensation = 0;
            boolean hasZeroFactor = false;
            boolean hasInfiniteFactor = false;
            for (int i = 0; i < xs.length; i++) {
                final double weight = weights[i];
                if (weight == 0) {
                    continue;
                }
                final double distance = distance(i, x, y);
                if (distance == 0) {
                    if (weight > 0) {
                        hasZeroFactor = true;
                    } else {
                        hasInfiniteFactor = true;
                    }
                } else if (Double.isInfinite(distance)) {
                    if (weight > 0) {
                        hasInfiniteFactor = true;
                    } else {
                        hasZeroFactor = true;
                    }
                } else {
                    final double term = weight / weightScale * Math.log(distance);
                    final double next = normalizedLogarithm + term;
                    if (Math.abs(normalizedLogarithm) >= Math.abs(term)) {
                        compensation += (normalizedLogarithm - next) + term;
                    } else {
                        compensation += (term - next) + normalizedLogarithm;
                    }
                    normalizedLogarithm = next;
                }
            }
            if (hasZeroFactor && hasInfiniteFactor) {
                return Double.NaN;
            }
            if (hasZeroFactor) {
                return 0;
            }
            final double logarithm = weightScale * (normalizedLogarithm + compensation);
            if (hasInfiniteFactor || logarithm > LOG_MAX_VALUE) {
                return Double.POSITIVE_INFINITY;
            }
            if (logarithm < LOG_MIN_VALUE) {
                return 0;
            }
            return Math.exp(logarithm);
        }
    }

    private static final class HyperbolaField extends FocusField {
        private static final int SORT_THRESHOLD = 24;

        private final ThreadLocal<double[]> buffer;

        HyperbolaField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
            buffer = ThreadLocal.withInitial(() -> new double[xs.length]);
        }

        @Override
        public double value(final double x, final double y) {
            if (xs.length < 2) {
                return 0;
            }
            final double[] distances = buffer.get();
            double largestMagnitude = 0;
            boolean allFinite = true;
            for (int i = 0; i < xs.length; i++) {
                distances[i] = distance(i, x, y) * weights[i];
                allFinite &= Double.isFinite(distances[i]);
                largestMagnitude = Math.max(largestMagnitude, Math.abs(distances[i]));
            }
            if (!allFinite) {
                return pairwiseMean(distances, 1);
            }
            if (largestMagnitude == 0) {
                return 0;
            }

            final double scale = largestMagnitude > Double.MAX_VALUE / (4.0 * xs.length)
                    ? largestMagnitude : 1;
            if (scale != 1) {
                for (int i = 0; i < distances.length; i++) {
                    distances[i] /= scale;
                }
            }

            final double normalizedMean;
            if (xs.length < SORT_THRESHOLD) {
                normalizedMean = pairwiseMean(distances, 1);
            } else {
                Arrays.sort(distances);
                double sum = 0;
                double compensation = 0;
                for (int i = 0; i < distances.length; i++) {
                    final double term = (2.0 * i - distances.length + 1) * distances[i];
                    final double next = sum + term;
                    if (Math.abs(sum) >= Math.abs(term)) {
                        compensation += (sum - next) + term;
                    } else {
                        compensation += (term - next) + sum;
                    }
                    sum = next;
                }
                normalizedMean = 2 * (sum + compensation)
                        / ((double) distances.length * (distances.length - 1));
            }
            return scale * normalizedMean;
        }

        private static double pairwiseMean(final double[] distances, final double scale) {
            double sum = 0;
            for (int i = 0; i < distances.length; i++) {
                for (int j = 0; j < i; j++) {
                    sum += Math.abs(distances[i] - distances[j]);
                }
            }
            return scale * 2 * sum / ((double) distances.length * (distances.length - 1));
        }
    }

    private static final class EnvelopeField extends FocusField {
        private final boolean nearest;

        EnvelopeField(final double[] xs, final double[] ys, final double[] weights,
                final boolean nearest) {
            super(xs, ys, weights);
            this.nearest = nearest;
        }

        @Override
        public double value(final double x, final double y) {
            double result = nearest ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            boolean active = false;
            for (int i = 0; i < xs.length; i++) {
                final double candidate = activeMagnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    continue;
                }
                result = nearest ? Math.min(result, candidate) : Math.max(result, candidate);
                active = true;
            }
            return active ? result : 0;
        }
    }

    private static final class QuadraticField extends FocusField {
        QuadraticField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
        }

        @Override
        public double value(final double x, final double y) {
            double norm = 0;
            for (int i = 0; i < xs.length; i++) {
                final double candidate = activeMagnitudeDistance(i, x, y);
                if (!Double.isNaN(candidate)) {
                    norm = Math.hypot(norm, candidate);
                }
            }
            return norm;
        }
    }

    private static final class RangeField extends FocusField {
        RangeField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
        }

        @Override
        public double value(final double x, final double y) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            int activeCount = 0;
            for (int i = 0; i < xs.length; i++) {
                final double candidate = activeMagnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    continue;
                }
                minimum = Math.min(minimum, candidate);
                maximum = Math.max(maximum, candidate);
                activeCount++;
            }
            return activeCount < 2 ? 0 : maximum - minimum;
        }
    }

    private static final class PotentialField extends FocusField {
        private final double weightScale;

        PotentialField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
            double maximum = 0;
            for (final double weight : weights) {
                maximum = Math.max(maximum, Math.abs(weight));
            }
            weightScale = maximum;
        }

        @Override
        public double value(final double x, final double y) {
            if (weightScale == 0) {
                return 0;
            }
            double normalizedSum = 0;
            double compensation = 0;
            boolean positiveInfinity = false;
            boolean negativeInfinity = false;
            for (int i = 0; i < xs.length; i++) {
                final double weight = weights[i];
                if (weight == 0) {
                    continue;
                }
                final double focalDistance = distance(i, x, y);
                if (focalDistance == 0) {
                    positiveInfinity |= weight > 0;
                    negativeInfinity |= weight < 0;
                    continue;
                }
                if (Double.isInfinite(focalDistance)) {
                    continue;
                }
                final double term = (weight / weightScale) / focalDistance;
                if (Double.isInfinite(term)) {
                    positiveInfinity |= term > 0;
                    negativeInfinity |= term < 0;
                    continue;
                }
                final double next = normalizedSum + term;
                if (Math.abs(normalizedSum) >= Math.abs(term)) {
                    compensation += (normalizedSum - next) + term;
                } else {
                    compensation += (term - next) + normalizedSum;
                }
                normalizedSum = next;
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
            return weightScale * (normalizedSum + compensation);
        }
    }
}
