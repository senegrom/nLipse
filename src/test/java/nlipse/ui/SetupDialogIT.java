package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import nlipse.model.Focus;
import nlipse.model.PlotConfig;
import org.junit.jupiter.api.Test;

/** The setup dialog's focus table under real Swing editing, run in CI under Xvfb. */
class SetupDialogIT {
    @Test
    void removingTheEditedRowDropsItsEditInsteadOfMovingItToTheNextRow() throws Exception {
        withDialog(dialog -> {
            edit(dialog, 1, "99");
            dialog.focusTable.setRowSelectionInterval(1, 1);
            dialog.removeSelectedFoci();
            assertFalse(dialog.focusTable.isEditing());
            assertRows(dialog, 1, 10, 3, 30);
        });
    }

    @Test
    void removingTheLastRowWhileEditingItLeavesAConsistentTable() throws Exception {
        withDialog(dialog -> {
            edit(dialog, 2, "99");
            dialog.focusTable.setRowSelectionInterval(2, 2);
            dialog.removeSelectedFoci();
            assertFalse(dialog.focusTable.isEditing());
            assertRows(dialog, 1, 10, 2, 20);
        });
    }

    @Test
    void anEditInAnotherRowIsCommittedToThatRowBeforeTheRemoval() throws Exception {
        withDialog(dialog -> {
            edit(dialog, 2, "77");
            dialog.focusTable.setRowSelectionInterval(0, 0);
            dialog.removeSelectedFoci();
            assertRows(dialog, 2, 20, 77, 30);
        });
    }

    @Test
    void everySelectedRowIsRemoved() throws Exception {
        withDialog(dialog -> {
            dialog.focusTable.setRowSelectionInterval(0, 0);
            dialog.focusTable.addRowSelectionInterval(2, 2);
            dialog.removeSelectedFoci();
            assertRows(dialog, 2, 20);
        });
    }

    private static void edit(final SetupDialog dialog, final int row, final String text) {
        dialog.focusTable.editCellAt(row, 0);
        ((JTextField) dialog.focusTable.getEditorComponent()).setText(text);
    }

    /** Expected rows as x, y pairs, compared by value (the table shows 1.0 for 1). */
    private static void assertRows(final SetupDialog dialog, final double... xy) {
        assertEquals(xy.length / 2, dialog.focusModel.getRowCount());
        for (int row = 0; row < xy.length / 2; row++) {
            assertEquals(xy[2 * row], value(dialog, row, 0), "row " + row + " x");
            assertEquals(xy[2 * row + 1], value(dialog, row, 1), "row " + row + " y");
        }
    }

    private static double value(final SetupDialog dialog, final int row, final int column) {
        return Double.parseDouble(String.valueOf(dialog.focusModel.getValueAt(row, column)));
    }

    private static void withDialog(final Consumer<SetupDialog> action) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final PlotConfig defaults = PlotConfig.defaults();
            final PlotConfig config = new PlotConfig(defaults.curveType(), defaults.familyParameter(),
                    List.of(new Focus(1, 10, 1), new Focus(2, 20, 1), new Focus(3, 30, 1)),
                    defaults.distanceMin(), defaults.distanceMax(), defaults.curveCount(),
                    defaults.viewport(), defaults.showBackground(), defaults.showExtrema(),
                    defaults.antiAlias(), defaults.logSpacing(), defaults.showLegend());
            final SetupDialog dialog = new SetupDialog(null, config);
            try {
                action.accept(dialog);
            } finally {
                dialog.dispose();
            }
        });
    }
}
