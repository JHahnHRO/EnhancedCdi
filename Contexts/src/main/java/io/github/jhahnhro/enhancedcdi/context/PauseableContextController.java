package io.github.jhahnhro.enhancedcdi.context;

/**
 * A {@link ContextController} for {@link PauseableContext pauseable contexts} that allows for explicit pausing, closing
 * and - if supported - unpausing of its context, firing the appropriate lifecycle events accordingly.
 *
 * @param <CONTEXT>
 */
public interface PauseableContextController<CONTEXT extends PauseableContext>
        extends CloseableContextController<CONTEXT> {

    /**
     * Runs the given action within the context. If this context is not yet active, tries to activate it first and
     * pauses the context again after the action has completed.
     *
     * @param action an action to run within the context
     * @throws UnsupportedOperationException if the context is not active yet and manual activation is not supported.
     */
    default void runInContext(Runnable action) {
        final CONTEXT context = getContext();
        if (context.isActive()) {
            action.run();
        } else {
            if (context.activate()) {
                initialized();
                try {
                    action.run();
                } finally {
                    beforePaused();
                    context.pause();
                    afterPaused();
                }
            }
        }
    }


    /**
     * Activates the context.
     *
     * @throws UnsupportedOperationException if manual activation is not supported.
     */
    default void activateContext() {
        if (getContext().activate()) {
            initialized();
        }
    }

    /**
     * Pauses the context if it is active.
     */
    default void pauseContext() {
        final CONTEXT context = getContext();
        if (context.isActive()) {
            beforePaused();
            context.pause();
            afterPaused();
        }
    }

    default void beforePaused() {
        CONTEXT context = getContext();
        lifecycleEvent().select(BeforePaused.Literal.of(context.getScope())).fire(context);
    }

    default void afterPaused() {
        CONTEXT context = getContext();
        lifecycleEvent().select(Paused.Literal.of(context.getScope())).fire(context);
    }
}
