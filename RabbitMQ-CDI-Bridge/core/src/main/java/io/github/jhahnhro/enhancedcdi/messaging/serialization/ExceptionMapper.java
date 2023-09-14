package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import javax.enterprise.inject.spi.Prioritized;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

public interface ExceptionMapper<E extends Throwable, RES> extends Prioritized {

    <T> Outgoing.Response<T, RES> apply(Incoming.Request<T> request, E exception);
}
