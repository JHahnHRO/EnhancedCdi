package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import io.github.jhahn.enhancedcdi.messaging.MessageAcknowledgment;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

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
