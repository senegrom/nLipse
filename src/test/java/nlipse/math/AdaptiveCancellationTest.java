package nlipse.math;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AdaptiveCancellationTest {
    @Test
    void interruptedEvaluationStopsBeforeAnotherPrecisionRound() {
        final AtomicBoolean evaluated = new AtomicBoolean();
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> AdaptiveDecimal.toDouble(context -> {
                evaluated.set(true);
                return Ball.exact(BigDecimal.ONE);
            }));
            assertFalse(evaluated.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void transcendentalSeriesCooperatesWithCancellation() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,
                    () -> DecimalMath.exp(new BigDecimal("0.25"), MathContext.DECIMAL128));
        } finally {
            Thread.interrupted();
        }
    }
}
