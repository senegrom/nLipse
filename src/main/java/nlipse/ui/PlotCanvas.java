package nlipse.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import nlipse.render.RenderResult;

/** Resizable image surface. Expensive plot construction happens elsewhere. */
public final class PlotCanvas extends JComponent {
    private static final long serialVersionUID = 1L;

    private transient BufferedImage image;
    private boolean rendering;
    private String message = "Preparing plot…";

    public PlotCanvas() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setFocusable(true);
        setPreferredSize(new Dimension(760, 760));
        setMinimumSize(new Dimension(240, 240));
    }

    public void setRenderResult(final RenderResult result) {
        image = result.getImage();
        rendering = false;
        message = "";
        repaint();
    }

    public void setRendering(final boolean value) {
        rendering = value;
        repaint();
    }

    public void setMessage(final String newMessage) {
        message = newMessage == null ? "" : newMessage;
        rendering = false;
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (image != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
            if (image == null && !message.isEmpty()) {
                drawCentredMessage(g, message);
            }
            if (rendering) {
                drawRenderingBadge(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawCentredMessage(final Graphics2D g, final String text) {
        g.setColor(new Color(75, 75, 75));
        final FontMetrics metrics = g.getFontMetrics();
        final int x = Math.max(8, (getWidth() - metrics.stringWidth(text)) / 2);
        final int y = Math.max(metrics.getAscent() + 8,
                (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
        g.drawString(text, x, y);
    }

    private void drawRenderingBadge(final Graphics2D g) {
        final String text = "Rendering…";
        final FontMetrics metrics = g.getFontMetrics();
        final int width = metrics.stringWidth(text) + 16;
        final int height = metrics.getHeight() + 8;
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRoundRect(10, 10, width, height, 10, 10);
        g.setColor(new Color(55, 55, 55));
        g.drawRoundRect(10, 10, width, height, 10, 10);
        g.drawString(text, 18, 14 + metrics.getAscent());
    }
}
