package io.github.jhahnhro.enhancedcdi.serialization.json;

import java.io.InputStream;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.InvalidMessageException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SelectableMessageReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;

/**
 * Abstract implementation of a {@link SelectableMessageReader} that converts RabbitMQ string messages to Java objects using
 * JSON-B. Use the {@link JsonMessageReader#JsonMessageReader(Jsonb, Class) protected constructor} to specify the type
 * you want to deserialize to (for simplicity only classes, not parametrized types) and to supply a {@link Jsonb}
 * instance - which you can probably get by letting whatever container you are running in inject it for you.
 *
 * @param <T> the java type to deserialize
 */
public abstract class JsonMessageReader<T> implements SelectableMessageReader<T> {
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
        return "application/json".equals(message.properties().getContentType()) && clazz.getCanonicalName()
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
}
