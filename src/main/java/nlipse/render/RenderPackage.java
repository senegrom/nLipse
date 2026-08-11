package nlipse.render;

import java.awt.Color;
import java.util.Objects;
import java.util.Optional;
import nlipse.model.PlotSnapshot;

/**
 * Immutable geometry and presentation data produced by one completed render.
 *
 * <p>Raster display, legends and vector export consume this same package so
 * they cannot disagree about the snapshot, deduplicated contour levels,
 * colours, extrema or stitched contour topology.</p>
 */
public final class RenderPackage {
    private final PlotSnapshot snapshot;
    private final int width;
    private final int height;
    private final RenderQuality quality;
    private final double[] levels;
    private final Color[] levelColors;
    private final Optional<FieldExtrema> extrema;
    private final ContourGeometry contours;

    RenderPackage(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality, final double[] levels, final Color[] levelColors,
            final Optional<FieldExtrema> extrema, final ContourGeometry contours) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.extrema = Objects.requireNonNull(extrema, "extrema");
        this.contours = Objects.requireNonNull(contours, "contours");
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Render size must be at least 2 by 2");
        }
        if (levels == null || levelColors == null || levels.length != levelColors.length) {
            throw new IllegalArgumentException("Contour levels and colours must have equal lengths");
        }
        if (contours.levelCount() != levels.length) {
            throw new IllegalArgumentException("Contour topology must match the contour levels");
        }
        this.width = width;
        this.height = height;
        this.levels = levels.clone();
        this.levelColors = levelColors.clone();
        for (final Color color : this.levelColors) {
            Objects.requireNonNull(color, "level color");
        }
    }

    public PlotSnapshot snapshot() {
        return snapshot;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public RenderQuality quality() {
        return quality;
    }

    public int levelCount() {
        return levels.length;
    }

    public double level(final int index) {
        return levels[index];
    }

    public Color levelColor(final int index) {
        return levelColors[index];
    }

    public double[] levels() {
        return levels.clone();
    }

    public Color[] levelColors() {
        return levelColors.clone();
    }

    public Optional<FieldExtrema> extrema() {
        return extrema;
    }

    ContourGeometry contours() {
        return contours;
    }
}
