package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.Outgoing;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RequestScoped
@Outgoing
class PropertiesBuilderImpl implements io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder {
    private String contentType;
    private String contentEncoding;
    private final Map<String, Object> headers = new HashMap<>();
    private Integer deliveryMode;
    private Integer priority;
    private String replyTo;
    private String correlationId;
    private String expiration;
    private String messageId;
    private Instant timestamp;
    private String type;
    private String userId;
    private String appId;

    private final PropertiesBuilderImpl self;

    /**
     * Creates an unmanaged instance
     */
    PropertiesBuilderImpl() {
        this.self = this;
    }

    @Inject
    PropertiesBuilderImpl(@Outgoing PropertiesBuilderImpl self) {
        this.self = self;
    }

    //region getters
    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public Map<String, Object> getHeaders() {
        return headers;
    }

    @Override
    public Integer getDeliveryMode() {
        return deliveryMode;
    }

    @Override
    public Integer getPriority() {
        return priority;
    }

    /**
     * @return the current correlationId. Note that this value will be overwritten with the correlationId of the request
     * for RPC responses.
     */
    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public String getReplyTo() {
        return replyTo;
    }

    @Override
    public String getExpiration() {
        return expiration;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public Date getTimestamp() {
        return Date.from(timestamp);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getAppId() {
        return appId;
    }
    //endregion

    //region setters
    @Override
    public PropertiesBuilderImpl setContentType(String contentType) {
        this.contentType = contentType;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setHeaders(Map<String, Object> headers) {
        this.headers.clear();
        this.headers.putAll(headers);
        return self;
    }

    @Override
    public PropertiesBuilderImpl addHeaders(Map<String, Object> headers) {
        return null;
    }

    @Override
    public PropertiesBuilderImpl addHeader(String name, Object value) {
        return null;
    }

    @Override
    public PropertiesBuilderImpl setDeliveryMode(Integer deliveryMode) {
        this.deliveryMode = deliveryMode;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setPriority(Integer priority) {
        this.priority = priority;
        return self;
    }

    public PropertiesBuilderImpl setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return self;
    }

    public PropertiesBuilderImpl setReplyTo(String replyTo) {
        this.replyTo = replyTo;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setExpiration(String expiration) {
        this.expiration = expiration;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setMessageId(String messageId) {
        this.messageId = messageId;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return self;
    }


    @Override
    public PropertiesBuilderImpl setType(String type) {
        this.type = type;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setUserId(String userId) {
        this.userId = userId;
        return self;
    }

    @Override
    public PropertiesBuilderImpl setAppId(String appId) {
        this.appId = appId;
        return self;
    }
    //endregion


    @Override
    public PropertiesBuilderImpl of(BasicProperties properties) {
        return (PropertiesBuilderImpl) io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder.super.of(properties);
    }

    @Override
    public AMQP.BasicProperties build() {
        return new AMQP.BasicProperties(contentType, contentEncoding, headers, deliveryMode, priority, correlationId,
                                        replyTo, expiration, messageId, Date.from(timestamp), type, userId, appId,
                                        null);
    }
}
