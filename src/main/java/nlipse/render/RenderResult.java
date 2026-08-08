package nlipse.render;

import java.awt.image.BufferedImage;
import java.util.Objects;
import nlipse.geometry.Point2;

/** Completed render and the sampled field statistics used to produce it. */
public record RenderResult(
        BufferedImage image,
        long sequence,
        RenderQuality quality,
        double fieldMin,
        double fieldMax,
        Point2 minPoint,
        Point2 maxPoint,
        long renderNanos) {

    public RenderResult {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(minPoint, "minPoint");
        Objects.requireNonNull(maxPoint, "maxPoint");
        if (sequence < 0 || renderNanos < 0) {
            throw new IllegalArgumentException("Render sequence and duration must not be negative");
        }
    }
}
