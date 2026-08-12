package nlipse.render;

import nlipse.model.PlotSnapshot;

public record RenderRequest(PlotSnapshot snapshot, int width, int height,
        RenderQuality quality, long sequence, boolean exactRequired) {
    public RenderRequest {
        if (snapshot == null || quality == null) {
            throw new IllegalArgumentException("Snapshot and quality are required");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Render sequence must not be negative");
        }
        RenderDimensions.checkedPixelCount(width, height, 2);
    }

    public RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality) {
        this(snapshot, width, height, quality, 0, false);
    }

    public RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality, final long sequence) {
        this(snapshot, width, height, quality, sequence, false);
    }

    public RenderRequest withSequence(final long newSequence) {
        return new RenderRequest(snapshot, width, height, quality, newSequence, exactRequired);
    }

    public RenderRequest requiringExact() {
        return exactRequired ? this
                : new RenderRequest(snapshot, width, height, quality, sequence, true);
    }
}
