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
        long checksum = 0;
        for (int iteration = 0; iteration < 3; iteration++) {
            checksum ^= render(renderer, ellipse());
            checksum ^= render(renderer, cassini());
            checksum ^= render(renderer, hyperbola());
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
}
