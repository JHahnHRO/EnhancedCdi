package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;
import io.github.jhahn.enhancedcdi.messaging.serialization.MessageReader;
import io.github.jhahn.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.types.WildcardTypeImpl;
import io.github.jhahnhro.enhancedcdi.util.BeanHelper;
import io.github.jhahnhro.enhancedcdi.util.BeanInstance;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Prioritized;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;

@ApplicationScoped
class Serialization {

    private static final Comparator<Prioritized> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(
            Prioritized::getPriority).reversed();
    @Inject
    BeanHelper beanHelper;

    /**
     * @return the Type {@code MessageReader<? extends T>} where {@code T} is the given type.
     */
    private static Type getMessageReaderType(final Type runtimeType) {
        return new ParameterizedTypeImpl(MessageReader.class, null,
                                         new WildcardTypeImpl(new Type[]{runtimeType}, new Type[0]));
    }

    /**
     * @return the Type {@code MessageWriter<? super T>} where {@code T} is the given type.
     */
    private static Type getMessageWriterType(final Type runtimeType) {
        return new ParameterizedTypeImpl(MessageWriter.class, null,
                                         new WildcardTypeImpl(new Type[0], new Type[]{runtimeType}));
    }

    private static <U> OutgoingMessageBuilder<?, Object> builderFrom(Outgoing<U> original) {
        final OutgoingMessageBuilder<?, Object> builder;
        if (original instanceof Outgoing.Response<?, U> response) {
            builder = new OutgoingMessageBuilder<>(null, response.request());
        } else {
            builder = new OutgoingMessageBuilder<>(null).setExchange(original.exchange())
                    .setRoutingKey(original.routingKey());
        }
        builder.setProperties(original.properties());

        return builder;
    }

    public <T> Incoming<T> deserialize(Incoming<byte[]> incomingMessage) throws IOException {
        MessageReader<?> reader = selectMessageReader(incomingMessage, Object.class);

        final Object content;
        try (var inputStream = new ByteArrayInputStream(incomingMessage.content())) {
            content = reader.read(incomingMessage.withContent(inputStream));
        }
        return (Incoming<T>) incomingMessage.withContent(content);
    }

    private <T> MessageReader<T> selectMessageReader(Incoming<byte[]> incoming, final Type typeHint) {
        Type messageReaderType = getMessageReaderType(typeHint);
        Collection<BeanInstance<MessageReader<T>>> instances = beanHelper.select(messageReaderType,
                                                                                 Any.Literal.INSTANCE);

        return instances.stream()
                .map(BeanInstance::instance)
                .filter(reader -> reader.canRead(incoming))
                .min(HIGHEST_PRIORITY_FIRST)
                .orElseThrow(() -> new IllegalStateException(
                        "No MessageReader of type " + messageReaderType + " is applicable to the message."));
    }

    public <T> Outgoing<byte[]> serialize(Outgoing<T> outgoingMessage, final Type runtimeType) throws IOException {
        final MessageWriter<T> writer = selectMessageWriter(outgoingMessage, runtimeType);

        final OutgoingMessageBuilder<?, Object> builder = builderFrom(outgoingMessage);

        final byte[] content;
        try (var outputStream = new ByteArrayOutputStream()) {
            writer.write(outgoingMessage, builder.setContent(outputStream));
            outputStream.flush();
            content = outputStream.toByteArray();
        }
        return builder.setContent(content).build();
    }

    private <T> MessageWriter<T> selectMessageWriter(Outgoing<T> outgoingMessage, final Type typeHint) {
        Type messageWriterType = getMessageWriterType(typeHint);
        Collection<BeanInstance<MessageWriter<T>>> instances = beanHelper.select(messageWriterType,
                                                                                 Any.Literal.INSTANCE);

        return instances.stream()
                .map(BeanInstance::instance)
                .filter(writer -> writer.canWrite(outgoingMessage))
                .min(HIGHEST_PRIORITY_FIRST)
                .orElseThrow(() -> new IllegalStateException(
                        "No MessageWriter of type " + messageWriterType + " is applicable to the message."));
    }
}
