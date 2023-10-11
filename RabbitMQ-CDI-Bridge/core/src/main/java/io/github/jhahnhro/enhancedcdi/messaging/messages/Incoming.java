package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static java.util.Objects.requireNonNull;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;

/**
 * Represents an incoming RabbitMQ message. Can either be a {@link Cast}, i.e. a fire-and-forget message, or a
 * {@link Request}, i.e. a message with its {@link BasicProperties#getReplyTo() replyTo property} set to a non-null
 * value, or a {@link Response}, i.e. a response to a previously sent {@link Outgoing.Request}.
 *
 * @param <T> the type of the payload, e.g. {@code byte[]} for messages in their serialized form, {@code InputStream}
 *            during de-serialization from bytes to Java objects, or any Java type after de-serialization.
 */
public sealed interface Incoming<T> extends Message<T> {

    private static void validate(final Envelope envelope, final AMQP.BasicProperties properties) {
        requireNonNull(envelope, "envelope of the delivery");
        requireNonNull(properties, "properties");

        final int deliveryMode = properties.getDeliveryMode();
        if (deliveryMode != DeliveryMode.TRANSIENT.nr && deliveryMode != DeliveryMode.PERSISTENT.nr) {
            throw new IllegalArgumentException("BasicProperties.deliveryMode must be set to either 1 or 2");
        }
    }

    /**
     * @return The envelope of the original {@link com.rabbitmq.client.Delivery}
     */
    Envelope envelope();

    @Override
    default String exchange() {
        return envelope().getExchange();
    }

    @Override
    default String routingKey() {
        return envelope().getRoutingKey();
    }

    @Override
    AMQP.BasicProperties properties();

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

    record Cast<T>(String queue, Envelope envelope, AMQP.BasicProperties properties, T content) implements Incoming<T> {
        public Cast {
            requireNonNull(queue, "queue");
            validate(envelope, properties);
        }

        @Override
        public <U> Incoming.Cast<U> withContent(U newContent) {
            return new Cast<>(queue, envelope, properties, newContent);
        }
    }

    record Request<T>(String queue, Envelope envelope, AMQP.BasicProperties properties, T content)
            implements Incoming<T> {

        public Request {
            requireNonNull(queue, "queue");
            validate(envelope, properties);

            requireNonNull(properties.getReplyTo(), "replyTo property");
            requireNonNull(properties.getCorrelationId(), "correlationId property");
        }

        @Override
        public <U> Incoming.Request<U> withContent(U newContent) {
            return new Request<>(queue, envelope, properties, newContent);
        }

        /**
         * @return a new {@link Outgoing.Response.Builder} that can be used to build a response for this request.
         */
        public Outgoing.Response.Builder<T, Object> newResponseBuilder() {
            return new Outgoing.Response.Builder<>(this);
        }
    }

    record Response<REQ, RES>(Envelope envelope, AMQP.BasicProperties properties, RES content,
                              Outgoing.Request<REQ> request) implements Incoming<RES> {
        public Response {
            requireNonNull(request, "request");
            validate(envelope, properties);
        }

        @Override
        public String queue() {
            return request.properties().getReplyTo();
        }

        @Override
        public <U> Incoming.Response<REQ, U> withContent(U newContent) {
            return new Response<>(envelope, properties, newContent, request);
        }
    }
}
