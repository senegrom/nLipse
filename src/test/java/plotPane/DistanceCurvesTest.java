package plotPane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import simpleGeom.Point;

class DistanceCurvesTest {
	private static final double EPSILON = 1e-12;

	@Test
	void nEllipseUsesWeightedSumOfDistances() {
		final Point[] points = {new Point(0, 0), new Point(3, 4)};
		final NLipse curve = new NLipse(points, 1, new double[]{2, 0.5});

		assertEquals(2.5, curve.getCumultDistance(0, 0), EPSILON);
	}

	@Test
	void cassiniUsesWeightedProductOfDistances() {
		final Point[] points = {new Point(0, 0), new Point(3, 4)};
		final NCassin curve = new NCassin(points, 1, new double[]{1, 1});

		assertEquals(12, curve.getCumultDistance(0, 4), EPSILON);
		assertEquals(0, curve.getCumultDistance(0, 0), EPSILON);
	}

	@Test
	void nHyperbolaUsesAveragePairwiseWeightedDifference() {
		final Point[] points = {new Point(0, 0), new Point(3, 0), new Point(0, 4)};
		final NHyperb curve = new NHyperb(points, 1, new double[]{1, 2, 0.5});

		assertEquals(4, curve.getCumultDistance(0, 0), EPSILON);
	}

	@Test
	void oneFocusHyperbolaIsZeroEverywhere() {
		final NHyperb curve = new NHyperb(new Point[]{new Point(1, 2)}, 0, new double[]{3});

		assertEquals(0, curve.getCumultDistance(100, -50), EPSILON);
	}

	@Test
	void distanceCalculationDoesNotOverflowForLargeFiniteCoordinates() {
		final NLipse curve = new NLipse(new Point[]{new Point(1e308, 0)}, 0, new double[]{1});
		final double value = curve.getCumultDistance(0, 0);

		assertTrue(Double.isFinite(value));
		assertEquals(1e308, value, 1e292);
	}

	@Test
	void finiteSignedAndZeroWeightsRemainSupported() {
		final Point[] points = {new Point(0, 0), new Point(2, 0)};
		final NLipse curve = new NLipse(points, 0, new double[]{-1, 0});

		assertEquals(-1, curve.getCumultDistance(1, 0), EPSILON);
	}

	@Test
	void constructorDefensivelyCopiesWeights() {
		final double[] weights = {2};
		final NLipse curve = new NLipse(new Point[]{new Point(0, 0)}, 0, weights);
		weights[0] = 100;

		assertEquals(2, curve.getCumultDistance(1, 0), EPSILON);
	}

	@Test
	void constructorRejectsMalformedDefinitions() {
		final Point[] point = {new Point(0, 0)};

		assertThrows(IllegalArgumentException.class, () -> new NLipse(null, 0, new double[]{1}));
		assertThrows(IllegalArgumentException.class, () -> new NLipse(new Point[0], 0, new double[0]));
		assertThrows(IllegalArgumentException.class, () -> new NLipse(point, 0, new double[]{1, 2}));
		assertThrows(IllegalArgumentException.class, () -> new NLipse(new Point[]{null}, 0, new double[]{1}));
		assertThrows(IllegalArgumentException.class,
				() -> new NLipse(new Point[]{new Point(Double.NaN, 0)}, 0, new double[]{1}));
		assertThrows(IllegalArgumentException.class, () -> new NLipse(point, 0, new double[]{Double.NaN}));
		assertThrows(IllegalArgumentException.class, () -> new NLipse(point, 0, new double[]{Double.POSITIVE_INFINITY}));
		assertThrows(IllegalArgumentException.class, () -> new NLipse(point, Double.NaN, new double[]{1}));
	}

	@Test
	void extremaSearchReturnsVisibleGridExtremaInOneResult() {
		final NLipse curve = new NLipse(new Point[]{new Point(0, 0)}, 0, new double[]{1});
		final PlotPane pane = new PlotPane(-1, 1, -1, 1, 3, 3);

		final PlotDistanceCurve.Extrema extrema = curve.getExtrema(pane);

		assertEquals(0, extrema.getMinValue(), EPSILON);
		assertEquals(0, extrema.getMinPoint().getX(), EPSILON);
		assertEquals(0, extrema.getMinPoint().getY(), EPSILON);
		assertEquals(Math.sqrt(2), extrema.getMaxValue(), EPSILON);
	}
}
