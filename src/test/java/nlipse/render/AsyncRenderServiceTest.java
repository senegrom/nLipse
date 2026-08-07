package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import nlipse.geometry.Point2;
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
            if (request.getSequence() == 1) {
                firstStarted.countDown();
                while (!token.isCancelled()) {
                    Thread.yield();
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
                deliveredSequence.set(result.getSequence());
                delivered.countDown();
            }, throwable -> { });

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
            assertEquals(2, deliveredSequence.get());
        }
    }

    private static RenderRequest request() {
        final PlotSnapshot snapshot = new PlotSnapshot(CurveType.LIPSE,
                Arrays.asList(new Focus(0, 0, 1)), 0, 1, 1,
                new Viewport(-1, 1, -1, 1), false, false, true, false, -1);
        return new RenderRequest(snapshot, 2, 2, RenderQuality.PREVIEW);
    }

    private static RenderResult result(final RenderRequest request) {
        return new RenderResult(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                request.getSequence(), request.getQuality(), 0, 1,
                new Point2(0, 0), new Point2(1, 1), 1);
    }
}
