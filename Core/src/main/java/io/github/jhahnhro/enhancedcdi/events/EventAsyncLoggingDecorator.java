package io.github.jhahnhro.enhancedcdi.events;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.event.Event;
import javax.enterprise.event.NotificationOptions;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enables logging of all exceptions that are thrown in {@link javax.enterprise.event.ObservesAsync async observer
 * methods} and would otherwise be lost. All exceptions from all failed observer methods are collected and logged after
 * the last observer method finished executing.
 *
 * @param <T>
 */
@Decorator
public class EventAsyncLoggingDecorator<T> extends AbstractEventDecorator<T> {

    /**
     * {@link Logger} of the {@link Bean#getBeanClass() Bean class} if the {@link Event} injection point.
     */
    private final Logger beanLogger;
    /**
     * Default logger that does not collect stack traces
     */
    private final ExceptionHandler exceptionHandler;

    //region Constructors and initializer methods

    /**
     * explicit constructor used in {@link #decorate(Event, EventMetadata)}.
     */
    EventAsyncLoggingDecorator(Event<T> delegate, EventMetadata eventMetadata) {
        super(delegate, eventMetadata);

        InjectionPoint injectionPoint = eventMetadata.getInjectionPoint();
        this.beanLogger = getLogger(injectionPoint);
        this.exceptionHandler = new ExceptionHandler();
    }

    private static Logger getLogger(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getCanonicalName());
    }

    /**
     * Bean constructor
     */
    @Inject
    EventAsyncLoggingDecorator(@Delegate @Any Event<T> delegate, InjectionPoint injectionPoint) {
        this(delegate, new EventMetadataImpl(injectionPoint));
    }
    //endregion

    @Override
    protected <U extends T> AbstractEventDecorator<U> decorate(Event<U> delegate, EventMetadata eventMetadata) {
        return new EventAsyncLoggingDecorator<>(delegate, eventMetadata);
    }

    //region decorated methods
    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event) {
        return super.fireAsync(event).whenComplete(handleExceptions());
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
        return super.fireAsync(event, options).whenComplete(handleExceptions());
    }

    private BiConsumer<Object, Throwable> handleExceptions() {
        if (beanLogger.isLoggable(Level.FINE)) {
            return new ExceptionHandlerWithStacktrace();
        } else {
            return exceptionHandler;
        }
    }
    //endregion

    private class ExceptionHandler implements BiConsumer<Object, Throwable>, Serializable {

        @Override
        public void accept(Object o, Throwable ex) {
            if (ex != null) {
                beanLogger.log(Level.SEVERE, () -> MessageFormat.format(
                        "Asynchronous observer method(s) of an event originating from {0} failed "
                        + "with exception(s): {0}", eventMetadata.getInjectionPoint(), ex.getSuppressed()));
                beanLogger.log(Level.FINE, "Exception was: ", ex);
            }
        }
    }

    private class ExceptionHandlerWithStacktrace extends ExceptionHandler {
        private final List<StackWalker.StackFrame> stackFrames;

        ExceptionHandlerWithStacktrace() {
            final String beanClassName = eventMetadata.getInjectionPoint().getMember().getDeclaringClass().getName();
            this.stackFrames = StackWalker.getInstance()
                    .walk(stream -> stream.dropWhile(frame -> !frame.getClassName().equals(beanClassName))
                            .collect(Collectors.toList()));
        }

        private String getMessage() {
            return stackFrames.stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining("\n\t", "The event was sent from:\n\t", ""));
        }

        @Override
        public void accept(Object o, Throwable ex) {
            super.accept(o, ex);
            if (ex != null) {
                beanLogger.log(Level.FINE, ex, this::getMessage);
            }
        }
    }
}
