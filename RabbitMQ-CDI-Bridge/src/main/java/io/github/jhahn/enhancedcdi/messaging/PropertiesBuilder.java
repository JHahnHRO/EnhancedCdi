package io.github.jhahn.enhancedcdi.messaging;

import com.rabbitmq.client.BasicProperties;

import java.time.Instant;
import java.util.Map;

public interface PropertiesBuilder extends BasicProperties {

    PropertiesBuilder setContentType(String contentType);

    PropertiesBuilder setContentEncoding(String contentEncoding);

    PropertiesBuilder setHeaders(Map<String, Object> headers);

    PropertiesBuilder addHeaders(Map<String, Object> headers);

    PropertiesBuilder addHeader(String name, Object value);

    PropertiesBuilder setDeliveryMode(Integer deliveryMode);

    PropertiesBuilder setPriority(Integer priority);

    /**
     * @param correlationId new correlation ID
     * @return self
     * @throws UnsupportedOperationException if this builder is for an RPC response message.
     */
    PropertiesBuilder setCorrelationId(String correlationId) throws UnsupportedOperationException;

    PropertiesBuilder setReplyTo(String replyTo);

    PropertiesBuilder setExpiration(String expiration);

    PropertiesBuilder setMessageId(String messageId);

    PropertiesBuilder setTimestamp(Instant timestamp);

    PropertiesBuilder setType(String type);

    PropertiesBuilder setUserId(String userId);

    PropertiesBuilder setAppId(String appId);

    default PropertiesBuilder of(BasicProperties properties) {
        return this.setContentType(properties.getContentType())
                .setContentEncoding(properties.getContentEncoding())
                .setHeaders(properties.getHeaders())
                .setDeliveryMode(properties.getDeliveryMode())
                .setPriority(properties.getPriority())
                .setCorrelationId(properties.getCorrelationId())
                .setReplyTo(properties.getReplyTo())
                .setExpiration(properties.getExpiration())
                .setMessageId(properties.getMessageId())
                .setTimestamp(properties.getTimestamp().toInstant())
                .setType(properties.getType())
                .setUserId(properties.getUserId())
                .setAppId(properties.getAppId());
    }

    BasicProperties build();
}
