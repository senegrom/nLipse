package nlipse.ui;

/** Exact, locale-independent text for finite values that users can edit and commit. */
final class EditableNumbers {
    private EditableNumbers() {
    }

    static String format(final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Editable values must be finite");
        }
        return Double.toString(value == 0 ? 0 : value);
    }

    static double parseFinite(final Object value, final String field) {
        final String text = String.valueOf(value).trim();
        final double parsed;
        try {
            parsed = Double.parseDouble(text);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(field + ": '" + text + "' is not a number");
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return parsed;
    }
}
