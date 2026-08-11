package nlipse.render;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * One-worker scheduler for latest-wins interactive renders and durable export jobs.
 *
 * <p>Interactive submissions replace one another and may cancel an active
 * interactive render. Export jobs are bounded, FIFO and are never cancelled by
 * later interactive activity. Both kinds of work therefore share one renderer
 * and one cache-ownership thread without allowing an unbounded backlog.</p>
 */
public final class AsyncRenderService implements AutoCloseable {
    private static final int MAX_PENDING_EXPORTS = 4;

    @FunctionalInterface
    public interface BackgroundJob<T> {
        T run(CancellationToken cancellationToken) throws Exception;
    }

    private final RenderEngine engine;
    private final Executor callbackExecutor;
    private final Object monitor = new Object();
    private final AtomicLong interactiveGeneration = new AtomicLong();
    private final AtomicLong jobSequence = new AtomicLong();
    private final ArrayDeque<ScheduledTask> pendingExports = new ArrayDeque<>();
    private final Thread workerThread;

    private ScheduledTask pendingInteractive;
    private ScheduledTask runningTask;
    private boolean closed;

    public AsyncRenderService(final RenderEngine engine) {
        this(engine, SwingUtilities::invokeLater);
    }

    public AsyncRenderService(final RenderEngine engine, final Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        workerThread = Thread.ofPlatform().name("nlipse-renderer").daemon()
                .start(this::workerLoop);
    }

    public long submit(final RenderRequest request,
            final Consumer<RenderResult> onSuccess, final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");

        final long generation;
        synchronized (monitor) {
            ensureOpen();
            generation = interactiveGeneration.incrementAndGet();
            if (pendingInteractive != null) {
                pendingInteractive.cancel();
            }
            if (runningTask != null && !runningTask.durable()) {
                runningTask.cancel();
                workerThread.interrupt();
            }
            final RenderRequest sequenced = request.withSequence(generation);
            pendingInteractive = new InteractiveTask(sequenced, generation,
                    onSuccess, onFailure);
            monitor.notifyAll();
        }
        return generation;
    }

    /**
     * Queues a durable background job on the renderer worker.
     *
     * <p>An export cancels obsolete interactive work, but later interactive
     * submissions do not cancel the export. At most four exports may wait.</p>
     */
    public <T> long submitExport(final BackgroundJob<T> job,
            final Consumer<T> onSuccess, final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");

        synchronized (monitor) {
            ensureOpen();
            if (pendingExports.size() >= MAX_PENDING_EXPORTS) {
                throw new RejectedExecutionException("The export queue is full");
            }
            interactiveGeneration.incrementAndGet();
            if (pendingInteractive != null) {
                pendingInteractive.cancel();
                pendingInteractive = null;
            }
            if (runningTask != null && !runningTask.durable()) {
                runningTask.cancel();
                workerThread.interrupt();
            }
            final long sequence = jobSequence.incrementAndGet();
            pendingExports.addLast(new ExportTask<>(sequence, job, onSuccess, onFailure));
            monitor.notifyAll();
            return sequence;
        }
    }

    private void workerLoop() {
        while (true) {
            final ScheduledTask task;
            synchronized (monitor) {
                while (!closed && pendingExports.isEmpty() && pendingInteractive == null) {
                    try {
                        monitor.wait();
                    } catch (final InterruptedException ignored) {
                        // Cancellation also interrupts the worker so blocking jobs wake promptly.
                    }
                }
                if (closed) {
                    return;
                }
                task = pendingExports.isEmpty()
                        ? takeInteractive() : pendingExports.removeFirst();
                runningTask = task;
            }

            Thread.interrupted();
            task.execute();
            Thread.interrupted();
            synchronized (monitor) {
                if (runningTask == task) {
                    runningTask = null;
                }
            }
        }
    }

    private ScheduledTask takeInteractive() {
        final ScheduledTask task = pendingInteractive;
        pendingInteractive = null;
        return task;
    }

    private void deliver(final Runnable callback) {
        try {
            callbackExecutor.execute(callback);
        } catch (final RuntimeException failed) {
            // A callback executor failure must not terminate the renderer worker.
            System.err.println("Could not deliver renderer callback: " + failed.getMessage());
        }
    }

    /** Cancels only interactive rendering; queued or active exports are preserved. */
    public void cancelInteractive() {
        synchronized (monitor) {
            interactiveGeneration.incrementAndGet();
            if (pendingInteractive != null) {
                pendingInteractive.cancel();
                pendingInteractive = null;
            }
            if (runningTask != null && !runningTask.durable()) {
                runningTask.cancel();
                workerThread.interrupt();
            }
        }
    }

    /** Cancels all interactive and durable work. */
    public void cancel() {
        synchronized (monitor) {
            interactiveGeneration.incrementAndGet();
            if (pendingInteractive != null) {
                pendingInteractive.cancel();
                pendingInteractive = null;
            }
            for (final ScheduledTask task : pendingExports) {
                task.cancel();
            }
            pendingExports.clear();
            if (runningTask != null) {
                runningTask.cancel();
                workerThread.interrupt();
            }
        }
    }

    int pendingTaskCount() {
        synchronized (monitor) {
            return (pendingInteractive == null ? 0 : 1) + pendingExports.size();
        }
    }

    int pendingExportCount() {
        synchronized (monitor) {
            return pendingExports.size();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Render service is closed");
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            interactiveGeneration.incrementAndGet();
            if (pendingInteractive != null) {
                pendingInteractive.cancel();
                pendingInteractive = null;
            }
            for (final ScheduledTask task : pendingExports) {
                task.cancel();
            }
            pendingExports.clear();
            if (runningTask != null) {
                runningTask.cancel();
            }
            monitor.notifyAll();
        }
        workerThread.interrupt();
    }

    private abstract class ScheduledTask {
        private volatile boolean cancelled;

        abstract boolean durable();

        abstract void run(CancellationToken token) throws Exception;

        final void cancel() {
            cancelled = true;
        }

        final boolean isCancelled() {
            return cancelled || Thread.currentThread().isInterrupted();
        }

        final void execute() {
            final CancellationToken token = this::isCancelled;
            try {
                run(token);
            } catch (final RenderCancelledException ignored) {
                // Cancellation is expected control flow.
            } catch (final Throwable throwable) {
                failed(throwable);
            }
        }

        abstract void failed(Throwable throwable);
    }

    private final class InteractiveTask extends ScheduledTask {
        private final RenderRequest request;
        private final long generation;
        private final Consumer<RenderResult> onSuccess;
        private final Consumer<Throwable> onFailure;

        InteractiveTask(final RenderRequest request, final long generation,
                final Consumer<RenderResult> onSuccess,
                final Consumer<Throwable> onFailure) {
            this.request = request;
            this.generation = generation;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }

        @Override
        boolean durable() {
            return false;
        }

        @Override
        void run(final CancellationToken token) {
            final RenderResult result = engine.render(request,
                    () -> token.isCancelled() || interactiveGeneration.get() != generation);
            if (!token.isCancelled() && interactiveGeneration.get() == generation) {
                deliver(() -> {
                    if (interactiveGeneration.get() == generation) {
                        onSuccess.accept(result);
                    }
                });
            }
        }

        @Override
        void failed(final Throwable throwable) {
            if (!isCancelled() && interactiveGeneration.get() == generation) {
                deliver(() -> {
                    if (interactiveGeneration.get() == generation) {
                        onFailure.accept(throwable);
                    }
                });
            }
        }
    }

    private final class ExportTask<T> extends ScheduledTask {
        private final long sequence;
        private final BackgroundJob<T> job;
        private final Consumer<T> onSuccess;
        private final Consumer<Throwable> onFailure;

        ExportTask(final long sequence, final BackgroundJob<T> job,
                final Consumer<T> onSuccess, final Consumer<Throwable> onFailure) {
            this.sequence = sequence;
            this.job = job;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }

        @Override
        boolean durable() {
            return true;
        }

        @Override
        void run(final CancellationToken token) throws Exception {
            final T result = job.run(token);
            token.throwIfCancelled();
            deliver(() -> {
                if (!isCancelled()) {
                    onSuccess.accept(result);
                }
            });
        }

        @Override
        void failed(final Throwable throwable) {
            if (!isCancelled()) {
                deliver(() -> {
                    if (!isCancelled()) {
                        onFailure.accept(throwable);
                    }
                });
            }
        }

        @Override
        public String toString() {
            return "ExportTask[sequence=" + sequence + ']';
        }
    }
}
