package nlipse.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nlipse.render.Viewport;

/** Immutable model snapshot safe to pass to a background renderer. */
public final class PlotSnapshot {
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
    private final int selectedFocusIndex;

    public PlotSnapshot(final CurveType curveType, final List<Focus> foci,
            final double distanceMin, final double distanceMax, final int curveCount,
            final Viewport viewport, final boolean showBackground, final boolean showExtrema,
            final boolean antiAlias, final boolean logSpacing, final int selectedFocusIndex) {
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
        this.selectedFocusIndex = selectedFocusIndex;
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

    public int getSelectedFocusIndex() {
        return selectedFocusIndex;
    }
}
