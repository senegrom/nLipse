package nlipse.render;

import nlipse.math.DistanceField;

/** Marching-squares contour extraction in pixel coordinates. */
public final class MarchingSquares {
    @FunctionalInterface
    public interface SegmentConsumer {
        void accept(double x1, double y1, double x2, double y2);
    }

    private MarchingSquares() {
    }

    public static int trace(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double level, final CancellationToken token,
            final SegmentConsumer consumer) {
        if (grid == null || field == null || viewport == null || token == null || consumer == null) {
            throw new IllegalArgumentException("Grid, field, viewport, token and consumer are required");
        }
        if (!Double.isFinite(level)) {
            return 0;
        }
        int segmentCount = 0;
        for (int row = 0; row < grid.getRows() - 1; row++) {
            if ((row & 7) == 0) {
                token.throwIfCancelled();
            }
            final double y0 = grid.getPixelY(row);
            final double y1 = grid.getPixelY(row + 1);
            for (int column = 0; column < grid.getColumns() - 1; column++) {
                final double x0 = grid.getPixelX(column);
                final double x1 = grid.getPixelX(column + 1);
                final double topLeft = grid.getValue(column, row);
                final double topRight = grid.getValue(column + 1, row);
                final double bottomRight = grid.getValue(column + 1, row + 1);
                final double bottomLeft = grid.getValue(column, row + 1);
                segmentCount += processCell(grid, field, viewport, level, token, consumer,
                        x0, y0, x1, y1, topLeft, topRight, bottomRight, bottomLeft, 0);
            }
        }
        return segmentCount;
    }

    private static int processCell(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double level, final CancellationToken token,
            final SegmentConsumer consumer, final double x0, final double y0,
            final double x1, final double y1, final double a, final double b,
            final double c, final double d, final int depth) {
        if (!Double.isFinite(a) || !Double.isFinite(b)
                || !Double.isFinite(c) || !Double.isFinite(d)) {
            return 0;
        }

        final int mask = mask(a, b, c, d, level);
        if ((mask == 0 || mask == 15) && grid.getStep() > 1 && depth == 0) {
            final double centreX = (x0 + x1) * 0.5;
            final double centreY = (y0 + y1) * 0.5;
            final double centre = sample(field, viewport, grid, centreX, centreY);
            if (Double.isFinite(centre) && (centre >= level) != (mask == 15)) {
                token.throwIfCancelled();
                final double top = sample(field, viewport, grid, centreX, y0);
                final double right = sample(field, viewport, grid, x1, centreY);
                final double bottom = sample(field, viewport, grid, centreX, y1);
                final double left = sample(field, viewport, grid, x0, centreY);
                if (Double.isFinite(top) && Double.isFinite(right)
                        && Double.isFinite(bottom) && Double.isFinite(left)) {
                    return processCell(grid, field, viewport, level, token, consumer,
                            x0, y0, centreX, centreY, a, top, centre, left, depth + 1)
                            + processCell(grid, field, viewport, level, token, consumer,
                                    centreX, y0, x1, centreY, top, b, right, centre, depth + 1)
                            + processCell(grid, field, viewport, level, token, consumer,
                                    centreX, centreY, x1, y1, centre, right, c, bottom, depth + 1)
                            + processCell(grid, field, viewport, level, token, consumer,
                                    x0, centreY, centreX, y1, left, centre, bottom, d, depth + 1);
                }
            }
            return 0;
        }
        if (mask == 0 || mask == 15) {
            return 0;
        }

        final EdgePoint[] edges = new EdgePoint[4];
        edges[0] = interpolate(x0, y0, a, x1, y0, b, level);
        edges[1] = interpolate(x1, y0, b, x1, y1, c, level);
        edges[2] = interpolate(x1, y1, c, x0, y1, d, level);
        edges[3] = interpolate(x0, y1, d, x0, y0, a, level);

        switch (mask) {
            case 1:
                emit(edges, 3, 0, consumer);
                return 1;
            case 2:
                emit(edges, 0, 1, consumer);
                return 1;
            case 3:
                emit(edges, 3, 1, consumer);
                return 1;
            case 4:
                emit(edges, 1, 2, consumer);
                return 1;
            case 5:
                return emitAmbiguous(grid, field, viewport, level, consumer,
                        x0, y0, x1, y1, edges, true);
            case 6:
                emit(edges, 0, 2, consumer);
                return 1;
            case 7:
                emit(edges, 3, 2, consumer);
                return 1;
            case 8:
                emit(edges, 2, 3, consumer);
                return 1;
            case 9:
                emit(edges, 0, 2, consumer);
                return 1;
            case 10:
                return emitAmbiguous(grid, field, viewport, level, consumer,
                        x0, y0, x1, y1, edges, false);
            case 11:
                emit(edges, 1, 2, consumer);
                return 1;
            case 12:
                emit(edges, 1, 3, consumer);
                return 1;
            case 13:
                emit(edges, 0, 1, consumer);
                return 1;
            case 14:
                emit(edges, 3, 0, consumer);
                return 1;
            default:
                return 0;
        }
    }

    private static int emitAmbiguous(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double level, final SegmentConsumer consumer,
            final double x0, final double y0, final double x1, final double y1,
            final EdgePoint[] edges, final boolean highOnTopLeftAndBottomRight) {
        final double centre = sample(field, viewport, grid, (x0 + x1) * 0.5, (y0 + y1) * 0.5);
        final boolean centreHigh = Double.isFinite(centre) && centre >= level;
        if (highOnTopLeftAndBottomRight) {
            if (centreHigh) {
                emit(edges, 0, 1, consumer);
                emit(edges, 2, 3, consumer);
            } else {
                emit(edges, 3, 0, consumer);
                emit(edges, 1, 2, consumer);
            }
        } else if (centreHigh) {
            emit(edges, 3, 0, consumer);
            emit(edges, 1, 2, consumer);
        } else {
            emit(edges, 0, 1, consumer);
            emit(edges, 2, 3, consumer);
        }
        return 2;
    }

    private static int mask(final double a, final double b, final double c,
            final double d, final double level) {
        int mask = 0;
        if (a >= level) {
            mask |= 1;
        }
        if (b >= level) {
            mask |= 2;
        }
        if (c >= level) {
            mask |= 4;
        }
        if (d >= level) {
            mask |= 8;
        }
        return mask;
    }

    private static EdgePoint interpolate(final double x0, final double y0, final double value0,
            final double x1, final double y1, final double value1, final double level) {
        final double difference = value1 - value0;
        final double t;
        if (difference == 0 || !Double.isFinite(difference)) {
            t = 0.5;
        } else {
            t = Math.clamp((level - value0) / difference, 0, 1);
        }
        return new EdgePoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t);
    }

    private static void emit(final EdgePoint[] edges, final int first, final int second,
            final SegmentConsumer consumer) {
        final EdgePoint a = edges[first];
        final EdgePoint b = edges[second];
        consumer.accept(a.x(), a.y(), b.x(), b.y());
    }

    private static double sample(final DistanceField field, final Viewport viewport,
            final FieldGrid grid, final double pixelX, final double pixelY) {
        return field.value(viewport.worldX(pixelX, grid.getPixelWidth()),
                viewport.worldY(pixelY, grid.getPixelHeight()));
    }

    private record EdgePoint(double x, double y) {
    }
}
