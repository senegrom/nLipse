package nlipse.math;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds how many precision-driven exact evaluations one render pass may spend.
 *
 * <p>The adaptive evaluator costs roughly a thousand times a primitive sample.
 * That is worth paying on the thin loci where cancellation destroys a value's
 * meaning, but a degenerate configuration — weighted distances that are
 * near-equal across the whole viewport — can make every pixel ill-conditioned
 * and turn a 20 ms render into several seconds. A render therefore declares a
 * budget: the first ill-conditioned samples are evaluated exactly and the rest
 * keep the primitive result, whose error stays bounded by the inputs' own
 * rounding.</p>
 *
 * <p>Only precision fallbacks consult the budget. Overflow, underflow and
 * non-finite intermediates still always take the exact path, because there the
 * primitive result is not merely imprecise but wrong. Outside a declared pass
 * the budget is unlimited, so cursor readouts, exports of a single value and
 * direct API use remain fully exact.</p>
 */
public final class ExactBudget {
    private static final long UNLIMITED = -1;
    private static final AtomicLong REMAINING = new AtomicLong(UNLIMITED);

    private ExactBudget() {
    }

    /** Declares the budget for the calling render; pair with {@link #end()}. */
    public static void begin(final long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Exact-evaluation budget must not be negative");
        }
        REMAINING.set(limit);
    }

    /** Restores unlimited exact evaluation. */
    public static void end() {
        REMAINING.set(UNLIMITED);
    }

    /** Whether the current pass may spend one more precision-driven evaluation. */
    static boolean tryConsume() {
        return REMAINING.getAndUpdate(value -> value <= 0 ? value : value - 1) != 0;
    }
}
