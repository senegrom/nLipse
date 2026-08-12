package nlipse.math;

import java.util.List;
import nlipse.model.Focus;

/** Compact immutable focal data plus overflow-safe distance primitives. */
final class FocusSet {
    private final double[] xs;
    private final double[] ys;
    private final double[] weights;
    private final int activeCount;
    private final double maximumAbsoluteWeight;
    private final ExactBudget exactBudget;
    private volatile ExactFocusData exactData;

    private FocusSet(final double[] xs, final double[] ys, final double[] weights,
            final int activeCount, final double maximumAbsoluteWeight,
            final ExactBudget exactBudget) {
        this.xs = xs;
        this.ys = ys;
        this.weights = weights;
        this.activeCount = activeCount;
        this.maximumAbsoluteWeight = maximumAbsoluteWeight;
        this.exactBudget = exactBudget;
    }

    static FocusSet from(final List<Focus> foci) {
        return from(foci, ExactBudget.unlimited());
    }

    static FocusSet from(final List<Focus> foci, final ExactBudget exactBudget) {
        if (exactBudget == null) {
            throw new IllegalArgumentException("Exact-evaluation budget is required");
        }
        final int size = foci.size();
        final double[] xs = new double[size];
        final double[] ys = new double[size];
        final double[] weights = new double[size];
        int activeCount = 0;
        double maximumAbsoluteWeight = 0;
        for (int index = 0; index < size; index++) {
            final Focus focus = foci.get(index);
            if (focus == null) {
                throw new IllegalArgumentException("Focus " + (index + 1) + " is null");
            }
            xs[index] = focus.x();
            ys[index] = focus.y();
            weights[index] = focus.weight();
            if (focus.weight() != 0) {
                activeCount++;
                maximumAbsoluteWeight = Math.max(maximumAbsoluteWeight,
                        Math.abs(focus.weight()));
            }
        }
        return new FocusSet(xs, ys, weights, activeCount, maximumAbsoluteWeight, exactBudget);
    }

    int size() {
        return xs.length;
    }

    int activeCount() {
        return activeCount;
    }

    boolean isActive(final int index) {
        return weights[index] != 0;
    }

    double x(final int index) {
        return xs[index];
    }

    double y(final int index) {
        return ys[index];
    }

    double weight(final int index) {
        return weights[index];
    }

    double maximumAbsoluteWeight() {
        return maximumAbsoluteWeight;
    }

    boolean tryConsumeExact() {
        return exactBudget.tryConsume();
    }

    ExactFocusData exactData() {
        ExactFocusData result = exactData;
        if (result == null) {
            synchronized (this) {
                result = exactData;
                if (result == null) {
                    result = new ExactFocusData(this);
                    exactData = result;
                }
            }
        }
        return result;
    }

    double distance(final int index, final double x, final double y) {
        return Math.hypot(x - xs[index], y - ys[index]);
    }

    double logDistance(final int index, final double x, final double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.NaN;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return Double.POSITIVE_INFINITY;
        }
        final double dx = x - xs[index];
        final double dy = y - ys[index];
        final double ordinary = Math.hypot(dx, dy);
        if (ordinary > 0 && Double.isFinite(ordinary)) {
            return Math.log(ordinary);
        }
        if (ordinary == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        final double logDx = logAbsoluteDifference(x, xs[index]);
        final double logDy = logAbsoluteDifference(y, ys[index]);
        final double maximum = Math.max(logDx, logDy);
        if (maximum == Double.NEGATIVE_INFINITY) {
            return maximum;
        }
        final double minimum = Math.min(logDx, logDy);
        return maximum + 0.5 * Math.log1p(Math.exp(2 * (minimum - maximum)));
    }

    double magnitudeDistance(final int index, final double x, final double y) {
        final double absoluteWeight = Math.abs(weights[index]);
        if (absoluteWeight == 0) {
            return 0;
        }
        final double ordinaryDistance = distance(index, x, y);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return ordinaryDistance * absoluteWeight;
        }

        final double scaledX = scaledAbsoluteDifference(x, xs[index], absoluteWeight);
        final double scaledY = scaledAbsoluteDifference(y, ys[index], absoluteWeight);
        final double scaled = Math.hypot(scaledX, scaledY);
        if (scaled != 0 || ordinaryDistance == 0) {
            return scaled;
        }

        // Each weighted Cartesian component can round to zero while their
        // combined Euclidean norm still rounds to the smallest subnormal.
        final double direct = ordinaryDistance * absoluteWeight;
        if (direct != 0) {
            return direct;
        }
        return ExactFieldMath.magnitudeDistance(this, index, x, y);
    }

    double signedDistance(final int index, final double x, final double y) {
        final double weight = weights[index];
        return weight == 0 ? 0
                : Math.copySign(magnitudeDistance(index, x, y), weight);
    }

    double logMagnitudeDistance(final int index, final double x, final double y) {
        final double absoluteWeight = Math.abs(weights[index]);
        if (absoluteWeight == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        final double magnitude = magnitudeDistance(index, x, y);
        if (magnitude > 0 && Double.isFinite(magnitude)) {
            return Math.log(magnitude);
        }
        return logDistance(index, x, y) + Math.log(absoluteWeight);
    }

    double magnitudeDistanceRatio(final int index, final double x, final double y,
            final double positiveScale) {
        final double absoluteWeight = Math.abs(weights[index]);
        if (absoluteWeight == 0) {
            return 0;
        }
        final double relativeWeight = absoluteWeight / positiveScale;
        if (relativeWeight > 0 && Double.isFinite(relativeWeight)
                && Double.isFinite(x) && Double.isFinite(y)) {
            final double scaledX = scaledAbsoluteDifference(x, xs[index], relativeWeight);
            final double scaledY = scaledAbsoluteDifference(y, ys[index], relativeWeight);
            final double scaled = Math.hypot(scaledX, scaledY);
            if (scaled != 0 || distance(index, x, y) == 0) {
                return scaled;
            }
            final double direct = distance(index, x, y) * relativeWeight;
            if (direct != 0) {
                return direct;
            }
        }
        return FieldMath.expFromLog(
                logMagnitudeDistance(index, x, y) - Math.log(positiveScale));
    }

    double distanceRatio(final int index, final double x, final double y,
            final double positiveScale) {
        final double reciprocal = 1.0 / positiveScale;
        if (reciprocal > 0 && Double.isFinite(reciprocal)
                && Double.isFinite(x) && Double.isFinite(y)) {
            final double ordinary = distance(index, x, y);
            final double scaledX = scaledAbsoluteDifference(x, xs[index], reciprocal);
            final double scaledY = scaledAbsoluteDifference(y, ys[index], reciprocal);
            final double scaled = Math.hypot(scaledX, scaledY);
            if (scaled != 0 || ordinary == 0) {
                return scaled;
            }
            final double direct = ordinary * reciprocal;
            if (direct != 0) {
                return direct;
            }
        }
        final double distance = distance(index, x, y);
        final double ratio = distance / positiveScale;
        if (Double.isFinite(ratio)) {
            return ratio;
        }
        return FieldMath.expFromLog(logDistance(index, x, y) - Math.log(positiveScale));
    }

    private static double scaledAbsoluteDifference(final double first,
            final double second, final double scale) {
        final double difference = first - second;
        final double result = Math.abs(difference) * scale;
        if (Double.isFinite(result)
                && (result != 0 || difference == 0 || scale == 0)) {
            return result;
        }
        if (Double.isFinite(first) && Double.isFinite(second)) {
            if (Double.isInfinite(difference)) {
                final double firstPart = Math.abs(first) * scale;
                final double secondPart = Math.abs(second) * scale;
                final double sum = firstPart + secondPart;
                if (Double.isFinite(sum)) {
                    return sum;
                }
            }
            return ExactFieldMath.scaledAbsoluteDifference(first, second, scale);
        }
        return result;
    }

    private static double logAbsoluteDifference(final double first, final double second) {
        final double difference = first - second;
        if (Double.isFinite(difference)) {
            return difference == 0 ? Double.NEGATIVE_INFINITY : Math.log(Math.abs(difference));
        }
        // A subtraction of two finite doubles overflows only when their signs differ.
        final double firstMagnitude = Math.abs(first);
        final double secondMagnitude = Math.abs(second);
        final double maximum = Math.max(firstMagnitude, secondMagnitude);
        final double minimum = Math.min(firstMagnitude, secondMagnitude);
        return Math.log(maximum) + Math.log1p(minimum / maximum);
    }
}
