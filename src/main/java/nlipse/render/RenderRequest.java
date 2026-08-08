package nlipse.render;

import java.util.Objects;
import nlipse.model.PlotSnapshot;

/** Immutable render input. */
public record RenderRequest(
        PlotSnapshot snapshot,
        int width,
        int height,
        RenderQuality quality,
        long sequence) {

    public RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality) {
        this(snapshot, width, height, quality, 0);
    }

    public RenderRequest {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(quality, "quality");
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Render size must be at least 2 by 2");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Render sequence must not be negative");
        }
    }

    public RenderRequest withSequence(final long newSequence) {
        return new RenderRequest(snapshot, width, height, quality, newSequence);
    }
}
