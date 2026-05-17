/**
 * @author CGH
 */

package plotPane;

import java.awt.BasicStroke;
import java.awt.Color;
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

public class PlotPane extends JPanel {
	private static final long		serialVersionUID	= -4073631369165565550L;

	private class JPanelDraw extends JPanel {
		private static final long	serialVersionUID	= 2015820816414651161L;

		private final PlotPane		fatherPlotPane;

		JPanelDraw(final PlotPane fatherPlotPane) {
			this.fatherPlotPane = fatherPlotPane;
		}

		@Override
		protected final void paintComponent(final Graphics gX) {
			super.paintComponent(gX);
			final Graphics2D g = (Graphics2D) gX;

			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

			for (final PlotCurve pc : plots)
				pc.drawMeBkgrd(g, fatherPlotPane);

			xAxis.drawMe(g, fatherPlotPane);
			yAxis.drawMe(g, fatherPlotPane);

			g.setColor(Color.BLACK);
			g.draw(border);

			for (final ShapeX pt : points)
				pt.drawMe(g, fatherPlotPane);
			for (final PlotCurve pc : plots)
				pc.drawMe(g, fatherPlotPane);
		}
	}

	private final static Color	colAxis			= Color.GRAY;
	private final static Color	colBackgrd		= Color.WHITE;
	private final static Stroke	mainStroke		= new BasicStroke();

	private boolean					antiAlias			= true;
	private Shape					border;
	private final List<PlotCurve>	plots				= new ArrayList<>();
	private final List<ShapeX>		points				= new ArrayList<>();
	private final JPanelDraw		pnlDraw;
	private ShapeX					xAxis, yAxis;
	private double					xmin, xmax, ymin, ymax;
	private final int				xres, yres;

	public PlotPane(final double xmin, final double xmax, final double ymin, final double ymax, final int xres, final int yres) {
		super();
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
		this.xres = xres;
		this.yres = yres;

		pnlDraw = new JPanelDraw(this);
		pnlDraw.setSize(xres, yres);
		pnlDraw.setBackground(colBackgrd);

		refreshBorderAndAxes();

		setLayout(null);
		add(pnlDraw);
		pnlDraw.setLocation(20, 20);
	}

	public int addPlot(final PlotCurve p, final Color c) {
		p.mainColor = c;
		p.mainStroke = mainStroke;
		plots.add(p);
		return plots.size();
	}

	public int addPoint(final Point p, final Color c) {
		points.add(new ShapeX(new Line(p, p), ShapeX.TYPE_POINT, c, mainStroke));
		return points.size();
	}

	public JPanel getDrawPanel() {
		return pnlDraw;
	}

	public void clearPlots() {
		plots.clear();
	}

	public void clearPoints() {
		points.clear();
	}

	public final Line fit(final Line l) {
		return new Line(fit(l.getP1()), fit(l.getP2()));
	}

	public final Point fit(final Point p) {
		return new Point(fitx(p.getX()), fity(p.getY()));
	}

	public final double fitx(final double x) {
		return xres * (x - xmin) / (xmax - xmin);
	}

	public final double fity(final double y) {
		return yres * (1 - (y - ymin) / (ymax - ymin));
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
		xAxis = new ShapeX(new Line(xmin, 0, xmax, 0), ShapeX.TYPE_LINE, colAxis);
		yAxis = new ShapeX(new Line(0, ymin, 0, ymax), ShapeX.TYPE_LINE, colAxis);
	}

	public void setAntiAlias(final boolean antiAlias) {
		this.antiAlias = antiAlias;
		repaint();
	}

	public void setDim(final double xmin, final double xmax, final double ymin, final double ymax) {
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
		refresh();
	}

	public final double unfitx(final double x) {
		return x / xres * (xmax - xmin) + xmin;
	}

	public final double unfity(final double y) {
		return (1 - y / yres) * (ymax - ymin) + ymin;
	}
}
