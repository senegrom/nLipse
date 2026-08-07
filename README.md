# nLipse

nLipse is a Java Swing visualiser for implicit curves defined by weighted distances to multiple focus points.

It supports three curve families:

- **n-Ellipse:** the weighted sum of focal distances.
- **Cassini family:** the product of focal distances raised to their weights.
- **n-Hyperbola:** the average pairwise absolute difference between weighted focal distances.

## Requirements

- Java 11 or newer
- Maven 3.8 or newer for builds and tests

## Build and run

```bash
mvn clean verify
java -jar target/nlipse-0.2.3-SNAPSHOT.jar
```

The Maven build compiles the existing source layout, runs the mathematical and coordinate-transform tests, and produces an executable JAR.

## Controls

- Left-click empty plot space to add a focus.
- Left-drag a focus to move it.
- Right-click a focus or press Delete to remove it.
- Middle-drag to pan.
- Use the mouse wheel to zoom around the cursor.
- Use the arrow keys to move the selected focus; hold Shift for fine movement.
- Edit focus coordinates and weights in the table.

Weights, plot bounds and coordinates must be finite.

## Continuous integration

GitHub Actions runs `mvn verify` on Java 11, 17 and 21 for every push and pull request.
