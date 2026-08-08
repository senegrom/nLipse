package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

class PlotRendererTest {
    @Test
    void rendersImageAndReusesFieldGridWhenOnlyDisplayLevelsChange() {
        final PlotRenderer renderer = new PlotRenderer();
        final PlotSnapshot first = snapshot(1, 3, 4, true);
        final RenderResult firstResult = renderer.render(
                new RenderRequest(first, 120, 90, RenderQuality.FULL), CancellationToken.NONE);

        final PlotSnapshot second = snapshot(1.25, 2.5, 7, false);
        final RenderResult secondResult = renderer.render(
                new RenderRequest(second, 120, 90, RenderQuality.FULL), CancellationToken.NONE);

        assertEquals(120, firstResult.image().getWidth());
        assertEquals(90, firstResult.image().getHeight());
        assertEquals(0xFF000000, firstResult.image().getRGB(10, 10) & 0xFF000000);
        assertTrue(firstResult.extrema().isPresent());
        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(120, secondResult.image().getWidth());
    }

    @Test
    void changedGeometryInvalidatesFieldCache() {
        final PlotRenderer renderer = new PlotRenderer();
        renderer.render(new RenderRequest(snapshot(1, 3, 4, true),
                80, 80, RenderQuality.FULL), CancellationToken.NONE);
        final PlotSnapshot moved = new PlotSnapshot(CurveType.LIPSE,
                List.of(new Focus(-0.5, 0, 1), new Focus(1, 0, 1)),
                1, 3, 4, new Viewport(-2, 2, -2, 2), true, true, true, false, 0);
        renderer.render(new RenderRequest(moved, 80, 80, RenderQuality.FULL), CancellationToken.NONE);

        assertEquals(2, renderer.getCacheMisses());
    }

    @Test
    void honoursCancellation() {
        final PlotRenderer renderer = new PlotRenderer();

        assertThrows(RenderCancelledException.class, () -> renderer.render(
                new RenderRequest(snapshot(1, 3, 4, true), 100, 100, RenderQuality.FULL),
                () -> true));
    }

    @Test
    void generatesLinearAndLogarithmicLevels() {
        assertEquals(2, PlotRenderer.levels(1, 3, 3, false)[1], 1e-12);
        assertEquals(10, PlotRenderer.levels(1, 100, 3, true)[1], 1e-12);
        assertEquals(1, PlotRenderer.levels(2, 2, 20, false).length);
        final double[] extreme = PlotRenderer.levels(
                -Double.MAX_VALUE, Double.MAX_VALUE, 3, false);
        assertEquals(-Double.MAX_VALUE, extreme[0]);
        assertEquals(0, extreme[1]);
        assertEquals(Double.MAX_VALUE, extreme[2]);
    }

    @Test
    void reportsWhenAFieldHasNoFiniteSamples() {
        final PlotSnapshot invalid = new PlotSnapshot(CurveType.LIPSE,
                List.of(new Focus(Double.MAX_VALUE, 0, Double.MAX_VALUE)),
                0, 1, 4, new Viewport(-1, 1, -1, 1),
                true, true, true, false, -1);

        final RenderResult result = new PlotRenderer().render(
                new RenderRequest(invalid, 40, 40, RenderQuality.FULL),
                CancellationToken.NONE);

        assertTrue(result.extrema().isEmpty());
        assertEquals(Color.WHITE.getRGB(), result.image().getRGB(2, 2));
    }

    private static PlotSnapshot snapshot(final double minimum, final double maximum,
            final int count, final boolean background) {
        return new PlotSnapshot(CurveType.LIPSE,
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                minimum, maximum, count, new Viewport(-2, 2, -2, 2),
                background, true, true, false, 0);
    }
}
