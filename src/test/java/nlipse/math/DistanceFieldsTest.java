package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;

class DistanceFieldsTest {
    private static final double EPSILON = 1e-12;

    @Test
    void ellipseUsesWeightedSum() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(0, 0, 2), new Focus(3, 4, 0.5)));
        assertEquals(2.5, field.value(0, 0), EPSILON);
    }

    @Test
    void cassiniUsesWeightedProduct() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 1), new Focus(3, 4, 1)));
        assertEquals(12, field.value(0, 4), EPSILON);
        assertEquals(0, field.value(0, 0), EPSILON);
    }

    @Test
    void hyperbolaUsesMeanPairwiseDifference() {
        final DistanceField field = DistanceFields.create(CurveType.HYPERB,
                List.of(new Focus(0, 0, 1), new Focus(3, 0, 2), new Focus(0, 4, 0.5)));
        assertEquals(4, field.value(0, 0), EPSILON);
    }

    @Test
    void oneFocusHyperbolaIsZero() {
        final DistanceField field = DistanceFields.create(CurveType.HYPERB,
                List.of(new Focus(1, 2, 3)));
        assertEquals(0, field.value(100, -50), EPSILON);
    }

    @Test
    void hypotAvoidsPrematureOverflow() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(1e308, 0, 1)));
        final double value = field.value(0, 0);
        assertTrue(Double.isFinite(value));
        assertEquals(1e308, value, 1e292);
    }

    @Test
    void signedAndZeroWeightsRemainSupported() {
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(0, 0, -1), new Focus(2, 0, 0)));
        assertEquals(-1, field.value(1, 0), EPSILON);
    }
}
