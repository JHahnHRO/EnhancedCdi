package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.*;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestScoped
class MessageMetaData {
    private Delivery incomingMessage;
    private String queue;

    void setIncomingMessage(Delivery message) {
        this.incomingMessage = message;
    }

    void setIncomingQueue(String queue) {
        this.queue = queue;
    }

    private void checkDelivery() {
        if (incomingMessage == null) {
            throw new IllegalStateException("No RabbitMQ message received yet in the current RequestScope");
        }
    }

    Optional<Delivery> getIncomingDelivery() {
        return Optional.of(incomingMessage);
    }

    @Produces
    @RoutingKey
    @Dependent
    String routingKey() {
        checkDelivery();
        return incomingMessage.getEnvelope().getRoutingKey();
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
    Instant instantHeader(InjectionPoint injectionPoint) {
        final Date header = header(injectionPoint, Date.class);
        return header == null ? null : header.toInstant();
    }

    @Produces
    @Dependent
    @Header("")
    BigDecimal bigDecimalHeader(InjectionPoint injectionPoint) {
        return header(injectionPoint, BigDecimal.class);
    }

    @Produces
    @Dependent
    @Header("")
    String stringHeader(InjectionPoint injectionPoint) {
        final Object header = header(injectionPoint, Object.class);
        return header == null ? null : header.toString();
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
                .map(Header.class::cast)
                .map(Header::value)
                .findAny()
                .orElseThrow();

        return clazz.cast(headers.get(headerName));
    }
    //endregion
}
