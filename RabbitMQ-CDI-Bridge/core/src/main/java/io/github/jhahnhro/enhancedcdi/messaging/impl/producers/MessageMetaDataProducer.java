package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahnhro.enhancedcdi.messaging.Exchange;
import io.github.jhahnhro.enhancedcdi.messaging.Header;
import io.github.jhahnhro.enhancedcdi.messaging.Queue;
import io.github.jhahnhro.enhancedcdi.messaging.RoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;

@RequestScoped
public class MessageMetaDataProducer {
    private Delivery delivery = null;
    private String queue = null;
    private Incoming<?> incomingMessage = null;
    private Acknowledgment acknowledgment = null;
    private Outgoing.Response.Builder<?, ?> responseBuilder = null;

    public void setDelivery(Delivery delivery, String queue, Acknowledgment acknowledgment) {
        this.delivery = delivery;
        this.queue = queue;
        this.acknowledgment = acknowledgment;
    }

    public void setMessage(final Incoming<?> incomingMessage) {
        checkDelivery(); // setDelivery must  be called first
        this.incomingMessage = incomingMessage;

        if (this.incomingMessage instanceof Incoming.Request<?> request) {
            this.responseBuilder = request.newResponseBuilder();
        }
    }

    private void checkDelivery() {
        if (this.delivery == null) {
            throw new IllegalStateException("No RabbitMQ message has been received in the current RequestScope");
        }
    }

    private void checkRequest() {
        checkDelivery();
        if (responseBuilder == null) {
            throw new RpcNotActiveException();
        }
    }

    @Produces
    @Dependent
    <T> Incoming<T> deserializedMessage() {
        checkDelivery();
        return (Incoming<T>) this.incomingMessage;
    }

    @Produces
    @Dependent
    Acknowledgment acknowledgement() {
        checkDelivery();
        return acknowledgment;
    }

    @Produces
    @Dependent
    <REQ, RES> Outgoing.Response.Builder<REQ, RES> responseBuilder(InjectionPoint ip) {
        checkRequest();
        Type typeRES = ((ParameterizedType) ip.getType()).getActualTypeArguments()[1];
        this.responseBuilder.setType(typeRES);
        return (Outgoing.Response.Builder<REQ, RES>) this.responseBuilder;
    }

    @Produces
    @RoutingKey
    @Dependent
    String routingKey() {
        checkDelivery();
        return delivery.getEnvelope().getRoutingKey();
    }

    @Produces
    @Exchange
    @Dependent
    String exchange() {
        checkDelivery();
        return delivery.getEnvelope().getExchange();
    }

    @Produces
    @Queue
    @Dependent
    String queue() {
        checkDelivery();
        return this.queue;
    }

    @Produces
    @Dependent
    BasicProperties basicProperties() {
        checkDelivery();
        return delivery.getProperties();
    }

    //region header objects
    @Produces
    @Dependent
    @Headers
    Map<String, Object> allHeaders() {
        checkDelivery();
        return basicProperties().getHeaders();
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
        return list == null ? null : List.copyOf(list);
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
}
