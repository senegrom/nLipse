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
        for (final double amplitude : new double[]{1, 1.1, 1.2, 1.3, 1.5, 2.2}) {
            final int actual = (int) method.invoke(null,
                    amplitude, -amplitude, amplitude, -amplitude, 0.0, true);
            assertEquals(0, actual, "Exact ac - bd is zero; amplitude=" + amplitude);
        }
    }
}
