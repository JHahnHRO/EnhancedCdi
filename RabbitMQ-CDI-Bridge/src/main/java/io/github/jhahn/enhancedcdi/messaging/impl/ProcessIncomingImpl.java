package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserializer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

abstract class ProcessIncomingImpl extends ProcessDeliveryImpl {
    protected final Delivery originalDelivery;
    protected final String queue;
    protected InputStream stream;
    protected Deserializer<?> deserializer;

    ProcessIncomingImpl(Delivery delivery, String queue) {
        super(delivery.getEnvelope().getExchange(),
              delivery.getEnvelope().getRoutingKey());
        this.originalDelivery = delivery;
        this.queue = queue;
        this.stream = new ByteArrayInputStream(delivery.getBody());
        this.deserializer = null;
    }

    public BasicProperties properties() {
        return originalDelivery.getProperties();
    }

    public String queue() {
        return queue;
    }

    public InputStream body() {
        return stream;
    }

    public void setBody(InputStream inputStream) {
        this.stream = inputStream;
    }

    public void setDeserializer(Deserializer<?> deserializer) {
        this.deserializer = deserializer;
    }
}
