package Main;

import javax.swing.SwingUtilities;

/**
 * @author CGH
 * @version 0.2.3
 */
public class Main {
	public final static String	version		= "0.2.3";

	public static void main(final String[] args) {
		SwingUtilities.invokeLater(() -> {
			final PlotConfig cfg = PlotConfig.defaults();
			final SetupDialog dlg = new SetupDialog(cfg);
			dlg.setOnConfirm(c -> new PlotWindow(c));
			dlg.setOnCancel(() -> System.exit(0));
			dlg.setVisible(true);
			dlg.toFront();
			dlg.requestFocus();
		});
	}
}
