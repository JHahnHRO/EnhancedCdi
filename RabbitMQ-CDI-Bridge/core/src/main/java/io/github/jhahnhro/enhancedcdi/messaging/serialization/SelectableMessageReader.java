package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.InputStream;
import javax.enterprise.inject.spi.Prioritized;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

/**
 * Deserializes Java objects from an {@link InputStream}. The {@link #canRead(Incoming)} method decides whether this
 * {@link SelectableMessageReader} is applicable to a given {@link Incoming incoming message} by examining the metadata, i.e. the
 * delivery's {@link Envelope} (containing the name of the exchange and the routing key) and {@link BasicProperties}.
 * <p>
 * It should always be tested (with {@link #canRead(Incoming)}) whether a specific {@code SelectableMessageReader} is applicable
 * to a given message before attempting serialization.
 * <p>
 * The {@code SelectableMessageReader} to use for any given delivery is determined by selecting the {@code SelectableMessageReader} with the
 * highest {@link #getPriority() priority} among those that are {@link #canRead(Incoming)}. If multiple
 * {@code SelectableMessageReader} instances with the same priority are applicable to a delivery, it is undefined which one will
 * be selected.
 *
 * @param <T> type of the deserialized objects
 * @see ByteArrayReaderWriter
 * @see PlainTextReaderWriter
 */
public interface SelectableMessageReader<T> extends Prioritized, MessageReader<T> {

    /**
     * Automatic {@code SelectableMessageReader}-selection uses {@link #canRead(Incoming)} to determine the {@code SelectableMessageReader}s
     * that are applicable to an incoming message and then selects the one with the highest priority.
     *
     * @return the priority of this {@link SelectableMessageReader}.
     */
    @Override
    int getPriority();

    /**
     * A (reasonably quick) test whether this {@code SelectableMessageReader} is applicable to the message, e.g. by evaluating the
     * message's {@link BasicProperties#getContentType() content type}, its {@link BasicProperties#getHeaders() headers}
     * or something else
     *
     * @return {@code true} iff this {@code SelectableMessageReader} can read the given message.
     * @implNote It is not recommended to make an attempt at deserialization of the content, because this method will be
     * called on all {@code SelectableMessageReader}-instances to determine which should perform the deserialization in the end.
     */
    boolean canRead(Incoming<byte[]> message);

}
