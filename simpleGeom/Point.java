/**
 * @author CGH
 */

package simpleGeom;

public final class Point {
	private final double	x, y;

	public Point(final double x, final double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	@Override
	public String toString() {
		return "( " + x + " | " + y + " )";
	}
}
