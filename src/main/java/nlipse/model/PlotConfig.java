package nlipse.model;

import java.util.List;
import java.util.Objects;
import nlipse.render.Viewport;

/** Immutable initial application configuration. */
public record PlotConfig(
        CurveType curveType,
        List<Focus> foci,
        double distanceMin,
        double distanceMax,
        int curveCount,
        Viewport viewport,
        boolean showBackground,
        boolean showExtrema,
        boolean antiAlias,
        boolean logSpacing) {

    public PlotConfig {
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
            throw new IllegalArgumentException("Distance range must be finite and ordered");
        }
        if (curveCount < 1 || curveCount > 200) {
            throw new IllegalArgumentException("Curve count must be between 1 and 200");
        }
        foci = List.copyOf(foci);
    }

    public static PlotConfig defaults() {
        return new PlotConfig(
                CurveType.LIPSE,
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
                false);
    }
}
