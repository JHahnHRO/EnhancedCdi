package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.*;

import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.spi.*;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class RabbitMqExtension implements Extension {
    private final Map<String, Set<AnnotatedMethod<?>>> necessaryQueues = new HashMap<>();
    private final Map<String, Set<AnnotatedMethod<?>>> necessaryExchanges = new HashMap<>();
    private final Map<AnnotatedElement, Pattern> routingKeyPatterns = new HashMap<>();

    //region ProcessManagedBean
    void validate(@Observes ProcessManagedBean<?> pb) {
        AnnotatedType<?> at = pb.getAnnotatedBeanClass();

        validatePatterns(at, at.getJavaClass(), pb);
        for (AnnotatedMethod<?> annotated : at.getMethods()) {
            validatePatterns(annotated, annotated.getJavaMember(), pb);
        }
    }

    private void validatePatterns(Annotated annotatedMethodOrType, AnnotatedElement annotatedElement,
                                  ProcessManagedBean<?> pb) {
        RoutingKeyPattern routingKeyPattern = annotatedMethodOrType.getAnnotation(RoutingKeyPattern.class);
        if (routingKeyPattern == null) {
            return;
        }
        String regex = routingKeyPattern.value();
        try {
            final Pattern pattern = Pattern.compile(regex);
            routingKeyPatterns.put(annotatedElement, pattern);
        } catch (PatternSyntaxException ex) {
            pb.addDefinitionError(
                    new DefinitionException(annotatedMethodOrType + " declares an invalid routing key pattern", ex));
        }
    }
    //endregion

    //region ProcessObserverMethod

    <T, X> void validateObserverMethods(@Observes ProcessObserverMethod<T, X> pom) {
        validateConsumerMethod(pom);

        if (pom.getAnnotatedMethod().isAnnotationPresent(RpcEndpoint.class)) {
            validateRpcMethod(pom);
        }
        if (pom.getAnnotatedMethod().isAnnotationPresent(PublishTo.class)) {
            validateFireAndForgetMethod(pom);
        }
    }

    private <T, X> void validateFireAndForgetMethod(ProcessObserverMethod<T, X> pom) {
        final AnnotatedMethod<X> method = pom.getAnnotatedMethod();
        final Method javaMethod = method.getJavaMember();

        necessaryExchanges.computeIfAbsent(method.getAnnotation(PublishTo.class).exchange(), __ -> new HashSet<>())
                .add(method);

        if (javaMethod.getReturnType() == Void.TYPE || javaMethod.getReturnType() == Void.class) {
            pom.addDefinitionError(new DefinitionException(
                    method + " was declared with @PublishTo method, " + "but has no return type"));
        }

    }

    private <X> AnnotatedParameter<X> getEventParameter(AnnotatedMethod<X> method) {
        return method.getParameters()
                .stream()
                .filter(p -> p.isAnnotationPresent(Observes.class) || p.isAnnotationPresent(ObservesAsync.class))
                .findAny()
                .orElseThrow();
    }

    private <T, X> void validateConsumerMethod(ProcessObserverMethod<T, X> pom) {
        final AnnotatedMethod<X> method = pom.getAnnotatedMethod();
        final AnnotatedParameter<X> eventParameter = getEventParameter(method);

        Optional.ofNullable(eventParameter.getAnnotation(FromQueue.class))
                .map(FromQueue::value)
                .ifPresent(queue -> necessaryQueues.computeIfAbsent(queue, __ -> new HashSet<>()).add(method));
        Optional.ofNullable(eventParameter.getAnnotation(FromExchange.class))
                .map(FromExchange::value)
                .ifPresent(exchange -> necessaryExchanges.computeIfAbsent(exchange, __ -> new HashSet<>()).add(method));

    }

    private <T, X> void validateRpcMethod(ProcessObserverMethod<T, X> pom) {
        final AnnotatedMethod<X> method = pom.getAnnotatedMethod();
        final Method javaMethod = method.getJavaMember();

        if (javaMethod.getReturnType() == Void.TYPE || javaMethod.getReturnType() == Void.class) {
            pom.addDefinitionError(
                    new DefinitionException(method + " was declared with @RpcMethod, " + "but has no return type"));
        }
    }
    //endregion

    Set<NecessaryName> getNecessaryQueues() {
        return necessaryQueues.entrySet().stream().map(NecessaryName::new).collect(Collectors.toSet());
    }

    Set<NecessaryName> getNecessaryExchanges() {
        return necessaryQueues.entrySet().stream().map(NecessaryName::new).collect(Collectors.toSet());
    }

    record NecessaryName(String name, Set<AnnotatedMethod<?>> causes) {
        public NecessaryName(Map.Entry<String, Set<AnnotatedMethod<?>>> mapEntry) {
            this(mapEntry.getKey(), mapEntry.getValue());
        }
    }

}
