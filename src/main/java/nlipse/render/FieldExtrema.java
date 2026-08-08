package nlipse.render;

import java.util.Objects;
import nlipse.geometry.Point2;

/** Finite extrema and their sampled locations for a scalar field. */
public record FieldExtrema(
        double minimum,
        double maximum,
        Point2 minimumPoint,
        Point2 maximumPoint) {

    public FieldExtrema {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Field extrema must be finite and ordered");
        }
        Objects.requireNonNull(minimumPoint, "minimumPoint");
        Objects.requireNonNull(maximumPoint, "maximumPoint");
    }
}
