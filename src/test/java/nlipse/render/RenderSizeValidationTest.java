package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

class RenderSizeValidationTest {
    @Test
    void rejectsOverBudgetPixelCountsBeforeAllocation() {
        final int side = (int) Math.sqrt(RenderDimensions.MAX_PIXEL_COUNT) + 1;
        final PlotSnapshot snapshot = snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> new RenderRequest(snapshot, side, side, RenderQuality.FULL));
        assertThrows(IllegalArgumentException.class,
                () -> RenderDimensions.checkedPixelCount(side, side, 1));
        assertThrows(IllegalArgumentException.class,
                () -> FieldGrid.sample((x, y) -> x + y, snapshot.viewport(),
                        side, side, 1, CancellationToken.NONE));
    }

    @Test
    void acceptsOrdinaryDimensionsAndRejectsUndersizedOnes() {
        final PlotSnapshot snapshot = snapshot();
        assertDoesNotThrow(() -> new RenderRequest(snapshot, 2, 2, RenderQuality.FULL));
        assertEquals(4, RenderDimensions.checkedPixelCount(2, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RenderDimensions.checkedPixelCount(0, 2, 1));
    }

    @Test
    void pixelLimitTracksSmallAndLargeHeapBudgets() {
        assertEquals(4, RenderDimensions.maximumPixelCount(1));
        assertEquals(16, RenderDimensions.maximumPixelCount(16L * 32));
        assertEquals(64_000_000,
                RenderDimensions.maximumPixelCount(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> RenderDimensions.maximumPixelCount(0));
    }

    @Test
    void exactRequirementSurvivesSequencing() {
        final RenderRequest ordinary = new RenderRequest(
                snapshot(), 2, 2, RenderQuality.FULL);
        assertFalse(ordinary.exactRequired());

        final RenderRequest exact = ordinary.requiringExact().withSequence(37);
        assertTrue(exact.exactRequired());
        assertEquals(37, exact.sequence());
        assertEquals(ordinary.snapshot(), exact.snapshot());
    }

    private static PlotSnapshot snapshot() {
        return new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, 1, 1,
                new Viewport(-1, 1, -1, 1), false, false, true, false, false, -1);
    }

    @Test
    void packageConvenienceConstructorsRejectMissingPackages() {
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(
                image, 0, RenderQuality.FULL, Optional.empty(), 0,
                (RenderPackage) null));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(
                image, 0, RenderQuality.FULL, Optional.empty(), 0, true,
                (RenderPackage) null));
    }

    @Test
    void rejectsNegativeRenderMetadata() {
        final PlotSnapshot snapshot = snapshot();
        assertThrows(IllegalArgumentException.class, () -> new RenderRequest(
                snapshot, 2, 2, RenderQuality.FULL, -1, false));
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(
                image, -1, RenderQuality.FULL, Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(
                image, 0, RenderQuality.FULL, Optional.empty(), -1));
    }
}
