package nlipse.app;

import java.util.List;
import java.util.stream.IntStream;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;
import nlipse.render.CancellationToken;
import nlipse.render.FieldExtrema;
import nlipse.render.PlotRenderer;
import nlipse.render.RenderQuality;
import nlipse.render.RenderRequest;
import nlipse.render.RenderResult;
import nlipse.render.Viewport;

/** Headless representative workload used to train a JDK 25 AOT cache. */
public final class AotTrainer {
    private AotTrainer() {
    }

    public static void main(final String[] arguments) {
        System.setProperty("java.awt.headless", "true");
        preloadUiClasses();
        final PlotRenderer renderer = new PlotRenderer();
        final List<PlotSnapshot> workloads = List.of(
                ellipse(), cassini(), hyperbola(),
                magnitudeFamily(CurveType.NEAREST, 0.05, 4.5),
                magnitudeFamily(CurveType.FARTHEST, 1, 7.5),
                magnitudeFamily(CurveType.QUADRATIC, 1, 10),
                magnitudeFamily(CurveType.RANGE, 0.05, 5.5),
                potential());
        long checksum = 0;
        for (int iteration = 0; iteration < 2; iteration++) {
            for (final PlotSnapshot workload : workloads) {
                checksum = Long.rotateLeft(checksum, 9) ^ renderSequence(renderer, workload);
            }
        }
        System.out.println("nLipse AOT training complete: " + Long.toUnsignedString(checksum));
    }

    private static void preloadUiClasses() {
        final ClassLoader loader = AotTrainer.class.getClassLoader();
        for (final String className : List.of(
                "nlipse.app.Main",
                "nlipse.ui.PlotCanvas",
                "nlipse.ui.PlotController",
                "nlipse.ui.PlotWindow",
                "nlipse.ui.SetupDialog")) {
            try {
                Class.forName(className, false, loader);
            } catch (final ClassNotFoundException exception) {
                throw new IllegalStateException("Missing application class: " + className, exception);
            }
        }
    }

    private static long renderSequence(final PlotRenderer renderer,
            final PlotSnapshot snapshot) {
        long checksum = render(renderer, snapshot);
        final Viewport pannedViewport = snapshot.viewport().panPixels(24, -12, 720, 540);
        final PlotSnapshot panned = new PlotSnapshot(snapshot.curveType(), snapshot.foci(),
                snapshot.distanceMin(), snapshot.distanceMax(), snapshot.curveCount(),
                pannedViewport, snapshot.showBackground(), snapshot.showExtrema(),
                snapshot.antiAlias(), snapshot.logSpacing(), snapshot.selectedFocusIndex());
        checksum ^= Long.rotateLeft(render(renderer, panned), 11);
        final PlotSnapshot restyled = new PlotSnapshot(snapshot.curveType(), snapshot.foci(),
                snapshot.distanceMin(), snapshot.distanceMax(), snapshot.curveCount(),
                snapshot.viewport(), !snapshot.showBackground(), snapshot.showExtrema(),
                snapshot.antiAlias(), snapshot.logSpacing(), snapshot.selectedFocusIndex());
        checksum ^= Long.rotateLeft(render(renderer, restyled), 23);
        return checksum;
    }

    private static long render(final PlotRenderer renderer, final PlotSnapshot snapshot) {
        final RenderResult result = renderer.render(
                new RenderRequest(snapshot, 720, 540, RenderQuality.FULL),
                CancellationToken.NONE);
        final int centre = result.image().getRGB(result.image().getWidth() / 2,
                result.image().getHeight() / 2);
        final FieldExtrema extrema = result.extrema().orElseThrow(
                () -> new IllegalStateException("AOT training field has no finite samples"));
        return Double.doubleToLongBits(extrema.minimum())
                ^ Long.rotateLeft(Double.doubleToLongBits(extrema.maximum()), 17)
                ^ Integer.toUnsignedLong(centre);
    }

    private static PlotSnapshot ellipse() {
        return new PlotSnapshot(CurveType.LIPSE,
                List.of(new Focus(2, 0, 1), new Focus(0, 1, 1),
                        new Focus(-1, -1.5, 1)),
                2, 12, 24, new Viewport(-4, 4, -3, 3),
                true, true, true, false, -1);
    }

    private static PlotSnapshot cassini() {
        return new PlotSnapshot(CurveType.CASSIN,
                List.of(new Focus(-1.2, 0, 1), new Focus(1.2, 0, 1)),
                0.05, 12, 24, new Viewport(-3, 3, -2.5, 2.5),
                true, true, true, true, -1);
    }

    private static PlotSnapshot hyperbola() {
        final List<Focus> foci = IntStream.range(0, 40)
                .mapToObj(index -> {
                    final double angle = index * Math.TAU / 40;
                    return new Focus(2.2 * Math.cos(angle), 2.2 * Math.sin(angle),
                            0.5 + index % 5 * 0.25);
                })
                .toList();
        return new PlotSnapshot(CurveType.HYPERB, foci,
                0.05, 5, 20, new Viewport(-4, 4, -3, 3),
                false, true, true, false, -1);
    }

    private static PlotSnapshot magnitudeFamily(final CurveType type,
            final double minimum, final double maximum) {
        return new PlotSnapshot(type,
                List.of(
                        new Focus(-1.7, -0.2, 1),
                        new Focus(1.5, 0.1, 0.65),
                        new Focus(0.2, 1.8, -1.25),
                        new Focus(0, -1.5, 0)),
                minimum, maximum, 22, new Viewport(-4, 4, -3, 3),
                true, true, true, false, -1);
    }

    private static PlotSnapshot potential() {
        return new PlotSnapshot(CurveType.POTENTIAL,
                List.of(
                        new Focus(-1.2, 0, 1),
                        new Focus(1.2, 0, -1),
                        new Focus(0, 1.6, 0.65)),
                -2.5, 2.5, 25, new Viewport(-4, 4, -3, 3),
                true, true, true, false, -1);
    }
}
