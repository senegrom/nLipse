/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public class NLipse extends PlotDistanceCurve {

	public NLipse(final Point[] points, final double dist, final double[] ws) {
		super(points, dist, ws);
	}

	@Override
	public final double getCumultDistance(final double x, final double y) {
		double d = 0;
		for (int i = 0; i < n; i++) {
			final double dx = x - fxCache[i];
			final double dy = y - fyCache[i];
			d += Math.sqrt(dx * dx + dy * dy) * weights[i];
		}
		return d;
	}
}
