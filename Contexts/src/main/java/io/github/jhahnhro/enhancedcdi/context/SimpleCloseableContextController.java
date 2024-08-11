package io.github.jhahnhro.enhancedcdi.context;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;

@Vetoed
public class SimpleCloseableContextController<CONTEXT extends CloseableContext>
        implements CloseableContextController<CONTEXT> {

    @Inject
    CONTEXT context;

    @Inject
    Event<CONTEXT> lifecycleEvent;

    public SimpleCloseableContextController(CONTEXT context) {

    }

    @Override
    public CONTEXT getContext() {
        return context;
    }

    @Override
    public Event<CONTEXT> lifecycleEvent() {
        return lifecycleEvent;
    }

    protected void onStartup(@Observes @Priority(LIBRARY_AFTER) @Initialized(ApplicationScoped.class) Object appScopedInitialized) {
        initialized();
    }
}
