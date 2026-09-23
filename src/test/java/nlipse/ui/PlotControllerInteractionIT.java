package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.SwingUtilities;
import nlipse.model.Focus;
import nlipse.model.PlotConfig;
import nlipse.model.PlotModel;
import nlipse.render.AsyncRenderService;
import nlipse.render.PlotRenderer;
import nlipse.render.Viewport;
import org.junit.jupiter.api.Test;

/** Canvas gestures through the controller's real listeners, run in CI under Xvfb. */
class PlotControllerInteractionIT {
    private static final int SIZE = 400;

    /** Delete during a drag removed the dragged focus but kept its index, so the drag moved a neighbour. */
    @Test
    void deletingTheDraggedFocusEndsTheDrag() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final PlotConfig defaults = PlotConfig.defaults();
            final PlotModel model = new PlotModel(new PlotConfig(defaults.curveType(),
                    defaults.familyParameter(),
                    List.of(new Focus(-2, 0, 1), new Focus(2, 0, 1), new Focus(0, 2, 1)),
                    defaults.distanceMin(), defaults.distanceMax(), defaults.curveCount(),
                    new Viewport(-5, 5, -5, 5), defaults.showBackground(), defaults.showExtrema(),
                    defaults.antiAlias(), defaults.logSpacing(), defaults.showLegend()));
            final PlotWindow view = new PlotWindow(model.snapshot(), "drag regression");
            final PlotRenderer renderer = new PlotRenderer();
            final PlotController controller = new PlotController(model, view, renderer,
                    new AsyncRenderService(renderer));
            try {
                view.canvas.setSize(SIZE, SIZE);
                final Viewport viewport = model.getViewport();
                mouse(view, MouseEvent.MOUSE_PRESSED, (int) Math.round(viewport.pixelX(-2, SIZE)),
                        (int) Math.round(viewport.pixelY(0, SIZE)));
                view.canvas.getActionMap().get("delete").actionPerformed(
                        new ActionEvent(view.canvas, ActionEvent.ACTION_PERFORMED, "delete"));
                mouse(view, MouseEvent.MOUSE_DRAGGED, 10, 10);
                assertEquals(2, model.getFocusCount());
                assertEquals(new Focus(2, 0, 1), model.getFocus(0), "a neighbour moved with the drag");
                assertEquals(new Focus(0, 2, 1), model.getFocus(1));
            } finally {
                controller.close();
                view.dispose();
            }
        });
    }

    private static void mouse(final PlotWindow view, final int id, final int x, final int y) {
        view.canvas.dispatchEvent(new MouseEvent(view.canvas, id, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
