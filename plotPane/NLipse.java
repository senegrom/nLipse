/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public final class NLipse extends PlotDistanceCurve {
	public NLipse(final Point[] points, final double dist, final double[] weights) {
		super(points, dist, weights);
	}

	@Override
	public double getCumultDistance(final double x, final double y) {
		double distance = 0;
		for (int i = 0; i < n; i++)
			distance += weightedDistanceToFocus(i, x, y);
		return distance;
	}
}
