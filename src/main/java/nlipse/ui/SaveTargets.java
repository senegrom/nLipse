package nlipse.ui;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Resolve the actual destination before asking whether it may be replaced. */
final class SaveTargets {
    private SaveTargets() {
    }

    static Optional<Path> approve(final Path selected, final String extension,
            final Predicate<Path> confirmReplacement) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(confirmReplacement, "confirmReplacement");
        if (selected.getFileName() == null || selected.getFileName().toString().isBlank()) {
            throw new IllegalArgumentException("Choose a file name, not a directory.");
        }
        if (!extension.isEmpty() && !extension.startsWith(".")) {
            throw new IllegalArgumentException("File extension must start with a dot");
        }
        final String name = selected.getFileName().toString();
        final Path target = name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))
                ? selected : selected.resolveSibling(name + extension);
        if (Files.isDirectory(target)) {
            throw new IllegalArgumentException("The destination is a directory: " + target);
        }
        // Also confirm replacement of a dangling symlink, not just a regular file.
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !confirmReplacement.test(target)) {
            return Optional.empty();
        }
        return Optional.of(target);
    }
}
