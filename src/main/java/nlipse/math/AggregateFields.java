package nlipse.math;

import java.util.Arrays;

/** Sum-, order- and mean-based focal fields. */
final class AggregateFields {
    private static final int HYPERBOLA_SORT_THRESHOLD = 24;
    private static final int MEDIAN_SORT_THRESHOLD = 16;

    private AggregateFields() {
    }

    static DistanceField sum(final FocusSet foci) {
        return new SumField(foci, false, 1);
    }

    static DistanceField hyperbola(final FocusSet foci) {
        return new HyperbolaField(foci);
    }

    static DistanceField range(final FocusSet foci) {
        return new RangeField(foci);
    }

    static DistanceField powerMean(final FocusSet foci, final double power) {
        if (foci.activeCount() == 0) {
            return (x, y) -> 0;
        }
        if (foci.activeCount() == 1) {
            return RadialFields.envelope(foci, true);
        }
        if (power == Double.POSITIVE_INFINITY) {
            return RadialFields.envelope(foci, false);
        }
        if (power == Double.NEGATIVE_INFINITY) {
            return RadialFields.envelope(foci, true);
        }
        if (power == 1) {
            return new SumField(foci, true, foci.activeCount());
        }
        if (power == 2) {
            return new RmsField(foci);
        }
        if (power == -1) {
            return new HarmonicMeanField(foci);
        }
        if (power == 0) {
            return new GeometricMeanField(foci);
        }
        return new PowerMeanField(foci, power);
    }

    static DistanceField median(final FocusSet foci) {
        return new MedianField(foci);
    }

    static DistanceField smoothEnvelope(final FocusSet foci,
            final double temperature, final boolean nearest) {
        return new SmoothEnvelopeField(foci, temperature, nearest);
    }

    private static final class SumField implements DistanceField {
        private final FocusSet foci;
        private final boolean magnitudes;
        private final int divisor;

        SumField(final FocusSet foci, final boolean magnitudes, final int divisor) {
            this.foci = foci;
            this.magnitudes = magnitudes;
            this.divisor = divisor;
        }

        @Override
        public double value(final double x, final double y) {
            final FieldMath.CompensatedSum sum = new FieldMath.CompensatedSum();
            boolean positiveInfinity = false;
            boolean negativeInfinity = false;
            boolean positiveFinite = false;
            boolean negativeFinite = false;
            boolean roundingSensitiveFinite = false;
            final FieldMath.CompensatedSum magnitudeSum = new FieldMath.CompensatedSum();
            int finiteTermCount = 0;
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            for (int index = 0; index < foci.size(); index++) {
                if (foci.weight(index) == 0) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double term = magnitudes
                        ? foci.magnitudeDistanceApproximate(index, x, y)
                        : foci.signedDistanceApproximate(index, x, y);
                if (Double.isNaN(term)) {
                    return Double.NaN;
                }
                if (finitePoint && term == 0 && distance != 0) {
                    return exactValue(x, y);
                }
                if (Double.isInfinite(term)) {
                    positiveInfinity |= term > 0;
                    negativeInfinity |= term < 0;
                } else {
                    positiveFinite |= term > 0;
                    negativeFinite |= term < 0;
                    roundingSensitiveFinite |= FieldMath.isMagnitudeRoundingSensitive(term);
                    magnitudeSum.add(Math.abs(term));
                    finiteTermCount++;
                    sum.add(term);
                }
            }
            if (finitePoint && (positiveInfinity || negativeInfinity)) {
                return exactValue(x, y);
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
            if (finitePoint && roundingSensitiveFinite && foci.tryConsumeExact()) {
                return exactValue(x, y);
            }
            final double rawSum = sum.value();
            final double result = rawSum / divisor;
            if (finitePoint && !magnitudes && positiveFinite && negativeFinite
                    && FieldMath.cancellationUncertain(rawSum, magnitudeSum.value(),
                            finiteTermCount, FieldMath.EXACT_CANCELLATION_RATIO)
                    && foci.tryConsumeExact()) {
                return exactValue(x, y);
            }
            return Double.isFinite(result) ? result : exactValue(x, y);
        }

        private double exactValue(final double x, final double y) {
            return magnitudes
                    ? ExactFieldMath.arithmeticMagnitudeMean(foci, x, y)
                    : ExactFieldMath.signedDistanceSum(foci, x, y);
        }
    }

    private static final class RmsField implements DistanceField {
        private final FocusSet foci;

        RmsField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            double norm = 0;
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            boolean roundingSensitive = false;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double value = foci.magnitudeDistanceApproximate(index, x, y);
                if (Double.isNaN(value)) {
                    return Double.NaN;
                }
                if (finitePoint && (!Double.isFinite(distance)
                        || (value == 0 && distance != 0))) {
                    return ExactFieldMath.quadraticMagnitudeMean(foci, x, y);
                }
                norm = Math.hypot(norm, value);
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(value)
                        || FieldMath.isMagnitudeRoundingSensitive(norm);
            }
            final double result = norm / Math.sqrt(foci.activeCount());
            if (finitePoint && (!Double.isFinite(result) || result == 0 && norm != 0)) {
                return ExactFieldMath.quadraticMagnitudeMean(foci, x, y);
            }
            return finitePoint && roundingSensitive && foci.tryConsumeExact()
                    ? ExactFieldMath.quadraticMagnitudeMean(foci, x, y) : result;
        }
    }

    private static final class HarmonicMeanField implements DistanceField {
        private final FocusSet foci;

        HarmonicMeanField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final double[] values = FieldMath.scratch(foci.size());
            double minimum = Double.POSITIVE_INFINITY;
            boolean exactNeeded = false;
            boolean roundingSensitiveNeeded = false;
            for (int index = 0; index < foci.size(); index++) {
                values[index] = Double.POSITIVE_INFINITY;
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double value = foci.magnitudeDistanceApproximate(index, x, y);
                if (Double.isNaN(value)) {
                    return Double.NaN;
                }
                if (value == 0) {
                    return 0;
                }
                exactNeeded |= finitePoint && (!Double.isFinite(distance)
                        || !Double.isFinite(value));
                roundingSensitiveNeeded |= FieldMath.isMagnitudeRoundingSensitive(value);
                values[index] = value;
                minimum = Math.min(minimum, value);
            }
            if (finitePoint && (exactNeeded
                    || roundingSensitiveNeeded && foci.tryConsumeExact())) {
                return ExactFieldMath.powerMean(foci, x, y, -1);
            }
            if (Double.isInfinite(minimum)) {
                return Double.POSITIVE_INFINITY;
            }

            final FieldMath.CompensatedSum reciprocalSum = new FieldMath.CompensatedSum();
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index) && Double.isFinite(values[index])) {
                    reciprocalSum.add(minimum / values[index]);
                }
            }
            final double scale = foci.activeCount() / reciprocalSum.value();
            final double result = minimum * scale;
            if (finitePoint && (!Double.isFinite(result)
                    || result == 0 && minimum != 0)) {
                return ExactFieldMath.powerMean(foci, x, y, -1);
            }
            return finitePoint && FieldMath.isMagnitudeRoundingSensitive(result)
                    && foci.tryConsumeExact()
                    ? ExactFieldMath.powerMean(foci, x, y, -1) : result;
        }
    }

    private static final class HyperbolaField implements DistanceField {
        private final FocusSet foci;

        HyperbolaField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            final int size = foci.size();
            if (size < 2) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final double[] values = FieldMath.scratch(foci.size());
            double scale = 0;
            boolean roundingSensitive = false;
            for (int index = 0; index < size; index++) {
                final double distance = foci.distance(index, x, y);
                final double value = foci.signedDistanceApproximate(index, x, y);
                if (Double.isNaN(value)) {
                    return Double.NaN;
                }
                if (finitePoint && (!Double.isFinite(distance)
                        || (!Double.isFinite(value) && foci.weight(index) != 0)
                        || (value == 0 && distance != 0 && foci.weight(index) != 0))) {
                    return ExactFieldMath.hyperbola(foci, x, y);
                }
                values[index] = value;
                scale = Math.max(scale, Math.abs(value));
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(value);
            }
            if (scale == 0) {
                return 0;
            }
            if (!Double.isFinite(scale)) {
                return finitePoint ? ExactFieldMath.hyperbola(foci, x, y)
                        : pairwiseMean(values, size, 1);
            }
            for (int index = 0; index < size; index++) {
                values[index] /= scale;
            }
            final double normalized = size < HYPERBOLA_SORT_THRESHOLD
                    ? pairwiseMean(values, size, 1)
                    : sortedPairwiseMean(values, size);
            final double result = scale * normalized;
            final int pairCount = size * (size - 1) / 2;
            if (finitePoint && (roundingSensitive
                    || FieldMath.cancellationUncertain(normalized, 1.0,
                            pairCount, FieldMath.EXACT_CANCELLATION_RATIO))
                    && foci.tryConsumeExact()) {
                return ExactFieldMath.hyperbola(foci, x, y);
            }
            return Double.isFinite(result) || !finitePoint ? result
                    : ExactFieldMath.hyperbola(foci, x, y);
        }

        private static double pairwiseMean(final double[] values, final int size,
                final double scale) {
            final FieldMath.CompensatedSum sum = new FieldMath.CompensatedSum();
            for (int index = 0; index < size; index++) {
                for (int previous = 0; previous < index; previous++) {
                    sum.add(Math.abs(values[index] - values[previous]));
                }
            }
            return scale * 2 * sum.value() / ((double) size * (size - 1));
        }

        private static double sortedPairwiseMean(final double[] values, final int size) {
            Arrays.sort(values, 0, size);
            final FieldMath.CompensatedSum sum = new FieldMath.CompensatedSum();
            for (int index = 0; index < size; index++) {
                sum.add((2.0 * index - size + 1) * values[index]);
            }
            return 2 * sum.value() / ((double) size * (size - 1));
        }
    }

    private static final class RangeField implements DistanceField {
        private final FocusSet foci;

        RangeField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            if (foci.activeCount() == 0) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            boolean roundingSensitive = false;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double candidate = foci.magnitudeDistanceApproximate(index, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                if (finitePoint && (!Double.isFinite(distance)
                        || (candidate == 0 && distance != 0))) {
                    return ExactFieldMath.range(foci, x, y);
                }
                minimum = Math.min(minimum, candidate);
                maximum = Math.max(maximum, candidate);
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(candidate);
            }
            if (foci.activeCount() < 2) {
                return 0;
            }
            final double result = maximum - minimum;
            if (finitePoint && roundingSensitive && foci.tryConsumeExact()) {
                return ExactFieldMath.range(foci, x, y);
            }
            if (finitePoint && Double.isFinite(maximum)
                    && FieldMath.cancellationUncertain(result, maximum, 2,
                            FieldMath.EXACT_CANCELLATION_RATIO)
                    && foci.tryConsumeExact()) {
                return ExactFieldMath.range(foci, x, y);
            }
            return Double.isFinite(result) || !finitePoint ? result
                    : ExactFieldMath.range(foci, x, y);
        }
    }

    private static final class GeometricMeanField implements DistanceField {
        private final FocusSet foci;

        GeometricMeanField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final FieldMath.CompensatedSum logarithms = new FieldMath.CompensatedSum();
            final FieldMath.CompensatedSum logarithmMagnitudes =
                    new FieldMath.CompensatedSum();
            boolean hasZero = false;
            boolean hasInfinity = false;
            boolean positiveLogarithm = false;
            boolean negativeLogarithm = false;
            boolean exactNeeded = false;
            boolean roundingSensitiveNeeded = false;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double magnitude = foci.magnitudeDistanceApproximate(index, x, y);
                final double logarithm = foci.logMagnitudeDistance(index, x, y);
                if (Double.isNaN(logarithm)) {
                    return Double.NaN;
                }
                if (finitePoint && (!Double.isFinite(distance)
                        || !Double.isFinite(magnitude) && Double.isFinite(logarithm)
                        || magnitude == 0 && distance != 0)) {
                    exactNeeded = true;
                }
                roundingSensitiveNeeded |= FieldMath.isMagnitudeRoundingSensitive(magnitude);
                hasZero |= logarithm == Double.NEGATIVE_INFINITY;
                hasInfinity |= logarithm == Double.POSITIVE_INFINITY;
                if (Double.isFinite(logarithm)) {
                    positiveLogarithm |= logarithm > 0;
                    negativeLogarithm |= logarithm < 0;
                    logarithmMagnitudes.add(Math.abs(logarithm));
                    logarithms.add(logarithm);
                }
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
            final double logarithmSum = logarithms.value();
            final double meanLogarithm = logarithmSum / foci.activeCount();
            if (finitePoint && (exactNeeded
                    || roundingSensitiveNeeded && foci.tryConsumeExact()
                    || positiveLogarithm && negativeLogarithm
                            && FieldMath.cancellationUncertain(logarithmSum,
                                    logarithmMagnitudes.value(), foci.activeCount(),
                                    FieldMath.EXACT_CANCELLATION_RATIO)
                            && foci.tryConsumeExact())) {
                return ExactFieldMath.powerMean(foci, x, y, 0);
            }
            return FieldMath.expFromLog(meanLogarithm);
        }
    }

    private static final class PowerMeanField implements DistanceField {
        private final FocusSet foci;
        private final double power;

        PowerMeanField(final FocusSet foci, final double power) {
            this.foci = foci;
            this.power = power;
        }

        @Override
        public double value(final double x, final double y) {
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final double[] logarithms = FieldMath.scratch(foci.size());
            int finiteCount = 0;
            boolean hasZero = false;
            boolean hasInfinity = false;
            boolean exactNeeded = false;
            boolean roundingSensitiveNeeded = false;
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double magnitude = foci.magnitudeDistanceApproximate(index, x, y);
                final double logarithm = foci.logMagnitudeDistance(index, x, y);
                if (Double.isNaN(logarithm)) {
                    return Double.NaN;
                }
                if (finitePoint && (!Double.isFinite(distance)
                        || !Double.isFinite(magnitude) && Double.isFinite(logarithm)
                        || magnitude == 0 && distance != 0)) {
                    exactNeeded = true;
                }
                roundingSensitiveNeeded |= FieldMath.isMagnitudeRoundingSensitive(magnitude);
                if (logarithm == Double.NEGATIVE_INFINITY) {
                    hasZero = true;
                } else if (logarithm == Double.POSITIVE_INFINITY) {
                    hasInfinity = true;
                } else {
                    logarithms[finiteCount++] = logarithm;
                    minimum = Math.min(minimum, logarithm);
                    maximum = Math.max(maximum, logarithm);
                }
            }
            if (power > 0) {
                if (hasInfinity) {
                    return Double.POSITIVE_INFINITY;
                }
                if (finiteCount == 0) {
                    return 0;
                }
            } else {
                if (hasZero) {
                    return 0;
                }
                if (finiteCount == 0) {
                    return Double.POSITIVE_INFINITY;
                }
            }
            if (finitePoint && (exactNeeded
                    || roundingSensitiveNeeded && foci.tryConsumeExact())) {
                return ExactFieldMath.powerMean(foci, x, y, power);
            }

            final double result;
            if (!hasZero && !hasInfinity
                    && Math.abs(power) * (maximum - minimum) <= FieldMath.CENTRED_LIMIT) {
                result = FieldMath.expFromLog(centredLogarithm(logarithms, finiteCount));
            } else {
                final double anchor = power > 0 ? maximum : minimum;
                final FieldMath.CompensatedSum exponentials = new FieldMath.CompensatedSum();
                for (int index = 0; index < finiteCount; index++) {
                    exponentials.add(Math.exp(power * (logarithms[index] - anchor)));
                }
                final double mean = exponentials.value() / foci.activeCount();
                result = FieldMath.expFromLog(anchor + Math.log(mean) / power);
            }
            if (finitePoint && (!Double.isFinite(result)
                    || result == 0 && finiteCount > 0 && !hasZero)) {
                return ExactFieldMath.powerMean(foci, x, y, power);
            }
            return result;
        }

        private double centredLogarithm(final double[] logarithms, final int count) {
            final FieldMath.CompensatedSum logSum = new FieldMath.CompensatedSum();
            for (int index = 0; index < count; index++) {
                logSum.add(logarithms[index]);
            }
            final double meanLogarithm = logSum.value() / count;
            final FieldMath.CompensatedSum deviations = new FieldMath.CompensatedSum();
            for (int index = 0; index < count; index++) {
                deviations.add(Math.expm1(power * (logarithms[index] - meanLogarithm)));
            }
            return meanLogarithm + Math.log1p(deviations.value() / count) / power;
        }
    }

    private static final class MedianField implements DistanceField {
        private final FocusSet foci;

        MedianField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            if (foci.activeCount() == 0) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final double[] values = FieldMath.scratch(foci.size());
            int count = 0;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double candidate = foci.magnitudeDistanceApproximate(index, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                if (finitePoint && (!Double.isFinite(distance)
                        || (candidate == 0 && distance != 0))) {
                    return ExactFieldMath.median(foci, x, y);
                }
                values[count++] = candidate;
            }
            final int lowerIndex = (count - 1) / 2;
            final int upperIndex = count / 2;
            if (count <= MEDIAN_SORT_THRESHOLD) {
                Arrays.sort(values, 0, count);
            } else {
                select(values, 0, count - 1, lowerIndex);
                if (lowerIndex != upperIndex) {
                    select(values, lowerIndex + 1, count - 1, upperIndex);
                }
            }
            final double lower = values[lowerIndex];
            final double upper = values[upperIndex];
            if (finitePoint
                    && (FieldMath.isMagnitudeRoundingSensitive(lower)
                            || FieldMath.isMagnitudeRoundingSensitive(upper)
                            || lowerIndex != upperIndex && lower == upper)
                    && foci.tryConsumeExact()) {
                return ExactFieldMath.median(foci, x, y);
            }
            final double result = ScalarRanges.interpolate(lower, upper, 0.5);
            return Double.isFinite(result) || !finitePoint ? result
                    : ExactFieldMath.median(foci, x, y);
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

    private static final class SmoothEnvelopeField implements DistanceField {
        private final FocusSet foci;
        private final double temperature;
        private final boolean nearest;
        private final DistanceField limitingEnvelope;

        SmoothEnvelopeField(final FocusSet foci, final double temperature,
                final boolean nearest) {
            this.foci = foci;
            this.temperature = temperature;
            this.nearest = nearest;
            limitingEnvelope = RadialFields.envelope(foci, nearest);
        }

        @Override
        public double value(final double x, final double y) {
            if (foci.activeCount() == 0) {
                return 0;
            }
            if (foci.activeCount() == 1) {
                for (int index = 0; index < foci.size(); index++) {
                    if (foci.isActive(index)) {
                        return foci.magnitudeDistance(index, x, y);
                    }
                }
            }

            final double[] ratios = FieldMath.scratch(foci.size());
            int count = 0;
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            boolean allZero = true;
            boolean exactRatioNeeded = false;
            boolean roundingSensitiveRatio = false;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                // Scale by |w|/temperature before taking the norm. This keeps the
                // ordinary path on one division per focus without overflowing |w|d.
                exactRatioNeeded |= foci.magnitudeDistanceRatioNeedsExact(
                        index, x, y, temperature);
                final double ratio = foci.magnitudeDistanceRatio(
                        index, x, y, temperature);
                if (Double.isNaN(ratio)) {
                    return Double.NaN;
                }
                ratios[count++] = ratio;
                allZero &= ratio == 0;
                roundingSensitiveRatio |= FieldMath.isMagnitudeRoundingSensitive(ratio);
                minimum = Math.min(minimum, ratio);
                maximum = Math.max(maximum, ratio);
            }
            if (allZero) {
                return ExactFieldMath.smoothEnvelope(foci, x, y, temperature, nearest);
            }
            if ((exactRatioNeeded || roundingSensitiveRatio)
                    && Double.isFinite(x) && Double.isFinite(y)
                    && foci.tryConsumeExact()) {
                return ExactFieldMath.smoothEnvelope(foci, x, y, temperature, nearest);
            }
            if (!nearest && Double.isInfinite(maximum)) {
                return limitingEnvelope.value(x, y);
            }
            if (nearest && Double.isInfinite(minimum)) {
                return limitingEnvelope.value(x, y);
            }
            if (minimum == maximum) {
                // Distinct distances can collapse to the same ratio after division by
                // a huge temperature. Their common large-temperature limit is the
                // arithmetic mean, not whichever focus happened to be visited first.
                return ExactFieldMath.smoothEnvelope(foci, x, y, temperature, nearest);
            }

            final double resultRatio;
            if (Double.isFinite(maximum) && maximum - minimum <= FieldMath.CENTRED_LIMIT) {
                final double mean = stableNonNegativeMean(ratios, count, maximum);
                final FieldMath.CompensatedSum deviations = new FieldMath.CompensatedSum();
                for (int index = 0; index < count; index++) {
                    deviations.add(Math.expm1(nearest
                            ? mean - ratios[index] : ratios[index] - mean));
                }
                final double correction = Math.log1p(deviations.value() / count);
                resultRatio = nearest ? mean - correction : mean + correction;
            } else {
                final double anchor = nearest ? minimum : maximum;
                final FieldMath.CompensatedSum exponentials = new FieldMath.CompensatedSum();
                for (int index = 0; index < count; index++) {
                    exponentials.add(Math.exp(nearest
                            ? anchor - ratios[index] : ratios[index] - anchor));
                }
                final double correction = Math.log(exponentials.value() / count);
                resultRatio = nearest ? anchor - correction : anchor + correction;
            }
            final double result = temperature * resultRatio;
            if (!Double.isFinite(result)) {
                return ExactFieldMath.smoothEnvelope(foci, x, y, temperature, nearest);
            }
            return FieldMath.isMagnitudeRoundingSensitive(result) && foci.tryConsumeExact()
                    ? ExactFieldMath.smoothEnvelope(foci, x, y, temperature, nearest)
                    : result;
        }

        private static double stableNonNegativeMean(final double[] values,
                final int count, final double maximum) {
            if (maximum == 0) {
                return 0;
            }
            final FieldMath.CompensatedSum sum = new FieldMath.CompensatedSum();
            for (int index = 0; index < count; index++) {
                sum.add(values[index] / maximum);
            }
            return maximum * (sum.value() / count);
        }
    }
}
