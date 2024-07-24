package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Exchange;
import io.github.jhahnhro.enhancedcdi.messaging.Header;
import io.github.jhahnhro.enhancedcdi.messaging.Headers;
import io.github.jhahnhro.enhancedcdi.messaging.Queue;
import io.github.jhahnhro.enhancedcdi.messaging.RoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgement;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;

@RequestScoped
public class MessageMetaDataProducer {
    private String queue = null;
    private Envelope envelope = null;
    private AMQP.BasicProperties deliveryProperties = null;
    private Acknowledgement acknowledgement = null;
    private Incoming<?> incomingMessage = null;

    private State state = State.NO_DATA;

    public void setDelivery(String queue, Envelope envelope, AMQP.BasicProperties properties,
                            Acknowledgement acknowledgement) {
        if (this.state == State.NO_DATA) {
            this.queue = Objects.requireNonNull(queue);
            this.envelope = Objects.requireNonNull(envelope);
            this.deliveryProperties = Objects.requireNonNull(properties);
            this.acknowledgement = Objects.requireNonNull(acknowledgement);
            this.state = State.RAW_DATA_AVAILABLE;
        } else {
            throw new IllegalStateException();
        }
    }

    public void setMessage(final Incoming<?> incomingMessage) {
        if (this.state == State.RAW_DATA_AVAILABLE) {
            this.incomingMessage = incomingMessage;
            this.state = State.DATA_AVAILABLE;
        } else {
            throw new IllegalStateException();
        }
    }

    private void checkDelivery() {
        if (this.state == State.NO_DATA) {
            throw new IllegalStateException("No RabbitMQ message has been received in the current RequestScope");
        }
    }

    private Incoming.Request<?> checkRequest() {
        checkDelivery();
        if (this.state == State.DATA_AVAILABLE && this.incomingMessage instanceof Incoming.Request<?> request) {
            return request;
        }
        throw new RpcNotActiveException();
    }

    @Produces
    @Dependent
    <T> Incoming<T> deserializedMessage() {
        checkDelivery();
        return (Incoming<T>) this.incomingMessage;
    }

    @Produces
    @RequestScoped
    Acknowledgement acknowledgement() {
        checkDelivery();
        return acknowledgement;
    }

    @Produces
    @Dependent
    <REQ, RES> Outgoing.Response.Builder<REQ, RES> responseBuilder(InjectionPoint ip) {
        Type typeRES = ((ParameterizedType) ip.getType()).getActualTypeArguments()[1];

        final var builder = checkRequest().newResponseBuilder().setType(typeRES);
        return (Outgoing.Response.Builder<REQ, RES>) builder;
    }

    @Produces
    @RoutingKey
    @Dependent
    String routingKey() {
        checkDelivery();
        return envelope.getRoutingKey();
    }

    @Produces
    @Exchange
    @Dependent
    String exchange() {
        checkDelivery();
        return envelope.getExchange();
    }

    @Produces
    @Queue
    @Dependent
    String queue() {
        checkDelivery();
        return this.queue;
    }

    @Produces
    @RequestScoped
    BasicProperties basicProperties() {
        checkDelivery();
        return deliveryProperties;
    }

    //region header objects
    @Produces
    @RequestScoped
    @Headers
    Map<String, Object> allHeaders() {
        checkDelivery();
        final Map<String, Object> headers = basicProperties().getHeaders();
        return headers == null ? null : Collections.unmodifiableMap(headers);
    }

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
        return list == null ? null : Collections.unmodifiableList(list);
    }

    private <T> T header(InjectionPoint injectionPoint, Class<T> clazz) {
        final Map<String, Object> headers = allHeaders();
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

    private enum State {
        NO_DATA, RAW_DATA_AVAILABLE, DATA_AVAILABLE
    }
}
