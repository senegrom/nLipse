package nlipse.render;

import java.awt.image.BufferedImage;
import java.util.Optional;

public record RenderResult(BufferedImage image, long sequence, RenderQuality quality,
        Optional<FieldExtrema> extrema, long renderNanos, boolean precisionLimited,
        Optional<RenderPackage> renderPackage) {
    public RenderResult {
        if (image == null || quality == null || extrema == null || renderPackage == null) {
            throw new IllegalArgumentException("Render result values must not be null");
        }
        if (sequence < 0 || renderNanos < 0) {
            throw new IllegalArgumentException(
                    "Render sequence and duration must not be negative");
        }
        renderPackage.ifPresent(completed -> {
            if (completed.width() != image.getWidth() || completed.height() != image.getHeight()
                    || completed.quality() != quality
                    || !completed.extrema().equals(extrema)) {
                throw new IllegalArgumentException(
                        "Render package must describe the completed raster result");
            }
        });
    }

    public RenderResult(final BufferedImage image, final long sequence,
            final RenderQuality quality, final Optional<FieldExtrema> extrema,
            final long renderNanos) {
        this(image, sequence, quality, extrema, renderNanos, false, Optional.empty());
    }

    public RenderResult(final BufferedImage image, final long sequence,
            final RenderQuality quality, final Optional<FieldExtrema> extrema,
            final long renderNanos, final boolean precisionLimited) {
        this(image, sequence, quality, extrema, renderNanos, precisionLimited,
                Optional.empty());
    }

    public RenderResult(final BufferedImage image, final long sequence,
            final RenderQuality quality, final Optional<FieldExtrema> extrema,
            final long renderNanos, final RenderPackage renderPackage) {
        this(image, sequence, quality, extrema, renderNanos, false,
                requiredPackage(renderPackage));
    }

    public RenderResult(final BufferedImage image, final long sequence,
            final RenderQuality quality, final Optional<FieldExtrema> extrema,
            final long renderNanos, final boolean precisionLimited,
            final RenderPackage renderPackage) {
        this(image, sequence, quality, extrema, renderNanos, precisionLimited,
                requiredPackage(renderPackage));
    }

    private static Optional<RenderPackage> requiredPackage(
            final RenderPackage renderPackage) {
        if (renderPackage == null) {
            throw new IllegalArgumentException("Render package is required");
        }
        return Optional.of(renderPackage);
    }
}
