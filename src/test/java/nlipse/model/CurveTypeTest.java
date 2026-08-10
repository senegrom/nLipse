package nlipse.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            assertTrue(type.htmlDescription(200).contains(type.description()));
            assertTrue(CurveType.validNames().contains(type.name()));
        }
    }

    @Test
    void onlyCassiniDefaultsToLogarithmicLevels() {
        for (final CurveType type : CurveType.values()) {
            assertEquals(type == CurveType.CASSIN, type.defaultLogSpacing());
        }
    }
}
