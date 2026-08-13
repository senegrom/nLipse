package nlipse.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import nlipse.math.DistanceField;
import nlipse.math.DistanceFields;
import nlipse.math.ExactBudget;
import nlipse.math.ScalarRanges;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** CPU renderer with bounded field and marker-free layer caches. */
public final class PlotRenderer implements RenderEngine {
    private static final long MEBIBYTE = 1024L * 1024;
    private static final int PALETTE_SIZE = 256;
    private static final Color BACKGROUND_TARGET = new Color(0, 100, 0);
    static final Color AXIS_COLOR = new Color(125, 125, 125);
    static final Color FOCUS_COLOR = new Color(35, 90, 210);
    static final Color SELECTED_FOCUS_COLOR = new Color(245, 145, 20);
    static final Color MIN_COLOR = new Color(210, 45, 45);
    static final Color MAX_COLOR = new Color(15, 175, 190);
    private static final Color LEGEND_BACKGROUND = new Color(255, 255, 255, 235);
    private static final int LEGEND_MAX_ROWS = 12;
    private static final int LEGEND_MARGIN = 8;
    private static final int LEGEND_PADDING = 7;
    private static final int LEGEND_SWATCH_WIDTH = 18;
    private static final int LEGEND_GAP = 6;
    private static final int[] BACKGROUND_PALETTE = createBackgroundPalette();

    private final long cacheBudgetBytes;
    private final long worldTileBudgetBytes;
    private final long gridBudgetBytes;
    private final long contourBudgetBytes;
    private final long layerBudgetBytes;
    private final WorldFieldCache worldFieldCache;
    private final Map<FieldKey, FieldGrid> gridCache = new LinkedHashMap<>(8, 0.75f, true);
    private final Map<ContourKey, ContourGeometry> contourCache =
            new LinkedHashMap<>(8, 0.75f, true);
    private final Map<LayerKey, BufferedImage> layerCache = new LinkedHashMap<>(8, 0.75f, true);
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong derivedGridHits = new AtomicLong();
    private final AtomicLong contourCacheHits = new AtomicLong();
    private final AtomicLong contourCacheMisses = new AtomicLong();
    private final AtomicLong layerCacheHits = new AtomicLong();
    private final AtomicLong layerCacheMisses = new AtomicLong();
    private final AtomicLong fullQualityPreviewHits = new AtomicLong();
    private final AtomicLong exactEvaluations = new AtomicLong();
    private long cachedGridBytes;
    private long cachedContourBytes;
    private long cachedLayerBytes;

    public PlotRenderer() {
        cacheBudgetBytes = cacheBudgetBytes();
        worldTileBudgetBytes = cacheBudgetBytes * 45 / 100;
        gridBudgetBytes = cacheBudgetBytes * 20 / 100;
        contourBudgetBytes = cacheBudgetBytes * 15 / 100;
        layerBudgetBytes = cacheBudgetBytes - worldTileBudgetBytes
                - gridBudgetBytes - contourBudgetBytes;
        worldFieldCache = new WorldFieldCache(worldTileBudgetBytes);
    }

    @Override
    public RenderResult render(final RenderRequest request, final CancellationToken token) {
        if (request == null || token == null) {
            throw new IllegalArgumentException("Render request and cancellation token are required");
        }
        final long started = System.nanoTime();
        token.throwIfCancelled();

        final PlotSnapshot snapshot = request.snapshot();
        final RenderArtifacts cachedFull = request.quality() == RenderQuality.PREVIEW
                ? fullQualityArtifacts(request) : null;
        final FieldGrid grid;
        final ContourGeometry contours;
        final double[] levels;
        final BufferedImage cachedLayer;
        final boolean cacheableArtifacts;
        if (cachedFull != null) {
            grid = cachedFull.grid();
            contours = cachedFull.contours();
            levels = cachedFull.levels();
            cachedLayer = cachedFull.layer();
            cacheableArtifacts = true;
        } else {
            final ExactBudget exactBudget = request.exactRequired()
                    ? ExactBudget.unlimited()
                    : ExactBudget.limited(exactBudget(request.width(), request.height()));
            final DistanceField field = DistanceFields.create(snapshot.curveType(), snapshot.foci(),
                    snapshot.familyParameter(), exactBudget);
            try {
                grid = getGrid(request, field, exactBudget, token);
                levels = contourLevels(snapshot, grid);
                contours = getContourGeometry(request, grid, field, levels, exactBudget, token);
            } finally {
                exactEvaluations.addAndGet(exactBudget.spent());
            }
            cachedLayer = null;
            cacheableArtifacts = !exactBudget.exhausted();
        }
        final RenderPackage completed = new RenderPackage(snapshot,
                request.width(), request.height(), request.quality(), levels,
                levelColors(levels.length), grid.getExtrema(), contours);
        final BufferedImage staticLayer = cachedLayer != null
                ? cachedLayer : getStaticLayer(request, grid, completed,
                        cacheableArtifacts, token);
        token.throwIfCancelled();

        final BufferedImage image = copyImage(staticLayer);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    snapshot.antiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON
                            : RenderingHints.VALUE_ANTIALIAS_OFF);
            drawFoci(graphics, request);
            if (snapshot.showExtrema()) {
                completed.extrema().ifPresent(extrema -> {
                    drawMarker(graphics, snapshot.viewport(), request.width(), request.height(),
                            extrema.minimumPoint().x(), extrema.minimumPoint().y(), MIN_COLOR, 7);
                    drawMarker(graphics, snapshot.viewport(), request.width(), request.height(),
                            extrema.maximumPoint().x(), extrema.maximumPoint().y(), MAX_COLOR, 7);
                });
            }
            if (snapshot.showLegend() && completed.levelCount() > 0) {
                drawLegend(graphics, completed);
            }
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawRect(0, 0, request.width() - 1, request.height() - 1);
        } finally {
            graphics.dispose();
        }

        token.throwIfCancelled();
        return new RenderResult(image, request.sequence(), request.quality(), completed.extrema(),
                System.nanoTime() - started, !cacheableArtifacts, completed);
    }

    /** Ill-conditioned samples normally form a curve, so a per-pass allowance that
     *  scales with the image perimeter covers them while capping the degenerate
     *  case in which every sample would otherwise be evaluated exactly. */
    static long exactBudget(final int width, final int height) {
        return Math.clamp((long) width + height, 4096, 65_536);
    }

    private static double[] contourLevels(final PlotSnapshot snapshot, final FieldGrid grid) {
        return grid.getExtrema().isEmpty() ? new double[0]
                : levels(snapshot.distanceMin(), snapshot.distanceMax(),
                        snapshot.curveCount(), snapshot.logSpacing());
    }

    private static Color[] levelColors(final int count) {
        final Color[] colors = new Color[count];
        for (int index = 0; index < count; index++) {
            colors[index] = curveColor(index, count);
        }
        return colors;
    }

    private RenderArtifacts fullQualityArtifacts(final RenderRequest request) {
        final FieldKey fullFieldKey = FieldKey.from(request).withSampleStep(1);
        final FieldGrid grid;
        synchronized (gridCache) {
            grid = gridCache.get(fullFieldKey);
        }
        if (grid == null) {
            return null;
        }
        final double[] levels = contourLevels(request.snapshot(), grid);
        final ContourGeometry contours;
        synchronized (contourCache) {
            contours = contourCache.get(ContourKey.from(fullFieldKey, levels));
        }
        if (contours == null) {
            return null;
        }
        final LayerKey fullLayerKey = LayerKey.from(request).asFullQuality();
        final BufferedImage layer;
        synchronized (layerCache) {
            layer = layerCache.get(fullLayerKey);
        }
        if (layer == null) {
            return null;
        }
        cacheHits.incrementAndGet();
        contourCacheHits.incrementAndGet();
        layerCacheHits.incrementAndGet();
        fullQualityPreviewHits.incrementAndGet();
        return new RenderArtifacts(grid, layer, contours, levels);
    }

    private FieldGrid getGrid(final RenderRequest request, final DistanceField field,
            final ExactBudget exactBudget, final CancellationToken token) {
        final FieldKey key = FieldKey.from(request);
        synchronized (gridCache) {
            final FieldGrid cached = gridCache.get(key);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return cached;
            }
        }

        if (key.sampleStep() > 1) {
            final FieldKey fullKey = key.withSampleStep(1);
            final FieldGrid fullGrid;
            synchronized (gridCache) {
                fullGrid = gridCache.get(fullKey);
            }
            if (fullGrid != null) {
                token.throwIfCancelled();
                final FieldGrid derived = fullGrid.coarsen(key.sampleStep(),
                        request.snapshot().viewport());
                derivedGridHits.incrementAndGet();
                cacheHits.incrementAndGet();
                return cacheGrid(key, derived);
            }
        }

        cacheMisses.incrementAndGet();
        final FieldGrid sampled;
        try {
            sampled = key.sampleStep() == 1
                    ? worldFieldCache.sample(key.identity(), field, request.snapshot().viewport(),
                            request.width(), request.height(), token)
                    : FieldGrid.sample(field, request.snapshot().viewport(),
                            request.width(), request.height(), key.sampleStep(), token);
        } finally {
            if (key.sampleStep() == 1 && exactBudget.exhausted()) {
                worldFieldCache.invalidate(key.identity());
            }
        }
        token.throwIfCancelled();
        return exactBudget.exhausted() ? sampled : cacheGrid(key, sampled);
    }

    private FieldGrid cacheGrid(final FieldKey key, final FieldGrid sampled) {
        synchronized (gridCache) {
            final FieldGrid raced = gridCache.get(key);
            if (raced != null) {
                cacheHits.incrementAndGet();
                return raced;
            }
            final long bytes = sampled.estimatedBytes();
            if (bytes > gridBudgetBytes) {
                return sampled;
            }
            gridCache.put(key, sampled);
            cachedGridBytes += bytes;
            evictOversizedGridCache();
            return sampled;
        }
    }

    private BufferedImage getStaticLayer(final RenderRequest request, final FieldGrid grid,
            final RenderPackage completed, final boolean cacheable,
            final CancellationToken token) {
        final LayerKey key = LayerKey.from(request);
        synchronized (layerCache) {
            final BufferedImage cached = layerCache.get(key);
            if (cached != null) {
                layerCacheHits.incrementAndGet();
                return cached;
            }
        }

        layerCacheMisses.incrementAndGet();
        final BufferedImage rendered = renderStaticLayer(request, grid, completed, token);
        if (!cacheable) {
            return rendered;
        }
        final long bytes = imageBytes(rendered);
        if (bytes > layerBudgetBytes) {
            return rendered;
        }
        synchronized (layerCache) {
            final BufferedImage raced = layerCache.get(key);
            if (raced != null) {
                layerCacheHits.incrementAndGet();
                return raced;
            }
            layerCache.put(key, rendered);
            cachedLayerBytes += bytes;
            evictOversizedLayerCache();
            return rendered;
        }
    }

    private BufferedImage renderStaticLayer(final RenderRequest request,
            final FieldGrid grid, final RenderPackage completed, final CancellationToken token) {
        final PlotSnapshot snapshot = request.snapshot();
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
            drawContours(graphics, request, completed, token);
        } finally {
            graphics.dispose();
        }
        token.throwIfCancelled();
        return image;
    }

    private ContourGeometry getContourGeometry(final RenderRequest request,
            final FieldGrid grid, final DistanceField field, final double[] levels,
            final ExactBudget exactBudget, final CancellationToken token) {
        final ContourKey key = ContourKey.from(request, levels);
        synchronized (contourCache) {
            final ContourGeometry cached = contourCache.get(key);
            if (cached != null) {
                contourCacheHits.incrementAndGet();
                return cached;
            }
        }

        contourCacheMisses.incrementAndGet();
        final ContourGeometry traced = grid.getExtrema().isEmpty()
                ? ContourGeometry.trace(grid, field, request.snapshot().viewport(),
                        new double[0], token)
                : ContourGeometry.trace(grid, field, request.snapshot().viewport(), levels, token);
        if (exactBudget.exhausted()) {
            return traced;
        }
        final long bytes = traced.estimatedBytes();
        if (bytes > contourBudgetBytes) {
            return traced;
        }
        synchronized (contourCache) {
            final ContourGeometry raced = contourCache.get(key);
            if (raced != null) {
                contourCacheHits.incrementAndGet();
                return raced;
            }
            contourCache.put(key, traced);
            cachedContourBytes += bytes;
            evictOversizedContourCache();
            return traced;
        }
    }

    private void evictOversizedGridCache() {
        final Iterator<Map.Entry<FieldKey, FieldGrid>> entries = gridCache.entrySet().iterator();
        while (cachedGridBytes > gridBudgetBytes && gridCache.size() > 1 && entries.hasNext()) {
            final Map.Entry<FieldKey, FieldGrid> entry = entries.next();
            cachedGridBytes -= entry.getValue().estimatedBytes();
            entries.remove();
        }
    }

    private void evictOversizedContourCache() {
        final Iterator<Map.Entry<ContourKey, ContourGeometry>> entries =
                contourCache.entrySet().iterator();
        while (cachedContourBytes > contourBudgetBytes && entries.hasNext()) {
            final Map.Entry<ContourKey, ContourGeometry> entry = entries.next();
            cachedContourBytes -= entry.getValue().estimatedBytes();
            entries.remove();
        }
    }

    private void evictOversizedLayerCache() {
        final Iterator<Map.Entry<LayerKey, BufferedImage>> entries = layerCache.entrySet().iterator();
        while (cachedLayerBytes > layerBudgetBytes && entries.hasNext()) {
            final Map.Entry<LayerKey, BufferedImage> entry = entries.next();
            cachedLayerBytes -= imageBytes(entry.getValue());
            entries.remove();
        }
    }

    private static long cacheBudgetBytes() {
        final long maximumHeap = Runtime.getRuntime().maxMemory();
        final long defaultBudget = Math.clamp(maximumHeap / 8, 32 * MEBIBYTE, 256 * MEBIBYTE);
        final String configured = System.getProperty("nlipse.cacheMiB");
        if (configured == null || configured.isBlank()) {
            return defaultBudget;
        }
        try {
            final long mebibytes = Long.parseLong(configured.trim());
            return Math.clamp(mebibytes, 16, 2048) * MEBIBYTE;
        } catch (final NumberFormatException ignored) {
            return defaultBudget;
        }
    }

    private static BufferedImage copyImage(final BufferedImage source) {
        final BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        final int[] sourcePixels = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();
        final int[] targetPixels = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);
        return copy;
    }

    private static long imageBytes(final BufferedImage image) {
        return 128L + (long) image.getWidth() * image.getHeight() * Integer.BYTES;
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
        final int[] sampledPixels = new int[grid.getColumns() * grid.getRows()];
        fillBackgroundPixels(sampledPixels, grid, token);
        final int[] targetPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        fillInterpolatedBackground(targetPixels, image.getWidth(), image.getHeight(),
                sampledPixels, grid, token);
    }


    private static void fillInterpolatedBackground(final int[] targetPixels,
            final int width, final int height, final int[] sampledPixels,
            final FieldGrid grid, final CancellationToken token) {
        int lowerRow = 0;
        for (int y = 0; y < height; y++) {
            if ((y & 15) == 0) {
                token.throwIfCancelled();
            }
            while (lowerRow + 1 < grid.getRows() - 1
                    && y > grid.getPixelY(lowerRow + 1)) {
                lowerRow++;
            }
            final int upperRow = Math.min(lowerRow + 1, grid.getRows() - 1);
            final int y0 = grid.getPixelY(lowerRow);
            final int y1 = grid.getPixelY(upperRow);
            final double vertical = y1 == y0 ? 0 : (y - y0) / (double) (y1 - y0);
            int lowerColumn = 0;
            final int targetOffset = y * width;
            for (int x = 0; x < width; x++) {
                while (lowerColumn + 1 < grid.getColumns() - 1
                        && x > grid.getPixelX(lowerColumn + 1)) {
                    lowerColumn++;
                }
                final int upperColumn = Math.min(lowerColumn + 1, grid.getColumns() - 1);
                final int x0 = grid.getPixelX(lowerColumn);
                final int x1 = grid.getPixelX(upperColumn);
                final double horizontal = x1 == x0 ? 0 : (x - x0) / (double) (x1 - x0);
                final int topOffset = lowerRow * grid.getColumns();
                final int bottomOffset = upperRow * grid.getColumns();
                targetPixels[targetOffset + x] = interpolateColor(
                        sampledPixels[topOffset + lowerColumn],
                        sampledPixels[topOffset + upperColumn],
                        sampledPixels[bottomOffset + lowerColumn],
                        sampledPixels[bottomOffset + upperColumn],
                        horizontal, vertical);
            }
        }
    }

    private static int interpolateColor(final int topLeft, final int topRight,
            final int bottomLeft, final int bottomRight,
            final double horizontal, final double vertical) {
        final int red = bilinearChannel(topLeft >>> 16, topRight >>> 16,
                bottomLeft >>> 16, bottomRight >>> 16, horizontal, vertical);
        final int green = bilinearChannel(topLeft >>> 8, topRight >>> 8,
                bottomLeft >>> 8, bottomRight >>> 8, horizontal, vertical);
        final int blue = bilinearChannel(topLeft, topRight, bottomLeft, bottomRight,
                horizontal, vertical);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int bilinearChannel(final int topLeft, final int topRight,
            final int bottomLeft, final int bottomRight,
            final double horizontal, final double vertical) {
        final double top = (topLeft & 0xFF) * (1 - horizontal)
                + (topRight & 0xFF) * horizontal;
        final double bottom = (bottomLeft & 0xFF) * (1 - horizontal)
                + (bottomRight & 0xFF) * horizontal;
        return Math.clamp((int) Math.round(top * (1 - vertical) + bottom * vertical), 0, 255);
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
            final RenderPackage completed, final CancellationToken token) {
        graphics.setStroke(new BasicStroke(request.quality() == RenderQuality.FULL ? 1.25f : 1f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int index = 0; index < completed.levelCount(); index++) {
            token.throwIfCancelled();
            graphics.setColor(completed.levelColor(index));
            graphics.draw(completed.contours().path(index, request.snapshot().viewport(),
                    request.width(), request.height(), token));
        }
    }

    private static void drawFoci(final Graphics2D graphics, final RenderRequest request) {
        final PlotSnapshot snapshot = request.snapshot();
        for (int index = 0; index < snapshot.foci().size(); index++) {
            final Focus focus = snapshot.foci().get(index);
            drawMarker(graphics, snapshot.viewport(), request.width(), request.height(),
                    focus.x(), focus.y(),
                    index == snapshot.selectedFocusIndex() ? SELECTED_FOCUS_COLOR : FOCUS_COLOR,
                    index == snapshot.selectedFocusIndex() ? 10 : 8);
        }
    }

    private static void drawMarker(final Graphics2D graphics, final Viewport viewport,
            final int width, final int height, final double worldX, final double worldY,
            final Color color, final int diameter) {
        final double x = viewport.pixelX(worldX, width);
        final double y = viewport.pixelY(worldY, height);
        final double radius = diameter / 2.0;
        if (!markerIntersectsCanvas(x, y, radius, width, height)) {
            return;
        }
        final int left = (int) Math.round(x - radius);
        final int top = (int) Math.round(y - radius);
        graphics.setColor(color);
        graphics.fillOval(left, top, diameter, diameter);
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawOval(left, top, diameter, diameter);
    }

    static boolean markerIntersectsCanvas(final double x, final double y,
            final double radius, final int width, final int height) {
        return Double.isFinite(x) && Double.isFinite(y)
                && x >= -radius && x <= width - 1.0 + radius
                && y >= -radius && y <= height - 1.0 + radius;
    }

    static double[] levels(final double min, final double max, final int count,
            final boolean logarithmic) {
        final int safeCount = Math.max(1, count);
        if (safeCount == 1 || min == max) {
            return new double[]{min};
        }
        final double[] generated = new double[safeCount];
        final boolean useLog = logarithmic && min > 0 && max > 0;
        final double logMin = useLog ? Math.log(min) : 0;
        final double logMax = useLog ? Math.log(max) : 0;
        int uniqueCount = 0;
        for (int index = 0; index < safeCount; index++) {
            final double level;
            if (index == 0) {
                level = min;
            } else if (index == safeCount - 1) {
                level = max;
            } else {
                final double fraction = index / (double) (safeCount - 1);
                level = useLog ? Math.exp(logMin + (logMax - logMin) * fraction)
                        : ScalarRanges.interpolate(min, max, fraction);
            }
            if (uniqueCount == 0
                    || Double.doubleToLongBits(level)
                            != Double.doubleToLongBits(generated[uniqueCount - 1])) {
                generated[uniqueCount++] = level;
            }
        }
        return uniqueCount == generated.length ? generated : Arrays.copyOf(generated, uniqueCount);
    }

    static Color curveColor(final int index, final int count) {
        if (count <= 1) {
            return Color.BLACK;
        }
        final float hue = (float) (0.66 * (1.0 - index / (double) (count - 1)));
        return Color.getHSBColor(hue, 0.85f, 0.72f);
    }

    /** Level indices shown in the legend: every level up to the cap, then an
     *  even subsample that always keeps both endpoints. */
    static int[] legendLevelIndices(final int levelCount) {
        return legendLevelIndices(levelCount, LEGEND_MAX_ROWS);
    }

    static int[] legendLevelIndices(final int levelCount, final int maximumRows) {
        final int rows = Math.min(levelCount, Math.clamp(maximumRows, 0, LEGEND_MAX_ROWS));
        if (rows <= 0) {
            return new int[0];
        }
        final int[] indices = new int[rows];
        if (rows == 1) {
            indices[0] = levelCount - 1;
            return indices;
        }
        for (int row = 0; row < rows; row++) {
            indices[row] = (int) Math.round(row * (levelCount - 1.0) / (rows - 1.0));
        }
        return indices;
    }

    static String formatLevel(final double level) {
        return String.format(Locale.ROOT, "%.4g", level);
    }

    private static void drawLegend(final Graphics2D graphics, final RenderPackage completed) {
        final FontMetrics metrics = graphics.getFontMetrics();
        final int rowHeight = metrics.getHeight() + 2;
        final int availableRows = Math.max(0,
                (completed.height() - 2 * LEGEND_MARGIN - 2 * LEGEND_PADDING + 2) / rowHeight);
        final int[] indices = legendLevelIndices(completed.levelCount(), availableRows);
        if (indices.length == 0) {
            return;
        }
        int textWidth = 0;
        for (final int index : indices) {
            textWidth = Math.max(textWidth,
                    metrics.stringWidth(formatLevel(completed.level(index))));
        }
        final int boxWidth = LEGEND_PADDING * 2 + LEGEND_SWATCH_WIDTH + LEGEND_GAP + textWidth;
        final int boxHeight = LEGEND_PADDING * 2 + rowHeight * indices.length - 2;
        if (boxWidth > completed.width() - 2 * LEGEND_MARGIN
                || boxHeight > completed.height() - 2 * LEGEND_MARGIN) {
            return;
        }
        final int left = completed.width() - boxWidth - LEGEND_MARGIN;
        final int top = LEGEND_MARGIN;

        graphics.setColor(LEGEND_BACKGROUND);
        graphics.fillRoundRect(left, top, boxWidth, boxHeight, 8, 8);
        graphics.setColor(AXIS_COLOR);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRoundRect(left, top, boxWidth, boxHeight, 8, 8);

        final int swatchLeft = left + LEGEND_PADDING;
        final int textLeft = swatchLeft + LEGEND_SWATCH_WIDTH + LEGEND_GAP;
        graphics.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Highest level on top, matching how a colour bar is usually read.
        for (int row = 0; row < indices.length; row++) {
            final int levelIndex = indices[indices.length - 1 - row];
            final int rowTop = top + LEGEND_PADDING + row * rowHeight;
            final int swatchY = rowTop + rowHeight / 2 - 1;
            graphics.setColor(completed.levelColor(levelIndex));
            graphics.drawLine(swatchLeft, swatchY, swatchLeft + LEGEND_SWATCH_WIDTH, swatchY);
            graphics.setColor(Color.BLACK);
            graphics.drawString(formatLevel(completed.level(levelIndex)), textLeft,
                    rowTop + metrics.getAscent());
        }
    }

    long getCacheHits() {
        return cacheHits.get();
    }

    long getCacheMisses() {
        return cacheMisses.get();
    }

    long getDerivedGridHits() {
        return derivedGridHits.get();
    }

    long getContourCacheHits() {
        return contourCacheHits.get();
    }

    long getContourCacheMisses() {
        return contourCacheMisses.get();
    }

    long getWorldTileHits() {
        return worldFieldCache.tileHits();
    }

    long getWorldTileMisses() {
        return worldFieldCache.tileMisses();
    }

    long getReusedWorldSamples() {
        return worldFieldCache.reusedSamples();
    }

    long getSampledWorldValues() {
        return worldFieldCache.sampledValues();
    }

    long getLayerCacheHits() {
        return layerCacheHits.get();
    }

    long getLayerCacheMisses() {
        return layerCacheMisses.get();
    }

    long getFullQualityPreviewHits() {
        return fullQualityPreviewHits.get();
    }

    long getCachedGridBytes() {
        synchronized (gridCache) {
            return cachedGridBytes;
        }
    }

    long getCachedContourBytes() {
        synchronized (contourCache) {
            return cachedContourBytes;
        }
    }

    long getCachedWorldTileBytes() {
        return worldFieldCache.cachedBytes();
    }

    long getCachedLayerBytes() {
        synchronized (layerCache) {
            return cachedLayerBytes;
        }
    }

    /** Exact evaluations spent by this renderer's passes; non-zero means an
     *  ill-conditioned field, and a budgeted value explains a slower render. */
    long getExactEvaluations() {
        return exactEvaluations.get();
    }

    public String cacheSummary() {
        final long exact = getExactEvaluations();
        return String.format(Locale.ROOT,
                "tiles %d/%d (%d reused), grid %d/%d (+%d derived), "
                        + "contour %d/%d, layer %d/%d (+%d full-preview), %.1f/%.1f MiB%s",
                getWorldTileHits(), getWorldTileMisses(), getReusedWorldSamples(),
                getCacheHits(), getCacheMisses(), getDerivedGridHits(),
                getContourCacheHits(), getContourCacheMisses(),
                getLayerCacheHits(), getLayerCacheMisses(), getFullQualityPreviewHits(),
                (getCachedWorldTileBytes() + getCachedGridBytes()
                        + getCachedContourBytes() + getCachedLayerBytes()) / (double) MEBIBYTE,
                cacheBudgetBytes / (double) MEBIBYTE,
                exact == 0 ? "" : ", " + exact + " exact");
    }

    private record ContourKey(FieldKey fieldKey, List<Long> levelBits) {
        ContourKey {
            levelBits = List.copyOf(levelBits);
        }

        static ContourKey from(final RenderRequest request, final double[] levels) {
            return from(FieldKey.from(request), levels);
        }

        static ContourKey from(final FieldKey fieldKey, final double[] levels) {
            final List<Long> bits = new ArrayList<>(levels.length);
            for (final double level : levels) {
                bits.add(Double.doubleToLongBits(level));
            }
            return new ContourKey(fieldKey, bits);
        }
    }

    private record LayerKey(
            FieldKey fieldKey,
            double distanceMin,
            double distanceMax,
            int curveCount,
            boolean logSpacing,
            boolean showBackground,
            boolean antiAlias,
            RenderQuality quality) {

        static LayerKey from(final RenderRequest request) {
            final PlotSnapshot snapshot = request.snapshot();
            return new LayerKey(FieldKey.from(request), snapshot.distanceMin(),
                    snapshot.distanceMax(), snapshot.curveCount(), snapshot.logSpacing(),
                    snapshot.showBackground(), snapshot.antiAlias(), request.quality());
        }

        LayerKey asFullQuality() {
            return new LayerKey(fieldKey.withSampleStep(1), distanceMin, distanceMax,
                    curveCount, logSpacing, showBackground, antiAlias, RenderQuality.FULL);
        }
    }

    private record RenderArtifacts(FieldGrid grid, BufferedImage layer,
            ContourGeometry contours, double[] levels) {
        RenderArtifacts {
            levels = levels.clone();
        }

        @Override
        public double[] levels() {
            return levels.clone();
        }
    }

}
