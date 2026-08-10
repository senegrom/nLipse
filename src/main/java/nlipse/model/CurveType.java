package nlipse.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Available scalar fields built from weighted focal distances. */
public enum CurveType {
    LIPSE(
            "n-Ellipse (sum)",
            false,
            "F = Σ wᵢdᵢ",
            "Adds signed weighted focal distances. Positive weights add distance and negative weights subtract it, so level sets may be open, disconnected or self-interacting.",
            "The sign and magnitude of every weight are used directly."),
    CASSIN(
            "Cassini family (product)",
            true,
            "F = ∏ dᵢ ^ wᵢ",
            "Multiplies powers of focal distances. Equal positive weights give classical Cassini-style ovals; mixed signs form distance ratios and Apollonius-type loci.",
            "Each weight is an exponent; zero disables a focus and a negative value puts that distance in the denominator."),
    HYPERB(
            "n-Hyperbola (mean difference)",
            false,
            "F = meanᵢ<ⱼ |wᵢdᵢ − wⱼdⱼ|",
            "Measures the mean pairwise disagreement between signed weighted focal distances. Two foci recover the absolute distance-difference family behind ordinary hyperbolas.",
            "The sign and magnitude of every weight are used before pairwise differences are taken."),
    NEAREST(
            "Nearest-focus envelope",
            false,
            "F = min zᵢ,  zᵢ = |wᵢ|dᵢ",
            "Takes the nearest active weighted focus. Its governing focus changes along multiplicatively weighted Voronoi boundaries, creating piecewise-smooth envelopes.",
            "Weight magnitude rescales distance, weight sign is ignored and zero disables a focus."),
    FARTHEST(
            "Farthest-focus envelope",
            false,
            "F = max zᵢ,  zᵢ = |wᵢ|dᵢ",
            "Takes the farthest active weighted focus. Sublevel regions are intersections of weighted discs and often have a rounded convex outer boundary.",
            "Weight magnitude rescales distance, weight sign is ignored and zero disables a focus."),
    QUADRATIC(
            "Quadratic n-Ellipse (L2 norm)",
            false,
            "F = √Σ zᵢ²,  zᵢ = |wᵢ|dᵢ",
            "Combines active weighted distances with a Euclidean norm. It is smoother than the ordinary sum and gives more influence to the larger distances.",
            "Weight magnitude rescales distance, weight sign is ignored and zero disables a focus."),
    RANGE(
            "Weighted-distance range",
            false,
            "F = max zᵢ − min zᵢ",
            "Measures the spread between the nearest and farthest active weighted distances. Small values mark places where all active foci are similarly distant.",
            "Here zᵢ = |wᵢ|dᵢ; weight sign is ignored and zero disables a focus."),
    POTENTIAL(
            "Inverse-distance potential",
            false,
            "F = Σ wᵢ / dᵢ",
            "Models signed point sources with inverse-distance decay. Positive and negative sources can cancel, while active foci are true singularities.",
            "Positive weights are sources, negative weights are sinks and zero disables a focus."),
    POWER_MEAN(
            "Generalised power mean",
            false,
            "Mₚ = (mean zᵢᵖ)¹⁄ᵖ",
            "Interpolates continuously between familiar distance aggregates. p=1 is the arithmetic mean, p=2 is the RMS distance, p=0 is the geometric mean and p=±∞ gives the exact envelopes.",
            "Here zᵢ = |wᵢ|dᵢ; weight sign is ignored and zero disables a focus.",
            ParameterDomain.POWER,
            "p",
            1,
            "Any real number is accepted, including 0, +∞ and −∞. Integer text such as 2 and decimal text such as 0.5 are both valid."),
    MEDIAN(
            "Median weighted distance",
            false,
            "F = medianᵢ zᵢ",
            "Uses the middle active weighted distance, averaging the two central values when their count is even. It is robust to one unusually near or far focus.",
            "Here zᵢ = |wᵢ|dᵢ; weight sign is ignored and zero disables a focus."),
    SMOOTH_NEAREST(
            "Smooth nearest-focus envelope",
            false,
            "F = −τ ln(mean exp(−zᵢ/τ))",
            "A differentiable soft minimum of the active weighted distances. Small τ closely follows the exact nearest envelope; larger τ blends neighbouring focus regions more broadly.",
            "Here zᵢ = |wᵢ|dᵢ; weight sign is ignored and zero disables a focus.",
            ParameterDomain.POSITIVE_FINITE,
            "τ",
            0.5,
            "Temperature controlling the blend width; it must be a finite number greater than zero."),
    SMOOTH_FARTHEST(
            "Smooth farthest-focus envelope",
            false,
            "F = τ ln(mean exp(zᵢ/τ))",
            "A differentiable soft maximum of the active weighted distances. Small τ closely follows the exact farthest envelope; larger τ blends the contributors.",
            "Here zᵢ = |wᵢ|dᵢ; weight sign is ignored and zero disables a focus.",
            ParameterDomain.POSITIVE_FINITE,
            "τ",
            0.5,
            "Temperature controlling the blend width; it must be a finite number greater than zero."),
    GAUSSIAN(
            "Gaussian radial-basis field",
            false,
            "F = Σ wᵢ exp(−dᵢ² / (2σ²))",
            "Adds smooth localised bumps and wells around the foci. Overlapping positive and negative contributions create blobs, saddles and nodal contours.",
            "Weight sign controls whether a focus is a peak or a well; magnitude controls amplitude and zero disables it.",
            ParameterDomain.POSITIVE_FINITE,
            "σ",
            1,
            "Global Gaussian width; it must be a finite number greater than zero."),
    ;

    private enum ParameterDomain {
        NONE,
        POWER,
        POSITIVE_FINITE
    }

    private final String displayName;
    private final boolean defaultLogSpacing;
    private final String formula;
    private final String description;
    private final String weightSemantics;
    private final ParameterDomain parameterDomain;
    private final String parameterLabel;
    private final double defaultParameter;
    private final String parameterDescription;

    CurveType(final String displayName, final boolean defaultLogSpacing,
            final String formula, final String description, final String weightSemantics) {
        this(displayName, defaultLogSpacing, formula, description, weightSemantics,
                ParameterDomain.NONE, "", 1, "");
    }

    CurveType(final String displayName, final boolean defaultLogSpacing,
            final String formula, final String description, final String weightSemantics,
            final ParameterDomain parameterDomain, final String parameterLabel,
            final double defaultParameter, final String parameterDescription) {
        this.displayName = displayName;
        this.defaultLogSpacing = defaultLogSpacing;
        this.formula = formula;
        this.description = description;
        this.weightSemantics = weightSemantics;
        this.parameterDomain = parameterDomain;
        this.parameterLabel = parameterLabel;
        this.defaultParameter = defaultParameter;
        this.parameterDescription = parameterDescription;
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

    public String weightSemantics() {
        return weightSemantics;
    }

    public boolean usesParameter() {
        return parameterDomain != ParameterDomain.NONE;
    }

    public String parameterLabel() {
        return parameterLabel;
    }

    public String parameterDescription() {
        return parameterDescription;
    }

    public double defaultParameter() {
        return defaultParameter;
    }

    /** Canonicalise and validate the one global parameter used by parameterised families. */
    public double normalizeParameter(final double value) {
        return switch (parameterDomain) {
            case NONE -> defaultParameter;
            case POWER -> {
                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Power p must be a real number or ±infinity");
                }
                yield value == 0 ? 0 : value;
            }
            case POSITIVE_FINITE -> {
                if (!Double.isFinite(value) || value <= 0) {
                    throw new IllegalArgumentException(parameterLabel
                            + " must be a finite number greater than zero");
                }
                yield value;
            }
        };
    }

    public double parseParameter(final String text) {
        if (!usesParameter()) {
            return defaultParameter;
        }
        final String trimmed = text == null ? "" : text.trim();
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        final double value = switch (lower) {
            case "+inf", "inf", "+infinity", "infinity", "+∞", "∞" ->
                    Double.POSITIVE_INFINITY;
            case "-inf", "-infinity", "−inf", "−infinity", "-∞", "−∞" ->
                    Double.NEGATIVE_INFINITY;
            default -> {
                try {
                    yield Double.parseDouble(trimmed);
                } catch (final NumberFormatException exception) {
                    throw new IllegalArgumentException(parameterLabel + ": '" + trimmed
                            + "' is not a valid number");
                }
            }
        };
        return normalizeParameter(value);
    }

    public String formatParameter(final double value) {
        final double normalized = normalizeParameter(value);
        if (normalized == Double.POSITIVE_INFINITY) {
            return "+∞";
        }
        if (normalized == Double.NEGATIVE_INFINITY) {
            return "−∞";
        }
        if (normalized == Math.rint(normalized) && Math.abs(normalized) < 1e15) {
            return Long.toString((long) normalized);
        }
        return Double.toString(normalized);
    }

    public String htmlDescription(final int widthPixels) {
        return htmlDescription(widthPixels, defaultParameter);
    }

    public String htmlDescription(final int widthPixels, final double parameter) {
        if (widthPixels < 1) {
            throw new IllegalArgumentException("Description width must be positive");
        }
        final StringBuilder html = new StringBuilder(384)
                .append("<html><div style='width:")
                .append(widthPixels)
                .append("px'><b>")
                .append(escapeHtml(formula))
                .append("</b><br>")
                .append(escapeHtml(description))
                .append("<br><i>Weights:</i> ")
                .append(escapeHtml(weightSemantics));
        if (usesParameter()) {
            html.append("<br><i>")
                    .append(escapeHtml(parameterLabel))
                    .append(":</i> ")
                    .append(escapeHtml(parameterDescription))
                    .append(" Current value: <b>")
                    .append(escapeHtml(formatParameter(parameter)))
                    .append("</b>.");
        }
        return html.append("</div></html>").toString();
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
