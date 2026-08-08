package nlipse.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import nlipse.geometry.Point2;
import nlipse.math.DistanceField;
import nlipse.math.DistanceFields;
import nlipse.math.ScalarRanges;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** CPU renderer that shares and caches one scalar-field grid per geometry/view. */
public final class PlotRenderer implements RenderEngine {
    private static final long CACHE_BUDGET_BYTES = 128L * 1024 * 1024;
    private static final int PALETTE_SIZE = 256;
    private static final Color BACKGROUND_TARGET = new Color(0, 100, 0);
    private static final Color AXIS_COLOR = new Color(125, 125, 125);
    private static final Color FOCUS_COLOR = new Color(35, 90, 210);
    private static final Color SELECTED_FOCUS_COLOR = new Color(245, 145, 20);
    private static final Color MIN_COLOR = new Color(210, 45, 45);
    private static final Color MAX_COLOR = new Color(15, 175, 190);
    private static final int[] BACKGROUND_PALETTE = createBackgroundPalette();

    private final Map<FieldKey, FieldGrid> gridCache = new LinkedHashMap<>(8, 0.75f, true);
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private long cachedGridBytes;

    @Override
    public RenderResult render(final RenderRequest request, final CancellationToken token) {
        if (request == null || token == null) {
            throw new IllegalArgumentException("Render request and cancellation token are required");
        }
        final long started = System.nanoTime();
        token.throwIfCancelled();

        final PlotSnapshot snapshot = request.snapshot();
        final DistanceField field = DistanceFields.create(snapshot.curveType(), snapshot.foci());
        final FieldGrid grid = getGrid(request, field, token);
        token.throwIfCancelled();

        final BufferedImage image = new BufferedImage(request.width(), request.height(),
                BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            if (!snapshot.showBackground()) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, request.width(), request.height());
            }
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    snapshot.antiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON
                            : RenderingHints.VALUE_ANTIALIAS_OFF);

            if (snapshot.showBackground()) {
                drawBackground(image, graphics, grid, token);
            }
            drawAxes(graphics, snapshot.viewport(), request.width(), request.height());
            drawContours(graphics, request, grid, field, token);
            drawFoci(graphics, request);
            if (snapshot.showExtrema()) {
                grid.getExtrema().ifPresent(extrema -> {
                    drawMarker(graphics, snapshot.viewport(), request.width(), request.height(),
                            extrema.minimumPoint(), MIN_COLOR, 7);
                    drawMarker(graphics, snapshot.viewport(), request.width(), request.height(),
                            extrema.maximumPoint(), MAX_COLOR, 7);
                });
            }
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawRect(0, 0, request.width() - 1, request.height() - 1);
        } finally {
            graphics.dispose();
        }

        token.throwIfCancelled();
        return new RenderResult(image, request.sequence(), request.quality(), grid.getExtrema(),
                System.nanoTime() - started);
    }

    private FieldGrid getGrid(final RenderRequest request, final DistanceField field,
            final CancellationToken token) {
        final FieldKey key = FieldKey.from(request);
        synchronized (gridCache) {
            final FieldGrid cached = gridCache.get(key);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return cached;
            }
        }

        cacheMisses.incrementAndGet();
        final FieldGrid sampled = FieldGrid.sample(field, request.snapshot().viewport(),
                request.width(), request.height(), request.quality().sampleStep(), token);
        token.throwIfCancelled();
        synchronized (gridCache) {
            final FieldGrid raced = gridCache.get(key);
            if (raced != null) {
                cacheHits.incrementAndGet();
                return raced;
            }
            gridCache.put(key, sampled);
            cachedGridBytes += sampled.estimatedBytes();
            evictOversizedCache();
        }
        return sampled;
    }

    private void evictOversizedCache() {
        final Iterator<Map.Entry<FieldKey, FieldGrid>> entries = gridCache.entrySet().iterator();
        while (cachedGridBytes > CACHE_BUDGET_BYTES && gridCache.size() > 1 && entries.hasNext()) {
            final Map.Entry<FieldKey, FieldGrid> entry = entries.next();
            cachedGridBytes -= entry.getValue().estimatedBytes();
            entries.remove();
        }
    }

    private static void drawBackground(final BufferedImage image, final Graphics2D graphics,
            final FieldGrid grid, final CancellationToken token) {
        if (grid.getExtrema().isEmpty()) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            return;
        }
        if (grid.getColumns() == image.getWidth() && grid.getRows() == image.getHeight()) {
            final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            fillBackgroundPixels(pixels, grid, token);
            return;
        }
        final BufferedImage sampled = new BufferedImage(grid.getColumns(), grid.getRows(),
                BufferedImage.TYPE_INT_RGB);
        final int[] pixels = ((DataBufferInt) sampled.getRaster().getDataBuffer()).getData();
        fillBackgroundPixels(pixels, grid, token);
        final Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(sampled, 0, 0, grid.getPixelWidth(), grid.getPixelHeight(), null);
        if (oldInterpolation != null) {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private static void fillBackgroundPixels(final int[] pixels, final FieldGrid grid,
            final CancellationToken token) {
        final FieldExtrema extrema = grid.getExtrema().orElse(null);
        for (int row = 0; row < grid.getRows(); row++) {
            if ((row & 15) == 0) {
                token.throwIfCancelled();
            }
            final int offset = row * grid.getColumns();
            for (int column = 0; column < grid.getColumns(); column++) {
                final double value = grid.getValue(column, row);
                final int paletteIndex;
                if (extrema != null && extrema.maximum() > extrema.minimum()
                        && Double.isFinite(value)) {
                    paletteIndex = Math.clamp(
                            (int) Math.round(ScalarRanges.fraction(value,
                                    extrema.minimum(), extrema.maximum()) * (PALETTE_SIZE - 1)),
                            0, PALETTE_SIZE - 1);
                } else {
                    paletteIndex = 0;
                }
                pixels[offset + column] = BACKGROUND_PALETTE[paletteIndex];
            }
        }
    }

    private static int[] createBackgroundPalette() {
        final int[] palette = new int[PALETTE_SIZE];
        for (int index = 0; index < palette.length; index++) {
            final double fraction = index / (double) (palette.length - 1);
            final int red = (int) Math.round(255 * (1 - fraction)
                    + BACKGROUND_TARGET.getRed() * fraction);
            final int green = (int) Math.round(255 * (1 - fraction)
                    + BACKGROUND_TARGET.getGreen() * fraction);
            final int blue = (int) Math.round(255 * (1 - fraction)
                    + BACKGROUND_TARGET.getBlue() * fraction);
            palette[index] = 0xFF000000 | red << 16 | green << 8 | blue;
        }
        return palette;
    }

    private static void drawAxes(final Graphics2D graphics, final Viewport viewport,
            final int width, final int height) {
        graphics.setColor(AXIS_COLOR);
        graphics.setStroke(new BasicStroke(1f));
        if (viewport.yMin() <= 0 && viewport.yMax() >= 0) {
            final double y = viewport.pixelY(0, height);
            graphics.drawLine(0, (int) Math.round(y), width - 1, (int) Math.round(y));
        }
        if (viewport.xMin() <= 0 && viewport.xMax() >= 0) {
            final double x = viewport.pixelX(0, width);
            graphics.drawLine((int) Math.round(x), 0, (int) Math.round(x), height - 1);
        }
    }

    private static void drawContours(final Graphics2D graphics, final RenderRequest request,
            final FieldGrid grid, final DistanceField field, final CancellationToken token) {
        if (grid.getExtrema().isEmpty()) {
            return;
        }
        final PlotSnapshot snapshot = request.snapshot();
        final double[] levels = levels(snapshot.distanceMin(), snapshot.distanceMax(),
                snapshot.curveCount(), snapshot.logSpacing());
        final Path2D.Float[] paths = new Path2D.Float[levels.length];
        for (int index = 0; index < paths.length; index++) {
            paths[index] = new Path2D.Float();
        }

        MarchingSquares.traceLevels(grid, field, snapshot.viewport(), levels, token,
                (levelIndex, x1, y1, x2, y2) -> {
                    final Path2D.Float path = paths[levelIndex];
                    path.moveTo(x1, y1);
                    path.lineTo(x2, y2);
                });

        graphics.setStroke(new BasicStroke(request.quality() == RenderQuality.FULL ? 1.25f : 1f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int index = 0; index < paths.length; index++) {
            token.throwIfCancelled();
            graphics.setColor(curveColor(index, paths.length));
            graphics.draw(paths[index]);
        }
    }

    private static void drawFoci(final Graphics2D graphics, final RenderRequest request) {
        final PlotSnapshot snapshot = request.snapshot();
        for (int index = 0; index < snapshot.foci().size(); index++) {
            final Focus focus = snapshot.foci().get(index);
            final Point2 point = new Point2(focus.x(), focus.y());
            drawMarker(graphics, snapshot.viewport(), request.width(), request.height(), point,
                    index == snapshot.selectedFocusIndex() ? SELECTED_FOCUS_COLOR : FOCUS_COLOR,
                    index == snapshot.selectedFocusIndex() ? 10 : 8);
        }
    }

    private static void drawMarker(final Graphics2D graphics, final Viewport viewport,
            final int width, final int height, final Point2 point, final Color color, final int diameter) {
        final double x = viewport.pixelX(point.x(), width);
        final double y = viewport.pixelY(point.y(), height);
        final int left = (int) Math.round(x - diameter / 2.0);
        final int top = (int) Math.round(y - diameter / 2.0);
        graphics.setColor(color);
        graphics.fillOval(left, top, diameter, diameter);
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawOval(left, top, diameter, diameter);
    }

    static double[] levels(final double min, final double max, final int count,
            final boolean logarithmic) {
        final int safeCount = Math.max(1, count);
        if (safeCount == 1 || min == max) {
            return new double[]{min};
        }
        final double[] levels = new double[safeCount];
        final boolean useLog = logarithmic && min > 0 && max > 0;
        final double logMin = useLog ? Math.log(min) : 0;
        final double logMax = useLog ? Math.log(max) : 0;
        for (int index = 0; index < safeCount; index++) {
            final double fraction = index / (double) (safeCount - 1);
            levels[index] = useLog ? Math.exp(logMin + (logMax - logMin) * fraction)
                    : ScalarRanges.interpolate(min, max, fraction);
        }
        return levels;
    }

    private static Color curveColor(final int index, final int count) {
        if (count <= 1) {
            return Color.BLACK;
        }
        final float hue = (float) (0.66 * (1.0 - index / (double) (count - 1)));
        return Color.getHSBColor(hue, 0.85f, 0.72f);
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public long getCachedGridBytes() {
        synchronized (gridCache) {
            return cachedGridBytes;
        }
    }

    public String cacheSummary() {
        return String.format(Locale.ROOT, "%d hits / %d misses / %.1f MiB",
                getCacheHits(), getCacheMisses(), getCachedGridBytes() / 1_048_576.0);
    }
}
