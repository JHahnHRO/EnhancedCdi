package io.github.jhahnhro.enhancedcdi.messaging.messages;

public interface IncomingDelivery<T> {

    Incoming<T> message();

    void acknowledge();

    void reject();
}
