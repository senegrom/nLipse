package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import nlipse.math.AdaptiveDecimalStatistics;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** A degenerate field must not turn one render into thousands of exact evaluations. */
class ExactBudgetRenderTest {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    @Test
    void degenerateFieldsSpendAtMostThePerRenderExactBudget() {
        for (final CurveType type : List.of(CurveType.RANGE, CurveType.HYPERB,
                CurveType.LIPSE)) {
            AdaptiveDecimalStatistics.reset();
            render(type, degenerateFoci(type));
            final long evaluations = AdaptiveDecimalStatistics.evaluations();
            final long budget = PlotRenderer.exactBudget(WIDTH, HEIGHT);

            assertTrue(evaluations > 0, type + " never used the exact path");
            assertTrue(evaluations <= budget,
                    type + " spent " + evaluations + " exact evaluations, budget " + budget);
        }
    }

    @Test
    void ordinaryFieldsStillRenderWithoutAnyExactEvaluation() {
        AdaptiveDecimalStatistics.reset();
        for (final CurveType type : CurveType.values()) {
            render(type, List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1),
                    new Focus(0, 0.7, 1)));
        }

        assertEquals(0, AdaptiveDecimalStatistics.evaluations());
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
        final PlotSnapshot snapshot = new PlotSnapshot(type, type.defaultParameter(),
                foci, type == CurveType.LIPSE ? -1 : 0.05, 5, 12,
                new Viewport(-3, 3, -2, 2), true, true, true, false, false, -1);

        new PlotRenderer().render(new RenderRequest(snapshot, WIDTH, HEIGHT,
                RenderQuality.FULL), CancellationToken.NONE);
    }
}
