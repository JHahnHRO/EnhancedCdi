package io.github.jhahnhro.enhancedcdi.context;

/**
 * A {@link ContextController} for {@link CloseableContext closeable contexts} that allows for explicit closing of the
 * context and fires the necessary lifecycle events accordingly.
 *
 * @param <CONTEXT>
 * @see CloseableContext#close()
 */
public interface CloseableContextController<CONTEXT extends CloseableContext> extends ContextController<CONTEXT> {

    default void closeContext() {
        final CONTEXT ctx = getContext();
        if (!ctx.isClosed()) {
            beforeDestroyed();
            ctx.close();
            afterDestroyed();
        }
    }

}
