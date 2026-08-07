/**
 * @author CGH
 */

package plotPane;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

public abstract class PlotCurve {
	protected static final Color DEFAULT_BACKGROUND_COLOR = new Color(0, 100, 0);
	protected static final Color DEFAULT_MAIN_COLOR = Color.BLACK;
	private static final double PIXEL_HALF_SIZE = 0.5;
	private static final int PALETTE_SIZE = 256;

	protected Color backgroundColor = DEFAULT_BACKGROUND_COLOR;
	protected boolean backgroundOn;
	protected Color mainColor = DEFAULT_MAIN_COLOR;

	public void drawMe(final Graphics2D g, final PlotPane p) {
		final int minX = -1;
		final int maxX = p.getXres();
		final int minY = -1;
		final int maxY = p.getYres();
		final int width = maxX - minX + 1;
		final int height = maxY - minY + 1;

		final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		final int rgb = mainColor.getRGB();

		IntStream.rangeClosed(minX, maxX).parallel().forEach(x -> {
			for (int y = minY; y <= maxY; y++) {
				if (pointIsToBeDrawn(x, y, p))
					pixels[(y - minY) * width + (x - minX)] = rgb;
			}
		});

		g.drawImage(img, minX, minY, null);
	}

	public void drawMeBkgrd(final Graphics2D g, final PlotPane p) {
		if (!backgroundOn)
			return;

		final int minX = -1;
		final int maxX = p.getXres();
		final int minY = -1;
		final int maxY = p.getYres();
		final int width = maxX - minX + 1;
		final int height = maxY - minY + 1;

		final double[] valueRange = getLocalFColorRange(p);
		final double min = valueRange[0];
		final double max = valueRange[1];
		final double range = max - min;

		final Color oldBackground = g.getBackground();
		final Color newBackground = backgroundColor;
		final int oldR = oldBackground.getRed();
		final int oldG = oldBackground.getGreen();
		final int oldB = oldBackground.getBlue();
		final int newR = newBackground.getRed();
		final int newG = newBackground.getGreen();
		final int newB = newBackground.getBlue();

		final int[] palette = new int[PALETTE_SIZE];
		for (int k = 0; k < PALETTE_SIZE; k++) {
			final double t = (double) k / (PALETTE_SIZE - 1);
			final int r = (int) Math.round(newR * t + oldR * (1 - t));
			final int green = (int) Math.round(newG * t + oldG * (1 - t));
			final int b = (int) Math.round(newB * t + oldB * (1 - t));
			palette[k] = 0xFF000000 | (r << 16) | (green << 8) | b;
		}

		final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

		IntStream.rangeClosed(minX, maxX).parallel().forEach(x -> {
			final double worldX = p.unfitx(x);
			for (int y = minY; y <= maxY; y++) {
				final double value = fColor(worldX, p.unfity(y));
				final double normalized = range > 0 && Double.isFinite(value) ? (value - min) / range : 0;
				int paletteIndex = (int) Math.round(normalized * (PALETTE_SIZE - 1));
				if (paletteIndex < 0)
					paletteIndex = 0;
				else if (paletteIndex >= PALETTE_SIZE)
					paletteIndex = PALETTE_SIZE - 1;
				pixels[(y - minY) * width + (x - minX)] = palette[paletteIndex];
			}
		});

		g.drawImage(img, minX, minY, null);
	}

	public final double f(final double x, final double y) {
		return fLeft(x, y) - fRight(x, y);
	}

	public abstract double fColor(double x, double y);

	public abstract double fLeft(double x, double y);

	public abstract double fRight(double x, double y);

	public abstract double getLocalFColorMax(PlotPane p);

	public abstract double getLocalFColorMin(PlotPane p);

	public double[] getLocalFColorRange(final PlotPane p) {
		return new double[]{getLocalFColorMin(p), getLocalFColorMax(p)};
	}

	public final boolean isBkgrdOn() {
		return backgroundOn;
	}

	private boolean pointIsToBeDrawn(final int x, final int y, final PlotPane p) {
		final double x0 = p.unfitx(x - PIXEL_HALF_SIZE);
		final double x1 = p.unfitx(x + PIXEL_HALF_SIZE);
		final double y0 = p.unfity(y - PIXEL_HALF_SIZE);
		final double y1 = p.unfity(y + PIXEL_HALF_SIZE);
		return cellContainsZeroContour(f(x0, y0), f(x1, y0), f(x0, y1), f(x1, y1));
	}

	static boolean cellContainsZeroContour(final double v00, final double v10, final double v01, final double v11) {
		if (!Double.isFinite(v00) || !Double.isFinite(v10) || !Double.isFinite(v01) || !Double.isFinite(v11))
			return false;

		if (v00 == 0 || v10 == 0 || v01 == 0 || v11 == 0)
			return true;
		final boolean hasPositive = v00 > 0 || v10 > 0 || v01 > 0 || v11 > 0;
		final boolean hasNegative = v00 < 0 || v10 < 0 || v01 < 0 || v11 < 0;
		return hasPositive && hasNegative;
	}

	public final void setBkgrdOn(final boolean backgroundOn) {
		this.backgroundOn = backgroundOn;
	}
}
