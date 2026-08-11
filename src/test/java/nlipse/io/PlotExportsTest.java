package nlipse.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;
import nlipse.render.CancellationToken;
import nlipse.render.PlotRenderer;
import nlipse.render.RenderQuality;
import nlipse.render.RenderRequest;
import nlipse.render.RenderResult;
import nlipse.render.Viewport;

class PlotExportsTest {
    @TempDir
    Path directory;

    @Test
    void writesPngAndSvgFromTheSameCompletedRender() throws Exception {
        final RenderResult result = new PlotRenderer().render(new RenderRequest(snapshot(),
                64, 48, RenderQuality.FULL), CancellationToken.NONE);
        final Path png = directory.resolve("plot.png");
        final Path svg = directory.resolve("plot.svg");

        PlotExports.writePng(result, png);
        PlotExports.writeSvg(result, svg);

        final BufferedImage decoded = ImageIO.read(png.toFile());
        assertNotNull(decoded);
        assertEquals(64, decoded.getWidth());
        assertEquals(48, decoded.getHeight());
        final String xml = Files.readString(svg);
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("<svg"));
        assertTrue(xml.contains("<polyline") || xml.contains("<path"));
    }

    private static PlotSnapshot snapshot() {
        return new PlotSnapshot(CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                1.5, 4, 4, new Viewport(-3, 3, -2, 2),
                true, true, true, false, true, -1);
    }
}
