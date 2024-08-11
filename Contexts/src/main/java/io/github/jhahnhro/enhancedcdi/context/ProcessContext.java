package io.github.jhahnhro.enhancedcdi.context;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Context;

/**
 * A context that associates beans with "processes". Multiple threads can work on the same process and see the same set
 * of contextual instances, but there can be more than one process in flight at any given moment.
 * <p>
 * An instance of this class manages {@link Process}es, i.e. child-contexts with the same {@link #getScope() scope},
 * each identified by a unique key, e.g. a number or a {@link java.util.UUID}, and each with their own set of contextual
 * instances.
 * <p>
 * A process can be created from any thread by calling {@link #getOrCreateProcess(KEY)}. Once created, they can be
 * {@link Process#activate() activated} and {@link PauseableContext#pause() deactivated} in any thread as
 * often as needed. They can also be {@link Process#close() closed} individually. Calling {@link #close()} on the whole
 * context closes all its processes.
 * <p>
 * If this was part of the CDI standard, {@link jakarta.enterprise.context.SessionScoped} and
 * {@link jakarta.enterprise.context.ConversationScoped} would be backed by something similar: Multiple threads can work
 * for the same session/conversation, but also multiple conversations/sessions can be active at the same time. In
 * contrast to {@link jakarta.enterprise.context.RequestScoped} which is usually single-threaded, i.e. every thread in
 * which a RequestContext is active sees its own set of contextual instances.
 *
 * @param <KEY> the type of key identifying the processes
 * @param <P>   the type of processes
 * @see ProcessContextController a controller for this context that fires lifecycle events and creates new processes.
 */
public class ProcessContext<KEY, P extends ProcessContext.Process<KEY>> extends ForwardingContext
        implements CloseableContext {
    private final Class<? extends Annotation> scope;
    private final Function<? super KEY, P> processCreator;
    /**
     * All known, non-closed processes.
     */
    private final Map<KEY, P> processes = new HashMap<>();
    /**
     * Guards the following invariants:
     * <ul>
     *    <li>{@code this.processed} can only be structurally changed while holding the write-lock</li>
     *    <li>There is at most one process active in any thread</li>
     *    <li>if {@code this.closed==true}, then all processes are closed as well</li>
     * </ul>
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    /**
     * If this context is closed.
     */
    private boolean closed = false;

    public ProcessContext(final Class<? extends Annotation> scope, final Function<? super KEY, P> processCreator) {
        this.scope = scope;
        this.processCreator = processCreator;
    }

    public ProcessContext(final Class<? extends Annotation> scope, final BiFunction<? super ProcessContext<KEY, P>, ?
            super KEY, P> processCreator) {
        this.scope = scope;
        this.processCreator = k -> processCreator.apply(this, k);
    }

    public static <K> ProcessContext<K, Process<K>> newProcessContext(final Class<? extends Annotation> scope) {
        return new ProcessContext<>(scope, Process::new);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public boolean isActive() {
        readLock.lock();
        try {
            return processes.values().stream().anyMatch(Context::isActive);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns all processes currently in this context. Note that closed processes get automatically removed and are
     * never contained in the result.
     *
     * @return all processes of this context. Immutable and never {@code null}.
     */
    public Set<P> getProcesses() {
        readLock.lock();
        try {
            return Set.copyOf(processes.values());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the process with the given key if it exists (and is not closed), a new process otherwise.
     *
     * @param key a process key
     * @return the process with the given key, possibly newly created if it did not exist before.
     * @throws ContextClosedException if this context has already been closed.
     */
    public P getOrCreateProcess(KEY key) {
        writeLock.lock();
        try {
            checkOpen();
            return processes.computeIfAbsent(key, processCreator);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean isClosed() {
        readLock.lock();
        try {
            return closed;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Closes all processes of this context and blocks until all processes are closed. This destroys all beans in this
     * context. This context cannot be activated again afterwards. New processes cannot be created either.
     * <p>
     * The method is idempotent. Calling it multiple times has no additional effect.
     */
    @Override
    public void close() {
        writeLock.lock();
        try {
            closed = true;
            processes.values().forEach(CloseableContext::close);
            processes.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    protected P delegate() {
        // delegate get(Contextual), get(Contextual, CreationalContext) and destroy(Contextual) to the process that
        // is active in the current thread
        return getProcesses().stream().filter(Context::isActive).findAny().orElseThrow(ContextNotActiveException::new);
    }

    public static class Process<KEY> extends ThreadAwarePauseableContext {
        protected final KEY key;
        private final ProcessContext<KEY, ? extends Process<KEY>> processContext;

        /**
         * @param processContext the process's context
         */
        protected Process(final ProcessContext<KEY, ? extends Process<KEY>> processContext, KEY key) {
            this.processContext = processContext;
            this.key = key;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return processContext.getScope();
        }

        @Override
        public boolean activate() {
            processContext.readLock.lock();

            try {
                checkOpen();
                processContext.processes.values()
                        .stream()
                        .filter(otherProcess -> this != otherProcess && otherProcess.isActive())
                        .findAny()
                        .ifPresent(otherProcess -> {
                            throw new IllegalStateException(
                                    "This process with cannot be activated in current thread, because the process "
                                    + "with key=" + otherProcess.getKey() + " is already active");
                        });
                return super.activate();
            } finally {
                processContext.readLock.unlock();
            }
        }

        @Override
        public void close() {
            processContext.writeLock.lock();
            try {
                processContext.processes.remove(key);
            } finally {
                processContext.writeLock.unlock();
            }
            super.close();
        }

        public KEY getKey() {
            return key;
        }
    }
}
