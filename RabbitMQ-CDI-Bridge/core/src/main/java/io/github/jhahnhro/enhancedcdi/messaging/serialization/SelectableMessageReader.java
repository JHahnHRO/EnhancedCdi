package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import javax.enterprise.inject.spi.Prioritized;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

/**
 * A {@link MessageReader} that participates in automatic deserialization. If a bean has this interface in its bean
 * types, then it will be automatically discovered and among the discovered readers the appropriate one will be
 * automatically selected for each incoming message.
 * <p>
 * Whether a particular reader is applicable to a given message is decided by the {@link #canRead(Incoming)} method by
 * examining the message's metadata, i.e. the delivery's {@link Envelope} (containing the name of the exchange and the
 * routing key) and {@link BasicProperties}.
 * <p>
 * It should always be tested (with {@link #canRead(Incoming)}) whether a specific {@code SelectableMessageReader} is
 * applicable to a given message before attempting deserialization.
 * <p>
 * The {@code SelectableMessageReader} that is selected for any given message is determined by selecting the reader with
 * the highest {@link #getPriority() priority} among those that are applicable. If multiple instances with the same
 * priority are applicable to a message, it is undefined which one will be selected.
 * <p>
 * The library provides a {@link javax.enterprise.context.RequestScoped} bean of type {@code MessageReader<Object>} with
 * qualifier {@link Selected} that represents the result of the selection process for the incoming message of the
 * current request context.
 *
 * @param <T> type of the deserialized objects
 * @see ByteArrayReaderWriter
 * @see PlainTextReaderWriter
 */
public interface SelectableMessageReader<T> extends Prioritized, MessageReader<T> {

    /**
     * Automatic {@code SelectableMessageReader}-selection uses {@link #canRead(Incoming)} to determine the
     * {@code SelectableMessageReader}s that are applicable to an incoming message and then selects the one with the
     * highest priority.
     *
     * @return the priority of this {@link SelectableMessageReader}.
     */
    @Override
    int getPriority();

    /**
     * A (reasonably quick) test whether this {@code SelectableMessageReader} is applicable to the message, e.g. by
     * evaluating the message's {@link BasicProperties#getContentType() content type}, its
     * {@link BasicProperties#getHeaders() headers} or something else.
     *
     * @return {@code true} iff this {@code SelectableMessageReader} can read the given message.
     * @implNote It is not recommended to make an attempt at deserialization of the content, because this method will be
     * called on all {@code SelectableMessageReader}-instances to determine which should perform the deserialization in
     * the end.
     */
    boolean canRead(Incoming<byte[]> message);

}
