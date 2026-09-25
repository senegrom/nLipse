package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    /** Opposite weights on one point: the signed families vanish everywhere. */
    private static final FocusSet COINCIDENT = FocusSet.from(List.of(
            new Focus(0, 0, 1), new Focus(0, 0, -1)));
    /** Opposite weights mirrored in x = 0, where the signed families vanish. */
    private static final FocusSet DIPOLE = FocusSet.from(List.of(
            new Focus(-1, 0, 1), new Focus(1, 0, -1)));
    /** Equal weights on one point: the hyperbola and the range vanish everywhere. */
    private static final FocusSet TWIN = FocusSet.from(List.of(
            new Focus(0, 0, 1), new Focus(0, 0, 1)));
    /** Equal weights mirrored in x = 0, where the hyperbola and the range vanish. */
    private static final FocusSet MIRRORED = FocusSet.from(List.of(
            new Focus(-1, 0, 1), new Focus(1, 0, 1)));

    @Test
    void ordinaryEvaluationsStayInteractive() {
        runBatch();
        final long start = System.nanoTime();
        runBatch();
        final long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < 3_000_000_000L,
                "ordinary exact evaluations took " + elapsed / 1_000_000 + " ms");
    }

    /**
     * Terms that cancel exactly, as for coincident opposite foci or on a
     * dipole's bisector, used to leave an enclosure 0 ± r that only the 544- or
     * 1088-digit rungs could certify: 18-22 ms per Gaussian evaluation, which
     * made this batch take two seconds. Merged by their exact squared distance
     * (or, for the hyperbola and the range, by exact w²d²), they cancel before
     * any rounding and resolve at the first rung.
     */
    @Test
    void exactZerosResolveAtTheFirstRung() {
        runZeroBatch();
        final long start = System.nanoTime();
        runZeroBatch();
        final long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < 1_500_000_000L,
                "exactly cancelling evaluations took " + elapsed / 1_000_000 + " ms");
    }

    private static void runZeroBatch() {
        for (int repeat = 0; repeat < 50; repeat++) {
            final double y = -2.5 + repeat * 0.1;
            assertEquals(0.0, ExactFieldMath.gaussian(COINCIDENT, 0.3, y, 1));
            assertEquals(0.0, ExactFieldMath.gaussian(DIPOLE, 0, y, 1));
            assertEquals(0.0, ExactFieldMath.signedDistanceSum(COINCIDENT, 0.3, y));
            assertEquals(0.0, ExactFieldMath.signedDistanceSum(DIPOLE, 0, y));
            assertEquals(0.0, ExactFieldMath.potential(COINCIDENT, 0.3, y));
            assertEquals(0.0, ExactFieldMath.potential(DIPOLE, 0, y));
            assertEquals(0.0, ExactFieldMath.hyperbola(TWIN, 0.3, y));
            assertEquals(0.0, ExactFieldMath.hyperbola(MIRRORED, 0, y));
            assertEquals(0.0, ExactFieldMath.range(TWIN, 0.3, y));
            assertEquals(0.0, ExactFieldMath.range(MIRRORED, 0, y));
        }
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
