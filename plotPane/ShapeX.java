/**
 * @author CGH
 */

package plotPane;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import simpleGeom.Line;

public final class ShapeX {
	private static final Stroke DEFAULT_STROKE = new BasicStroke();
	private static final double DEFAULT_POINT_DIAMETER = 8;
	public static final byte TYPE_LINE = 0;
	public static final byte TYPE_POINT = 1;

	private final Color mainColor;
	private final Stroke mainStroke;
	private final Line source;
	private final byte type;

	ShapeX(final Line source, final byte type, final Color mainColor) {
		this(source, type, mainColor, DEFAULT_STROKE);
	}

	ShapeX(final Line source, final byte type, final Color mainColor, final Stroke mainStroke) {
		if (source == null || mainColor == null || mainStroke == null)
			throw new IllegalArgumentException("Shape source, colour and stroke must not be null");
		if (type != TYPE_LINE && type != TYPE_POINT)
			throw new IllegalArgumentException("Unknown shape type: " + type);
		this.source = source;
		this.type = type;
		this.mainColor = mainColor;
		this.mainStroke = mainStroke;
	}

	public void drawMe(final Graphics2D g, final PlotPane p) {
		final Line l = p.fit(source);
		g.setColor(mainColor);
		g.setStroke(mainStroke);
		if (type == TYPE_LINE) {
			g.draw(new Line2D.Double(l.getX1(), l.getY1(), l.getX2(), l.getY2()));
		} else {
			g.fill(new Ellipse2D.Double(l.getX1() - DEFAULT_POINT_DIAMETER / 2,
					l.getY1() - DEFAULT_POINT_DIAMETER / 2, DEFAULT_POINT_DIAMETER, DEFAULT_POINT_DIAMETER));
		}
	}
}
