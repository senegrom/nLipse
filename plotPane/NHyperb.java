/**
 * @author CGH
 */

package plotPane;

import simpleGeom.Point;

public class NHyperb extends PlotDistanceCurve {

	public NHyperb() {
		super(nDef, distDef);
	}

	public NHyperb(final double[] xcoords, final double[] ycoords) {
		super(xcoords, ycoords, distDef, defWeights(Math.min(xcoords.length, ycoords.length)));
	}

	public NHyperb(final double[] xcoords, final double[] ycoords, final double dist) {
		super(xcoords, ycoords, dist, defWeights(Math.min(xcoords.length, ycoords.length)));
	}

	public NHyperb(final double[] xcoords, final double[] ycoords, final double dist, final double[] ws) {
		super(xcoords, ycoords, dist, ws);
	}

	public NHyperb(final int n) {
		super(n, distDef);
	}

	public NHyperb(final int n, final double dist) {
		super(n, dist);
	}

	public NHyperb(final Point[] points) {
		super(points, distDef, defWeights(points.length));
	}

	public NHyperb(final Point[] points, final double dist) {
		super(points, dist, defWeights(points.length));
	}

	public NHyperb(final Point[] points, final double dist, final double[] ws) {
		super(points, dist, ws);
	}

	@Override
	public final double getCumultDistance(final Point p) {
		return getCumultDistance(p.getX(), p.getY());
	}

	@Override
	public final double getCumultDistance(final double x, final double y) {
		if (n < 2)
			return 0;
		final double[] dists = new double[n];
		for (int i = 0; i < n; i++) {
			final double dx = x - fxCache[i];
			final double dy = y - fyCache[i];
			dists[i] = Math.sqrt(dx * dx + dy * dy);
		}
		double d = 0;
		for (int i = 0; i < n; i++)
			for (int j = 0; j < i; j++)
				d += Math.abs(dists[i] - dists[j]);
		return 2 * d / (n * (n - 1));
	}
}
