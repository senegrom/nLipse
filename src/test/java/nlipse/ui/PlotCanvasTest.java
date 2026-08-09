package nlipse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import nlipse.render.RenderQuality;
import nlipse.render.RenderResult;

class PlotCanvasTest {
    @Test
    void panPreviewTranslatesTheLastImageAndLeavesExposedPixelsBlank() {
        final BufferedImage source = new BufferedImage(5, 3, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D sourceGraphics = source.createGraphics();
        try {
            sourceGraphics.setColor(Color.WHITE);
            sourceGraphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            source.setRGB(1, 1, Color.RED.getRGB());
        } finally {
            sourceGraphics.dispose();
        }
        final PlotCanvas canvas = new PlotCanvas();
        canvas.setSize(5, 3);
        canvas.setRenderResult(new RenderResult(source, 1, RenderQuality.FULL,
                Optional.empty(), 1));

        assertTrue(canvas.beginPanPreview());
        canvas.updatePanPreview(2, 0);
        final BufferedImage preview = paint(canvas);

        assertEquals(Color.RED.getRGB(), preview.getRGB(3, 1));
        assertEquals(Color.WHITE.getRGB(), preview.getRGB(0, 1));

        canvas.commitPanPreview();
        assertEquals(Color.RED.getRGB(), canvas.image().getRGB(3, 1));
        assertEquals(Color.WHITE.getRGB(), canvas.image().getRGB(0, 1));
    }

    private static BufferedImage paint(final PlotCanvas canvas) {
        final BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
