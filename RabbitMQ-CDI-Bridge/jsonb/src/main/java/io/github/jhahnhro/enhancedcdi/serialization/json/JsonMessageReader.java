package io.github.jhahnhro.enhancedcdi.serialization.json;

import java.io.InputStream;
import javax.annotation.PreDestroy;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbException;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.InvalidMessageException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;

public abstract class JsonMessageReader<T> implements MessageReader<T> {
    private final Jsonb jsonb;
    private final Class<T> clazz;

    protected JsonMessageReader(Jsonb jsonb, Class<T> jsonType) {
        this.clazz = jsonType;
        this.jsonb = jsonb;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canRead(Incoming<byte[]> message) {
        return "application/json".equals(message.properties().getContentType()) && clazz.getSimpleName()
                .equals(message.properties().getType());
    }

    @Override
    public T read(Incoming<InputStream> message) throws InvalidMessageException {
        try {
            return jsonb.fromJson(message.content(), clazz);
        } catch (JsonbException e) {
            throw new InvalidMessageException(e);
        }
    }

    @PreDestroy
    private void close() throws Exception {
        jsonb.close();
    }
}
