package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;
import nlipse.render.CancellationToken;
import nlipse.render.PlotRenderer;
import nlipse.render.PrecisionLimitExceededException;
import nlipse.render.RenderExactness;
import nlipse.render.RenderQuality;
import nlipse.render.RenderRequest;
import nlipse.render.RenderResult;
import nlipse.render.Viewport;

/** A degenerate field must not turn one render into thousands of exact evaluations. */
class ExactBudgetRenderTest {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    @Test
    void degenerateFieldsSpendAtMostThePerRenderExactBudget() {
        for (final CurveType type : List.of(CurveType.RANGE, CurveType.HYPERB,
                CurveType.LIPSE)) {
            AdaptiveDecimal.resetStatistics();
            render(type, degenerateFoci(type));
            final long evaluations = AdaptiveDecimal.statistics().evaluations();
            final long budget = expectedBudget();

            assertTrue(evaluations > 0, type + " never used the exact path");
            assertTrue(evaluations <= budget,
                    type + " spent " + evaluations + " exact evaluations, budget " + budget);
        }
    }

    @Test
    void ordinaryFieldsStillRenderWithoutAnyExactEvaluation() {
        AdaptiveDecimal.resetStatistics();
        for (final CurveType type : CurveType.values()) {
            render(type, List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1),
                    new Focus(0, 0.7, 1)));
        }

        assertEquals(0, AdaptiveDecimal.statistics().evaluations());
    }

    @Test
    void exhaustedRenderDoesNotSeedReusableApproximateArtifacts() {
        final PlotRenderer renderer = new PlotRenderer();
        final PlotSnapshot snapshot = snapshot(CurveType.RANGE,
                degenerateFoci(CurveType.RANGE));
        final RenderRequest request = new RenderRequest(snapshot, WIDTH, HEIGHT,
                RenderQuality.FULL);

        AdaptiveDecimal.resetStatistics();
        final RenderResult firstResult = renderer.render(request, CancellationToken.NONE);
        final long first = AdaptiveDecimal.statistics().evaluations();
        AdaptiveDecimal.resetStatistics();
        final RenderResult secondResult = renderer.render(request, CancellationToken.NONE);
        final long second = AdaptiveDecimal.statistics().evaluations();

        assertTrue(firstResult.precisionLimited());
        assertTrue(secondResult.precisionLimited());
        assertTrue(first > 0);
        assertTrue(second > 0, "an exhausted render was reused from a cache");
        assertTrue(second <= expectedBudget());
    }

    @Test
    void exactRenderRejectsAResultThatWouldBePrecisionLimited() {
        final PlotSnapshot snapshot = snapshot(CurveType.RANGE,
                degenerateFoci(CurveType.RANGE));
        final RenderRequest request = new RenderRequest(snapshot, WIDTH, HEIGHT,
                RenderQuality.FULL, RenderExactness.REQUIRE_EXACT);

        assertThrows(PrecisionLimitExceededException.class,
                () -> new PlotRenderer().render(request, CancellationToken.NONE));
    }

    private static long expectedBudget() {
        return Math.clamp((long) WIDTH + HEIGHT, 4096, 65_536);
    }

    private static PlotSnapshot snapshot(final CurveType type, final List<Focus> foci) {
        return new PlotSnapshot(type, type.defaultParameter(),
                foci, type == CurveType.LIPSE ? -1 : 0.05, 5, 12,
                new Viewport(-3, 3, -2, 2), true, true, true, false, false, -1);
    }

    /** Weighted distances that stay near-equal across the whole viewport, which is
     *  what a far-out zoom does to any ordinary focus arrangement. */
    private static List<Focus> degenerateFoci(final CurveType type) {
        final double separation = 1e-9;
        if (type == CurveType.LIPSE) {
            // The signed sum only cancels when the weights oppose.
            return List.of(new Focus(-separation / 2, 0, 1),
                    new Focus(separation / 2, 0, -1));
        }
        return List.of(new Focus(-separation / 2, 0, 1),
                new Focus(separation / 2, 0, 1),
                new Focus(0, separation / 3, 1));
    }

    private static void render(final CurveType type, final List<Focus> foci) {
        final PlotSnapshot snapshot = snapshot(type, foci);

        new PlotRenderer().render(new RenderRequest(snapshot, WIDTH, HEIGHT,
                RenderQuality.FULL), CancellationToken.NONE);
    }
}
