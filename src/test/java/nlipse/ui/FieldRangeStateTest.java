package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import nlipse.geometry.Point2;
import nlipse.render.FieldExtrema;

class FieldRangeStateTest {
    @Test
    void precisionLimitedExtremaRemainDiagnosticAndDoNotConsumeAutoFit() {
        final FieldRangeState state = new FieldRangeState(0, 10);
        state.mark(FieldRangeState.Adjustment.AUTO_FIT);

        final FieldRangeState.Completion limited = state.observe(
                Optional.of(extrema(100, 200)), true, 0, 10);

        assertFalse(limited.rangeChanged());
        assertEquals(0, state.fullMinimum());
        assertEquals(10, state.fullMaximum());
        assertEquals(100, state.sampledMinimum());
        assertEquals(200, state.sampledMaximum());
        assertTrue(state.sampledApproximate());
        assertTrue(state.hasPendingAdjustment());

        final FieldRangeState.Completion exact = state.observe(
                Optional.of(extrema(100, 200)), false, 0, 10);

        assertTrue(exact.rangeChanged());
        assertEquals(105, exact.minimum());
        assertEquals(195, exact.maximum());
        assertFalse(state.sampledApproximate());
        assertFalse(state.hasPendingAdjustment());
    }

    @Test
    void limitedRenderWithoutFiniteSamplesKeepsTheExistingExactRange() {
        final FieldRangeState state = new FieldRangeState(-2, 3);
        state.mark(FieldRangeState.Adjustment.CLAMP);

        final FieldRangeState.Completion completion = state.observe(
                Optional.empty(), true, -2, 3);

        assertFalse(completion.rangeChanged());
        assertEquals(-2, state.fullMinimum());
        assertEquals(3, state.fullMaximum());
        assertTrue(state.sampledApproximate());
        assertTrue(state.hasPendingAdjustment());
    }

    @Test
    void exactEmptyFieldClearsAnImpossiblePendingAdjustment() {
        final FieldRangeState state = new FieldRangeState(-2, 3);
        state.mark(FieldRangeState.Adjustment.AUTO_FIT);

        final FieldRangeState.Completion completion = state.observe(
                Optional.empty(), false, -2, 3);

        assertFalse(completion.rangeChanged());
        assertFalse(state.sampledApproximate());
        assertFalse(state.hasPendingAdjustment());
    }

    private static FieldExtrema extrema(final double minimum, final double maximum) {
        return new FieldExtrema(minimum, maximum,
                new Point2(0, 0), new Point2(1, 1));
    }
}
