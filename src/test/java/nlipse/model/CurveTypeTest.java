package nlipse.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CurveTypeTest {
    @Test
    void everyFamilyHasDistinctUserFacingDocumentation() {
        assertEquals(CurveType.values().length, Arrays.stream(CurveType.values())
                .map(CurveType::toString).distinct().count());
        for (final CurveType type : CurveType.values()) {
            assertFalse(type.formula().isBlank());
            assertFalse(type.description().isBlank());
            final String html = type.htmlDescription(200, type.defaultParameter());
            assertTrue(html.contains(type.description()));
            assertTrue(html.contains("Weights:"));
            if (type.usesParameter()) {
                assertTrue(html.contains(type.parameterDescription()));
                assertTrue(html.contains(type.formatParameter(type.defaultParameter())));
            }
            assertTrue(CurveType.validNames().contains(type.name()));
        }
    }

    @Test
    void onlyCassiniDefaultsToLogarithmicLevels() {
        for (final CurveType type : CurveType.values()) {
            assertEquals(type == CurveType.CASSIN, type.defaultLogSpacing());
        }
    }

    @Test
    void powerParameterAcceptsIntegerDecimalAndSignedInfinityText() {
        assertEquals(2, CurveType.POWER_MEAN.parseParameter("2"), 0);
        assertEquals(0.5, CurveType.POWER_MEAN.parseParameter("0.5"), 0);
        assertEquals(Double.POSITIVE_INFINITY,
                CurveType.POWER_MEAN.parseParameter("+inf"));
        assertEquals(Double.NEGATIVE_INFINITY,
                CurveType.POWER_MEAN.parseParameter("−∞"));
        assertEquals("+∞", CurveType.POWER_MEAN.formatParameter(Double.POSITIVE_INFINITY));
        assertEquals("−∞", CurveType.POWER_MEAN.formatParameter(Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> CurveType.POWER_MEAN.parseParameter("NaN"));
    }

    @Test
    void scaleParametersMustBeStrictlyPositiveAndFinite() {
        for (final CurveType type : Arrays.asList(CurveType.SMOOTH_NEAREST,
                CurveType.SMOOTH_FARTHEST, CurveType.GAUSSIAN)) {
            assertTrue(type.usesParameter());
            assertEquals(0.25, type.parseParameter("0.25"), 0);
            assertThrows(IllegalArgumentException.class, () -> type.parseParameter("0"));
            assertThrows(IllegalArgumentException.class, () -> type.parseParameter("-1"));
            assertThrows(IllegalArgumentException.class, () -> type.parseParameter("Infinity"));
        }
    }

    @Test
    void nonParameterizedFamiliesCanonicalizeTheUnusedValue() {
        assertFalse(CurveType.LIPSE.usesParameter());
        assertEquals(CurveType.LIPSE.defaultParameter(),
                CurveType.LIPSE.normalizeParameter(12345), 0);
    }
}
