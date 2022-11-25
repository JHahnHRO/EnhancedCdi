package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

abstract class ForwardingContext implements AlterableContext {
    protected abstract AlterableContext delegate();

    @Override
    public boolean isActive() {
        return delegate().isActive();
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        return delegate().get(contextual, creationalContext);
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        return delegate().get(contextual);
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        delegate().destroy(contextual);
    }
}
