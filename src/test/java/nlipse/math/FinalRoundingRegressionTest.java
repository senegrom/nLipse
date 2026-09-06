package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import org.junit.jupiter.api.Test;

/** Final-value rounding and precision-amplification regressions from the second review. */
class FinalRoundingRegressionTest {
    @Test
    void cassiniRoundsTheExponentialAtOverflowAndUnderflow() {
        // These references are exact powers of two, independent of log/exp.
        for (final int exponent : new int[]{-1076, -1074, -1073, -1022, 1022, 1023, 1024, 1025}) {
            final List<Focus> foci = List.of(new Focus(0, 0, exponent));
            final double expected = Math.scalb(1.0, exponent);
            assertEquals(expected, ExactFieldMath.cassini(FocusSet.from(foci), 2, 0),
                    "exact evaluator: 2^" + exponent);
            assertEquals(expected, DistanceFields.create(CurveType.CASSIN, foci).value(2, 0),
                    "public field: 2^" + exponent);
        }
    }

    @Test
    void cassiniDistinguishesBothSidesOfTheUnderflowMidpoint() {
        for (final double exponent : new double[]{Math.nextDown(-1075.0), -1075.0,
                Math.nextUp(-1075.0)}) {
            // 2^-1075 is exactly halfway between zero and MIN_VALUE: ties to even is zero.
            final double expected = exponent > -1075 ? Double.MIN_VALUE : 0;
            assertEquals(expected, ExactFieldMath.cassini(
                    FocusSet.from(List.of(new Focus(0, 0, exponent))), 2, 0));
        }
        assertTrue(Double.isFinite(ExactFieldMath.cassini(
                FocusSet.from(List.of(new Focus(0, 0, Math.nextDown(1024.0)))), 2, 0)));
    }

    @Test
    void exactCassiniRetainsLargeWeightCancellationBeforeExponentiation() {
        final FocusSet foci = FocusSet.from(List.of(
                new Focus(0, 0, Double.MAX_VALUE),
                new Focus(0, 0, -Double.MAX_VALUE),
                new Focus(0, 0, 10)));
        assertEquals(1024, ExactFieldMath.cassini(foci, 2, 0));
    }

    @Test
    void tinyPositiveAndNegativePowersAgreeAcrossEquivalentScalings() {
        final List<Focus> ordinary = List.of(new Focus(0.125, 0, 1), new Focus(0.5, 0, 1));
        final List<Focus> subnormal = List.of(
                new Focus(0, 0, 0x1.0p1021), new Focus(0, 0, 0x1.0p1023));
        for (final double magnitude : new double[]{1e-20, 1e-40, 1e-100, 1e-250,
                Double.MIN_NORMAL, Double.MIN_VALUE}) {
            for (final double sign : new double[]{-1, 1}) {
                final double power = sign * magnitude;
                // M_p = 1/4 * exp(log(cosh(p log 2))/p). For these p, the
                // correction is below half an ulp, so 1/4 is the exact binary64 result.
                assertEquals(0.25, ExactFieldMath.powerMean(
                        FocusSet.from(ordinary), 0, 0, power), "exact p=" + power);
                assertEquals(0.25, DistanceFields.create(CurveType.POWER_MEAN, subnormal, power)
                        .value(0x1.0p-1024, 0), "subnormal input p=" + power);
                assertEquals(0.25, DistanceFields.create(CurveType.POWER_MEAN, ordinary, power)
                        .value(0, 0), 2 * Math.ulp(0.25), "ordinary input p=" + power);
            }
        }
    }

    @Test
    void nearZeroPowerKeepsARepresentableCorrection() {
        final FocusSet foci = FocusSet.from(
                List.of(new Focus(0.125, 0, 1), new Focus(0.5, 0, 1)));
        for (final double power : new double[]{-1e-4, -1e-6, 1e-6, 1e-4}) {
            // cosh(t) - 1 = 2*sinh(t/2)^2 avoids subtracting two nearly equal doubles.
            final double halfSinh = Math.sinh(power * Math.log(2) / 2);
            final double expected = 0.25 * Math.exp(Math.log1p(2 * halfSinh * halfSinh) / power);
            final double actual = ExactFieldMath.powerMean(foci, 0, 0, power);
            assertEquals(expected, actual, 2 * Math.ulp(expected));
            assertTrue(power < 0 ? actual < 0.25 : actual > 0.25);
        }
    }

    @Test
    void exactPowerMeanHandlesZeroInputs() {
        final FocusSet foci = FocusSet.from(List.of(new Focus(0, 0, 1), new Focus(0, 0, 2)));
        for (final double power : new double[]{-1e-100, 0, 1e-100, 0.5, 3}) {
            assertEquals(0, ExactFieldMath.powerMean(foci, 0, 0, power));
        }
    }
}
