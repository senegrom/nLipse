package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

class RenderPackageTest {
    @Test
    void packageDefensivelyCopiesLevelsAndColours() {
        final RenderPackage rendered = render(snapshot(-1), 180, 120)
                .renderPackage().orElseThrow();
        final double originalLevel = rendered.level(0);
        final Color originalColor = rendered.levelColor(0);
        final double[] levels = {originalLevel};
        final Color[] colors = {originalColor};
        final ContourGeometry contours = ContourGeometry.trace(
                FieldGrid.sample((x, y) -> x, snapshot(-1).viewport(), 2, 2, 1,
                        CancellationToken.NONE),
                (x, y) -> x, snapshot(-1).viewport(), levels, CancellationToken.NONE);
        final RenderPackage completed = new RenderPackage(snapshot(-1), 2, 2,
                RenderQuality.FULL, levels, colors, Optional.empty(), contours);

        levels[0] = originalLevel + 100;
        colors[0] = Color.MAGENTA;

        assertEquals(originalLevel, completed.level(0));
        assertEquals(originalColor, completed.levelColor(0));
        assertEquals(completed.levelCount(), completed.contours().levelCount());
        assertTrue(rendered.extrema().isPresent());
    }

    @Test
    void selectionOnlyRedrawReusesTopologyButProducesASeparatePackage() {
        final PlotRenderer renderer = new PlotRenderer();
        final RenderPackage first = renderer.render(
                new RenderRequest(snapshot(0), 180, 120, RenderQuality.FULL),
                CancellationToken.NONE).renderPackage().orElseThrow();
        final RenderPackage second = renderer.render(
                new RenderRequest(snapshot(1), 180, 120, RenderQuality.FULL),
                CancellationToken.NONE).renderPackage().orElseThrow();

        assertNotEquals(first.snapshot().selectedFocusIndex(),
                second.snapshot().selectedFocusIndex());
        assertSame(first.contours(), second.contours());
        assertEquals(first.levelCount(), second.levelCount());
    }

    private static RenderResult render(final PlotSnapshot snapshot,
            final int width, final int height) {
        return new PlotRenderer().render(
                new RenderRequest(snapshot, width, height, RenderQuality.FULL),
                CancellationToken.NONE);
    }

    private static PlotSnapshot snapshot(final int selected) {
        return new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                1.25, 4, 8, new Viewport(-2, 2, -2, 2),
                true, true, true, false, true, selected);
    }
}
