package nlipse.math;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-field allowance for precision-driven exact evaluations.
 *
 * <p>Overflow, underflow and non-finite primitive results bypass this allowance;
 * only fallbacks requested because a finite result is ill-conditioned consume it.
 * Each rendered field owns its allowance, so concurrent renders and direct cursor
 * evaluations cannot change one another's numerical policy.</p>
 */
public final class ExactBudget {
    private static final long UNLIMITED = -1;
    private static final ExactBudget UNLIMITED_INSTANCE = new ExactBudget(UNLIMITED);

    private final AtomicLong remaining;
    private final AtomicBoolean exhausted = new AtomicBoolean();

    private ExactBudget(final long limit) {
        remaining = new AtomicLong(limit);
    }

    /** Returns the shared stateless allowance used by direct field evaluation. */
    public static ExactBudget unlimited() {
        return UNLIMITED_INSTANCE;
    }

    /** Returns a new allowance with the given non-negative limit. */
    public static ExactBudget limited(final long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Exact-evaluation budget must not be negative");
        }
        return new ExactBudget(limit);
    }

    /** Whether at least one precision-driven evaluation was denied. */
    public boolean exhausted() {
        return exhausted.get();
    }

    /** Whether this field may spend one more precision-driven evaluation. */
    boolean tryConsume() {
        while (true) {
            final long value = remaining.get();
            if (value == UNLIMITED) {
                return true;
            }
            if (value == 0) {
                exhausted.set(true);
                return false;
            }
            if (remaining.compareAndSet(value, value - 1)) {
                return true;
            }
        }
    }
}
