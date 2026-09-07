package nlipse.render;

import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Cache key for data that changes the sampled scalar field. */
record FieldKey(
        CurveType curveType,
        double familyParameter,
        List<Focus> foci,
        SamplingLattice samplingLattice,
        int width,
        int height,
        int sampleStep) {

    FieldKey {
        familyParameter = curveType.normalizeParameter(familyParameter);
        foci = List.copyOf(foci);
    }

    static FieldKey from(final RenderRequest request) {
        final var snapshot = request.snapshot();
        return new FieldKey(
                snapshot.curveType(),
                snapshot.familyParameter(),
                snapshot.foci(),
                snapshot.viewport().samplingLattice(request.width(), request.height()),
                request.width(),
                request.height(),
                request.quality().sampleStep());
    }

    FieldIdentity identity() {
        return new FieldIdentity(curveType, familyParameter, foci);
    }

    FieldKey withSampleStep(final int newSampleStep) {
        return new FieldKey(curveType, familyParameter, foci,
                samplingLattice, width, height, newSampleStep);
    }
}
