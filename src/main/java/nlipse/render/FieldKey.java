package nlipse.render;

import java.util.List;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Cache key for data that changes the sampled scalar field. */
record FieldKey(
        CurveType curveType,
        List<Focus> foci,
        Viewport viewport,
        int width,
        int height,
        int sampleStep) {

    FieldKey {
        foci = List.copyOf(foci);
    }

    static FieldKey from(final RenderRequest request) {
        final var snapshot = request.snapshot();
        return new FieldKey(
                snapshot.curveType(),
                snapshot.foci(),
                snapshot.viewport(),
                request.width(),
                request.height(),
                request.quality().sampleStep());
    }

    FieldKey withSampleStep(final int newSampleStep) {
        return new FieldKey(curveType, foci, viewport, width, height, newSampleStep);
    }
}
