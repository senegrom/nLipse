package nlipse.render;

import java.util.Objects;

/** Immutable mapping between world coordinates and a pixel rectangle. */
public final class Viewport {
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;

    public Viewport(final double xMin, final double xMax, final double yMin, final double yMax) {
        if (!Double.isFinite(xMin) || !Double.isFinite(xMax)
                || !Double.isFinite(yMin) || !Double.isFinite(yMax)) {
            throw new IllegalArgumentException("Viewport bounds must be finite");
        }
        if (xMin >= xMax || yMin >= yMax) {
            throw new IllegalArgumentException("Viewport bounds must have min < max");
        }
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public double getXMin() {
        return xMin;
    }

    public double getXMax() {
        return xMax;
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }

    public double getWidth() {
        return xMax - xMin;
    }

    public double getHeight() {
        return yMax - yMin;
    }

    public double worldX(final double pixelX, final int pixelWidth) {
        requireResolution(pixelWidth);
        return xMin + pixelX / (pixelWidth - 1.0) * getWidth();
    }

    public double worldY(final double pixelY, final int pixelHeight) {
        requireResolution(pixelHeight);
        return yMax - pixelY / (pixelHeight - 1.0) * getHeight();
    }

    public double pixelX(final double worldX, final int pixelWidth) {
        requireResolution(pixelWidth);
        return (worldX - xMin) / getWidth() * (pixelWidth - 1.0);
    }

    public double pixelY(final double worldY, final int pixelHeight) {
        requireResolution(pixelHeight);
        return (yMax - worldY) / getHeight() * (pixelHeight - 1.0);
    }

    public Viewport panPixels(final double dxPixels, final double dyPixels,
            final int pixelWidth, final int pixelHeight) {
        requireResolution(pixelWidth);
        requireResolution(pixelHeight);
        final double dxWorld = dxPixels * getWidth() / (pixelWidth - 1.0);
        final double dyWorld = dyPixels * getHeight() / (pixelHeight - 1.0);
        return new Viewport(xMin - dxWorld, xMax - dxWorld,
                yMin + dyWorld, yMax + dyWorld);
    }

    public Viewport zoomAtPixel(final double pixelX, final double pixelY,
            final int pixelWidth, final int pixelHeight, final double scale) {
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Zoom scale must be finite and positive");
        }
        final double centreX = worldX(pixelX, pixelWidth);
        final double centreY = worldY(pixelY, pixelHeight);
        return new Viewport(
                centreX + (xMin - centreX) * scale,
                centreX + (xMax - centreX) * scale,
                centreY + (yMin - centreY) * scale,
                centreY + (yMax - centreY) * scale);
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
        if (!(other instanceof Viewport)) {
            return false;
        }
        final Viewport viewport = (Viewport) other;
        return Double.doubleToLongBits(xMin) == Double.doubleToLongBits(viewport.xMin)
                && Double.doubleToLongBits(xMax) == Double.doubleToLongBits(viewport.xMax)
                && Double.doubleToLongBits(yMin) == Double.doubleToLongBits(viewport.yMin)
                && Double.doubleToLongBits(yMax) == Double.doubleToLongBits(viewport.yMax);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Double.doubleToLongBits(xMin), Double.doubleToLongBits(xMax),
                Double.doubleToLongBits(yMin), Double.doubleToLongBits(yMax));
    }
}
