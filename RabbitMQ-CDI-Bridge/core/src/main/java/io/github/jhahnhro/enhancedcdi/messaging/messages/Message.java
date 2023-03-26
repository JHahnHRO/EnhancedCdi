package io.github.jhahnhro.enhancedcdi.messaging.messages;

import java.util.Map;
import java.util.Optional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;

/**
 * Common supertype of incoming and outgoing messages.
 */
public sealed interface Message<T> permits Incoming, Outgoing, Outgoing.Builder {
    /**
     * @return the name of the exchange the message was received from / will be published to
     */
    String exchange();

    /**
     * @return the routing key of the message
     */
    String routingKey();

    /**
     * @return the properties of the message.
     */
    AMQP.BasicProperties properties();

    /**
     * Convenience method to access a single {@link BasicProperties#getHeaders() header in the message's properties}.
     *
     * @param key name of the header
     * @return the header value. May be null if the header is not present.
     */
    default Optional<Object> getHeader(String key) {
        return Optional.ofNullable(getHeaders()).map(m -> m.get(key));
    }

    /**
     * Convenience method to access the {@link BasicProperties#getHeaders() headers in the message's properties}.
     *
     * @return the headers. May be null if no headers are present.
     */
    default Map<String, Object> getHeaders() {
        return this.properties().getHeaders();
    }

    /**
     * @return the content of the message.
     */
    T content();
}
