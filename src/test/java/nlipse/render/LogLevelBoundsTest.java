package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import nlipse.io.PlotExports;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogLevelBoundsTest {
    @TempDir
    Path directory;

    @Test
    void adjacentLogarithmicBoundsKeepExactlyTheirTwoRepresentableLevels() {
        for (final double minimum : new double[]{Double.MIN_VALUE,
                Math.nextDown(Double.MIN_NORMAL), Double.MIN_NORMAL, 0.1, 1, 100,
                1e300, Math.nextDown(Double.MAX_VALUE)}) {
            final double maximum = Math.nextUp(minimum);
            for (final int count : new int[]{2, 3, 17, 200}) {
                assertArrayEquals(new double[]{minimum, maximum},
                        PlotRenderer.levels(minimum, maximum, count, true));
            }
        }
    }

    @Test
    void generatedLevelsAreFiniteStrictlyIncreasingAndInsideTheirBounds() {
        final Random random = new Random(0x4c4f474c4556454cL);
        for (int sample = 0; sample < 300; sample++) {
            final double first = Double.longBitsToDouble(
                    1 + Math.floorMod(random.nextLong(), 0x7feffffffffffffeL));
            final double second = Double.longBitsToDouble(
                    1 + Math.floorMod(random.nextLong(), 0x7feffffffffffffeL));
            final double minimum = Math.min(first, second);
            final double maximum = Math.max(first, second);
            final double[] levels = PlotRenderer.levels(minimum, maximum, 200, true);
            assertEquals(minimum, levels[0]);
            assertEquals(maximum, levels[levels.length - 1]);
            for (int index = 0; index < levels.length; index++) {
                assertTrue(Double.isFinite(levels[index]));
                assertTrue(levels[index] >= minimum && levels[index] <= maximum);
                if (index > 0) {
                    assertTrue(levels[index] > levels[index - 1]);
                }
            }
        }
    }

    @Test
    void adjacentLogarithmicLevelsRenderAndExportExactly() throws IOException {
        final PlotSnapshot snapshot = new PlotSnapshot(CurveType.LIPSE, 1,
                List.of(new Focus(0, 0, 1)), 100, Math.nextUp(100.0), 3,
                new Viewport(99.5, 100.5, -0.5, 0.5),
                false, false, false, true, false, -1);
        final RenderResult result = new PlotRenderer().render(
                new RenderRequest(snapshot, 11, 11, RenderQuality.FULL).requiringExact(),
                CancellationToken.NONE);
        assertFalse(result.precisionLimited());
        final RenderPackage completed = result.renderPackage().orElseThrow();
        assertEquals(2, completed.levelCount());
        assertEquals(100, completed.level(0));
        assertEquals(Math.nextUp(100.0), completed.level(1));
        final Path png = directory.resolve("plot.png");
        final Path svg = directory.resolve("plot.svg");
        PlotExports.writePng(result, png);
        PlotExports.writeSvg(result, svg);
        assertTrue(Files.size(png) > 0);
        assertTrue(Files.size(svg) > 0);
    }
}
