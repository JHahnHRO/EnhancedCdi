package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import io.github.jhahn.enhancedcdi.messaging.MessageAcknowledgment;

import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import java.io.Closeable;
import java.io.IOException;

@RequestScoped
public class MessageAcknowledgementProducer {

    private MessageAcknowledgment acknowledgement;

    @Produces
    @RequestScoped
    public MessageAcknowledgment getAcknowledgement() {
        return acknowledgement;
    }

    void closeAcknowledgment(@Disposes MessageAcknowledgment acknowledgement) throws IOException {
        closeIfNecessary(acknowledgement);
    }

    void endOfScope(@Observes @BeforeDestroyed(RequestScoped.class) Object endOfRequestScope) throws IOException {
        if (this.acknowledgement != null) {
            closeIfNecessary(this.acknowledgement);
        }
    }

    private void closeIfNecessary(MessageAcknowledgment acknowledgement) throws IOException {
        if (acknowledgement instanceof Closeable closeable) {
            // manual acknowledgement has a close method that logs an error message if ack/reject was not called
            // and prevents indefinite wait for it to happen
            closeable.close();
        }
    }

    public void setAcknowledgement(MessageAcknowledgment acknowledgement) {
        this.acknowledgement = acknowledgement;
    }
}
