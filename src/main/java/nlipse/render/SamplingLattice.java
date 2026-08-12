package nlipse.render;

/** Exact sampling-lattice lineage carried by viewports created through integer-pixel pans. */
record SamplingLattice(
        double originX,
        double rootXMax,
        double originY,
        double rootYMin,
        double stepX,
        double stepY,
        long offsetX,
        long offsetY,
        int pixelWidth,
        int pixelHeight) {

    SamplingLattice {
        if (pixelWidth < 2 || pixelHeight < 2) {
            throw new IllegalArgumentException("Sampling-lattice resolution must be at least 2x2");
        }
        stepX = stepX == 0 ? 0 : stepX;
        stepY = stepY == 0 ? -0.0 : stepY;
        if (!Double.isFinite(originX) || !Double.isFinite(rootXMax)
                || !Double.isFinite(originY) || !Double.isFinite(rootYMin)
                || Double.isNaN(stepX) || Double.isNaN(stepY)
                || originX >= rootXMax || rootYMin >= originY
                || stepX < 0 || stepY > 0
                || Double.isInfinite(stepX) && pixelWidth != 2
                || Double.isInfinite(stepY) && pixelHeight != 2) {
            throw new IllegalArgumentException("Sampling lattice must be finite and oriented");
        }
    }

    static SamplingLattice fromViewport(final double xMin, final double xMax,
            final double yMin, final double yMax,
            final int pixelWidth, final int pixelHeight) {
        return new SamplingLattice(xMin, xMax, yMax, yMin,
                stepBetween(xMin, xMax, pixelWidth - 1),
                stepBetween(yMax, yMin, pixelHeight - 1),
                0, 0, pixelWidth, pixelHeight);
    }

    /**
     * Returns the representable per-interval step without first overflowing the span.
     * A two-pixel full-range axis has no finite step, so signed infinity is retained as
     * an endpoint-only sentinel; wider axes divide the endpoints before subtraction.
     */
    static double stepBetween(final double start, final double end, final int intervals) {
        if (intervals < 1 || !Double.isFinite(start) || !Double.isFinite(end) || start == end) {
            throw new IllegalArgumentException("Finite distinct endpoints and intervals are required");
        }
        final double span = end - start;
        if (Double.isFinite(span)) {
            return span / intervals;
        }
        if (intervals == 1) {
            return Math.copySign(Double.POSITIVE_INFINITY, span);
        }
        return end / intervals - start / intervals;
    }

    boolean matches(final int width, final int height) {
        return pixelWidth == width && pixelHeight == height;
    }

    SamplingLattice shifted(final long deltaX, final long deltaY) {
        return new SamplingLattice(originX, rootXMax, originY, rootYMin, stepX, stepY,
                Math.addExact(offsetX, deltaX), Math.addExact(offsetY, deltaY),
                pixelWidth, pixelHeight);
    }

    double worldX(final double pixelX) {
        final double globalX = offsetX + pixelX;
        if (globalX == 0) {
            return originX;
        }
        if (globalX == pixelWidth - 1.0) {
            return rootXMax;
        }
        return Math.fma(globalX, stepX, originX);
    }

    double worldY(final double pixelY) {
        final double globalY = offsetY + pixelY;
        if (globalY == 0) {
            return originY;
        }
        if (globalY == pixelHeight - 1.0) {
            return rootYMin;
        }
        return Math.fma(globalY, stepY, originY);
    }

    double worldXAtIndex(final long globalX) {
        if (globalX == 0) {
            return originX;
        }
        if (globalX == pixelWidth - 1L) {
            return rootXMax;
        }
        return Math.fma(globalX, stepX, originX);
    }

    double worldYAtIndex(final long globalY) {
        if (globalY == 0) {
            return originY;
        }
        if (globalY == pixelHeight - 1L) {
            return rootYMin;
        }
        return Math.fma(globalY, stepY, originY);
    }

    long originXBits() {
        return Double.doubleToLongBits(originX);
    }

    long rootXMaxBits() {
        return Double.doubleToLongBits(rootXMax);
    }

    long originYBits() {
        return Double.doubleToLongBits(originY);
    }

    long rootYMinBits() {
        return Double.doubleToLongBits(rootYMin);
    }

    long stepXBits() {
        return Double.doubleToLongBits(stepX);
    }

    long stepYBits() {
        return Double.doubleToLongBits(stepY);
    }
}
