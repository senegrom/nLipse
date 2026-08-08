package nlipse.render;

import java.util.stream.IntStream;
import nlipse.geometry.Point2;
import nlipse.math.DistanceField;

/** Reusable sample grid shared by shading, extrema and contour extraction. */
public final class FieldGrid {
    private static final long PARALLEL_SAMPLE_THRESHOLD = 128L * 1024;

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
        token.throwIfCancelled();

        final int step = Math.max(1, requestedStep);
        final int columns = Math.ceilDiv(pixelWidth - 1, step) + 1;
        final int rows = Math.ceilDiv(pixelHeight - 1, step) + 1;
        final int[] pixelXs = new int[columns];
        final int[] pixelYs = new int[rows];
        final double[] worldXs = new double[columns];
        final double[] worldYs = new double[rows];
        for (int column = 0; column < columns; column++) {
            final int pixelX = Math.min(column * step, pixelWidth - 1);
            pixelXs[column] = pixelX;
            worldXs[column] = viewport.worldX(pixelX, pixelWidth);
        }
        for (int row = 0; row < rows; row++) {
            final int pixelY = Math.min(row * step, pixelHeight - 1);
            pixelYs[row] = pixelY;
            worldYs[row] = viewport.worldY(pixelY, pixelHeight);
        }

        final double[] values = new double[columns * rows];
        final RowExtrema[] rowExtrema = new RowExtrema[rows];
        final IntStream rowIndexes = IntStream.range(0, rows);
        if ((long) columns * rows >= PARALLEL_SAMPLE_THRESHOLD
                && Runtime.getRuntime().availableProcessors() > 1) {
            rowIndexes.parallel().forEach(row -> sampleRow(field, worldXs, worldYs[row],
                    columns, row, values, rowExtrema, token));
        } else {
            rowIndexes.forEach(row -> sampleRow(field, worldXs, worldYs[row],
                    columns, row, values, rowExtrema, token));
        }
        token.throwIfCancelled();

        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        int minColumn = 0;
        int minRow = 0;
        int maxColumn = 0;
        int maxRow = 0;
        for (int row = 0; row < rows; row++) {
            final RowExtrema extrema = rowExtrema[row];
            if (extrema == null || !extrema.valid()) {
                continue;
            }
            if (extrema.minValue() < minValue) {
                minValue = extrema.minValue();
                minColumn = extrema.minColumn();
                minRow = row;
            }
            if (extrema.maxValue() > maxValue) {
                maxValue = extrema.maxValue();
                maxColumn = extrema.maxColumn();
                maxRow = row;
            }
        }

        if (!Double.isFinite(minValue) || !Double.isFinite(maxValue)) {
            minValue = 0;
            maxValue = 1;
            minColumn = 0;
            minRow = 0;
            maxColumn = columns - 1;
            maxRow = rows - 1;
        }

        return new FieldGrid(pixelWidth, pixelHeight, step, columns, rows,
                pixelXs, pixelYs, values, minValue, maxValue,
                minColumn, minRow, maxColumn, maxRow, viewport);
    }

    private static void sampleRow(final DistanceField field, final double[] worldXs,
            final double worldY, final int columns, final int row, final double[] values,
            final RowExtrema[] rowExtrema, final CancellationToken token) {
        if (token.isCancelled()) {
            return;
        }
        final int offset = row * columns;
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        int minColumn = 0;
        int maxColumn = 0;
        boolean valid = false;
        for (int column = 0; column < columns; column++) {
            if ((column & 255) == 0 && token.isCancelled()) {
                return;
            }
            final double value = field.value(worldXs[column], worldY);
            values[offset + column] = value;
            if (!Double.isFinite(value)) {
                continue;
            }
            if (!valid || value < minValue) {
                minValue = value;
                minColumn = column;
            }
            if (!valid || value > maxValue) {
                maxValue = value;
                maxColumn = column;
            }
            valid = true;
        }
        rowExtrema[row] = new RowExtrema(minValue, minColumn, maxValue, maxColumn, valid);
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

    public long estimatedBytes() {
        return 128L + (long) values.length * Double.BYTES
                + (long) (pixelXs.length + pixelYs.length) * Integer.BYTES;
    }

    public Point2 getMinPoint() {
        return new Point2(viewport.worldX(pixelXs[minColumn], pixelWidth),
                viewport.worldY(pixelYs[minRow], pixelHeight));
    }

    public Point2 getMaxPoint() {
        return new Point2(viewport.worldX(pixelXs[maxColumn], pixelWidth),
                viewport.worldY(pixelYs[maxRow], pixelHeight));
    }

    private record RowExtrema(
            double minValue,
            int minColumn,
            double maxValue,
            int maxColumn,
            boolean valid) {
    }
}
