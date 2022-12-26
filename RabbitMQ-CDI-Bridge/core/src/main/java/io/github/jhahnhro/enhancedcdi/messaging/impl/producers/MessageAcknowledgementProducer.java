package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

import io.github.jhahnhro.enhancedcdi.messaging.MessageAcknowledgment;

@RequestScoped
public class MessageAcknowledgementProducer {

    private MessageAcknowledgment acknowledgement;

    @Produces
    @RequestScoped
    public MessageAcknowledgment getAcknowledgement() {
        return acknowledgement;
    }

    public void setAcknowledgement(MessageAcknowledgment acknowledgement) {
        this.acknowledgement = acknowledgement;
    }
}
