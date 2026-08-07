/**
 * @author CGH
 */

package plotPane;

import java.util.stream.IntStream;
import simpleGeom.Point;

public abstract class PlotDistanceCurve extends PlotCurve {
	public static final class Extrema {
		private final Point minPoint;
		private final double minValue;
		private final Point maxPoint;
		private final double maxValue;

		private Extrema(final Point minPoint, final double minValue, final Point maxPoint, final double maxValue) {
			this.minPoint = minPoint;
			this.minValue = minValue;
			this.maxPoint = maxPoint;
			this.maxValue = maxValue;
		}

		public Point getMinPoint() {
			return minPoint;
		}

		public double getMinValue() {
			return minValue;
		}

		public Point getMaxPoint() {
			return maxPoint;
		}

		public double getMaxValue() {
			return maxValue;
		}
	}

	private static final class PixelExtrema {
		private final boolean valid;
		private final double minValue;
		private final int minX;
		private final int minY;
		private final double maxValue;
		private final int maxX;
		private final int maxY;

		private PixelExtrema(final boolean valid, final double minValue, final int minX, final int minY,
				final double maxValue, final int maxX, final int maxY) {
			this.valid = valid;
			this.minValue = minValue;
			this.minX = minX;
			this.minY = minY;
			this.maxValue = maxValue;
			this.maxX = maxX;
			this.maxY = maxY;
		}

		private PixelExtrema combine(final PixelExtrema other) {
			if (!valid)
				return other;
			if (!other.valid)
				return this;

			final boolean otherHasLowerMin = other.minValue < minValue
					|| other.minValue == minValue && comesFirst(other.minX, other.minY, minX, minY);
			final boolean otherHasHigherMax = other.maxValue > maxValue
					|| other.maxValue == maxValue && comesFirst(other.maxX, other.maxY, maxX, maxY);

			return new PixelExtrema(true,
					otherHasLowerMin ? other.minValue : minValue,
					otherHasLowerMin ? other.minX : minX,
					otherHasLowerMin ? other.minY : minY,
					otherHasHigherMax ? other.maxValue : maxValue,
					otherHasHigherMax ? other.maxX : maxX,
					otherHasHigherMax ? other.maxY : maxY);
		}

		private static boolean comesFirst(final int x1, final int y1, final int x2, final int y2) {
			return x1 < x2 || x1 == x2 && y1 < y2;
		}
	}

	protected final double dist;
	protected final int n;
	protected final double[] fxCache;
	protected final double[] fyCache;
	protected final double[] weights;

	protected PlotDistanceCurve(final Point[] points, final double dist, final double[] weights) {
		if (points == null || weights == null)
			throw new IllegalArgumentException("Focus points and weights must not be null");
		if (points.length == 0)
			throw new IllegalArgumentException("At least one focus point is required");
		if (points.length != weights.length)
			throw new IllegalArgumentException("Focus point and weight counts must match");
		if (!Double.isFinite(dist))
			throw new IllegalArgumentException("Curve distance must be finite");

		this.dist = dist;
		this.n = points.length;
		this.fxCache = new double[n];
		this.fyCache = new double[n];
		this.weights = weights.clone();
		for (int i = 0; i < n; i++) {
			if (points[i] == null)
				throw new IllegalArgumentException("Focus point " + (i + 1) + " must not be null");
			final double x = points[i].getX();
			final double y = points[i].getY();
			final double weight = this.weights[i];
			if (!Double.isFinite(x) || !Double.isFinite(y))
				throw new IllegalArgumentException("Focus point " + (i + 1) + " coordinates must be finite");
			if (!Double.isFinite(weight))
				throw new IllegalArgumentException("Focus point " + (i + 1) + " weight must be finite");
			fxCache[i] = x;
			fyCache[i] = y;
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
		if (p == null)
			throw new IllegalArgumentException("Point must not be null");
		return getCumultDistance(p.getX(), p.getY());
	}

	protected final double distanceToFocus(final int index, final double x, final double y) {
		return Math.hypot(x - fxCache[index], y - fyCache[index]);
	}

	protected final double weightedDistanceToFocus(final int index, final double x, final double y) {
		return distanceToFocus(index, x, y) * weights[index];
	}

	@Override
	public final double getLocalFColorMax(final PlotPane p) {
		return getExtrema(p).getMaxValue();
	}

	@Override
	public final double getLocalFColorMin(final PlotPane p) {
		return getExtrema(p).getMinValue();
	}

	@Override
	public final double[] getLocalFColorRange(final PlotPane p) {
		final Extrema extrema = getExtrema(p);
		return new double[]{extrema.getMinValue(), extrema.getMaxValue()};
	}

	public final Point getMaxPoint(final PlotPane p) {
		return getExtrema(p).getMaxPoint();
	}

	public final Point getMinPoint(final PlotPane p) {
		return getExtrema(p).getMinPoint();
	}

	public final Extrema getExtrema(final PlotPane p) {
		if (p == null)
			throw new IllegalArgumentException("Plot pane must not be null");

		final int minX = 0;
		final int maxX = p.getXres() - 1;
		final int minY = 0;
		final int maxY = p.getYres() - 1;

		final PixelExtrema result = IntStream.rangeClosed(minX, maxX).parallel().mapToObj(x -> {
			double columnMin = Double.POSITIVE_INFINITY;
			double columnMax = Double.NEGATIVE_INFINITY;
			int columnMinY = minY;
			int columnMaxY = minY;
			boolean valid = false;
			final double worldX = p.unfitx(x);
			for (int y = minY; y <= maxY; y++) {
				final double value = getCumultDistance(worldX, p.unfity(y));
				if (!Double.isFinite(value))
					continue;
				if (!valid || value < columnMin) {
					columnMin = value;
					columnMinY = y;
				}
				if (!valid || value > columnMax) {
					columnMax = value;
					columnMaxY = y;
				}
				valid = true;
			}
			return new PixelExtrema(valid, columnMin, x, columnMinY, columnMax, x, columnMaxY);
		}).reduce(PixelExtrema::combine).orElse(new PixelExtrema(false, 0, minX, minY, 1, minX, minY));

		if (!result.valid) {
			final Point fallback = new Point(p.getXmin(), p.getYmin());
			return new Extrema(fallback, 0, fallback, 1);
		}

		return new Extrema(new Point(p.unfitx(result.minX), p.unfity(result.minY)), result.minValue,
				new Point(p.unfitx(result.maxX), p.unfity(result.maxY)), result.maxValue);
	}
}
