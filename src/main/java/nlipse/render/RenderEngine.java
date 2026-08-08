package nlipse.render;

@FunctionalInterface
public interface RenderEngine {
    RenderResult render(RenderRequest request, CancellationToken cancellationToken);
}
