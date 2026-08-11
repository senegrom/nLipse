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
                CurveType.CASSIN, CurveType.CASSIN.defaultParameter(),
                List.of(new Focus(-1.25, 0.5, 1), new Focus(1.75, -2.125, 0.375)),
                0.0625, 17.5, 42,
                new Viewport(-4.5, 3.25, -2.75, 5.5),
                false, true, false, true, false);
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
                CurveType.HYPERB, CurveType.HYPERB.defaultParameter(),
                List.of(new Focus(9, 9, 2)),
                1, 2, 3,
                new Viewport(-1, 1, -1, 1),
                false, false, false, false, false);

        model.apply(loaded);

        assertEquals(loaded, new PlotConfig(model.getCurveType(), model.getFamilyParameter(),
                model.getFociCopy(), model.getDistanceMin(), model.getDistanceMax(),
                model.getCurveCount(),
                model.getViewport(), model.isShowBackground(), model.isShowExtrema(),
                model.isAntiAlias(), model.isLogSpacing(), model.isShowLegend()));
        assertEquals(-1, model.getSelectedFocusIndex());
        model.resetViewport();
        assertEquals(loaded.viewport(), model.getViewport());
    }
    @Test
    void everyCurveFamilySurvivesPersistence() throws Exception {
        for (final CurveType type : CurveType.values()) {
            final PlotConfig config = new PlotConfig(type, type.defaultParameter(),
                    List.of(new Focus(0, 0, 1)),
                    -2, 3, 5, new Viewport(-1, 1, -1, 1),
                    true, true, true, type.defaultLogSpacing(), true);
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


    @Test
    void parameterizedFamilyAndInfinitySurvivePersistence() throws Exception {
        final PlotConfig config = new PlotConfig(CurveType.POWER_MEAN,
                Double.POSITIVE_INFINITY,
                List.of(new Focus(0, 0, 1), new Focus(1, 0, 2)),
                0, 5, 7, new Viewport(-2, 2, -2, 2),
                true, false, true, false, true);
        final Path file = tempDirectory.resolve("power-mean.properties");

        PlotConfigIO.save(file, new PlotModel(config).snapshot());

        assertEquals(config, PlotConfigIO.load(file));
        assertTrue(Files.readString(file).contains("familyParameter=Infinity"));
    }

    @Test
    void powerParameterAcceptsHumanReadableInfinityInSetupFiles() throws Exception {
        final PlotConfig source = new PlotConfig(CurveType.POWER_MEAN, 1,
                List.of(new Focus(0, 0, 1)),
                0, 2, 5, new Viewport(-1, 1, -1, 1),
                true, true, true, false, true);
        final Path file = tempDirectory.resolve("unicode-infinity.properties");
        PlotConfigIO.save(file, new PlotModel(source).snapshot());
        Files.writeString(file, Files.readString(file)
                .replace("familyParameter=1.0", "familyParameter=−∞"));

        assertEquals(Double.NEGATIVE_INFINITY, PlotConfigIO.load(file).familyParameter());
    }

    @Test
    void oldSetupWithoutFamilyParameterUsesTheFamilyDefault() throws Exception {
        final PlotConfig source = new PlotConfig(CurveType.GAUSSIAN,
                CurveType.GAUSSIAN.defaultParameter(),
                List.of(new Focus(0, 0, 1)),
                -1, 1, 5, new Viewport(-1, 1, -1, 1),
                true, true, true, false, true);
        final Path file = tempDirectory.resolve("old-setup.properties");
        PlotConfigIO.save(file, new PlotModel(source).snapshot());
        Files.writeString(file, Files.readString(file)
                .replaceAll("(?m)^familyParameter=.*\\R", ""));

        assertEquals(CurveType.GAUSSIAN.defaultParameter(),
                PlotConfigIO.load(file).familyParameter(), 0);
    }

    @Test
    void oldSetupWithoutShowLegendDefaultsToVisible() throws Exception {
        final PlotConfig source = new PlotConfig(
                CurveType.LIPSE, CurveType.LIPSE.defaultParameter(),
                List.of(new Focus(0, 0, 1)),
                0, 2, 5, new Viewport(-1, 1, -1, 1),
                true, true, true, false, false);
        final Path file = tempDirectory.resolve("pre-legend.properties");
        PlotConfigIO.save(file, new PlotModel(source).snapshot());
        Files.writeString(file, Files.readString(file)
                .replaceAll("(?m)^showLegend=.*\\R", ""));

        assertTrue(PlotConfigIO.load(file).showLegend());
    }

    @Test
    void invalidFamilyParameterNamesTheOffendingKey() throws Exception {
        final PlotConfig source = new PlotConfig(CurveType.GAUSSIAN,
                CurveType.GAUSSIAN.defaultParameter(), List.of(new Focus(0, 0, 1)),
                -1, 1, 5, new Viewport(-1, 1, -1, 1),
                true, true, true, false, true);
        final Path file = tempDirectory.resolve("bad-parameter.properties");
        PlotConfigIO.save(file, new PlotModel(source).snapshot());
        Files.writeString(file, Files.readString(file)
                .replace("familyParameter=1.0", "familyParameter=0"));

        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PlotConfigIO.load(file));
        assertTrue(failure.getMessage().contains("familyParameter"));
    }

}
