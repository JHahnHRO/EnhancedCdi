package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.IOException;
import java.io.OutputStream;
import javax.enterprise.inject.spi.Prioritized;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

/**
 * Serializes Java objects into an {@link OutputStream}.
 * <p>
 * It should always be tested (with {@link #canWrite(Outgoing)}) whether a specific {@code MessageWriter} is applicable
 * to a given message before attempting serialization.
 * <p>
 * By default, what {@code MessageWriter} to use for any given object is determined by selecting the
 * {@code MessageWriter} with the highest {@link #getPriority() priority} among those that are applicable. If multiple
 * {@code MessageWriter} instances with the same priority are applicable, it is undefined which one will be selected.
 *
 * @param <T> type of the message contents that this {@code MessageWriter} can serialize.
 * @implSpec {@code MessageWriter} instances should be thread-safe and re-entrant. Ideally they are completely
 * stateless.
 * @see ByteArrayReaderWriter
 * @see PlainTextReaderWriter
 */
public interface MessageWriter<T> extends Prioritized {

    /**
     * A (reasonably quick) test whether this {@code MessageWriter} can serialize the given message, e.g. by examining
     * the {@link BasicProperties#getHeaders() headers} or do an {@code instanceof} check on the content. It is not
     * recommended to make an attempt at serialization, because this method will be called on all
     * {@code MessageWriter}-instances to determine which {@code MessageWriter} should perform the serialization in the
     * end.
     *
     * @param message a message
     * @return {@code true} this {@code MessageWriter} can serialize the given message.
     */
    boolean canWrite(Outgoing<T> message);

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
