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

    @Test
    void cleanupFailureDoesNotHideTheWriterFailure() throws Exception {
        final Path target = temporaryDirectory.resolve("plot.txt");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        final IOException failure = assertThrows(IOException.class,
                () -> AtomicFiles.replace(target, temporary -> {
                    Files.delete(temporary);
                    Files.createDirectory(temporary);
                    Files.writeString(temporary.resolve("child"), "partial",
                            StandardCharsets.UTF_8);
                    throw new IOException("primary writer failure");
                }));

        assertEquals("primary writer failure", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void longTargetNamesStillSave() throws Exception {
        // 244 characters is a valid name; the whole of it in the temporary
        // file's name, plus the random suffix, was not
        final Path target = temporaryDirectory.resolve("a".repeat(240) + ".txt");

        AtomicFiles.writeString(target, "saved", StandardCharsets.UTF_8);

        assertEquals("saved", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(hasTemporarySibling(target));
    }

    private static boolean hasTemporarySibling(final Path target) throws IOException {
        try (Stream<Path> siblings = Files.list(target.toAbsolutePath().getParent())) {
            return siblings.anyMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }
}
