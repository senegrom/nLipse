package nlipse.render;

import java.math.BigDecimal;

import nlipse.math.DistanceField;
import nlipse.math.ScalarRanges;

/** Allocation-free, multi-level marching-squares extraction in pixel coordinates. */
final class MarchingSquares {
    @FunctionalInterface
    interface LevelSegmentConsumer {
        void accept(int levelIndex, double x1, double y1, double x2, double y2);
    }

    private MarchingSquares() {
    }

    static void traceLevels(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double[] levels, final CancellationToken token,
            final LevelSegmentConsumer consumer) {
        if (grid == null || field == null || viewport == null || levels == null
                || token == null || consumer == null) {
            throw new IllegalArgumentException(
                    "Grid, field, viewport, levels, token and consumer are required");
        }
        validateLevels(levels);
        if (levels.length == 0) {
            return;
        }

        for (int row = 0; row < grid.getRows() - 1; row++) {
            if ((row & 7) == 0) {
                token.throwIfCancelled();
            }
            final double y0 = grid.getPixelY(row);
            final double y1 = grid.getPixelY(row + 1);
            for (int column = 0; column < grid.getColumns() - 1; column++) {
                if ((column & 127) == 0) {
                    token.throwIfCancelled();
                }
                final double x0 = grid.getPixelX(column);
                final double x1 = grid.getPixelX(column + 1);
                final double topLeft = grid.getValue(column, row);
                final double topRight = grid.getValue(column + 1, row);
                final double bottomRight = grid.getValue(column + 1, row + 1);
                final double bottomLeft = grid.getValue(column, row + 1);
                if (!Double.isFinite(topLeft) || !Double.isFinite(topRight)
                        || !Double.isFinite(bottomRight) || !Double.isFinite(bottomLeft)) {
                    continue;
                }

                double minimum = Math.min(Math.min(topLeft, topRight),
                        Math.min(bottomRight, bottomLeft));
                double maximum = Math.max(Math.max(topLeft, topRight),
                        Math.max(bottomRight, bottomLeft));
                double centre = Double.NaN;
                if (grid.getStep() > 1) {
                    centre = sample(field, viewport, grid, (x0 + x1) * 0.5, (y0 + y1) * 0.5);
                    if (Double.isFinite(centre)) {
                        minimum = Math.min(minimum, centre);
                        maximum = Math.max(maximum, centre);
                    }
                }

                final int firstLevel = lowerBound(levels, minimum);
                final int afterLastLevel = upperBound(levels, maximum);
                for (int levelIndex = firstLevel; levelIndex < afterLastLevel; levelIndex++) {
                    processCell(grid, field, viewport,
                            levels[levelIndex], levelIndex, token, consumer,
                            x0, y0, x1, y1,
                            topLeft, topRight, bottomRight, bottomLeft, centre, 0);
                }
            }
        }
    }

    private static void processCell(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double level, final int levelIndex,
            final CancellationToken token, final LevelSegmentConsumer consumer,
            final double x0, final double y0, final double x1, final double y1,
            final double a, final double b, final double c, final double d,
            final double knownCentre, final int depth) {
        // Corners are finite: traceLevels skips cells with non-finite ones, and
        // recursion only follows centre and edge samples it has checked.
        final int mask = mask(a, b, c, d, level);
        if ((mask == 0 || mask == 15) && grid.getStep() > 1
                && depth == 0 && x1 - x0 > 1 && y1 - y0 > 1) {
            final double centreX = (x0 + x1) * 0.5;
            final double centreY = (y0 + y1) * 0.5;
            final double centre = Double.isFinite(knownCentre) ? knownCentre
                    : sample(field, viewport, grid, centreX, centreY);
            if (Double.isFinite(centre) && (centre >= level) != (mask == 15)) {
                token.throwIfCancelled();
                final double top = sample(field, viewport, grid, centreX, y0);
                final double right = sample(field, viewport, grid, x1, centreY);
                final double bottom = sample(field, viewport, grid, centreX, y1);
                final double left = sample(field, viewport, grid, x0, centreY);
                if (Double.isFinite(top) && Double.isFinite(right)
                        && Double.isFinite(bottom) && Double.isFinite(left)) {
                    processCell(grid, field, viewport, level, levelIndex, token, consumer,
                            x0, y0, centreX, centreY, a, top, centre, left,
                            Double.NaN, depth + 1);
                    processCell(grid, field, viewport, level, levelIndex, token, consumer,
                            centreX, y0, x1, centreY,
                            top, b, right, centre, Double.NaN, depth + 1);
                    processCell(grid, field, viewport, level, levelIndex, token, consumer,
                            centreX, centreY, x1, y1,
                            centre, right, c, bottom, Double.NaN, depth + 1);
                    processCell(grid, field, viewport, level, levelIndex, token, consumer,
                            x0, centreY, centreX, y1,
                            left, centre, bottom, d, Double.NaN, depth + 1);
                }
            }
            return;
        }
        if (mask == 0 || mask == 15) {
            return;
        }

        final double topX = interpolate(x0, a, x1, b, level);
        final double rightY = interpolate(y0, b, y1, c, level);
        final double bottomX = interpolate(x1, c, x0, d, level);
        final double leftY = interpolate(y1, d, y0, a, level);

        switch (mask) {
            case 1, 14 -> consumer.accept(levelIndex, x0, leftY, topX, y0);
            case 2, 13 -> consumer.accept(levelIndex, topX, y0, x1, rightY);
            case 3 -> consumer.accept(levelIndex, x0, leftY, x1, rightY);
            case 4, 11 -> consumer.accept(levelIndex, x1, rightY, bottomX, y1);
            case 5 -> emitAmbiguous(grid, field, viewport, level, levelIndex, consumer,
                    x0, y0, x1, y1, a, b, c, d,
                    topX, rightY, bottomX, leftY, true, knownCentre);
            case 6, 9 -> consumer.accept(levelIndex, topX, y0, bottomX, y1);
            case 7 -> consumer.accept(levelIndex, x0, leftY, bottomX, y1);
            case 8 -> consumer.accept(levelIndex, bottomX, y1, x0, leftY);
            case 10 -> emitAmbiguous(grid, field, viewport, level, levelIndex, consumer,
                    x0, y0, x1, y1, a, b, c, d,
                    topX, rightY, bottomX, leftY, false, knownCentre);
            case 12 -> consumer.accept(levelIndex, x1, rightY, x0, leftY);
        }
    }

    private static void emitAmbiguous(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double level, final int levelIndex,
            final LevelSegmentConsumer consumer, final double x0, final double y0,
            final double x1, final double y1, final double a, final double b,
            final double c, final double d, final double topX, final double rightY,
            final double bottomX, final double leftY,
            final boolean highOnTopLeftAndBottomRight, final double knownCentre) {
        final int asymptoticDecision = asymptoticHighConnection(
                a, b, c, d, level, highOnTopLeftAndBottomRight);
        final boolean highConnected;
        if (asymptoticDecision != 0) {
            highConnected = asymptoticDecision > 0;
        } else {
            final double centre = Double.isFinite(knownCentre) ? knownCentre
                    : sample(field, viewport, grid,
                            (x0 + x1) * 0.5, (y0 + y1) * 0.5);
            highConnected = Double.isFinite(centre) && centre >= level;
        }
        // Equal flags: the top-left and bottom-right corners connect, as the high
        // or the low pair, so the segments cut off the other two; else these two
        if (highOnTopLeftAndBottomRight == highConnected) {
            consumer.accept(levelIndex, topX, y0, x1, rightY);
            consumer.accept(levelIndex, bottomX, y1, x0, leftY);
        } else {
            consumer.accept(levelIndex, x0, leftY, topX, y0);
            consumer.accept(levelIndex, x1, rightY, bottomX, y1);
        }
    }

    /** Returns 1 when high corners connect, -1 when low corners connect, and 0 for a tie. */
    private static int asymptoticHighConnection(final double a, final double b,
            final double c, final double d, final double level,
            final boolean highOnTopLeftAndBottomRight) {
        final double largestMagnitude = Math.max(Math.abs(level),
                Math.max(Math.max(Math.abs(a), Math.abs(b)),
                        Math.max(Math.abs(c), Math.abs(d))));
        if (largestMagnitude > 0 && Double.isFinite(largestMagnitude)) {
            final int exponent = Math.getExponent(largestMagnitude);
            final double scaledLevel = Math.scalb(level, -exponent);
            final double shiftedA = Math.scalb(a, -exponent) - scaledLevel;
            final double shiftedB = Math.scalb(b, -exponent) - scaledLevel;
            final double shiftedC = Math.scalb(c, -exponent) - scaledLevel;
            final double shiftedD = Math.scalb(d, -exponent) - scaledLevel;
            final double secondProduct = shiftedB * shiftedD;
            final double determinant = Math.fma(shiftedA, shiftedC, -secondProduct)
                    + Math.fma(-shiftedB, shiftedD, secondProduct);
            // Scaled inputs have magnitude < 2 and shifted inputs < 4.
            // This conservative absolute bound covers scaling, subtraction,
            // and both products, including underflow in the scaled inputs.
            // A single fma alone can give a spurious sign for equal products.
            if (Math.abs(determinant) > 128 * Math.ulp(1.0)) {
                return (determinant > 0) == highOnTopLeftAndBottomRight ? 1 : -1;
            }
        }
        final BigDecimal exactLevel = new BigDecimal(level);
        final BigDecimal exactA = new BigDecimal(a).subtract(exactLevel);
        final BigDecimal exactB = new BigDecimal(b).subtract(exactLevel);
        final BigDecimal exactC = new BigDecimal(c).subtract(exactLevel);
        final BigDecimal exactD = new BigDecimal(d).subtract(exactLevel);
        final int sign = exactA.multiply(exactC).subtract(exactB.multiply(exactD)).signum();
        return highOnTopLeftAndBottomRight ? sign : -sign;
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

    private static double interpolate(final double coordinate0, final double value0,
            final double coordinate1, final double value1, final double level) {
        final double fraction;
        if (value0 < value1) {
            fraction = ScalarRanges.fraction(level, value0, value1);
        } else if (value0 > value1) {
            fraction = 1 - ScalarRanges.fraction(level, value1, value0);
        } else {
            fraction = 0.5;
        }
        return coordinate0 + (coordinate1 - coordinate0) * fraction;
    }

    private static double sample(final DistanceField field, final Viewport viewport,
            final FieldGrid grid, final double pixelX, final double pixelY) {
        final double cached = grid.finiteValueAtPixel(pixelX, pixelY);
        if (Double.isFinite(cached)) {
            return cached;
        }
        return field.value(viewport.worldX(pixelX, grid.getPixelWidth()),
                viewport.worldY(pixelY, grid.getPixelHeight()));
    }

    private static int lowerBound(final double[] values, final double target) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            final int middle = (low + high) >>> 1;
            if (values[middle] < target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperBound(final double[] values, final double target) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            final int middle = (low + high) >>> 1;
            if (values[middle] <= target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static void validateLevels(final double[] levels) {
        double previous = Double.NEGATIVE_INFINITY;
        for (final double level : levels) {
            if (!Double.isFinite(level)) {
                throw new IllegalArgumentException("Contour levels must be finite");
            }
            if (level < previous) {
                throw new IllegalArgumentException("Contour levels must be sorted");
            }
            previous = level;
        }
    }
}
