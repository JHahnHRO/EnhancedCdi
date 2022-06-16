package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessOutgoing;

class ProcessOutgoingRequest<T> extends ProcessOutgoingImpl<T> implements ProcessOutgoing.Request<T> {
    ProcessOutgoingRequest(Outgoing<T> message) {
        super(message, new PropertiesBuilderImpl().of(message.properties()));
    }

    @Override
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }
}
