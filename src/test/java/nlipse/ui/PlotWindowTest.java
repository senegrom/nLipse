package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class PlotWindowTest {
    /**
     * A fixed 800-pixel preferred height made the side panel's scroll pane stop
     * short: the controls need about 1000 pixels, so on smaller screens the
     * lower ones and the status line could not be scrolled into view.
     */
    @Test
    void sidePanelKeepsItsWidthButTakesItsNaturalHeight() {
        final JPanel side = new PlotWindow.SidePanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        final JPanel controls = new JPanel();
        controls.setPreferredSize(new Dimension(100, 1200));
        side.add(controls);

        assertEquals(new Dimension(390, 1200), side.getPreferredSize());
    }
}
