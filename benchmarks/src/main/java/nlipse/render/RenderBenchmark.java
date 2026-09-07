package nlipse.render;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** End-to-end render benchmarks for cold, cached and integer-pan paths. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 750, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class RenderBenchmark {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final Viewport VIEWPORT = new Viewport(-4, 4, -3, 3);
    private static final List<Focus> FOCI = foci();

    @State(Scope.Thread)
    public static class ColdState {
        PlotRenderer renderer;
        RenderRequest request;

        @Setup(Level.Invocation)
        public void setup() {
            renderer = new PlotRenderer();
            request = request(VIEWPORT);
        }
    }

    @State(Scope.Thread)
    public static class CachedState {
        PlotRenderer renderer;
        RenderRequest request;

        @Setup(Level.Trial)
        public void setup() {
            renderer = new PlotRenderer();
            request = request(VIEWPORT);
            renderer.render(request, CancellationToken.NONE);
        }
    }

    @State(Scope.Thread)
    public static class PanState {
        PlotRenderer renderer;
        RenderRequest request;

        @Setup(Level.Invocation)
        public void setup() {
            renderer = new PlotRenderer();
            renderer.render(request(VIEWPORT), CancellationToken.NONE);
            final Viewport panned = VIEWPORT.panPixels(11, -7, WIDTH, HEIGHT);
            request = request(panned);
        }
    }

    @Benchmark
    public RenderResult coldFullRender(final ColdState state) {
        return state.renderer.render(state.request, CancellationToken.NONE);
    }

    @Benchmark
    public RenderResult cachedFullRender(final CachedState state) {
        return state.renderer.render(state.request, CancellationToken.NONE);
    }

    @Benchmark
    public RenderResult integerPanReuse(final PanState state) {
        return state.renderer.render(state.request, CancellationToken.NONE);
    }

    private static RenderRequest request(final Viewport viewport) {
        final PlotSnapshot snapshot = new PlotSnapshot(
                CurveType.LIPSE,
                CurveType.LIPSE.defaultParameter(),
                FOCI,
                1,
                8,
                9,
                viewport,
                true,
                false,
                true,
                false,
                false,
                -1);
        return new RenderRequest(snapshot, WIDTH, HEIGHT, RenderQuality.FULL);
    }

    private static List<Focus> foci() {
        final List<Focus> result = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            final double angle = index * Math.PI * 2 / 12;
            final double radius = 1.25 + 0.08 * index;
            result.add(new Focus(Math.cos(angle) * radius,
                    Math.sin(angle) * radius, 0.6 + 0.08 * (index % 5)));
        }
        return List.copyOf(result);
    }
}
