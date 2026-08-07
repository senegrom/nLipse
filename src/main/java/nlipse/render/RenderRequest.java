package nlipse.render;

import nlipse.model.PlotSnapshot;

/** Immutable render input. */
public final class RenderRequest {
    private final PlotSnapshot snapshot;
    private final int width;
    private final int height;
    private final RenderQuality quality;
    private final long sequence;

    public RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality) {
        this(snapshot, width, height, quality, 0);
    }

    private RenderRequest(final PlotSnapshot snapshot, final int width, final int height,
            final RenderQuality quality, final long sequence) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Plot snapshot is required");
        }
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Render size must be at least 2 by 2");
        }
        if (quality == null) {
            throw new IllegalArgumentException("Render quality is required");
        }
        this.snapshot = snapshot;
        this.width = width;
        this.height = height;
        this.quality = quality;
        this.sequence = sequence;
    }

    public RenderRequest withSequence(final long newSequence) {
        return new RenderRequest(snapshot, width, height, quality, newSequence);
    }

    public PlotSnapshot getSnapshot() {
        return snapshot;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public RenderQuality getQuality() {
        return quality;
    }

    public long getSequence() {
        return sequence;
    }
}
