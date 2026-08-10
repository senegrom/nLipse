package nlipse.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import nlipse.render.Viewport;

class PlotConfigIOTest {
    @TempDir
    Path tempDirectory;

    @Test
    void setupSurvivesASaveLoadRoundTripExactly() throws Exception {
        final PlotConfig original = new PlotConfig(
                CurveType.CASSIN,
                List.of(new Focus(-1.25, 0.5, 1), new Focus(1.75, -2.125, 0.375)),
                0.0625, 17.5, 42,
                new Viewport(-4.5, 3.25, -2.75, 5.5),
                false, true, false, true);
        final Path file = tempDirectory.resolve("setup.properties");

        PlotConfigIO.save(file, new PlotModel(original).snapshot());

        assertEquals(original, PlotConfigIO.load(file));
    }

    @Test
    void loadReportsTheOffendingKey() throws Exception {
        final Path file = tempDirectory.resolve("broken.properties");
        Files.writeString(file, """
                format=1
                curveType=CASSIN
                focus.count=1
                focus.0.x=not-a-number
                """);
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PlotConfigIO.load(file));
        assertTrue(failure.getMessage().contains("focus.0.x"));
    }

    @Test
    void unknownFormatVersionIsRejected() throws Exception {
        final Path file = tempDirectory.resolve("future.properties");
        Files.writeString(file, "format=99\n");
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PlotConfigIO.load(file));
        assertTrue(failure.getMessage().contains("format 99"));
    }

    @Test
    void applyReplacesModelStateAndClearsSelection() {
        final PlotModel model = new PlotModel(PlotConfig.defaults());
        model.setSelectedFocusIndex(1);
        final PlotConfig loaded = new PlotConfig(
                CurveType.HYPERB,
                List.of(new Focus(9, 9, 2)),
                1, 2, 3,
                new Viewport(-1, 1, -1, 1),
                false, false, false, false);

        model.apply(loaded);

        assertEquals(loaded, new PlotConfig(model.getCurveType(), model.getFociCopy(),
                model.getDistanceMin(), model.getDistanceMax(), model.getCurveCount(),
                model.getViewport(), model.isShowBackground(), model.isShowExtrema(),
                model.isAntiAlias(), model.isLogSpacing()));
        assertEquals(-1, model.getSelectedFocusIndex());
        model.resetViewport();
        assertEquals(loaded.viewport(), model.getViewport());
    }
    @Test
    void everyCurveFamilySurvivesPersistence() throws Exception {
        for (final CurveType type : CurveType.values()) {
            final PlotConfig config = new PlotConfig(type,
                    List.of(new Focus(0, 0, 1)),
                    -2, 3, 5, new Viewport(-1, 1, -1, 1),
                    true, true, true, type.defaultLogSpacing());
            final Path file = tempDirectory.resolve(type.name() + ".properties");

            PlotConfigIO.save(file, new PlotModel(config).snapshot());

            assertEquals(config, PlotConfigIO.load(file));
        }
    }

    @Test
    void invalidCurveFamilyReportsAllValidNames() throws Exception {
        final Path file = tempDirectory.resolve("unknown-family.properties");
        final PlotConfig config = PlotConfig.defaults();
        PlotConfigIO.save(file, new PlotModel(config).snapshot());
        Files.writeString(file, Files.readString(file)
                .replace("curveType=LIPSE", "curveType=UNKNOWN"));

        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PlotConfigIO.load(file));
        for (final CurveType type : CurveType.values()) {
            assertTrue(failure.getMessage().contains(type.name()));
        }
    }

}
