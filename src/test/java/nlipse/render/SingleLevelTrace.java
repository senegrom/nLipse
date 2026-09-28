package nlipse.render;

import nlipse.math.DistanceField;

/** Test helper: one contour level through {@link MarchingSquares#traceLevels}. */
final class SingleLevelTrace {
    @FunctionalInterface
    interface SegmentConsumer {
        void accept(double x1, double y1, double x2, double y2);
    }

    private SingleLevelTrace() {
    }

    /** Traces one level and returns how many segments it emitted. */
    static int trace(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double level, final CancellationToken token,
            final SegmentConsumer consumer) {
        final int[] count = new int[1];
        MarchingSquares.traceLevels(grid, field, viewport, new double[]{level}, token,
                (ignored, x1, y1, x2, y2) -> {
                    count[0]++;
                    consumer.accept(x1, y1, x2, y2);
                });
        return count[0];
    }
}
