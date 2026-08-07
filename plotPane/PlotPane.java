/**
 * @author CGH
 */

package plotPane;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import simpleGeom.Line;
import simpleGeom.Point;

public final class PlotPane extends JPanel {
	private static final long serialVersionUID = -4073631369165565550L;
	private static final int MARGIN = 20;
	private static final Color AXIS_COLOR = Color.GRAY;
	private static final Color BACKGROUND_COLOR = Color.WHITE;
	private static final Stroke SHAPE_STROKE = new BasicStroke();

	private final class DrawPanel extends JPanel {
		private static final long serialVersionUID = 2015820816414651161L;

		@Override
		protected void paintComponent(final Graphics graphics) {
			super.paintComponent(graphics);
			final Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setBackground(getBackground());
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

				for (final PlotCurve curve : plots)
					curve.drawMeBkgrd(g, PlotPane.this);

				xAxis.drawMe(g, PlotPane.this);
				yAxis.drawMe(g, PlotPane.this);

				for (final PlotCurve curve : plots)
					curve.drawMe(g, PlotPane.this);
				for (final ShapeX point : points)
					point.drawMe(g, PlotPane.this);

				g.setColor(Color.BLACK);
				g.draw(border);
			} finally {
				g.dispose();
			}
		}
	}

	private boolean antiAlias = true;
	private Shape border;
	private final List<PlotCurve> plots = new ArrayList<>();
	private final List<ShapeX> points = new ArrayList<>();
	private final DrawPanel drawPanel;
	private ShapeX xAxis;
	private ShapeX yAxis;
	private double xmin;
	private double xmax;
	private double ymin;
	private double ymax;
	private final int xres;
	private final int yres;

	public PlotPane(final double xmin, final double xmax, final double ymin, final double ymax, final int xres, final int yres) {
		validateDimensions(xmin, xmax, ymin, ymax, xres, yres);
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
		this.xres = xres;
		this.yres = yres;

		drawPanel = new DrawPanel();
		drawPanel.setBounds(MARGIN, MARGIN, xres, yres);
		drawPanel.setBackground(BACKGROUND_COLOR);

		refreshBorderAndAxes();

		setLayout(null);
		setPreferredSize(new Dimension(xres + 2 * MARGIN, yres + 2 * MARGIN));
		add(drawPanel);
	}

	public int addPlot(final PlotCurve plot, final Color color) {
		if (plot == null || color == null)
			throw new IllegalArgumentException("Plot and colour must not be null");
		plot.mainColor = color;
		plots.add(plot);
		return plots.size();
	}

	public int addPoint(final Point point, final Color color) {
		if (point == null || color == null)
			throw new IllegalArgumentException("Point and colour must not be null");
		points.add(new ShapeX(new Line(point, point), ShapeX.TYPE_POINT, color, SHAPE_STROKE));
		return points.size();
	}

	public JPanel getDrawPanel() {
		return drawPanel;
	}

	public void clearPlots() {
		plots.clear();
	}

	public void clearPoints() {
		points.clear();
	}

	public Line fit(final Line line) {
		if (line == null)
			throw new IllegalArgumentException("Line must not be null");
		return new Line(fit(line.getP1()), fit(line.getP2()));
	}

	public Point fit(final Point point) {
		if (point == null)
			throw new IllegalArgumentException("Point must not be null");
		return new Point(fitx(point.getX()), fity(point.getY()));
	}

	public double fitx(final double x) {
		return (xres - 1) * (x - xmin) / (xmax - xmin);
	}

	public double fity(final double y) {
		return (yres - 1) * (1 - (y - ymin) / (ymax - ymin));
	}

	public double getXmax() {
		return xmax;
	}

	public double getXmin() {
		return xmin;
	}

	public int getXres() {
		return xres;
	}

	public double getYmax() {
		return ymax;
	}

	public double getYmin() {
		return ymin;
	}

	public int getYres() {
		return yres;
	}

	public void refresh() {
		refreshBorderAndAxes();
		repaint();
	}

	private void refreshBorderAndAxes() {
		border = new Rectangle2D.Double(0, 0, xres - 1, yres - 1);
		xAxis = new ShapeX(new Line(xmin, 0, xmax, 0), ShapeX.TYPE_LINE, AXIS_COLOR);
		yAxis = new ShapeX(new Line(0, ymin, 0, ymax), ShapeX.TYPE_LINE, AXIS_COLOR);
	}

	public void setAntiAlias(final boolean antiAlias) {
		this.antiAlias = antiAlias;
		repaint();
	}

	public void setDim(final double xmin, final double xmax, final double ymin, final double ymax) {
		validateDimensions(xmin, xmax, ymin, ymax, xres, yres);
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
		refresh();
	}

	public double unfitx(final double x) {
		return x / (xres - 1) * (xmax - xmin) + xmin;
	}

	public double unfity(final double y) {
		return (1 - y / (yres - 1)) * (ymax - ymin) + ymin;
	}

	private static void validateDimensions(final double xmin, final double xmax, final double ymin, final double ymax,
			final int xres, final int yres) {
		if (!Double.isFinite(xmin) || !Double.isFinite(xmax) || !Double.isFinite(ymin) || !Double.isFinite(ymax))
			throw new IllegalArgumentException("Plot bounds must be finite");
		if (xmin >= xmax || ymin >= ymax)
			throw new IllegalArgumentException("Plot bounds must have min < max");
		if (xres < 2 || yres < 2)
			throw new IllegalArgumentException("Plot resolution must be at least 2 by 2");
	}
}
