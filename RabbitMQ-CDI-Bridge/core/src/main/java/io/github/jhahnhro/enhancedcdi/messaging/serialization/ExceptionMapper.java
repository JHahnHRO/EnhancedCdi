package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.util.function.Function;
import javax.enterprise.inject.spi.Prioritized;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

public interface ExceptionMapper<E extends Throwable, RES>
        extends Function<E, Outgoing.Response<byte[], RES>>, Prioritized {

    @Override
    Outgoing.Response<byte[], RES> apply(E exception);
}
