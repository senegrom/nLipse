package nlipse.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import nlipse.render.RenderPackage;
import nlipse.render.RenderQuality;
import nlipse.render.RenderResult;
import nlipse.render.SvgPlotWriter;

/** Encodes completed immutable render results through atomic file replacement. */
public final class PlotExports {
    private PlotExports() {
    }

    public static void writePng(final RenderResult result, final Path target) throws IOException {
        requireExportable(result);
        Objects.requireNonNull(target, "target");
        AtomicFiles.replace(target, temporary -> {
            // A FileChannel stops at an interrupt, unlike ImageIO's own file output
            // and the stream of Files.newOutputStream: closing the window during an
            // export ends the write and removes the temporary file. The in-memory
            // cache avoids ImageIO's cache file in the system temp directory.
            try (OutputStream output = Channels.newOutputStream(
                            FileChannel.open(temporary, StandardOpenOption.WRITE));
                    ImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
                if (!ImageIO.write(result.image(), "png", imageOutput)) {
                    throw new IOException("No PNG writer is installed");
                }
            }
        });
    }

    public static void writeSvg(final RenderResult result, final Path target) throws IOException {
        requireExportable(result);
        Objects.requireNonNull(target, "target");
        final RenderPackage completed = result.renderPackage().orElseThrow(
                () -> new IOException("Renderer did not produce an export package"));
        AtomicFiles.writeString(target, SvgPlotWriter.write(completed), StandardCharsets.UTF_8);
    }

    private static void requireExportable(final RenderResult result) throws IOException {
        Objects.requireNonNull(result, "result");
        if (result.quality() != RenderQuality.FULL || result.precisionLimited()) {
            throw new IOException("Export requires an exact full-quality render");
        }
    }
}
