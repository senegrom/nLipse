package nlipse.model;

import java.util.List;
import java.util.Objects;
import nlipse.render.Viewport;

/** Immutable initial application configuration. */
public record PlotConfig(
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
        boolean showLegend) {

    public static final int MAX_FOCI = 1_000;
    public static final int MAX_CURVES = 200;

    public PlotConfig {
        Objects.requireNonNull(curveType, "curveType");
        Objects.requireNonNull(foci, "foci");
        Objects.requireNonNull(viewport, "viewport");
        familyParameter = curveType.normalizeParameter(familyParameter);
        if (foci.isEmpty() || foci.size() > MAX_FOCI) {
            throw new IllegalArgumentException("Focus count must be between 1 and " + MAX_FOCI);
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
        if (curveCount < 1 || curveCount > MAX_CURVES) {
            throw new IllegalArgumentException("Curve count must be between 1 and " + MAX_CURVES);
        }
        foci = List.copyOf(foci);
    }

    public static PlotConfig defaults() {
        return new PlotConfig(
                CurveType.LIPSE,
                CurveType.LIPSE.defaultParameter(),
                List.of(
                        new Focus(2, 0, 1),
                        new Focus(0, 1, 1),
                        new Focus(-1, -1.5, 1)),
                14.5,
                45,
                12,
                new Viewport(-3, 3, -3, 3),
                true,
                true,
                true,
                false,
                true);
    }
}
