package nlipse.render;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/**
 * Writes a completed render package as a self-contained vector SVG document.
 *
 * <p>The contours, colours, extrema, focus markers, axes and legend are taken
 * from the same immutable package as the raster render. The heatmap background
 * is deliberately omitted because it has no vector representation.</p>
 */
public final class SvgPlotWriter {
    private static final int LEGEND_FONT_SIZE = 12;
    private static final int LEGEND_ROW_HEIGHT = 17;
    private static final double LEGEND_CHAR_WIDTH = 7.2;

    private SvgPlotWriter() {
    }

    public static String write(final RenderPackage completed) {
        if (completed == null) {
            throw new IllegalArgumentException("Completed render package is required");
        }
        if (completed.precisionLimited()) {
            throw new IllegalArgumentException("Precision-limited geometry cannot be exported");
        }
        final PlotSnapshot snapshot = completed.snapshot();
        final int width = completed.width();
        final int height = completed.height();

        final StringBuilder svg = new StringBuilder(16 * 1024);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\">\n");
        appendDescription(svg, completed);
        svg.append("<rect width=\"").append(width).append("\" height=\"").append(height)
                .append("\" fill=\"#ffffff\"/>\n");
        appendAxes(svg, snapshot.viewport(), width, height);
        appendContours(svg, completed);
        appendFoci(svg, snapshot, width, height);
        if (snapshot.showExtrema()) {
            completed.extrema().ifPresent(extrema -> {
                appendMarker(svg, snapshot.viewport(), width, height,
                        extrema.minimumPoint().x(), extrema.minimumPoint().y(),
                        3.5, PlotRenderer.MIN_COLOR);
                appendMarker(svg, snapshot.viewport(), width, height,
                        extrema.maximumPoint().x(), extrema.maximumPoint().y(),
                        3.5, PlotRenderer.MAX_COLOR);
            });
        }
        if (snapshot.showLegend() && completed.levelCount() > 0) {
            appendLegend(svg, completed);
        }
        svg.append("<rect x=\"0.5\" y=\"0.5\" width=\"").append(width - 1)
                .append("\" height=\"").append(height - 1)
                .append("\" fill=\"none\" stroke=\"#000000\"/>\n");
        return svg.append("</svg>\n").toString();
    }

    private static void appendDescription(final StringBuilder svg,
            final RenderPackage completed) {
        final PlotSnapshot snapshot = completed.snapshot();
        svg.append("<title>nLipse — ").append(escape(snapshot.curveType().toString()))
                .append("</title>\n<desc>").append(escape(snapshot.curveType().formula()));
        if (snapshot.curveType().usesParameter()) {
            svg.append(", ").append(escape(snapshot.curveType().parameterLabel())).append(" = ")
                    .append(escape(snapshot.curveType().formatParameter(
                            snapshot.familyParameter())));
        }
        svg.append("; ").append(snapshot.foci().size()).append(" foci");
        if (completed.levelCount() > 0) {
            svg.append("; levels ").append(PlotRenderer.formatLevel(completed.level(0)))
                    .append(" to ")
                    .append(PlotRenderer.formatLevel(
                            completed.level(completed.levelCount() - 1)));
        }
        svg.append("</desc>\n");
    }

    private static void appendAxes(final StringBuilder svg, final Viewport viewport,
            final int width, final int height) {
        final String stroke = color(PlotRenderer.AXIS_COLOR);
        if (viewport.yMin() <= 0 && viewport.yMax() >= 0) {
            final double y = viewport.pixelY(0, height);
            svg.append("<line x1=\"0\" y1=\"").append(coordinate(y))
                    .append("\" x2=\"").append(width - 1).append("\" y2=\"")
                    .append(coordinate(y)).append("\" stroke=\"").append(stroke)
                    .append("\"/>\n");
        }
        if (viewport.xMin() <= 0 && viewport.xMax() >= 0) {
            final double x = viewport.pixelX(0, width);
            svg.append("<line x1=\"").append(coordinate(x)).append("\" y1=\"0\" x2=\"")
                    .append(coordinate(x)).append("\" y2=\"").append(height - 1)
                    .append("\" stroke=\"").append(stroke).append("\"/>\n");
        }
    }

    private static void appendContours(final StringBuilder svg,
            final RenderPackage completed) {
        final Viewport viewport = completed.snapshot().viewport();
        final int width = completed.width();
        final int height = completed.height();
        final ContourGeometry contours = completed.contours();
        for (int index = 0; index < completed.levelCount(); index++) {
            final List<ContourGeometry.Polyline> polylines = contours.polylines(index);
            if (polylines.isEmpty()) {
                continue;
            }
            svg.append("<path fill=\"none\" stroke=\"")
                    .append(color(completed.levelColor(index)))
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

    private static void appendLegend(final StringBuilder svg,
            final RenderPackage completed) {
        final int width = completed.width();
        final int height = completed.height();
        final int availableRows = Math.max(0,
                (height - 16 - 2 * 7 + 2) / LEGEND_ROW_HEIGHT);
        final int[] indices = PlotRenderer.legendLevelIndices(
                completed.levelCount(), availableRows);
        if (indices.length == 0) {
            return;
        }
        int textCharacters = 0;
        for (final int index : indices) {
            textCharacters = Math.max(textCharacters,
                    PlotRenderer.formatLevel(completed.level(index)).length());
        }
        final int padding = 7;
        final int swatchWidth = 18;
        final int gap = 6;
        final int boxWidth = padding * 2 + swatchWidth + gap
                + (int) Math.ceil(textCharacters * LEGEND_CHAR_WIDTH);
        final int boxHeight = padding * 2 + LEGEND_ROW_HEIGHT * indices.length - 2;
        if (boxWidth > width - 16 || boxHeight > height - 16) {
            return;
        }
        final int left = width - boxWidth - 8;
        final int top = 8;
        svg.append("<g font-family=\"sans-serif\" font-size=\"").append(LEGEND_FONT_SIZE)
                .append("\">\n<rect x=\"").append(left).append("\" y=\"").append(top)
                .append("\" width=\"").append(boxWidth).append("\" height=\"")
                .append(boxHeight)
                .append("\" rx=\"6\" fill=\"#ffffff\" fill-opacity=\"0.92\" stroke=\"")
                .append(color(PlotRenderer.AXIS_COLOR)).append("\"/>\n");
        for (int row = 0; row < indices.length; row++) {
            final int levelIndex = indices[indices.length - 1 - row];
            final int rowTop = top + padding + row * LEGEND_ROW_HEIGHT;
            final int swatchY = rowTop + LEGEND_ROW_HEIGHT / 2 - 1;
            svg.append("<line x1=\"").append(left + padding).append("\" y1=\"")
                    .append(swatchY).append("\" x2=\"")
                    .append(left + padding + swatchWidth)
                    .append("\" y2=\"").append(swatchY)
                    .append("\" stroke=\"").append(color(completed.levelColor(levelIndex)))
                    .append("\" stroke-width=\"2.5\" stroke-linecap=\"round\"/>\n")
                    .append("<text x=\"").append(left + padding + swatchWidth + gap)
                    .append("\" y=\"").append(rowTop + LEGEND_FONT_SIZE)
                    .append("\">")
                    .append(escape(PlotRenderer.formatLevel(completed.level(levelIndex))))
                    .append("</text>\n");
        }
        svg.append("</g>\n");
    }

    private static String coordinate(final double value) {
        final double rounded = Math.rint(value * 1_000) / 1_000;
        return Double.toString(rounded == 0 ? 0 : rounded);
    }

    private static String color(final Color value) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                value.getRed(), value.getGreen(), value.getBlue());
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
