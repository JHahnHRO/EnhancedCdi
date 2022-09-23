package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.ContextNotActiveException;

/**
 * Thrown if some action is performed with a {@link CloseableContext} that is already closed.
 */
public class ContextClosedException extends ContextNotActiveException {
    private final CloseableContext context;

    public ContextClosedException(CloseableContext context) {
        super("Context is already closed.");
        this.context = context;
    }

    public CloseableContext getContext() {
        return context;
    }
}
