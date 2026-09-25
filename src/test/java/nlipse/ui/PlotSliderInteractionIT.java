package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import nlipse.model.PlotConfig;
import nlipse.model.PlotModel;
import nlipse.render.AsyncRenderService;
import nlipse.render.PlotRenderer;
import org.junit.jupiter.api.Test;

/** Actual Swing event delivery, run in CI under Xvfb rather than in the headless suite. */
class PlotSliderInteractionIT {
    @Test
    void movingMinimumPreservesMaximumExactly() throws Exception {
        withController(1.23456789, 6.789012345, (model, view) -> {
            view.distanceMin.setValue(200);
            assertEquals(2, model.getDistanceMin());
            assertEquals(6.789012345, model.getDistanceMax());
        });
    }

    @Test
    void movingMaximumPreservesMinimumExactly() throws Exception {
        withController(1.23456789, 6.789012345, (model, view) -> {
            view.distanceMax.setValue(500);
            assertEquals(1.23456789, model.getDistanceMin());
            assertEquals(5, model.getDistanceMax());
        });
    }

    @Test
    void draggingAndReleasingDoesNotQuantizeTheUntouchedBound() throws Exception {
        withController(1.23456789, 6.789012345, (model, view) -> {
            view.distanceMin.setValueIsAdjusting(true);
            view.distanceMin.setValue(150);
            view.distanceMin.setValue(200);
            view.distanceMin.setValueIsAdjusting(false);
            assertEquals(6.789012345, model.getDistanceMax());
            assertEquals(2, model.getDistanceMin());
        });
    }

    /** Pressing and releasing a knob in place fires change events; the exact bound must survive them. */
    @Test
    void clickingAKnobWithoutMovingItKeepsTheExactBound() throws Exception {
        withController(1.23456789, 6.789012345, (model, view) -> {
            view.distanceMin.setValueIsAdjusting(true);
            view.distanceMin.setValueIsAdjusting(false);
            view.distanceMax.setValueIsAdjusting(true);
            view.distanceMax.setValueIsAdjusting(false);
            assertEquals(1.23456789, model.getDistanceMin());
            assertEquals(6.789012345, model.getDistanceMax());
        });
    }

    @Test
    void crossingMaximumMovesBothBounds() throws Exception {
        withController(1.23456789, 6.789012345, (model, view) -> {
            view.distanceMin.setValue(800);
            assertEquals(8, model.getDistanceMin());
            assertEquals(8, model.getDistanceMax());
            assertEquals(view.distanceMin.getValue(), view.distanceMax.getValue());
        });
    }

    @Test
    void crossingWithinTheSameRoundedTickStillKeepsBoundsOrdered() throws Exception {
        withController(1, Math.nextDown(2.0), (model, view) -> {
            assertEquals(200, view.distanceMax.getValue());
            view.distanceMin.setValue(200);
            assertEquals(2, model.getDistanceMin());
            assertEquals(2, model.getDistanceMax());
        });
        withController(Math.nextUp(2.0), 5, (model, view) -> {
            assertEquals(200, view.distanceMin.getValue());
            view.distanceMax.setValue(200);
            assertEquals(2, model.getDistanceMin());
            assertEquals(2, model.getDistanceMax());
        });
    }

    /**
     * Zoomed in, a clamp narrows the displayed levels to the visible field.
     * Dragging one knob then made the other bound's clamped value a request as
     * well, so zooming back out could no longer restore it.
     */
    @Test
    void draggingOneKnobKeepsTheOtherRequestedBound() throws Exception {
        withController(1.23456789, 6.789012345, (model, view, controller) -> {
            // What a clamp to a zoomed-in field does: the display only
            model.setDistanceRange(2, 5);
            view.distanceMin.setValue(300);
            assertEquals(3, controller.requestedMinimum());
            assertEquals(6.789012345, controller.requestedMaximum());

            model.setDistanceRange(3.5, 4);
            view.distanceMax.setValue(450);
            assertEquals(3, controller.requestedMinimum());
            assertEquals(4.5, controller.requestedMaximum());
        });
    }

    @FunctionalInterface
    private interface ControllerAction {
        void accept(PlotModel model, PlotWindow view, PlotController controller);
    }

    private static void withController(final double minimum, final double maximum,
            final BiConsumer<PlotModel, PlotWindow> action) throws Exception {
        withController(minimum, maximum, (model, view, controller) -> action.accept(model, view));
    }

    private static void withController(final double minimum, final double maximum,
            final ControllerAction action) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final PlotModel model = new PlotModel(PlotConfig.defaults());
            model.setDistanceRange(minimum, maximum);
            final PlotWindow view = new PlotWindow(model.snapshot(), "slider regression");
            final PlotRenderer renderer = new PlotRenderer();
            final PlotController controller = new PlotController(model, view, renderer,
                    new AsyncRenderService(renderer));
            try {
                // Isolate slider input from asynchronous, sample-derived domain changes.
                controller.pinSliderDomain(0, 10);
                action.accept(model, view, controller);
            } finally {
                controller.close();
                view.dispose();
            }
        });
    }
}
