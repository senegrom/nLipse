/**
 * @author CGH
 */

package plotPane;

import java.util.stream.IntStream;
import simpleGeom.Point;

public abstract class PlotDistanceCurve extends PlotCurve {
	protected final double		dist;
	protected final int			n;
	protected final Point[]		points;
	protected final double[]	fxCache, fyCache;
	protected final double[]	weights;

	public PlotDistanceCurve(final Point[] points, final double dist, final double[] ws) {
		this.points = points;
		this.dist = dist;
		this.weights = ws;
		this.n = points.length;
		this.fxCache = new double[n];
		this.fyCache = new double[n];
		for (int i = 0; i < n; i++) {
			fxCache[i] = points[i].getX();
			fyCache[i] = points[i].getY();
		}
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
		return dist;
	}

	public abstract double getCumultDistance(double x, double y);

	public final double getCumultDistance(final Point p) {
		return getCumultDistance(p.getX(), p.getY());
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
		return findExtremum(p, true);
	}

	public final Point getMinPoint(final PlotPane p) {
		return findExtremum(p, false);
	}

	private Point findExtremum(final PlotPane p, final boolean max) {
		final int dminX = -1, dmaxX = p.getXres() + 1;
		final int dminY = -1, dmaxY = p.getYres() + 1;
		final double sign = max ? 1 : -1;

		final double[][] colBest = IntStream.rangeClosed(dminX, dmaxX).parallel().mapToObj(x -> {
			double bd = -Double.MAX_VALUE;
			int by = dminY;
			final double wx = p.unfitx(x);
			for (int y = dminY; y <= dmaxY; y++) {
				final double d = sign * getCumultDistance(wx, p.unfity(y));
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
}
