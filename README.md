# nLipse

nLipse is a Java Swing visualiser for implicit curves defined by weighted distances to multiple focus points.

It supports:

- **n-Ellipse:** weighted sum of focal distances.
- **Cassini family:** product of focal distances raised to their weights.
- **n-Hyperbola:** average pairwise absolute difference between weighted focal distances.

## Requirements

- Java 21
- Maven 3.9 or newer

Older Java releases are deliberately unsupported. The build enforces Java 21+, compiles with `--release 21`, enables all compiler warnings and treats warnings as errors.

## Build and run

```bash
mvn clean verify
java -jar target/nlipse-0.4.1-SNAPSHOT.jar
```

## Controls

- Left-click empty plot space to add a focus.
- Left-drag a focus to move it.
- Right-click a focus or press Delete to remove it.
- Middle-drag to pan.
- Use the mouse wheel to zoom around the cursor.
- Use the arrow keys to move the selected focus; hold Shift for fine movement.
- Edit focus coordinates and weights in the table.

## Rendering architecture

Rendering runs on a cancellable background worker, never on Swing's event-dispatch thread. Interactive changes use a coalesced preview render and are followed by a full-quality render. The scalar field is sampled once and cached so the background, extrema and all contour levels share the same data. Contours are generated with marching squares, including centre sampling for ambiguous cells and one-level adaptive subdivision in coarse previews.

The plot canvas follows the window size; no fixed-resolution or null-layout drawing panel remains. Immutable values and render messages use Java records, while the CPU-bound renderer deliberately stays on a single platform thread rather than a virtual thread.

## Continuous integration

GitHub Actions runs the complete Maven test suite on Java 21 for pushes to `main` and for manual workflow runs.
