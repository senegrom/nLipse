package nlipse.math;

import java.util.Arrays;
import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Factory for immutable weighted multi-focus fields. */
public final class DistanceFields {
    private static final double LOG_MAX_VALUE = Math.log(Double.MAX_VALUE);
    private static final double LOG_MIN_VALUE = Math.log(Double.MIN_VALUE);
    private static final double CENTRED_POWER_LIMIT = 0.5;

    private DistanceFields() {
    }

    public static DistanceField create(final CurveType type, final List<Focus> foci) {
        return create(type, foci, type == null ? 1 : type.defaultParameter());
    }

    public static DistanceField create(final CurveType type, final List<Focus> foci,
            final double familyParameter) {
        if (type == null) {
            throw new IllegalArgumentException("Curve type is required");
        }
        if (foci == null || foci.isEmpty()) {
            throw new IllegalArgumentException("At least one focus is required");
        }
        final double parameter = type.normalizeParameter(familyParameter);
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
            case POWER_MEAN -> createPowerMean(xs, ys, weights, parameter);
            case MEDIAN -> new MedianField(xs, ys, weights);
            case SMOOTH_NEAREST -> new SmoothEnvelopeField(xs, ys, weights, parameter, true);
            case SMOOTH_FARTHEST -> new SmoothEnvelopeField(xs, ys, weights, parameter, false);
            case GAUSSIAN -> new GaussianField(xs, ys, weights, parameter);
        };
    }

    private static DistanceField createPowerMean(final double[] xs, final double[] ys,
            final double[] weights, final double power) {
        final int activeCount = activeCount(weights);
        if (activeCount == 0) {
            return (x, y) -> 0;
        }
        if (power == Double.POSITIVE_INFINITY) {
            return new EnvelopeField(xs, ys, weights, false);
        }
        if (power == Double.NEGATIVE_INFINITY) {
            return new EnvelopeField(xs, ys, weights, true);
        }
        if (power == 1) {
            // Reuse the compensated signed-sum implementation with magnitude weights.
            return new ScaledField(new SumField(xs, ys, absoluteWeights(weights)),
                    1.0 / activeCount);
        }
        if (power == 2) {
            // RMS is the existing quadratic norm divided by sqrt(active count).
            return new ScaledField(new QuadraticField(xs, ys, weights),
                    1.0 / Math.sqrt(activeCount));
        }
        if (power == 0) {
            return new GeometricMeanField(xs, ys, weights);
        }
        return new PowerMeanField(xs, ys, weights, power, activeCount);
    }

    private static int activeCount(final double[] weights) {
        int count = 0;
        for (final double weight : weights) {
            if (weight != 0) {
                count++;
            }
        }
        return count;
    }

    private static double[] absoluteWeights(final double[] weights) {
        final double[] magnitudes = weights.clone();
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = Math.abs(magnitudes[i]);
        }
        return magnitudes;
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

        protected final boolean isActive(final int index) {
            return weights[index] != 0;
        }

        protected final double distance(final int index, final double x, final double y) {
            return Math.hypot(x - xs[index], y - ys[index]);
        }

        protected final double magnitudeDistance(final int index,
                final double x, final double y) {
            return distance(index, x, y) * Math.abs(weights[index]);
        }
    }

    private static final class ScaledField implements DistanceField {
        private final DistanceField delegate;
        private final double scale;

        ScaledField(final DistanceField delegate, final double scale) {
            this.delegate = delegate;
            this.scale = scale;
        }

        @Override
        public double value(final double x, final double y) {
            return delegate.value(x, y) * scale;
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
            boolean positiveInfinity = false;
            boolean negativeInfinity = false;
            for (int i = 0; i < xs.length; i++) {
                final double term = distance(i, x, y) * weights[i];
                if (Double.isNaN(term)) {
                    return Double.NaN;
                }
                if (Double.isInfinite(term)) {
                    positiveInfinity |= term > 0;
                    negativeInfinity |= term < 0;
                    continue;
                }
                largestMagnitude = Math.max(largestMagnitude, Math.abs(term));
                final double next = sum + term;
                if (Math.abs(sum) >= Math.abs(term)) {
                    compensation += (sum - next) + term;
                } else {
                    compensation += (term - next) + sum;
                }
                sum = next;
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

            final double result = sum + compensation;
            if (Double.isFinite(result) || largestMagnitude == 0) {
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
                final double focalDistance = distance(i, x, y);
                if (focalDistance == 0) {
                    if (weight > 0) {
                        hasZeroFactor = true;
                    } else {
                        hasInfiniteFactor = true;
                    }
                } else if (Double.isInfinite(focalDistance)) {
                    if (weight > 0) {
                        hasInfiniteFactor = true;
                    } else {
                        hasZeroFactor = true;
                    }
                } else {
                    final double term = weight / weightScale * Math.log(focalDistance);
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
                if (!isActive(i)) {
                    continue;
                }
                final double candidate = magnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
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
                if (isActive(i)) {
                    norm = Math.hypot(norm, magnitudeDistance(i, x, y));
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
                if (!isActive(i)) {
                    continue;
                }
                final double candidate = magnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
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
                if (Double.isNaN(focalDistance)) {
                    return Double.NaN;
                }
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

    private static final class GeometricMeanField extends FocusField {
        GeometricMeanField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
        }

        @Override
        public double value(final double x, final double y) {
            int count = 0;
            double logarithmSum = 0;
            double compensation = 0;
            boolean hasZero = false;
            boolean hasInfinity = false;
            for (int i = 0; i < xs.length; i++) {
                if (!isActive(i)) {
                    continue;
                }
                count++;
                final double candidate = magnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                if (candidate == 0) {
                    hasZero = true;
                } else if (Double.isInfinite(candidate)) {
                    hasInfinity = true;
                } else {
                    final double term = Math.log(candidate);
                    final double next = logarithmSum + term;
                    if (Math.abs(logarithmSum) >= Math.abs(term)) {
                        compensation += (logarithmSum - next) + term;
                    } else {
                        compensation += (term - next) + logarithmSum;
                    }
                    logarithmSum = next;
                }
            }
            if (count == 0) {
                return 0;
            }
            if (hasZero && hasInfinity) {
                return Double.NaN;
            }
            if (hasZero) {
                return 0;
            }
            if (hasInfinity) {
                return Double.POSITIVE_INFINITY;
            }
            return expFromLog((logarithmSum + compensation) / count);
        }
    }

    private static final class PowerMeanField extends FocusField {
        private final double power;
        private final int activeCount;
        private final ThreadLocal<double[]> logarithmBuffer;

        PowerMeanField(final double[] xs, final double[] ys, final double[] weights,
                final double power, final int activeCount) {
            super(xs, ys, weights);
            this.power = power;
            this.activeCount = activeCount;
            logarithmBuffer = ThreadLocal.withInitial(() -> new double[xs.length]);
        }

        @Override
        public double value(final double x, final double y) {
            final double[] logarithms = logarithmBuffer.get();
            int finitePositiveCount = 0;
            boolean hasZero = false;
            boolean hasInfinity = false;
            double minimumLog = Double.POSITIVE_INFINITY;
            double maximumLog = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < xs.length; i++) {
                if (!isActive(i)) {
                    continue;
                }
                final double candidate = magnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                if (candidate == 0) {
                    hasZero = true;
                } else if (Double.isInfinite(candidate)) {
                    hasInfinity = true;
                } else {
                    final double logarithm = Math.log(candidate);
                    logarithms[finitePositiveCount++] = logarithm;
                    minimumLog = Math.min(minimumLog, logarithm);
                    maximumLog = Math.max(maximumLog, logarithm);
                }
            }

            if (power > 0) {
                if (hasInfinity) {
                    return Double.POSITIVE_INFINITY;
                }
                if (finitePositiveCount == 0) {
                    return 0;
                }
            } else {
                if (hasZero) {
                    return 0;
                }
                if (finitePositiveCount == 0) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            if (!hasZero && !hasInfinity
                    && Math.abs(power) * (maximumLog - minimumLog) <= CENTRED_POWER_LIMIT) {
                return expFromLog(centredPowerMeanLog(logarithms, finitePositiveCount, power));
            }

            final double anchor = power > 0 ? maximumLog : minimumLog;
            double sum = 0;
            double compensation = 0;
            for (int i = 0; i < finitePositiveCount; i++) {
                final double term = Math.exp(power * (logarithms[i] - anchor));
                final double next = sum + term;
                compensation += Math.abs(sum) >= Math.abs(term)
                        ? (sum - next) + term : (term - next) + sum;
                sum = next;
            }
            final double mean = (sum + compensation) / activeCount;
            return expFromLog(anchor + Math.log(mean) / power);
        }

        private static double centredPowerMeanLog(final double[] logarithms,
                final int count, final double power) {
            double sum = 0;
            double compensation = 0;
            for (int i = 0; i < count; i++) {
                final double term = logarithms[i];
                final double next = sum + term;
                compensation += Math.abs(sum) >= Math.abs(term)
                        ? (sum - next) + term : (term - next) + sum;
                sum = next;
            }
            final double meanLogarithm = (sum + compensation) / count;

            sum = 0;
            compensation = 0;
            for (int i = 0; i < count; i++) {
                final double term = Math.expm1(power * (logarithms[i] - meanLogarithm));
                final double next = sum + term;
                compensation += Math.abs(sum) >= Math.abs(term)
                        ? (sum - next) + term : (term - next) + sum;
                sum = next;
            }
            return meanLogarithm + Math.log1p((sum + compensation) / count) / power;
        }
    }

    private static final class MedianField extends FocusField {
        private static final int SORT_THRESHOLD = 16;
        private final ThreadLocal<double[]> buffer;

        MedianField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
            buffer = ThreadLocal.withInitial(() -> new double[xs.length]);
        }

        @Override
        public double value(final double x, final double y) {
            final double[] values = buffer.get();
            int count = 0;
            for (int i = 0; i < xs.length; i++) {
                if (!isActive(i)) {
                    continue;
                }
                final double candidate = magnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                values[count++] = candidate;
            }
            if (count == 0) {
                return 0;
            }
            final int lowerIndex = (count - 1) / 2;
            final int upperIndex = count / 2;
            if (count <= SORT_THRESHOLD) {
                Arrays.sort(values, 0, count);
                return ScalarRanges.interpolate(values[lowerIndex], values[upperIndex], 0.5);
            }
            final double lower = select(values, 0, count - 1, lowerIndex);
            if (lowerIndex == upperIndex) {
                return lower;
            }
            final double upper = select(values, 0, count - 1, upperIndex);
            return ScalarRanges.interpolate(lower, upper, 0.5);
        }

        private static double select(final double[] values, int left, int right, final int target) {
            while (left < right) {
                final double pivot = medianOfThree(values[left], values[(left + right) >>> 1],
                        values[right]);
                int lower = left;
                int index = left;
                int upper = right;
                while (index <= upper) {
                    final int comparison = Double.compare(values[index], pivot);
                    if (comparison < 0) {
                        swap(values, lower++, index++);
                    } else if (comparison > 0) {
                        swap(values, index, upper--);
                    } else {
                        index++;
                    }
                }
                if (target < lower) {
                    right = lower - 1;
                } else if (target > upper) {
                    left = upper + 1;
                } else {
                    return values[target];
                }
            }
            return values[left];
        }

        private static double medianOfThree(final double first,
                final double second, final double third) {
            if (Double.compare(first, second) > 0) {
                return Double.compare(second, third) > 0 ? second
                        : Double.compare(first, third) > 0 ? third : first;
            }
            return Double.compare(first, third) > 0 ? first
                    : Double.compare(second, third) > 0 ? third : second;
        }

        private static void swap(final double[] values, final int first, final int second) {
            final double temporary = values[first];
            values[first] = values[second];
            values[second] = temporary;
        }
    }

    private static final class SmoothEnvelopeField extends FocusField {
        private final double temperature;
        private final boolean nearest;
        private final ThreadLocal<double[]> buffer;

        SmoothEnvelopeField(final double[] xs, final double[] ys, final double[] weights,
                final double temperature, final boolean nearest) {
            super(xs, ys, weights);
            this.temperature = temperature;
            this.nearest = nearest;
            buffer = ThreadLocal.withInitial(() -> new double[xs.length]);
        }

        @Override
        public double value(final double x, final double y) {
            final double[] values = buffer.get();
            int count = 0;
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < xs.length; i++) {
                if (!isActive(i)) {
                    continue;
                }
                final double candidate = magnitudeDistance(i, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                values[count++] = candidate;
                minimum = Math.min(minimum, candidate);
                maximum = Math.max(maximum, candidate);
            }
            if (count == 0) {
                return 0;
            }
            if (!nearest && Double.isInfinite(maximum)) {
                return Double.POSITIVE_INFINITY;
            }
            if (nearest && Double.isInfinite(minimum)) {
                return Double.POSITIVE_INFINITY;
            }

            if (Double.isFinite(maximum)
                    && (maximum - minimum) / temperature <= CENTRED_POWER_LIMIT) {
                final double mean = stableNonNegativeMean(values, count, maximum);
                double sum = 0;
                double compensation = 0;
                for (int i = 0; i < count; i++) {
                    final double exponent = nearest
                            ? (mean - values[i]) / temperature
                            : (values[i] - mean) / temperature;
                    final double term = Math.expm1(exponent);
                    final double next = sum + term;
                    compensation += Math.abs(sum) >= Math.abs(term)
                            ? (sum - next) + term : (term - next) + sum;
                    sum = next;
                }
                final double correction = temperature
                        * Math.log1p((sum + compensation) / count);
                return nearest ? mean - correction : mean + correction;
            }

            final double anchor = nearest ? minimum : maximum;
            double sum = 0;
            double compensation = 0;
            for (int i = 0; i < count; i++) {
                final double exponent = nearest
                        ? (anchor - values[i]) / temperature
                        : (values[i] - anchor) / temperature;
                final double term = Math.exp(exponent);
                final double next = sum + term;
                compensation += Math.abs(sum) >= Math.abs(term)
                        ? (sum - next) + term : (term - next) + sum;
                sum = next;
            }
            final double correction = temperature * Math.log((sum + compensation) / count);
            return nearest ? anchor - correction : anchor + correction;
        }

        private static double stableNonNegativeMean(final double[] values,
                final int count, final double maximum) {
            if (maximum == 0) {
                return 0;
            }
            double sum = 0;
            double compensation = 0;
            for (int i = 0; i < count; i++) {
                final double term = values[i] / maximum;
                final double next = sum + term;
                compensation += Math.abs(sum) >= Math.abs(term)
                        ? (sum - next) + term : (term - next) + sum;
                sum = next;
            }
            return maximum * ((sum + compensation) / count);
        }
    }

    private static final class GaussianField extends FocusField {
        private final double sigma;
        private final double weightScale;

        GaussianField(final double[] xs, final double[] ys, final double[] weights,
                final double sigma) {
            super(xs, ys, weights);
            this.sigma = sigma;
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
            double sum = 0;
            double compensation = 0;
            for (int i = 0; i < xs.length; i++) {
                final double weight = weights[i];
                if (weight == 0) {
                    continue;
                }
                final double focalDistance = distance(i, x, y);
                if (Double.isNaN(focalDistance)) {
                    return Double.NaN;
                }
                final double scaledDistance = focalDistance / sigma;
                final double kernel = Double.isInfinite(scaledDistance)
                        ? 0 : Math.exp(-0.5 * scaledDistance * scaledDistance);
                final double term = (weight / weightScale) * kernel;
                final double next = sum + term;
                compensation += Math.abs(sum) >= Math.abs(term)
                        ? (sum - next) + term : (term - next) + sum;
                sum = next;
            }
            return weightScale * (sum + compensation);
        }
    }

    private static double expFromLog(final double logarithm) {
        if (logarithm > LOG_MAX_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        if (logarithm < LOG_MIN_VALUE) {
            return 0;
        }
        return Math.exp(logarithm);
    }
}
