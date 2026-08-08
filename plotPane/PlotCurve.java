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
	protected final static Color	bkgrdColorDef	= new Color(0, 100, 0);
	protected final static boolean	bkgrdOnDef		= false;
	private final static double		eps				= 0.5;
	protected final static Color	mainColorDef	= Color.BLACK;
	private final static int		PALETTE_SIZE	= 256;

	protected Color					bkgrdColor		= bkgrdColorDef;
	protected boolean				bkgrdOn			= bkgrdOnDef;
	protected Color					mainColor		= mainColorDef;

	public void drawMe(final Graphics2D g, final PlotPane p) {
		final int dminX = -1;
		final int dmaxX = p.getXres() + 1;
		final int dminY = -1;
		final int dmaxY = p.getYres() + 1;
		final int width = dmaxX - dminX + 1;
		final int height = dmaxY - dminY + 1;

		final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		final int rgb = mainColor.getRGB();

		IntStream.rangeClosed(dminX, dmaxX).parallel().forEach(x -> {
			final double[] xs = new double[2];
			final double[] ys = new double[2];
			for (int y = dminY; y <= dmaxY; y++)
				if (pointIsToBeDrawn(x, y, p, xs, ys))
					pixels[(y - dminY) * width + (x - dminX)] = rgb;
		});

		g.drawImage(img, dminX, dminY, null);
	}

	public void drawMeBkgrd(final Graphics2D g, final PlotPane p) {
		if (!bkgrdOn)
			return;
		final int dminX = -1;
		final int dmaxX = p.getXres() + 1;
		final int dminY = -1;
		final int dmaxY = p.getYres() + 1;
		final int width = dmaxX - dminX + 1;
		final int height = dmaxY - dminY + 1;

		final double max = getLocalFColorMax(p);
		final double min = getLocalFColorMin(p);
		final double range = max - min;

		final Color clrOldBack = g.getBackground();
		final Color clrNewBack = bkgrdColor;
		final int oldR = clrOldBack.getRed(), oldG = clrOldBack.getGreen(), oldB = clrOldBack.getBlue();
		final int newR = clrNewBack.getRed(), newG = clrNewBack.getGreen(), newB = clrNewBack.getBlue();

		final int[] paletteRGB = new int[PALETTE_SIZE];
		for (int k = 0; k < PALETTE_SIZE; k++) {
			final double t = (double) k / (PALETTE_SIZE - 1);
			final int r = (int) Math.round(newR * t + oldR * (1 - t));
			final int g0 = (int) Math.round(newG * t + oldG * (1 - t));
			final int b = (int) Math.round(newB * t + oldB * (1 - t));
			paletteRGB[k] = 0xFF000000 | (r << 16) | (g0 << 8) | b;
		}

		final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

		IntStream.rangeClosed(dminX, dmaxX).parallel().forEach(x -> {
			final double wx = p.unfitx(x);
			for (int y = dminY; y <= dmaxY; y++) {
				final double v = range == 0 ? 0 : (fColor(wx, p.unfity(y)) - min) / range;
				int k = (int) Math.round(v * (PALETTE_SIZE - 1));
				if (k < 0)
					k = 0;
				else if (k >= PALETTE_SIZE)
					k = PALETTE_SIZE - 1;
				pixels[(y - dminY) * width + (x - dminX)] = paletteRGB[k];
			}
		});

		g.drawImage(img, dminX, dminY, null);
	}

	public final double f(final double x, final double y) {
		return fLeft(x, y) - fRight(x, y);
	}

	public abstract double fColor(double x, double y);

	public abstract double fLeft(double x, double y);

	public abstract double fRight(double x, double y);

	public abstract double getLocalFColorMax(PlotPane p);

	public abstract double getLocalFColorMin(PlotPane p);

	private final boolean pointIsToBeDrawn(final int x, final int y, final PlotPane p, final double[] xs, final double[] ys) {
		xs[0] = p.unfitx(x - eps);
		xs[1] = p.unfitx(x + eps);
		ys[0] = p.unfity(y - eps);
		ys[1] = p.unfity(y + eps);

		if (Math.signum(f(xs[0], ys[0])) != Math.signum(f(xs[1], ys[1])))
			return true;
		return Math.signum(f(xs[0], ys[1])) != Math.signum(f(xs[1], ys[0]));
	}

	public final void setBkgrdOn(final boolean bkgrdOn) {
		this.bkgrdOn = bkgrdOn;
	}
}
