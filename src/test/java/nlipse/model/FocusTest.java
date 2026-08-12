package nlipse.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FocusTest {
    @Test
    void canonicalizesSignedZeroForStableEqualityAndCacheIdentity() {
        final Focus negativeZero = new Focus(-0.0, -0.0, -0.0);
        final Focus positiveZero = new Focus(0.0, 0.0, 0.0);

        assertEquals(positiveZero, negativeZero);
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
        assertEquals(0L, Double.doubleToRawLongBits(negativeZero.x()));
        assertEquals(0L, Double.doubleToRawLongBits(negativeZero.y()));
        assertEquals(0L, Double.doubleToRawLongBits(negativeZero.weight()));
    }
}
