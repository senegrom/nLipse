package nlipse.render;

import java.util.Objects;
import nlipse.math.ScalarRanges;

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
        final double step = SamplingLattice.stepBetween(xMin, xMax, pixelWidth - 1);
        if (pixelX == 0) {
            return xMin;
        }
        if (pixelX == pixelWidth - 1.0) {
            return xMax;
        }
        return Double.isInfinite(step)
                ? ScalarRanges.affine(xMin, xMax,
                        pixelX / (pixelWidth - 1.0))
                : Math.fma(pixelX, step, xMin);
    }

    public double worldY(final double pixelY, final int pixelHeight) {
        requireResolution(pixelHeight);
        if (lattice != null && lattice.pixelHeight() == pixelHeight) {
            return lattice.worldY(pixelY);
        }
        final double step = SamplingLattice.stepBetween(yMax, yMin, pixelHeight - 1);
        if (pixelY == 0) {
            return yMax;
        }
        if (pixelY == pixelHeight - 1.0) {
            return yMin;
        }
        return Double.isInfinite(step)
                ? ScalarRanges.affine(yMax, yMin,
                        pixelY / (pixelHeight - 1.0))
                : Math.fma(pixelY, step, yMax);
    }

    public double pixelX(final double worldX, final int pixelWidth) {
        requireResolution(pixelWidth);
        if (worldX == xMin) {
            return 0;
        }
        if (worldX == xMax) {
            return pixelWidth - 1.0;
        }
        final double step = lattice != null && lattice.pixelWidth() == pixelWidth
                ? lattice.stepX() : SamplingLattice.stepBetween(xMin, xMax, pixelWidth - 1);
        final double origin = lattice != null && lattice.pixelWidth() == pixelWidth
                ? lattice.originX() : xMin;
        final long offset = lattice != null && lattice.pixelWidth() == pixelWidth
                ? lattice.offsetX() : 0;
        double mapped = Double.NaN;
        if (step != 0 && Double.isFinite(step)) {
            mapped = (worldX - origin) / step - offset;
        }
        if (!Double.isFinite(mapped)) {
            mapped = ScalarRanges.unboundedFraction(worldX, xMin, xMax)
                    * (pixelWidth - 1.0);
        }
        final double lastPixel = pixelWidth - 1.0;
        if (worldX > xMin && worldX < xMax) {
            mapped = Math.clamp(mapped, 0, lastPixel);
        }
        return preserveOutside(worldX, xMin, xMax, mapped, lastPixel, false);
    }

    public double pixelY(final double worldY, final int pixelHeight) {
        requireResolution(pixelHeight);
        if (worldY == yMax) {
            return 0;
        }
        if (worldY == yMin) {
            return pixelHeight - 1.0;
        }
        final double step = lattice != null && lattice.pixelHeight() == pixelHeight
                ? lattice.stepY() : SamplingLattice.stepBetween(yMax, yMin, pixelHeight - 1);
        final double origin = lattice != null && lattice.pixelHeight() == pixelHeight
                ? lattice.originY() : yMax;
        final long offset = lattice != null && lattice.pixelHeight() == pixelHeight
                ? lattice.offsetY() : 0;
        double mapped = Double.NaN;
        if (step != 0 && Double.isFinite(step)) {
            mapped = (worldY - origin) / step - offset;
        }
        if (!Double.isFinite(mapped)) {
            mapped = (1 - ScalarRanges.unboundedFraction(worldY, yMin, yMax))
                    * (pixelHeight - 1.0);
        }
        final double lastPixel = pixelHeight - 1.0;
        if (worldY > yMin && worldY < yMax) {
            mapped = Math.clamp(mapped, 0, lastPixel);
        }
        return preserveOutside(worldY, yMin, yMax, mapped, lastPixel, true);
    }

    private static double preserveOutside(final double value,
            final double minimum, final double maximum, final double mapped,
            final double lastPixel, final boolean reversed) {
        if (!reversed) {
            if (value < minimum && !(mapped < 0)) {
                return Math.nextDown(0.0);
            }
            if (value > maximum && !(mapped > lastPixel)) {
                return Math.nextUp(lastPixel);
            }
        } else {
            if (value > maximum && !(mapped < 0)) {
                return Math.nextDown(0.0);
            }
            if (value < minimum && !(mapped > lastPixel)) {
                return Math.nextUp(lastPixel);
            }
        }
        return mapped;
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

        final double dxWorld = dxPixels
                * SamplingLattice.stepBetween(xMin, xMax, pixelWidth - 1);
        final double dyWorld = dyPixels
                * SamplingLattice.stepBetween(yMin, yMax, pixelHeight - 1);
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
                scaledFrom(centreX, xMin, scale),
                scaledFrom(centreX, xMax, scale),
                scaledFrom(centreY, yMin, scale),
                scaledFrom(centreY, yMax, scale));
    }

    SamplingLattice samplingLattice(final int pixelWidth, final int pixelHeight) {
        requireResolution(pixelWidth);
        requireResolution(pixelHeight);
        if (lattice != null && lattice.matches(pixelWidth, pixelHeight)) {
            return lattice;
        }
        return SamplingLattice.fromViewport(xMin, xMax, yMin, yMax,
                pixelWidth, pixelHeight);
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

    private static double scaledFrom(final double anchor, final double endpoint,
            final double scale) {
        if (scale < 1) {
            return ScalarRanges.interpolate(anchor, endpoint, scale);
        }
        final double direct = Math.fma(endpoint - anchor, scale, anchor);
        if (Double.isFinite(direct)) {
            return direct;
        }
        return Math.fma(endpoint, scale, anchor * (1 - scale));
    }

    private static boolean validBounds(final double newXMin, final double newXMax,
            final double newYMin, final double newYMax) {
        return Double.isFinite(newXMin) && Double.isFinite(newXMax)
                && Double.isFinite(newYMin) && Double.isFinite(newYMax)
                && newXMin < newXMax && newYMin < newYMax;
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
