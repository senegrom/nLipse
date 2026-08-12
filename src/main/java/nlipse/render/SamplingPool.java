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

    static void invoke(final RecursiveAction action) {
        POOL.invoke(action);
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
}
