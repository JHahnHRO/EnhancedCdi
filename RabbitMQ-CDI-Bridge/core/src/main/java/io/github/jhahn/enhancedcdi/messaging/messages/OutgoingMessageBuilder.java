package io.github.jhahn.enhancedcdi.messaging.messages;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;

import java.util.Objects;
import java.util.Optional;

public final class OutgoingMessageBuilder<REQ, RES> implements Message<RES> {
    private final Incoming.Request<REQ> request;
    private final AMQP.BasicProperties.Builder propertiesBuilder;
    private Object content;
    private String exchange = null;
    private String routingKey = null;

    public OutgoingMessageBuilder() {
        this(null);
    }

    public OutgoingMessageBuilder(RES content) {
        this(content, null);
    }

    public OutgoingMessageBuilder(RES content, Incoming.Request<REQ> request) {
        this.content = content;
        this.propertiesBuilder = new AMQP.BasicProperties.Builder();
        this.request = request;
        if (request != null) {
            this.exchange = "";
            this.routingKey = request.properties().getReplyTo();
            this.propertiesBuilder.correlationId(request.properties().getCorrelationId());
        }
    }

    @Override
    public String exchange() {
        return exchange;
    }

    /**
     * @param exchange name of the exchange the message will be published to
     * @return this
     * @throws UnsupportedOperationException if this builder is building an {@link Outgoing.Response}, because RPC
     *                                       responses will always be published to the default exchange whose name is
     *                                       the empty string.
     */
    public OutgoingMessageBuilder<REQ, RES> setExchange(String exchange) {
        if (request != null && !this.exchange.equals(exchange)) {
            throw new UnsupportedOperationException(
                    "Response messages cannot have an exchange other then the default \"\"");
        }
        this.exchange = Objects.requireNonNull(exchange);
        return this;
    }

    @Override
    public String routingKey() {
        return routingKey;
    }

    /**
     * @param routingKey routing key the message will be published with
     * @return this
     * @throws UnsupportedOperationException if this builder is building an {@link Outgoing.Response}, because RPC
     *                                       responses will always be published with the corresponding request's
     *                                       {@link BasicProperties#getReplyTo() replyTo property} as its routing key.
     */
    public OutgoingMessageBuilder<REQ, RES> setRoutingKey(String routingKey) {
        if (request != null && !this.routingKey.equals(routingKey)) {
            throw new UnsupportedOperationException(
                    "Response messages cannot have anything other than the corresponding request's replyTo property "
                    + "as routing key");
        }
        this.routingKey = Objects.requireNonNull(routingKey);
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

    public OutgoingMessageBuilder<REQ, RES> setProperties(BasicProperties properties) {
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

    @Override
    public RES content() {
        return (RES) this.content;
    }

    public <T> OutgoingMessageBuilder<REQ, T> setContent(T content) {
        this.content = content;
        return (OutgoingMessageBuilder<REQ, T>) this;
    }

    public Optional<Incoming.Request<REQ>> getRequest() {
        return Optional.ofNullable(this.request);
    }

    public Outgoing<RES> build() {
        final AMQP.BasicProperties.Builder builder = this.propertiesBuilder;
        if (request != null) {
            builder.correlationId(request.properties().getCorrelationId());
            return new Outgoing.Response<>(builder.build(), content(), request);
        }

        final AMQP.BasicProperties properties = builder.build();
        if (properties.getReplyTo() != null) {
            return new Outgoing.Request<>(exchange(), routingKey(), properties, content());
        }
        return new Outgoing.Cast<>(exchange(), routingKey(), properties, content());
    }
}
