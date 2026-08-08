package nlipse.model;

/** Immutable weighted focus point. */
public record Focus(double x, double y, double weight) {
    public Focus {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Focus coordinates must be finite");
        }
        if (!Double.isFinite(weight)) {
            throw new IllegalArgumentException("Focus weight must be finite");
        }
    }

    public Focus withPosition(final double newX, final double newY) {
        return new Focus(newX, newY, weight);
    }

    public Focus withWeight(final double newWeight) {
        return new Focus(x, y, newWeight);
    }
}
