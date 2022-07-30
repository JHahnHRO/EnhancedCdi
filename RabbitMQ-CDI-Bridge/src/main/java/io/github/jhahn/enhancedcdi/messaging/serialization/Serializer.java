package io.github.jhahn.enhancedcdi.messaging.serialization;

import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

import javax.enterprise.inject.spi.Prioritized;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Serializes Java objects into byte streams. A Serializer is applicable to a given object if the object is an instance
 * of {@link #serializableType()} and {@link #canSerialize(Object)} is true.
 * <p>
 * It should always be tested (with {@link #canSerialize(Object)}) whether a specific Serializer is applicable to a
 * given message before attempting serialization.
 * <p>
 * Serializers should be CDI beans or created by a CDI bean of type {@link SerializerProvider} in order to be
 * automatically discovered. It is also possible to register custom Serializer instances with
 * {@link SerializationSelector#register(Serializer)}.
 * <p>
 * By default, what Serializer to use for any given object is determined by selecting the Serializer with the highest
 * {@link #getPriority() priority} among those that are applicable. If multiple Deserializer instances with the same
 * priority are applicable, it is undefined which one will be selected.
 * <p>
 * But it is possible to disable automatic selection and force the usage of a specific Serializer instance by
 * {@link javax.enterprise.event.Observes observing} a
 * {@link io.github.jhahn.enhancedcdi.messaging.processing.ProcessOutgoing} event and calling
 * {@link io.github.jhahn.enhancedcdi.messaging.processing.ProcessOutgoing#setSerializer(Serializer)} on it.
 *
 * @param <T> type of the objects to serialize
 * @implSpec Serializer instances should be thread-safe and re-entrant. Ideally they are completely stateless.
 * @see SerializationSelector
 * @see ByteArrayCodec
 * @see PlainTextCodec
 */
public interface Serializer<T> extends Prioritized {

    /**
     * @return the class of objects that can be serialized with this Serializer instance
     */
    Class<T> serializableType();

    /**
     * @param payload an object
     * @return {@code true} iff the given object can be serialized
     */
    default boolean canSerialize(T payload) {
        return serializableType().isInstance(payload);
    }

    void serialize(T payload, PropertiesBuilder responseProperties, OutputStream outputStream) throws IOException;
}
