package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Every {@link Ball} operation must enclose the true result of the operation
 * on the true operands: the operands are computed at eight digits from
 * enclosures of known truths, the truth at 120 digits.
 */
class BallTest {
    private static final MathContext LOW = new MathContext(8, RoundingMode.HALF_EVEN);
    private static final MathContext HIGH = new MathContext(120, RoundingMode.HALF_EVEN);
    private static final int SAMPLES = 400;

    /** A truth and an enclosure of it whose midpoint is off by up to the radius. */
    private record Known(BigDecimal truth, Ball ball) {
    }

    private static Known known(final Random random, final double truth, final boolean exact) {
        final BigDecimal value = new BigDecimal(truth);
        if (exact) {
            return new Known(value, Ball.exact(value));
        }
        final BigDecimal radius = value.abs().multiply(BigDecimal.valueOf(random.nextDouble() * 1e-5))
                .add(BigDecimal.valueOf(1e-9 * random.nextDouble()));
        final BigDecimal offset = radius.multiply(BigDecimal.valueOf(2 * random.nextDouble() - 1));
        return new Known(value, Ball.of(value.add(offset), radius));
    }

    private static double magnitude(final Random random, final int minimumExponent,
            final int maximumExponent) {
        final int exponent = minimumExponent + random.nextInt(maximumExponent - minimumExponent + 1);
        return Math.copySign(Math.scalb(0.5 + 0.5 * random.nextDouble(), exponent),
                random.nextBoolean() ? 1 : -1);
    }

    private static void assertEncloses(final Ball ball, final BigDecimal truth, final String label) {
        assertTrue(ball.isBounded(), label + ": unbounded");
        final BigDecimal distance = ball.midpoint().subtract(truth).abs();
        assertTrue(distance.compareTo(ball.radius()) <= 0,
                () -> label + ": truth " + truth + " outside " + ball);
    }

    private void checkBinary(final String label, final BiFunction<Ball, Ball, Ball> operation,
            final BiFunction<BigDecimal, BigDecimal, BigDecimal> truth, final boolean positiveSecond) {
        checkBinary(label, operation, truth, positiveSecond, false);
    }

    private void checkBinary(final String label, final BiFunction<Ball, Ball, Ball> operation,
            final BiFunction<BigDecimal, BigDecimal, BigDecimal> truth, final boolean positiveSecond,
            final boolean exactSecond) {
        final Random random = new Random(label.hashCode());
        for (int sample = 0; sample < SAMPLES; sample++) {
            final Known first = known(random, magnitude(random, -20, 20), random.nextInt(4) == 0);
            final double secondValue = magnitude(random, positiveSecond ? 0 : -20, 20);
            final Known second = known(random, positiveSecond ? Math.abs(secondValue) : secondValue,
                    exactSecond || random.nextInt(4) == 0);
            assertEncloses(operation.apply(first.ball(), second.ball()),
                    truth.apply(first.truth(), second.truth()), label + " sample " + sample);
        }
    }

    private void checkUnary(final String label, final Function<Ball, Ball> operation,
            final Function<BigDecimal, BigDecimal> truth, final int minimumExponent,
            final int maximumExponent, final boolean positive) {
        final Random random = new Random(label.hashCode());
        for (int sample = 0; sample < SAMPLES; sample++) {
            final double value = magnitude(random, minimumExponent, maximumExponent);
            final Known operand = known(random, positive ? Math.abs(value) : value,
                    random.nextInt(4) == 0);
            assertEncloses(operation.apply(operand.ball()), truth.apply(operand.truth()),
                    label + " sample " + sample);
        }
    }

    @Test
    void additionAndSubtractionEncloseTheTruth() {
        checkBinary("add", (a, b) -> a.add(b, LOW), (a, b) -> a.add(b), false);
        checkBinary("subtract", (a, b) -> a.subtract(b, LOW), (a, b) -> a.subtract(b), false);
    }

    @Test
    void multiplicationEnclosesTheTruth() {
        checkBinary("multiply", (a, b) -> a.multiply(b, LOW), (a, b) -> a.multiply(b), false);
        checkBinary("multiply exact", (a, b) -> a.multiply(b.midpoint(), LOW),
                (a, b) -> a.multiply(b), false, true);
    }

    @Test
    void divisionEnclosesTheTruth() {
        checkBinary("divide", (a, b) -> a.divide(b, LOW), (a, b) -> a.divide(b, HIGH), true);
        checkBinary("divide exact", (a, b) -> a.divide(b.midpoint(), LOW),
                (a, b) -> a.divide(b, HIGH), true, true);
    }

    @Test
    void rootExponentialAndLogarithmEncloseTheTruth() {
        checkUnary("sqrt", ball -> ball.sqrt(LOW), value -> value.sqrt(HIGH), -40, 40, true);
        checkUnary("exp", ball -> ball.exp(LOW), value -> DecimalMath.exp(value, HIGH), -8, 8, false);
        // The generator adds an absolute 1e-9 to radii, so tiny operands would straddle zero.
        checkUnary("log", ball -> ball.log(LOW), value -> DecimalMath.log(value, HIGH), -18, 40, true);
    }

    @Test
    void roundingErrorAloneIsCarried() {
        final Ball third = Ball.exact(BigDecimal.ONE).divide(Ball.exact(BigDecimal.valueOf(3)), LOW);
        assertEncloses(third, BigDecimal.ONE.divide(BigDecimal.valueOf(3), HIGH), "one third");
        assertTrue(third.radius().compareTo(new BigDecimal("1e-7")) < 0, "eight-digit rounding: " + third);
        final Ball two = Ball.exact(BigDecimal.valueOf(2)).sqrt(LOW);
        assertEncloses(two, BigDecimal.valueOf(2).sqrt(HIGH), "root two");
        assertTrue(Ball.exact(BigDecimal.valueOf(4)).sqrt(LOW).radius().compareTo(new BigDecimal("1e-6")) < 0);
    }

    @Test
    void enclosuresStayTight() {
        // Radii grow by the operands' radii and one rounding, not by orders of magnitude.
        final Ball a = Ball.of(new BigDecimal("3.0000000"), new BigDecimal("1e-9"));
        final Ball b = Ball.of(new BigDecimal("2.0000000"), new BigDecimal("1e-9"));
        assertTrue(a.add(b, LOW).radius().compareTo(new BigDecimal("1e-6")) < 0);
        assertTrue(a.multiply(b, LOW).radius().compareTo(new BigDecimal("1e-6")) < 0);
        assertTrue(a.divide(b, LOW).radius().compareTo(new BigDecimal("1e-6")) < 0);
        assertTrue(a.sqrt(LOW).radius().compareTo(new BigDecimal("1e-6")) < 0);
        assertTrue(a.exp(LOW).radius().compareTo(new BigDecimal("1e-5")) < 0);
        assertTrue(a.log(LOW).radius().compareTo(new BigDecimal("1e-6")) < 0);
    }

    @Test
    void unboundedCasesAreReported() {
        final Ball straddling = Ball.of(new BigDecimal("0.5"), BigDecimal.ONE);
        assertFalse(Ball.exact(BigDecimal.ONE).divide(straddling, LOW).isBounded(), "divisor may be zero");
        assertFalse(straddling.log(LOW).isBounded(), "logarithm of a value that may be zero");
        assertFalse(Ball.of(BigDecimal.ZERO, BigDecimal.valueOf(2)).exp(LOW).isBounded(),
                "exponent wider than one");
        assertFalse(Ball.UNBOUNDED.add(Ball.exact(BigDecimal.ONE), LOW).isBounded());
        assertFalse(Ball.exact(BigDecimal.ONE).multiply(Ball.UNBOUNDED, LOW).isBounded());
        assertFalse(Ball.UNBOUNDED.sqrt(LOW).isBounded());
        assertFalse(Ball.orderStatistic(new Ball[] {Ball.UNBOUNDED, Ball.ZERO}, 0).isBounded());
    }

    @Test
    void exponentialBeyondTheDoubleRangeIsAFinalOverflow() {
        final Ball overflow = Ball.exact(BigDecimal.valueOf(3000)).exp(LOW);
        assertTrue(overflow.isOverflow());
        assertEquals(Double.POSITIVE_INFINITY, overflow.midpoint().doubleValue());
        assertFalse(overflow.add(Ball.exact(BigDecimal.ONE), LOW).isBounded(), "an overflow is final");
        final Ball truncated = Ball.exact(BigDecimal.valueOf(-100_000)).exp(LOW);
        assertEquals(0, truncated.midpoint().signum());
        assertTrue(truncated.radius().compareTo(new BigDecimal("1e-600")) < 0, "" + truncated);
        assertTrue(truncated.radius().signum() > 0, "a truncated exponential is not exactly zero");
    }

    @Test
    void squareRootOfAnEnclosureReachingZeroIsClipped() {
        final Ball nearZero = Ball.of(new BigDecimal("1e-20"), new BigDecimal("1e-18"));
        final Ball root = nearZero.sqrt(LOW);
        assertEncloses(root, BigDecimal.ZERO, "zero");
        assertEncloses(root, new BigDecimal("1.01e-18").sqrt(HIGH), "upper end");
        assertEncloses(Ball.of(BigDecimal.ZERO, new BigDecimal("4e-10")).sqrt(LOW),
                new BigDecimal("3e-10").sqrt(HIGH), "zero midpoint");
    }

    @Test
    void orderStatisticsEncloseTheTrueOrderStatistic() {
        final Random random = new Random(0x4f52444552L);
        for (int sample = 0; sample < SAMPLES; sample++) {
            final int count = 1 + random.nextInt(7);
            final Ball[] balls = new Ball[count];
            final BigDecimal[] truths = new BigDecimal[count];
            for (int index = 0; index < count; index++) {
                final Known value = known(random, magnitude(random, -3, 3), random.nextInt(3) == 0);
                balls[index] = value.ball();
                truths[index] = value.truth();
            }
            Arrays.sort(truths);
            for (int rank = 0; rank < count; rank++) {
                assertEncloses(Ball.orderStatistic(balls, rank), truths[rank],
                        "sample " + sample + " rank " + rank);
            }
        }
    }

    @Test
    void absoluteValueAndNegationKeepTheRadius() {
        final Ball ball = Ball.of(new BigDecimal("-2.5"), new BigDecimal("0.125"));
        assertEquals(new BigDecimal("2.5"), ball.abs().midpoint());
        assertEquals(new BigDecimal("0.125"), ball.abs().radius());
        assertEquals(new BigDecimal("2.5"), ball.negate().midpoint());
        assertTrue(Ball.of(new BigDecimal("0.1"), new BigDecimal("0.2")).abs().radius()
                .compareTo(new BigDecimal("0.2")) == 0, "|x| stays within the same radius of |m|");
        assertTrue(Ball.ZERO.isExactlyZero());
        assertFalse(Ball.of(BigDecimal.ZERO, new BigDecimal("1e-9")).isExactlyZero());
        assertTrue(Ball.of(BigDecimal.ONE, new BigDecimal("0.5")).isPositive());
        assertFalse(Ball.of(BigDecimal.ONE, BigDecimal.ONE).isPositive());
    }
}
