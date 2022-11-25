package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.Context;
import javax.enterprise.event.Event;

/**
 * A ContextController is responsible for changing the state of a context if it allows for state changes. It fires
 * lifecycle events accordingly.
 *
 * @param <CONTEXT> The type of context this controller is responsible for.
 * @see Initialized
 * @see BeforeDestroyed
 * @see Destroyed
 */
public interface ContextController<CONTEXT extends Context> {
    /**
     * @return The specific context this controller is responsible for
     */
    CONTEXT getContext();

    // TODO maybe have Event<Object> instead!?
    Event<CONTEXT> lifecycleEvent();

    default void initialized() {
        CONTEXT context = getContext();
        lifecycleEvent().select(Initialized.Literal.of(context.getScope())).fire(context);
    }

    default void beforeDestroyed() {
        CONTEXT context = getContext();
        lifecycleEvent().select(BeforeDestroyed.Literal.of(context.getScope())).fire(context);
    }

    default void afterDestroyed() {
        CONTEXT context = getContext();
        lifecycleEvent().select(Destroyed.Literal.of(context.getScope())).fire(context);
    }
}
