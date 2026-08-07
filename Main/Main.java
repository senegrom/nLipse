package Main;

import javax.swing.SwingUtilities;

/**
 * Application entry point.
 *
 * @author CGH
 * @version 0.2.3
 */
public final class Main {
	public static final String PROGRAM_NAME = "nLipse";
	public static final String VERSION = "0.2.3";

	private Main() {
	}

	public static void main(final String[] args) {
		SwingUtilities.invokeLater(() -> {
			final PlotConfig cfg = PlotConfig.defaults();
			final SetupDialog dlg = new SetupDialog(cfg);
			dlg.setOnConfirm(PlotWindow::new);
			dlg.setOnCancel(() -> System.exit(0));
			dlg.setVisible(true);
			dlg.toFront();
			dlg.requestFocus();
		});
	}
}
