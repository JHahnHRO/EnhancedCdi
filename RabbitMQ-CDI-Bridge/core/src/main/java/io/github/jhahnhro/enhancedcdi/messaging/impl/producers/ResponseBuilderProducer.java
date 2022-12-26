package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

@RequestScoped
public class ResponseBuilderProducer {

    private Outgoing.Response.Builder<?, ?> messageBuilder = null;

    public void createResponseBuilderFor(Incoming<?> incoming) {
        if (incoming instanceof Incoming.Request<?> request) {
            this.messageBuilder = new Outgoing.Response.Builder<>(request);
        }
    }

    @Produces
    @Dependent
    public <REQ, RES> Outgoing.Response.Builder<REQ, RES> getMessageBuilder() {
        return (Outgoing.Response.Builder<REQ, RES>) messageBuilder;
    }
}
