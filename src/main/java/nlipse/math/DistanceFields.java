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
        };
    }

    private abstract static class FocusField implements DistanceField {
        protected final double[] xs;
        protected final double[] ys;
        protected final double[] weights;

        FocusField(final double[] xs, final double[] ys, final double[] weights) {
            this.xs = xs.clone();
            this.ys = ys.clone();
            this.weights = weights.clone();
        }

        protected final double distance(final int index, final double x, final double y) {
            return Math.hypot(x - xs[index], y - ys[index]);
        }
    }

    private static final class SumField extends FocusField {
        SumField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
        }

        @Override
        public double value(final double x, final double y) {
            double sum = 0;
            for (int i = 0; i < xs.length; i++) {
                sum += distance(i, x, y) * weights[i];
            }
            return sum;
        }
    }

    private static final class CassiniField extends FocusField {
        CassiniField(final double[] xs, final double[] ys, final double[] weights) {
            super(xs, ys, weights);
        }

        @Override
        public double value(final double x, final double y) {
            double product = 1;
            for (int i = 0; i < xs.length; i++) {
                product *= Math.pow(distance(i, x, y), weights[i]);
            }
            return product;
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
            for (int i = 0; i < xs.length; i++) {
                distances[i] = distance(i, x, y) * weights[i];
            }
            final double sum;
            if (xs.length < SORT_THRESHOLD) {
                double pairwiseSum = 0;
                for (int i = 0; i < xs.length; i++) {
                    for (int j = 0; j < i; j++) {
                        pairwiseSum += Math.abs(distances[i] - distances[j]);
                    }
                }
                sum = pairwiseSum;
            } else {
                Arrays.sort(distances);
                double sortedSum = 0;
                for (int i = 0; i < distances.length; i++) {
                    sortedSum += (2.0 * i - distances.length + 1) * distances[i];
                }
                sum = sortedSum;
            }
            return 2 * sum / ((double) xs.length * (xs.length - 1));
        }
    }
}
