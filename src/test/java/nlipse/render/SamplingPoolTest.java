package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SamplingPoolTest {
    /**
     * A cancelled render throws out of one half of a split while another worker
     * still runs the other half. {@code invokeAll} rethrew at once, so that half
     * went on writing samples into tiles the render had already decided to keep.
     */
    @Test
    @Timeout(10)
    void aFailedHalfWaitsForItsRunningSibling() {
        final CountDownLatch siblingRunning = new CountDownLatch(1);
        final AtomicBoolean siblingFinished = new AtomicBoolean();
        final AtomicBoolean finishedWhenThrown = new AtomicBoolean();
        final AtomicReference<RuntimeException> thrown = new AtomicReference<>();
        final RecursiveAction sibling = new Body(() -> {
            siblingRunning.countDown();
            pause(200);
            siblingFinished.set(true);
        });
        final RecursiveAction failing = new Body(() -> {
            // Fail only once another worker has taken the sibling.
            await(siblingRunning);
            throw new IllegalStateException("cancelled");
        });
        final ForkJoinPool pool = new ForkJoinPool(2);
        try {
            pool.invoke(new Body(() -> {
                try {
                    SamplingPool.invokeBoth(failing, sibling);
                } catch (final IllegalStateException failure) {
                    finishedWhenThrown.set(siblingFinished.get());
                    thrown.set(failure);
                }
            }));
        } finally {
            pool.shutdownNow();
        }
        assertNotNull(thrown.get(), "the failure must propagate");
        assertTrue(finishedWhenThrown.get(), "invokeBoth threw while its sibling was still running");
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pause(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Body extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final transient Runnable body;

        Body(final Runnable body) {
            this.body = body;
        }

        @Override
        protected void compute() {
            body.run();
        }
    }
}
