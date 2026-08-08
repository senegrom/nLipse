package nlipse.model;

import java.util.ArrayList;
import java.util.List;
import nlipse.render.Viewport;

/** Mutable application state. All UI mutations are expected on Swing's EDT. */
public final class PlotModel {
    private CurveType curveType;
    private final List<Focus> foci;
    private double distanceMin;
    private double distanceMax;
    private int curveCount;
    private Viewport viewport;
    private final Viewport defaultViewport;
    private boolean showBackground;
    private boolean showExtrema;
    private boolean antiAlias;
    private boolean logSpacing;
    private int selectedFocusIndex;

    public PlotModel(final PlotConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration is required");
        }
        curveType = config.curveType();
        foci = new ArrayList<>(config.foci());
        distanceMin = config.distanceMin();
        distanceMax = config.distanceMax();
        curveCount = config.curveCount();
        viewport = config.viewport();
        defaultViewport = config.viewport();
        showBackground = config.showBackground();
        showExtrema = config.showExtrema();
        antiAlias = config.antiAlias();
        logSpacing = config.logSpacing();
        selectedFocusIndex = -1;
    }

    public synchronized PlotSnapshot snapshot() {
        return new PlotSnapshot(curveType, foci, distanceMin, distanceMax, curveCount,
                viewport, showBackground, showExtrema, antiAlias, logSpacing, selectedFocusIndex);
    }

    public synchronized CurveType getCurveType() {
        return curveType;
    }

    public synchronized void setCurveType(final CurveType newCurveType) {
        if (newCurveType == null) {
            throw new IllegalArgumentException("Curve type is required");
        }
        curveType = newCurveType;
    }

    public synchronized int getFocusCount() {
        return foci.size();
    }

    public synchronized Focus getFocus(final int index) {
        return foci.get(index);
    }

    public synchronized List<Focus> getFociCopy() {
        return List.copyOf(foci);
    }

    public synchronized int addFocus(final Focus focus) {
        if (focus == null) {
            throw new IllegalArgumentException("Focus is required");
        }
        foci.add(focus);
        selectedFocusIndex = foci.size() - 1;
        return selectedFocusIndex;
    }

    public synchronized boolean removeFocus(final int index) {
        if (index < 0 || index >= foci.size() || foci.size() <= 1) {
            return false;
        }
        foci.remove(index);
        if (selectedFocusIndex > index) {
            selectedFocusIndex--;
        } else if (selectedFocusIndex == index) {
            selectedFocusIndex = Math.min(index, foci.size() - 1);
        }
        return true;
    }

    public synchronized void setFocusPosition(final int index, final double x, final double y) {
        final Focus focus = foci.get(index);
        foci.set(index, focus.withPosition(x, y));
    }

    public synchronized void setFocusWeight(final int index, final double weight) {
        final Focus focus = foci.get(index);
        foci.set(index, focus.withWeight(weight));
    }

    public synchronized int getSelectedFocusIndex() {
        return selectedFocusIndex;
    }

    public synchronized void setSelectedFocusIndex(final int index) {
        selectedFocusIndex = index >= 0 && index < foci.size() ? index : -1;
    }

    public synchronized double getDistanceMin() {
        return distanceMin;
    }

    public synchronized double getDistanceMax() {
        return distanceMax;
    }

    public synchronized void setDistanceRange(final double min, final double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("Distance range must be finite and ordered");
        }
        distanceMin = min;
        distanceMax = max;
    }

    public synchronized int getCurveCount() {
        return curveCount;
    }

    public synchronized void setCurveCount(final int count) {
        if (count < 1 || count > 200) {
            throw new IllegalArgumentException("Curve count must be between 1 and 200");
        }
        curveCount = count;
    }

    public synchronized Viewport getViewport() {
        return viewport;
    }

    public synchronized void setViewport(final Viewport newViewport) {
        if (newViewport == null) {
            throw new IllegalArgumentException("Viewport is required");
        }
        viewport = newViewport;
    }

    public synchronized void resetViewport() {
        viewport = defaultViewport;
    }

    public synchronized boolean isShowBackground() {
        return showBackground;
    }

    public synchronized void setShowBackground(final boolean value) {
        showBackground = value;
    }

    public synchronized boolean isShowExtrema() {
        return showExtrema;
    }

    public synchronized void setShowExtrema(final boolean value) {
        showExtrema = value;
    }

    public synchronized boolean isAntiAlias() {
        return antiAlias;
    }

    public synchronized void setAntiAlias(final boolean value) {
        antiAlias = value;
    }

    public synchronized boolean isLogSpacing() {
        return logSpacing;
    }

    public synchronized void setLogSpacing(final boolean value) {
        logSpacing = value;
    }
}
