package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;
import io.github.jhahnhro.enhancedcdi.reflection.TypeVariableResolver;

import javax.enterprise.inject.spi.Prioritized;
import javax.enterprise.util.TypeLiteral;
import java.lang.reflect.Type;

public interface Serializer<T> extends Prioritized {
    default boolean canSerialize(Type payloadType, BasicProperties responseProperties) {
        return payloadType instanceof Class<?> payloadClass && getEncodableType() instanceof Class<?> classT
               && classT.isAssignableFrom(payloadClass);
    }

    default Type getEncodableType() {
        final Type variableT = new TypeLiteral<T>() {}.getType();
        return TypeVariableResolver.withKnownTypesOf(this.getClass()).resolve(variableT);
    }

    byte[] serialize(T payload, PropertiesBuilder responseProperties);
}
