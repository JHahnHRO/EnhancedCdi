package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessOutgoing;

class ProcessOutgoingMessage<T> extends ProcessOutgoingImpl<T> implements ProcessOutgoing.Message<T> {
    ProcessOutgoingMessage(Outgoing<T> message) {
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
