package nlipse.ui;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import nlipse.math.DistanceField;

/** One active cursor evaluation and at most one pending, latest-wins request. */
final class AsyncCursorService implements AutoCloseable {
    private record Request(long sequence, DistanceField field, double x, double y,
            Consumer<Double> onValue, Consumer<Throwable> onFailure) {
    }

    private final Object monitor = new Object();
    private final Executor callbacks;
    private final Thread worker;
    private long sequence;
    private Request pending;
    private boolean active;
    private boolean closed;

    AsyncCursorService() {
        this(SwingUtilities::invokeLater);
    }

    AsyncCursorService(final Executor callbacks) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        worker = Thread.ofPlatform().name("nlipse-cursor").daemon().start(this::work);
    }

    void submit(final DistanceField field, final double x, final double y,
            final Consumer<Double> onValue, final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(onValue, "onValue");
        Objects.requireNonNull(onFailure, "onFailure");
        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("Cursor service is closed");
            }
            pending = new Request(++sequence, field, x, y, onValue, onFailure);
            if (active) {
                worker.interrupt();
            }
            monitor.notifyAll();
        }
    }

    void cancel() {
        synchronized (monitor) {
            sequence++;
            pending = null;
            if (active) {
                worker.interrupt();
            }
        }
    }

    private Request awaitRequest() {
        synchronized (monitor) {
            while (!closed && pending == null) {
                try {
                    monitor.wait();
                } catch (final InterruptedException ignored) {
                    // Recheck pending work and shutdown under the same lock.
                }
            }
            if (closed) {
                return null;
            }
            final Request request = pending;
            pending = null;
            active = true;
            // Clear only the previous task's interrupt, before submit can
            // interrupt this task. Clearing outside the lock loses cancellation.
            Thread.interrupted();
            return request;
        }
    }

    private void work() {
        for (Request request = awaitRequest(); request != null; request = awaitRequest()) {
            double value = Double.NaN;
            Throwable failure = null;
            try {
                value = request.field().value(request.x(), request.y());
            } catch (final VirtualMachineError fatal) {
                throw fatal;
            } catch (final Throwable failed) {
                failure = failed;
            } finally {
                synchronized (monitor) {
                    active = false;
                }
            }
            deliver(request, value, failure);
        }
    }

    private void deliver(final Request request, final double value, final Throwable failure) {
        if (!isCurrent(request)) {
            return;
        }
        try {
            callbacks.execute(() -> {
                // A completed callback can wait in Swing's queue while a newer
                // cursor/model request arrives. Check again at delivery time.
                if (!isCurrent(request)) {
                    return;
                }
                if (failure == null) {
                    request.onValue().accept(value);
                } else {
                    request.onFailure().accept(failure);
                }
            });
        } catch (final RuntimeException failed) {
            if (isCurrent(request)) {
                System.err.println("Could not deliver cursor callback: " + failed.getMessage());
            }
        }
    }

    private boolean isCurrent(final Request request) {
        synchronized (monitor) {
            return !closed && request.sequence() == sequence;
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            sequence++;
            pending = null;
            worker.interrupt();
            monitor.notifyAll();
        }
    }
}
