/**
 * @author CGH
 */

package plotPane;

import java.util.stream.IntStream;
import simpleGeom.Point;

public abstract class PlotDistanceCurve extends PlotCurve {
	protected final static double	distDef	= 3;
	protected final static int		nDef	= 3;

	public final static double[] defWeights(final int n) {
		final double[] w = new double[n];
		for (int i = 0; i < w.length; i++)
			w[i] = 1;
		return w;
	}

	protected double	dist;
	protected int		n;
	protected Point[]	points;
	protected double[]	fxCache, fyCache;
	protected double[]	weights;

	public PlotDistanceCurve() {
		this(nDef, distDef);
	}

	public PlotDistanceCurve(final double[] xcoords, final double[] ycoords) {
		this(xcoords, ycoords, distDef, defWeights(Math.min(xcoords.length, ycoords.length)));
	}

	public PlotDistanceCurve(final double[] xcoords, final double[] ycoords, final double dist) {
		this(xcoords, ycoords, dist, defWeights(Math.min(xcoords.length, ycoords.length)));
	}

	public PlotDistanceCurve(final double[] xcoords, final double[] ycoords, final double dist, final double[] ws) {
		final int l = Math.min(xcoords.length, ycoords.length);
		final Point[] p = new Point[l];
		for (int i = 0; i < l; i++)
			p[i] = new Point(xcoords[i], ycoords[i]);
		setFocusPoints(p);
		setDist(dist);
		setWeights(ws);
	}

	public PlotDistanceCurve(final int n) {
		this(n, distDef);
	}

	public PlotDistanceCurve(final int n, final double dist) {
		setN(n);
		setDist(dist);
		setWeights();
	}

	public PlotDistanceCurve(final Point[] points) {
		this(points, distDef, defWeights(points.length));
	}

	public PlotDistanceCurve(final Point[] points, final double dist) {
		this(points, dist, defWeights(points.length));
	}

	public PlotDistanceCurve(final Point[] points, final double dist, final double[] ws) {
		setFocusPoints(points);
		setDist(dist);
		setWeights(ws);
	}

	@Override
	public final double fColor(final double x, final double y) {
		return getCumultDistance(x, y);
	}

	@Override
	public final double fLeft(final double x, final double y) {
		return getCumultDistance(x, y);
	}

	@Override
	public final double fRight(final double x, final double y) {
		return getDist();
	}

	public double getCumultDistance(final double x, final double y) {
		return getCumultDistance(new Point(x, y));
	}

	public abstract double getCumultDistance(Point p);

	public final double getDist() {
		return dist;
	}

	public final Point[] getFocusPoints() {
		return points;
	}

	@Override
	public final double getLocalFColorMax(final PlotPane p) {
		return getCumultDistance(getMaxPoint(p));
	}

	@Override
	public final double getLocalFColorMin(final PlotPane p) {
		return getCumultDistance(getMinPoint(p));
	}

	public final Point getMaxPoint(final PlotPane p) {
		final int dminX = -1;
		final int dmaxX = p.getXres() + 1;
		final int dminY = -1;
		final int dmaxY = p.getYres() + 1;

		final double[][] colBest = IntStream.rangeClosed(dminX, dmaxX).parallel().mapToObj(x -> {
			double bd = -Double.MAX_VALUE;
			int by = dminY;
			final double wx = p.unfitx(x);
			for (int y = dminY; y <= dmaxY; y++) {
				final double d = getCumultDistance(wx, p.unfity(y));
				if (d > bd) {
					bd = d;
					by = y;
				}
			}
			return new double[]{bd, x, by };
		}).toArray(double[][]::new);

		double bestD = -Double.MAX_VALUE;
		int bestX = dminX, bestY = dminY;
		for (final double[] r : colBest)
			if (r[0] > bestD) {
				bestD = r[0];
				bestX = (int) r[1];
				bestY = (int) r[2];
			}

		return new Point(p.unfitx(bestX), p.unfity(bestY));
	}

	public final Point[] getMinMaxPoint(final PlotPane p) {
		return new Point[]{getMinPoint(p), getMaxPoint(p) };
	}

	public final Point getMinPoint(final PlotPane p) {
		final int dminX = -1;
		final int dmaxX = p.getXres() + 1;
		final int dminY = -1;
		final int dmaxY = p.getYres() + 1;

		final double[][] colBest = IntStream.rangeClosed(dminX, dmaxX).parallel().mapToObj(x -> {
			double bd = Double.MAX_VALUE;
			int by = dminY;
			final double wx = p.unfitx(x);
			for (int y = dminY; y <= dmaxY; y++) {
				final double d = getCumultDistance(wx, p.unfity(y));
				if (d < bd) {
					bd = d;
					by = y;
				}
			}
			return new double[]{bd, x, by };
		}).toArray(double[][]::new);

		double bestD = Double.MAX_VALUE;
		int bestX = dminX, bestY = dminY;
		for (final double[] r : colBest)
			if (r[0] < bestD) {
				bestD = r[0];
				bestX = (int) r[1];
				bestY = (int) r[2];
			}

		return new Point(p.unfitx(bestX), p.unfity(bestY));
	}

	public final int getN() {
		return n;
	}

	public final double getWeight(final int i) {
		return getWeights()[i];
	}

	public final double[] getWeights() {
		return weights;
	}

	public final void setDist(final double dist) {
		this.dist = dist;
	}

	public final void setFocusPoints(final Point[] points) {
		this.points = points;
		n = points.length;
		fxCache = new double[n];
		fyCache = new double[n];
		for (int i = 0; i < n; i++) {
			fxCache[i] = points[i].getX();
			fyCache[i] = points[i].getY();
		}
	}

	public final void setN(final int n) {
		this.n = n;
		final Point[] p = new Point[n];
		for (int i = 0; i < n; i++)
			p[i] = new Point();
		setFocusPoints(p);
	}

	public final void setWeights() {
		setWeights(defWeights(n));
	}

	public final void setWeights(final double[] ws) {
		weights = ws;
	}
}
