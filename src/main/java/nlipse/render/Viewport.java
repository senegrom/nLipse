package nlipse.render;

import java.util.Objects;

/** Immutable mapping between world coordinates and a pixel rectangle. */
public final class Viewport {
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final SamplingLattice lattice;

    public Viewport(final double xMin, final double xMax,
            final double yMin, final double yMax) {
        this(xMin, xMax, yMin, yMax, null);
    }

    private Viewport(final double xMin, final double xMax,
            final double yMin, final double yMax, final SamplingLattice lattice) {
        final double canonicalXMin = canonicalZero(xMin);
        final double canonicalXMax = canonicalZero(xMax);
        final double canonicalYMin = canonicalZero(yMin);
        final double canonicalYMax = canonicalZero(yMax);
        if (!Double.isFinite(canonicalXMin) || !Double.isFinite(canonicalXMax)
                || !Double.isFinite(canonicalYMin) || !Double.isFinite(canonicalYMax)) {
            throw new IllegalArgumentException("Viewport bounds must be finite");
        }
        if (canonicalXMin >= canonicalXMax || canonicalYMin >= canonicalYMax) {
            throw new IllegalArgumentException("Viewport bounds must have min < max");
        }
        if (!Double.isFinite(canonicalXMax - canonicalXMin)
                || !Double.isFinite(canonicalYMax - canonicalYMin)) {
            throw new IllegalArgumentException("Viewport spans must be finite");
        }
        this.xMin = canonicalXMin;
        this.xMax = canonicalXMax;
        this.yMin = canonicalYMin;
        this.yMax = canonicalYMax;
        this.lattice = lattice;
    }

    public double xMin() {
        return xMin;
    }

    public double xMax() {
        return xMax;
    }

    public double yMin() {
        return yMin;
    }

    public double yMax() {
        return yMax;
    }

    public double width() {
        return xMax - xMin;
    }

    public double height() {
        return yMax - yMin;
    }

    public double worldX(final double pixelX, final int pixelWidth) {
        requireResolution(pixelWidth);
        if (lattice != null && lattice.pixelWidth() == pixelWidth) {
            return lattice.worldX(pixelX);
        }
        final double step = width() / (pixelWidth - 1.0);
        if (pixelX == 0) {
            return xMin;
        }
        if (pixelX == pixelWidth - 1.0) {
            return xMax;
        }
        return Math.fma(pixelX, step, xMin);
    }

    public double worldY(final double pixelY, final int pixelHeight) {
        requireResolution(pixelHeight);
        if (lattice != null && lattice.pixelHeight() == pixelHeight) {
            return lattice.worldY(pixelY);
        }
        final double step = -height() / (pixelHeight - 1.0);
        if (pixelY == 0) {
            return yMax;
        }
        if (pixelY == pixelHeight - 1.0) {
            return yMin;
        }
        return Math.fma(pixelY, step, yMax);
    }

    public double pixelX(final double worldX, final int pixelWidth) {
        requireResolution(pixelWidth);
        final double step = lattice != null && lattice.pixelWidth() == pixelWidth
                ? lattice.stepX() : width() / (pixelWidth - 1.0);
        final double origin = lattice != null && lattice.pixelWidth() == pixelWidth
                ? lattice.originX() : xMin;
        final long offset = lattice != null && lattice.pixelWidth() == pixelWidth
                ? lattice.offsetX() : 0;
        return (worldX - origin) / step - offset;
    }

    public double pixelY(final double worldY, final int pixelHeight) {
        requireResolution(pixelHeight);
        final double step = lattice != null && lattice.pixelHeight() == pixelHeight
                ? lattice.stepY() : -height() / (pixelHeight - 1.0);
        final double origin = lattice != null && lattice.pixelHeight() == pixelHeight
                ? lattice.originY() : yMax;
        final long offset = lattice != null && lattice.pixelHeight() == pixelHeight
                ? lattice.offsetY() : 0;
        return (worldY - origin) / step - offset;
    }

    public Viewport panPixels(final double dxPixels, final double dyPixels,
            final int pixelWidth, final int pixelHeight) {
        requireResolution(pixelWidth);
        requireResolution(pixelHeight);
        if (!Double.isFinite(dxPixels) || !Double.isFinite(dyPixels)) {
            return this;
        }
        if (dxPixels == 0 && dyPixels == 0) {
            return this;
        }
        final Long integralX = integralPixelOffset(dxPixels);
        final Long integralY = integralPixelOffset(dyPixels);
        if (integralX != null && integralY != null) {
            try {
                final SamplingLattice shifted = samplingLattice(pixelWidth, pixelHeight)
                        .shifted(Math.negateExact(integralX), Math.negateExact(integralY));
                return fromLatticeOrThis(shifted);
            } catch (final ArithmeticException overflow) {
                return this;
            }
        }

        final double dxWorld = dxPixels * width() / (pixelWidth - 1.0);
        final double dyWorld = dyPixels * height() / (pixelHeight - 1.0);
        return transformedOrThis(xMin - dxWorld, xMax - dxWorld,
                yMin + dyWorld, yMax + dyWorld);
    }

    public Viewport zoomAtPixel(final double pixelX, final double pixelY,
            final int pixelWidth, final int pixelHeight, final double scale) {
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Zoom scale must be finite and positive");
        }
        if (scale == 1) {
            return this;
        }
        final double centreX = worldX(pixelX, pixelWidth);
        final double centreY = worldY(pixelY, pixelHeight);
        return transformedOrThis(
                centreX + (xMin - centreX) * scale,
                centreX + (xMax - centreX) * scale,
                centreY + (yMin - centreY) * scale,
                centreY + (yMax - centreY) * scale);
    }

    SamplingLattice samplingLattice(final int pixelWidth, final int pixelHeight) {
        requireResolution(pixelWidth);
        requireResolution(pixelHeight);
        if (lattice != null && lattice.matches(pixelWidth, pixelHeight)) {
            return lattice;
        }
        return new SamplingLattice(xMin, xMax, yMax, yMin,
                width() / (pixelWidth - 1.0),
                -height() / (pixelHeight - 1.0),
                0, 0, pixelWidth, pixelHeight);
    }

    private Viewport fromLatticeOrThis(final SamplingLattice shifted) {
        final double newXMin = shifted.worldX(0);
        final double newXMax = shifted.worldX(shifted.pixelWidth() - 1.0);
        final double newYMax = shifted.worldY(0);
        final double newYMin = shifted.worldY(shifted.pixelHeight() - 1.0);
        if (!validBounds(newXMin, newXMax, newYMin, newYMax)) {
            return this;
        }
        return new Viewport(newXMin, newXMax, newYMin, newYMax, shifted);
    }

    private Viewport transformedOrThis(final double newXMin, final double newXMax,
            final double newYMin, final double newYMax) {
        if (!validBounds(newXMin, newXMax, newYMin, newYMax)) {
            return this;
        }
        return new Viewport(newXMin, newXMax, newYMin, newYMax);
    }

    private static boolean validBounds(final double newXMin, final double newXMax,
            final double newYMin, final double newYMax) {
        return Double.isFinite(newXMin) && Double.isFinite(newXMax)
                && Double.isFinite(newYMin) && Double.isFinite(newYMax)
                && newXMin < newXMax && newYMin < newYMax
                && Double.isFinite(newXMax - newXMin)
                && Double.isFinite(newYMax - newYMin);
    }

    private static Long integralPixelOffset(final double value) {
        if (value != Math.rint(value) || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            return null;
        }
        return (long) value;
    }

    private static double canonicalZero(final double value) {
        return value == 0 ? 0 : value;
    }

    private static void requireResolution(final int resolution) {
        if (resolution < 2) {
            throw new IllegalArgumentException("Pixel resolution must be at least 2");
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Viewport viewport)) {
            return false;
        }
        return Double.doubleToLongBits(xMin) == Double.doubleToLongBits(viewport.xMin)
                && Double.doubleToLongBits(xMax) == Double.doubleToLongBits(viewport.xMax)
                && Double.doubleToLongBits(yMin) == Double.doubleToLongBits(viewport.yMin)
                && Double.doubleToLongBits(yMax) == Double.doubleToLongBits(viewport.yMax);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xMin, xMax, yMin, yMax);
    }

    @Override
    public String toString() {
        return "Viewport[xMin=" + xMin + ", xMax=" + xMax
                + ", yMin=" + yMin + ", yMax=" + yMax + ']';
    }
}
