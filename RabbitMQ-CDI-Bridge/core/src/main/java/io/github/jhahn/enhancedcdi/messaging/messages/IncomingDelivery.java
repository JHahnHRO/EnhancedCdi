package io.github.jhahn.enhancedcdi.messaging.messages;

public interface IncomingDelivery<T> {

    Incoming<T> message();

    void acknowledge();

    void reject();
}
