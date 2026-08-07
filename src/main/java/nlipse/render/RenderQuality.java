package nlipse.render;

public enum RenderQuality {
    PREVIEW(4),
    FULL(1);

    private final int sampleStep;

    RenderQuality(final int sampleStep) {
        this.sampleStep = sampleStep;
    }

    public int getSampleStep() {
        return sampleStep;
    }
}
