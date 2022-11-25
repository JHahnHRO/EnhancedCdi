package io.github.jhahn.enhancedcdi.messaging.serialization;

import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Singleton
public class ByteArrayReaderWriter implements MessageReader<byte[]>, MessageWriter<byte[]> {

    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    //region Decoder
    @Override
    public boolean canRead(Incoming<byte[]> message) {
        return APPLICATION_OCTET_STREAM.equals(message.properties().getContentType());
    }

    @Override
    public byte[] read(Incoming<InputStream> message) throws IOException {
        try (InputStream inputStream = message.content()) {
            return inputStream.readAllBytes();
        }
    }
    //endregion

    //region Encoder
    @Override
    public boolean canWrite(Outgoing<byte[]> message) {
        return message.content() != null;
    }

    @Override
    public void write(Outgoing<byte[]> originalMessage, OutgoingMessageBuilder<?, OutputStream> builder)
            throws IOException {
        builder.propertiesBuilder().contentType(APPLICATION_OCTET_STREAM);
        try (OutputStream outputStream = builder.content()) {
            outputStream.write(originalMessage.content());
        }
    }
    //endregion
}
