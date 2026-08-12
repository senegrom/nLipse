package nlipse.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;
import nlipse.render.RenderPackage;
import nlipse.render.RenderResult;
import nlipse.render.SvgPlotWriter;

/** Encodes completed immutable render results through atomic file replacement. */
public final class PlotExports {
    private PlotExports() {
    }

    public static void writePng(final RenderResult result, final Path target) throws IOException {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(target, "target");
        AtomicFiles.replace(target, temporary -> {
            if (!ImageIO.write(result.image(), "png", temporary.toFile())) {
                throw new IOException("No PNG writer is installed");
            }
        });
    }

    public static void writeSvg(final RenderResult result, final Path target) throws IOException {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(target, "target");
        final RenderPackage completed = result.renderPackage().orElseThrow(
                () -> new IOException("Renderer did not produce an export package"));
        AtomicFiles.writeString(target, SvgPlotWriter.write(completed), StandardCharsets.UTF_8);
    }
}
