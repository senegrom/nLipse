package nlipse.math;

/** Exact-evaluation counters, exposed so renderer tests can pin the budget. */
public final class AdaptiveDecimalStatistics {
    private AdaptiveDecimalStatistics() {
    }

    public static void reset() {
        AdaptiveDecimal.resetStatistics();
    }

    public static long evaluations() {
        return AdaptiveDecimal.statistics().evaluations();
    }
}
