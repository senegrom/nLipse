package nlipse.model;

import java.util.ArrayList;
import java.util.List;
import nlipse.render.Viewport;

/** Mutable application state. All UI mutations are expected on Swing's EDT. */
public final class PlotModel {
    private CurveType curveType;
    private double familyParameter;
    private final List<Focus> foci;
    private double distanceMin;
    private double distanceMax;
    private int curveCount;
    private Viewport viewport;
    private Viewport defaultViewport;
    private boolean showBackground;
    private boolean showExtrema;
    private boolean antiAlias;
    private boolean logSpacing;
    private boolean showLegend;
    private int selectedFocusIndex;

    public PlotModel(final PlotConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration is required");
        }
        curveType = config.curveType();
        familyParameter = config.familyParameter();
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
        showLegend = config.showLegend();
        selectedFocusIndex = -1;
    }

    public synchronized PlotSnapshot snapshot() {
        return new PlotSnapshot(curveType, familyParameter, foci,
                distanceMin, distanceMax, curveCount, viewport,
                showBackground, showExtrema, antiAlias, logSpacing, showLegend,
                selectedFocusIndex);
    }

    /** Replace the whole state with a loaded setup; the loaded viewport also
     *  becomes the new "Reset view" target. Selection is cleared. */
    public synchronized void apply(final PlotConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration is required");
        }
        curveType = config.curveType();
        familyParameter = config.familyParameter();
        foci.clear();
        foci.addAll(config.foci());
        distanceMin = config.distanceMin();
        distanceMax = config.distanceMax();
        curveCount = config.curveCount();
        viewport = config.viewport();
        defaultViewport = config.viewport();
        showBackground = config.showBackground();
        showExtrema = config.showExtrema();
        antiAlias = config.antiAlias();
        logSpacing = config.logSpacing();
        showLegend = config.showLegend();
        selectedFocusIndex = -1;
    }

    public synchronized CurveType getCurveType() {
        return curveType;
    }

    /** Switching family also restores that family's valid default parameter. */
    public synchronized void setCurveType(final CurveType newCurveType) {
        if (newCurveType == null) {
            throw new IllegalArgumentException("Curve type is required");
        }
        curveType = newCurveType;
        familyParameter = newCurveType.defaultParameter();
    }

    public synchronized double getFamilyParameter() {
        return familyParameter;
    }

    public synchronized void setFamilyParameter(final double value) {
        familyParameter = curveType.normalizeParameter(value);
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
        if (foci.size() >= PlotConfig.MAX_FOCI) {
            throw new IllegalStateException("At most " + PlotConfig.MAX_FOCI
                    + " focus points are supported");
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
            // Deleting the selection leaves nothing selected; auto-selecting a
            // neighbour would let a repeated Delete remove foci the user never chose.
            selectedFocusIndex = -1;
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
            throw new IllegalArgumentException("Field level range must be finite and ordered");
        }
        distanceMin = min == 0 ? 0 : min;
        distanceMax = max == 0 ? 0 : max;
    }

    public synchronized int getCurveCount() {
        return curveCount;
    }

    public synchronized void setCurveCount(final int count) {
        if (count < 1 || count > PlotConfig.MAX_CURVES) {
            throw new IllegalArgumentException("Curve count must be between 1 and "
                    + PlotConfig.MAX_CURVES);
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

    public synchronized void setShowBackground(final boolean value) {
        showBackground = value;
    }

    public synchronized void setShowExtrema(final boolean value) {
        showExtrema = value;
    }

    public synchronized void setAntiAlias(final boolean value) {
        antiAlias = value;
    }

    public synchronized void setLogSpacing(final boolean value) {
        logSpacing = value;
    }

    public synchronized void setShowLegend(final boolean value) {
        showLegend = value;
    }
}
