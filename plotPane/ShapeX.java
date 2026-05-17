/**
 * @author CGH
 */

package plotPane;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import simpleGeom.Line;

public class ShapeX {
	private final static double	EPS				= 0.5;
	private final static Stroke	defStroke		= new BasicStroke();
	private final static double	pointThickDef	= 8;
	public final static byte	TYPE_LINE		= 0;
	public final static byte	TYPE_POINT		= 1;

	private final Color		mainColor;
	private final Stroke	mainStroke;
	private final Line		source;
	private final byte		type;

	ShapeX(final Line source, final byte type, final Color mainColor) {
		this(source, type, mainColor, defStroke);
	}

	ShapeX(final Line source, final byte type, final Color mainColor, final Stroke mainStroke) {
		this.source = source;
		this.type = type;
		this.mainColor = mainColor;
		this.mainStroke = mainStroke;
	}

	public void drawMe(final Graphics2D g, final PlotPane p) {
		final Line l = p.fit(source);
		g.setColor(mainColor);
		g.setStroke(mainStroke);
		if (type == TYPE_LINE)
			g.draw(new Line2D.Double(l.getX1(), l.getY1(), l.getX2(), l.getY2()));
		else if (type == TYPE_POINT)
			g.fill(new Arc2D.Double(l.getX1() - pointThickDef / 2 + EPS, l.getY1() - pointThickDef / 2 + EPS, pointThickDef, pointThickDef,
					0, 360, Arc2D.CHORD));
	}
}
