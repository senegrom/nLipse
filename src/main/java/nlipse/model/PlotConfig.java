package nlipse.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nlipse.render.Viewport;

/** Immutable initial application configuration. */
public final class PlotConfig {
    private final CurveType curveType;
    private final List<Focus> foci;
    private final double distanceMin;
    private final double distanceMax;
    private final int curveCount;
    private final Viewport viewport;
    private final boolean showBackground;
    private final boolean showExtrema;
    private final boolean antiAlias;
    private final boolean logSpacing;

    public PlotConfig(final CurveType curveType, final List<Focus> foci,
            final double distanceMin, final double distanceMax, final int curveCount,
            final Viewport viewport, final boolean showBackground, final boolean showExtrema,
            final boolean antiAlias, final boolean logSpacing) {
        if (curveType == null) {
            throw new IllegalArgumentException("Curve type is required");
        }
        if (foci == null || foci.isEmpty()) {
            throw new IllegalArgumentException("At least one focus is required");
        }
        if (!Double.isFinite(distanceMin) || !Double.isFinite(distanceMax) || distanceMin > distanceMax) {
            throw new IllegalArgumentException("Distance range must be finite and ordered");
        }
        if (curveCount < 1 || curveCount > 200) {
            throw new IllegalArgumentException("Curve count must be between 1 and 200");
        }
        if (viewport == null) {
            throw new IllegalArgumentException("Viewport is required");
        }
        this.curveType = curveType;
        this.foci = Collections.unmodifiableList(new ArrayList<>(foci));
        this.distanceMin = distanceMin;
        this.distanceMax = distanceMax;
        this.curveCount = curveCount;
        this.viewport = viewport;
        this.showBackground = showBackground;
        this.showExtrema = showExtrema;
        this.antiAlias = antiAlias;
        this.logSpacing = logSpacing;
    }

    public static PlotConfig defaults() {
        final List<Focus> defaultFoci = new ArrayList<>();
        defaultFoci.add(new Focus(2, 0, 1));
        defaultFoci.add(new Focus(0, 1, 1));
        defaultFoci.add(new Focus(-1, -1.5, 1));
        return new PlotConfig(CurveType.LIPSE, defaultFoci, 14.5, 45, 12,
                new Viewport(-3, 3, -3, 3), true, true, true, false);
    }

    public CurveType getCurveType() {
        return curveType;
    }

    public List<Focus> getFoci() {
        return foci;
    }

    public double getDistanceMin() {
        return distanceMin;
    }

    public double getDistanceMax() {
        return distanceMax;
    }

    public int getCurveCount() {
        return curveCount;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public boolean isShowBackground() {
        return showBackground;
    }

    public boolean isShowExtrema() {
        return showExtrema;
    }

    public boolean isAntiAlias() {
        return antiAlias;
    }

    public boolean isLogSpacing() {
        return logSpacing;
    }
}
