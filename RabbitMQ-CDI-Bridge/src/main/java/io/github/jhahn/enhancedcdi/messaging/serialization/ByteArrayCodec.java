package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

import javax.enterprise.context.Dependent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

@Dependent
public class ByteArrayCodec implements BuiltInCodec<byte[], byte[]> {

    //region Decoder
    @Override
    public boolean canDeserialize(BasicProperties messageProperties) {
        return true;
    }

    @Override
    public Deserialized<byte[]> deserialize(InputStream messageBody, BasicProperties properties) throws IOException {
        return new Deserialized<>(messageBody.readAllBytes());
    }
    //endregion

    //region Encoder
    @Override
    public boolean canSerialize(Type payloadType, BasicProperties responseProperties) {
        return true;
    }

    @Override
    public byte[] serialize(byte[] payload, PropertiesBuilder responseProperties) {
        return payload;
    }
    //endregion
}
