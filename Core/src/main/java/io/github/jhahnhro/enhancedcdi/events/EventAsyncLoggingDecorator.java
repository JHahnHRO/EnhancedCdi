package io.github.jhahnhro.enhancedcdi.events;

import java.io.Serializable;
import java.lang.System.Logger.Level;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

/**
 * Enables logging of all exceptions that are thrown in
 * {@link jakarta.enterprise.event.ObservesAsync async observer methods} and would otherwise be lost. All exceptions from
 * all failed observer methods are collected and logged after the last observer method finished executing.
 *
 * @param <T>
 */
@Decorator
public class EventAsyncLoggingDecorator<T> extends AbstractEventDecorator<T> {

    /**
     * {@link java.lang.System.Logger} of the {@link Bean#getBeanClass() Bean class} if the {@link Event} injection
     * point.
     */
    private final System.Logger beanLogger;
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

        this.beanLogger = getLogger(eventMetadata);
        this.exceptionHandler = new ExceptionHandler();
    }

    private static System.Logger getLogger(EventMetadata eventMetadata) {
        return System.getLogger(getOriginClassName(eventMetadata));
    }

    private static String getOriginClassName(EventMetadata eventMetadata) {
        return eventMetadata.getInjectionPoint().getMember().getDeclaringClass().getCanonicalName();
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

    private BiConsumer<T, Throwable> handleExceptions() {
        if (beanLogger.isLoggable(Level.DEBUG)) {
            return new ExceptionHandlerWithStacktrace();
        } else {
            return exceptionHandler;
        }
    }
    //endregion

    private class ExceptionHandler implements BiConsumer<T, Throwable>, Serializable {

        @Override
        public void accept(T o, Throwable ex) {
            if (ex != null) {
                beanLogger.log(Level.ERROR, () -> MessageFormat.format(
                        "Asynchronous observer method(s) of an event originating from {0} failed with exception",
                        eventMetadata.getInjectionPoint()), ex);
            }
        }
    }

    private class ExceptionHandlerWithStacktrace extends ExceptionHandler {
        private final List<StackWalker.StackFrame> stackFrames;

        ExceptionHandlerWithStacktrace() {
            final String beanClassName = getOriginClassName(eventMetadata);
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
        public void accept(T o, Throwable ex) {
            super.accept(o, ex);
            if (ex != null) {
                beanLogger.log(Level.DEBUG, this::getMessage, ex);
            }
        }
    }
}
