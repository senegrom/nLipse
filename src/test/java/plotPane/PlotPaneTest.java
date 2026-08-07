package plotPane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlotPaneTest {
	private static final double EPSILON = 1e-12;

	@Test
	void mapsPlotBoundsToActualFirstAndLastPixels() {
		final PlotPane pane = new PlotPane(-3, 3, -2, 2, 101, 81);

		assertEquals(0, pane.fitx(-3), EPSILON);
		assertEquals(100, pane.fitx(3), EPSILON);
		assertEquals(0, pane.fity(2), EPSILON);
		assertEquals(80, pane.fity(-2), EPSILON);
		assertEquals(-3, pane.unfitx(0), EPSILON);
		assertEquals(3, pane.unfitx(100), EPSILON);
		assertEquals(2, pane.unfity(0), EPSILON);
		assertEquals(-2, pane.unfity(80), EPSILON);
	}

	@Test
	void coordinateTransformsRoundTrip() {
		final PlotPane pane = new PlotPane(-10, 7, -4, 13, 257, 193);

		assertEquals(2.25, pane.unfitx(pane.fitx(2.25)), EPSILON);
		assertEquals(-1.75, pane.unfity(pane.fity(-1.75)), EPSILON);
	}

	@Test
	void rejectsInvalidRangesAndResolutions() {
		assertThrows(IllegalArgumentException.class, () -> new PlotPane(0, 0, -1, 1, 10, 10));
		assertThrows(IllegalArgumentException.class, () -> new PlotPane(-1, 1, 2, 1, 10, 10));
		assertThrows(IllegalArgumentException.class, () -> new PlotPane(-1, 1, -1, 1, 1, 10));
		assertThrows(IllegalArgumentException.class, () -> new PlotPane(Double.NaN, 1, -1, 1, 10, 10));
	}
}
