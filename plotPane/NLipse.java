/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public class NLipse extends PlotDistanceCurve {

	public NLipse() {
		super(nDef, distDef);
	}

	public NLipse(final double[] xcoords, final double[] ycoords) {
		super(xcoords, ycoords, distDef, defWeights(Math.min(xcoords.length, ycoords.length)));
	}

	public NLipse(final double[] xcoords, final double[] ycoords, final double dist) {
		super(xcoords, ycoords, dist, defWeights(Math.min(xcoords.length, ycoords.length)));
	}

	public NLipse(final double[] xcoords, final double[] ycoords, final double dist, final double[] ws) {
		super(xcoords, ycoords, dist, ws);
	}

	public NLipse(final int n) {
		super(n, distDef);
	}

	public NLipse(final int n, final double dist) {
		super(n, dist);
	}

	public NLipse(final Point[] points) {
		super(points, distDef, defWeights(points.length));
	}

	public NLipse(final Point[] points, final double dist) {
		super(points, dist, defWeights(points.length));
	}

	public NLipse(final Point[] points, final double dist, final double[] ws) {
		super(points, dist, ws);
	}

	@Override
	public final double getCumultDistance(final Point p) {
		return getCumultDistance(p.getX(), p.getY());
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
