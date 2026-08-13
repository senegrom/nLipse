package nlipse.math;

/** Product-, envelope- and radial-kernel focal fields. */
final class RadialFields {
    private RadialFields() {
    }

    static DistanceField cassini(final FocusSet foci) {
        return new CassiniField(foci);
    }

    static DistanceField envelope(final FocusSet foci, final boolean nearest) {
        return new EnvelopeField(foci, nearest);
    }

    static DistanceField quadratic(final FocusSet foci) {
        return new QuadraticField(foci);
    }

    static DistanceField potential(final FocusSet foci) {
        return new PotentialField(foci);
    }

    static DistanceField gaussian(final FocusSet foci, final double sigma) {
        return new GaussianField(foci, sigma);
    }

    private static final class CassiniField implements DistanceField {
        private final FocusSet foci;

        CassiniField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            final double weightScale = foci.maximumAbsoluteWeight();
            if (weightScale == 0) {
                return 1;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final FieldMath.CompensatedSum normalizedSum = new FieldMath.CompensatedSum();
            final FieldMath.CompensatedSum normalizedMagnitudes =
                    new FieldMath.CompensatedSum();
            boolean exactNeeded = false;
            boolean zeroFactor = false;
            boolean infiniteFactor = false;
            boolean positiveTerm = false;
            boolean negativeTerm = false;
            for (int index = 0; index < foci.size(); index++) {
                final double weight = foci.weight(index);
                if (weight == 0) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double logarithm = foci.logDistance(index, x, y);
                if (Double.isNaN(logarithm)) {
                    return Double.NaN;
                }
                if (finitePoint && !Double.isFinite(distance)
                        && Double.isFinite(logarithm)) {
                    exactNeeded = true;
                }
                if (logarithm == Double.NEGATIVE_INFINITY) {
                    zeroFactor |= weight > 0;
                    infiniteFactor |= weight < 0;
                    continue;
                }
                if (logarithm == Double.POSITIVE_INFINITY) {
                    infiniteFactor |= weight > 0;
                    zeroFactor |= weight < 0;
                    continue;
                }
                final double normalizedWeight = weight / weightScale;
                final double term = normalizedWeight * logarithm;
                if ((term == 0 && logarithm != 0) || !Double.isFinite(term)) {
                    exactNeeded = true;
                } else {
                    positiveTerm |= term > 0;
                    negativeTerm |= term < 0;
                    normalizedMagnitudes.add(Math.abs(term));
                    normalizedSum.add(term);
                }
            }
            if (zeroFactor && infiniteFactor) {
                return Double.NaN;
            }
            if (zeroFactor) {
                return 0;
            }
            if (infiniteFactor) {
                return Double.POSITIVE_INFINITY;
            }
            final double normalized = normalizedSum.value();
            final double resultLogarithm = weightScale * normalized;
            if (finitePoint && (exactNeeded || !Double.isFinite(resultLogarithm)
                    || positiveTerm && negativeTerm
                            && FieldMath.cancellationUncertain(normalized,
                                    normalizedMagnitudes.value(), foci.activeCount(),
                                    FieldMath.EXACT_CANCELLATION_RATIO)
                            && foci.tryConsumeExact())) {
                return ExactFieldMath.cassini(foci, x, y);
            }
            return FieldMath.expFromLog(resultLogarithm);
        }
    }

    private static final class EnvelopeField implements DistanceField {
        private final FocusSet foci;
        private final boolean nearest;

        EnvelopeField(final FocusSet foci, final boolean nearest) {
            this.foci = foci;
            this.nearest = nearest;
        }

        @Override
        public double value(final double x, final double y) {
            if (foci.activeCount() == 0) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            boolean exactNeeded = false;
            boolean roundingSensitive = false;
            double result = nearest ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double candidate = foci.magnitudeDistanceApproximate(index, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                exactNeeded |= finitePoint && (!Double.isFinite(distance)
                        || !Double.isFinite(candidate)
                        || candidate == 0 && distance != 0);
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(candidate);
                result = nearest ? Math.min(result, candidate) : Math.max(result, candidate);
            }
            return exactNeeded || finitePoint && roundingSensitive && foci.tryConsumeExact()
                    ? ExactFieldMath.envelope(foci, x, y, nearest) : result;
        }
    }

    private static final class QuadraticField implements DistanceField {
        private final FocusSet foci;

        QuadraticField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            boolean exactNeeded = false;
            boolean roundingSensitive = false;
            double norm = 0;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                final double magnitude = foci.magnitudeDistanceApproximate(index, x, y);
                exactNeeded |= finitePoint && (!Double.isFinite(distance)
                        || magnitude == 0 && distance != 0);
                norm = Math.hypot(norm, magnitude);
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(magnitude)
                        || FieldMath.isMagnitudeRoundingSensitive(norm);
            }
            if (exactNeeded || finitePoint && !Double.isFinite(norm)) {
                return ExactFieldMath.quadraticMagnitudeNorm(foci, x, y);
            }
            return finitePoint && roundingSensitive && foci.tryConsumeExact()
                    ? ExactFieldMath.quadraticMagnitudeNorm(foci, x, y) : norm;
        }
    }

    private static final class PotentialField implements DistanceField {
        private final FocusSet foci;

        PotentialField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            if (foci.activeCount() == 0) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final double[] terms = FieldMath.scratch(foci.size());
            boolean positiveInfinity = false;
            boolean negativeInfinity = false;
            boolean positiveTerm = false;
            boolean negativeTerm = false;
            boolean exactNeeded = false;
            boolean roundingSensitive = false;
            double scale = 0;
            for (int index = 0; index < foci.size(); index++) {
                terms[index] = 0;
                final double weight = foci.weight(index);
                if (weight == 0) {
                    continue;
                }
                final double distance = foci.distance(index, x, y);
                if (Double.isNaN(distance)) {
                    return Double.NaN;
                }
                if (distance == 0) {
                    positiveInfinity |= weight > 0;
                    negativeInfinity |= weight < 0;
                    continue;
                }
                if (!Double.isFinite(distance)) {
                    exactNeeded |= finitePoint;
                    continue;
                }
                final double term = weight / distance;
                if (!Double.isFinite(term) || term == 0) {
                    exactNeeded |= finitePoint;
                    continue;
                }
                terms[index] = term;
                scale = Math.max(scale, Math.abs(term));
                positiveTerm |= term > 0;
                negativeTerm |= term < 0;
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(term);
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
            if (finitePoint && (exactNeeded
                    || roundingSensitive && foci.tryConsumeExact())) {
                return ExactFieldMath.potential(foci, x, y);
            }
            if (scale == 0) {
                return 0;
            }

            final FieldMath.CompensatedSum normalizedSum = new FieldMath.CompensatedSum();
            final FieldMath.CompensatedSum magnitudeSum = new FieldMath.CompensatedSum();
            int termCount = 0;
            for (int index = 0; index < foci.size(); index++) {
                final double term = terms[index];
                if (term == 0) {
                    continue;
                }
                final double normalizedTerm = term / scale;
                if (normalizedTerm == 0) {
                    return finitePoint ? ExactFieldMath.potential(foci, x, y) : 0;
                }
                normalizedSum.add(normalizedTerm);
                magnitudeSum.add(Math.abs(normalizedTerm));
                termCount++;
            }
            final double normalized = normalizedSum.value();
            final double magnitude = magnitudeSum.value();
            final boolean illConditioned = positiveTerm && negativeTerm
                    && FieldMath.cancellationUncertain(normalized, magnitude,
                            termCount, FieldMath.EXACT_CANCELLATION_RATIO);
            if (finitePoint && illConditioned && foci.tryConsumeExact()) {
                return ExactFieldMath.potential(foci, x, y);
            }
            final double result = scale * normalized;
            return !finitePoint || Double.isFinite(result) && (result != 0 || normalized == 0)
                    ? result : ExactFieldMath.potential(foci, x, y);
        }
    }

    private static final class GaussianField implements DistanceField {
        private final FocusSet foci;
        private final double sigma;

        GaussianField(final FocusSet foci, final double sigma) {
            this.foci = foci;
            this.sigma = sigma;
        }

        @Override
        public double value(final double x, final double y) {
            if (foci.activeCount() == 0) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final FieldMath.CompensatedSum sum = new FieldMath.CompensatedSum();
            final FieldMath.CompensatedSum magnitudes = new FieldMath.CompensatedSum();
            boolean positive = false;
            boolean negative = false;
            boolean exactNeeded = false;
            boolean roundingSensitive = false;
            for (int index = 0; index < foci.size(); index++) {
                final double weight = foci.weight(index);
                if (weight == 0) {
                    continue;
                }
                final double ordinaryDistance = foci.distance(index, x, y);
                final double ratio = foci.distanceRatio(index, x, y, sigma);
                if (Double.isNaN(ratio)) {
                    return Double.NaN;
                }
                exactNeeded |= finitePoint && !Double.isFinite(ordinaryDistance)
                        && Double.isFinite(foci.logDistance(index, x, y));
                final double exponent = -0.5 * ratio * ratio;
                final double kernel = Math.exp(exponent);
                double term = weight * kernel;
                if (term == 0 && weight != 0 && Double.isFinite(exponent)) {
                    term = FieldMath.multiplyFromLog(weight, exponent);
                    exactNeeded |= term == 0;
                }
                positive |= term > 0;
                negative |= term < 0;
                roundingSensitive |= FieldMath.isMagnitudeRoundingSensitive(term);
                magnitudes.add(Math.abs(term));
                sum.add(term);
            }
            final double result = sum.value();
            final boolean uncertain = roundingSensitive
                    || positive && negative
                            && FieldMath.cancellationUncertain(result, magnitudes.value(),
                                    foci.activeCount(), FieldMath.EXACT_CANCELLATION_RATIO);
            if (finitePoint && (exactNeeded || !Double.isFinite(result)
                    || uncertain && foci.tryConsumeExact())) {
                return ExactFieldMath.gaussian(foci, x, y, sigma);
            }
            return result;
        }
    }
}
