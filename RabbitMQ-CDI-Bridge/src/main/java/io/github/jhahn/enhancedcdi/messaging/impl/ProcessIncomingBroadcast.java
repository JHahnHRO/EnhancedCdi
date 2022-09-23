package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;

class ProcessIncomingBroadcast extends ProcessIncomingImpl implements ProcessIncoming.Broadcast {

    public ProcessIncomingBroadcast(Delivery delivery, String queue) {
        super(delivery, queue);
    }

}
