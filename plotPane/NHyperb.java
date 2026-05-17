/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public class NHyperb extends PlotDistanceCurve {

	public NHyperb(final Point[] points, final double dist, final double[] ws) {
		super(points, dist, ws);
	}

	@Override
	public final double getCumultDistance(final double x, final double y) {
		if (n < 2)
			return 0;
		final double[] dists = new double[n];
		for (int i = 0; i < n; i++) {
			final double dx = x - fxCache[i];
			final double dy = y - fyCache[i];
			dists[i] = Math.sqrt(dx * dx + dy * dy) * weights[i];
		}
		double d = 0;
		for (int i = 0; i < n; i++)
			for (int j = 0; j < i; j++)
				d += Math.abs(dists[i] - dists[j]);
		return 2 * d / (n * (n - 1));
	}
}
