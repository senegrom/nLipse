package nlipse.render;

/** Immutable mapping between world coordinates and a pixel rectangle. */
public record Viewport(double xMin, double xMax, double yMin, double yMax) {
    public Viewport {
        if (!Double.isFinite(xMin) || !Double.isFinite(xMax)
                || !Double.isFinite(yMin) || !Double.isFinite(yMax)) {
            throw new IllegalArgumentException("Viewport bounds must be finite");
        }
        if (xMin >= xMax || yMin >= yMax) {
            throw new IllegalArgumentException("Viewport bounds must have min < max");
        }
        if (!Double.isFinite(xMax - xMin) || !Double.isFinite(yMax - yMin)) {
            throw new IllegalArgumentException("Viewport spans must be finite");
        }
    }

    public double width() {
        return xMax - xMin;
    }

    public double height() {
        return yMax - yMin;
    }

    public double worldX(final double pixelX, final int pixelWidth) {
        requireResolution(pixelWidth);
        return xMin + pixelX / (pixelWidth - 1.0) * width();
    }

    public double worldY(final double pixelY, final int pixelHeight) {
        requireResolution(pixelHeight);
        return yMax - pixelY / (pixelHeight - 1.0) * height();
    }

    public double pixelX(final double worldX, final int pixelWidth) {
        requireResolution(pixelWidth);
        return (worldX - xMin) / width() * (pixelWidth - 1.0);
    }

    public double pixelY(final double worldY, final int pixelHeight) {
        requireResolution(pixelHeight);
        return (yMax - worldY) / height() * (pixelHeight - 1.0);
    }

    public Viewport panPixels(final double dxPixels, final double dyPixels,
            final int pixelWidth, final int pixelHeight) {
        requireResolution(pixelWidth);
        requireResolution(pixelHeight);
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
        final double centreX = worldX(pixelX, pixelWidth);
        final double centreY = worldY(pixelY, pixelHeight);
        return transformedOrThis(
                centreX + (xMin - centreX) * scale,
                centreX + (xMax - centreX) * scale,
                centreY + (yMin - centreY) * scale,
                centreY + (yMax - centreY) * scale);
    }

    private Viewport transformedOrThis(final double newXMin, final double newXMax,
            final double newYMin, final double newYMax) {
        if (!Double.isFinite(newXMin) || !Double.isFinite(newXMax)
                || !Double.isFinite(newYMin) || !Double.isFinite(newYMax)
                || newXMin >= newXMax || newYMin >= newYMax
                || !Double.isFinite(newXMax - newXMin)
                || !Double.isFinite(newYMax - newYMin)) {
            return this;
        }
        return new Viewport(newXMin, newXMax, newYMin, newYMax);
    }

    private static void requireResolution(final int resolution) {
        if (resolution < 2) {
            throw new IllegalArgumentException("Pixel resolution must be at least 2");
        }
    }
}
