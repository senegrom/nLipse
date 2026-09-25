package nlipse.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Same-directory temporary writes followed by atomic destination replacement where supported. */
public final class AtomicFiles {
    /** Code points of the target's name that the temporary file's name keeps. */
    private static final int TEMPORARY_NAME_STEM = 40;

    @FunctionalInterface
    public interface TemporaryWriter {
        void write(Path temporary) throws IOException;
    }

    private AtomicFiles() {
    }

    public static void writeString(final Path target, final CharSequence content,
            final Charset charset) throws IOException {
        replace(target, temporary -> Files.writeString(temporary, content, charset));
    }

    public static void replace(final Path target, final TemporaryWriter writer)
            throws IOException {
        if (target == null || writer == null) {
            throw new IllegalArgumentException("Target and writer are required");
        }
        final Path absolute = target.toAbsolutePath();
        final Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("Target file has no parent directory: " + target);
        }
        // The temporary name starts like the target's, but not with all of it: a
        // long name plus the random suffix would pass the 255-character limit
        final String stem = absolute.getFileName().toString().codePoints()
                .limit(TEMPORARY_NAME_STEM)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        final Path temporary = Files.createTempFile(parent, "." + stem + '-', ".tmp");
        boolean moved = false;
        Throwable primaryFailure = null;
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, absolute,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } catch (final IOException | RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (final IOException | RuntimeException cleanupFailure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }
}
