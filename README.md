# nLipse

nLipse is a Java Swing visualiser for implicit curves defined by weighted distances to multiple focus points.

It supports:

- **n-Ellipse:** weighted sum of focal distances.
- **Cassini family:** product of focal distances raised to their weights.
- **n-Hyperbola:** average pairwise absolute difference between weighted focal distances.

## Requirements

- JDK 25 or newer
- Maven 3.9 or newer

The build intentionally emits Java 25 bytecode and does not support older Java releases.

## Build and run

```bash
mvn clean verify
java -jar target/nlipse-0.5.0-SNAPSHOT.jar
```

For the optimized JDK 25 launch path, first create a platform- and JDK-specific AOT cache and then run with compact object headers enabled:

```bash
./scripts/create-aot-cache.sh
./scripts/run-optimized.sh
```

PowerShell equivalents are available as `scripts/create-aot-cache.ps1` and `scripts/run-optimized.ps1`. The AOT cache is generated under `target/` and must be recreated after changing the application, JDK build, operating system, or CPU architecture.

## Controls

- Left-click empty plot space to add a focus.
- Left-drag a focus to move it.
- Right-click a focus or press Delete to remove it.
- Middle-drag to pan.
- Use the mouse wheel to zoom around the cursor.
- Use the arrow keys to move the selected focus; hold Shift for fine movement.
- Edit focus coordinates and weights in the table.

## Rendering architecture

Rendering runs on a cancellable background worker, never on Swing's event-dispatch thread. Interactive changes use a coalesced preview render and are followed by a full-quality render.

The scalar field is sampled once and cached so the background, extrema and all contour levels share the same data. Large grids are sampled across the common work-stealing pool. Contours are generated in one allocation-free, multi-level marching-squares pass, including centre sampling for ambiguous cells and adaptive subdivision in coarse previews. Background pixels are written directly into the raster through a precomputed palette.

The field-grid cache is limited by memory rather than an arbitrary entry count. The n-hyperbola evaluator switches from quadratic pairwise comparison to a sorted O(n log n) formulation for larger focus sets.

## Continuous integration

GitHub Actions runs the complete Maven test suite and the JDK 25 AOT-training path on Java 25 for every push to `main`.
