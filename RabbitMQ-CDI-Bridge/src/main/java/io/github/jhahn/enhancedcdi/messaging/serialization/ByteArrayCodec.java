package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

import javax.enterprise.context.Dependent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Dependent
public class ByteArrayCodec implements Deserializer<byte[]>, Serializer<byte[]> {

    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    //region Decoder
    @Override
    public boolean isApplicable(Envelope envelope, BasicProperties messageProperties) {
        return APPLICATION_OCTET_STREAM.equals(messageProperties.getContentType());
    }

    @Override
    public byte[] deserialize(Envelope envelope, BasicProperties messageProperties, InputStream messageBody) throws IOException {
        return messageBody.readAllBytes();
    }
    //endregion

    //region Encoder
    @Override
    public Class<byte[]> serializableType() {
        return byte[].class;
    }

    @Override
    public void serialize(byte[] payload, PropertiesBuilder responseProperties, OutputStream outputStream)
            throws IOException {
        responseProperties.setContentType(APPLICATION_OCTET_STREAM);
        outputStream.write(payload);
    }
    //endregion
}
