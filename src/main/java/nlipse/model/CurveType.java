package nlipse.model;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Available scalar fields built from weighted focal distances. */
public enum CurveType {
    LIPSE(
            "n-Ellipse (sum)",
            false,
            "F = Σ wᵢdᵢ",
            "Signed weighted sum of focal distances."),
    CASSIN(
            "Cassini family (product)",
            true,
            "F = ∏ dᵢ ^ wᵢ",
            "Weighted product of focal distances; negative weights form distance ratios."),
    HYPERB(
            "n-Hyperbola (mean difference)",
            false,
            "F = meanᵢ<ⱼ |wᵢdᵢ − wⱼdⱼ|",
            "Mean pairwise absolute difference between signed weighted focal distances."),
    NEAREST(
            "Nearest-focus envelope",
            false,
            "F = min |wᵢ|dᵢ",
            "Nearest active weighted focus. Weight magnitude scales distance; zero weight disables a focus."),
    FARTHEST(
            "Farthest-focus envelope",
            false,
            "F = max |wᵢ|dᵢ",
            "Farthest active weighted focus. Weight magnitude scales distance; zero weight disables a focus."),
    QUADRATIC(
            "Quadratic n-Ellipse (L2)",
            false,
            "F = √Σ (|wᵢ|dᵢ)²",
            "Root-sum-square of active weighted focal distances."),
    RANGE(
            "Weighted-distance range",
            false,
            "F = max |wᵢ|dᵢ − min |wᵢ|dᵢ",
            "Span between the nearest and farthest active weighted focal distances."),
    POTENTIAL(
            "Inverse-distance potential",
            false,
            "F = Σ wᵢ / dᵢ",
            "Signed point-source potential. Positive and negative weights act as sources and sinks."),
    ;

    private final String displayName;
    private final boolean defaultLogSpacing;
    private final String formula;
    private final String description;

    CurveType(final String displayName, final boolean defaultLogSpacing,
            final String formula, final String description) {
        this.displayName = displayName;
        this.defaultLogSpacing = defaultLogSpacing;
        this.formula = formula;
        this.description = description;
    }

    public boolean defaultLogSpacing() {
        return defaultLogSpacing;
    }

    public String formula() {
        return formula;
    }

    public String description() {
        return description;
    }

    public String htmlDescription(final int widthPixels) {
        if (widthPixels < 1) {
            throw new IllegalArgumentException("Description width must be positive");
        }
        return "<html><div style='width:" + widthPixels + "px'><b>" + escapeHtml(formula)
                + "</b><br>" + escapeHtml(description) + "</div></html>";
    }

    private static String escapeHtml(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String validNames() {
        return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return displayName;
    }
}
