/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public final class NCassin extends PlotDistanceCurve {
	public NCassin(final Point[] points, final double dist, final double[] weights) {
		super(points, dist, weights);
	}

	@Override
	public double getCumultDistance(final double x, final double y) {
		double distance = 1;
		for (int i = 0; i < n; i++)
			distance *= Math.pow(distanceToFocus(i, x, y), weights[i]);
		return distance;
	}
}
