package io.github.jhahnhro.enhancedcdi.context.businessprocess;

import io.github.jhahnhro.enhancedcdi.context.CloseableSuspendableContext;
import io.github.jhahnhro.enhancedcdi.context.SuspendableContext;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class BusinessProcessController {

    private static final Annotation INITIALIZED = Initialized.Literal.of(BusinessProcessScoped.class);
    private static final Annotation BEFORE_DESTROYED = BeforeDestroyed.Literal.of(BusinessProcessScoped.class);
    private static final Annotation DESTROYED = Destroyed.Literal.of(BusinessProcessScoped.class);

    private final AtomicInteger nextId = new AtomicInteger();
    private BusinessProcessContext context;

    @Inject
    Event<BusinessProcess> lifeCycleEvent;

    @Inject
    void setContext(BusinessProcessExtension extension) {
        this.context = extension.context;
    }

    public BusinessProcess createNewProcess() {
        final int processId = nextId.getAndIncrement();
        final BusinessProcess newProcess = getOrCreateProcess(processId);
        lifeCycleEvent.select(INITIALIZED).fire(newProcess);
        return newProcess;
    }

    public Optional<BusinessProcess> getProcess(int processId) {
        final int nextId = this.nextId.get();
        if (0 <= processId && processId < nextId) {
            return Optional.of(getOrCreateProcess(processId));
        } else {
            return Optional.empty();
        }
    }

    public BusinessProcess getOrCreateProcess(int processId) {
        return new BusinessProcess(context.getOrCreate(processId), processId);
    }

    @PreDestroy
    void closeOnShutDown() {
        context.close();
    }

    public final class BusinessProcess implements AutoCloseable {
        private final CloseableSuspendableContext underlyingContext;

        private final int processId;

        private BusinessProcess(CloseableSuspendableContext underlyingContext, int processId) {
            this.underlyingContext = underlyingContext;
            this.processId = processId;
        }

        public int getProcessId() {
            return processId;
        }

        public SuspendableContext.ActivationToken activate() {
            return underlyingContext.activate();
        }

        public boolean isActive() {
            return underlyingContext.isActive();
        }

        @Override
        public void close() {
            if (!isClosed()) {
                lifeCycleEvent.select(BEFORE_DESTROYED).fire(this);
                underlyingContext.close();
                lifeCycleEvent.select(DESTROYED).fire(this);
            }
        }

        public boolean isClosed() {
            return underlyingContext.isClosed();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return (o instanceof BusinessProcess that) && this.processId == that.processId;
        }

        @Override
        public int hashCode() {
            return processId;
        }
    }
}
