package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

@RequestScoped
public class ResponseBuilderProducer {

    private OutgoingMessageBuilder<?, ?> messageBuilder = new OutgoingMessageBuilder<>();

    public void createResponseBuilderFor(Incoming<?> incoming) {
        if (incoming instanceof Incoming.Request<?> request) {
            final OutgoingMessageBuilder<?, ?> previousBuilder = this.messageBuilder;
            this.messageBuilder = new OutgoingMessageBuilder<>(null, request).setProperties(
                    previousBuilder.properties());
        }
    }

    @Produces
    @Dependent
    public <REQ, RES> OutgoingMessageBuilder<REQ, RES> getMessageBuilder() {
        return (OutgoingMessageBuilder<REQ, RES>) messageBuilder;
    }
}
