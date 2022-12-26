package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.IOException;
import java.io.InputStream;
import javax.enterprise.inject.spi.Prioritized;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

/**
 * Deserializes Java objects from an {@link InputStream}. A {@code MessageReader} decides whether it is applicable to a
 * given delivery by examining the metadata, i.e. the delivery's {@link Envelope} (containing the name of the exchange
 * and the routing key) and {@link BasicProperties}.
 * <p>
 * It should always be tested (with {@link #canRead(Incoming)}) whether a specific
 * {@code MessageReader} is applicable to a given message before attempting serialization.
 * <p>
 * The {@code MessageReader} to use for any given delivery is determined by selecting the {@code MessageReader} with the
 * highest {@link #getPriority() priority} among those that are {@link #canRead(Incoming)}. If multiple
 * {@code MessageReader} instances with the same priority are applicable to a delivery, it is undefined which one will
 * be selected.
 *
 * @param <T> type of the deserialized objects
 * @implSpec {@code MessageReader} instances should be thread-safe and re-entrant. Ideally they are completely
 * stateless.
 * @see ByteArrayReaderWriter
 * @see PlainTextReaderWriter
 */
public interface MessageReader<T> extends Prioritized {

    /**
     * Automatic {@code MessageReader}-selection uses {@link #canRead(Incoming)} to determine the {@code MessageReader}s
     * that are applicable to an incoming message and then selects the one with the highest priority.
     *
     * @return the priority of this MessageReader
     */
    @Override
    int getPriority();

    /**
     * A (reasonably quick) test whether this {@code MessageReader} is applicable to the message, e.g. by evaluating the
     * message's {@link BasicProperties#getContentType() content type}, its {@link BasicProperties#getHeaders() headers}
     * or something else. It is not recommended to make an attempt at deserialization of the content, because this
     * method will be called on all {@code MessageReader}-instances to determine which should perform the
     * deserialization in the end.
     *
     * @return {@code true} iff this {@code MessageReader} can read the given message.
     */
    boolean canRead(Incoming<byte[]> message);

    /**
     * Deserializes an incoming message by reading from the given {@link InputStream}.
     *
     * @param message the incoming message and its metadata.
     * @return the content of the message
     * @throws IllegalArgumentException if this {@code MessageReader} is not applicable to a message with the provided
     *                                  metadata
     * @throws InvalidMessageException  if the message could not be serialized
     * @throws IOException              if the message could not be read from the InputStream
     */
    T read(Incoming<InputStream> message) throws InvalidMessageException, IOException;

}
