package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nlipse.model.Focus;
import org.junit.jupiter.api.Test;

/**
 * Public cost contract of the adaptive evaluators: ordinary-magnitude inputs
 * must resolve near the initial precision, not at each family's static worst
 * case. Before the analytic per-call scales, one such power-mean evaluation
 * cost over half a second at 1088 digits; the whole batch below ran for
 * fourteen seconds. The bound leaves more than an order of magnitude of
 * headroom over current cost while sitting far below that regression.
 */
class ExactFieldMathCostTest {
    private static final FocusSet PLAIN = FocusSet.from(List.of(
            new Focus(-1, 0, 1), new Focus(1, 0, 1), new Focus(0, 1, 1)));
    private static final FocusSet MIXED = FocusSet.from(List.of(
            new Focus(-1, 0, 1), new Focus(1, 0, -1), new Focus(0, 1, 0.5)));

    @Test
    void ordinaryEvaluationsStayInteractive() {
        runBatch();
        final long start = System.nanoTime();
        runBatch();
        final long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < 3_000_000_000L,
                "ordinary exact evaluations took " + elapsed / 1_000_000 + " ms");
    }

    private static void runBatch() {
        for (int repeat = 0; repeat < 10; repeat++) {
            ExactFieldMath.signedDistanceSum(MIXED, 0.3, 0.4);
            ExactFieldMath.hyperbola(MIXED, 0.3, 0.4);
            ExactFieldMath.range(PLAIN, 0.3, 0.4);
            ExactFieldMath.potential(MIXED, 0.3, 0.4);
            ExactFieldMath.cassini(PLAIN, 0.3, 0.4);
            ExactFieldMath.powerMean(PLAIN, 0.3, 0.4, 3);
            ExactFieldMath.powerMean(PLAIN, 0.3, 0.4, -2);
            ExactFieldMath.powerMean(PLAIN, 0.3, 0.4, 0);
            ExactFieldMath.smoothEnvelope(PLAIN, 0.3, 0.4, 1, true);
            ExactFieldMath.gaussian(PLAIN, 0.3, 0.4, 1);
        }
    }
}
