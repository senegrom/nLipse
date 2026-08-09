package nlipse.render;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Single-worker latest-wins render queue with cooperative cancellation and bounded backlog. */
public final class AsyncRenderService implements AutoCloseable {
    private final RenderEngine engine;
    private final Executor callbackExecutor;
    private final ThreadPoolExecutor worker;
    private final AtomicLong generation = new AtomicLong();
    private Future<?> currentTask;
    private boolean closed;

    public AsyncRenderService(final RenderEngine engine) {
        this(engine, SwingUtilities::invokeLater);
    }

    public AsyncRenderService(final RenderEngine engine, final Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        worker = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().name("nlipse-renderer").daemon().factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized long submit(final RenderRequest request,
            final Consumer<RenderResult> onSuccess, final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        if (closed) {
            throw new IllegalStateException("Render service is closed");
        }

        final long sequence = generation.incrementAndGet();
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        worker.getQueue().clear();
        final RenderRequest sequenced = request.withSequence(sequence);
        try {
            currentTask = worker.submit(() -> runRender(sequenced, sequence, onSuccess, onFailure));
        } catch (final RejectedExecutionException exception) {
            if (closed || worker.isShutdown()) {
                throw new IllegalStateException("Render service is closed", exception);
            }
            throw exception;
        }
        return sequence;
    }

    private void runRender(final RenderRequest request, final long sequence,
            final Consumer<RenderResult> onSuccess, final Consumer<Throwable> onFailure) {
        final CancellationToken token = () -> Thread.currentThread().isInterrupted()
                || generation.get() != sequence;
        try {
            final RenderResult result = engine.render(request, token);
            if (!token.isCancelled()) {
                callbackExecutor.execute(() -> {
                    if (generation.get() == sequence) {
                        onSuccess.accept(result);
                    }
                });
            }
        } catch (final RenderCancelledException ignored) {
            // Superseded render; intentionally silent.
        } catch (final Throwable throwable) {
            if (!token.isCancelled()) {
                callbackExecutor.execute(() -> {
                    if (generation.get() == sequence) {
                        onFailure.accept(throwable);
                    }
                });
            }
        }
    }

    public synchronized void cancel() {
        generation.incrementAndGet();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        worker.getQueue().clear();
    }

    int pendingTaskCount() {
        return worker.getQueue().size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancel();
        worker.shutdownNow();
    }
}
