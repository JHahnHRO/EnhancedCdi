package io.github.jhahn.enhancedcdi.messaging.impl;


import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

abstract class ProcessOutgoingImpl<T> extends ProcessDeliveryImpl {
    protected final T content;
    protected final PropertiesBuilderImpl propertiesBuilder;
    protected final ByteArrayOutputStream finalBody;
    protected OutputStream currentBody;
    protected Serializer<T> serializer;


    ProcessOutgoingImpl(Outgoing<T> outgoing, PropertiesBuilderImpl propertiesBuilder) {
        super(outgoing.exchange(), outgoing.routingKey());
        this.propertiesBuilder = propertiesBuilder;
        this.content = outgoing.content();
        this.currentBody = this.finalBody = new ByteArrayOutputStream();
        this.serializer = null;
    }

    public PropertiesBuilderImpl properties() {
        return propertiesBuilder;
    }

    public OutputStream body() {
        return currentBody;
    }

    public T content() {
        return content;
    }

    public void setSerializer(Serializer<T> serializer) {
        this.serializer = serializer;
    }

    public void setBody(OutputStream outputStream) {
        this.currentBody = outputStream;
    }
}
