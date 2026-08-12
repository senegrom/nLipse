package nlipse.render;

/** Shared checked allocation contract for render and field-grid dimensions. */
final class RenderDimensions {
    private static final long BYTES_PER_PIXEL_ALLOWANCE = 32;
    private static final long MINIMUM_PIXEL_LIMIT = 4;
    private static final long MAXIMUM_PIXEL_LIMIT = 64_000_000;

    static final int MAX_PIXEL_COUNT = maximumPixelCount(
            Runtime.getRuntime().maxMemory());

    private RenderDimensions() {
    }

    static int maximumPixelCount(final long maximumHeapBytes) {
        if (maximumHeapBytes <= 0) {
            throw new IllegalArgumentException("Maximum heap size must be positive");
        }
        return (int) Math.clamp(
                maximumHeapBytes / BYTES_PER_PIXEL_ALLOWANCE,
                MINIMUM_PIXEL_LIMIT, MAXIMUM_PIXEL_LIMIT);
    }

    static int checkedPixelCount(final int width, final int height,
            final int minimumDimension) {
        if (width < minimumDimension || height < minimumDimension) {
            throw new IllegalArgumentException(
                    "Dimensions must be at least " + minimumDimension + " by "
                            + minimumDimension);
        }
        final long pixels = (long) width * height;
        if (pixels > MAX_PIXEL_COUNT) {
            throw new IllegalArgumentException(
                    "Render size " + width + "×" + height + " exceeds the "
                            + MAX_PIXEL_COUNT + "-pixel allocation limit");
        }
        return Math.toIntExact(pixels);
    }
}
