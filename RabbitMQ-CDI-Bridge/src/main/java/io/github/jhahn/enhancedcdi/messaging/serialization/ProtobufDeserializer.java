package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahn.enhancedcdi.messaging.InvalidMessageException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class ProtobufDeserializer<T extends Message> implements Deserializer<T> {
    private final Class<T> clazz;
    private final Parser<T> parser;

    public ProtobufDeserializer(Class<T> protobufType) {
        this.clazz = protobufType;
        try {
            this.parser = (Parser<T>) protobufType.getMethod("parser").invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean isApplicable(Envelope envelope, BasicProperties messageProperties) {
        return "application/x-protobuf".equals(messageProperties.getContentType()) && clazz.getSimpleName()
                .equals(messageProperties.getType());
    }

    @Override
    public T deserialize(Envelope envelope, BasicProperties messageProperties, InputStream messageBody)
            throws InvalidMessageException, IOException {
        try {
            return clazz.cast(parser.parseFrom(messageBody));
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidMessageException(e);
        }
    }
}
