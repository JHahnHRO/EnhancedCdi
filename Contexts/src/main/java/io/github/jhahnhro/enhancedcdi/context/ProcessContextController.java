package io.github.jhahnhro.enhancedcdi.context;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;

@Vetoed
public class ProcessContextController<KEY, PROCESS extends ProcessContext.Process<KEY>,
        CONTEXT extends ProcessContext<KEY, PROCESS>>
        implements CloseableContextController<CONTEXT> {

    private final CONTEXT context;
    private final Map<KEY, ProcessController> processControllers = new ConcurrentHashMap<>();

    @Inject
    protected Event<CONTEXT> contextLifecycle;

    @Inject
    protected Event<PROCESS> processLifecycle;

    public ProcessContextController(CONTEXT context) {
        this.context = context;
    }

    public static <KEY> ProcessContextController<KEY, ProcessContext.Process<KEY>, ProcessContext<KEY,
            ProcessContext.Process<KEY>>> newProcessContextController(Class<? extends Annotation> scope) {
        return new ProcessContextController<>(ProcessContext.newProcessContext(scope));
    }


    @Override
    public CONTEXT getContext() {
        return context;
    }

    @Override
    public Event<CONTEXT> lifecycleEvent() {
        return contextLifecycle;
    }

    public ProcessController processController(KEY key) {
        return processControllers.computeIfAbsent(key, k -> new ProcessController(context.getOrCreateProcess(k)));
    }

    public class ProcessController implements PauseableContextController<PROCESS> {
        private final PROCESS process;

        public ProcessController(PROCESS process) {this.process = process;}

        @Override
        public PROCESS getContext() {
            return process;
        }

        @Override
        public Event<PROCESS> lifecycleEvent() {
            return processLifecycle;
        }
    }
}
