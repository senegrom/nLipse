package nlipse.model;

import java.util.List;
import java.util.Objects;
import nlipse.render.Viewport;

/** Immutable model snapshot safe to pass to a background renderer. */
public record PlotSnapshot(
        CurveType curveType,
        double familyParameter,
        List<Focus> foci,
        double distanceMin,
        double distanceMax,
        int curveCount,
        Viewport viewport,
        boolean showBackground,
        boolean showExtrema,
        boolean antiAlias,
        boolean logSpacing,
        boolean showLegend,
        int selectedFocusIndex) {

    public PlotSnapshot {
        Objects.requireNonNull(curveType, "curveType");
        Objects.requireNonNull(foci, "foci");
        Objects.requireNonNull(viewport, "viewport");
        familyParameter = curveType.normalizeParameter(familyParameter);
        if (foci.isEmpty() || foci.size() > PlotConfig.MAX_FOCI) {
            throw new IllegalArgumentException("Focus count must be between 1 and "
                    + PlotConfig.MAX_FOCI);
        }
        if (foci.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Focus list must not contain nulls");
        }
        if (!Double.isFinite(distanceMin) || !Double.isFinite(distanceMax)
                || distanceMin > distanceMax) {
            throw new IllegalArgumentException("Field level range must be finite and ordered");
        }
        distanceMin = distanceMin == 0 ? 0 : distanceMin;
        distanceMax = distanceMax == 0 ? 0 : distanceMax;
        if (curveCount < 1 || curveCount > PlotConfig.MAX_CURVES) {
            throw new IllegalArgumentException("Curve count must be between 1 and "
                    + PlotConfig.MAX_CURVES);
        }
        if (selectedFocusIndex < -1 || selectedFocusIndex >= foci.size()) {
            throw new IllegalArgumentException("Selected focus index is out of range");
        }
        foci = List.copyOf(foci);
    }

}
