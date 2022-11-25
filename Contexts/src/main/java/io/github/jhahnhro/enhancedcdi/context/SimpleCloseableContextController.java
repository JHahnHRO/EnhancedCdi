package io.github.jhahnhro.enhancedcdi.context;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Vetoed;
import javax.inject.Inject;

import static javax.interceptor.Interceptor.Priority.LIBRARY_AFTER;

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
