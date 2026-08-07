package nlipse.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import nlipse.geometry.Point2;
import nlipse.math.DistanceField;
import nlipse.math.DistanceFields;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** CPU renderer that shares and caches one scalar-field grid per geometry/view. */
public final class PlotRenderer implements RenderEngine {
    private static final int CACHE_SIZE = 4;
    private static final Color BACKGROUND_TARGET = new Color(0, 100, 0);
    private static final Color AXIS_COLOR = new Color(125, 125, 125);
    private static final Color FOCUS_COLOR = new Color(35, 90, 210);
    private static final Color SELECTED_FOCUS_COLOR = new Color(245, 145, 20);
    private static final Color MIN_COLOR = new Color(210, 45, 45);
    private static final Color MAX_COLOR = new Color(15, 175, 190);

    private final Map<FieldKey, FieldGrid> gridCache = new LinkedHashMap<FieldKey, FieldGrid>(
            CACHE_SIZE, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(final Map.Entry<FieldKey, FieldGrid> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

    @Override
    public RenderResult render(final RenderRequest request, final CancellationToken token) {
        if (request == null || token == null) {
            throw new IllegalArgumentException("Render request and cancellation token are required");
        }
        final long started = System.nanoTime();
        token.throwIfCancelled();

        final PlotSnapshot snapshot = request.getSnapshot();
        final DistanceField field = DistanceFields.create(snapshot.getCurveType(), snapshot.getFoci());
        final FieldGrid grid = getGrid(request, field, token);
        token.throwIfCancelled();

        final BufferedImage image = new BufferedImage(request.getWidth(), request.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, request.getWidth(), request.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    snapshot.isAntiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON
                            : RenderingHints.VALUE_ANTIALIAS_OFF);

            if (snapshot.isShowBackground()) {
                drawBackground(graphics, grid, token);
            }
            drawAxes(graphics, snapshot.getViewport(), request.getWidth(), request.getHeight());
            drawContours(graphics, request, grid, field, token);
            drawFoci(graphics, request);
            if (snapshot.isShowExtrema()) {
                drawMarker(graphics, snapshot.getViewport(), request.getWidth(), request.getHeight(),
                        grid.getMinPoint(), MIN_COLOR, 7);
                drawMarker(graphics, snapshot.getViewport(), request.getWidth(), request.getHeight(),
                        grid.getMaxPoint(), MAX_COLOR, 7);
            }
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawRect(0, 0, request.getWidth() - 1, request.getHeight() - 1);
        } finally {
            graphics.dispose();
        }

        token.throwIfCancelled();
        return new RenderResult(image, request.getSequence(), request.getQuality(),
                grid.getMinValue(), grid.getMaxValue(), grid.getMinPoint(), grid.getMaxPoint(),
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
        final FieldGrid sampled = FieldGrid.sample(field, request.getSnapshot().getViewport(),
                request.getWidth(), request.getHeight(), request.getQuality().getSampleStep(), token);
        token.throwIfCancelled();
        synchronized (gridCache) {
            gridCache.put(key, sampled);
        }
        return sampled;
    }

    private static void drawBackground(final Graphics2D graphics, final FieldGrid grid,
            final CancellationToken token) {
        final BufferedImage sampled = new BufferedImage(grid.getColumns(), grid.getRows(),
                BufferedImage.TYPE_INT_RGB);
        final double min = grid.getMinValue();
        final double range = grid.getMaxValue() - min;
        for (int row = 0; row < grid.getRows(); row++) {
            if ((row & 15) == 0) {
                token.throwIfCancelled();
            }
            for (int column = 0; column < grid.getColumns(); column++) {
                final double value = grid.getValue(column, row);
                final double normalized = range > 0 && Double.isFinite(value)
                        ? clamp01((value - min) / range) : 0;
                sampled.setRGB(column, row, blend(Color.WHITE, BACKGROUND_TARGET, normalized).getRGB());
            }
        }
        final Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(sampled, 0, 0, grid.getPixelWidth(), grid.getPixelHeight(), null);
        if (oldInterpolation != null) {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private static void drawAxes(final Graphics2D graphics, final Viewport viewport,
            final int width, final int height) {
        graphics.setColor(AXIS_COLOR);
        graphics.setStroke(new BasicStroke(1f));
        if (viewport.getYMin() <= 0 && viewport.getYMax() >= 0) {
            final double y = viewport.pixelY(0, height);
            graphics.drawLine(0, (int) Math.round(y), width - 1, (int) Math.round(y));
        }
        if (viewport.getXMin() <= 0 && viewport.getXMax() >= 0) {
            final double x = viewport.pixelX(0, width);
            graphics.drawLine((int) Math.round(x), 0, (int) Math.round(x), height - 1);
        }
    }

    private static void drawContours(final Graphics2D graphics, final RenderRequest request,
            final FieldGrid grid, final DistanceField field, final CancellationToken token) {
        final PlotSnapshot snapshot = request.getSnapshot();
        final double[] levels = levels(snapshot.getDistanceMin(), snapshot.getDistanceMax(),
                snapshot.getCurveCount(), snapshot.isLogSpacing());
        graphics.setStroke(new BasicStroke(request.getQuality() == RenderQuality.FULL ? 1.25f : 1f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int index = 0; index < levels.length; index++) {
            token.throwIfCancelled();
            graphics.setColor(curveColor(index, levels.length));
            final Path2D.Double path = new Path2D.Double();
            MarchingSquares.trace(grid, field, snapshot.getViewport(), levels[index], token,
                    (x1, y1, x2, y2) -> {
                        path.moveTo(x1, y1);
                        path.lineTo(x2, y2);
                    });
            graphics.draw(path);
        }
    }

    private static void drawFoci(final Graphics2D graphics, final RenderRequest request) {
        final PlotSnapshot snapshot = request.getSnapshot();
        for (int index = 0; index < snapshot.getFoci().size(); index++) {
            final Focus focus = snapshot.getFoci().get(index);
            final Point2 point = new Point2(focus.getX(), focus.getY());
            drawMarker(graphics, snapshot.getViewport(), request.getWidth(), request.getHeight(), point,
                    index == snapshot.getSelectedFocusIndex() ? SELECTED_FOCUS_COLOR : FOCUS_COLOR,
                    index == snapshot.getSelectedFocusIndex() ? 10 : 8);
        }
    }

    private static void drawMarker(final Graphics2D graphics, final Viewport viewport,
            final int width, final int height, final Point2 point, final Color color, final int diameter) {
        final double x = viewport.pixelX(point.getX(), width);
        final double y = viewport.pixelY(point.getY(), height);
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
        final double[] levels = new double[safeCount];
        if (safeCount == 1) {
            levels[0] = min;
            return levels;
        }
        final boolean useLog = logarithmic && min > 0 && max > 0;
        final double logMin = useLog ? Math.log(min) : 0;
        final double logMax = useLog ? Math.log(max) : 0;
        for (int index = 0; index < safeCount; index++) {
            final double fraction = index / (double) (safeCount - 1);
            levels[index] = useLog ? Math.exp(logMin + (logMax - logMin) * fraction)
                    : min + (max - min) * fraction;
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

    private static Color blend(final Color from, final Color to, final double fraction) {
        final double t = clamp01(fraction);
        final int red = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        final int green = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        final int blue = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        return new Color(red, green, blue);
    }

    private static double clamp01(final double value) {
        return Math.max(0, Math.min(1, value));
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public String cacheSummary() {
        return String.format(Locale.ROOT, "%d hits / %d misses", getCacheHits(), getCacheMisses());
    }
}
