package io.github.jhahnhro.enhancedcdi.serialization.protobuf;

import java.io.IOException;
import java.io.OutputStream;
import javax.enterprise.context.Dependent;

import com.google.protobuf.MessageLite;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;

@Dependent
public class ProtobufMessageWriter<M extends MessageLite> implements MessageWriter<M> {

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canWrite(Outgoing<M> message) {
        return message.content() != null;
    }

    @Override
    public void write(Outgoing<M> originalMessage, Outgoing.Builder<OutputStream> outgoingMessageBuilder)
            throws IOException {
        outgoingMessageBuilder.propertiesBuilder()
                .contentType("application/x-protobuf")
                .type(originalMessage.content().getClass().getSimpleName());
        originalMessage.content().writeTo(outgoingMessageBuilder.content());
    }
}

