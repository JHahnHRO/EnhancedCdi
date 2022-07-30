package io.github.jhahnhro.enhancedcdi.context;

/**
 * Thrown if some action is performed with a {@link CloseableContext} that is already closed.
 */
public class ContextClosedException extends IllegalStateException {
    private final CloseableContext context;

    public ContextClosedException(CloseableContext context) {
        super("Context is already closed.");
        this.context = context;
    }

    public CloseableContext getContext() {
        return context;
    }
}
