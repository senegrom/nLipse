package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditableNumbersTest {
    @Test
    void finiteValuesRoundTripExactlyAndCanonicalizeZero() {
        final double[] values = {
                0.0, -0.0, Double.MIN_VALUE, Math.nextUp(1.0),
                1.2345678901234567, 1e-300, Double.MAX_VALUE
        };

        for (final double value : values) {
            final double expected = value == 0 ? 0 : value;
            final String text = EditableNumbers.format(value);
            assertEquals(Double.doubleToLongBits(expected),
                    Double.doubleToLongBits(Double.parseDouble(text)), text);
        }
    }

    @Test
    void rejectsValuesThatEditableModelFieldsCannotAccept() {
        assertThrows(IllegalArgumentException.class,
                () -> EditableNumbers.format(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> EditableNumbers.format(Double.POSITIVE_INFINITY));
    }
    @Test
    void parsesFiniteValuesAndNamesInvalidFields() {
        assertEquals(Math.nextUp(1.0),
                EditableNumbers.parseFinite(Double.toString(Math.nextUp(1.0)), "Coordinate"));
        final IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> EditableNumbers.parseFinite("not-a-number", "Coordinate"));
        assertTrue(invalid.getMessage().contains("Coordinate"));
        assertThrows(IllegalArgumentException.class,
                () -> EditableNumbers.parseFinite("Infinity", "Coordinate"));
    }

}
