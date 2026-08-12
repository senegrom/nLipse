package nlipse.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void displaysManifestVersionsWithoutSnapshotNoise() {
        assertEquals("0.11.4", Main.displayVersion("0.11.4-SNAPSHOT"));
        assertEquals("0.11.4", Main.displayVersion("0.11.4"));
        assertEquals("development", Main.displayVersion(null));
        assertEquals("development", Main.displayVersion("  "));
    }
}
