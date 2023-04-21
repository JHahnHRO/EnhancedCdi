package io.github.jhahnhro.enhancedcdi.serialization.protobuf;

import java.io.IOException;
import java.io.OutputStream;
import javax.enterprise.context.Dependent;

import com.google.protobuf.Message;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SelectableMessageWriter;

@Dependent
public class ProtobufMessageWriter<M extends Message> implements SelectableMessageWriter<M> {

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canWrite(Outgoing<M> message) {
        return message.content() != null;
    }

    @Override
    public void write(Outgoing<M> originalMessage, MessageBuilder<OutputStream,?> outgoingMessageBuilder)
            throws IOException {
        outgoingMessageBuilder.propertiesBuilder()
                .contentType("application/x-protobuf")
                .type(originalMessage.content().getDescriptorForType().getFullName());
        originalMessage.content().writeTo(outgoingMessageBuilder.content());
    }
}

