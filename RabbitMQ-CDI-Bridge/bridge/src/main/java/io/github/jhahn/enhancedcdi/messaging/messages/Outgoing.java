package io.github.jhahn.enhancedcdi.messaging.messages;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.Topology;

import java.util.UUID;


/**
 * Represents an outgoing RabbitMQ message. Can either be a {@link Cast}, i.e. a fire-and-forget message, or a
 * {@link Request}, i.e. a message with its {@link BasicProperties#getReplyTo() replyTo property} set to a non-null
 * value, or a {@link Response}, i.e. a response to a previously received {@link Incoming.Request}.
 *
 * @param <T> the type of the payload, e.g. {@code byte[]} for messages in their serialized form, {@code OutputStream}
 *            during serialization from Java objects to byte streams, or any Java type before serialization.
 */
public sealed interface Outgoing<T> extends Message<T> {

    private static void requireNonNull(Object param, String name) {
        if (param == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
    }

    record Cast<T>(String exchange, String routingKey, AMQP.BasicProperties properties, T content)
            implements Outgoing<T> {
        public Cast {
            requireNonNull(exchange, "exchange");
            requireNonNull(routingKey, "routingKey");
            requireNonNull(properties, "properties");
        }
    }

    record Request<T>(String exchange, String routingKey, AMQP.BasicProperties properties, T content)
            implements Outgoing<T> {
        public Request {
            requireNonNull(exchange, "exchange");
            requireNonNull(routingKey, "routingKey");
            requireNonNull(properties, "properties");

            if (properties.getReplyTo() == null) {
                properties = properties.builder().replyTo(Topology.RABBITMQ_REPLY_TO).build();
            }
            if (properties.getCorrelationId() == null) {
                properties = properties.builder().correlationId(UUID.randomUUID().toString()).build();
            }
        }
    }

    record Response<REQ, RES>(AMQP.BasicProperties properties, RES content, Incoming.Request<REQ> request)
            implements Outgoing<RES> {

        public Response {
            requireNonNull(properties, "properties");
            requireNonNull(request, "request");

            if (!properties.getCorrelationId().equals(request.properties().getCorrelationId())) {
                throw new IllegalArgumentException(
                        "The response does not belong to the request (correlation id differs).");
            }
        }

        /**
         * @return the name of the exchange of the outgoing RPC response, which is always the empty string, i.e. the
         * built-in default exchange.
         */
        @Override
        public String exchange() {
            return "";
        }

        /**
         * @return the routing key of the outgoing RPC response, which is always equal to the
         * {@link BasicProperties#getReplyTo() replyTo property} of the request.
         */
        @Override
        public String routingKey() {
            return this.request().properties().getReplyTo();
        }
    }
}
