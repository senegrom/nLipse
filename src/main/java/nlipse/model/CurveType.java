package nlipse.model;

public enum CurveType {
    LIPSE("n-Ellipse (sum)", false),
    CASSIN("Cassini oval (product)", true),
    HYPERB("n-Hyperbola (average difference)", false);

    private final String displayName;
    private final boolean defaultLogSpacing;

    CurveType(final String displayName, final boolean defaultLogSpacing) {
        this.displayName = displayName;
        this.defaultLogSpacing = defaultLogSpacing;
    }

    public boolean defaultLogSpacing() {
        return defaultLogSpacing;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
