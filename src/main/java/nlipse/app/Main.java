package nlipse.app;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import nlipse.model.PlotConfig;
import nlipse.model.PlotConfigIO;
import nlipse.model.PlotModel;
import nlipse.render.AsyncRenderService;
import nlipse.render.PlotRenderer;
import nlipse.ui.PlotController;
import nlipse.ui.PlotWindow;
import nlipse.ui.SetupDialog;

public final class Main {
    public static final String VERSION = "0.11.2";

    private Main() {
    }

    /** The last closed session's setup when one exists and still parses;
     *  the stock defaults otherwise. The setup dialog shows it for editing
     *  either way, so a stale file never locks the user out. */
    private static PlotConfig initialConfig() {
        final java.nio.file.Path lastSession = PlotConfigIO.lastSessionFile();
        try {
            if (java.nio.file.Files.isRegularFile(lastSession)) {
                return PlotConfigIO.load(lastSession);
            }
        } catch (final java.io.IOException | RuntimeException failed) {
            System.err.println("Ignoring unreadable last session " + lastSession + ": "
                    + failed.getMessage());
        }
        return PlotConfig.defaults();
    }

    public static void main(final String[] arguments) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (final ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // The cross-platform look and feel remains available.
            }
            final PlotConfig config = SetupDialog.showDialog(null, initialConfig());
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
