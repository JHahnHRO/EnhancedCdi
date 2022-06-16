package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.*;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AnnotatedCallable;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.DefinitionException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import java.lang.reflect.AnnotatedElement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequestScoped
class MessageMetaData {
    Delivery incomingMessage;
    String queue;

    void setIncomingMessage(Delivery message) {
        this.incomingMessage = message;
    }

    void setIncomingQueue(String queue) {
        this.queue = queue;
    }

    Delivery checkDelivery() {
        if (incomingMessage == null) {
            throw new IllegalStateException("No RabbitMQ message received yet in the current RequestScope");
        }
        return incomingMessage;
    }

    @Produces
    @RoutingKey
    @Dependent
    String routingKey() {
        checkDelivery();
        return incomingMessage.getEnvelope().getRoutingKey();
    }

    @Inject
    RoutingKeyPatternCache routingKeyPatternCache;

    @Produces
    @RoutingKeyGroup("")
    @Dependent
    String routingKeyGroup(InjectionPoint injectionPoint) {
        checkDelivery();

        if (injectionPoint.getAnnotated() instanceof AnnotatedParameter<?> parameter) {
            final RoutingKeyGroup routingKeyGroup = parameter.getAnnotation(RoutingKeyGroup.class);

            final AnnotatedCallable<?> methodOrConstructor = parameter.getDeclaringCallable();
            final Optional<Pattern> pattern = routingKeyPatternCache.get(
                    (AnnotatedElement) methodOrConstructor.getJavaMember());

            if (pattern.isPresent()) {
                final Matcher matcher = pattern.get().matcher(routingKey());
                if (matcher.matches()) {
                    return matcher.group(routingKeyGroup.value());
                } else {
                    return null;
                }
            }
        }
        throw new DefinitionException(
                "@RoutingKeyGroup can only be used on parameters of methods annotated with @RoutingKeyPattern");

    }

    @Produces
    @Exchange
    @Dependent
    String exchange() {
        checkDelivery();
        return incomingMessage.getEnvelope().getExchange();
    }

    @Produces
    @Queue
    @Dependent
    String queue() {
        checkDelivery();
        return queue;
    }

    @Produces
    @Incoming
    @RequestScoped
    BasicProperties basicProperties() {
        checkDelivery();
        return incomingMessage.getProperties();
    }

    //region header objects

    @Produces
    @Dependent
    @Header("")
    Boolean booleanHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Boolean.class);
    }

    @Produces
    @Dependent
    @Header("")
    Byte byteHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Byte.class);
    }

    @Produces
    @Dependent
    @Header("")
    Short shortHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Short.class);
    }

    @Produces
    @Dependent
    @Header("")
    Integer integerHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Integer.class);
    }

    @Produces
    @Dependent
    @Header("")
    Float floatHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Float.class);
    }

    @Produces
    @Dependent
    @Header("")
    Long longHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Long.class);
    }


    @Produces
    @Dependent
    @Header("")
    Double doubleHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Double.class);
    }

    @Produces
    @Dependent
    @Header("")
    byte[] byteArrayHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, byte[].class).clone();
    }

    @Produces
    @Dependent
    @Header("")
    Instant instantHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, Date.class).toInstant();
    }

    @Produces
    @Dependent
    @Header("")
    BigDecimal bigDecimalHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, BigDecimal.class);
    }

    @SuppressWarnings("unchecked")
    @Produces
    @Dependent
    @Header("")
    <T> Map<String, T> mapHeader(InjectionPoint injectionPoint) {
        final Map<String, T> map = header(injectionPoint, Map.class);
        return map == null ? null : Map.copyOf(map);
    }

    @SuppressWarnings("unchecked")
    @Produces
    @Dependent
    @Header("")
    <T> List<T> listHeader(InjectionPoint injectionPoint) {
        final List<T> list = header(injectionPoint, List.class);
        return list == null ? null : List.copyOf(list);
    }

    private <T> T header(InjectionPoint injectionPoint, Class<T> clazz) {
        checkDelivery();
        final Map<String, Object> headers = incomingMessage.getProperties().getHeaders();
        if (headers == null) {
            return null;
        }

        final String headerName = injectionPoint.getQualifiers()
                .stream()
                .filter(ann -> ann.annotationType() == Header.class)
                .findAny()
                .map(Header.class::cast)
                .map(Header::value)
                .orElseThrow();

        return clazz.cast(headers.get(headerName));
    }
    //endregion

}
