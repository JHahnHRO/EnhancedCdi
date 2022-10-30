package io.github.jhahnhro.enhancedcdi.serialization.json;

import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;
import io.github.jhahn.enhancedcdi.messaging.serialization.MessageWriter;

import javax.annotation.PreDestroy;
import javax.json.bind.Jsonb;
import java.io.IOException;
import java.io.OutputStream;

public abstract class JsonMessageWriter<T> implements MessageWriter<T> {

    private final Jsonb jsonb;

    protected JsonMessageWriter(Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canWrite(Outgoing<T> message) {
        return true;
    }

    @Override
    public void write(Outgoing<T> originalMessage, OutgoingMessageBuilder<?, OutputStream> outgoingMessageBuilder)
            throws IOException {
        outgoingMessageBuilder.propertiesBuilder()
                .contentType("application/json")
                .type(originalMessage.content().getClass().getSimpleName());
        jsonb.toJson(originalMessage.content(), outgoingMessageBuilder.content());
    }

    @PreDestroy
    private void close() throws Exception {
        jsonb.close();
    }
}

