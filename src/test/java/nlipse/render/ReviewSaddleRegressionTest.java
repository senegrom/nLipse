package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** White-box regression for the documented zero/tie contract. */
class ReviewSaddleRegressionTest {
    @Test
    void symmetricProductsRemainTiesRegardlessOfPositiveScale() throws Exception {
        final Method method = MarchingSquares.class.getDeclaredMethod(
                "asymptoticHighConnection", double.class, double.class,
                double.class, double.class, double.class, boolean.class);
        method.setAccessible(true);
        for (final double amplitude : new double[]{1, 1.1, 1.2, 1.3, 1.5, 2.2, 1e-300, 1e300, Double.MIN_VALUE}) {
            final int actual = (int) method.invoke(null,
                    amplitude, -amplitude, amplitude, -amplitude, 0.0, true);
            assertEquals(0, actual, "Exact ac - bd is zero; amplitude=" + amplitude);
        }
    }
    @Test
    void determinantFilterAgreesWithExactArithmeticAcrossExponents() throws Exception {
        final Method method = MarchingSquares.class.getDeclaredMethod(
                "asymptoticHighConnection", double.class, double.class,
                double.class, double.class, double.class, boolean.class);
        method.setAccessible(true);
        final java.util.Random random = new java.util.Random(0x534144444c45L);
        for (int sample = 0; sample < 256; sample++) {
            final double a = Math.scalb(1 + random.nextDouble(), random.nextInt(2001) - 1000);
            final double b = -Math.scalb(1 + random.nextDouble(), random.nextInt(2001) - 1000);
            final double c = Math.scalb(1 + random.nextDouble(), random.nextInt(2001) - 1000);
            final double d = -Math.scalb(1 + random.nextDouble(), random.nextInt(2001) - 1000);
            final java.math.BigDecimal ac = new java.math.BigDecimal(a).multiply(new java.math.BigDecimal(c));
            final java.math.BigDecimal bd = new java.math.BigDecimal(b).multiply(new java.math.BigDecimal(d));
            final int expected = ac.subtract(bd).signum();
            assertEquals(expected, (int) method.invoke(null, a, b, c, d, 0.0, true));
            assertEquals(-expected, (int) method.invoke(null, a, b, c, d, 0.0, false));
        }
    }

    @Test
    void exactNonBinarySaddleTiesUseTheCentreSample() {
        final Viewport viewport = new Viewport(-1, 1, -1, 1);
        for (final double amplitude : new double[]{1.1, 1.2, 2.2}) {
            final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            final nlipse.math.DistanceField field = (x, y) -> {
                calls.incrementAndGet();
                return amplitude * x * y;
            };
            final FieldGrid grid = FieldGrid.sample(field, viewport, 2, 2, 1, CancellationToken.NONE);
            calls.set(0);
            assertEquals(2, MarchingSquares.trace(grid, field, viewport, 0,
                    CancellationToken.NONE, (x1, y1, x2, y2) -> { }));
            assertEquals(1, calls.get());
        }
    }
}
