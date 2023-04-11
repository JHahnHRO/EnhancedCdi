package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.IOException;
import java.io.OutputStream;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

/**
 * Serializes Java objects into an {@link OutputStream}.
 */
public interface MessageWriter<T> {
    /**
     * Serializes the given message by writing into the given {@link Outgoing.Builder}, in particular by writing to its
     * {@link Outgoing.Builder#content() content} {@link OutputStream}. The builder will be initialized with the same
     * metadata as the {@code originalMessage} and an {@code OutputStream} that has not written any bytes yet. The
     * builder's {@link Outgoing.Builder#propertiesBuilder() message properties} can also be manipulated, e.g. by
     * setting additional headers, setting the message's {@link BasicProperties#getContentType() content type} and/or
     * {@link BasicProperties#getType() message type}.
     *
     * @param originalMessage   the message that should be serialized into bytes.
     * @param serializedMessage a builder that should contain the serialized form of the message content and may contain
     *                          altered metadata.
     * @throws IOException if the message could not be written to the {@code OutputStream}
     */
    void write(Outgoing<T> originalMessage, Outgoing.Builder<OutputStream> serializedMessage) throws IOException;
}
