package nlipse.render;

import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Immutable identity of the scalar field, excluding view and sampling geometry. */
record FieldIdentity(CurveType curveType, double familyParameter, List<Focus> foci) {
    FieldIdentity {
        if (curveType == null || foci == null || foci.isEmpty()) {
            throw new IllegalArgumentException("Curve type and at least one focus are required");
        }
        familyParameter = curveType.normalizeParameter(familyParameter);
        foci = List.copyOf(foci);
    }

}
