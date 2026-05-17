/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public class NCassin extends PlotDistanceCurve {

	public NCassin(final Point[] points, final double dist, final double[] ws) {
		super(points, dist, ws);
	}

	@Override
	public final double getCumultDistance(final double x, final double y) {
		double d = 1;
		for (int i = 0; i < n; i++) {
			final double dx = x - fxCache[i];
			final double dy = y - fyCache[i];
			d *= Math.pow(Math.sqrt(dx * dx + dy * dy), weights[i]);
		}
		return d;
	}
}
