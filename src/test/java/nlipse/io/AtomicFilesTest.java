package nlipse.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void replacesTheDestinationOnlyAfterTheWriterCompletes() throws Exception {
        final Path target = temporaryDirectory.resolve("plot.txt");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        AtomicFiles.writeString(target, "new", StandardCharsets.UTF_8);

        assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(hasTemporarySibling(target));
    }

    @Test
    void failedWritersLeaveTheOldDestinationAndNoTemporaryFile() throws Exception {
        final Path target = temporaryDirectory.resolve("plot.txt");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> AtomicFiles.replace(target, temporary -> {
            Files.writeString(temporary, "partial", StandardCharsets.UTF_8);
            throw new IOException("simulated failure");
        }));

        assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(hasTemporarySibling(target));
    }

    private static boolean hasTemporarySibling(final Path target) throws IOException {
        try (Stream<Path> siblings = Files.list(target.toAbsolutePath().getParent())) {
            final String prefix = "." + target.getFileName() + '-';
            return siblings.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }
}
