package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * @return An {@link Builder} initialized with the metadata and content of this message.
     */
    Builder<T> builder();

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
            final Builder<T> builder = new Builder<>(this.exchange(), this.routingKey(), DeliveryMode.PERSISTENT);
            builder.setContent(this.content).setType(this.type).setProperties(this.properties);
            return builder;
        }

        public static final class Builder<T> extends Outgoing.Builder<T> {

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
            final Builder<T> builder = new Builder<>(this.exchange(), this.routingKey(), DeliveryMode.PERSISTENT);
            builder.setContent(this.content).setType(this.type).setProperties(this.properties);
            return builder;
        }

        public static final class Builder<T> extends Outgoing.Builder<T> {

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

            @Override
            public Request<T> build() {
                return new Request<>(exchange, routingKey, this.properties(), this.content(), this.type());
            }

        }
    }

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

        /**
         * @return An {@link Outgoing.Builder} initialized with the metadata and content of this message.
         */
        @Override
        public Response.Builder<REQ, RES> builder() {
            return new Builder<>(request).setContent(this.content).setType(this.type).setProperties(this.properties);
        }

        public static final class Builder<REQ, RES> extends Outgoing.Builder<RES> {

            private final Incoming.Request<REQ> request;

            public Builder(Incoming.Request<REQ> request) {
                super("", request.properties().getReplyTo(), request.deliveryMode());
                this.request = request;
                this.propertiesBuilder.correlationId(request.properties().getCorrelationId());
            }

            @Override
            public Builder<REQ, RES> setDeliveryMode(DeliveryMode deliveryMode) {
                super.setDeliveryMode(deliveryMode);
                return this;
            }

            @Override
            public Builder<REQ, RES> setProperties(BasicProperties properties) {
                super.setProperties(properties);
                return this;
            }

            @Override
            public <T> Builder<REQ, T> setContent(T content) {
                super.setContent(content);
                //noinspection unchecked
                return (Builder<REQ, T>) this;
            }

            @Override
            public Builder<REQ, RES> setType(Type type) {
                super.setType(type);
                return this;
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

    sealed class Builder<RES> implements Message<RES> permits Cast.Builder, Request.Builder, Response.Builder {

        protected final AMQP.BasicProperties.Builder propertiesBuilder;

        protected Object content = null; // mutable for all
        protected Type type = null; // mutable for all
        protected String exchange; // immutable for Response.Builder
        protected String routingKey; // immutable for Response.Builder

        public Builder(final String exchange, final String routingKey, DeliveryMode deliveryMode) {
            this.propertiesBuilder = new AMQP.BasicProperties.Builder().deliveryMode(deliveryMode.nr);
            this.exchange = requireNonNull(exchange);
            this.routingKey = requireNonNull(routingKey);
        }

        @Override
        public String exchange() {
            return exchange;
        }

        @Override
        public String routingKey() {
            return routingKey;
        }

        public Builder<RES> setDeliveryMode(DeliveryMode deliveryMode) {
            this.propertiesBuilder.deliveryMode(deliveryMode.nr);
            return this;
        }

        public AMQP.BasicProperties.Builder propertiesBuilder() {
            return propertiesBuilder;
        }

        /**
         * @return the current properties of the message being build.
         */
        @Override
        public AMQP.BasicProperties properties() {
            return this.propertiesBuilder.build();
        }

        public Builder<RES> setProperties(BasicProperties properties) {
            this.propertiesBuilder.contentType(properties.getContentType())
                    .contentEncoding(properties.getContentEncoding())
                    .headers(properties.getHeaders())
                    .deliveryMode(properties.getDeliveryMode())
                    .priority(properties.getPriority())
                    .correlationId(properties.getCorrelationId())
                    .replyTo(properties.getReplyTo())
                    .expiration(properties.getExpiration())
                    .messageId(properties.getMessageId())
                    .timestamp(properties.getTimestamp())
                    .type(properties.getType())
                    .userId(properties.getUserId())
                    .appId(properties.getAppId());

            return this;
        }

        /**
         * Returns a <b>mutable</b> map containing all {@link BasicProperties#getHeaders() headers} currently contained
         * in the {@link #propertiesBuilder()}. The returned map is also written to the builder so any changes to it
         * will be reflected in the final message (except if
         * {@link com.rabbitmq.client.AMQP.BasicProperties.Builder#headers(Map)} is not called)
         *
         * @return the (mutable) map of headers currently in the {@link #propertiesBuilder()}. Never null, but may be
         * empty.
         */
        @Override
        public Map<String, Object> getHeaders() {
            final Map<String, Object> prevHeaders = properties().getHeaders();
            Map<String, Object> mutableHeaders = prevHeaders == null ? new HashMap<>() : new HashMap<>(prevHeaders);
            propertiesBuilder.headers(mutableHeaders);
            return mutableHeaders;
        }

        @Override
        @SuppressWarnings("unchecked")
        public RES content() {
            return (RES) this.content;
        }

        @SuppressWarnings("unchecked")
        public <T> Builder<T> setContent(T content) {
            this.content = content;
            return (Builder<T>) this;
        }

        public Type type() {
            return type;
        }

        public Builder<RES> setType(Type type) {
            this.type = type;
            return this;
        }

        public Outgoing<RES> build() {
            final AMQP.BasicProperties properties = properties();
            if (properties.getReplyTo() != null) {
                return new Request<>(exchange(), routingKey(), properties, content(), type());
            }
            return new Cast<>(exchange(), routingKey(), properties, content(), type());
        }

    }
    //endregion
}
