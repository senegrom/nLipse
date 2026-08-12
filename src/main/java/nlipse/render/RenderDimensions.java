package nlipse.render;

/** Shared checked allocation policy for raster images and sampled field grids. */
final class RenderDimensions {
    private static final long MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8L;
    private static final long MIN_DEFAULT_PIXELS = 256L * 256;
    private static final long MAX_DEFAULT_PIXELS = 64_000_000L;
    private static final long ESTIMATED_BYTES_PER_PIXEL = 32;
    private static final String LIMIT_PROPERTY = "nlipse.maxRenderPixels";

    private RenderDimensions() {
    }

    static int pixelCount(final int width, final int height) {
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Render size must be at least 2 by 2");
        }
        final long count = (long) width * height;
        final long limit = maximumPixels();
        if (count > MAX_ARRAY_LENGTH || count > limit) {
            throw new IllegalArgumentException("Render size " + width + 'x' + height
                    + " requires " + count + " pixels; maximum is " + limit);
        }
        return (int) count;
    }

    static void validate(final int width, final int height) {
        pixelCount(width, height);
    }

    static long maximumPixels() {
        final String configured = System.getProperty(LIMIT_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            final long parsed;
            try {
                parsed = Long.parseLong(configured.trim());
            } catch (final NumberFormatException exception) {
                throw new IllegalArgumentException(LIMIT_PROPERTY
                        + " must be a positive integer", exception);
            }
            if (parsed < 4 || parsed > MAX_ARRAY_LENGTH) {
                throw new IllegalArgumentException(LIMIT_PROPERTY + " must be between 4 and "
                        + MAX_ARRAY_LENGTH);
            }
            return parsed;
        }
        return Math.clamp(Runtime.getRuntime().maxMemory() / ESTIMATED_BYTES_PER_PIXEL,
                MIN_DEFAULT_PIXELS, MAX_DEFAULT_PIXELS);
    }
}
