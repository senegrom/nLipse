package nlipse.math;

/** Scalar field evaluated in world coordinates. */
@FunctionalInterface
public interface DistanceField {
    double value(double x, double y);
}
