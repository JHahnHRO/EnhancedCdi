package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.IOException;
import java.io.InputStream;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

public interface MessageReader<T> {
    /**
     * Deserializes an incoming message by reading from the given {@link InputStream}.
     *
     * @param message the incoming message and its metadata.
     * @return the content of the message
     * @throws IllegalArgumentException if this {@code SelectableMessageReader} is not applicable to a message with the
     *                                  provided metadata
     * @throws InvalidMessageException  if the message could not be serialized
     * @throws IOException              if the message could not be read from the InputStream
     */
    T read(Incoming<InputStream> message) throws InvalidMessageException, IOException;
}
