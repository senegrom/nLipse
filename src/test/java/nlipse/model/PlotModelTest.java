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
        assertNotEquals(before.getFoci().get(0), after.getFoci().get(0));
        assertEquals(2, before.getFoci().get(0).getX(), 1e-12);
    }

    @Test
    void removingSelectedFocusSelectsItsSuccessor() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        model.setSelectedFocusIndex(1);
        assertTrue(model.removeFocus(1));
        assertEquals(1, model.getSelectedFocusIndex());
        assertEquals(2, model.getFocusCount());
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
}
