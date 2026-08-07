package nlipse.geometry;

import java.util.Objects;

/** Immutable two-dimensional point. */
public final class Point2 {
    private final double x;
    private final double y;

    public Point2(final double x, final double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Point coordinates must be finite");
        }
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Point2)) {
            return false;
        }
        final Point2 point = (Point2) other;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(point.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(point.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Double.doubleToLongBits(x), Double.doubleToLongBits(y));
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
