package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;

class ProcessIncomingResponse extends ProcessIncomingImpl
        implements ProcessIncoming.Response {

    public ProcessIncomingResponse(DeliveryFromQueue delivery) {
        super(delivery.message(), delivery.queue());
    }

    @Override
    public Delivery request() {
        return null;
    }
}
