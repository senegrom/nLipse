package nlipse.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import nlipse.render.Viewport;

/** Saves and loads a complete plot setup as a properties file. */
public final class PlotConfigIO {
    private static final int FORMAT_VERSION = 1;

    private PlotConfigIO() {
    }

    /** Where the closing window drops its setup so the next start can offer it. */
    public static Path lastSessionFile() {
        return Path.of(System.getProperty("user.home"), ".nlipse-session.properties");
    }

    public static void save(final Path file, final PlotSnapshot snapshot) throws IOException {
        final Properties values = new Properties();
        values.setProperty("format", Integer.toString(FORMAT_VERSION));
        values.setProperty("curveType", snapshot.curveType().name());
        values.setProperty("familyParameter", Double.toString(snapshot.familyParameter()));
        values.setProperty("focus.count", Integer.toString(snapshot.foci().size()));
        for (int index = 0; index < snapshot.foci().size(); index++) {
            final Focus focus = snapshot.foci().get(index);
            values.setProperty("focus." + index + ".x", Double.toString(focus.x()));
            values.setProperty("focus." + index + ".y", Double.toString(focus.y()));
            values.setProperty("focus." + index + ".weight", Double.toString(focus.weight()));
        }
        values.setProperty("distanceMin", Double.toString(snapshot.distanceMin()));
        values.setProperty("distanceMax", Double.toString(snapshot.distanceMax()));
        values.setProperty("curveCount", Integer.toString(snapshot.curveCount()));
        values.setProperty("viewport.xMin", Double.toString(snapshot.viewport().xMin()));
        values.setProperty("viewport.xMax", Double.toString(snapshot.viewport().xMax()));
        values.setProperty("viewport.yMin", Double.toString(snapshot.viewport().yMin()));
        values.setProperty("viewport.yMax", Double.toString(snapshot.viewport().yMax()));
        values.setProperty("showBackground", Boolean.toString(snapshot.showBackground()));
        values.setProperty("showExtrema", Boolean.toString(snapshot.showExtrema()));
        values.setProperty("antiAlias", Boolean.toString(snapshot.antiAlias()));
        values.setProperty("logSpacing", Boolean.toString(snapshot.logSpacing()));
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            values.store(writer, "nLipse plot setup");
        }
    }

    /** Load and fully validate a setup; throws with a readable message on any defect. */
    public static PlotConfig load(final Path file) throws IOException {
        final Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        final int format = intValue(values, "format");
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException(file + " uses setup format " + format
                    + "; this nLipse reads format " + FORMAT_VERSION);
        }
        final CurveType curveType;
        try {
            curveType = CurveType.valueOf(required(values, "curveType"));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("curveType must be one of " + CurveType.validNames());
        }
        final int focusCount = intValue(values, "focus.count");
        if (focusCount < 1 || focusCount > 1_000) {
            throw new IllegalArgumentException("focus.count must be between 1 and 1000");
        }
        final List<Focus> foci = new ArrayList<>(focusCount);
        for (int index = 0; index < focusCount; index++) {
            foci.add(new Focus(
                    doubleValue(values, "focus." + index + ".x"),
                    doubleValue(values, "focus." + index + ".y"),
                    doubleValue(values, "focus." + index + ".weight")));
        }
        return new PlotConfig(
                curveType,
                parameterValue(values, curveType),
                foci,
                doubleValue(values, "distanceMin"),
                doubleValue(values, "distanceMax"),
                intValue(values, "curveCount"),
                new Viewport(
                        doubleValue(values, "viewport.xMin"),
                        doubleValue(values, "viewport.xMax"),
                        doubleValue(values, "viewport.yMin"),
                        doubleValue(values, "viewport.yMax")),
                booleanValue(values, "showBackground"),
                booleanValue(values, "showExtrema"),
                booleanValue(values, "antiAlias"),
                booleanValue(values, "logSpacing"));
    }

    private static double parameterValue(final Properties values, final CurveType curveType) {
        final String raw = values.getProperty("familyParameter");
        if (raw == null || raw.isBlank()) {
            return curveType.defaultParameter();
        }
        try {
            return curveType.parseParameter(raw);
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("'familyParameter': " + invalid.getMessage());
        }
    }

    private static String required(final Properties values, final String key) {
        final String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("setup file is missing '" + key + "'");
        }
        return value.trim();
    }

    private static int intValue(final Properties values, final String key) {
        try {
            return Integer.parseInt(required(values, key));
        } catch (final NumberFormatException invalid) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
    }

    private static double doubleValue(final Properties values, final String key) {
        final double value;
        try {
            value = Double.parseDouble(required(values, key));
        } catch (final NumberFormatException invalid) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("'" + key + "' must be finite");
        }
        return value;
    }

    private static boolean booleanValue(final Properties values, final String key) {
        final String value = required(values, key);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("'" + key + "' must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
