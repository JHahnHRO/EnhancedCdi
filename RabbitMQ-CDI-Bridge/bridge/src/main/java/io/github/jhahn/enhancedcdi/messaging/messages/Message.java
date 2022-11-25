package io.github.jhahn.enhancedcdi.messaging.messages;

import com.rabbitmq.client.AMQP;

/**
 * Common supertype of events that are fired for both incoming and outgoing deliveries.
 */
public sealed interface Message<T> permits Incoming, Outgoing, OutgoingMessageBuilder {
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
