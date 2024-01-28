package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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

    /**
     * builds the properties of the message
     */
    protected final AMQP.BasicProperties.Builder propertiesBuilder;
    /**
     * the headers of the message. Invariant: This is always the <i>same</i> map as in {@code propertiesBuilder}
     *
     * @see #syncHeaders(Map)
     */
    private final Map<String, Object> headers;

    protected Object content = null; // mutable for all
    protected Type type = null; // mutable for all
    protected String exchange; // immutable for Response.Builder
    protected String routingKey; // immutable for Response.Builder

    protected MessageBuilder(final String exchange, final String routingKey, DeliveryMode deliveryMode) {
        this.headers = new HashMap<>();
        this.propertiesBuilder = new AMQP.BasicProperties.Builder().deliveryMode(deliveryMode.nr).headers(headers);
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

    /**
     * Accepts a lambda that configures the message's {@link com.rabbitmq.client.AMQP.BasicProperties.Builder}.
     * <p>
     * If the lambda sets new headers and/or the timestamp, defensive copies will be created.
     *
     * @param configurator a lambda
     * @return
     */
    public SELF configureProperties(Consumer<AMQP.BasicProperties.Builder> configurator) {
        configurator.accept(this.propertiesBuilder);
        final AMQP.BasicProperties newProperties = this.propertiesBuilder.build();
        syncHeaders(newProperties.getHeaders());
        syncTimestamp(newProperties.getTimestamp());
        return self();
    }

    public SELF setProperties(BasicProperties properties) {
        syncHeaders(properties.getHeaders());
        syncTimestamp(properties.getTimestamp());

        this.propertiesBuilder.contentType(properties.getContentType())
                .contentEncoding(properties.getContentEncoding())
                .deliveryMode(properties.getDeliveryMode())
                .priority(properties.getPriority())
                .correlationId(properties.getCorrelationId())
                .replyTo(properties.getReplyTo())
                .expiration(properties.getExpiration())
                .messageId(properties.getMessageId())
                .type(properties.getType())
                .userId(properties.getUserId())
                .appId(properties.getAppId());

        return self();
    }

    private void syncTimestamp(Date newTimestamp) {
        if (newTimestamp != null) {
            newTimestamp = (Date) newTimestamp.clone();
        }
        this.propertiesBuilder.timestamp(newTimestamp);
    }

    /**
     * Ensures that {@code this.headers} and the headers in {@code this.propertiesBuilder} are the same map by creating
     * a fresh copy of the argument and overwriting both with it.
     *
     * @param newHeaders the new headers.
     */
    private void syncHeaders(final Map<String, Object> newHeaders) {
        this.headers.clear();
        if (newHeaders != null) {
            this.headers.putAll(newHeaders);
        }
        this.propertiesBuilder.headers(this.headers);
    }

    /**
     * Returns a <b>modifiable</b> map. Upon calling {@link #build()}, that map is written into the propertiesBuilder
     * before the message's properties are being built so that the final message will have these headers.
     *
     * @return the (mutable) map of headers currently in the {@link #propertiesBuilder()}. Never null, but may be empty.
     */
    @Override
    public Map<String, Object> getHeaders() {
        return headers;
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
     * @return this builder (with new type bounds)
     */
    @SuppressWarnings("java:S1452") // Sonar does not like returning wildcards, but here it is necessary
    public abstract <U> MessageBuilder<U, ?> setContent(U content);

    public Type type() {
        return type;
    }

    /**
     * Sets the type
     *
     * @param type the new type
     * @return this builder (with the same type bounds)
     */
    public SELF setType(Type type) {
        this.type = type;
        return self();
    }

    /**
     * Sets the type
     *
     * @param type the new type
     * @param <U>  the new type
     * @return this builder (with the new type bounds)
     */
    @SuppressWarnings("java:S1452") // Sonar does not like returning wildcards, but here it is necessary
    public abstract <U> MessageBuilder<U, ?> setType(Class<U> type);

    public abstract Outgoing<T> build();
}
