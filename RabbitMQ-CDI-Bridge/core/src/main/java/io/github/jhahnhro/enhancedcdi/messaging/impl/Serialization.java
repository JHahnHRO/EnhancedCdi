package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Prioritized;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageTooLargeException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.types.WildcardTypeImpl;
import io.github.jhahnhro.enhancedcdi.util.BeanHelper;
import io.github.jhahnhro.enhancedcdi.util.BeanInstance;

@ApplicationScoped
class Serialization {

    private static final Comparator<Prioritized> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(
            Prioritized::getPriority).reversed();
    @Inject
    BeanHelper beanHelper;
    private int maxMessageSize;

    /**
     * Increased Visibility because there is a test case for this method.
     *
     * @return the Type {@code MessageReader<? extends T>} where {@code T} is the given type.
     */
    static Type getMessageReaderType(final Type runtimeType) {
        return new ParameterizedTypeImpl(MessageReader.class, null,
                                         new WildcardTypeImpl(new Type[]{runtimeType}, new Type[0]));
    }

    /**
     * Increased Visibility because there is a test case for this method.
     *
     * @return the Type {@code MessageWriter<? super T>} where {@code T} is the given type.
     */
    static Type getMessageWriterType(final Type runtimeType) {
        return new ParameterizedTypeImpl(MessageWriter.class, null,
                                         new WildcardTypeImpl(new Type[]{Object.class}, new Type[]{runtimeType}));
    }

    @Inject
    void setMaxMessageSize(Configuration configuration) {
        this.maxMessageSize = configuration.maxMessageSize();
    }

    public <T> Incoming<T> deserialize(Incoming<byte[]> incomingMessage) throws IOException {

        try (var inputStream = new ByteArrayInputStream(incomingMessage.content());
             var applicableReaders = getApplicableReaders(incomingMessage, Object.class)) {
            var selectedReader = selectHighestPriority(applicableReaders,
                                                       "No MessageReader is applicable to the message.");
            final Object content = selectedReader.read(incomingMessage.withContent(inputStream));
            return (Incoming<T>) incomingMessage.withContent(content);
        }
    }

    private <X extends Prioritized> X selectHighestPriority(Stream<X> instances, final String msg) {
        return instances.min(HIGHEST_PRIORITY_FIRST).orElseThrow(() -> new IllegalStateException(msg));
    }

    private <T> Stream<MessageReader<T>> getApplicableReaders(Incoming<byte[]> incoming, final Type typeHint) {
        Type messageReaderType = getMessageReaderType(typeHint);
        return beanHelper.<MessageReader<T>>safeStream(messageReaderType, Any.Literal.INSTANCE)
                .map(BeanInstance::instance)
                .filter(reader -> reader.canRead(incoming));
    }

    public <T> Outgoing<byte[]> serialize(Outgoing<T> outgoingMessage, final Type runtimeType) throws IOException {
        final Outgoing.Builder<?> builder = outgoingMessage.builder();

        try (var outputStream = new BoundedByteArrayOutputStream(maxMessageSize);
             var applicableWriters = getApplicableWriters(outgoingMessage, runtimeType)) {
            var selectedWriter = selectHighestPriority(applicableWriters, "No MessageWriter of type " + runtimeType
                                                                          + " is applicable to the message.");
            selectedWriter.write(outgoingMessage, builder.setContent(outputStream));
            outputStream.flush();
            return builder.setContent(outputStream.toByteArray()).build();
        }
    }

    private <T> Stream<MessageWriter<T>> getApplicableWriters(Outgoing<T> outgoingMessage, final Type typeHint) {
        Type messageWriterType = getMessageWriterType(typeHint);
        return beanHelper.<MessageWriter<T>>safeStream(messageWriterType, Any.Literal.INSTANCE)
                .map(BeanInstance::instance)
                .filter(writer -> writer.canWrite(outgoingMessage));
    }

    private static class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
        final int maxSize;

        private BoundedByteArrayOutputStream(int maxSize) {this.maxSize = maxSize;}

        @Override
        public synchronized void write(int b) {
            checkSize(1);
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            checkSize(len);
            super.write(b, off, len);
        }

        @Override
        public void writeBytes(byte[] b) {
            checkSize(b.length);
            super.writeBytes(b);
        }

        private void checkSize(int increment) {
            if (size() + increment > maxSize) {
                throw new MessageTooLargeException();
            }
        }
    }
}
