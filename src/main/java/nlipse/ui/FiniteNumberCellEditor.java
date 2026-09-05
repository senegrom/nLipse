package nlipse.ui;

import java.awt.Component;
import javax.swing.DefaultCellEditor;
import javax.swing.JTable;
import javax.swing.JTextField;

/** Reject invalid values before JTable can commit them to its model. */
final class FiniteNumberCellEditor extends DefaultCellEditor {
    private static final long serialVersionUID = 1L;

    FiniteNumberCellEditor() {
        super(new JTextField());
        setClickCountToStart(2);
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, final Object value,
            final boolean selected, final int row, final int column) {
        InputValidation.accept((JTextField) getComponent());
        return super.getTableCellEditorComponent(table, value, selected, row, column);
    }

    @Override
    public boolean stopCellEditing() {
        final JTextField field = (JTextField) getComponent();
        try {
            EditableNumbers.parseFinite(field.getText(), "Value");
        } catch (final IllegalArgumentException invalid) {
            InputValidation.reject(field, invalid.getMessage());
            return false;
        }
        InputValidation.accept(field);
        return super.stopCellEditing();
    }
}
