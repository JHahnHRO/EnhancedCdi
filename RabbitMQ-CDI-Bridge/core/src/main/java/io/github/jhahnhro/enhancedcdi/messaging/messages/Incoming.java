package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static java.util.Objects.requireNonNull;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;

/**
 * Represents an incoming RabbitMQ message. Can either be a {@link Cast}, i.e. a fire-and-forget message, or a
 * {@link Request}, i.e. a message with its {@link BasicProperties#getReplyTo() replyTo property} set to a non-null
 * value, or a {@link Response}, i.e. a response to a previously sent {@link Outgoing.Request}.
 *
 * @param <T> the type of the payload, e.g. {@code byte[]} for messages in their serialized form, {@code InputStream}
 *            during de-serialization from bytes to Java objects, or any Java type after de-serialization.
 */
public sealed interface Incoming<T> extends Message<T> {

    private static void validate(Delivery delivery) {
        requireNonNull(delivery, "delivery");
        requireNonNull(delivery.getEnvelope(), "envelope of the delivery");
        requireNonNull(delivery.getProperties(), "properties");

        final int deliveryMode = delivery.getProperties().getDeliveryMode();
        if (deliveryMode != DeliveryMode.TRANSIENT.nr && deliveryMode != DeliveryMode.PERSISTENT.nr) {
            throw new IllegalArgumentException("BasicProperties.deliveryMode must be set to either 1 or 2");
        }
    }

    @Override
    default String exchange() {
        return delivery().getEnvelope().getExchange();
    }

    @Override
    default String routingKey() {
        return delivery().getEnvelope().getRoutingKey();
    }

    @Override
    default AMQP.BasicProperties properties() {
        return delivery().getProperties();
    }

    /**
     * Returns a new {@link Incoming} message of the same type as {@code this}, but with the given
     * {@link #content() content} instead. This message is not modified in any way.
     *
     * @param newContent content of the new incoming message.
     * @param <U>        type of the new content
     * @return a new {@link Incoming} message of the same type as {@code this}, but with the given
     * {@link #content() content} instead.
     */
    <U> Incoming<U> withContent(U newContent);

    /**
     * @return the name of the queue the delivery was received on. Maybe an auto-generated name for a temporary queue
     * that no longer exists (or never existed).
     */
    String queue();

    /**
     * @return the raw delivery that is represented by this incoming message.
     */
    Delivery delivery();

    record Cast<T>(Delivery delivery, String queue, T content) implements Incoming<T> {
        public Cast {
            validate(delivery);
            requireNonNull(queue, "queue");
        }

        @Override
        public <U> Incoming.Cast<U> withContent(U newContent) {
            return new Cast<>(delivery, queue, newContent);
        }
    }

    record Request<T>(Delivery delivery, String queue, T content) implements Incoming<T> {

        public Request {
            validate(delivery);
            requireNonNull(queue, "queue");

            requireNonNull(delivery.getProperties().getReplyTo(), "replyTo property");
            requireNonNull(delivery.getProperties().getCorrelationId(), "correlationId property");
        }

        @Override
        public <U> Incoming.Request<U> withContent(U newContent) {
            return new Request<>(delivery, queue, newContent);
        }
    }

    record Response<REQ, RES>(Delivery delivery, RES content, Outgoing.Request<REQ> request) implements Incoming<RES> {
        public Response {
            validate(delivery);
            requireNonNull(request, "request");
        }

        @Override
        public String queue() {
            return request.properties().getReplyTo();
        }

        @Override
        public <U> Incoming.Response<REQ, U> withContent(U newContent) {
            return new Response<>(delivery, newContent, request);
        }
    }
}
