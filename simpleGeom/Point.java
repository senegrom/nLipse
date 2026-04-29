/**
 * @author CGH
 */

package simpleGeom;

public class Point {
	private final static double	xDef	= 0;
	private final static double	yDef	= 0;

	public final static double dist(final Point p1, final Point p2) {
		final double dx = p1.getX() - p2.getX();
		final double dy = p1.getY() - p2.getY();
		return Math.sqrt(dx * dx + dy * dy);
	}

	private double	x, y;

	public Point() {
		this(xDef, yDef);
	}

	public Point(final double x, final double y) {
		setX(x);
		setY(y);
	}

	public Point(final int x, final int y) {
		setX(x);
		setY(y);
	}

	public void addX(final double x) {
		this.x += x;
	}

	public void addY(final double y) {
		this.y += y;
	}

	public final double distTo(final Point p) {
		return dist(this, p);
	}

	public double[] getPos() {
		return new double[]{x, y };
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public void setPos(final double x, final double y) {
		setX(x);
		setY(y);
	}

	public void setPos(final double[] i) {
		setX(i[0]);
		setY(i[1]);
	}

	public void setX(final double x) {
		this.x = x;
	}

	public void setY(final double y) {
		this.y = y;
	}

	@Override
	public String toString() {
		return "( " + getX() + " | " + getY() + " )";
	}
}
