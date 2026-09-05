package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class AsyncCursorServiceTest {
    @Test
    void slowEvaluationDoesNotBlockSubmissionOrBuildAnUnboundedQueue() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        final AtomicInteger evaluations = new AtomicInteger();
        final AtomicReference<Double> displayed = new AtomicReference<>();
        try (var service = new AsyncCursorService(callbacks::add)) {
            service.submit((x, y) -> {
                evaluations.incrementAndGet();
                assertFalse(SwingUtilities.isEventDispatchThread());
                entered.countDown();
                awaitUninterruptibly(release);
                return x;
            }, 0, 0, displayed::set, failure -> { throw new AssertionError(failure); });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int index = 1; index <= 100; index++) {
                service.submit((x, y) -> {
                    assertFalse(Thread.currentThread().isInterrupted());
                    evaluations.incrementAndGet();
                    return x;
                }, index, 0, displayed::set, failure -> { throw new AssertionError(failure); });
            }
            assertEquals(1, evaluations.get());
            release.countDown();
            take(callbacks).run();
            assertEquals(100.0, displayed.get().doubleValue());
            assertEquals(2, evaluations.get());
            assertTrue(callbacks.isEmpty());
        } finally {
            release.countDown();
        }
    }

    @Test
    void newerRequestSuppressesAnAlreadyQueuedCallback() throws Exception {
        final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        final AtomicReference<Double> displayed = new AtomicReference<>();
        try (var service = new AsyncCursorService(callbacks::add)) {
            service.submit((x, y) -> x, 1, 0, displayed::set, ignored -> { });
            final Runnable stale = take(callbacks);
            service.submit((x, y) -> x, 2, 0, displayed::set, ignored -> { });
            final Runnable latest = take(callbacks);
            stale.run();
            assertNull(displayed.get());
            latest.run();
            assertEquals(2.0, displayed.get().doubleValue());
        }
    }

    @Test
    void cancellationAndCloseSuppressQueuedCallbacks() throws Exception {
        final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        final AtomicInteger delivered = new AtomicInteger();
        final AsyncCursorService service = new AsyncCursorService(callbacks::add);
        try {
            service.submit((x, y) -> 1, 0, 0, value -> delivered.incrementAndGet(), ignored -> { });
            final Runnable cancelled = take(callbacks);
            service.cancel();
            cancelled.run();
            service.submit((x, y) -> 2, 0, 0, value -> delivered.incrementAndGet(), ignored -> { });
            final Runnable closed = take(callbacks);
            service.close();
            closed.run();
            assertEquals(0, delivered.get());
            assertThrows(IllegalStateException.class,
                    () -> service.submit((x, y) -> 0, 0, 0, ignored -> { }, ignored -> { }));
        } finally {
            service.close();
        }
    }

    @Test
    void failedEvaluationDoesNotKillTheWorker() throws Exception {
        final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<Double> displayed = new AtomicReference<>();
        try (var service = new AsyncCursorService(callbacks::add)) {
            service.submit((x, y) -> { throw new IllegalArgumentException("test failure"); },
                    0, 0, displayed::set, failure::set);
            take(callbacks).run();
            assertEquals("test failure", failure.get().getMessage());
            service.submit((x, y) -> 3, 0, 0, displayed::set, failure::set);
            take(callbacks).run();
            assertEquals(3.0, displayed.get().doubleValue());
        }
    }

    private static Runnable take(final LinkedBlockingQueue<Runnable> callbacks)
            throws InterruptedException {
        final Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
        assertNotNull(callback, "Cursor evaluation did not complete");
        return callback;
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (final InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
