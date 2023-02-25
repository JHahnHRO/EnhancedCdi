package io.github.jhahnhro.enhancedcdi.serialization.protobuf;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.InvalidMessageException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;

public abstract class ProtobufMessageReader<T extends Message> implements MessageReader<T> {
    private final Class<T> clazz;
    private final Parser<T> parser;
    private final Descriptors.Descriptor descriptor;

    protected ProtobufMessageReader(Class<T> protobufType) {
        this.clazz = protobufType;

        try {
            final MethodHandle getDescriptor = getMethodHandle("getDescriptor", Descriptors.Descriptor.class);
            this.descriptor = (Descriptors.Descriptor) getDescriptor.invoke();
            final MethodHandle getParser = getMethodHandle("parser", Parser.class);
            this.parser = (Parser<T>) getParser.invoke();
        } catch (Throwable e) {
            throw new IllegalArgumentException("Cannot obtain Descriptor and/or Parser for " + protobufType, e);
        }
    }

    private MethodHandle getMethodHandle(final String name, final Class<?> returnType) {
        MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        try {
            return publicLookup.findStatic(clazz, name, MethodType.methodType(returnType));
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(clazz + " is not accessible.", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(clazz + " is not a protobuf type.", e);
        }
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canRead(Incoming<byte[]> message) {
        return "application/x-protobuf".equals(message.properties().getContentType()) && descriptor.getFullName()
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
