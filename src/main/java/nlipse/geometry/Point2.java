package nlipse.geometry;

/** Immutable two-dimensional point. */
public record Point2(double x, double y) {
    public Point2 {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Point coordinates must be finite");
        }
    }
}
