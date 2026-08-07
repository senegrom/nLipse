package nlipse.model;

import java.util.Objects;

/** Immutable weighted focus point. */
public final class Focus {
    private final double x;
    private final double y;
    private final double weight;

    public Focus(final double x, final double y, final double weight) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Focus coordinates must be finite");
        }
        if (!Double.isFinite(weight)) {
            throw new IllegalArgumentException("Focus weight must be finite");
        }
        this.x = x;
        this.y = y;
        this.weight = weight;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWeight() {
        return weight;
    }

    public Focus withPosition(final double newX, final double newY) {
        return new Focus(newX, newY, weight);
    }

    public Focus withWeight(final double newWeight) {
        return new Focus(x, y, newWeight);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Focus)) {
            return false;
        }
        final Focus focus = (Focus) other;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(focus.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(focus.y)
                && Double.doubleToLongBits(weight) == Double.doubleToLongBits(focus.weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Double.doubleToLongBits(x), Double.doubleToLongBits(y),
                Double.doubleToLongBits(weight));
    }
}
