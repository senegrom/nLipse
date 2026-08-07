package plotPane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotCurveTest {
	@Test
	void detectsCheckerboardSaddleCrossing() {
		assertTrue(PlotCurve.cellContainsZeroContour(1, -1, -1, 1));
	}

	@Test
	void detectsAnyFiniteOppositeSignsAndExactZeros() {
		assertTrue(PlotCurve.cellContainsZeroContour(-1, 2, 3, 4));
		assertTrue(PlotCurve.cellContainsZeroContour(0, 1, 1, 1));
	}

	@Test
	void rejectsCellsWithoutACrossingOrWithUndefinedValues() {
		assertFalse(PlotCurve.cellContainsZeroContour(1, 2, 3, 4));
		assertFalse(PlotCurve.cellContainsZeroContour(-1, -2, -3, -4));
		assertFalse(PlotCurve.cellContainsZeroContour(Double.NaN, -1, 1, 1));
		assertFalse(PlotCurve.cellContainsZeroContour(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 1));
	}
}
