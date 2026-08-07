package Main;

import java.util.ArrayList;
import java.util.List;

public class PlotConfig {
	public enum CurveType {
		LIPSE("n-Ellipse (sum)"),
		CASSIN("Cassini oval (product)"),
		HYPERB("n-Hyperbola (avg diff)");

		private final String displayName;

		CurveType(final String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	public static class FocusSpec {
		public double x;
		public double y;
		public double weight;

		public FocusSpec(final double x, final double y, final double weight) {
			this.x = x;
			this.y = y;
			this.weight = weight;
		}
	}

	public CurveType curveType = CurveType.LIPSE;
	public List<FocusSpec> foci = new ArrayList<>();
	public double dmin = 14.5;
	public double dmax = 45;
	public int nCurves = 12;
	public double xmin = -3;
	public double xmax = 3;
	public double ymin = -3;
	public double ymax = 3;
	public boolean showBackground = true;
	public boolean showMinMax = true;
	public boolean antiAlias = true;
	public boolean logSpacing = false;

	public static PlotConfig defaults() {
		final PlotConfig c = new PlotConfig();
		c.foci.add(new FocusSpec(2, 0, 1));
		c.foci.add(new FocusSpec(0, 1, 1));
		c.foci.add(new FocusSpec(-1, -1.5, 1));
		return c;
	}
}
