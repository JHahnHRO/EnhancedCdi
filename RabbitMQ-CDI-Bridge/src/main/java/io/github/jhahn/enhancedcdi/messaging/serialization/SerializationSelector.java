package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.Delivery;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Prioritized;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApplicationScoped
public class SerializationSelector {

    private static final Comparator<Prioritized> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(
            Prioritized::getPriority).reversed();

    private Set<Deserializer<?>> deserializers;

    private Set<Serializer<?>> serializers;

    private ReadWriteLock deserializerLock;

    private ReadWriteLock serializerLock;

    @Inject
    void setDeserializers(@Any Instance<Deserializer<?>> deserializers, @Any Instance<DeserializerProvider> providers) {

        this.deserializers = new TreeSet<>(HIGHEST_PRIORITY_FIRST);
        deserializers.stream().forEach(this.deserializers::add);
        providers.stream().map(DeserializerProvider::get).forEach(this.deserializers::addAll);

        this.deserializerLock = new ReentrantReadWriteLock();
    }

    @Inject
    void setSerializers(@Any Instance<Serializer<?>> serializers, @Any Instance<SerializerProvider> providers) {

        this.serializers = new TreeSet<>(HIGHEST_PRIORITY_FIRST);
        serializers.stream().forEach(this.serializers::add);
        providers.stream().map(SerializerProvider::get).forEach(this.serializers::addAll);

        this.serializerLock = new ReentrantReadWriteLock();
    }

    @PreDestroy
    void destroySerializersAndDeserializers() throws Exception {
        List<Exception> exceptions = new ArrayList<>();
        this.deserializers.forEach(deserializer -> tryClose(exceptions, deserializer));
        this.serializers.forEach(serializer -> tryClose(exceptions, serializer));
        if (!exceptions.isEmpty()) {
            if (exceptions.size() == 1) {
                throw exceptions.get(0);
            } else {
                final RuntimeException exception = new RuntimeException();
                exceptions.forEach(exception::addSuppressed);
                throw exception;
            }
        }
    }

    private void tryClose(List<Exception> exceptions, Object o) {
        if (o instanceof AutoCloseable) {
            try {
                ((AutoCloseable) o).close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
    }

    public <T> Optional<Serializer<T>> selectSerializer(T payloadType) {
        serializerLock.readLock().lock();
        try {
            return serializers.stream()
                    .filter(serializer -> serializer.serializableType().isAssignableFrom(payloadType.getClass()))
                    .map(serializer -> (Serializer<T>) serializer)
                    .filter(serializer -> serializer.canSerialize(payloadType))
                    .findFirst();
        } finally {
            serializerLock.readLock().unlock();
        }
    }

    public Optional<Deserializer<?>> selectDeserializer(Delivery delivery) {
        deserializerLock.readLock().lock();
        try {
            return deserializers.stream()
                    .filter(d -> d.isApplicable(delivery.getEnvelope(), delivery.getProperties()))
                    .findFirst();
        } finally {
            deserializerLock.readLock().unlock();
        }
    }

    public void register(Deserializer<?> deserializer) {
        Objects.requireNonNull(deserializer);
        deserializerLock.writeLock().lock();
        try {
            this.deserializers.add(deserializer);
        } finally {
            deserializerLock.writeLock().unlock();
        }
    }

    public void register(Serializer<?> serializer) {
        Objects.requireNonNull(serializer);
        serializerLock.writeLock().lock();
        try {
            this.serializers.add(serializer);
        } finally {
            serializerLock.writeLock().unlock();
        }
    }
}
