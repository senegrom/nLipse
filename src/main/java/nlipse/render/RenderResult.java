package nlipse.render;

import java.awt.image.BufferedImage;
import nlipse.geometry.Point2;

/** Completed render and the sampled field statistics used to produce it. */
public final class RenderResult {
    private final BufferedImage image;
    private final long sequence;
    private final RenderQuality quality;
    private final double fieldMin;
    private final double fieldMax;
    private final Point2 minPoint;
    private final Point2 maxPoint;
    private final long renderNanos;

    public RenderResult(final BufferedImage image, final long sequence, final RenderQuality quality,
            final double fieldMin, final double fieldMax, final Point2 minPoint,
            final Point2 maxPoint, final long renderNanos) {
        if (image == null || quality == null || minPoint == null || maxPoint == null) {
            throw new IllegalArgumentException("Render result fields must not be null");
        }
        this.image = image;
        this.sequence = sequence;
        this.quality = quality;
        this.fieldMin = fieldMin;
        this.fieldMax = fieldMax;
        this.minPoint = minPoint;
        this.maxPoint = maxPoint;
        this.renderNanos = renderNanos;
    }

    public BufferedImage getImage() {
        return image;
    }

    public long getSequence() {
        return sequence;
    }

    public RenderQuality getQuality() {
        return quality;
    }

    public double getFieldMin() {
        return fieldMin;
    }

    public double getFieldMax() {
        return fieldMax;
    }

    public Point2 getMinPoint() {
        return minPoint;
    }

    public Point2 getMaxPoint() {
        return maxPoint;
    }

    public long getRenderNanos() {
        return renderNanos;
    }
}
