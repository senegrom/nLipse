package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import nlipse.geometry.Point2;
import nlipse.render.FieldExtrema;
import nlipse.render.RenderQuality;

class PlotControllerTest {
    private static final FieldExtrema EXTREMA = new FieldExtrema(10, 20,
            new Point2(0, 0), new Point2(1, 1));

    @Test
    void retainsEveryRepresentableLevelChange() {
        assertFalse(PlotController.sameDouble(0, Double.MIN_VALUE));
        assertFalse(PlotController.sameDouble(1, Math.nextUp(1.0)));
        assertTrue(PlotController.sameDouble(-0.0, 0.0));
    }

    @Test
    void onlyExactFullRendersMayMutateTrustedRangeState() {
        assertFalse(PlotController.trustsRangeExtrema(RenderQuality.PREVIEW, false));
        assertFalse(PlotController.trustsRangeExtrema(RenderQuality.FULL, true));
        assertTrue(PlotController.trustsRangeExtrema(RenderQuality.FULL, false));
    }

    @Test
    void initialSliderDomainRemainsOrderedAtTheNegativeFiniteLimit() {
        final double maximum = PlotController.initialFullMaximum(
                -Double.MAX_VALUE, -Double.MAX_VALUE);

        assertTrue(maximum > -Double.MAX_VALUE);
        assertTrue(Double.isFinite(maximum));
    }

    @Test
    void untrustedExtremaDoNotChangeExactRangeState() {
        final PlotController.RangeResolution resolution = PlotController.resolveRange(
                -5, 5, 1, 2, PlotController.RangeAdjustment.AUTO_FIT,
                Optional.of(EXTREMA), false);

        assertEquals(-5, resolution.fullMin());
        assertEquals(5, resolution.fullMax());
        assertEquals(1, resolution.levelMin());
        assertEquals(2, resolution.levelMax());
        assertFalse(resolution.rangeChanged());
        assertTrue(resolution.adjustmentDeferred());
    }

    @Test
    void deferredRangeAdjustmentRetriesAfterASettledLimitedFullRender() {
        final PlotController.RangeResolution deferred = PlotController.resolveRange(
                -5, 5, 1, 2, PlotController.RangeAdjustment.CLAMP,
                Optional.of(EXTREMA), false);

        assertFalse(PlotController.requiresExactRangeRetry(RenderQuality.PREVIEW, deferred));
        assertTrue(PlotController.requiresExactRangeRetry(RenderQuality.FULL, deferred));

        final PlotController.RangeResolution noAdjustment = PlotController.resolveRange(
                -5, 5, 1, 2, PlotController.RangeAdjustment.NONE,
                Optional.of(EXTREMA), false);
        assertFalse(PlotController.requiresExactRangeRetry(
                RenderQuality.FULL, noAdjustment));
    }

    @Test
    void exactExtremaApplyDeferredAutoFit() {
        final PlotController.RangeResolution resolution = PlotController.resolveRange(
                -5, 5, 1, 2, PlotController.RangeAdjustment.AUTO_FIT,
                Optional.of(EXTREMA), true);

        assertEquals(10, resolution.fullMin());
        assertEquals(20, resolution.fullMax());
        assertEquals(10.5, resolution.levelMin());
        assertEquals(19.5, resolution.levelMax());
        assertTrue(resolution.rangeChanged());
        assertFalse(resolution.adjustmentDeferred());
    }

    @Test
    void exactRenderWithoutFiniteSamplesConsumesPendingAdjustmentSafely() {
        final PlotController.RangeResolution resolution = PlotController.resolveRange(
                -5, 5, 1, 2, PlotController.RangeAdjustment.CLAMP,
                Optional.empty(), true);

        assertEquals(-5, resolution.fullMin());
        assertEquals(5, resolution.fullMax());
        assertEquals(1, resolution.levelMin());
        assertEquals(2, resolution.levelMax());
        assertFalse(resolution.rangeChanged());
        assertFalse(resolution.adjustmentDeferred());
    }

    @Test
    void exactExtremaDoNotAutoFitManualLevelsWithoutAPendingAdjustment() {
        final PlotController.RangeResolution resolution = PlotController.resolveRange(
                -5, 5, -20, -10, PlotController.RangeAdjustment.NONE,
                Optional.of(EXTREMA), true);

        assertEquals(10, resolution.fullMin());
        assertEquals(20, resolution.fullMax());
        assertEquals(-20, resolution.levelMin());
        assertEquals(-10, resolution.levelMax());
        assertFalse(resolution.rangeChanged());
        assertFalse(resolution.adjustmentDeferred());
    }

    /**
     * A clamp starts from the requested levels, not the previous display, so
     * zooming in (which narrows the displayed levels to the visible field) and
     * back out restores them. Intersecting the display each time ratcheted it:
     * five wheel notches in and out left 5.188..7.799 of the default 5.188..13.878.
     */
    @Test
    void clampingForAZoomedViewKeepsTheRequestedLevels() {
        final PlotController.RangeResolution zoomedIn = PlotController.resolveRange(
                10, 20, 12, 18, 12, 18, PlotController.RangeAdjustment.CLAMP,
                Optional.of(new FieldExtrema(11, 14, new Point2(0, 0), new Point2(1, 1))), true);
        assertEquals(12, zoomedIn.levelMin());
        assertEquals(14, zoomedIn.levelMax());

        final PlotController.RangeResolution zoomedOut = PlotController.resolveRange(
                11, 14, zoomedIn.levelMin(), zoomedIn.levelMax(), 12, 18,
                PlotController.RangeAdjustment.CLAMP, Optional.of(EXTREMA), true);
        assertEquals(12, zoomedOut.levelMin());
        assertEquals(18, zoomedOut.levelMax());
        assertTrue(zoomedOut.rangeChanged());
    }

    @Test
    void clampRefitsOnlyWhenTheRequestedLevelsMissTheField() {
        final PlotController.RangeResolution disjoint = PlotController.resolveRange(
                -5, 5, 1, 2, 1, 2, PlotController.RangeAdjustment.CLAMP,
                Optional.of(EXTREMA), true);
        assertEquals(10.5, disjoint.levelMin());
        assertEquals(19.5, disjoint.levelMax());
    }

    @Test
    void exactExtremaRefreshSliderDomainWithoutChangingManualLevels() {
        final PlotController.RangeResolution resolution = PlotController.resolveRange(
                -5, 5, 12, 18, PlotController.RangeAdjustment.NONE,
                Optional.of(EXTREMA), true);

        assertEquals(10, resolution.fullMin());
        assertEquals(20, resolution.fullMax());
        assertEquals(12, resolution.levelMin());
        assertEquals(18, resolution.levelMax());
        assertFalse(resolution.rangeChanged());
        assertFalse(resolution.adjustmentDeferred());
    }
}
