package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        final PlotSnapshot first = snapshot(1, 3, 4, true, 0);
        final RenderResult firstResult = renderer.render(
                new RenderRequest(first, 120, 90, RenderQuality.FULL), CancellationToken.NONE);

        final PlotSnapshot second = snapshot(1.25, 2.5, 7, false, 0);
        final RenderResult secondResult = renderer.render(
                new RenderRequest(second, 120, 90, RenderQuality.FULL), CancellationToken.NONE);

        assertEquals(120, firstResult.image().getWidth());
        assertEquals(90, firstResult.image().getHeight());
        assertTrue(firstResult.extrema().isPresent());
        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(2, renderer.getLayerCacheMisses());
        assertEquals(120, secondResult.image().getWidth());
    }

    @Test
    void selectionOnlyChangeReusesMarkerFreeLayer() {
        final PlotRenderer renderer = new PlotRenderer();
        final RenderResult first = renderer.render(new RenderRequest(
                snapshot(1, 3, 8, true, 0), 180, 120, RenderQuality.FULL),
                CancellationToken.NONE);
        final RenderResult second = renderer.render(new RenderRequest(
                snapshot(1, 3, 8, true, 1), 180, 120, RenderQuality.FULL),
                CancellationToken.NONE);

        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(1, renderer.getLayerCacheMisses());
        assertEquals(1, renderer.getLayerCacheHits());
        assertNotEquals(first.image().getRGB(45, 60), second.image().getRGB(45, 60));
    }

    @Test
    void identicalPreviewReusesTheCachedFullQualityLayer() {
        final PlotRenderer renderer = new PlotRenderer();
        final PlotSnapshot snapshot = snapshot(1, 3, 10, true, 0);
        renderer.render(new RenderRequest(snapshot, 200, 140, RenderQuality.FULL),
                CancellationToken.NONE);
        renderer.render(new RenderRequest(snapshot, 200, 140, RenderQuality.PREVIEW),
                CancellationToken.NONE);

        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(0, renderer.getDerivedGridHits());
        assertEquals(1, renderer.getFullQualityPreviewHits());
    }

    @Test
    void changedLevelsPreviewIsDerivedFromCachedFullResolutionGrid() {
        final PlotRenderer renderer = new PlotRenderer();
        renderer.render(new RenderRequest(snapshot(1, 3, 10, true, 0),
                200, 140, RenderQuality.FULL), CancellationToken.NONE);
        renderer.render(new RenderRequest(snapshot(1.1, 2.9, 7, true, 0),
                200, 140, RenderQuality.PREVIEW), CancellationToken.NONE);

        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(1, renderer.getDerivedGridHits());
        assertEquals(0, renderer.getFullQualityPreviewHits());
    }

    @Test
    void displayStyleChangeReusesCachedContourTopology() {
        final PlotRenderer renderer = new PlotRenderer();
        final PlotSnapshot withBackground = snapshot(1, 3, 8, true, 0);
        final PlotSnapshot withoutBackground = snapshot(1, 3, 8, false, 0);

        renderer.render(new RenderRequest(withBackground, 180, 120, RenderQuality.FULL),
                CancellationToken.NONE);
        renderer.render(new RenderRequest(withoutBackground, 180, 120, RenderQuality.FULL),
                CancellationToken.NONE);

        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(1, renderer.getContourCacheMisses());
        assertEquals(1, renderer.getContourCacheHits());
        assertEquals(2, renderer.getLayerCacheMisses());
    }

    @Test
    void integerPixelPanReusesOverlappingWorldSamples() {
        final int width = 129;
        final int height = 97;
        final int panX = 9;
        final int panY = -6;
        final PlotRenderer renderer = new PlotRenderer();
        final PlotSnapshot initial = snapshot(1, 3, 8, true, 0);
        renderer.render(new RenderRequest(initial, width, height, RenderQuality.FULL),
                CancellationToken.NONE);
        final long sampledBeforePan = renderer.getSampledWorldValues();
        final Viewport pannedViewport = initial.viewport().panPixels(
                panX, panY, width, height);
        final PlotSnapshot panned = new PlotSnapshot(initial.curveType(), initial.foci(),
                initial.distanceMin(), initial.distanceMax(), initial.curveCount(), pannedViewport,
                initial.showBackground(), initial.showExtrema(), initial.antiAlias(),
                initial.logSpacing(), initial.selectedFocusIndex());

        renderer.render(new RenderRequest(panned, width, height, RenderQuality.FULL),
                CancellationToken.NONE);

        final long overlap = (long) (width - Math.abs(panX))
                * (height - Math.abs(panY));
        final long newlyExposed = (long) width * height - overlap;
        assertEquals(newlyExposed, renderer.getSampledWorldValues() - sampledBeforePan);
        assertTrue(renderer.getReusedWorldSamples() >= overlap);
        assertTrue(renderer.getWorldTileHits() > 0);
    }

    @Test
    void changedGeometryInvalidatesFieldCache() {
        final PlotRenderer renderer = new PlotRenderer();
        renderer.render(new RenderRequest(snapshot(1, 3, 4, true, 0),
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
                new RenderRequest(snapshot(1, 3, 4, true, 0), 100, 100, RenderQuality.FULL),
                () -> true));
    }

    @Test
    void generatesStableUniqueLinearAndLogarithmicLevels() {
        assertEquals(2, PlotRenderer.levels(1, 3, 3, false)[1], 1e-12);
        assertEquals(10, PlotRenderer.levels(1, 100, 3, true)[1], 1e-12);
        assertEquals(1, PlotRenderer.levels(2, 2, 20, false).length);
        final double[] extreme = PlotRenderer.levels(
                -Double.MAX_VALUE, Double.MAX_VALUE, 3, false);
        assertEquals(-Double.MAX_VALUE, extreme[0]);
        assertEquals(0, extreme[1]);
        assertEquals(Double.MAX_VALUE, extreme[2]);
        assertEquals(2, PlotRenderer.levels(1, Math.nextUp(1.0), 200, false).length);
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

    @Test
    void rendersEveryCurveFamily() {
        final PlotRenderer renderer = new PlotRenderer();
        final List<Focus> foci = List.of(
                new Focus(-0.7, 0.1, 1),
                new Focus(0.8, -0.2, -0.75),
                new Focus(0.1, 1.1, 0.4));

        for (final CurveType type : CurveType.values()) {
            final double minimum = type == CurveType.POTENTIAL ? -3 : 0.05;
            final double maximum = type == CurveType.POTENTIAL ? 3 : 8;
            final PlotSnapshot snapshot = new PlotSnapshot(type, foci,
                    minimum, maximum, 9, new Viewport(-2, 2, -2, 2),
                    true, true, true, type.defaultLogSpacing(), -1);

            final RenderResult result = renderer.render(
                    new RenderRequest(snapshot, 96, 72, RenderQuality.FULL),
                    CancellationToken.NONE);

            assertEquals(96, result.image().getWidth());
            assertEquals(72, result.image().getHeight());
            assertTrue(result.extrema().isPresent(), type.name());
        }
        assertEquals(CurveType.values().length, renderer.getCacheMisses());
    }

    private static PlotSnapshot snapshot(final double minimum, final double maximum,
            final int count, final boolean background, final int selectedFocus) {
        return new PlotSnapshot(CurveType.LIPSE,
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                minimum, maximum, count, new Viewport(-2, 2, -2, 2),
                background, true, true, false, selectedFocus);
    }
}
