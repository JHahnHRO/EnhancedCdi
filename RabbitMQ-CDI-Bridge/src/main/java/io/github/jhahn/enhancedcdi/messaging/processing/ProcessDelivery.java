package io.github.jhahn.enhancedcdi.messaging.processing;

import com.rabbitmq.client.BasicProperties;

/**
 * Common supertype of events that are fired for both incoming and outgoing deliveries.
 */
public sealed interface ProcessDelivery permits ProcessIncoming, ProcessOutgoing {
    /**
     * @return the name of the exchange the delivery was received from / will be published to
     */
    String exchange();

    /**
     * @return the routing key of the delivery
     */
    String routingKey();

    /**
     * @return the properties of the RabbitMQ delivery. Immutable for incoming deliveries, mutable for outgoing
     * deliveries.
     */
    BasicProperties properties();

    /**
     * If called, no further handling of the delivery will happen after the observers of this event are finished, i.e.
     * no message will be published to the broker. The method is idempotent.
     * <p>
     * There is no way to "un-veto" in later observer methods once {@code veto()} has been called.
     */
    void veto();
}
