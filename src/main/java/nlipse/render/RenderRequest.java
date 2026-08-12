package nlipse.render;

import java.util.Objects;
import nlipse.model.PlotSnapshot;

/** Immutable render input. */
public record RenderRequest(
        PlotSnapshot snapshot,
        int width,
        int height,
        RenderQuality quality,
        RenderExactness exactness,
        long sequence) {

    public RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality) {
        this(snapshot, width, height, quality, RenderExactness.ALLOW_LIMITED, 0);
    }

    public RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality, final RenderExactness exactness) {
        this(snapshot, width, height, quality, exactness, 0);
    }

    public RenderRequest {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(exactness, "exactness");
        RenderDimensions.validate(width, height);
        if (sequence < 0) {
            throw new IllegalArgumentException("Render sequence must not be negative");
        }
    }

    public RenderRequest withSequence(final long newSequence) {
        return new RenderRequest(snapshot, width, height, quality, exactness, newSequence);
    }
}
