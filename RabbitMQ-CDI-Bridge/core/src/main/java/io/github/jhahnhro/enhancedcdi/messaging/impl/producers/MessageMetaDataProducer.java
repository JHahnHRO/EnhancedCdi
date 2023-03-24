package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.Exchange;
import io.github.jhahnhro.enhancedcdi.messaging.Header;
import io.github.jhahnhro.enhancedcdi.messaging.Queue;
import io.github.jhahnhro.enhancedcdi.messaging.RoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.Serialized;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

@RequestScoped
public class MessageMetaDataProducer {

    private Incoming<byte[]> incomingMessage = null;
    private Incoming<?> deserializedMessage = null;
    private Acknowledgment acknowledgment = null;

    private Outgoing.Response.Builder<?, ?> responseBuilder = null;

    public void setRawMessage(final Incoming<byte[]> rawMessage, final Acknowledgment acknowledgment) {
        this.incomingMessage = rawMessage;
        this.acknowledgment = acknowledgment;

        if (rawMessage instanceof Incoming.Request<byte[]> request) {
            this.responseBuilder = new Outgoing.Response.Builder<>(request);
        }
    }

    public void setDeserializedMessage(final Incoming<?> incomingMessage) {
        this.deserializedMessage = incomingMessage;
    }

    private void checkDelivery() {
        if (this.incomingMessage == null) {
            throw new IllegalStateException("No RabbitMQ message has been received in the current RequestScope");
        }
    }

    private void checkDeserializedMessage() {
        checkDelivery();
        if (this.deserializedMessage == null) {
            throw new IllegalStateException("RabbitMQ message has been not been serialized yet");
        }
    }

    private void checkRequest() {
        checkDelivery();
        if (responseBuilder == null) {
            throw new IllegalStateException("No RabbitMQ request has been received in the current RequestScope");
        }
    }

    @Produces
    @Dependent
    @Serialized
    Incoming<byte[]> serializedMessage() {
        checkDelivery();
        return this.incomingMessage;
    }

    @Produces
    @Dependent
    <T> Incoming<T> deserializedMessage() {
        checkDeserializedMessage();
        return (Incoming<T>) deserializedMessage;
    }

    @Produces
    @Dependent
    Acknowledgment acknowledgement() {
        checkDelivery();
        return acknowledgment;
    }

    @Produces
    @Dependent
    <REQ, RES> Outgoing.Response.Builder<REQ, RES> responseBuilder() {
        checkRequest();
        return (Outgoing.Response.Builder<REQ, RES>) this.responseBuilder;
    }

    @Produces
    @RoutingKey
    @Dependent
    String routingKey() {
        checkDelivery();
        return this.incomingMessage.delivery().getEnvelope().getRoutingKey();
    }

    @Produces
    @Exchange
    @Dependent
    String exchange() {
        checkDelivery();
        return this.incomingMessage.delivery().getEnvelope().getExchange();
    }

    @Produces
    @Queue
    @Dependent
    String queue() {
        checkDelivery();
        return this.incomingMessage.queue();
    }

    @Produces
    @Dependent
    BasicProperties basicProperties() {
        checkDelivery();
        return this.incomingMessage.delivery().getProperties();
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

        final Map<String, Object> headers = basicProperties().getHeaders();
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
