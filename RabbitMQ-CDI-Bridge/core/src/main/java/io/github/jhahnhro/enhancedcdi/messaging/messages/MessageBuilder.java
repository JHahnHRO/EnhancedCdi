package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;

/**
 * Common superclass of the three Builder classes for the subclasses of {@link Outgoing}.
 *
 * @param <T>    type of the content the final message will have
 * @param <SELF> the concrete subclass extending this class
 */
public abstract sealed class MessageBuilder<T, SELF extends MessageBuilder<T, SELF>> implements Message<T>
        permits Outgoing.Cast.Builder, Outgoing.Request.Builder, Outgoing.Response.Builder {

    protected final AMQP.BasicProperties.Builder propertiesBuilder;

    protected Object content = null; // mutable for all
    protected Type type = null; // mutable for all
    protected String exchange; // immutable for Response.Builder
    protected String routingKey; // immutable for Response.Builder

    protected MessageBuilder(final String exchange, final String routingKey, DeliveryMode deliveryMode) {
        this.propertiesBuilder = new AMQP.BasicProperties.Builder().deliveryMode(deliveryMode.nr);
        this.exchange = requireNonNull(exchange);
        this.routingKey = requireNonNull(routingKey);
    }

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    @Override
    public String exchange() {
        return exchange;
    }

    @Override
    public String routingKey() {
        return routingKey;
    }

    public SELF setDeliveryMode(DeliveryMode deliveryMode) {
        this.propertiesBuilder.deliveryMode(deliveryMode.nr);
        return self();
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

    public SELF setProperties(BasicProperties properties) {
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

        return self();
    }

    /**
     * Returns a <b>mutable</b> map containing all {@link BasicProperties#getHeaders() headers} currently contained in
     * the {@link #propertiesBuilder()}. The returned map is also written to the builder so any changes to it will be
     * reflected in the final message (except if {@link AMQP.BasicProperties.Builder#headers(Map)} is not called)
     *
     * @return the (mutable) map of headers currently in the {@link #propertiesBuilder()}. Never null, but may be empty.
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
    public T content() {
        return (T) this.content;
    }

    /**
     * Sets the content.
     *
     * @param content the new content
     * @param <U>     type of the content
     * @return this build (with new type bounds)
     */
    @SuppressWarnings("java:S1452") // Sonar does not like returning wildcards, but here it is necessary
    public abstract <U> MessageBuilder<U, ?> setContent(U content);

    public Type type() {
        return type;
    }

    public SELF setType(Type type) {
        this.type = type;
        return self();
    }

    public Outgoing<T> build() {
        final AMQP.BasicProperties properties = properties();
        if (properties.getReplyTo() != null) {
            return new Outgoing.Request<>(exchange(), routingKey(), properties, content(), type());
        }
        return new Outgoing.Cast<>(exchange(), routingKey(), properties, content(), type());
    }

}
