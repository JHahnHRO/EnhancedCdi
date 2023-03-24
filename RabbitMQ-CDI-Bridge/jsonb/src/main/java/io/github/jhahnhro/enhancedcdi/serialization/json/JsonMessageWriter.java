package io.github.jhahnhro.enhancedcdi.serialization.json;

import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.PreDestroy;
import javax.json.bind.Jsonb;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SelectableMessageWriter;

/**
 * A general purpose {@link SelectableMessageWriter} that serializes arbitrary Java objects to RabbitMQ string messages using
 * JSON-B. Use the {@link JsonMessageWriter#JsonMessageWriter(Jsonb) protected constructor} to supply a {@link Jsonb}
 * instance - which you can probably get by letting whatever container you are running in inject it for you.
 *
 * @param <T> the java type to serialize
 */
public abstract class JsonMessageWriter<T> implements SelectableMessageWriter<T> {

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
    public void write(Outgoing<T> originalMessage, Outgoing.Builder<OutputStream> outgoingMessageBuilder)
            throws IOException {
        outgoingMessageBuilder.propertiesBuilder()
                .contentType("application/json")
                .type(originalMessage.content().getClass().getCanonicalName());
        jsonb.toJson(originalMessage.content(), outgoingMessageBuilder.content());
    }

    @PreDestroy
    private void close() throws Exception {
        jsonb.close();
    }
}

