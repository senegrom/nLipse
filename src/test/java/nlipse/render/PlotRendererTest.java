package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void changedFamilyParameterInvalidatesFieldAndContourCaches() {
        final PlotRenderer renderer = new PlotRenderer();
        final List<Focus> foci = List.of(new Focus(-1, 0, 1), new Focus(1, 0, 2));
        final PlotSnapshot arithmetic = new PlotSnapshot(CurveType.POWER_MEAN, 1, foci,
                0.1, 4, 8, new Viewport(-2, 2, -2, 2),
                true, true, true, false, false, -1);
        final PlotSnapshot quadratic = new PlotSnapshot(CurveType.POWER_MEAN, 2, foci,
                0.1, 4, 8, new Viewport(-2, 2, -2, 2),
                true, true, true, false, false, -1);

        renderer.render(new RenderRequest(arithmetic, 100, 80, RenderQuality.FULL),
                CancellationToken.NONE);
        renderer.render(new RenderRequest(quadratic, 100, 80, RenderQuality.FULL),
                CancellationToken.NONE);

        assertEquals(2, renderer.getCacheMisses());
        assertEquals(2, renderer.getContourCacheMisses());
        assertEquals(0, renderer.getCacheHits());
    }

    @Test
    void changedGeometryInvalidatesFieldCache() {
        final PlotRenderer renderer = new PlotRenderer();
        renderer.render(new RenderRequest(snapshot(1, 3, 4, true, 0),
                80, 80, RenderQuality.FULL), CancellationToken.NONE);
        final PlotSnapshot moved = new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-0.5, 0, 1), new Focus(1, 0, 1)),
                1, 3, 4, new Viewport(-2, 2, -2, 2), true, true, true, false, false, 0);
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

        final double logarithmicMinimum = 3.1270742916138413e-84;
        final double logarithmicMaximum = 1.2345678901234567e123;
        final double[] logarithmic = PlotRenderer.levels(
                logarithmicMinimum, logarithmicMaximum, 17, true);
        assertEquals(Double.doubleToLongBits(logarithmicMinimum),
                Double.doubleToLongBits(logarithmic[0]));
        assertEquals(Double.doubleToLongBits(logarithmicMaximum),
                Double.doubleToLongBits(logarithmic[logarithmic.length - 1]));
    }

    @Test
    void reportsWhenAFieldHasNoFiniteSamples() {
        final PlotSnapshot invalid = new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(Double.MAX_VALUE, 0, Double.MAX_VALUE)),
                0, 1, 4, new Viewport(-1, 1, -1, 1),
                true, true, true, false, true, -1);

        final RenderResult result = new PlotRenderer().render(
                new RenderRequest(invalid, 40, 40, RenderQuality.FULL),
                CancellationToken.NONE);

        assertTrue(result.extrema().isEmpty());
        assertEquals(Color.WHITE.getRGB(), result.image().getRGB(2, 2));
    }

    @Test
    void legendOverlayDrawsWithoutInvalidatingTheStaticLayerCache() {
        final int width = 200;
        final int height = 140;
        final PlotRenderer renderer = new PlotRenderer();
        final PlotSnapshot plain = snapshot(1, 3, 8, true, -1);
        final PlotSnapshot legended = new PlotSnapshot(plain.curveType(),
                plain.familyParameter(), plain.foci(),
                plain.distanceMin(), plain.distanceMax(), plain.curveCount(), plain.viewport(),
                plain.showBackground(), plain.showExtrema(), plain.antiAlias(),
                plain.logSpacing(), true, plain.selectedFocusIndex());

        final RenderResult without = renderer.render(
                new RenderRequest(plain, width, height, RenderQuality.FULL),
                CancellationToken.NONE);
        final RenderResult with = renderer.render(
                new RenderRequest(legended, width, height, RenderQuality.FULL),
                CancellationToken.NONE);

        // The legend box occupies the top-right corner and only that corner.
        assertNotEquals(without.image().getRGB(width - 20, 20),
                with.image().getRGB(width - 20, 20));
        assertEquals(without.image().getRGB(10, height / 2),
                with.image().getRGB(10, height / 2));
        // The overlay is drawn per request; grid and static layer stay shared.
        assertEquals(1, renderer.getCacheMisses());
        assertEquals(1, renderer.getCacheHits());
        assertEquals(1, renderer.getLayerCacheMisses());
        assertEquals(1, renderer.getLayerCacheHits());
    }

    @Test
    void fullyOffscreenFocusMarkersDoNotWrapOntoTheCanvas() {
        final PlotSnapshot visibleOnly = new PlotSnapshot(CurveType.NEAREST,
                CurveType.NEAREST.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, 2, 4,
                new Viewport(-1, 1, -1, 1),
                false, false, true, false, false, -1);
        final PlotSnapshot withFarDisabledFocus = new PlotSnapshot(CurveType.NEAREST,
                CurveType.NEAREST.defaultParameter(),
                List.of(new Focus(0, 0, 1), new Focus(Double.MAX_VALUE, 0, 0)),
                0, 2, 4, new Viewport(-1, 1, -1, 1),
                false, false, true, false, false, -1);
        final int width = 101;
        final int height = 101;

        final RenderResult expected = new PlotRenderer().render(
                new RenderRequest(visibleOnly, width, height, RenderQuality.FULL),
                CancellationToken.NONE);
        final RenderResult actual = new PlotRenderer().render(
                new RenderRequest(withFarDisabledFocus, width, height, RenderQuality.FULL),
                CancellationToken.NONE);

        assertArrayEquals(expected.image().getRGB(0, 0, width, height, null, 0, width),
                actual.image().getRGB(0, 0, width, height, null, 0, width));
    }

    @Test
    void legendSubsamplingKeepsBothEndpointsAndTheRowCap() {
        assertArrayEquals(new int[]{0, 1, 2}, PlotRenderer.legendLevelIndices(3));
        final int[] subsampled = PlotRenderer.legendLevelIndices(200);
        assertEquals(12, subsampled.length);
        assertEquals(0, subsampled[0]);
        assertEquals(199, subsampled[subsampled.length - 1]);
        for (int index = 1; index < subsampled.length; index++) {
            assertTrue(subsampled[index] > subsampled[index - 1]);
        }
        assertEquals(0, PlotRenderer.legendLevelIndices(0).length);
        assertArrayEquals(new int[]{0, 50, 100, 149, 199},
                PlotRenderer.legendLevelIndices(200, 5));
        assertArrayEquals(new int[]{199}, PlotRenderer.legendLevelIndices(200, 1));
        assertEquals(0, PlotRenderer.legendLevelIndices(10, 0).length);
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
            final PlotSnapshot snapshot = new PlotSnapshot(type, type.defaultParameter(), foci,
                    minimum, maximum, 9, new Viewport(-2, 2, -2, 2),
                    true, true, true, type.defaultLogSpacing(), false, -1);

            final RenderResult result = renderer.render(
                    new RenderRequest(snapshot, 96, 72, RenderQuality.FULL),
                    CancellationToken.NONE);

            assertEquals(96, result.image().getWidth());
            assertEquals(72, result.image().getHeight());
            assertTrue(result.extrema().isPresent(), type.name());
        }
        assertEquals(CurveType.values().length, renderer.getCacheMisses());
    }

    @Test
    void rendersMinimumSubnormalViewportAtFullAndPreviewQuality() {
        final PlotSnapshot tiny = new PlotSnapshot(CurveType.NEAREST,
                CurveType.NEAREST.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, Double.MIN_VALUE, 2,
                new Viewport(0, Double.MIN_VALUE, 0, Double.MIN_VALUE),
                true, true, true, false, false, -1);
        final PlotRenderer renderer = new PlotRenderer();

        for (final RenderQuality quality : RenderQuality.values()) {
            final RenderResult result = renderer.render(
                    new RenderRequest(tiny, 5, 4, quality), CancellationToken.NONE);
            assertEquals(5, result.image().getWidth());
            assertEquals(4, result.image().getHeight());
            assertTrue(result.extrema().isPresent());
        }
    }

    @Test
    void rendersAViewportSpanningTheFullFiniteDoubleRange() {
        final double maximum = Double.MAX_VALUE;
        final PlotSnapshot fullRange = new PlotSnapshot(CurveType.NEAREST,
                CurveType.NEAREST.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, maximum, 3,
                new Viewport(-maximum, maximum, -maximum, maximum),
                false, true, true, false, false, -1);
        final PlotRenderer renderer = new PlotRenderer();

        for (final int size : new int[] {2, 4}) {
            final RenderResult result = renderer.render(
                    new RenderRequest(fullRange, size, size, RenderQuality.FULL),
                    CancellationToken.NONE);
            assertEquals(size, result.image().getWidth());
            assertEquals(size, result.image().getHeight());
            assertEquals(size > 2, result.extrema().isPresent());
        }
    }

    private static PlotSnapshot snapshot(final double minimum, final double maximum,
            final int count, final boolean background, final int selectedFocus) {
        return new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                minimum, maximum, count, new Viewport(-2, 2, -2, 2),
                background, true, true, false, false, selectedFocus);
    }
}
