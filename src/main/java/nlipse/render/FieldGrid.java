package nlipse.render;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.RecursiveAction;
import nlipse.geometry.Point2;
import nlipse.math.DistanceField;

/** Reusable sample grid shared by shading, extrema and contour extraction. */
public final class FieldGrid {
    private static final long PARALLEL_SAMPLE_THRESHOLD = 128L * 1024;
    private static final int ROWS_PER_TASK = 16;

    private final int pixelWidth;
    private final int pixelHeight;
    private final int step;
    private final int columns;
    private final int rows;
    private final int[] pixelXs;
    private final int[] pixelYs;
    private final double[] values;
    private final Optional<FieldExtrema> extrema;
    private final WeakReference<FieldGrid> sampleSource;

    private FieldGrid(final int pixelWidth, final int pixelHeight, final int step,
            final int columns, final int rows, final int[] pixelXs, final int[] pixelYs,
            final double[] values, final Optional<FieldExtrema> extrema,
            final FieldGrid sampleSource) {
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.step = step;
        this.columns = columns;
        this.rows = rows;
        this.pixelXs = pixelXs;
        this.pixelYs = pixelYs;
        this.values = values;
        this.extrema = extrema;
        this.sampleSource = sampleSource == null ? null : new WeakReference<>(sampleSource);
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
        final double[] rowMinima = new double[rows];
        final double[] rowMaxima = new double[rows];
        final int[] rowMinColumns = new int[rows];
        final int[] rowMaxColumns = new int[rows];
        final boolean[] rowValid = new boolean[rows];
        if ((long) columns * rows >= PARALLEL_SAMPLE_THRESHOLD && SamplingPool.parallelism() > 1) {
            SamplingPool.invoke(new SampleRowsTask(field, worldXs, worldYs, columns,
                    0, rows, values, rowMinima, rowMaxima,
                    rowMinColumns, rowMaxColumns, rowValid, token));
        } else {
            sampleRows(field, worldXs, worldYs, columns, 0, rows, values,
                    rowMinima, rowMaxima, rowMinColumns, rowMaxColumns, rowValid, token);
        }
        token.throwIfCancelled();

        final Optional<FieldExtrema> extrema = extremaFromRows(viewport, pixelWidth, pixelHeight,
                pixelXs, pixelYs, rowMinima, rowMaxima,
                rowMinColumns, rowMaxColumns, rowValid);
        return new FieldGrid(pixelWidth, pixelHeight, step, columns, rows,
                pixelXs, pixelYs, values, extrema, null);
    }

    /**
     * Builds a coarser grid by selecting samples from this grid without evaluating the field again.
     */
    public FieldGrid coarsen(final int requestedStep, final Viewport viewport) {
        if (viewport == null) {
            throw new IllegalArgumentException("Viewport is required");
        }
        final int targetStep = Math.max(1, requestedStep);
        if (targetStep == step) {
            return this;
        }
        if (targetStep < step || targetStep % step != 0) {
            throw new IllegalArgumentException("Target step must be a multiple of the source step");
        }

        final int targetColumns = Math.ceilDiv(pixelWidth - 1, targetStep) + 1;
        final int targetRows = Math.ceilDiv(pixelHeight - 1, targetStep) + 1;
        final int[] targetPixelXs = new int[targetColumns];
        final int[] targetPixelYs = new int[targetRows];
        final double[] targetValues = new double[targetColumns * targetRows];
        final double[] rowMinima = new double[targetRows];
        final double[] rowMaxima = new double[targetRows];
        final int[] rowMinColumns = new int[targetRows];
        final int[] rowMaxColumns = new int[targetRows];
        final boolean[] rowValid = new boolean[targetRows];

        for (int column = 0; column < targetColumns; column++) {
            targetPixelXs[column] = Math.min(column * targetStep, pixelWidth - 1);
        }
        for (int row = 0; row < targetRows; row++) {
            final int pixelY = Math.min(row * targetStep, pixelHeight - 1);
            targetPixelYs[row] = pixelY;
            final int sourceRow = sourceIndex(pixelY, pixelHeight, rows);
            final int targetOffset = row * targetColumns;
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            int minimumColumn = 0;
            int maximumColumn = 0;
            boolean valid = false;
            for (int column = 0; column < targetColumns; column++) {
                final int sourceColumn = sourceIndex(targetPixelXs[column], pixelWidth, columns);
                final double value = values[sourceRow * columns + sourceColumn];
                targetValues[targetOffset + column] = value;
                if (!Double.isFinite(value)) {
                    continue;
                }
                if (!valid || value < minimum) {
                    minimum = value;
                    minimumColumn = column;
                }
                if (!valid || value > maximum) {
                    maximum = value;
                    maximumColumn = column;
                }
                valid = true;
            }
            rowMinima[row] = minimum;
            rowMaxima[row] = maximum;
            rowMinColumns[row] = minimumColumn;
            rowMaxColumns[row] = maximumColumn;
            rowValid[row] = valid;
        }

        final Optional<FieldExtrema> targetExtrema = extremaFromRows(viewport,
                pixelWidth, pixelHeight, targetPixelXs, targetPixelYs,
                rowMinima, rowMaxima, rowMinColumns, rowMaxColumns, rowValid);
        final FieldGrid rootSource = sampleSource == null ? this
                : Optional.ofNullable(sampleSource.get()).orElse(this);
        return new FieldGrid(pixelWidth, pixelHeight, targetStep,
                targetColumns, targetRows, targetPixelXs, targetPixelYs,
                targetValues, targetExtrema, rootSource);
    }

    private int sourceIndex(final int pixelCoordinate, final int pixelResolution,
            final int sampleCount) {
        if (pixelCoordinate == pixelResolution - 1) {
            return sampleCount - 1;
        }
        return pixelCoordinate / step;
    }

    static FieldGrid fromFullResolutionValues(final Viewport viewport, final int pixelWidth,
            final int pixelHeight, final double[] values, final CancellationToken token) {
        if (viewport == null || values == null || token == null) {
            throw new IllegalArgumentException(
                    "Viewport, sampled values and cancellation token are required");
        }
        if (pixelWidth < 2 || pixelHeight < 2
                || values.length != Math.multiplyExact(pixelWidth, pixelHeight)) {
            throw new IllegalArgumentException("Full-resolution values must match the grid size");
        }
        final int[] pixelXs = new int[pixelWidth];
        final int[] pixelYs = new int[pixelHeight];
        final double[] rowMinima = new double[pixelHeight];
        final double[] rowMaxima = new double[pixelHeight];
        final int[] rowMinColumns = new int[pixelHeight];
        final int[] rowMaxColumns = new int[pixelHeight];
        final boolean[] rowValid = new boolean[pixelHeight];
        for (int column = 0; column < pixelWidth; column++) {
            pixelXs[column] = column;
        }
        for (int row = 0; row < pixelHeight; row++) {
            pixelYs[row] = row;
        }
        if ((long) pixelWidth * pixelHeight >= PARALLEL_SAMPLE_THRESHOLD
                && SamplingPool.parallelism() > 1) {
            SamplingPool.invoke(new ScanRowsTask(values, pixelWidth, 0, pixelHeight,
                    rowMinima, rowMaxima, rowMinColumns, rowMaxColumns, rowValid, token));
        } else {
            scanRows(values, pixelWidth, 0, pixelHeight, rowMinima, rowMaxima,
                    rowMinColumns, rowMaxColumns, rowValid, token);
        }
        final Optional<FieldExtrema> extrema = extremaFromRows(viewport, pixelWidth, pixelHeight,
                pixelXs, pixelYs, rowMinima, rowMaxima,
                rowMinColumns, rowMaxColumns, rowValid);
        return new FieldGrid(pixelWidth, pixelHeight, 1, pixelWidth, pixelHeight,
                pixelXs, pixelYs, values, extrema, null);
    }

    private static void scanRows(final double[] values, final int columns,
            final int fromRow, final int toRow, final double[] rowMinima,
            final double[] rowMaxima, final int[] rowMinColumns,
            final int[] rowMaxColumns, final boolean[] rowValid,
            final CancellationToken token) {
        for (int row = fromRow; row < toRow; row++) {
            token.throwIfCancelled();
            final int offset = row * columns;
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            int minimumColumn = 0;
            int maximumColumn = 0;
            boolean valid = false;
            for (int column = 0; column < columns; column++) {
                if ((column & 255) == 0) {
                    token.throwIfCancelled();
                }
                final double value = values[offset + column];
                if (!Double.isFinite(value)) {
                    continue;
                }
                if (!valid || value < minimum) {
                    minimum = value;
                    minimumColumn = column;
                }
                if (!valid || value > maximum) {
                    maximum = value;
                    maximumColumn = column;
                }
                valid = true;
            }
            rowMinima[row] = minimum;
            rowMaxima[row] = maximum;
            rowMinColumns[row] = minimumColumn;
            rowMaxColumns[row] = maximumColumn;
            rowValid[row] = valid;
        }
    }

    private static void sampleRows(final DistanceField field, final double[] worldXs,
            final double[] worldYs, final int columns, final int fromRow, final int toRow,
            final double[] values, final double[] rowMinima, final double[] rowMaxima,
            final int[] rowMinColumns, final int[] rowMaxColumns, final boolean[] rowValid,
            final CancellationToken token) {
        for (int row = fromRow; row < toRow; row++) {
            token.throwIfCancelled();
            final int offset = row * columns;
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            int minimumColumn = 0;
            int maximumColumn = 0;
            boolean valid = false;
            for (int column = 0; column < columns; column++) {
                if ((column & 127) == 0) {
                    token.throwIfCancelled();
                }
                final double value = field.value(worldXs[column], worldYs[row]);
                values[offset + column] = value;
                if (!Double.isFinite(value)) {
                    continue;
                }
                if (!valid || value < minimum) {
                    minimum = value;
                    minimumColumn = column;
                }
                if (!valid || value > maximum) {
                    maximum = value;
                    maximumColumn = column;
                }
                valid = true;
            }
            rowMinima[row] = minimum;
            rowMaxima[row] = maximum;
            rowMinColumns[row] = minimumColumn;
            rowMaxColumns[row] = maximumColumn;
            rowValid[row] = valid;
        }
    }

    private static Optional<FieldExtrema> extremaFromRows(final Viewport viewport,
            final int pixelWidth, final int pixelHeight, final int[] pixelXs,
            final int[] pixelYs, final double[] rowMinima, final double[] rowMaxima,
            final int[] rowMinColumns, final int[] rowMaxColumns, final boolean[] rowValid) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        int minimumColumn = 0;
        int minimumRow = 0;
        int maximumColumn = 0;
        int maximumRow = 0;
        for (int row = 0; row < rowValid.length; row++) {
            if (!rowValid[row]) {
                continue;
            }
            if (rowMinima[row] < minimum) {
                minimum = rowMinima[row];
                minimumColumn = rowMinColumns[row];
                minimumRow = row;
            }
            if (rowMaxima[row] > maximum) {
                maximum = rowMaxima[row];
                maximumColumn = rowMaxColumns[row];
                maximumRow = row;
            }
        }
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            return Optional.empty();
        }
        return Optional.of(new FieldExtrema(minimum, maximum,
                new Point2(viewport.worldX(pixelXs[minimumColumn], pixelWidth),
                        viewport.worldY(pixelYs[minimumRow], pixelHeight)),
                new Point2(viewport.worldX(pixelXs[maximumColumn], pixelWidth),
                        viewport.worldY(pixelYs[maximumRow], pixelHeight))));
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

    public Optional<FieldExtrema> getExtrema() {
        return extrema;
    }

    /** Returns a cached finite value for an exact pixel, or NaN when unavailable. */
    public double finiteValueAtPixel(final double pixelX, final double pixelY) {
        if (pixelX != Math.rint(pixelX) || pixelY != Math.rint(pixelY)
                || pixelX < 0 || pixelX >= pixelWidth
                || pixelY < 0 || pixelY >= pixelHeight) {
            return Double.NaN;
        }
        final int integerX = (int) pixelX;
        final int integerY = (int) pixelY;
        final boolean sampledX = integerX == pixelWidth - 1 || integerX % step == 0;
        final boolean sampledY = integerY == pixelHeight - 1 || integerY % step == 0;
        if (sampledX && sampledY) {
            final int column = integerX == pixelWidth - 1 ? columns - 1 : integerX / step;
            final int row = integerY == pixelHeight - 1 ? rows - 1 : integerY / step;
            final double value = values[row * columns + column];
            return Double.isFinite(value) ? value : Double.NaN;
        }
        if (sampleSource != null) {
            final FieldGrid source = sampleSource.get();
            if (source != null) {
                return source.finiteValueAtPixel(pixelX, pixelY);
            }
        }
        return Double.NaN;
    }

    public long estimatedBytes() {
        return 128L + (long) values.length * Double.BYTES
                + (long) (pixelXs.length + pixelYs.length) * Integer.BYTES;
    }

    private static final class ScanRowsTask extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final double[] values;
        private final int columns;
        private final int fromRow;
        private final int toRow;
        private final double[] rowMinima;
        private final double[] rowMaxima;
        private final int[] rowMinColumns;
        private final int[] rowMaxColumns;
        private final boolean[] rowValid;
        private final transient CancellationToken token;

        ScanRowsTask(final double[] values, final int columns, final int fromRow,
                final int toRow, final double[] rowMinima, final double[] rowMaxima,
                final int[] rowMinColumns, final int[] rowMaxColumns,
                final boolean[] rowValid, final CancellationToken token) {
            this.values = values;
            this.columns = columns;
            this.fromRow = fromRow;
            this.toRow = toRow;
            this.rowMinima = rowMinima;
            this.rowMaxima = rowMaxima;
            this.rowMinColumns = rowMinColumns;
            this.rowMaxColumns = rowMaxColumns;
            this.rowValid = rowValid;
            this.token = token;
        }

        @Override
        protected void compute() {
            token.throwIfCancelled();
            if (toRow - fromRow <= ROWS_PER_TASK) {
                scanRows(values, columns, fromRow, toRow, rowMinima, rowMaxima,
                        rowMinColumns, rowMaxColumns, rowValid, token);
                return;
            }
            final int middle = (fromRow + toRow) >>> 1;
            invokeAll(
                    new ScanRowsTask(values, columns, fromRow, middle, rowMinima,
                            rowMaxima, rowMinColumns, rowMaxColumns, rowValid, token),
                    new ScanRowsTask(values, columns, middle, toRow, rowMinima,
                            rowMaxima, rowMinColumns, rowMaxColumns, rowValid, token));
        }
    }

    private static final class SampleRowsTask extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final transient DistanceField field;
        private final double[] worldXs;
        private final double[] worldYs;
        private final int columns;
        private final int fromRow;
        private final int toRow;
        private final double[] values;
        private final double[] rowMinima;
        private final double[] rowMaxima;
        private final int[] rowMinColumns;
        private final int[] rowMaxColumns;
        private final boolean[] rowValid;
        private final transient CancellationToken token;

        SampleRowsTask(final DistanceField field, final double[] worldXs,
                final double[] worldYs, final int columns, final int fromRow, final int toRow,
                final double[] values, final double[] rowMinima, final double[] rowMaxima,
                final int[] rowMinColumns, final int[] rowMaxColumns,
                final boolean[] rowValid, final CancellationToken token) {
            this.field = field;
            this.worldXs = worldXs;
            this.worldYs = worldYs;
            this.columns = columns;
            this.fromRow = fromRow;
            this.toRow = toRow;
            this.values = values;
            this.rowMinima = rowMinima;
            this.rowMaxima = rowMaxima;
            this.rowMinColumns = rowMinColumns;
            this.rowMaxColumns = rowMaxColumns;
            this.rowValid = rowValid;
            this.token = token;
        }

        @Override
        protected void compute() {
            token.throwIfCancelled();
            if (toRow - fromRow <= ROWS_PER_TASK) {
                sampleRows(field, worldXs, worldYs, columns, fromRow, toRow,
                        values, rowMinima, rowMaxima, rowMinColumns,
                        rowMaxColumns, rowValid, token);
                return;
            }
            final int middle = (fromRow + toRow) >>> 1;
            invokeAll(
                    new SampleRowsTask(field, worldXs, worldYs, columns, fromRow, middle,
                            values, rowMinima, rowMaxima, rowMinColumns,
                            rowMaxColumns, rowValid, token),
                    new SampleRowsTask(field, worldXs, worldYs, columns, middle, toRow,
                            values, rowMinima, rowMaxima, rowMinColumns,
                            rowMaxColumns, rowValid, token));
        }
    }
}
