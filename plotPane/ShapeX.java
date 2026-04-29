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
	private final static double	epsX			= 0.5;
	private final static double	epsY			= 0.5;
	private final static Color	mainColorDef	= Color.BLACK;
	private final static Stroke	mainStrokeDef	= new BasicStroke();
	private final static double	pointThickDef	= 8;
	private final static Line	sourceDef		= new Line(0, 0, 0, 0);
	public final static byte	TYPE_LINE		= 0;
	public final static byte	TYPE_POINT		= 1;
	private final static byte	typeDef			= TYPE_LINE;

	private Color				mainColor;
	private Stroke				mainStroke;
	private final double		pointThick;
	private Line				source;
	private byte				type;

	ShapeX() {
		this(sourceDef, typeDef, mainColorDef, mainStrokeDef);
	}

	ShapeX(final Line source, final byte type) {
		this(source, type, mainColorDef, mainStrokeDef);
	}

	ShapeX(final Line source, final byte type, final Color mainColor) {
		this(source, type, mainColor, mainStrokeDef);
	}

	ShapeX(final Line source, final byte type, final Color mainColor, final Stroke mainStroke) {
		this.source = source;
		this.type = type;
		this.mainColor = mainColor;
		this.mainStroke = mainStroke;
		this.pointThick = pointThickDef;
	}

	ShapeX(final Line source, final byte type, final Stroke mainStroke) {
		this(source, type, mainColorDef, mainStroke);
	}

	public void drawMe(final Graphics2D g, final PlotPane p) {
		final Line l = p.fit(source);
		g.setColor(mainColor);
		g.setStroke(mainStroke);
		if (type == TYPE_LINE)
			g.draw(new Line2D.Double(l.getX1(), l.getY1(), l.getX2(), l.getY2()));
		else if (type == TYPE_POINT)
			g.fill(new Arc2D.Double(l.getX1() - pointThick / 2 + epsX, l.getY1() - pointThick / 2 + epsY, pointThick, pointThick, 0, 360,
					Arc2D.CHORD));
	}

	public Color getMainColor() {
		return mainColor;
	}

	public Stroke getMainStroke() {
		return mainStroke;
	}

	public Line getSource() {
		return source;
	}

	public byte getType() {
		return type;
	}

	public void setMainColor(final Color mainColor) {
		this.mainColor = mainColor;
	}

	public void setMainStroke(final Stroke mainStroke) {
		this.mainStroke = mainStroke;
	}

	public void setSource(final Line source) {
		this.source = source;
	}

	public void setType(final byte type) {
		this.type = type;
	}
}
