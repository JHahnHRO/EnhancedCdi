package io.github.jhahnhro.enhancedcdi.serialization.protobuf;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.InvalidMessageException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;

public abstract class ProtobufMessageReader<T extends Message> implements MessageReader<T> {
    private final Class<T> clazz;
    private final Parser<T> parser;

    protected ProtobufMessageReader(Class<T> protobufType) {
        this.clazz = protobufType;
        try {
            final Method parserMethod = protobufType.getMethod("parser");
            if (!Modifier.isStatic(parserMethod.getModifiers()) || !Parser.class.isAssignableFrom(
                    parserMethod.getReturnType())) {
                throw new IllegalArgumentException(protobufType + " is not a protobuf type.");
            }
            this.parser = Parser.class.cast(parserMethod.invoke(null));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException(protobufType + " is not a protobuf type.", e);
        }
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canRead(Incoming<byte[]> message) {
        return "application/x-protobuf".equals(message.properties().getContentType()) && clazz.getSimpleName()
                .equals(message.properties().getType());
    }

    @Override
    public T read(Incoming<InputStream> message) throws InvalidMessageException {
        try {
            return clazz.cast(parser.parseFrom(message.content()));
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidMessageException(e);
        }
    }
}
