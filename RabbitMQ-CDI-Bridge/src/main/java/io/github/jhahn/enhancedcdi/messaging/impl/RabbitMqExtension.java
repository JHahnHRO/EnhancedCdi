package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.FromExchange;
import io.github.jhahn.enhancedcdi.messaging.FromQueue;
import io.github.jhahn.enhancedcdi.messaging.RpcEndpoint;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RabbitMqExtension implements Extension {
    private final Map<String, Set<AnnotatedMethod<?>>> necessaryQueues = new HashMap<>();
    private final Map<String, Set<AnnotatedMethod<?>>> necessaryExchanges = new HashMap<>();

    //region ProcessManagedBean
    <T, X> void collectInjectionPoints(@Observes ProcessInjectionPoint<T, X> pip) {

    }
    //endregion

    //region ProcessObserverMethod

    <T, X> void validateObserverMethods(@Observes ProcessObserverMethod<T, X> pom) {
        validateConsumerMethod(pom);

        if (pom.getAnnotatedMethod().isAnnotationPresent(RpcEndpoint.class)) {
            validateRpcMethod(pom);
        }
    }

    private <T, X> void validateConsumerMethod(ProcessObserverMethod<T, X> pom) {
        final Set<Annotation> observedQualifiers = pom.getObserverMethod().getObservedQualifiers();

        final AnnotatedMethod<X> method = pom.getAnnotatedMethod();

        observedQualifiers.stream()
                .filter(ann -> ann.annotationType() == FromQueue.class)
                .map(FromQueue.class::cast)
                .map(FromQueue::value)
                .forEach(queue -> necessaryQueues.computeIfAbsent(queue, __ -> new HashSet<>()).add(method));
        observedQualifiers.stream()
                .filter(ann -> ann.annotationType() == FromExchange.class)
                .map(FromExchange.class::cast)
                .map(FromExchange::value)
                .findAny()
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
