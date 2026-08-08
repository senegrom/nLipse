package nlipse.app;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import nlipse.model.PlotConfig;
import nlipse.model.PlotModel;
import nlipse.render.AsyncRenderService;
import nlipse.render.PlotRenderer;
import nlipse.ui.PlotController;
import nlipse.ui.PlotWindow;
import nlipse.ui.SetupDialog;

public final class Main {
    public static final String VERSION = "0.5.0";

    private Main() {
    }

    public static void main(final String[] arguments) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (final ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // The cross-platform look and feel remains available.
            }
            final PlotConfig config = SetupDialog.showDialog(null, PlotConfig.defaults());
            if (config == null) {
                return;
            }
            final PlotModel model = new PlotModel(config);
            final PlotWindow window = new PlotWindow(model.snapshot(), VERSION);
            final PlotRenderer renderer = new PlotRenderer();
            final AsyncRenderService renderService = new AsyncRenderService(renderer);
            new PlotController(model, window, renderer, renderService);
            window.setVisible(true);
        });
    }
}
