/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public final class NHyperb extends PlotDistanceCurve {
	private static final ThreadLocal<double[]> DISTANCE_BUFFER = ThreadLocal.withInitial(() -> new double[0]);

	public NHyperb(final Point[] points, final double dist, final double[] weights) {
		super(points, dist, weights);
	}

	@Override
	public double getCumultDistance(final double x, final double y) {
		if (n < 2)
			return 0;

		double[] distances = DISTANCE_BUFFER.get();
		if (distances.length < n) {
			distances = new double[n];
			DISTANCE_BUFFER.set(distances);
		}
		for (int i = 0; i < n; i++)
			distances[i] = weightedDistanceToFocus(i, x, y);

		double differenceSum = 0;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < i; j++)
				differenceSum += Math.abs(distances[i] - distances[j]);
		}
		return 2 * differenceSum / ((double) n * (n - 1));
	}
}
