package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;

class ProcessIncomingMessage extends ProcessIncomingImpl
        implements ProcessIncoming.Message {

    public ProcessIncomingMessage(Delivery delivery, String queue) {
        super(delivery, queue);
    }

}
