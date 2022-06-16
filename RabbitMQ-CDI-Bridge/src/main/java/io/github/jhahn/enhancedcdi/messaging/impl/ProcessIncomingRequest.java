package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming;

import java.util.Objects;
import java.util.Optional;

class ProcessIncomingRequest extends ProcessIncomingImpl implements ProcessIncoming.Request {

    ImmediateResponse immediateResponse;

    ProcessIncomingRequest(Delivery delivery, String queue) {
        super(delivery, queue);
        immediateResponse = null;
    }

    @Override
    public Optional<ImmediateResponse> immediateResponse() {
        return Optional.ofNullable(immediateResponse);
    }

    @Override
    public void setImmediateResponse(ImmediateResponse response) {
        if (this.immediateResponse != null) {
            throw new IllegalStateException("immediateResponse already set");
        }

        final BasicProperties requestProperties = properties();
        if (!Objects.equals(requestProperties.getCorrelationId(), response.properties().getCorrelationId())) {
            throw new IllegalArgumentException("");
        }
        this.immediateResponse = response;
        veto();
    }
}
