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
mvn verify
java -jar target/nlipse.jar
```

For the optimized JDK 25 launch path, first create a platform- and JDK-specific AOT cache and then run with compact object headers enabled:

```bash
./scripts/create-aot-cache.sh
./scripts/run-optimized.sh
```

PowerShell equivalents are available as `scripts/create-aot-cache.ps1` and `scripts/run-optimized.ps1`. The AOT cache and its metadata fingerprint are generated under `target/`. The optimized launchers automatically ignore the cache and warn when the application or JDK fingerprint has changed; recreate it after changing the operating system or CPU architecture.

## Controls

- Left-click empty plot space to add a focus.
- Left-drag a focus to move it.
- Right-click a focus or press Delete to remove it.
- Middle-drag to pan.
- Use the mouse wheel to zoom around the cursor.
- Use the arrow keys to move the selected focus; hold Shift for fine movement.
- Edit focus coordinates and weights in the table.

## Rendering architecture

Rendering runs on a bounded, latest-wins background queue rather than Swing's event-dispatch thread. Interactive edits still use coalesced previews followed by a full-quality render. While middle-dragging, the last completed image is translated immediately; releasing the mouse starts the exact render.

Full-resolution field samples are cached in memory-bounded 32×32 world-space tiles. An integer-pixel pan keeps the same sampling lattice, so overlapping samples are reused exactly and only newly exposed rows and columns are evaluated. Exact viewport grids remain cached separately for fast redraws and coarse previews can be derived from a cached full grid without evaluating the field again.

Contours are extracted in one multi-level marching-squares pass. Ambiguous saddle cells use the bilinear asymptotic decider, with a centre sample only for an exact numerical tie. Individual cell segments are stitched into continuous world-coordinate polylines and cached independently of background shading, anti-aliasing and focus selection. Marker-free raster layers are cached separately, so selection-only redraws do not resample the field or retrace contours. Coarse previews retain the existing bounded one-level refinement for small hidden loops; a full adaptive quadtree remains deliberately deferred until representative benchmarks justify its added complexity.

CPU-bound sampling uses a renderer-owned daemon pool instead of Java's common pool. The default worker count is capped at 32 and can be overridden with `-Dnlipse.renderThreads=<count>`. The combined cache budget defaults to one-eighth of the maximum heap, bounded between 32 and 256 MiB, and can be overridden with `-Dnlipse.cacheMiB=<MiB>`.

The mathematical evaluators use compensated or scaled arithmetic for extreme finite values. Cassini products are accumulated in the logarithmic domain, and larger n-hyperbola fields use a sorted O(n log n) formulation.

## Continuous integration

GitHub Actions runs the complete Maven test suite and the JDK 25 AOT-training path on Java 25 for pull requests and every push to `main`.
