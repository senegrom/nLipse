package nlipse.model;

import java.util.List;
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
        // The shared settings are validated and made canonical as a configuration's are
        final PlotConfig settings = new PlotConfig(curveType, familyParameter, foci, distanceMin,
                distanceMax, curveCount, viewport, showBackground, showExtrema, antiAlias,
                logSpacing, showLegend);
        familyParameter = settings.familyParameter();
        foci = settings.foci();
        distanceMin = settings.distanceMin();
        distanceMax = settings.distanceMax();
        if (selectedFocusIndex < -1 || selectedFocusIndex >= foci.size()) {
            throw new IllegalArgumentException("Selected focus index is out of range");
        }
    }

}
