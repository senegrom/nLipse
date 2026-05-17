/**
 * @author CGH
 */

package simpleGeom;

public class Line {
	private final Point	p1, p2;

	public Line(final double x1, final double y1, final double x2, final double y2) {
		this(new Point(x1, y1), new Point(x2, y2));
	}

	public Line(final Point p1, final Point p2) {
		this.p1 = p1;
		this.p2 = p2;
	}

	public Point getP1() {
		return p1;
	}

	public Point getP2() {
		return p2;
	}

	public double getX1() {
		return p1.getX();
	}

	public double getX2() {
		return p2.getX();
	}

	public double getY1() {
		return p1.getY();
	}

	public double getY2() {
		return p2.getY();
	}

	@Override
	public String toString() {
		return p1 + " , " + p2;
	}
}
