package nlipse.render;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Single-worker latest-wins render queue with cooperative cancellation. */
public final class AsyncRenderService implements AutoCloseable {
    private final RenderEngine engine;
    private final Executor callbackExecutor;
    private final ExecutorService worker;
    private final AtomicLong generation = new AtomicLong();
    private Future<?> currentTask;

    public AsyncRenderService(final RenderEngine engine) {
        this(engine, SwingUtilities::invokeLater);
    }

    public AsyncRenderService(final RenderEngine engine, final Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        final ThreadFactory threadFactory = runnable -> {
            final Thread thread = new Thread(runnable, "nlipse-renderer");
            thread.setDaemon(true);
            return thread;
        };
        worker = Executors.newSingleThreadExecutor(threadFactory);
    }

    public synchronized long submit(final RenderRequest request,
            final Consumer<RenderResult> onSuccess, final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        final long sequence = generation.incrementAndGet();
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        final RenderRequest sequenced = request.withSequence(sequence);
        currentTask = worker.submit(() -> {
            final CancellationToken token = () -> Thread.currentThread().isInterrupted()
                    || generation.get() != sequence;
            try {
                final RenderResult result = engine.render(sequenced, token);
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
        });
        return sequence;
    }

    public synchronized void cancel() {
        generation.incrementAndGet();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    @Override
    public synchronized void close() {
        cancel();
        worker.shutdownNow();
    }
}
