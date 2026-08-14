package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nlipse.model.Focus;
import org.junit.jupiter.api.Test;

/**
 * Ordinary-magnitude exact evaluations must derive their adaptive precision
 * from the actual inputs instead of escalating to the static worst case.
 */
class ExactFieldMathPrecisionTest {
    private static final int ORDINARY_PRECISION_LIMIT = 136;

    private static final FocusSet PLAIN = FocusSet.from(List.of(
            new Focus(-1, 0, 1), new Focus(1, 0, 1), new Focus(0, 1, 1)));
    private static final FocusSet MIXED = FocusSet.from(List.of(
            new Focus(-1, 0, 1), new Focus(1, 0, -1), new Focus(0, 1, 0.5)));

    @Test
    void ordinaryEvaluationsStayNearTheInitialPrecision() {
        assertOrdinaryPrecision("signedDistanceSum",
                () -> ExactFieldMath.signedDistanceSum(MIXED, 0.3, 0.4));
        assertOrdinaryPrecision("hyperbola",
                () -> ExactFieldMath.hyperbola(MIXED, 0.3, 0.4));
        assertOrdinaryPrecision("range",
                () -> ExactFieldMath.range(PLAIN, 0.3, 0.4));
        assertOrdinaryPrecision("potential",
                () -> ExactFieldMath.potential(MIXED, 0.3, 0.4));
        assertOrdinaryPrecision("cassini",
                () -> ExactFieldMath.cassini(PLAIN, 0.3, 0.4));
        assertOrdinaryPrecision("powerMean 3",
                () -> ExactFieldMath.powerMean(PLAIN, 0.3, 0.4, 3));
        assertOrdinaryPrecision("powerMean -2",
                () -> ExactFieldMath.powerMean(PLAIN, 0.3, 0.4, -2));
        assertOrdinaryPrecision("powerMean 0",
                () -> ExactFieldMath.powerMean(PLAIN, 0.3, 0.4, 0));
        assertOrdinaryPrecision("smoothEnvelope",
                () -> ExactFieldMath.smoothEnvelope(PLAIN, 0.3, 0.4, 1, true));
        assertOrdinaryPrecision("gaussian",
                () -> ExactFieldMath.gaussian(PLAIN, 0.3, 0.4, 1));
    }

    @Test
    void extremeCancellationStillEscalatesPastTheInitialPrecision() {
        final FocusSet cancelling = FocusSet.from(List.of(
                new Focus(-1, 0, 1e300), new Focus(1, 0, -1e300)));
        AdaptiveDecimal.resetStatistics();
        final double value = ExactFieldMath.signedDistanceSum(cancelling, 0, 1e-3);

        assertEquals(0, value, 0);
        assertTrue(AdaptiveDecimal.statistics().peakPrecision() > 136,
                "cancelling magnitudes must still raise precision, saw "
                        + AdaptiveDecimal.statistics().peakPrecision());
    }

    private static void assertOrdinaryPrecision(final String label, final Runnable evaluation) {
        AdaptiveDecimal.resetStatistics();
        evaluation.run();
        final AdaptiveDecimal.Statistics statistics = AdaptiveDecimal.statistics();
        assertTrue(statistics.peakPrecision() <= ORDINARY_PRECISION_LIMIT,
                label + " escalated to precision " + statistics.peakPrecision());
    }
}
