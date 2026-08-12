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
        // Signed zero has no geometric or weight meaning here. Canonicalizing it
        // avoids duplicate cache/config identities for identical foci.
        x = x == 0 ? 0 : x;
        y = y == 0 ? 0 : y;
        weight = weight == 0 ? 0 : weight;
    }

    public Focus withPosition(final double newX, final double newY) {
        return new Focus(newX, newY, weight);
    }

    public Focus withWeight(final double newWeight) {
        return new Focus(x, y, newWeight);
    }
}
