package nlipse.render;

/** Whether a render may return after exhausting its precision-fallback allowance. */
public enum RenderExactness {
    /** Interactive display may use bounded primitive approximations in degenerate regions. */
    ALLOW_LIMITED,
    /** The render must fail rather than return any precision-limited samples. */
    REQUIRE_EXACT
}
