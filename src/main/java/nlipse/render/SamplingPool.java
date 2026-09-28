package nlipse.render;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared, bounded worker pool for CPU-bound scalar-field sampling. */
final class SamplingPool {
    private static final AtomicInteger WORKER_NUMBER = new AtomicInteger();
    private static final int PARALLELISM = configuredParallelism();
    private static final ForkJoinPool POOL = new ForkJoinPool(
            PARALLELISM,
            SamplingPool::newWorker,
            null,
            true);

    private SamplingPool() {
    }

    static int parallelism() {
        return PARALLELISM;
    }

    /** Work over the indices [from, to) of a split range. */
    @FunctionalInterface
    interface RangeWork {
        void run(int from, int to);
    }

    /**
     * Runs {@code work} over [from, to) on the pool, halving the range down to
     * chunks of at most {@code grain} indices; the token is checked at each split.
     */
    static void invokeRange(final int from, final int to, final int grain,
            final CancellationToken token, final RangeWork work) {
        POOL.invoke(new RangeTask(from, to, grain, token, work));
    }

    /**
     * Runs both halves of a split task and returns, or throws, only once no
     * half is still running. {@code ForkJoinTask.invokeAll} rethrows the first
     * failure, a cancellation for instance, without waiting for a sibling
     * already running: that half could then go on writing samples into shared
     * tiles after the render had decided whether to keep them. A sibling that
     * has not started is skipped instead, as there.
     */
    static void invokeBoth(final RecursiveAction first, final RecursiveAction second) {
        second.fork();
        try {
            first.invoke();
        } catch (final RuntimeException | Error failure) {
            if (!second.tryUnfork()) {
                second.quietlyJoin();
            }
            throw failure;
        }
        second.join();
    }

    private static int configuredParallelism() {
        final int processors = Runtime.getRuntime().availableProcessors();
        final int defaultParallelism = Math.min(32, Math.max(1, processors - 1));
        final String configured = System.getProperty("nlipse.renderThreads");
        if (configured == null || configured.isBlank()) {
            return defaultParallelism;
        }
        try {
            return Math.clamp(Integer.parseInt(configured.trim()), 1, 256);
        } catch (final NumberFormatException ignored) {
            return defaultParallelism;
        }
    }

    private static ForkJoinWorkerThread newWorker(final ForkJoinPool pool) {
        final ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                .newThread(pool);
        thread.setName("nlipse-sampler-" + WORKER_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static final class RangeTask extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final int from;
        private final int to;
        private final int grain;
        private final transient CancellationToken token;
        private final transient RangeWork work;

        RangeTask(final int from, final int to, final int grain,
                final CancellationToken token, final RangeWork work) {
            this.from = from;
            this.to = to;
            this.grain = grain;
            this.token = token;
            this.work = work;
        }

        @Override
        protected void compute() {
            token.throwIfCancelled();
            if (to - from <= grain) {
                work.run(from, to);
                return;
            }
            final int middle = (from + to) >>> 1;
            invokeBoth(new RangeTask(from, middle, grain, token, work),
                    new RangeTask(middle, to, grain, token, work));
        }
    }
}
