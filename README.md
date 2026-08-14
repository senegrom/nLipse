# nLipse

nLipse is a Java Swing visualiser for implicit curves defined by weighted distances to multiple focus points.

## Curve families

For a point `(x, y)`, let `dᵢ` be its Euclidean distance from focus `i`, `wᵢ` that focus's weight, and `zᵢ = |wᵢ|dᵢ` for magnitude-based families. A zero weight disables a focus wherever `zᵢ` is used.

- **n-Ellipse:** `Σ wᵢdᵢ`, the signed weighted sum of focal distances.
- **Cassini family:** `∏ dᵢ ^ wᵢ`, accumulated in the logarithmic domain; negative weights form distance ratios.
- **n-Hyperbola:** the mean pairwise absolute difference between the signed values `wᵢdᵢ`.
- **Nearest-focus envelope:** `min zᵢ`, producing multiplicatively weighted Voronoi-style level sets.
- **Farthest-focus envelope:** `max zᵢ`.
- **Quadratic n-Ellipse:** `√Σzᵢ²`, the root-sum-square or L2 aggregate.
- **Weighted-distance range:** `max zᵢ − min zᵢ`, measuring the span of active focal distances.
- **Inverse-distance potential:** `Σ wᵢ/dᵢ`, with signed source and sink weights and genuine singularities at active foci.
- **Generalised power mean:** `(mean zᵢᵖ)¹⁄ᵖ`. The parameter `p` may be any real `double`, including `0`, `+∞`, and `−∞`; `p=0` is the geometric mean, `p=1` the arithmetic mean, `p=2` the RMS distance, and `p=±∞` uses the exact envelope implementations.
- **Median weighted distance:** `median zᵢ`, with an overflow-safe average of the two central values for an even number of active foci.
- **Smooth nearest envelope:** `−τ ln(mean exp(−zᵢ/τ))` for finite `τ > 0`.
- **Smooth farthest envelope:** `τ ln(mean exp(zᵢ/τ))` for finite `τ > 0`.
- **Gaussian radial-basis field:** `Σ wᵢ exp(−dᵢ²/(2σ²))` for finite `σ > 0`; signed weights create peaks, wells, saddles, and nodal contours.

The magnitude-only envelope, quadratic, range, power-mean, median, and smooth-envelope families ignore weight signs. Weight signs remain meaningful for the n-ellipse, Cassini, n-hyperbola, inverse potential, and Gaussian families. Both the setup dialog and the right-hand control bar show the selected family's formula, geometric interpretation, weight semantics, parameter meaning, and current parameter value.

The parameter box accepts ordinary integer or decimal text. For the power mean it also accepts `inf`, `+inf`, `-inf`, `infinity`, `+∞`, and `−∞`. Press Enter or move focus away from the box to validate the value and refresh the right-hand explanation.

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
- Edit the family parameter (`p`, `τ`, or `σ`) in the right-hand panel when enabled.

## Legend and export

The optional level legend (Display → "Level legend") lists the drawn contour levels with their colours in the top-right corner, highest level first; when more than twelve levels are drawn it shows an even subsample that always includes both endpoints. On a small canvas the row count is reduced automatically, and the legend is omitted rather than clipped if even one row cannot fit. Because the legend is part of the rendered image, PNG exports include it.

"Export PNG…" and "Export SVG…" capture a fresh full-quality, exact-arithmetic render from the committed controls and focus table at the current canvas size. Interactive renders retain a bounded precision-fallback allowance for responsiveness, but accepted exports use an unlimited allowance and are rejected if a render engine reports a precision-limited result. They run through the bounded background scheduler, so encoding never blocks Swing and later mouse movement cannot cancel an accepted export. A second simultaneous export is rejected instead of building an unbounded queue. PNG contains the complete raster image; SVG writes contour polylines, axes, focus and extrema markers, and the legend, while deliberately omitting the raster heatmap background. Setup, PNG and SVG writes use a same-directory temporary file and replace the destination only after the complete output has been produced.

Editable coordinates, weights, viewport bounds and field levels use locale-independent round-trip decimal text, so opening and committing a cell does not silently discard valid `double` precision.

## Rendering architecture

Rendering runs on a bounded single-worker scheduler rather than Swing's event-dispatch thread. Interactive edits are latest-wins and use coalesced previews followed by a full-quality render. Durable PNG/SVG exports share the same scheduler but are never superseded by later interaction. While middle-dragging, the last completed image is translated immediately; releasing the mouse starts the exact render.

Full-resolution field samples are cached in memory-bounded 32×32 world-space tiles. An integer-pixel pan retains a canonical lattice identity consisting of its origin, sample steps and integer indices, so overlapping samples are reused exactly and only newly exposed rows and columns are evaluated. Fractional pans and zooms create a new lattice. Exact viewport grids remain cached separately for fast redraws and coarse previews can be derived from a cached full grid without evaluating the field again.

Contours are extracted in one multi-level marching-squares pass. Ambiguous saddle cells use the bilinear asymptotic decider, with a centre sample only for an exact numerical tie. Individual cell segments are stitched into continuous world-coordinate polylines and cached independently of background shading, anti-aliasing and focus selection. Marker-free raster layers are cached separately, so selection-only redraws do not resample the field or retrace contours. Coarse previews retain the existing bounded one-level refinement for small hidden loops; a full adaptive quadtree remains deliberately deferred until representative benchmarks justify its added complexity.

Render requests are rejected before allocation when their pixel count would exceed a conservative heap-derived budget: one thirty-second of the maximum heap, capped at sixty-four million pixels and never below the 2×2 minimum. This protects field, image and cache buffers from integer overflow and predictable out-of-memory failures while retaining ordinary 4K and 8K display sizes on appropriately sized heaps.

CPU-bound sampling uses a renderer-owned daemon pool instead of Java's common pool. The default worker count is capped at 32 and can be overridden with `-Dnlipse.renderThreads=<count>`. The combined cache budget defaults to one-eighth of the maximum heap, bounded between 32 and 256 MiB, and can be overridden with `-Dnlipse.cacheMiB=<MiB>`.

The mathematical evaluators use compensated, scaled, logarithmic-domain or adaptive arbitrary-precision fallback arithmetic according to the family. Ordinary pixels stay on primitive `double` paths; the fallback starts at modest precision and increases it only until the correctly rounded `double` result is stable. Each adaptive evaluation derives its cancellation-scale floor from the actual inputs, so ordinary-magnitude fallbacks settle near the initial precision instead of paying each family's static worst case. Exact focus coordinates and weights are cached once per immutable field. Weighted norms and aggregates near binary64 underflow or overflow are resolved after the complete mathematical expression rather than after each component or term. Cassini products and general power means use logarithmic-domain accumulation, and normalization factors that become low-precision subnormals are detected before a large logarithm or distance can magnify their rounding. The harmonic mean has a scaled reciprocal-free special case, smooth envelopes retain both their zero- and infinite-temperature limits, the quadratic family uses overflow-resistant `hypot` accumulation, inverse potentials detect numerically ill-conditioned mixed-sign cancellation, Gaussian amplitudes resolve underflowed or coarsely rounded kernels before amplification, median selection avoids a full sort for larger focus sets, and larger n-hyperbola fields use a sorted O(n log n) formulation.

Interactive renders declare a per-field budget of exact evaluations that scales with the image perimeter. Ill-conditioned samples normally form a curve and stay well inside that allowance, so cancellation is resolved wherever it would otherwise destroy a value. A degenerate configuration whose weighted distances are near-equal across the whole viewport — what a far-out zoom does to any focus arrangement — would otherwise evaluate every pixel exactly and take seconds; such renders spend the budget and keep the primitive result elsewhere, where the error stays bounded by the inputs' own rounding. The status bar marks such a display as precision limited, its newly computed samples are not cached, and extrema-derived fit/clamp changes are deferred rather than committing approximate limits to the model. The displayed field extrema remain explicitly labelled approximate. A settled full render with a pending fit or clamp immediately schedules a cancellable unlimited exact retry; exports likewise require an exact render. Overflow, a primitive zero caused by underflow and non-finite intermediates ignore the budget. Far-field Gaussian sums whose every term underflows are the exception: their bound is proven in primitive arithmetic and they return a signed zero directly, so zooming far away from every focus stays interactive; only a near-balanced mixed-sign underflow still resolves exactly. A finite magnitude near underflow or overflow whose final rounding is ambiguous may spend the same precision allowance; if that allowance is exhausted the frame is marked precision limited and kept out of exact state and caches. Cursor readouts, direct API use and exact exports are unlimited.

Viewport sampling, panning, zooming and inverse pixel mapping use overflow-safe scaled or fused arithmetic. Every finite ordered pair of viewport endpoints is accepted even when its span is larger than `Double.MAX_VALUE`; adjacent endpoints whose per-pixel step underflows still preserve both explicit edges, and strictly off-screen points remain off-screen after pixel rounding.

Every completed full render also produces one immutable `RenderPackage` containing the snapshot, dimensions, deduplicated levels, colours, extrema and stitched contour geometry. Raster display, legends and SVG export consume that same package so they cannot combine state from different renders.

## Correctness and performance checks

The JUnit suite includes deterministic exponent-biased differential tests against independent high-precision or direct references. Seeds are fixed so a failure can be reproduced. They cover algebraic fields, inverse-potential cancellation, transcendental families, power-mean ordering and smooth-envelope bounds.

The separate Java 25 JMH harness measures ordinary batched ellipse, potential and Gaussian sampling as well as deliberately exceptional ellipse and potential evaluations:

```bash
mvn -DskipTests install
mvn -f benchmarks/pom.xml package
java -jar benchmarks/target/benchmarks.jar
```

The manually dispatched **JMH benchmark** GitHub workflow accepts an include regex and a fork count from 1 to 8 and uploads JSON results.

## Continuous integration

GitHub Actions runs the complete Maven test suite, builds and smoke-tests the JMH harness, and verifies the JDK 25 AOT-training path for pull requests and every push to `main`.
