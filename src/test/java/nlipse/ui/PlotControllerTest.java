package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotControllerTest {
    @Test
    void retainsEveryRepresentableLevelChange() {
        assertFalse(PlotController.sameDouble(0, Double.MIN_VALUE));
        assertFalse(PlotController.sameDouble(1, Math.nextUp(1.0)));
        assertTrue(PlotController.sameDouble(-0.0, 0.0));
    }
}
