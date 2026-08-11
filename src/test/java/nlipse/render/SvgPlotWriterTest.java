package nlipse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

class SvgPlotWriterTest {
    @Test
    void writesWellFormedSvgWithContoursFociAxesAndLegend() throws Exception {
        final PlotSnapshot snapshot = new PlotSnapshot(
                CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1), new Focus(0, 1, 0.5)),
                1.5, 4, 8, new Viewport(-2, 2, -2, 2),
                true, true, true, false, true, 1);

        final String svg = write(snapshot, 200, 150);
        final Document document = parse(svg);

        assertEquals("svg", document.getDocumentElement().getTagName());
        assertEquals("200", document.getDocumentElement().getAttribute("width"));
        assertEquals("150", document.getDocumentElement().getAttribute("height"));
        assertTrue(document.getElementsByTagName("path").getLength() > 0);
        // Three focus markers plus the two extrema markers.
        assertEquals(5, document.getElementsByTagName("circle").getLength());
        // Both axes cross the viewport; every other line belongs to legend swatches.
        assertEquals(2 + document.getElementsByTagName("text").getLength(),
                document.getElementsByTagName("line").getLength());
        assertEquals(7, document.getElementsByTagName("text").getLength());
        assertTrue(svg.contains(">4.000<"));
        assertTrue(svg.contains(">1.500<"));
        assertTrue(svg.contains(SvgPlotWriterTest.color(PlotRenderer.curveColor(0, 8))));
    }

    @Test
    void legendAndExtremaAreOmittedWhenDisabled() throws Exception {
        final PlotSnapshot snapshot = new PlotSnapshot(
                CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                1.5, 4, 6, new Viewport(-2, 2, -2, 2),
                true, false, true, false, false, -1);

        final String svg = write(snapshot, 160, 120);
        final Document document = parse(svg);

        assertEquals(0, document.getElementsByTagName("text").getLength());
        assertEquals(2, document.getElementsByTagName("circle").getLength());
        assertEquals(2, document.getElementsByTagName("line").getLength());
    }

    @Test
    void fieldWithoutFiniteSamplesStillProducesAValidEmptyPlot() throws Exception {
        final PlotSnapshot snapshot = new PlotSnapshot(
                CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(Double.MAX_VALUE, 0, Double.MAX_VALUE)),
                0, 1, 4, new Viewport(-1, 1, -1, 1),
                true, true, true, false, true, -1);

        final String svg = write(snapshot, 80, 60);
        final Document document = parse(svg);

        assertEquals(0, document.getElementsByTagName("path").getLength());
        assertEquals(0, document.getElementsByTagName("text").getLength());
        assertFalse(svg.isBlank());
    }


    @Test
    void legendRowsAreReducedToFitTheSvgHeight() throws Exception {
        final PlotSnapshot snapshot = new PlotSnapshot(
                CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(-1, 0, 1), new Focus(1, 0, 1)),
                1, 4, 12, new Viewport(-2, 2, -2, 2),
                false, false, true, false, true, -1);

        final Document document = parse(write(snapshot, 160, 60));

        assertTrue(document.getElementsByTagName("text").getLength() <= 1);
    }

    @Test
    void rejectsDegenerateSizes() {
        final PlotSnapshot snapshot = new PlotSnapshot(
                CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(0, 0, 1)),
                0, 1, 3, new Viewport(-1, 1, -1, 1),
                true, true, true, false, true, -1);

        assertThrows(IllegalArgumentException.class,
                () -> new RenderRequest(snapshot, 1, 100, RenderQuality.FULL));
        assertThrows(IllegalArgumentException.class,
                () -> SvgPlotWriter.write(null));
    }


    private static String write(final PlotSnapshot snapshot, final int width, final int height) {
        final RenderResult result = new PlotRenderer().render(
                new RenderRequest(snapshot, width, height, RenderQuality.FULL),
                CancellationToken.NONE);
        return SvgPlotWriter.write(result.renderPackage().orElseThrow());
    }

    private static Document parse(final String svg) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static String color(final java.awt.Color value) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                value.getRed(), value.getGreen(), value.getBlue());
    }
}
