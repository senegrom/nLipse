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
					isAntiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

			for (final PlotCurve pc : plots)
				pc.drawMeBkgrd(g, fatherPlotPane);

			if (isDrawXAxis())
				xAxis.drawMe(g, fatherPlotPane);
			if (isDrawYAxis())
				yAxis.drawMe(g, fatherPlotPane);

			g.setColor(colBorder);
			if (isDrawBorder())
				g.draw(border);

			for (final ShapeX l : lines)
				l.drawMe(g, fatherPlotPane);
			for (final ShapeX pt : points)
				pt.drawMe(g, fatherPlotPane);
			for (final PlotCurve pc : plots)
				pc.drawMe(g, fatherPlotPane);
		}
	}

	private final static boolean	antiAliasDef		= true;
	private final static Color		colAxis				= Color.GRAY;
	private final static Color		colBackgrd			= Color.WHITE;
	private final static Color		colBorder			= Color.BLACK;
	private final static Color		colForeground		= Color.BLACK;
	private final static boolean	drawBorderDef		= true;
	private final static boolean	drawXAxisDef		= true;
	private final static boolean	drawYAxisDef		= true;
	private final static Stroke		mainStroke			= new BasicStroke();
	private static final long		serialVersionUID	= -4073631369165565550L;
	private final static double		xmaxDef				= 10;
	private final static double		xminDef				= -10;
	private final static int		xresDef				= 500;
	private final static double		ymaxDef				= 10;
	private final static double		yminDef				= -10;
	private final static int		yresDef				= 500;

	private boolean					antiAlias, drawBorder, drawXAxis, drawYAxis;
	private Shape					border;
	private boolean					initialized			= false;
	private final List<ShapeX>		lines				= new ArrayList<>();
	private final List<PlotCurve>	plots				= new ArrayList<>();
	private final List<ShapeX>		points				= new ArrayList<>();
	private final JPanelDraw		pnlDraw;
	private ShapeX					xAxis, yAxis;
	private double					xmin, xmax, ymin, ymax;
	private int						xres, yres;

	public PlotPane() {
		this(xminDef, xmaxDef, yminDef, ymaxDef, xresDef, yresDef);
	}

	public PlotPane(final double xmin, final double xmax, final double ymin, final double ymax) {
		this(xmin, xmax, ymin, ymax, xresDef, yresDef);
	}

	public PlotPane(final double xmin, final double xmax, final double ymin, final double ymax, final int xres, final int yres) {
		super();
		pnlDraw = new JPanelDraw(this);

		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
		this.xres = xres;
		this.yres = yres;
		this.antiAlias = antiAliasDef;
		this.drawBorder = drawBorderDef;
		this.drawXAxis = drawXAxisDef;
		this.drawYAxis = drawYAxisDef;

		initialized = true;
		refresh();

		pnlDraw.setBackground(colBackgrd);
		this.setLayout(null);
		this.add(pnlDraw);
		pnlDraw.setLocation(20, 20);
	}

	public PlotPane(final double[] koord) {
		this(koord[0], koord[1], koord[2], koord[3], xresDef, yresDef);
	}

	public PlotPane(final double[] koord, final int[] res) {
		this(koord[0], koord[1], koord[2], koord[3], res[0], res[1]);
	}

	public PlotPane(final int xres, final int yres) {
		this(xminDef, xmaxDef, yminDef, ymaxDef, xres, yres);
	}

	public PlotPane(final int[] res) {
		this(xminDef, xmaxDef, yminDef, ymaxDef, res[0], res[1]);
	}

	public int addLine(final Line l) {
		return addLine(l, colForeground, mainStroke);
	}

	public int addLine(final Line l, final Color c) {
		return addLine(l, c, mainStroke);
	}

	public int addLine(final Line l, final Color c, final Stroke s) {
		lines.add(new ShapeX(l, ShapeX.TYPE_LINE, c, s));
		return lines.size();
	}

	public int addPlot(final PlotCurve p) {
		return addPlot(p, colForeground, mainStroke);
	}

	public int addPlot(final PlotCurve p, final Color c) {
		return addPlot(p, c, mainStroke);
	}

	public int addPlot(final PlotCurve p, final Color c, final Stroke s) {
		p.mainColor = c;
		p.mainStroke = s;
		plots.add(p);
		return plots.size();
	}

	public int addPoint(final Point p) {
		return addPoint(p, colForeground, mainStroke);
	}

	public int addPoint(final Point p, final Color c) {
		return addPoint(p, c, mainStroke);
	}

	public int addPoint(final Point p, final Color c, final Stroke s) {
		points.add(new ShapeX(new Line(p, p), ShapeX.TYPE_POINT, c, s));
		return points.size();
	}

	public JPanel getDrawPanel() {
		return pnlDraw;
	}

	public void clearLines() {
		lines.clear();
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

	public double getPixelX() {
		return (xmax - xmin) / xres;
	}

	public double getPixelY() {
		return (ymax - ymin) / yres;
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

	public boolean isAntiAlias() {
		return antiAlias;
	}

	public boolean isDrawBorder() {
		return drawBorder;
	}

	public boolean isDrawXAxis() {
		return drawXAxis;
	}

	public boolean isDrawYAxis() {
		return drawYAxis;
	}

	public void refresh() {
		if (!initialized)
			return;
		pnlDraw.setSize(xres, yres);
		refreshBorder();
		refreshAxes();
		repaint();
	}

	private final void refreshAxes() {
		xAxis = new ShapeX(new Line(xmin, 0, xmax, 0), ShapeX.TYPE_LINE, colAxis);
		yAxis = new ShapeX(new Line(0, ymin, 0, ymax), ShapeX.TYPE_LINE, colAxis);
	}

	private final void refreshBorder() {
		border = new Rectangle2D.Double(0, 0, xres - 1, yres - 1);
	}

	public void removeLine() {
		if (!lines.isEmpty())
			lines.remove(lines.size() - 1);
	}

	public void removeLine(final int i) {
		if (i < 0 || i >= lines.size())
			return;
		lines.remove(i);
	}

	public void removePlot() {
		if (!plots.isEmpty())
			plots.remove(plots.size() - 1);
	}

	public void removePlot(final int i) {
		if (i < 0 || i >= plots.size())
			return;
		plots.remove(i);
	}

	public void removePoint() {
		if (!points.isEmpty())
			points.remove(points.size() - 1);
	}

	public void removePoint(final int i) {
		if (i < 0 || i >= points.size())
			return;
		points.remove(i);
	}

	public void setAntiAlias(final boolean antiAlias) {
		this.antiAlias = antiAlias;
		refresh();
	}

	public void setDim(final double xmin, final double xmax, final double ymin, final double ymax) {
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
		refresh();
	}

	public void setDrawBorder(final boolean drawBorder) {
		this.drawBorder = drawBorder;
		refresh();
	}

	public void setDrawXAxis(final boolean drawXAxis) {
		this.drawXAxis = drawXAxis;
		refresh();
	}

	public void setDrawYAxis(final boolean drawYAxis) {
		this.drawYAxis = drawYAxis;
		refresh();
	}

	public void setRes(final int xres, final int yres) {
		this.xres = xres;
		this.yres = yres;
		refresh();
	}

	public void setXmax(final double xmax) {
		this.xmax = xmax;
		refresh();
	}

	public void setXmin(final double xmin) {
		this.xmin = xmin;
		refresh();
	}

	public void setXres(final int xres) {
		this.xres = xres;
		refresh();
	}

	public void setYmax(final double ymax) {
		this.ymax = ymax;
		refresh();
	}

	public void setYmin(final double ymin) {
		this.ymin = ymin;
		refresh();
	}

	public void setYres(final int yres) {
		this.yres = yres;
		refresh();
	}

	public final Line unfit(final Line l) {
		return new Line(unfit(l.getP1()), unfit(l.getP2()));
	}

	public final Point unfit(final Point p) {
		return new Point(unfitx(p.getX()), unfity(p.getY()));
	}

	public final double unfitx(final double x) {
		return x / xres * (xmax - xmin) + xmin;
	}

	public final double unfity(final double y) {
		return (1 - y / yres) * (ymax - ymin) + ymin;
	}
}
