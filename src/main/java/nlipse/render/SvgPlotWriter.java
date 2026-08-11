package nlipse.render;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import nlipse.math.DistanceField;
import nlipse.math.DistanceFields;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** Writes the current plot as a self-contained vector SVG document.
 *
 *  <p>The traced contour polylines, focus markers, extrema markers, axes and
 *  legend match the raster renderer; the heatmap background is deliberately
 *  omitted because it has no vector representation. */
public final class SvgPlotWriter {
    private static final int LEGEND_FONT_SIZE = 12;
    private static final int LEGEND_ROW_HEIGHT = 17;
    private static final double LEGEND_CHAR_WIDTH = 7.2;

    private SvgPlotWriter() {
    }

    public static String write(final PlotSnapshot snapshot, final int width, final int height) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot is required");
        }
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("SVG size must be at least 2x2 pixels");
        }
        final DistanceField field = DistanceFields.create(snapshot.curveType(),
                snapshot.foci(), snapshot.familyParameter());
        final FieldGrid grid = FieldGrid.sample(field, snapshot.viewport(), width, height,
                1, CancellationToken.NONE);
        final double[] levels = grid.getExtrema().isEmpty()
                ? new double[0]
                : PlotRenderer.levels(snapshot.distanceMin(), snapshot.distanceMax(),
                        snapshot.curveCount(), snapshot.logSpacing());
        final ContourGeometry contours = ContourGeometry.trace(grid, field,
                snapshot.viewport(), levels, CancellationToken.NONE);

        final StringBuilder svg = new StringBuilder(16 * 1024);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\">\n");
        appendDescription(svg, snapshot);
        svg.append("<rect width=\"").append(width).append("\" height=\"").append(height)
                .append("\" fill=\"#ffffff\"/>\n");
        appendAxes(svg, snapshot.viewport(), width, height);
        appendContours(svg, snapshot.viewport(), contours, width, height);
        appendFoci(svg, snapshot, width, height);
        if (snapshot.showExtrema()) {
            grid.getExtrema().ifPresent(extrema -> {
                appendMarker(svg, snapshot.viewport(), width, height,
                        extrema.minimumPoint().x(), extrema.minimumPoint().y(),
                        3.5, PlotRenderer.MIN_COLOR);
                appendMarker(svg, snapshot.viewport(), width, height,
                        extrema.maximumPoint().x(), extrema.maximumPoint().y(),
                        3.5, PlotRenderer.MAX_COLOR);
            });
        }
        if (snapshot.showLegend() && levels.length > 0) {
            appendLegend(svg, levels, width);
        }
        svg.append("<rect x=\"0.5\" y=\"0.5\" width=\"").append(width - 1)
                .append("\" height=\"").append(height - 1)
                .append("\" fill=\"none\" stroke=\"#000000\"/>\n");
        return svg.append("</svg>\n").toString();
    }

    private static void appendDescription(final StringBuilder svg, final PlotSnapshot snapshot) {
        svg.append("<title>nLipse — ").append(escape(snapshot.curveType().toString()))
                .append("</title>\n<desc>").append(escape(snapshot.curveType().formula()));
        if (snapshot.curveType().usesParameter()) {
            svg.append(", ").append(escape(snapshot.curveType().parameterLabel())).append(" = ")
                    .append(escape(snapshot.curveType().formatParameter(
                            snapshot.familyParameter())));
        }
        svg.append("; ").append(snapshot.foci().size()).append(" foci; levels ")
                .append(PlotRenderer.formatLevel(snapshot.distanceMin())).append(" to ")
                .append(PlotRenderer.formatLevel(snapshot.distanceMax()))
                .append("</desc>\n");
    }

    private static void appendAxes(final StringBuilder svg, final Viewport viewport,
            final int width, final int height) {
        final String stroke = color(PlotRenderer.AXIS_COLOR);
        if (viewport.yMin() <= 0 && viewport.yMax() >= 0) {
            final double y = viewport.pixelY(0, height);
            svg.append("<line x1=\"0\" y1=\"").append(coordinate(y))
                    .append("\" x2=\"").append(width - 1).append("\" y2=\"").append(coordinate(y))
                    .append("\" stroke=\"").append(stroke).append("\"/>\n");
        }
        if (viewport.xMin() <= 0 && viewport.xMax() >= 0) {
            final double x = viewport.pixelX(0, width);
            svg.append("<line x1=\"").append(coordinate(x)).append("\" y1=\"0\" x2=\"")
                    .append(coordinate(x)).append("\" y2=\"").append(height - 1)
                    .append("\" stroke=\"").append(stroke).append("\"/>\n");
        }
    }

    private static void appendContours(final StringBuilder svg, final Viewport viewport,
            final ContourGeometry contours, final int width, final int height) {
        for (int index = 0; index < contours.levelCount(); index++) {
            final List<ContourGeometry.Polyline> polylines = contours.polylines(index);
            if (polylines.isEmpty()) {
                continue;
            }
            svg.append("<path fill=\"none\" stroke=\"")
                    .append(color(PlotRenderer.curveColor(index, contours.levelCount())))
                    .append("\" stroke-width=\"1.25\" stroke-linecap=\"round\"")
                    .append(" stroke-linejoin=\"round\" d=\"");
            for (final ContourGeometry.Polyline line : polylines) {
                for (int point = 0; point < line.pointCount(); point++) {
                    svg.append(point == 0 ? 'M' : 'L')
                            .append(coordinate(viewport.pixelX(line.x(point), width)))
                            .append(' ')
                            .append(coordinate(viewport.pixelY(line.y(point), height)));
                }
                if (line.closed()) {
                    svg.append('Z');
                }
            }
            svg.append("\"/>\n");
        }
    }

    private static void appendFoci(final StringBuilder svg, final PlotSnapshot snapshot,
            final int width, final int height) {
        for (int index = 0; index < snapshot.foci().size(); index++) {
            final Focus focus = snapshot.foci().get(index);
            final boolean selected = index == snapshot.selectedFocusIndex();
            appendMarker(svg, snapshot.viewport(), width, height, focus.x(), focus.y(),
                    selected ? 5 : 4,
                    selected ? PlotRenderer.SELECTED_FOCUS_COLOR : PlotRenderer.FOCUS_COLOR);
        }
    }

    private static void appendMarker(final StringBuilder svg, final Viewport viewport,
            final int width, final int height, final double worldX, final double worldY,
            final double radius, final Color fill) {
        final double x = viewport.pixelX(worldX, width);
        final double y = viewport.pixelY(worldY, height);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return;
        }
        svg.append("<circle cx=\"").append(coordinate(x)).append("\" cy=\"")
                .append(coordinate(y)).append("\" r=\"").append(coordinate(radius))
                .append("\" fill=\"").append(color(fill))
                .append("\" stroke=\"#ffffff\"/>\n");
    }

    private static void appendLegend(final StringBuilder svg, final double[] levels,
            final int width) {
        final int[] indices = PlotRenderer.legendLevelIndices(levels.length);
        if (indices.length == 0) {
            return;
        }
        int textCharacters = 0;
        for (final int index : indices) {
            textCharacters = Math.max(textCharacters,
                    PlotRenderer.formatLevel(levels[index]).length());
        }
        final int padding = 7;
        final int swatchWidth = 18;
        final int gap = 6;
        final int boxWidth = padding * 2 + swatchWidth + gap
                + (int) Math.ceil(textCharacters * LEGEND_CHAR_WIDTH);
        final int boxHeight = padding * 2 + LEGEND_ROW_HEIGHT * indices.length - 2;
        final int left = width - boxWidth - 8;
        final int top = 8;
        svg.append("<g font-family=\"sans-serif\" font-size=\"").append(LEGEND_FONT_SIZE)
                .append("\">\n<rect x=\"").append(left).append("\" y=\"").append(top)
                .append("\" width=\"").append(boxWidth).append("\" height=\"").append(boxHeight)
                .append("\" rx=\"6\" fill=\"#ffffff\" fill-opacity=\"0.92\" stroke=\"")
                .append(color(PlotRenderer.AXIS_COLOR)).append("\"/>\n");
        for (int row = 0; row < indices.length; row++) {
            final int levelIndex = indices[indices.length - 1 - row];
            final int rowTop = top + padding + row * LEGEND_ROW_HEIGHT;
            final int swatchY = rowTop + LEGEND_ROW_HEIGHT / 2 - 1;
            svg.append("<line x1=\"").append(left + padding).append("\" y1=\"").append(swatchY)
                    .append("\" x2=\"").append(left + padding + swatchWidth)
                    .append("\" y2=\"").append(swatchY)
                    .append("\" stroke=\"")
                    .append(color(PlotRenderer.curveColor(levelIndex, levels.length)))
                    .append("\" stroke-width=\"2.5\" stroke-linecap=\"round\"/>\n")
                    .append("<text x=\"").append(left + padding + swatchWidth + gap)
                    .append("\" y=\"").append(rowTop + LEGEND_FONT_SIZE)
                    .append("\">").append(escape(PlotRenderer.formatLevel(levels[levelIndex])))
                    .append("</text>\n");
        }
        svg.append("</g>\n");
    }

    private static String coordinate(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String color(final Color value) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                value.getRed(), value.getGreen(), value.getBlue());
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
