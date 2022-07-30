package io.github.jhahn.enhancedcdi.messaging.serialization;

import java.util.Collection;

public interface DeserializerProvider {

    Collection<Deserializer<?>> get();
}
