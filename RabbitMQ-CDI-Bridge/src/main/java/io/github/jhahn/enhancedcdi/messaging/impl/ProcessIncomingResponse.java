package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;

class ProcessIncomingResponse<R> extends ProcessIncomingImpl implements ProcessIncoming.Response<R> {

    private final Outgoing<R> request;

    public ProcessIncomingResponse(Delivery response, Outgoing<R> request) {
        super(response, request.properties().getReplyTo());
        this.request = request;
    }

    @Override
    public Outgoing<R> request() {
        return request;
    }
}
