package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserialized;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserializer;
import io.github.jhahn.enhancedcdi.messaging.serialization.Serializer;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Prioritized;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class Serialization {

    private static final Comparator<Prioritized> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(
            Prioritized::getPriority).reversed();

    @Inject
    @Any
    Instance<Deserializer<?>> deserializersInstance;
    @Inject
    @Any
    Instance<Serializer<?>> serializersInstance;

    List<Deserializer<?>> deserializers;
    List<Serializer<?>> serializers;

    @Inject
    void setSerializersAndDeserializers() {
        this.deserializers = deserializersInstance.stream().sorted(HIGHEST_PRIORITY_FIRST).toList();
        this.serializers = serializersInstance.stream().sorted(HIGHEST_PRIORITY_FIRST).toList();
    }

    @PreDestroy
    void destroySerializersAndDeserializers() {
        this.deserializers.forEach(deserializersInstance::destroy);
        this.serializers.forEach(serializersInstance::destroy);
    }

    public Optional<Serializer<?>> selectSerializer(Type payloadType, BasicProperties responseProperties) {
        return serializers.stream()
                .filter(serializer -> serializer.canSerialize(payloadType, responseProperties))
                .findFirst();
    }

    public Optional<Deserializer<?>> selectDeserializer(BasicProperties properties) {
        return deserializers.stream().filter(d -> d.canDeserialize(properties)).findFirst();
    }

}
