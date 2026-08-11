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
        private final ThreadLocal<double[]> logarithmBuffer;

        CassiniField(final FocusSet foci) {
            this.foci = foci;
            logarithmBuffer = ThreadLocal.withInitial(() -> new double[foci.size()]);
        }

        @Override
        public double value(final double x, final double y) {
            final double weightScale = foci.maximumAbsoluteWeight();
            if (weightScale == 0) {
                return 1;
            }
            final double[] logarithms = logarithmBuffer.get();
            final FieldMath.CompensatedSum normalizedSum = new FieldMath.CompensatedSum();
            boolean exactNeeded = false;
            boolean zeroFactor = false;
            boolean infiniteFactor = false;
            for (int index = 0; index < foci.size(); index++) {
                final double weight = foci.weight(index);
                logarithms[index] = 0;
                if (weight == 0) {
                    continue;
                }
                final double logarithm = foci.logDistance(index, x, y);
                if (Double.isNaN(logarithm)) {
                    return Double.NaN;
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
                logarithms[index] = logarithm;
                final double normalizedWeight = weight / weightScale;
                if (normalizedWeight == 0 && logarithm != 0) {
                    exactNeeded = true;
                }
                normalizedSum.add(normalizedWeight * logarithm);
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
            double resultLogarithm = weightScale * normalizedSum.value();
            if (exactNeeded || !Double.isFinite(resultLogarithm)) {
                final double[] weights = new double[foci.size()];
                for (int index = 0; index < weights.length; index++) {
                    weights[index] = foci.weight(index);
                }
                resultLogarithm = ExactFieldMath.weightedLogSum(logarithms, weights);
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
            double result = nearest ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            for (int index = 0; index < foci.size(); index++) {
                if (!foci.isActive(index)) {
                    continue;
                }
                final double candidate = foci.magnitudeDistance(index, x, y);
                if (Double.isNaN(candidate)) {
                    return Double.NaN;
                }
                result = nearest ? Math.min(result, candidate) : Math.max(result, candidate);
            }
            return result;
        }
    }

    private static final class QuadraticField implements DistanceField {
        private final FocusSet foci;

        QuadraticField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            double norm = 0;
            for (int index = 0; index < foci.size(); index++) {
                if (foci.isActive(index)) {
                    norm = Math.hypot(norm, foci.magnitudeDistance(index, x, y));
                }
            }
            return norm;
        }
    }

    private static final class PotentialField implements DistanceField {
        private final FocusSet foci;

        PotentialField(final FocusSet foci) {
            this.foci = foci;
        }

        @Override
        public double value(final double x, final double y) {
            final double scale = foci.maximumAbsoluteWeight();
            if (scale == 0) {
                return 0;
            }
            final boolean finitePoint = Double.isFinite(x) && Double.isFinite(y);
            final FieldMath.CompensatedSum normalizedSum = new FieldMath.CompensatedSum();
            boolean positiveInfinity = false;
            boolean negativeInfinity = false;
            boolean exactNeeded = false;
            for (int index = 0; index < foci.size(); index++) {
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
                    if (finitePoint) {
                        exactNeeded = true;
                    }
                    continue;
                }
                final double normalizedWeight = weight / scale;
                final double term = normalizedWeight / distance;
                if (!Double.isFinite(term)
                        || (term == 0 && normalizedWeight != 0)) {
                    exactNeeded = finitePoint;
                } else {
                    normalizedSum.add(term);
                }
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
            if (exactNeeded) {
                return ExactFieldMath.potential(foci, x, y);
            }
            final double result = scale * normalizedSum.value();
            return Double.isFinite(result) || !finitePoint ? result
                    : ExactFieldMath.potential(foci, x, y);
        }
    }

    private static final class GaussianField implements DistanceField {
        private final FocusSet foci;
        private final double sigma;
        private final ThreadLocal<double[]> kernelBuffer;

        GaussianField(final FocusSet foci, final double sigma) {
            this.foci = foci;
            this.sigma = sigma;
            kernelBuffer = ThreadLocal.withInitial(() -> new double[foci.size()]);
        }

        @Override
        public double value(final double x, final double y) {
            final double scale = foci.maximumAbsoluteWeight();
            if (scale == 0) {
                return 0;
            }
            final double[] kernels = kernelBuffer.get();
            final double[] weights = new double[foci.size()];
            final FieldMath.CompensatedSum normalizedSum = new FieldMath.CompensatedSum();
            boolean positive = false;
            boolean negative = false;
            boolean exactNeeded = false;
            for (int index = 0; index < foci.size(); index++) {
                final double weight = foci.weight(index);
                weights[index] = weight;
                kernels[index] = 0;
                if (weight == 0) {
                    continue;
                }
                final double ratio = foci.distanceRatio(index, x, y, sigma);
                if (Double.isNaN(ratio)) {
                    return Double.NaN;
                }
                final double kernel = ratio > Math.sqrt(FieldMath.LOG_MAX_VALUE * 2)
                        ? 0 : Math.exp(-0.5 * ratio * ratio);
                kernels[index] = kernel;
                positive |= weight > 0 && kernel != 0;
                negative |= weight < 0 && kernel != 0;
                final double normalizedWeight = weight / scale;
                final double term = normalizedWeight * kernel;
                if ((normalizedWeight == 0 && kernel != 0)
                        || (!Double.isFinite(term))) {
                    exactNeeded = true;
                } else {
                    normalizedSum.add(term);
                }
            }
            final double result = scale * normalizedSum.value();
            if (exactNeeded || !Double.isFinite(result)
                    || (result == 0 && positive && negative)) {
                return ExactFieldMath.weightedDoubleSum(kernels, weights);
            }
            return result;
        }
    }
}
