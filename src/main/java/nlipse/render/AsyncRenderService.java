package nlipse.render;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Bounded single-worker scheduler for interactive renders and durable exports.
 *
 * <p>Interactive work is latest-wins: a newer request cancels the active or
 * pending interactive request and suppresses its callbacks. One durable export
 * may be active or queued and is never superseded by later interaction.</p>
 */
public final class AsyncRenderService implements AutoCloseable {
    @FunctionalInterface
    public interface ExportOperation {
        void write(RenderResult result) throws Exception;
    }

    private static final class WorkItem {
        private final boolean export;
        private final RenderRequest request;
        private final Consumer<RenderResult> onSuccess;
        private final Consumer<Throwable> onFailure;
        private final ExportOperation exportOperation;
        private volatile boolean cancelled;

        WorkItem(final boolean export, final RenderRequest request,
                final Consumer<RenderResult> onSuccess,
                final Consumer<Throwable> onFailure,
                final ExportOperation exportOperation) {
            this.export = export;
            this.request = request;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
            this.exportOperation = exportOperation;
        }
    }

    private record Outcome(RenderResult result, Throwable failure) {
        static Outcome success(final RenderResult result) {
            return new Outcome(result, null);
        }

        static Outcome failure(final Throwable failure) {
            return new Outcome(null, failure);
        }
    }

    private final RenderEngine engine;
    private final Executor callbackExecutor;
    private final Object monitor = new Object();
    private final Thread worker;

    private long nextSequence;
    private long latestInteractiveSequence = -1;
    private WorkItem active;
    private WorkItem pendingInteractive;
    private WorkItem pendingExport;
    private volatile boolean closed;

    public AsyncRenderService(final RenderEngine engine) {
        this(engine, SwingUtilities::invokeLater);
    }

    public AsyncRenderService(final RenderEngine engine, final Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        worker = Thread.ofPlatform().name("nlipse-renderer").daemon().start(this::workerLoop);
    }

    public long submitInteractive(final RenderRequest request,
            final Consumer<RenderResult> onSuccess,
            final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        synchronized (monitor) {
            ensureOpen();
            final long sequence = ++nextSequence;
            latestInteractiveSequence = sequence;
            cancel(pendingInteractive);
            pendingInteractive = new WorkItem(false, request.withSequence(sequence),
                    onSuccess, onFailure, null);
            if (active != null && !active.export) {
                cancel(active);
                worker.interrupt();
            }
            monitor.notifyAll();
            return sequence;
        }
    }

    /**
     * Attempts to enqueue one full-quality durable export.
     *
     * @return {@code false} when another export is already active or queued
     */
    public boolean submitExport(final RenderRequest request,
            final ExportOperation operation,
            final Consumer<RenderResult> onSuccess,
            final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        if (request.quality() != RenderQuality.FULL) {
            throw new IllegalArgumentException("Exports require a full-quality render");
        }
        if (request.exactness() != RenderExactness.REQUIRE_EXACT) {
            throw new IllegalArgumentException("Exports require an exact render request");
        }
        synchronized (monitor) {
            ensureOpen();
            if (hasOutstandingExport()) {
                return false;
            }
            final long sequence = ++nextSequence;
            pendingExport = new WorkItem(true, request.withSequence(sequence),
                    onSuccess, onFailure, operation);
            // Do not discard a display render that is already producing a
            // useful frame. The export has priority over queued interaction
            // once the active render finishes.
            monitor.notifyAll();
            return true;
        }
    }

    private void workerLoop() {
        while (true) {
            final WorkItem item;
            synchronized (monitor) {
                item = awaitNextItem();
                if (item == null) {
                    return;
                }
                active = item;
            }

            // Cancellation interrupts belong to the previous interactive task.
            Thread.interrupted();
            final Outcome outcome;
            try {
                outcome = execute(item);
            } finally {
                synchronized (monitor) {
                    if (active == item) {
                        active = null;
                    }
                }
            }
            dispatch(item, outcome);
        }
    }

    private WorkItem awaitNextItem() {
        while (!closed && pendingExport == null && pendingInteractive == null) {
            try {
                monitor.wait();
            } catch (final InterruptedException ignored) {
                // Re-evaluate closed state and pending work.
            }
        }
        if (closed) {
            return null;
        }
        if (pendingExport != null) {
            final WorkItem item = pendingExport;
            pendingExport = null;
            return item;
        }
        final WorkItem item = pendingInteractive;
        pendingInteractive = null;
        return item;
    }

    private Outcome execute(final WorkItem item) {
        final CancellationToken token = () -> item.cancelled
                || Thread.currentThread().isInterrupted() || closed;
        try {
            final RenderResult result = engine.render(item.request, token);
            token.throwIfCancelled();
            if (item.export && result.precisionLimited()) {
                throw new PrecisionLimitExceededException();
            }
            if (item.export) {
                item.exportOperation.write(result);
                token.throwIfCancelled();
            }
            return Outcome.success(result);
        } catch (final RenderCancelledException ignored) {
            return null;
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return item.cancelled || closed ? null : Outcome.failure(interrupted);
        } catch (final VirtualMachineError fatal) {
            throw fatal;
        } catch (final Throwable failure) {
            return item.cancelled || closed ? null : Outcome.failure(failure);
        }
    }

    private void dispatch(final WorkItem item, final Outcome outcome) {
        if (outcome == null) {
            return;
        }
        try {
            callbackExecutor.execute(() -> {
                if (!shouldDeliver(item)) {
                    return;
                }
                if (outcome.failure() == null) {
                    item.onSuccess.accept(outcome.result());
                } else {
                    item.onFailure.accept(outcome.failure());
                }
            });
        } catch (final RuntimeException failed) {
            if (!closed) {
                System.err.println("Could not deliver render callback: " + failed.getMessage());
            }
        }
    }

    private boolean shouldDeliver(final WorkItem item) {
        synchronized (monitor) {
            return !closed && !item.cancelled
                    && (item.export || item.request.sequence() == latestInteractiveSequence);
        }
    }

    /** Cancels only interactive work; an accepted export remains durable. */
    public void cancel() {
        synchronized (monitor) {
            latestInteractiveSequence = -1;
            cancel(pendingInteractive);
            pendingInteractive = null;
            if (active != null && !active.export) {
                cancel(active);
                worker.interrupt();
            }
        }
    }

    private boolean hasOutstandingExport() {
        return pendingExport != null || active != null && active.export;
    }

    private static void cancel(final WorkItem item) {
        if (item != null) {
            item.cancelled = true;
        }
    }


    int pendingTaskCount() {
        synchronized (monitor) {
            return (pendingInteractive == null ? 0 : 1) + (pendingExport == null ? 0 : 1);
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
            latestInteractiveSequence = -1;
            cancel(active);
            cancel(pendingInteractive);
            cancel(pendingExport);
            pendingInteractive = null;
            pendingExport = null;
            worker.interrupt();
            monitor.notifyAll();
        }
    }
}
