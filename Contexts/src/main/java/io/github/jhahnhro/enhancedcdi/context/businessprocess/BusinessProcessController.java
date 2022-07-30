package io.github.jhahnhro.enhancedcdi.context.businessprocess;

import io.github.jhahnhro.enhancedcdi.context.SuspendableContext;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class BusinessProcessController {

    private final AtomicInteger nextId = new AtomicInteger();
    private BusinessProcessContext context;

    @Inject
    void setContext(BusinessProcessExtension extension) {
        this.context = extension.context;
    }

    public SuspendableContext createNew() {
        return context.getOrCreate(nextId.getAndIncrement());
    }

    public Optional<SuspendableContext> get(int processId) {
        final int nextId = this.nextId.get();
        if (0 <= processId && processId < nextId) {
            return Optional.of(context.getOrCreate(processId));
        } else {
            return Optional.empty();
        }
    }

    public SuspendableContext getOrCreate(int processId) {
        return context.getOrCreate(processId);
    }
}
