package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DefinitionException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.util.AnnotationLiteral;

import com.rabbitmq.client.AMQP;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.FromExchange;
import io.github.jhahnhro.enhancedcdi.messaging.FromQueue;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint;

public class RabbitMqExtension implements Extension {
    private final Map<String, Set<AnnotatedMethod<?>>> necessaryQueues = new HashMap<>();
    private final Map<String, Set<AnnotatedMethod<?>>> necessaryExchanges = new HashMap<>();

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

        if (javaMethod.getReturnType() == void.class) {
            pom.addDefinitionError(
                    new DefinitionException(method + " was declared with @RpcEndpoint, but does not return anything"));
        }
    }
    //endregion

    //region AfterBeanDiscovery
    void addContext(@Observes AfterBeanDiscovery abd) {

    }
    //endregion

    //region AfterDeploymentValidation
    void validate(@Observes AfterDeploymentValidation adv, BeanManager beanManager) {
        final Topology consolidatedTopology = beanManager.createInstance()
                .select(Topology.class, new AnnotationLiteral<Consolidated>() {})
                .get();

        validateTopology(adv, consolidatedTopology);

        // no need to keep these around
        necessaryQueues.clear();
        necessaryExchanges.clear();
    }

    private void validateTopology(AfterDeploymentValidation adv, Topology topology) {
        final Set<String> declaredQueueNames = topology.queueDeclarations()
                .stream()
                .map(AMQP.Queue.Declare::getQueue)
                .collect(Collectors.toSet());

        final Set<String> declaredExchangeNames = topology.exchangeDeclarations()
                .stream()
                .map(AMQP.Exchange.Declare::getExchange)
                .collect(Collectors.toCollection(HashSet::new));
        declaredExchangeNames.addAll(Topology.PREDECLARED_EXCHANGES);


        final String pattern = "The method(s) {1} require(s) the {0} \"{2}\", but no {0} declaration was found for "
                               + "that name.";
        necessaryQueues.forEach((queue, methods) -> {
            if (!declaredQueueNames.contains(queue)) {
                final String message = MessageFormat.format(pattern, "queue", methods, queue);
                adv.addDeploymentProblem(new DefinitionException(message));
            }
        });

        necessaryExchanges.forEach((exchange, methods) -> {
            if (!declaredExchangeNames.contains(exchange)) {
                final String message = MessageFormat.format(pattern, "exchange", methods, exchange);
                adv.addDeploymentProblem(new DefinitionException(message));
            }
        });
    }
    //endregion
}
