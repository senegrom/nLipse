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
}
