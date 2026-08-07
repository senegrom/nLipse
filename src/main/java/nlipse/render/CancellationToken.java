package nlipse.render;

@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    boolean isCancelled();

    default void throwIfCancelled() {
        if (isCancelled()) {
            throw new RenderCancelledException();
        }
    }
}
