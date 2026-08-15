package nlipse.render;

/** Internal control-flow exception used to abort superseded render work. */
final class RenderCancelledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    RenderCancelledException() {
        super("Render cancelled", null, false, false);
    }
}
