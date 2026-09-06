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

    private static void withController(final double minimum, final double maximum,
            final BiConsumer<PlotModel, PlotWindow> action) throws Exception {
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
                action.accept(model, view);
            } finally {
                controller.close();
                view.dispose();
            }
        });
    }
}
