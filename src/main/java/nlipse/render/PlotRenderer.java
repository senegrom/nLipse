package nlipse.render;

import java.awt.BasicStroke;
import java.awt.Color;
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
import nlipse.geometry.Point2;
import nlipse.math.DistanceField;
import nlipse.math.DistanceFields;
import nlipse.math.ScalarRanges;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** CPU renderer with bounded field and marker-free layer caches. */
public final class PlotRenderer implements RenderEngine {
    private static final long MEBIBYTE = 1024L * 1024;
    private static final int PALETTE_SIZE = 256;
    private static final Color BACKGROUND_TARGET = new Color(0, 100, 0);
    private static final Color AXIS_COLOR = new Color(125, 125, 125);
    private static final Color FOCUS_COLOR = new Color(35, 90, 210);
    private static final Color SELECTED_FOCUS_COLOR = new Color(245, 145, 20);
    private static final Color MIN_COLOR = new Color(210, 45, 45);
    private static final Color MAX_COLOR = new Color(15, 175, 190);
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
        final BufferedImage staticLayer;
        if (cachedFull != null) {
            grid = cachedFull.grid();
            staticLayer = cachedFull.layer();
        } else {
            final DistanceField field = DistanceFields.create(snapshot.curveType(), snapshot.foci(),
                    snapshot.familyParameter());
            grid = getGrid(request, field, token);
            staticLayer = getStaticLayer(request, grid, field, token);
        }
        token.throwIfCancelled();

        final BufferedImage image = copyImage(staticLayer);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    snapshot.antiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON
                            : RenderingHints.VALUE_ANTIALIAS_OFF);
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

    private RenderArtifacts fullQualityArtifacts(final RenderRequest request) {
        final FieldKey fullFieldKey = FieldKey.from(request).withSampleStep(1);
        final FieldGrid grid;
        synchronized (gridCache) {
            grid = gridCache.get(fullFieldKey);
        }
        if (grid == null) {
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
        layerCacheHits.incrementAndGet();
        fullQualityPreviewHits.incrementAndGet();
        return new RenderArtifacts(grid, layer);
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
        final FieldGrid sampled = key.sampleStep() == 1
                ? worldFieldCache.sample(key.identity(), field, request.snapshot().viewport(),
                        request.width(), request.height(), token)
                : FieldGrid.sample(field, request.snapshot().viewport(),
                        request.width(), request.height(), key.sampleStep(), token);
        token.throwIfCancelled();
        return cacheGrid(key, sampled);
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
            final DistanceField field, final CancellationToken token) {
        final LayerKey key = LayerKey.from(request);
        synchronized (layerCache) {
            final BufferedImage cached = layerCache.get(key);
            if (cached != null) {
                layerCacheHits.incrementAndGet();
                return cached;
            }
        }

        layerCacheMisses.incrementAndGet();
        final BufferedImage rendered = renderStaticLayer(request, grid, field, token);
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
            final FieldGrid grid, final DistanceField field, final CancellationToken token) {
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
            final double[] levels = levels(snapshot.distanceMin(), snapshot.distanceMax(),
                    snapshot.curveCount(), snapshot.logSpacing());
            final ContourGeometry contours = getContourGeometry(request, grid, field, levels, token);
            drawContours(graphics, request, contours, token);
        } finally {
            graphics.dispose();
        }
        token.throwIfCancelled();
        return image;
    }

    private ContourGeometry getContourGeometry(final RenderRequest request,
            final FieldGrid grid, final DistanceField field, final double[] levels,
            final CancellationToken token) {
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
            final ContourGeometry contours, final CancellationToken token) {
        graphics.setStroke(new BasicStroke(request.quality() == RenderQuality.FULL ? 1.25f : 1f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int index = 0; index < contours.levelCount(); index++) {
            token.throwIfCancelled();
            graphics.setColor(curveColor(index, contours.levelCount()));
            graphics.draw(contours.path(index, request.snapshot().viewport(),
                    request.width(), request.height(), token));
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
        final double[] generated = new double[safeCount];
        final boolean useLog = logarithmic && min > 0 && max > 0;
        final double logMin = useLog ? Math.log(min) : 0;
        final double logMax = useLog ? Math.log(max) : 0;
        int uniqueCount = 0;
        for (int index = 0; index < safeCount; index++) {
            final double fraction = index / (double) (safeCount - 1);
            final double level = useLog ? Math.exp(logMin + (logMax - logMin) * fraction)
                    : ScalarRanges.interpolate(min, max, fraction);
            if (uniqueCount == 0
                    || Double.doubleToLongBits(level)
                            != Double.doubleToLongBits(generated[uniqueCount - 1])) {
                generated[uniqueCount++] = level;
            }
        }
        return uniqueCount == generated.length ? generated : Arrays.copyOf(generated, uniqueCount);
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

    public long getDerivedGridHits() {
        return derivedGridHits.get();
    }

    public long getContourCacheHits() {
        return contourCacheHits.get();
    }

    public long getContourCacheMisses() {
        return contourCacheMisses.get();
    }

    public long getWorldTileHits() {
        return worldFieldCache.tileHits();
    }

    public long getWorldTileMisses() {
        return worldFieldCache.tileMisses();
    }

    public long getReusedWorldSamples() {
        return worldFieldCache.reusedSamples();
    }

    public long getSampledWorldValues() {
        return worldFieldCache.sampledValues();
    }

    public long getLayerCacheHits() {
        return layerCacheHits.get();
    }

    public long getLayerCacheMisses() {
        return layerCacheMisses.get();
    }

    public long getFullQualityPreviewHits() {
        return fullQualityPreviewHits.get();
    }

    public long getCachedGridBytes() {
        synchronized (gridCache) {
            return cachedGridBytes;
        }
    }

    public long getCachedContourBytes() {
        synchronized (contourCache) {
            return cachedContourBytes;
        }
    }

    public long getCachedWorldTileBytes() {
        return worldFieldCache.cachedBytes();
    }

    public long getCachedLayerBytes() {
        synchronized (layerCache) {
            return cachedLayerBytes;
        }
    }

    public long getCacheBudgetBytes() {
        return cacheBudgetBytes;
    }

    public String cacheSummary() {
        return String.format(Locale.ROOT,
                "tiles %d/%d (%d reused), grid %d/%d (+%d derived), "
                        + "contour %d/%d, layer %d/%d (+%d full-preview), %.1f/%.1f MiB",
                getWorldTileHits(), getWorldTileMisses(), getReusedWorldSamples(),
                getCacheHits(), getCacheMisses(), getDerivedGridHits(),
                getContourCacheHits(), getContourCacheMisses(),
                getLayerCacheHits(), getLayerCacheMisses(), getFullQualityPreviewHits(),
                (getCachedWorldTileBytes() + getCachedGridBytes()
                        + getCachedContourBytes() + getCachedLayerBytes()) / (double) MEBIBYTE,
                cacheBudgetBytes / (double) MEBIBYTE);
    }

    private record ContourKey(FieldKey fieldKey, List<Long> levelBits) {
        ContourKey {
            levelBits = List.copyOf(levelBits);
        }

        static ContourKey from(final RenderRequest request, final double[] levels) {
            final List<Long> bits = new ArrayList<>(levels.length);
            for (final double level : levels) {
                bits.add(Double.doubleToLongBits(level));
            }
            return new ContourKey(FieldKey.from(request), bits);
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

    private record RenderArtifacts(FieldGrid grid, BufferedImage layer) {
    }

}
