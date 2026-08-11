package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

class AsyncRenderServiceTest {
    @Test
    void newerSubmissionCancelsAndSuppressesOlderResult() throws Exception {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch delivered = new CountDownLatch(1);
        final AtomicInteger callbacks = new AtomicInteger();
        final AtomicLong deliveredSequence = new AtomicLong();

        final RenderEngine engine = (request, token) -> {
            if (request.sequence() == 1) {
                firstStarted.countDown();
                while (!token.isCancelled()) {
                    Thread.onSpinWait();
                }
                token.throwIfCancelled();
            }
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            service.submit(request(), result -> callbacks.incrementAndGet(), throwable -> { });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            service.submit(request(), result -> {
                callbacks.incrementAndGet();
                deliveredSequence.set(result.sequence());
                delivered.countDown();
            }, throwable -> { });

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
            assertEquals(2, deliveredSequence.get());
        }
    }

    @Test
    void rapidSubmissionsKeepTheBacklogBoundedAndDeliverOnlyTheLatest() throws Exception {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch delivered = new CountDownLatch(1);
        final CountDownLatch releaseLatest = new CountDownLatch(1);
        final AtomicInteger callbacks = new AtomicInteger();
        final AtomicLong deliveredSequence = new AtomicLong();
        final RenderEngine engine = (request, token) -> {
            if (request.sequence() == 1) {
                firstStarted.countDown();
                while (!token.isCancelled()) {
                    Thread.onSpinWait();
                }
                token.throwIfCancelled();
            }
            while (releaseLatest.getCount() != 0 && !token.isCancelled()) {
                Thread.onSpinWait();
            }
            token.throwIfCancelled();
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            service.submit(request(), ignored -> callbacks.incrementAndGet(), ignored -> { });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            for (int index = 0; index < 99; index++) {
                final boolean last = index == 98;
                service.submit(request(), result -> {
                    callbacks.incrementAndGet();
                    if (last) {
                        deliveredSequence.set(result.sequence());
                        delivered.countDown();
                    }
                }, ignored -> { });
                assertTrue(service.pendingTaskCount() <= 1);
            }
            releaseLatest.countDown();

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
            assertEquals(100, deliveredSequence.get());
        }
    }

    @Test
    void submissionsAfterCloseAreRejected() {
        final AsyncRenderService service = new AsyncRenderService(
                (request, token) -> result(request), Runnable::run);
        service.close();

        assertThrows(IllegalStateException.class,
                () -> service.submit(request(), ignored -> { }, ignored -> { }));
    }

    private static RenderRequest request() {
        final PlotSnapshot snapshot = new PlotSnapshot(CurveType.LIPSE,
                CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, 1, 1,
                new Viewport(-1, 1, -1, 1), false, false, true, false, false, -1);
        return new RenderRequest(snapshot, 2, 2, RenderQuality.PREVIEW);
    }

    private static RenderResult result(final RenderRequest request) {
        return new RenderResult(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                request.sequence(), request.quality(), Optional.empty(), 1);
    }
}
