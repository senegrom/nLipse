package nlipse.render;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

/** Completed raster render plus the immutable geometry package used to produce it. */
public record RenderResult(
        BufferedImage image,
        long sequence,
        RenderQuality quality,
        Optional<FieldExtrema> extrema,
        long renderNanos,
        Optional<RenderPackage> renderPackage) {

    public RenderResult(final BufferedImage image, final long sequence,
            final RenderQuality quality, final Optional<FieldExtrema> extrema,
            final long renderNanos) {
        this(image, sequence, quality, extrema, renderNanos, Optional.empty());
    }

    public RenderResult(final BufferedImage image, final long sequence,
            final RenderQuality quality, final Optional<FieldExtrema> extrema,
            final long renderNanos, final RenderPackage renderPackage) {
        this(image, sequence, quality, extrema, renderNanos, Optional.of(renderPackage));
    }

    public RenderResult {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(extrema, "extrema");
        Objects.requireNonNull(renderPackage, "renderPackage");
        if (sequence < 0 || renderNanos < 0) {
            throw new IllegalArgumentException("Render sequence and duration must not be negative");
        }
        renderPackage.ifPresent(completed -> {
            if (completed.width() != image.getWidth() || completed.height() != image.getHeight()) {
                throw new IllegalArgumentException("Render package size must match the image");
            }
            if (completed.quality() != quality || !completed.extrema().equals(extrema)) {
                throw new IllegalArgumentException("Render package metadata must match the result");
            }
        });
    }
}
