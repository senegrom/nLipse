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
    /** Marks worker threads whose evaluations belong to the declared render pass. */
    public interface Participant {
    }

    private static final long UNLIMITED = -1;
    private static final AtomicLong REMAINING = new AtomicLong(UNLIMITED);
    private static volatile Thread declaringThread;

    private ExactBudget() {
    }

    /** Declares the budget for the calling render; pair with {@link #end()}. */
    public static void begin(final long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Exact-evaluation budget must not be negative");
        }
        declaringThread = Thread.currentThread();
        REMAINING.set(limit);
    }

    /** Restores unlimited exact evaluation. */
    public static void end() {
        declaringThread = null;
        REMAINING.set(UNLIMITED);
    }

    /** Whether the current thread may spend one more precision-driven evaluation.
     *  Only the declaring thread and {@link Participant} sampling workers are
     *  budgeted; any other thread — the event-dispatch thread probing the field
     *  under the cursor, or direct API use — always evaluates exactly, even
     *  while a render pass is active. */
    static boolean tryConsume() {
        final Thread current = Thread.currentThread();
        if (current != declaringThread && !(current instanceof Participant)) {
            return true;
        }
        return REMAINING.getAndUpdate(value -> value <= 0 ? value : value - 1) != 0;
    }
}
