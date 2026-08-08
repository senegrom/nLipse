package nlipse.render;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

/** Completed render and the sampled field statistics used to produce it. */
public record RenderResult(
        BufferedImage image,
        long sequence,
        RenderQuality quality,
        Optional<FieldExtrema> extrema,
        long renderNanos) {

    public RenderResult {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(extrema, "extrema");
        if (sequence < 0 || renderNanos < 0) {
            throw new IllegalArgumentException("Render sequence and duration must not be negative");
        }
    }
}
