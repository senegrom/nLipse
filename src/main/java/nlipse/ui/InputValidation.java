package nlipse.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.border.Border;

/** Non-destructive, accessible feedback; validation never replaces the user's text. */
final class InputValidation {
    private static final String ORIGINAL = "nlipse.validation.original";

    private record Presentation(Border border, String tooltip, String description) {
    }

    private InputValidation() {
    }

    static void reject(final JTextField field, final String message) {
        if (field.getClientProperty(ORIGINAL) == null) {
            field.putClientProperty(ORIGINAL, new Presentation(field.getBorder(),
                    field.getToolTipText(), field.getAccessibleContext().getAccessibleDescription()));
        }
        field.setBorder(BorderFactory.createLineBorder(new Color(180, 35, 35)));
        field.setToolTipText(message);
        field.getAccessibleContext().setAccessibleDescription(message);
    }

    static void accept(final JTextField field) {
        if (field.getClientProperty(ORIGINAL) instanceof Presentation original) {
            field.setBorder(original.border());
            field.setToolTipText(original.tooltip());
            field.getAccessibleContext().setAccessibleDescription(original.description());
            field.putClientProperty(ORIGINAL, null);
        }
    }
}
