package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

@RequestScoped
public class ResponseBuilderProducer {

    private OutgoingMessageBuilder<?, ?> responseBuilder = null;

    public void createResponseBuilderFor(Incoming.Request<?> request) {
        this.responseBuilder = new OutgoingMessageBuilder<>(null, request);
    }

    @Produces
    @Dependent
    public <REQ, RES> OutgoingMessageBuilder<REQ, RES> getResponseBuilder() {
        return (OutgoingMessageBuilder<REQ, RES>) responseBuilder;
    }
}
