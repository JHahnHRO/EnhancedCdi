package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.Prioritized;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageTooLargeException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.util.EnhancedInstance;

@ApplicationScoped
class Serialization {

    static final Comparator<Prioritized> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(Prioritized::getPriority)
            .reversed();

    //region Deserialization
    @Inject
    @Selected
    SelectedMessageReader selectedMessageReader;

    public Incoming<Object> deserialize(Incoming<byte[]> incomingMessage) throws IOException {
        selectedMessageReader.selectReader(incomingMessage);

        try (var inputStream = new ByteArrayInputStream(incomingMessage.content())) {
            final Object content = selectedMessageReader.read(incomingMessage.withContent(inputStream));
            return incomingMessage.withContent(content);
        }
    }
    //endregion


    //region Serialization
    @Inject
    EnhancedInstance<Object> enhancedInstance;
    private int maxMessageSize;

    @Inject
    void setMaxMessageSize(Configuration configuration) {
        this.maxMessageSize = configuration.maxMessageSize();
    }

    public <T> Outgoing<byte[]> serialize(Outgoing<T> outgoingMessage) throws IOException {
        final Outgoing.Builder<?> builder = outgoingMessage.builder();

        try (var outputStream = new BoundedByteArrayOutputStream(maxMessageSize)) {
            writeWithSelectedWriter(outgoingMessage, builder.setType(OutputStream.class).setContent(outputStream));
            outputStream.flush();
            return builder.setType(byte[].class).setContent(outputStream.toByteArray()).build();
        }
    }

    private <T> void writeWithSelectedWriter(Outgoing<T> outgoingMessage,
                                             Outgoing.Builder<OutputStream> serializedMessage)
            throws IOException {

        MessageWriter<T> selectedWriter = getSelectedWriter(outgoingMessage);
        try {
            selectedWriter.write(outgoingMessage, serializedMessage);
        } finally {
            enhancedInstance.destroy(selectedWriter);
        }
    }

    private <T> MessageWriter<T> getSelectedWriter(Outgoing<T> outgoingMessage) {
        var messageWriterType = new ParameterizedTypeImpl(MessageWriter.class, null, outgoingMessage.type());
        return this.enhancedInstance.<MessageWriter<T>>select(messageWriterType, Selected.Literal.INSTANCE).get();
    }

    //endregion

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
