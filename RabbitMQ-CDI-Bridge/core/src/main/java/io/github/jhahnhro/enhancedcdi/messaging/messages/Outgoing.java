package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.lang.reflect.Type;
import java.util.UUID;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.types.Types;


/**
 * Represents an outgoing RabbitMQ message. Can either be a {@link Cast}, i.e. a fire-and-forget message, or a
 * {@link Request}, i.e. a message with its {@link BasicProperties#getReplyTo() replyTo property} set to a non-null
 * value, or a {@link Response}, i.e. a response to a previously received {@link Incoming.Request}.
 *
 * @param <T> the type of the payload, e.g. {@code byte[]} for messages in their serialized form, {@code OutputStream}
 *            during serialization from Java objects to byte streams, or any Java type before serialization.
 */
public sealed interface Outgoing<T> extends Message<T> {

    /**
     * The runtime type of this message's content, i.e. it could be {@code List<Integer>} if
     * {@code content().getClass() == List.class}.
     *
     * @return the runtime type of this message's content
     */
    Type type();

    //region private helper methods
    private static <X> void validate(String exchange, String routingKey, AMQP.BasicProperties properties, X content,
                                     Type type) {
        requireNonNull(exchange, "exchange");
        requireNonNull(routingKey, "routingKey");
        validateProperties(properties);
        validateType(content, type);
    }

    private static void validateType(Object content, Type type) {
        requireNonNull(content, "content");
        requireNonNull(type, "type");

        if (!Types.erasure(type).isAssignableFrom(content.getClass())) {
            throw new IllegalArgumentException(
                    "content class %s is not compatible with given type %s".formatted(content.getClass(), type));
        }
    }

    private static void validateProperties(AMQP.BasicProperties properties) {
        requireNonNull(properties, "properties");

        final int deliveryMode = properties.getDeliveryMode();
        if (deliveryMode != DeliveryMode.TRANSIENT.nr && deliveryMode != DeliveryMode.PERSISTENT.nr) {
            throw new IllegalArgumentException("BasicProperties.deliveryMode must be set to either 1 or 2");
        }
    }
    //endregion

    /**
     * @return A {@link MessageBuilder} initialized with the metadata and content of this message.
     */
    @SuppressWarnings("java:S1452")
    // Sonar does not like returning wildcards, but here it is necessary
    MessageBuilder<T, ?> builder();

    record Cast<T>(String exchange, String routingKey, AMQP.BasicProperties properties, T content, Type type)
            implements Outgoing<T> {

        public Cast {
            type = requireNonNullElse(type, content.getClass());
            validate(exchange, routingKey, properties, content, type);
        }

        public Cast(String exchange, String routingKey, AMQP.BasicProperties properties, T content) {
            this(exchange, routingKey, properties, content, content.getClass());
        }

        @Override
        public Builder<T> builder() {
            return new Builder<>(this.exchange(), this.routingKey(), DeliveryMode.PERSISTENT).setContent(this.content)
                    .setType(this.type)
                    .setProperties(this.properties);
        }

        public static final class Builder<T> extends MessageBuilder<T, Builder<T>> {

            public Builder(String exchange, String routingKey, DeliveryMode deliveryMode) {
                super(exchange, routingKey, deliveryMode);
            }

            public Builder<T> setExchange(String exchange) {
                this.exchange = requireNonNull(exchange);
                return this;
            }

            public Builder<T> setRoutingKey(String routingKey) {
                this.routingKey = requireNonNull(routingKey);
                return this;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <U> Builder<U> setContent(U content) {
                super.content = content;
                return (Builder<U>) this;
            }

            @Override
            public Cast<T> build() {
                return new Cast<>(exchange, routingKey, this.properties(), this.content(), this.type());
            }

        }
    }

    record Request<T>(String exchange, String routingKey, AMQP.BasicProperties properties, T content, Type type)
            implements Outgoing<T> {

        public Request {
            type = requireNonNullElse(type, content.getClass());
            validate(exchange, routingKey, properties, content, type);

            if (properties.getReplyTo() == null) {
                properties = properties.builder().replyTo(Topology.RABBITMQ_REPLY_TO).build();
            }
            if (properties.getCorrelationId() == null) {
                properties = properties.builder().correlationId(UUID.randomUUID().toString()).build();
            }
        }

        public Request(String exchange, String routingKey, AMQP.BasicProperties properties, T content) {
            this(exchange, routingKey, properties, content, content.getClass());
        }

        @Override
        public Builder<T> builder() {
            return new Builder<>(this.exchange(), this.routingKey(), DeliveryMode.PERSISTENT).setContent(this.content)
                    .setType(this.type)
                    .setProperties(this.properties);
        }

        public static final class Builder<T> extends MessageBuilder<T, Builder<T>> {

            public Builder(String exchange, String routingKey, DeliveryMode deliveryMode) {
                super(exchange, routingKey, deliveryMode);
            }

            public Builder<T> setExchange(String exchange) {
                this.exchange = requireNonNull(exchange);
                return this;
            }

            public Builder<T> setRoutingKey(String routingKey) {
                this.routingKey = requireNonNull(routingKey);
                return this;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <U> Builder<U> setContent(U content) {
                super.content = content;
                return (Builder<U>) self();
            }

            @Override
            public Request<T> build() {
                return new Request<>(exchange, routingKey, this.properties(), this.content(), this.type());
            }

        }
    }

    record Response<REQ, RES>(AMQP.BasicProperties properties, RES content, Type type, Incoming.Request<REQ> request)
            implements Outgoing<RES> {

        public Response {
            type = requireNonNullElse(type, content.getClass());
            validate("", request.properties().getReplyTo(), properties, content, type);

            if (properties.getCorrelationId() == null) {
                properties = properties.builder().correlationId(request.properties().getCorrelationId()).build();
            } else if (!request.properties().getCorrelationId().equals(properties.getCorrelationId())) {
                throw new IllegalArgumentException(
                        "The response does not belong to the request (correlation id differs).");
            }
        }

        public Response(AMQP.BasicProperties properties, RES content, Incoming.Request<REQ> request) {
            this(properties, content, content.getClass(), request);
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

        @Override
        public Response.Builder<REQ, RES> builder() {
            return new Builder<>(request).setContent(this.content).setType(this.type).setProperties(this.properties);
        }

        public static final class Builder<REQ, RES> extends MessageBuilder<RES, Builder<REQ, RES>> {

            private final Incoming.Request<REQ> request;

            public Builder(Incoming.Request<REQ> request) {
                super("", request.properties().getReplyTo(), request.deliveryMode());
                this.request = request;
                this.propertiesBuilder.correlationId(request.properties().getCorrelationId());
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> Builder<REQ, T> setContent(T content) {
                this.content = content;
                return (Builder<REQ, T>) this;
            }

            @Override
            public Outgoing.Response<REQ, RES> build() {
                return new Response<>(this.properties(), this.content(), this.type(), request);
            }

            public Incoming.Request<REQ> getRequest() {
                return request;
            }

        }
    }
}
