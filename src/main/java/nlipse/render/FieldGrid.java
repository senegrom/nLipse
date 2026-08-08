package nlipse.render;

import nlipse.geometry.Point2;
import nlipse.math.DistanceField;

/** Reusable sample grid shared by shading, extrema and contour extraction. */
public final class FieldGrid {
    private final int pixelWidth;
    private final int pixelHeight;
    private final int step;
    private final int columns;
    private final int rows;
    private final int[] pixelXs;
    private final int[] pixelYs;
    private final double[] values;
    private final double minValue;
    private final double maxValue;
    private final int minColumn;
    private final int minRow;
    private final int maxColumn;
    private final int maxRow;
    private final Viewport viewport;

    private FieldGrid(final int pixelWidth, final int pixelHeight, final int step,
            final int columns, final int rows, final int[] pixelXs, final int[] pixelYs,
            final double[] values, final double minValue, final double maxValue,
            final int minColumn, final int minRow, final int maxColumn, final int maxRow,
            final Viewport viewport) {
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.step = step;
        this.columns = columns;
        this.rows = rows;
        this.pixelXs = pixelXs;
        this.pixelYs = pixelYs;
        this.values = values;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.minColumn = minColumn;
        this.minRow = minRow;
        this.maxColumn = maxColumn;
        this.maxRow = maxRow;
        this.viewport = viewport;
    }

    public static FieldGrid sample(final DistanceField field, final Viewport viewport,
            final int pixelWidth, final int pixelHeight, final int requestedStep,
            final CancellationToken token) {
        if (field == null || viewport == null || token == null) {
            throw new IllegalArgumentException("Field, viewport and cancellation token are required");
        }
        if (pixelWidth < 2 || pixelHeight < 2) {
            throw new IllegalArgumentException("Grid size must be at least 2 by 2");
        }
        final int step = Math.max(1, requestedStep);
        final int columns = Math.ceilDiv(pixelWidth - 1, step) + 1;
        final int rows = Math.ceilDiv(pixelHeight - 1, step) + 1;
        final int[] pixelXs = new int[columns];
        final int[] pixelYs = new int[rows];
        for (int column = 0; column < columns; column++) {
            pixelXs[column] = Math.min(column * step, pixelWidth - 1);
        }
        for (int row = 0; row < rows; row++) {
            pixelYs[row] = Math.min(row * step, pixelHeight - 1);
        }

        final double[] values = new double[columns * rows];
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        int minColumn = 0;
        int minRow = 0;
        int maxColumn = 0;
        int maxRow = 0;

        for (int row = 0; row < rows; row++) {
            if ((row & 7) == 0) {
                token.throwIfCancelled();
            }
            final double worldY = viewport.worldY(pixelYs[row], pixelHeight);
            for (int column = 0; column < columns; column++) {
                final double worldX = viewport.worldX(pixelXs[column], pixelWidth);
                final double value = field.value(worldX, worldY);
                values[row * columns + column] = value;
                if (!Double.isFinite(value)) {
                    continue;
                }
                if (value < minValue) {
                    minValue = value;
                    minColumn = column;
                    minRow = row;
                }
                if (value > maxValue) {
                    maxValue = value;
                    maxColumn = column;
                    maxRow = row;
                }
            }
        }

        if (!Double.isFinite(minValue) || !Double.isFinite(maxValue)) {
            minValue = 0;
            maxValue = 1;
            minColumn = 0;
            minRow = 0;
            maxColumn = columns - 1;
            maxRow = rows - 1;
        } else if (maxValue <= minValue) {
            maxValue = minValue + 1;
        }

        return new FieldGrid(pixelWidth, pixelHeight, step, columns, rows,
                pixelXs, pixelYs, values, minValue, maxValue,
                minColumn, minRow, maxColumn, maxRow, viewport);
    }

    public int getPixelWidth() {
        return pixelWidth;
    }

    public int getPixelHeight() {
        return pixelHeight;
    }

    public int getStep() {
        return step;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getPixelX(final int column) {
        return pixelXs[column];
    }

    public int getPixelY(final int row) {
        return pixelYs[row];
    }

    public double getValue(final int column, final int row) {
        return values[row * columns + column];
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public Point2 getMinPoint() {
        return new Point2(viewport.worldX(pixelXs[minColumn], pixelWidth),
                viewport.worldY(pixelYs[minRow], pixelHeight));
    }

    public Point2 getMaxPoint() {
        return new Point2(viewport.worldX(pixelXs[maxColumn], pixelWidth),
                viewport.worldY(pixelYs[maxRow], pixelHeight));
    }

    public double interpolateAtPixel(final double pixelX, final double pixelY) {
        final double clampedX = Math.clamp(pixelX, 0, pixelWidth - 1.0);
        final double clampedY = Math.clamp(pixelY, 0, pixelHeight - 1.0);
        final int left = locate(pixelXs, clampedX);
        final int top = locate(pixelYs, clampedY);
        final int right = Math.min(left + 1, columns - 1);
        final int bottom = Math.min(top + 1, rows - 1);
        final double x0 = pixelXs[left];
        final double x1 = pixelXs[right];
        final double y0 = pixelYs[top];
        final double y1 = pixelYs[bottom];
        final double tx = x1 == x0 ? 0 : (clampedX - x0) / (x1 - x0);
        final double ty = y1 == y0 ? 0 : (clampedY - y0) / (y1 - y0);
        final double a = getValue(left, top);
        final double b = getValue(right, top);
        final double c = getValue(right, bottom);
        final double d = getValue(left, bottom);
        if (!Double.isFinite(a) || !Double.isFinite(b)
                || !Double.isFinite(c) || !Double.isFinite(d)) {
            return Double.NaN;
        }
        final double topValue = a + (b - a) * tx;
        final double bottomValue = d + (c - d) * tx;
        return topValue + (bottomValue - topValue) * ty;
    }

    private static int locate(final int[] coordinates, final double value) {
        int low = 0;
        int high = coordinates.length - 1;
        while (low + 1 < high) {
            final int middle = (low + high) >>> 1;
            if (coordinates[middle] <= value) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
