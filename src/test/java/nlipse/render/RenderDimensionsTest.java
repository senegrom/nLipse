package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

class RenderDimensionsTest {
    private static final String PROPERTY = "nlipse.maxRenderPixels";

    @Test
    void rejectsDimensionsWhoseProductCannotBeAllocatedSafely() {
        assertThrows(IllegalArgumentException.class, () -> new RenderRequest(
                snapshot(), Integer.MAX_VALUE, Integer.MAX_VALUE, RenderQuality.FULL));
        assertThrows(IllegalArgumentException.class, () -> new RenderRequest(
                snapshot(), 1, 100, RenderQuality.FULL));
    }

    @Test
    void configuredPixelLimitIsAppliedBeforeAnyAllocation() {
        synchronized (RenderDimensions.class) {
            final String previous = System.getProperty(PROPERTY);
            try {
                System.setProperty(PROPERTY, "100");
                assertDoesNotThrow(() -> new RenderRequest(
                        snapshot(), 10, 10, RenderQuality.FULL));
                assertThrows(IllegalArgumentException.class, () -> new RenderRequest(
                        snapshot(), 11, 10, RenderQuality.FULL));
            } finally {
                if (previous == null) {
                    System.clearProperty(PROPERTY);
                } else {
                    System.setProperty(PROPERTY, previous);
                }
            }
        }
    }

    private static PlotSnapshot snapshot() {
        return new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, 1, 1,
                new Viewport(-1, 1, -1, 1), false, false, true, false, false, -1);
    }
}
