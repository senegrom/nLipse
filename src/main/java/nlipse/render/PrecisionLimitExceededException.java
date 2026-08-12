package nlipse.render;

/** Raised when an exact render would exceed the bounded adaptive-precision allowance. */
public final class PrecisionLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PrecisionLimitExceededException() {
        super("Exact rendering exceeded the adaptive-precision allowance. "
                + "Narrow the viewport or simplify the focus configuration before exporting.");
    }
}
