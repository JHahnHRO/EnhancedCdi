package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessOutgoing;

class ProcessOutgoingBroadcast<T> extends ProcessOutgoingImpl<T> implements ProcessOutgoing.Broadcast<T> {
    ProcessOutgoingBroadcast(Outgoing<T> message) {
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
