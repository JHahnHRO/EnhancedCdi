package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.google.protobuf.Message;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

import java.io.IOException;
import java.io.OutputStream;

public class ProtobufSerializer<T extends Message> implements Serializer<T> {

    private final Class<T> clazz;

    public ProtobufSerializer(Class<T> protobufType) {
        clazz = protobufType;
    }

    @Override
    public Class<T> serializableType() {
        return clazz;
    }

    @Override
    public void serialize(T payload, PropertiesBuilder responseProperties, OutputStream outputStream)
            throws IOException {
        responseProperties.setContentType("application/x-protobuf").setType(clazz.getSimpleName());
        payload.writeTo(outputStream);
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
