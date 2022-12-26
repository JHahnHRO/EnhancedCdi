package io.github.jhahnhro.enhancedcdi.messaging.messages;

import com.rabbitmq.client.AMQP;

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
     * @return the content of the message.
     */
    T content();
}
