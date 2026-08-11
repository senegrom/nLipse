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
        if (!Double.isFinite(originX) || !Double.isFinite(rootXMax)
                || !Double.isFinite(originY) || !Double.isFinite(rootYMin)
                || !Double.isFinite(stepX) || !Double.isFinite(stepY)
                || originX >= rootXMax || rootYMin >= originY
                || stepX <= 0 || stepY >= 0) {
            throw new IllegalArgumentException("Sampling lattice must be finite and oriented");
        }
        if (pixelWidth < 2 || pixelHeight < 2) {
            throw new IllegalArgumentException("Sampling-lattice resolution must be at least 2x2");
        }
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
