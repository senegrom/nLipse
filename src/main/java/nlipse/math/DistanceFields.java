package nlipse.math;

import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Validates focal input and dispatches to immutable field implementations. */
public final class DistanceFields {
    private DistanceFields() {
    }

    public static DistanceField create(final CurveType type, final List<Focus> foci) {
        return create(type, foci, type == null ? 1 : type.defaultParameter());
    }

    public static DistanceField create(final CurveType type, final List<Focus> foci,
            final double familyParameter) {
        return create(type, foci, familyParameter, ExactBudget.unlimited());
    }

    /** Creates a field with an isolated precision-fallback allowance. */
    public static DistanceField create(final CurveType type, final List<Focus> foci,
            final double familyParameter, final ExactBudget exactBudget) {
        if (type == null) {
            throw new IllegalArgumentException("Curve type is required");
        }
        if (foci == null || foci.isEmpty()) {
            throw new IllegalArgumentException("At least one focus is required");
        }
        final FocusSet focusSet = FocusSet.from(foci, exactBudget);
        final double parameter = type.normalizeParameter(familyParameter);
        return switch (type) {
            case LIPSE -> AggregateFields.sum(focusSet);
            case CASSIN -> RadialFields.cassini(focusSet);
            case HYPERB -> AggregateFields.hyperbola(focusSet);
            case NEAREST -> RadialFields.envelope(focusSet, true);
            case FARTHEST -> RadialFields.envelope(focusSet, false);
            case QUADRATIC -> RadialFields.quadratic(focusSet);
            case RANGE -> AggregateFields.range(focusSet);
            case POTENTIAL -> RadialFields.potential(focusSet);
            case POWER_MEAN -> AggregateFields.powerMean(focusSet, parameter);
            case MEDIAN -> AggregateFields.median(focusSet);
            case SMOOTH_NEAREST -> AggregateFields.smoothEnvelope(focusSet, parameter, true);
            case SMOOTH_FARTHEST -> AggregateFields.smoothEnvelope(focusSet, parameter, false);
            case GAUSSIAN -> RadialFields.gaussian(focusSet, parameter);
        };
    }
}
