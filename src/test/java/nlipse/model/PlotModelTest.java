package nlipse.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotModelTest {
    @Test
    void snapshotsAreIndependentOfLaterMutations() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        final PlotSnapshot before = model.snapshot();
        model.setFocusPosition(0, 99, 100);
        final PlotSnapshot after = model.snapshot();
        assertNotEquals(before.foci().get(0), after.foci().get(0));
        assertEquals(2, before.foci().get(0).x(), 1e-12);
    }

    @Test
    void removingSelectedFocusClearsSelection() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        model.setSelectedFocusIndex(1);
        assertTrue(model.removeFocus(1));
        assertEquals(-1, model.getSelectedFocusIndex());
        assertEquals(2, model.getFocusCount());
    }

    @Test
    void removingBelowSelectionShiftsSelectionDown() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        model.setSelectedFocusIndex(2);
        assertTrue(model.removeFocus(0));
        assertEquals(1, model.getSelectedFocusIndex());
    }

    @Test
    void lastFocusCannotBeRemoved() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        model.removeFocus(2);
        model.removeFocus(1);
        assertEquals(1, model.getFocusCount());
        org.junit.jupiter.api.Assertions.assertFalse(model.removeFocus(0));
    }

    @Test
    void invalidDistanceRangesAreRejected() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        assertThrows(IllegalArgumentException.class, () -> model.setDistanceRange(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> model.setDistanceRange(Double.NaN, 1));
    }

    @Test
    void familyParametersAreValidatedAndIncludedInSnapshots() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        model.setCurveType(CurveType.POWER_MEAN);
        assertEquals(CurveType.POWER_MEAN.defaultParameter(), model.getFamilyParameter(), 0);

        model.setFamilyParameter(Double.NEGATIVE_INFINITY);
        assertEquals(Double.NEGATIVE_INFINITY, model.snapshot().familyParameter());

        model.setCurveType(CurveType.GAUSSIAN);
        assertEquals(CurveType.GAUSSIAN.defaultParameter(), model.getFamilyParameter(), 0);
        assertThrows(IllegalArgumentException.class, () -> model.setFamilyParameter(0));
    }

}
