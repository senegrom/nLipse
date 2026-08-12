package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
            service.submitInteractive(request(RenderQuality.PREVIEW),
                    ignored -> callbacks.incrementAndGet(), ignored -> { });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            service.submitInteractive(request(RenderQuality.PREVIEW), result -> {
                callbacks.incrementAndGet();
                deliveredSequence.set(result.sequence());
                delivered.countDown();
            }, ignored -> { });

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
            assertEquals(2, deliveredSequence.get());
        }
    }

    @Test
    void rapidSubmissionsKeepTheInteractiveBacklogBoundedAndDeliverOnlyTheLatest()
            throws Exception {
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
            service.submitInteractive(request(RenderQuality.PREVIEW),
                    ignored -> callbacks.incrementAndGet(), ignored -> { });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            for (int index = 0; index < 99; index++) {
                final boolean last = index == 98;
                service.submitInteractive(request(RenderQuality.PREVIEW), result -> {
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
    void acceptedExportWaitsForAUsefulActiveInteractiveRender() throws Exception {
        final CountDownLatch interactiveStarted = new CountDownLatch(1);
        final CountDownLatch releaseInteractive = new CountDownLatch(1);
        final CountDownLatch interactiveCompleted = new CountDownLatch(1);
        final CountDownLatch exportStarted = new CountDownLatch(1);
        final CountDownLatch exportCompleted = new CountDownLatch(1);
        final AtomicBoolean interactiveCancelled = new AtomicBoolean();
        final RenderEngine engine = (request, token) -> {
            if (request.quality() == RenderQuality.PREVIEW) {
                interactiveStarted.countDown();
                while (releaseInteractive.getCount() != 0) {
                    interactiveCancelled.set(token.isCancelled());
                    Thread.onSpinWait();
                }
            } else {
                exportStarted.countDown();
            }
            token.throwIfCancelled();
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            service.submitInteractive(request(RenderQuality.PREVIEW),
                    ignored -> interactiveCompleted.countDown(), ignored -> { });
            assertTrue(interactiveStarted.await(2, TimeUnit.SECONDS));
            assertTrue(service.submitExport(request(RenderQuality.FULL),
                    ignored -> { }, ignored -> exportCompleted.countDown(), ignored -> { }));

            assertFalse(exportStarted.await(100, TimeUnit.MILLISECONDS));
            assertFalse(interactiveCancelled.get());
            releaseInteractive.countDown();

            assertTrue(interactiveCompleted.await(2, TimeUnit.SECONDS));
            assertTrue(exportStarted.await(2, TimeUnit.SECONDS));
            assertTrue(exportCompleted.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void durableExportSurvivesLaterInteractiveSubmissions() throws Exception {
        final CountDownLatch exportStarted = new CountDownLatch(1);
        final CountDownLatch releaseExport = new CountDownLatch(1);
        final CountDownLatch exportCompleted = new CountDownLatch(1);
        final CountDownLatch interactiveCompleted = new CountDownLatch(1);
        final AtomicBoolean exportTokenCancelled = new AtomicBoolean();
        final AtomicInteger exportWrites = new AtomicInteger();

        final RenderEngine engine = (request, token) -> {
            if (request.quality() == RenderQuality.FULL) {
                exportStarted.countDown();
                while (releaseExport.getCount() != 0) {
                    exportTokenCancelled.set(token.isCancelled());
                    Thread.onSpinWait();
                }
            }
            token.throwIfCancelled();
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL), result ->
                    exportWrites.incrementAndGet(), ignored -> exportCompleted.countDown(), ignored -> { }));
            assertTrue(exportStarted.await(2, TimeUnit.SECONDS));

            service.submitInteractive(request(RenderQuality.PREVIEW),
                    ignored -> interactiveCompleted.countDown(), ignored -> { });
            releaseExport.countDown();

            assertTrue(exportCompleted.await(2, TimeUnit.SECONDS));
            assertTrue(interactiveCompleted.await(2, TimeUnit.SECONDS));
            assertFalse(exportTokenCancelled.get());
            assertEquals(1, exportWrites.get());
        }
    }

    @Test
    void onlyOneDurableExportMayBeOutstanding() throws Exception {
        final CountDownLatch exportStarted = new CountDownLatch(1);
        final CountDownLatch releaseExport = new CountDownLatch(1);
        final RenderEngine engine = (request, token) -> {
            exportStarted.countDown();
            while (releaseExport.getCount() != 0 && !token.isCancelled()) {
                Thread.onSpinWait();
            }
            token.throwIfCancelled();
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL),
                    ignored -> { }, ignored -> { }, ignored -> { }));
            assertTrue(exportStarted.await(2, TimeUnit.SECONDS));
            assertFalse(service.submitExport(request(RenderQuality.FULL),
                    ignored -> { }, ignored -> { }, ignored -> { }));
            releaseExport.countDown();
        }
    }

    @Test
    void cancellingInteractiveWorkDoesNotCancelAnExport() throws Exception {
        final CountDownLatch exportStarted = new CountDownLatch(1);
        final CountDownLatch releaseExport = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        final RenderEngine engine = (request, token) -> {
            exportStarted.countDown();
            while (releaseExport.getCount() != 0) {
                Thread.onSpinWait();
            }
            token.throwIfCancelled();
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL),
                    ignored -> { }, ignored -> completed.countDown(), ignored -> { }));
            assertTrue(exportStarted.await(2, TimeUnit.SECONDS));
            service.cancel();
            releaseExport.countDown();
            assertTrue(completed.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void exportSlotIsReleasedBeforeCompletionCallbackRuns() throws Exception {
        final CountDownLatch secondCompleted = new CountDownLatch(1);
        final AtomicBoolean secondAccepted = new AtomicBoolean();
        try (AsyncRenderService service = new AsyncRenderService(
                (request, token) -> result(request), Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL),
                    ignored -> { }, ignored -> secondAccepted.set(service.submitExport(
                            request(RenderQuality.FULL), ignoredResult -> { },
                            secondResult -> secondCompleted.countDown(), failure -> { })),
                    failure -> { }));
            assertTrue(secondCompleted.await(2, TimeUnit.SECONDS));
            assertTrue(secondAccepted.get());
        }
    }

    @Test
    void exportFailuresAreDeliveredAndReleaseTheExportSlot() throws Exception {
        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicReference<Throwable> delivered = new AtomicReference<>();
        try (AsyncRenderService service = new AsyncRenderService(
                (request, token) -> result(request), Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL), result -> {
                throw new IOException("disk full");
            }, ignored -> { }, throwable -> {
                delivered.set(throwable);
                failed.countDown();
            }));
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            assertEquals("disk full", delivered.get().getMessage());

            final CountDownLatch second = new CountDownLatch(1);
            assertTrue(service.submitExport(request(RenderQuality.FULL),
                    ignored -> { }, ignored -> second.countDown(), ignored -> { }));
            assertTrue(second.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void recoverableErrorsAreReportedWithoutKillingTheWorker() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch failureDelivered = new CountDownLatch(1);
        final CountDownLatch successDelivered = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final RenderEngine engine = (request, token) -> {
            if (calls.getAndIncrement() == 0) {
                throw new LinkageError("broken renderer dependency");
            }
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            service.submitInteractive(request(RenderQuality.PREVIEW), ignored -> { }, throwable -> {
                failure.set(throwable);
                failureDelivered.countDown();
            });
            assertTrue(failureDelivered.await(2, TimeUnit.SECONDS));
            assertEquals("broken renderer dependency", failure.get().getMessage());

            service.submitInteractive(request(RenderQuality.PREVIEW),
                    ignored -> successDelivered.countDown(), ignored -> { });
            assertTrue(successDelivered.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void exportsRequireFullQualityRequests() {
        try (AsyncRenderService service = new AsyncRenderService(
                (request, token) -> result(request), Runnable::run)) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.submitExport(request(RenderQuality.PREVIEW),
                            ignored -> { }, ignored -> { }, ignored -> { }));
        }
    }

    @Test
    void exportCompletionCanImmediatelyQueueTheNextExport() throws Exception {
        final CountDownLatch secondCompleted = new CountDownLatch(1);
        final AtomicBoolean secondAccepted = new AtomicBoolean();
        final RenderEngine engine = (request, token) -> result(request);

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL), ignored -> { },
                    ignored -> secondAccepted.set(service.submitExport(
                            request(RenderQuality.FULL), ignoredAgain -> { },
                            ignoredAgain -> secondCompleted.countDown(), ignoredAgain -> { })),
                    ignored -> { }));
            assertTrue(secondCompleted.await(2, TimeUnit.SECONDS));
            assertTrue(secondAccepted.get());
        }
    }

    @Test
    void durableExportsForceExactRendering() throws Exception {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean exact = new AtomicBoolean();
        final RenderEngine engine = (request, token) -> {
            exact.set(request.exactRequired());
            return result(request);
        };

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL), ignored -> { },
                    ignored -> completed.countDown(), ignored -> { }));
            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertTrue(exact.get());
        }
    }

    @Test
    void precisionLimitedExactResultFailsBeforeWriting() throws Exception {
        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicBoolean written = new AtomicBoolean();
        final AtomicReference<Throwable> delivered = new AtomicReference<>();
        final RenderEngine engine = (request, token) -> new RenderResult(
                new BufferedImage(request.width(), request.height(),
                        BufferedImage.TYPE_INT_ARGB),
                request.sequence(), request.quality(), Optional.empty(), 1, true);

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            assertTrue(service.submitExport(request(RenderQuality.FULL), ignored ->
                    written.set(true), ignored -> { }, failure -> {
                        delivered.set(failure);
                        failed.countDown();
                    }));
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            assertFalse(written.get());
            assertTrue(delivered.get().getMessage().contains("precision allowance"));
        }
    }

    @Test
    void mismatchedRenderResultsAreRejected() throws Exception {
        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicReference<Throwable> delivered = new AtomicReference<>();
        final RenderEngine engine = (request, token) -> new RenderResult(
                new BufferedImage(request.width(), request.height(),
                        BufferedImage.TYPE_INT_ARGB),
                request.sequence() + 1, request.quality(), Optional.empty(), 1);

        try (AsyncRenderService service = new AsyncRenderService(engine, Runnable::run)) {
            service.submitInteractive(request(RenderQuality.PREVIEW), ignored -> { }, failure -> {
                delivered.set(failure);
                failed.countDown();
            });
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            assertTrue(delivered.get().getMessage().contains("different request"));
        }
    }

    @Test
    void submissionsAfterCloseAreRejected() {
        final AsyncRenderService service = new AsyncRenderService(
                (request, token) -> result(request), Runnable::run);
        service.close();

        assertThrows(IllegalStateException.class,
                () -> service.submitInteractive(request(RenderQuality.PREVIEW),
                        ignored -> { }, ignored -> { }));
        assertThrows(IllegalStateException.class,
                () -> service.submitExport(request(RenderQuality.FULL),
                        ignored -> { }, ignored -> { }, ignored -> { }));
    }

    private static RenderRequest request(final RenderQuality quality) {
        final PlotSnapshot snapshot = new PlotSnapshot(CurveType.LIPSE,
                CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(0, 0, 1)), 0, 1, 1,
                new Viewport(-1, 1, -1, 1), false, false, true, false, false, -1);
        return new RenderRequest(snapshot, 2, 2, quality);
    }

    private static RenderResult result(final RenderRequest request) {
        return new RenderResult(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                request.sequence(), request.quality(), Optional.empty(), 1);
    }
}
