package nlipse.model;

import java.util.List;
import java.util.Objects;
import nlipse.render.Viewport;

/** Immutable model snapshot safe to pass to a background renderer. */
public record PlotSnapshot(
        CurveType curveType,
        List<Focus> foci,
        double distanceMin,
        double distanceMax,
        int curveCount,
        Viewport viewport,
        boolean showBackground,
        boolean showExtrema,
        boolean antiAlias,
        boolean logSpacing,
        int selectedFocusIndex) {

    public PlotSnapshot {
        Objects.requireNonNull(curveType, "curveType");
        Objects.requireNonNull(foci, "foci");
        Objects.requireNonNull(viewport, "viewport");
        if (foci.isEmpty()) {
            throw new IllegalArgumentException("At least one focus is required");
        }
        if (foci.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Focus list must not contain nulls");
        }
        if (!Double.isFinite(distanceMin) || !Double.isFinite(distanceMax)
                || distanceMin > distanceMax) {
            throw new IllegalArgumentException("Field level range must be finite and ordered");
        }
        if (curveCount < 1 || curveCount > 200) {
            throw new IllegalArgumentException("Curve count must be between 1 and 200");
        }
        if (selectedFocusIndex < -1 || selectedFocusIndex >= foci.size()) {
            throw new IllegalArgumentException("Selected focus index is out of range");
        }
        foci = List.copyOf(foci);
    }
}
