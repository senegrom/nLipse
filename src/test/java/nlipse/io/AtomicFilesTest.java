package nlipse.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
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

    /**
     * On POSIX systems a new file gets the umask's permissions and a replaced one
     * keeps its own: the owner-only temporary file made every save private.
     */
    @Test
    void savedFilesKeepOrdinaryPermissions() throws Exception {
        assumeTrue(temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        final Path control = Files.createFile(temporaryDirectory.resolve("control.txt"));
        final Path created = temporaryDirectory.resolve("created.txt");
        AtomicFiles.writeString(created, "new", StandardCharsets.UTF_8);
        assertEquals(Files.getPosixFilePermissions(control), Files.getPosixFilePermissions(created));

        final Path shared = temporaryDirectory.resolve("shared.txt");
        Files.writeString(shared, "old", StandardCharsets.UTF_8);
        final Set<PosixFilePermission> groupWritable = PosixFilePermissions.fromString("rw-rw-r--");
        Files.setPosixFilePermissions(shared, groupWritable);
        AtomicFiles.writeString(shared, "new", StandardCharsets.UTF_8);
        assertEquals(groupWritable, Files.getPosixFilePermissions(shared));
    }

    /**
     * Replacing a file is up to its directory, so a read-only target is replaced
     * and stays read-only. Its mode used to reach the temporary file before the
     * writer opened it, which then failed.
     */
    @Test
    void readOnlyTargetsAreReplacedAndStayReadOnly() throws Exception {
        assumeTrue(temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        final Path target = temporaryDirectory.resolve("locked.txt");
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        final Set<PosixFilePermission> readOnly = PosixFilePermissions.fromString("r--r--r--");
        Files.setPosixFilePermissions(target, readOnly);

        AtomicFiles.writeString(target, "new", StandardCharsets.UTF_8);

        assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(readOnly, Files.getPosixFilePermissions(target));
        assertFalse(hasTemporarySibling(target));
    }

    /**
     * Closing the window interrupts an export, and a writer may finish without
     * noticing, as Files.writeString does: the move then replaced the file that
     * "Close now" promised to keep.
     */
    @Test
    void interruptedWritesKeepTheOldDestination() throws Exception {
        final Path target = temporaryDirectory.resolve("plot.svg");
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        try {
            assertThrows(InterruptedIOException.class, () -> AtomicFiles.replace(target, temporary -> {
                Files.writeString(temporary, "new", StandardCharsets.UTF_8);
                Thread.currentThread().interrupt();
            }));
        } finally {
            Thread.interrupted();
        }

        assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(hasTemporarySibling(target));
    }

    private static boolean hasTemporarySibling(final Path target) throws IOException {
        try (Stream<Path> siblings = Files.list(target.toAbsolutePath().getParent())) {
            return siblings.anyMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }
}
