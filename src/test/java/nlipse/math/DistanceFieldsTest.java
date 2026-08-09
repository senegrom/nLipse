package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
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
    void ellipseRecoversFiniteCancellationAfterIntermediateOverflow() {
        final double coordinate = Double.MAX_VALUE * 0.75;
        final DistanceField field = DistanceFields.create(CurveType.LIPSE,
                List.of(new Focus(coordinate, 0, 1), new Focus(coordinate, 0, 1),
                        new Focus(coordinate, 0, -1), new Focus(coordinate, 0, -1)));

        assertEquals(0, field.value(0, 0), 0);
    }

    @Test
    void cassiniUsesWeightedProduct() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 1), new Focus(3, 4, 1)));
        assertEquals(12, field.value(0, 4), EPSILON);
        assertEquals(0, field.value(0, 0), EPSILON);
    }

    @Test
    void cassiniAccumulatesInTheLogDomainBeforeExponentiating() {
        final double coordinate = 1e200;
        final DistanceField field = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(coordinate, 0, 2), new Focus(-coordinate, 0, -2)));

        assertEquals(1, field.value(0, 0), 1e-12);
    }

    @Test
    void cassiniRecoversFiniteCancellationWithExtremeWeights() {
        final DistanceField field = DistanceFields.create(CurveType.CASSIN, List.of(
                new Focus(0, 0, Double.MAX_VALUE),
                new Focus(0, 0, -Double.MAX_VALUE),
                new Focus(0, 0, 1)));

        assertEquals(Math.E, field.value(Math.E, 0), 1e-14);
    }

    @Test
    void cassiniPreservesZeroAndSingularFactorSemantics() {
        final DistanceField zeroWeight = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 0)));
        final DistanceField undefined = DistanceFields.create(CurveType.CASSIN,
                List.of(new Focus(0, 0, 1), new Focus(0, 0, -1)));

        assertEquals(1, zeroWeight.value(0, 0), 0);
        assertTrue(Double.isNaN(undefined.value(0, 0)));
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
    void largeHyperbolaMatchesBruteForceReference() {
        final List<Focus> foci = IntStream.range(0, 64)
                .mapToObj(index -> new Focus(index * 0.17 - 4, index % 7 - 3,
                        index % 5 - 2.25))
                .toList();
        final DistanceField field = DistanceFields.create(CurveType.HYPERB, foci);
        final double x = 0.75;
        final double y = -1.25;
        final double[] distances = foci.stream()
                .mapToDouble(focus -> Math.hypot(x - focus.x(), y - focus.y()) * focus.weight())
                .toArray();
        double sum = 0;
        for (int i = 0; i < distances.length; i++) {
            for (int j = 0; j < i; j++) {
                sum += Math.abs(distances[i] - distances[j]);
            }
        }
        final double expected = 2 * sum / ((double) distances.length * (distances.length - 1));

        assertEquals(expected, field.value(x, y), 1e-10);
    }

    @Test
    void hyperbolaAvoidsOverflowWhenTheFinalMeanIsFinite() {
        final double coordinate = Double.MAX_VALUE * 0.75;
        final DistanceField field = DistanceFields.create(CurveType.HYPERB,
                List.of(new Focus(coordinate, 0, 1), new Focus(coordinate, 0, 1),
                        new Focus(coordinate, 0, -1), new Focus(coordinate, 0, -1)));

        final double value = field.value(0, 0);
        assertTrue(Double.isFinite(value));
        assertEquals(4.0 / 3.0, value / coordinate, 1e-12);
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
