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
}
